package com.portfolio.tracker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A refresh token issued at login. Only the SHA-256 hash of the token is stored; the token itself
 * exists only on the client. Tokens from the same login share a family so a stolen, replayed token
 * can end that whole session.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(Long userId, String tokenHash, String familyId, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public String getFamilyId() { return familyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired(LocalDateTime now) { return !now.isBefore(expiresAt); }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) revokedAt = now;
    }
}
