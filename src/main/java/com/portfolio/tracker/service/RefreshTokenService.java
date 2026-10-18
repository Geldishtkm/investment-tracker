package com.portfolio.tracker.service;

import com.portfolio.tracker.model.RefreshToken;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.RefreshTokenRepository;
import com.portfolio.tracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and rotates refresh tokens. Each refresh replaces the presented token with a new one
 * (rotation); a token that was already replaced is treated as stolen and ends that login session.
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               UserRepository userRepository,
                               @Value("${auth.refresh-token.ttl-days}") long ttlDays) {
        if (ttlDays < 1) {
            throw new IllegalArgumentException("auth.refresh-token.ttl-days must be at least 1");
        }
        this.repository = repository;
        this.userRepository = userRepository;
        this.ttl = Duration.ofDays(ttlDays);
    }

    /** The token's owner and the replacement refresh token, returned by {@link #rotate}. */
    public record Rotation(User user, String refreshToken) {}

    /** Why a refresh token was rejected. */
    public static class RefreshTokenException extends RuntimeException {
        private final String code;

        RefreshTokenException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }

    public long getTtlSeconds() {
        return ttl.toSeconds();
    }

    /** Starts a new login session (token family) for the user and returns its first refresh token. */
    @Transactional
    public String issue(User user) {
        return create(user.getId(), UUID.randomUUID().toString(), LocalDateTime.now());
    }

    /**
     * Exchanges a refresh token for a new one. The old token stops working immediately.
     * Rejections are committed (noRollbackFor) so a detected reuse really revokes the family.
     */
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken current = find(rawToken);

        if (current.isRevoked()) {
            // Only the holder of the newest token can refresh; an old one being replayed means a copy exists
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            logger.warn("Reuse of a replaced refresh token for user id {}: ended that session ({} token(s) revoked)",
                    current.getUserId(), revoked);
            throw invalid();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw new RefreshTokenException("REFRESH_TOKEN_EXPIRED", "Your session has expired. Please log in again.");
        }
        User user = userRepository.findById(current.getUserId()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }

        current.revoke(now);
        return new Rotation(user, create(user.getId(), current.getFamilyId(), now));
    }

    /** Logout: ends the login session the token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), LocalDateTime.now()));
    }

    /** Ends every session of a user, e.g. after a password change. */
    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId, LocalDateTime.now());
    }

    @Scheduled(cron = "${auth.refresh-token.cleanup-cron}")
    @Transactional
    public void deleteExpiredTokens() {
        try {
            int removed = repository.deleteExpired(LocalDateTime.now());
            if (removed > 0) {
                logger.info("Removed {} expired refresh token(s)", removed);
            }
        } catch (Exception e) {
            logger.error("Cleanup of expired refresh tokens failed: {}", e.getMessage(), e);
        }
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        return repository.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
    }

    private String create(Long userId, String familyId, LocalDateTime now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(userId, hash(rawToken), familyId, now, now.plus(ttl)));
        return rawToken;
    }

    private static RefreshTokenException invalid() {
        return new RefreshTokenException("REFRESH_TOKEN_INVALID", "Your session is no longer valid. Please log in again.");
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
