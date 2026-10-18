package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.security.JwtUtil;
import com.portfolio.tracker.service.EmailVerificationService;
import com.portfolio.tracker.service.EmailVerificationService.VerificationException;
import com.portfolio.tracker.service.UserDetailsServiceImpl;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/auth", "/api/auth"})
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Test endpoint to verify auth controller is accessible
     */
    @GetMapping("/test")
    public ResponseEntity<?> testAuth() {
        logger.info("Auth test endpoint accessed");
        return ResponseEntity.ok(Map.of(
            "message", "Auth controller is working",
            "timestamp", System.currentTimeMillis(),
            "endpoints", Map.of(
                "login", "POST /auth/login",
                "register", "POST /auth/register",
                "verifyEmail", "POST /auth/verify-email",
                "resendCode", "POST /auth/resend-code",
                "test", "GET /auth/test"
            )
        ));
    }

    /**
     * Check if there are any users in the database
     */
    @GetMapping("/status")
    public ResponseEntity<?> checkStatus() {
        try {
            // This is a simple way to check if the service is working
            // In production, you might want to add more sophisticated health checks
            logger.info("Status check requested");
            return ResponseEntity.ok(Map.of(
                "status", "Auth service is running",
                "timestamp", System.currentTimeMillis(),
                "message", "Ready to handle authentication requests"
            ));
        } catch (Exception e) {
            logger.error("Status check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Service status check failed"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestParam String username,
                                      @RequestParam String email,
                                      @RequestParam String password) {
        try {
            logger.info("Registration attempt for username: {}", username);
            
            UserService.Registration registration = userService.registerUser(username.trim(), email, password);
            User user = registration.user();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requiresVerification", true);
            body.put("email", user.getEmail());
            body.put("codeExpiresInSeconds", emailVerificationService.getCodeTtlSeconds());

            if (registration.existingPendingSignup()) {
                // Same person signing up again before verifying: back to the code screen
                EmailVerificationService.ResumeOutcome outcome = emailVerificationService.resumePendingSignup(user);
                logger.info("Resumed pending signup for {}", username);
                body.put("message", outcome.codeSent()
                        ? "You already started signing up. We sent a new code to " + user.getEmail() + "."
                        : outcome.sendFailed()
                            ? "You already started signing up, but we couldn't send a new code. Use \"Resend code\" to try again."
                            : "You already started signing up. Enter the code we sent to " + user.getEmail() + ".");
                body.put("emailSent", !outcome.sendFailed());
                body.put("resumed", true);
                body.put("resendCooldownSeconds", outcome.resendAvailableInSeconds());
                return ResponseEntity.ok(body);
            }

            boolean emailSent = emailVerificationService.issueCode(user);
            logger.info("User registered, awaiting email verification: {}", username);
            body.put("message", emailSent
                    ? "Account created. We sent a 6-digit code to " + user.getEmail() + "."
                    : "Account created, but we couldn't send the verification email. Use \"Resend code\" to try again.");
            body.put("emailSent", emailSent);
            body.put("resumed", false);
            body.put("resendCooldownSeconds", emailSent ? emailVerificationService.getResendCooldownSeconds() : 0);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Registration failed for username {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    /**
     * Confirm the 6-digit code emailed at signup and activate the account
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
        try {
            User user = emailVerificationService.verify(request.get("email"), request.get("code"));
            return ResponseEntity.ok(Map.of(
                "message", "Email verified. You can now log in.",
                "username", user.getUsername(),
                "verified", true
            ));
        } catch (VerificationException e) {
            return ResponseEntity.status(e.getStatus()).body(e.toBody());
        }
    }

    /**
     * Send a new verification code (subject to a cooldown between sends)
     */
    @PostMapping("/resend-code")
    public ResponseEntity<?> resendCode(@RequestBody Map<String, String> request) {
        try {
            boolean sent = emailVerificationService.resend(request.get("email"));
            if (!sent) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "We couldn't send the email right now. Please try again in a moment.",
                    "code", "EMAIL_SEND_FAILED"
                ));
            }
            return ResponseEntity.ok(Map.of(
                "message", "A new code has been sent.",
                "codeExpiresInSeconds", emailVerificationService.getCodeTtlSeconds(),
                "resendCooldownSeconds", emailVerificationService.getResendCooldownSeconds()
            ));
        } catch (VerificationException e) {
            return ResponseEntity.status(e.getStatus()).body(e.toBody());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        try {
            logger.info("Login attempt for username: {}", username);
            
            // Basic validation
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Username is required");
            }
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required");
            }
            
            // Authenticate the user credentials
            authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            logger.debug("Authentication successful for user: {}", username);
            
            // Load user details by username
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            logger.debug("User details loaded for: {}", username);
            
            // Generate JWT token using UserDetails
            String token = jwtUtil.generateToken(userDetails);
            logger.info("JWT token generated successfully for user: {}", username);
            
            return ResponseEntity.ok(Map.of(
                "token", token, 
                "message", "Login successful",
                "username", username
            ));
        } catch (DisabledException e) {
            // Spring checks "enabled" before the password, so confirm the password first to avoid
            // revealing which accounts exist but are unverified
            User user = userService.findByUsername(username);
            if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
            }
            logger.info("Login blocked for unverified user: {}", username);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Please verify your email before logging in. Check your inbox for the 6-digit code.");
            body.put("code", "EMAIL_NOT_VERIFIED");
            body.put("email", user.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        } catch (BadCredentialsException e) {
            logger.warn("Invalid credentials for user: {}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password"));
        } catch (Exception e) {
            logger.error("Login failed for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }
}