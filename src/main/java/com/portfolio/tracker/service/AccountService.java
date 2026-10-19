package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Trade;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AssetRepository;
import com.portfolio.tracker.repository.AuditLogRepository;
import com.portfolio.tracker.repository.RefreshTokenRepository;
import com.portfolio.tracker.repository.TradeRepository;
import com.portfolio.tracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The user's paper-trading account: the starting bankroll (S₀), the cash it leaves, and deleting the
 * account after an emailed confirmation code.
 *
 * <p>S₀ is chosen once and then fixed. Cash is S₀ − every buy + every sell in the trade ledger, and a
 * buy that costs more than the cash available is refused, so the portfolio can't grow from money that
 * was never there.
 */
@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final BigDecimal MIN_BANKROLL = new BigDecimal("100");
    public static final BigDecimal MAX_BANKROLL = new BigDecimal("10000000");
    /** Half a cent: rounding below this never decides whether a buy is affordable. */
    private static final double CASH_TOLERANCE = 0.005;

    private final UserRepository userRepository;
    private final TradeRepository tradeRepository;
    private final AssetRepository assetRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final long deletionCodeTtlMinutes;
    private final long resendCooldownSeconds;
    private final int maxAttempts;

    public AccountService(UserRepository userRepository, TradeRepository tradeRepository,
                          AssetRepository assetRepository, RefreshTokenRepository refreshTokenRepository,
                          AuditLogRepository auditLogRepository, PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          @Value("${app.account-deletion.code-ttl-minutes:15}") long deletionCodeTtlMinutes,
                          @Value("${app.verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
                          @Value("${app.verification.max-attempts:5}") int maxAttempts) {
        this.userRepository = userRepository;
        this.tradeRepository = tradeRepository;
        this.assetRepository = assetRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.deletionCodeTtlMinutes = deletionCodeTtlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
    }

    /** An account rule that was broken, with the HTTP status and machine-readable code the API returns. */
    public static class AccountException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        private final Map<String, Object> details;

        public AccountException(HttpStatus status, String code, String message, Map<String, Object> details) {
            super(message);
            this.status = status;
            this.code = code;
            this.details = details;
        }

        public AccountException(HttpStatus status, String code, String message) {
            this(status, code, message, Map.of());
        }

        public HttpStatus getStatus() { return status; }
        public String getCode() { return code; }

        public Map<String, Object> toBody() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", getMessage());
            body.put("code", code);
            body.putAll(details);
            return body;
        }
    }

    /**
     * @param initialBankroll  S₀, or null until the user sets it
     * @param cash             S₀ − buys + sells; null without S₀
     * @param netInvested      buys − sells: the cash the ledger has already used
     * @param minimumBankroll  the smallest S₀ that covers the existing ledger
     */
    public record Account(String username, String email, BigDecimal initialBankroll, Instant bankrollSetAt,
                          Double cash, double netInvested, double minimumBankroll, boolean hasPortfolio) {
        public boolean bankrollRequired() {
            return initialBankroll == null;
        }
    }

    @Transactional(readOnly = true)
    public Account account(User user) {
        double invested = netInvested(user.getId());
        BigDecimal s0 = user.getInitialBankroll();
        boolean hasPortfolio = invested != 0.0 || !tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(user.getId()).isEmpty();
        return new Account(user.getUsername(), user.getEmail(), s0, user.getBankrollSetAt(),
                s0 == null ? null : s0.doubleValue() - invested, invested,
                Math.max(0.0, Math.ceil(invested * 100) / 100), hasPortfolio);
    }

    /**
     * Set S₀ once. With resetPortfolio the existing holdings and trades are cleared first; otherwise S₀
     * must cover what the ledger has already spent.
     */
    @Transactional
    public Account setBankroll(User user, BigDecimal amount, boolean resetPortfolio) {
        userRepository.lockById(user.getId());
        User current = userRepository.findById(user.getId()).orElseThrow();
        if (current.getInitialBankroll() != null) {
            throw new AccountException(HttpStatus.CONFLICT, "BANKROLL_ALREADY_SET",
                    "Your starting bankroll is already set to $" + current.getInitialBankroll().toPlainString()
                            + " and can't be changed.");
        }
        if (amount == null || amount.compareTo(MIN_BANKROLL) < 0 || amount.compareTo(MAX_BANKROLL) > 0) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_BANKROLL",
                    "Choose a starting bankroll between $100 and $10,000,000.");
        }
        BigDecimal s0 = amount.setScale(2, RoundingMode.HALF_UP);
        if (resetPortfolio) {
            int trades = tradeRepository.deleteByUserId(current.getId());
            int assets = assetRepository.deleteByUserId(current.getId());
            logger.info("User {} reset their portfolio ({} holdings, {} trades) to start with a bankroll", current.getUsername(), assets, trades);
        } else {
            double invested = netInvested(current.getId());
            if (s0.doubleValue() + CASH_TOLERANCE < invested) {
                throw new AccountException(HttpStatus.BAD_REQUEST, "BANKROLL_TOO_SMALL",
                        "Your recorded trades have already spent $" + money(invested) + ". Choose at least that much, "
                                + "or start over with an empty portfolio.",
                        Map.of("minimumBankroll", Math.ceil(invested * 100) / 100));
            }
        }
        current.setInitialBankroll(s0);
        current.setBankrollSetAt(Instant.now());
        userRepository.save(current);
        logger.info("User {} set their starting bankroll to {}", current.getUsername(), s0.toPlainString());
        return account(current);
    }

    /** Refuse trading until S₀ is set; a cheap check to run before slower ones such as the market price. */
    public void requireBankroll(User user) {
        if (user.getInitialBankroll() == null
                && userRepository.findById(user.getId()).map(User::getInitialBankroll).orElse(null) == null) {
            throw new AccountException(HttpStatus.CONFLICT, "BANKROLL_REQUIRED", "Set your starting bankroll before buying.");
        }
    }

    /**
     * Refuse a buy the cash can't pay for. Call inside the transaction that records the buy: the user's row
     * stays locked until it commits, so two buys at once can't both spend the same cash.
     *
     * @param previousCost cost already counted in the ledger that this purchase replaces (when an opening
     *                     buy is corrected); 0 for a new buy
     */
    public void requireCash(User user, double cost, double previousCost) {
        userRepository.lockById(user.getId());
        BigDecimal s0 = userRepository.findById(user.getId()).map(User::getInitialBankroll).orElse(null);
        if (s0 == null) {
            throw new AccountException(HttpStatus.CONFLICT, "BANKROLL_REQUIRED",
                    "Set your starting bankroll before buying.");
        }
        double cash = s0.doubleValue() - netInvested(user.getId()) + previousCost;
        if (cost > cash + CASH_TOLERANCE) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS",
                    "Not enough cash: this costs $" + money(cost) + " and you have $" + money(Math.max(0, cash)) + " available.",
                    Map.of("cash", Math.max(0, cash), "cost", cost));
        }
    }

    private double netInvested(Long userId) {
        return tradeRepository.sumNotional(userId, Trade.Side.BUY) - tradeRepository.sumNotional(userId, Trade.Side.SELL);
    }

    // ---------------------------------------------------------------------
    // Account deletion
    // ---------------------------------------------------------------------

    /**
     * Check the password and email a 6-digit code that confirms the deletion.
     * @return true if the email went out (or was logged because SMTP isn't configured)
     */
    @Transactional
    public boolean requestDeletion(User user, String password) {
        User current = userRepository.findById(user.getId()).orElseThrow();
        if (password == null || !passwordEncoder.matches(password, current.getPassword())) {
            throw new AccountException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "Incorrect password.");
        }
        if (current.getEmail() == null || current.getEmail().isBlank()) {
            throw new AccountException(HttpStatus.CONFLICT, "NO_EMAIL",
                    "This account has no email address to send the confirmation code to.");
        }
        if (current.getDeletionCodeSentAt() != null) {
            long elapsed = Duration.between(current.getDeletionCodeSentAt(), Instant.now()).getSeconds();
            long wait = resendCooldownSeconds - elapsed;
            if (wait > 0) {
                throw new AccountException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_COOLDOWN",
                        "Please wait " + wait + " seconds before requesting another code.", Map.of("retryAfterSeconds", wait));
            }
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        current.setDeletionCodeHash(sha256(code));
        current.setDeletionCodeExpiresAt(now.plus(Duration.ofMinutes(deletionCodeTtlMinutes)));
        current.setDeletionCodeSentAt(now);
        current.setDeletionAttempts(0);
        userRepository.save(current);
        try {
            emailService.sendAccountDeletionCode(current.getEmail(), current.getUsername(), code, deletionCodeTtlMinutes);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send the account deletion email to user {}: {}", current.getUsername(), e.getMessage());
            return false;
        }
    }

    /** Check the emailed code and, if it matches, permanently delete the account and everything in it. */
    // Keep the failed-attempt counter even though a wrong code ends in an exception
    @Transactional(noRollbackFor = AccountException.class)
    public void confirmDeletion(User user, String code) {
        userRepository.lockById(user.getId());
        User current = userRepository.findById(user.getId()).orElseThrow();
        if (current.getDeletionCodeHash() == null || current.getDeletionCodeExpiresAt() == null) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_CODE",
                    "There is no active deletion code. Request a new one.");
        }
        if (current.getDeletionAttempts() >= maxAttempts) {
            throw new AccountException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many incorrect attempts. Request a new code.");
        }
        if (Instant.now().isAfter(current.getDeletionCodeExpiresAt())) {
            throw new AccountException(HttpStatus.GONE, "CODE_EXPIRED", "This code has expired. Request a new one.");
        }
        String submitted = code == null ? "" : code.trim();
        // Compare hashes in constant time so response timing doesn't reveal correct digits
        boolean matches = MessageDigest.isEqual(sha256(submitted).getBytes(StandardCharsets.UTF_8),
                current.getDeletionCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            current.setDeletionAttempts(current.getDeletionAttempts() + 1);
            userRepository.save(current);
            int remaining = maxAttempts - current.getDeletionAttempts();
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_CODE",
                    remaining > 0 ? "Incorrect code. " + remaining + (remaining == 1 ? " attempt" : " attempts") + " left."
                            : "Incorrect code. Request a new code to try again.",
                    Map.of("attemptsRemaining", remaining));
        }
        deleteEverything(current);
    }

    private void deleteEverything(User user) {
        Long id = user.getId();
        int trades = tradeRepository.deleteByUserId(id);
        int sessions = refreshTokenRepository.deleteAllForUser(id);
        int audits = auditLogRepository.deleteByUserId(id);
        int assets = assetRepository.deleteByUserId(id);
        userRepository.deleteUserById(id);
        logger.info("Deleted account {} (id {}): {} holdings, {} trades, {} sessions, {} audit entries",
                user.getUsername(), id, assets, trades, sessions, audits);
    }

    public long getDeletionCodeTtlSeconds() {
        return deletionCodeTtlMinutes * 60;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String money(double v) {
        return String.format(java.util.Locale.US, "%,.2f", v);
    }
}
