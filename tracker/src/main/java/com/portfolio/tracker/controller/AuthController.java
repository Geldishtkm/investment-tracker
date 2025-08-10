package com.portfolio.tracker.controller;

import com.portfolio.tracker.security.JwtUtil;
import com.portfolio.tracker.service.UserDetailsServiceImpl;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/auth")
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
    public ResponseEntity<?> register(@RequestParam String username, @RequestParam String password) {
        try {
            logger.info("Registration attempt for username: {}", username);
            
            // Basic validation
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Username is required");
            }
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required");
            }
            if (password.length() < 6) {
                return ResponseEntity.badRequest().body("Password must be at least 6 characters long");
            }
            
            userService.registerUser(username, password);
            logger.info("User registered successfully: {}", username);
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        } catch (Exception e) {
            logger.error("Registration failed for username {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed: " + e.getMessage()));
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