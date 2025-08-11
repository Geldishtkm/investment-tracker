package com.portfolio.tracker.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class MfaService {
    
    private final Map<String, String> userMfaSecrets = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> mfaAttempts = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedMfaAttempts = new ConcurrentHashMap<>();
    
    private static final int MFA_SECRET_LENGTH = 32;
    private static final int MFA_CODE_LENGTH = 6;
    private static final int MAX_MFA_ATTEMPTS = 3;
    private static final int MFA_LOCKOUT_MINUTES = 15;
    
    /**
     * Generate a new MFA secret for a user
     */
    public String generateMfaSecret(String username) {
        SecureRandom random = new SecureRandom();
        byte[] secretBytes = new byte[MFA_SECRET_LENGTH];
        random.nextBytes(secretBytes);
        String secret = Base64.getEncoder().encodeToString(secretBytes);
        
        userMfaSecrets.put(username, secret);
        return secret;
    }
    
    /**
     * Generate a 6-digit MFA code for testing purposes
     * In production, this would use TOTP algorithm
     */
    public String generateMfaCode(String username) {
        String secret = userMfaSecrets.get(username);
        if (secret == null) {
            throw new IllegalStateException("No MFA secret found for user: " + username);
        }
        
        // Simple hash-based code generation for demo
        // In production, use proper TOTP implementation
        int code = Math.abs(secret.hashCode() % 1000000);
        return String.format("%06d", code);
    }
    
    /**
     * Verify MFA code entered by user
     */
    public boolean verifyMfaCode(String username, String code) {
        // Check if user is locked out
        if (isMfaLocked(username)) {
            return false;
        }
        
        // Check if code matches
        String expectedCode = generateMfaCode(username);
        boolean isValid = expectedCode.equals(code);
        
        if (isValid) {
            // Reset failed attempts on success
            failedMfaAttempts.remove(username);
            mfaAttempts.remove(username);
        } else {
            // Increment failed attempts
            int failedCount = failedMfaAttempts.getOrDefault(username, 0) + 1;
            failedMfaAttempts.put(username, failedCount);
            
            if (failedCount >= MAX_MFA_ATTEMPTS) {
                // Lock out user
                mfaAttempts.put(username, LocalDateTime.now());
            }
        }
        
        return isValid;
    }
    
    /**
     * Check if user is locked out from MFA attempts
     */
    public boolean isMfaLocked(String username) {
        LocalDateTime lockoutTime = mfaAttempts.get(username);
        if (lockoutTime == null) {
            return false;
        }
        
        long minutesSinceLockout = ChronoUnit.MINUTES.between(lockoutTime, LocalDateTime.now());
        if (minutesSinceLockout >= MFA_LOCKOUT_MINUTES) {
            // Remove lockout
            mfaAttempts.remove(username);
            failedMfaAttempts.remove(username);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get remaining lockout time in minutes
     */
    public long getRemainingLockoutTime(String username) {
        LocalDateTime lockoutTime = mfaAttempts.get(username);
        if (lockoutTime == null) {
            return 0;
        }
        
        long minutesSinceLockout = ChronoUnit.MINUTES.between(lockoutTime, LocalDateTime.now());
        long remainingTime = MFA_LOCKOUT_MINUTES - minutesSinceLockout;
        
        return Math.max(0, remainingTime);
    }
    
    /**
     * Get failed MFA attempts count
     */
    public int getFailedMfaAttempts(String username) {
        return failedMfaAttempts.getOrDefault(username, 0);
    }
    
    /**
     * Reset MFA for a user (admin function)
     */
    public void resetMfa(String username) {
        userMfaSecrets.remove(username);
        mfaAttempts.remove(username);
        failedMfaAttempts.remove(username);
    }
    
    /**
     * Check if user has MFA enabled
     */
    public boolean hasMfaEnabled(String username) {
        return userMfaSecrets.containsKey(username);
    }
    
    /**
     * Get MFA secret for QR code generation
     */
    public String getMfaSecret(String username) {
        return userMfaSecrets.get(username);
    }
    
    /**
     * Generate QR code data for authenticator apps
     */
    public String generateQrCodeData(String username, String issuer) {
        String secret = userMfaSecrets.get(username);
        if (secret == null) {
            return null;
        }
        
        // Format: otpauth://totp/Issuer:Username?secret=SECRET&issuer=Issuer
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s", 
                           issuer, username, secret, issuer);
    }
}
