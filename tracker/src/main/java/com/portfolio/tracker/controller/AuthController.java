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

    /**
     * Check database status and create test user if needed
     */
    @GetMapping("/db-status")
    public ResponseEntity<?> checkDatabaseStatus() {
        try {
            logger.info("Database status check requested");
            
            // Check if any users exist
            long userCount = userService.getUserCount();
            
            if (userCount == 0) {
                // Create a test user if none exist
                try {
                    userService.registerUser("testuser", "testpass123");
                    logger.info("Created test user: testuser");
                    return ResponseEntity.ok(Map.of(
                        "status", "Database connected",
                        "message", "No users found, created test user: testuser / testpass123",
                        "userCount", 1
                    ));
                } catch (Exception e) {
                    logger.error("Failed to create test user: {}", e.getMessage());
                    return ResponseEntity.ok(Map.of(
                        "status", "Database connected",
                        "message", "No users found, failed to create test user",
                        "userCount", 0,
                        "error", e.getMessage()
                    ));
                }
            } else {
                return ResponseEntity.ok(Map.of(
                    "status", "Database connected",
                    "message", "Users found in database",
                    "userCount", userCount
                ));
            }
        } catch (Exception e) {
            logger.error("Database status check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Database status check failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            String username = loginRequest.getUsername();
            String password = loginRequest.getPassword();
            
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
            logger.warn("Invalid credentials for user: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password"));
        } catch (Exception e) {
            logger.error("Login failed for user {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    /**
     * Handle form data login (for backward compatibility)
     */
    @PostMapping(value = "/login", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> loginForm(@RequestParam String username, @RequestParam String password) {
        try {
            logger.info("Form login attempt for username: {}", username);
            
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
            logger.error("Login failed for user {}: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    /**
     * Handle CORS preflight request for login endpoint
     */
    @RequestMapping(value = "/login", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> loginOptions() {
        logger.info("Handling OPTIONS request for /auth/login");
        return ResponseEntity.ok()
            .header("Access-Control-Allow-Origin", "http://localhost:5173")
            .header("Access-Control-Allow-Methods", "POST, OPTIONS")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
            .header("Access-Control-Allow-Credentials", "true")
            .build();
    }

    @PostMapping(value = "/register", consumes = "application/x-www-form-urlencoded")
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

    /**
     * Request DTO for login
     */
    public static class LoginRequest {
        private String username;
        private String password;

        // Default constructor
        public LoginRequest() {}

        // Constructor with parameters
        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        // Getters and setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}