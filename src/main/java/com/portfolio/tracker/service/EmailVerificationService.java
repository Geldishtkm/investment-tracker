package com.portfolio.tracker.service;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Issues and checks the 6-digit codes that activate new accounts. Codes come from a
 * cryptographic RNG, expire after a configurable time, allow a limited number of guesses,
 * and can only be re-sent after a cooldown.
 */
@Service
public class EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final long codeTtlMinutes;
    private final long resendCooldownSeconds;
    private final int maxAttempts;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailService emailService,
                                    @Value("${app.verification.code-ttl-minutes}") long codeTtlMinutes,
                                    @Value("${app.verification.resend-cooldown-seconds}") long resendCooldownSeconds,
                                    @Value("${app.verification.max-attempts}") int maxAttempts) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.codeTtlMinutes = codeTtlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
    }

    /** A verification failure with the HTTP status and machine-readable code the API returns. */
    public static class VerificationException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        private final Map<String, Object> details;

        public VerificationException(HttpStatus status, String code, String message, Map<String, Object> details) {
            super(message);
            this.status = status;
            this.code = code;
            this.details = details;
        }

        public HttpStatus getStatus() { return status; }
        public String getCode() { return code; }
        public Map<String, Object> getDetails() { return details; }

        public Map<String, Object> toBody() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", getMessage());
            body.put("code", code);
            body.putAll(details);
            return body;
        }
    }

    /**
     * Generate a fresh code for the user, store it and email it.
     * @return true if the email went out (or was logged because SMTP isn't configured), false if sending failed
     */
    @Transactional
    public boolean issueCode(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();
        user.setVerificationCode(code);
        user.setCodeExpiration(now.plusMinutes(codeTtlMinutes));
        user.setVerificationAttempts(0);
        user.setVerificationSentAt(now);
        userRepository.save(user);
        try {
            emailService.sendVerificationCode(user.getEmail(), user.getUsername(), code, codeTtlMinutes);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
            return false;
        }
    }

    /** Check the code for an email address and activate the account if it matches. */
    // Keep the failed-attempt counter even though a wrong code ends in an exception
    @Transactional(noRollbackFor = VerificationException.class)
    public User verify(String email, String code) {
        User user = findPendingUser(email);
        if (user.getVerificationCode() == null || user.getCodeExpiration() == null) {
            throw new VerificationException(HttpStatus.BAD_REQUEST, "NO_ACTIVE_CODE",
                    "There is no active code for this account. Request a new one.", Map.of());
        }
        if (user.getVerificationAttempts() >= maxAttempts) {
            throw new VerificationException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many incorrect attempts. Request a new code.", Map.of());
        }
        if (LocalDateTime.now().isAfter(user.getCodeExpiration())) {
            throw new VerificationException(HttpStatus.GONE, "CODE_EXPIRED",
                    "This code has expired. Request a new one.", Map.of());
        }
        String submitted = code == null ? "" : code.trim();
        // Constant-time comparison so response timing doesn't reveal correct digits
        boolean matches = MessageDigest.isEqual(
                submitted.getBytes(StandardCharsets.UTF_8),
                user.getVerificationCode().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            user.setVerificationAttempts(user.getVerificationAttempts() + 1);
            userRepository.save(user);
            int remaining = maxAttempts - user.getVerificationAttempts();
            throw new VerificationException(HttpStatus.BAD_REQUEST, "INVALID_CODE",
                    remaining > 0
                            ? "Incorrect code. " + remaining + (remaining == 1 ? " attempt" : " attempts") + " left."
                            : "Incorrect code. Request a new code to try again.",
                    Map.of("attemptsRemaining", remaining));
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setCodeExpiration(null);
        user.setVerificationAttempts(0);
        user.setVerificationSentAt(null);
        logger.info("Email verified for user {}", user.getUsername());
        return userRepository.save(user);
    }

    /** Send a new code, unless the previous one was sent less than the cooldown ago. */
    @Transactional
    public boolean resend(String email) {
        User user = findPendingUser(email);
        long wait = secondsUntilResendAllowed(user);
        if (wait > 0) {
            throw new VerificationException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_COOLDOWN",
                    "Please wait " + wait + " seconds before requesting another code.",
                    Map.of("retryAfterSeconds", wait));
        }
        return issueCode(user);
    }

    public long getCodeTtlSeconds() {
        return codeTtlMinutes * 60;
    }

    public long getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    private long secondsUntilResendAllowed(User user) {
        if (user.getVerificationSentAt() == null) return 0;
        long elapsed = Duration.between(user.getVerificationSentAt(), LocalDateTime.now()).getSeconds();
        return Math.max(0, resendCooldownSeconds - elapsed);
    }

    private User findPendingUser(String email) {
        if (email == null || email.isBlank()) {
            throw new VerificationException(HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED", "Email is required.", Map.of());
        }
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new VerificationException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "No account is waiting for verification with this email.", Map.of()));
        if (user.isEnabled()) {
            throw new VerificationException(HttpStatus.CONFLICT, "ALREADY_VERIFIED",
                    "This email is already verified. You can log in.", Map.of());
        }
        return user;
    }
}
