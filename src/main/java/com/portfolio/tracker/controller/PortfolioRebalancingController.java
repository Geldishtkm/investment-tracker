package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.PortfolioRebalancing;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.PortfolioRebalancingService;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Portfolio Rebalancing operations
 * Provides endpoints for Mean-Variance Optimization and Black-Litterman model
 */
@RestController
@RequestMapping("/api/portfolio-rebalancing")
@CrossOrigin(origins = "*")
public class PortfolioRebalancingController {

    @Autowired
    private PortfolioRebalancingService rebalancingService;

    @Autowired
    private UserService userService;

    /**
     * Calculate optimal portfolio allocation using Mean-Variance Optimization
     */
    @PostMapping("/optimize")
    public ResponseEntity<PortfolioRebalancing> optimizePortfolio(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }
            
            String username = authentication.getName();
            Double riskTolerance = (Double) request.get("riskTolerance");
            
            if (riskTolerance == null) {
                return ResponseEntity.badRequest().build();
            }
            
            // Get the authenticated user from the database
            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).build();
            }
            
            PortfolioRebalancing result = rebalancingService.calculateOptimalAllocation(user, riskTolerance);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Calculate optimal allocation using Black-Litterman model
     */
    @PostMapping("/black-litterman")
    public ResponseEntity<PortfolioRebalancing> blackLittermanOptimization(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }
            
            String username = authentication.getName();
            Double riskTolerance = (Double) request.get("riskTolerance");
            @SuppressWarnings("unchecked")
            Map<String, Double> userViews = (Map<String, Double>) request.get("userViews");
            
            if (riskTolerance == null || userViews == null) {
                return ResponseEntity.badRequest().build();
            }
            
            // Get the authenticated user from the database
            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).build();
            }
            
            PortfolioRebalancing result = rebalancingService.calculateBlackLittermanAllocation(
                user, riskTolerance, userViews);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if portfolio needs rebalancing
     */
    @GetMapping("/needs-rebalancing/{username}")
    public ResponseEntity<Map<String, Object>> checkRebalancingNeeded(
            @PathVariable String username,
            @RequestParam(defaultValue = "0.05") double threshold,
            Authentication authentication) {
        
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }
            
            // Verify the authenticated user is requesting their own data
            String authenticatedUsername = authentication.getName();
            if (!authenticatedUsername.equals(username)) {
                return ResponseEntity.status(403).build(); // Forbidden - can't access other user's data
            }
            
            // Get the authenticated user from the database
            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).build();
            }
            
            boolean needsRebalancing = rebalancingService.needsRebalancing(user, threshold);
            
            Map<String, Object> response = Map.of(
                "needsRebalancing", needsRebalancing,
                "threshold", threshold,
                "username", username
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get rebalancing summary for dashboard
     */
    @GetMapping("/summary/{username}")
    public ResponseEntity<Map<String, Object>> getRebalancingSummary(
            @PathVariable String username) {
        
        try {
            // Create a mock user for now (in production, get from authentication)
            User user = new User();
            user.setUsername(username);
            
            Map<String, Object> summary = rebalancingService.getRebalancingSummary(user);
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of("error", e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get current portfolio allocation
     */
    @GetMapping("/allocation/{username}")
    public ResponseEntity<Map<String, Object>> getCurrentAllocation(
            @PathVariable String username) {
        
        try {
            // Create a mock user for now (in production, get from authentication)
            User user = new User();
            user.setUsername(username);
            
            // For now, return a simple response
            // In production, this would calculate actual allocation
            Map<String, Object> response = Map.of(
                "username", username,
                "message", "Allocation calculation endpoint - implement with actual asset data"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = Map.of(
            "status", "UP",
            "service", "Portfolio Rebalancing Service",
            "message", "Mean-Variance Optimization and Black-Litterman model ready"
        );
        return ResponseEntity.ok(response);
    }
}
