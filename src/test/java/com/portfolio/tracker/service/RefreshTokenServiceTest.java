package com.portfolio.tracker.service;

import com.portfolio.tracker.model.RefreshToken;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.RefreshTokenRepository;
import com.portfolio.tracker.repository.UserRepository;
import com.portfolio.tracker.service.RefreshTokenService.RefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Refresh tokens: rotation, expiry and reuse detection")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenService service;
    private User user;

    /** Saved tokens by hash, so the mocked repository behaves like the table. */
    private final Map<String, RefreshToken> table = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, userRepository, 7);
        user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setEnabled(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            table.put(t.getTokenHash(), t);
            return t;
        });
        when(repository.findByTokenHash(anyString())).thenAnswer(inv -> Optional.ofNullable(table.get(inv.<String>getArgument(0))));
        when(repository.revokeFamily(anyString(), any())).thenAnswer(inv -> {
            String family = inv.getArgument(0);
            LocalDateTime now = inv.getArgument(1);
            table.values().stream().filter(t -> t.getFamilyId().equals(family)).forEach(t -> t.revoke(now));
            return 1;
        });
    }

    @Test
    @DisplayName("Only a hash is stored, never the token itself")
    void storesOnlyHash() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertNotEquals(raw, saved.getValue().getTokenHash());
        assertEquals(64, saved.getValue().getTokenHash().length());
        assertTrue(raw.length() >= 43); // 256 random bits
    }

    @Test
    @DisplayName("Refreshing returns a new token and the old one stops working")
    void rotates() {
        String first = service.issue(user);

        RefreshTokenService.Rotation rotation = service.rotate(first);

        assertEquals("alice", rotation.user().getUsername());
        assertNotEquals(first, rotation.refreshToken());
        assertTrue(table.get(RefreshTokenService.hash(first)).isRevoked());
        assertFalse(table.get(RefreshTokenService.hash(rotation.refreshToken())).isRevoked());
        assertEquals(table.get(RefreshTokenService.hash(first)).getFamilyId(),
                table.get(RefreshTokenService.hash(rotation.refreshToken())).getFamilyId());
    }

    @Test
    @DisplayName("Replaying a replaced token ends the whole session, including the newest token")
    void reuseRevokesFamily() {
        String first = service.issue(user);
        String second = service.rotate(first).refreshToken();

        RefreshTokenException e = assertThrows(RefreshTokenException.class, () -> service.rotate(first));
        assertEquals("REFRESH_TOKEN_INVALID", e.getCode());
        assertThrows(RefreshTokenException.class, () -> service.rotate(second));
    }

    @Test
    @DisplayName("Logging in elsewhere is unaffected when one session is revoked")
    void sessionsAreIndependent() {
        String laptop = service.issue(user);
        String phone = service.issue(user);

        service.revoke(laptop);

        assertThrows(RefreshTokenException.class, () -> service.rotate(laptop));
        assertNotNull(service.rotate(phone).refreshToken());
    }

    @Test
    @DisplayName("Expired tokens are rejected with REFRESH_TOKEN_EXPIRED")
    void rejectsExpired() {
        String raw = "expired-token";
        LocalDateTime past = LocalDateTime.now().minusDays(8);
        table.put(RefreshTokenService.hash(raw), new RefreshToken(42L, RefreshTokenService.hash(raw), "f", past, past.plusDays(7)));

        RefreshTokenException e = assertThrows(RefreshTokenException.class, () -> service.rotate(raw));
        assertEquals("REFRESH_TOKEN_EXPIRED", e.getCode());
    }

    @Test
    @DisplayName("Unknown, empty and missing tokens are rejected")
    void rejectsUnknown() {
        for (String raw : new String[]{"not-a-real-token", "", null}) {
            RefreshTokenException e = assertThrows(RefreshTokenException.class, () -> service.rotate(raw));
            assertEquals("REFRESH_TOKEN_INVALID", e.getCode());
        }
    }

    @Test
    @DisplayName("A disabled or deleted account can't refresh")
    void rejectsDisabledUser() {
        String raw = service.issue(user);
        user.setEnabled(false);

        assertThrows(RefreshTokenException.class, () -> service.rotate(raw));
        assertTrue(table.get(RefreshTokenService.hash(raw)).isRevoked());
    }
}
