package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.AuditService;
import com.portfolio.tracker.service.MfaService;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@CrossOrigin(origins = "*")
public class SecurityController {
    
    @Autowired
    private MfaService mfaService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AuditService auditService;
    
    /**
     * Setup MFA for a user
     */
    @PostMapping("/mfa/setup")
    public ResponseEntity<Map<String, Object>> setupMfa(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            // Verify user credentials first
            User user = userService.authenticateUser(username, password);
            if (user == null) {
                auditService.logFailedAction(user, "MFA_SETUP_ATTEMPT", "Invalid credentials");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid credentials"
                ));
            }
            
            // Generate MFA secret
            String secret = mfaService.generateMfaSecret(username);
            String qrCodeData = mfaService.generateQrCodeData(username, "Portfolio Tracker");
            
            // Enable MFA for user
            user.setMfaEnabled(true);
            user.setMfaSecret(secret);
            userService.updateUser(user);
            
            auditService.logAction(user, "MFA_SETUP_SUCCESS", "MFA enabled successfully");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "MFA setup successful",
                "secret", secret,
                "qrCodeData", qrCodeData,
                "mfaEnabled", true
            ));
            
        } catch (Exception e) {
            auditService.logFailedAction(null, "MFA_SETUP_ERROR", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error setting up MFA: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Verify MFA code
     */
    @PostMapping("/mfa/verify")
    public ResponseEntity<Map<String, Object>> verifyMfa(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String code = request.get("code");
            
            if (username == null || code == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Username and code are required"
                ));
            }
            
            // Check if user is locked out
            if (mfaService.isMfaLocked(username)) {
                long remainingTime = mfaService.getRemainingLockoutTime(username);
                auditService.logFailedAction(null, "MFA_VERIFY_ATTEMPT", 
                    "User locked out: " + username);
                
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Account temporarily locked. Try again in " + remainingTime + " minutes",
                    "locked", true,
                    "remainingTime", remainingTime
                ));
            }
            
            // Verify MFA code
            boolean isValid = mfaService.verifyMfaCode(username, code);
            
            if (isValid) {
                auditService.logMfaAttempt(username, true, "MFA verification successful");
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "MFA verification successful",
                    "verified", true
                ));
            } else {
                int failedAttempts = mfaService.getFailedMfaAttempts(username);
                auditService.logMfaAttempt(username, false, "Invalid MFA code");
                
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid MFA code",
                    "failedAttempts", failedAttempts,
                    "maxAttempts", 3
                ));
            }
            
        } catch (Exception e) {
            auditService.logFailedAction(null, "MFA_VERIFY_ERROR", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error verifying MFA: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Disable MFA for a user
     */
    @PostMapping("/mfa/disable")
    public ResponseEntity<Map<String, Object>> disableMfa(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            // Verify user credentials
            User user = userService.authenticateUser(username, password);
            if (user == null) {
                auditService.logFailedAction(user, "MFA_DISABLE_ATTEMPT", "Invalid credentials");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid credentials"
                ));
            }
            
            // Disable MFA
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
            userService.updateUser(user);
            
            // Reset MFA service data
            mfaService.resetMfa(username);
            
            auditService.logAction(user, "MFA_DISABLE_SUCCESS", "MFA disabled successfully");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "MFA disabled successfully",
                "mfaEnabled", false
            ));
            
        } catch (Exception e) {
            auditService.logFailedAction(null, "MFA_DISABLE_ERROR", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error disabling MFA: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get MFA status for a user
     */
    @GetMapping("/mfa/status/{username}")
    public ResponseEntity<Map<String, Object>> getMfaStatus(@PathVariable String username) {
        try {
            User user = userService.getUserByUsername(username);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            boolean mfaEnabled = mfaService.hasMfaEnabled(username);
            int failedAttempts = mfaService.getFailedMfaAttempts(username);
            boolean isLocked = mfaService.isMfaLocked(username);
            long remainingLockoutTime = mfaService.getRemainingLockoutTime(username);
            
            return ResponseEntity.ok(Map.of(
                "username", username,
                "mfaEnabled", mfaEnabled,
                "failedAttempts", failedAttempts,
                "isLocked", isLocked,
                "remainingLockoutTime", remainingLockoutTime
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting MFA status: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Generate new MFA code for testing
     */
    @PostMapping("/mfa/generate-code")
    public ResponseEntity<Map<String, Object>> generateMfaCode(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            
            if (!mfaService.hasMfaEnabled(username)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "MFA not enabled for this user"
                ));
            }
            
            String code = mfaService.generateMfaCode(username);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "username", username,
                "code", code,
                "message", "MFA code generated successfully"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error generating MFA code: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Health check for security services
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Security Service",
                "mfaService", "UP",
                "auditService", "UP",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "DOWN",
                "service", "Security Service",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}
