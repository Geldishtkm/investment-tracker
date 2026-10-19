package com.portfolio.tracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(name = "email")
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;
    
    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;
    
    @Column(name = "mfa_secret")
    private String mfaSecret;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;
    
    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_active")
    private boolean isActive = true;

    // Email verification: new accounts stay disabled until the emailed code is confirmed
    // Target annual volatility for risk-target adherence (e.g. 0.40 = 40%); null when not set
    @Column(name = "risk_target_volatility")
    private Double riskTargetVolatility;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "verification_code", length = 6)
    private String verificationCode;

    @Column(name = "code_expiration")
    private LocalDateTime codeExpiration;

    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts = 0;

    @Column(name = "verification_sent_at")
    private LocalDateTime verificationSentAt;

    /** Paper-trading starting capital (S₀); null until the user sets it, then fixed. */
    @Column(name = "initial_bankroll", precision = 20, scale = 2)
    private java.math.BigDecimal initialBankroll;

    @Column(name = "bankroll_set_at")
    private java.time.Instant bankrollSetAt;

    @Column(name = "deletion_code_hash", length = 64)
    private String deletionCodeHash;

    @Column(name = "deletion_code_expires_at")
    private java.time.Instant deletionCodeExpiresAt;

    @Column(name = "deletion_code_sent_at")
    private java.time.Instant deletionCodeSentAt;

    @Column(name = "deletion_attempts", nullable = false)
    private int deletionAttempts = 0;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<Asset> assets = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<AuditLog> auditLogs = new HashSet<>();

    public enum UserRole {
        USER, ADMIN, MANAGER
    }

    // Constructors
    public User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public User(String username, String password, String role) {
        this();
        this.username = username;
        this.password = password;
        this.email = username + "@example.com"; // Default email
        this.role = UserRole.valueOf(role.toUpperCase());
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public LocalDateTime getAccountLockedUntil() { return accountLockedUntil; }
    public void setAccountLockedUntil(LocalDateTime accountLockedUntil) { this.accountLockedUntil = accountLockedUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public LocalDateTime getCodeExpiration() { return codeExpiration; }
    public void setCodeExpiration(LocalDateTime codeExpiration) { this.codeExpiration = codeExpiration; }

    public int getVerificationAttempts() { return verificationAttempts; }
    public void setVerificationAttempts(int verificationAttempts) { this.verificationAttempts = verificationAttempts; }

    public LocalDateTime getVerificationSentAt() { return verificationSentAt; }
    public void setVerificationSentAt(LocalDateTime verificationSentAt) { this.verificationSentAt = verificationSentAt; }

    public Set<Asset> getAssets() { return assets; }
    public void setAssets(Set<Asset> assets) { this.assets = assets; }

    public Set<AuditLog> getAuditLogs() { return auditLogs; }
    public void setAuditLogs(Set<AuditLog> auditLogs) { this.auditLogs = auditLogs; }

    // Business logic methods
    public boolean isAccountLocked() {
        return accountLockedUntil != null && LocalDateTime.now().isBefore(accountLockedUntil);
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.accountLockedUntil = LocalDateTime.now().plusMinutes(30);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLockedUntil = null;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasRole(UserRole requiredRole) {
        if (requiredRole == UserRole.USER) return true;
        if (requiredRole == UserRole.MANAGER) return role == UserRole.MANAGER || role == UserRole.ADMIN;
        return role == UserRole.ADMIN;
    }

    /**
     * Check if user has admin role
     * @return true if user is admin, false otherwise
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Double getRiskTargetVolatility() { return riskTargetVolatility; }
    public void setRiskTargetVolatility(Double riskTargetVolatility) { this.riskTargetVolatility = riskTargetVolatility; }

    public java.math.BigDecimal getInitialBankroll() { return initialBankroll; }
    public void setInitialBankroll(java.math.BigDecimal initialBankroll) { this.initialBankroll = initialBankroll; }
    public java.time.Instant getBankrollSetAt() { return bankrollSetAt; }
    public void setBankrollSetAt(java.time.Instant bankrollSetAt) { this.bankrollSetAt = bankrollSetAt; }
    public String getDeletionCodeHash() { return deletionCodeHash; }
    public void setDeletionCodeHash(String deletionCodeHash) { this.deletionCodeHash = deletionCodeHash; }
    public java.time.Instant getDeletionCodeExpiresAt() { return deletionCodeExpiresAt; }
    public void setDeletionCodeExpiresAt(java.time.Instant deletionCodeExpiresAt) { this.deletionCodeExpiresAt = deletionCodeExpiresAt; }
    public java.time.Instant getDeletionCodeSentAt() { return deletionCodeSentAt; }
    public void setDeletionCodeSentAt(java.time.Instant deletionCodeSentAt) { this.deletionCodeSentAt = deletionCodeSentAt; }
    public int getDeletionAttempts() { return deletionAttempts; }
    public void setDeletionAttempts(int deletionAttempts) { this.deletionAttempts = deletionAttempts; }
}
