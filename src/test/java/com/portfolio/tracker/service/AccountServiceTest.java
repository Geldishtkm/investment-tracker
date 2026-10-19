package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Trade;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AssetRepository;
import com.portfolio.tracker.repository.AuditLogRepository;
import com.portfolio.tracker.repository.RefreshTokenRepository;
import com.portfolio.tracker.repository.TradeRepository;
import com.portfolio.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Account: bankroll, cash and deletion")
class AccountServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EmailService emailService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AccountService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, tradeRepository, assetRepository, refreshTokenRepository,
                auditLogRepository, encoder, emailService, 15, 60, 5);
        user = new User();
        user.setId(3L);
        user.setUsername("trader");
        user.setEmail("trader@example.com");
        user.setPassword(encoder.encode("Correct-Horse-1"));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tradeRepository.findByUserIdOrderByTradeDateAscIdAsc(3L)).thenReturn(List.of());
        spent(0, 0);
    }

    private void spent(double buys, double sells) {
        when(tradeRepository.sumNotional(3L, Trade.Side.BUY)).thenReturn(buys);
        when(tradeRepository.sumNotional(3L, Trade.Side.SELL)).thenReturn(sells);
    }

    private static HttpStatus statusOf(Runnable r) {
        return assertThrows(AccountService.AccountException.class, r::run).getStatus();
    }

    @Nested
    @DisplayName("Starting bankroll")
    class Bankroll {
        @Test
        @DisplayName("A new account must set S₀; once set it is stored and fixed")
        void setOnce() {
            assertTrue(service.account(user).bankrollRequired());
            AccountService.Account a = service.setBankroll(user, new BigDecimal("10000"), false);
            assertEquals(new BigDecimal("10000.00"), user.getInitialBankroll());
            assertNotNull(user.getBankrollSetAt());
            assertEquals(10000.0, a.cash());
            verify(userRepository).lockById(3L);

            AccountService.AccountException again = assertThrows(AccountService.AccountException.class,
                    () -> service.setBankroll(user, new BigDecimal("100000"), false));
            assertEquals("BANKROLL_ALREADY_SET", again.getCode());
            assertEquals(new BigDecimal("10000.00"), user.getInitialBankroll());
        }

        @Test
        @DisplayName("S₀ must be between $100 and $10,000,000")
        void range() {
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.setBankroll(user, new BigDecimal("99.99"), false)));
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.setBankroll(user, new BigDecimal("10000000.01"), false)));
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.setBankroll(user, null, false)));
            assertNull(user.getInitialBankroll());
        }

        @Test
        @DisplayName("An existing ledger needs S₀ to cover what it spent, or a reset to an empty portfolio")
        void existingLedger() {
            spent(28_887_343, 0);
            AccountService.AccountException e = assertThrows(AccountService.AccountException.class,
                    () -> service.setBankroll(user, new BigDecimal("10000"), false));
            assertEquals("BANKROLL_TOO_SMALL", e.getCode());
            assertEquals(28_887_343.0, e.toBody().get("minimumBankroll"));
            verify(tradeRepository, never()).deleteByUserId(any());

            service.setBankroll(user, new BigDecimal("10000"), true);
            verify(tradeRepository).deleteByUserId(3L);
            verify(assetRepository).deleteByUserId(3L);
            assertEquals(new BigDecimal("10000.00"), user.getInitialBankroll());
        }
    }

    @Nested
    @DisplayName("Cash")
    class Cash {
        @Test
        @DisplayName("Buys are refused without a bankroll, and beyond the cash left")
        void requireCash() {
            AccountService.AccountException none = assertThrows(AccountService.AccountException.class,
                    () -> service.requireCash(user, 10, 0));
            assertEquals("BANKROLL_REQUIRED", none.getCode());

            user.setInitialBankroll(new BigDecimal("1000.00"));
            spent(900, 300); // cash 400
            service.requireCash(user, 400, 0);
            AccountService.AccountException e = assertThrows(AccountService.AccountException.class,
                    () -> service.requireCash(user, 400.01, 0));
            assertEquals("INSUFFICIENT_FUNDS", e.getCode());
            assertEquals(400.0, e.toBody().get("cash"));
            service.requireCash(user, 500, 100); // correcting a $100 buy to $500 needs $400 more
            verify(userRepository, atLeastOnce()).lockById(3L);
        }

        @Test
        @DisplayName("Cash is S₀ − buys + sells")
        void account() {
            user.setInitialBankroll(new BigDecimal("1000.00"));
            spent(900, 300);
            AccountService.Account a = service.account(user);
            assertEquals(400.0, a.cash());
            assertEquals(600.0, a.netInvested());
        }
    }

    @Nested
    @DisplayName("Deletion")
    class Deletion {
        private String requestCode() {
            ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
            assertTrue(service.requestDeletion(user, "Correct-Horse-1"));
            verify(emailService, atLeastOnce()).sendAccountDeletionCode(eq("trader@example.com"), eq("trader"), code.capture(), eq(15L));
            return code.getValue();
        }

        @Test
        @DisplayName("Needs the password; the emailed code is stored only as a hash")
        void request() {
            assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> service.requestDeletion(user, "wrong")));
            verify(emailService, never()).sendAccountDeletionCode(any(), any(), any(), anyLong());

            String code = requestCode();
            assertTrue(code.matches("\\d{6}"));
            assertEquals(AccountService.sha256(code), user.getDeletionCodeHash());
            assertNotEquals(code, user.getDeletionCodeHash());
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf(() -> service.requestDeletion(user, "Correct-Horse-1")));
        }

        @Test
        @DisplayName("The right code deletes the user's trades, sessions, audit entries, holdings and the user")
        void confirm() {
            String code = requestCode();
            service.confirmDeletion(user, code);
            var order = inOrder(tradeRepository, refreshTokenRepository, auditLogRepository, assetRepository, userRepository);
            order.verify(tradeRepository).deleteByUserId(3L);
            order.verify(refreshTokenRepository).deleteAllForUser(3L);
            order.verify(auditLogRepository).deleteByUserId(3L);
            order.verify(assetRepository).deleteByUserId(3L);
            order.verify(userRepository).deleteUserById(3L);
        }

        @Test
        @DisplayName("Wrong, expired or missing codes delete nothing; five wrong guesses lock the code")
        void rejected() {
            assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.confirmDeletion(user, "123456")));
            String code = requestCode();
            String wrong = code.equals("000000") ? "000001" : "000000";
            for (int i = 0; i < 5; i++) assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.confirmDeletion(user, wrong)));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf(() -> service.confirmDeletion(user, code)));

            user.setDeletionAttempts(0);
            user.setDeletionCodeExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            assertEquals(HttpStatus.GONE, statusOf(() -> service.confirmDeletion(user, code)));
            verify(userRepository, never()).deleteUserById(any());
        }
    }
}
