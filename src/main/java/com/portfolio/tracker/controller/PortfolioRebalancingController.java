package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.PortfolioRebalancing;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.PortfolioRebalancingService;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Portfolio Rebalancing operations
 * Provides endpoints for Mean-Variance Optimization and Black-Litterman model.
 * Every endpoint works on the authenticated user's own portfolio. Errors caused by the
 * portfolio's contents (no assets, too little history) are returned as HTTP 422 by
 * AnalyticsExceptionHandler.
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
    public ResponseEntity<?> optimizePortfolio(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        Double riskTolerance = riskTolerance(request.get("riskTolerance"));
        if (riskTolerance == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "riskTolerance must be a number between 0 and 1"));
        }

        PortfolioRebalancing result = rebalancingService.calculateOptimalAllocation(user, riskTolerance);
        return ResponseEntity.ok(result);
    }

    /**
     * Calculate optimal allocation using Black-Litterman model
     */
    @PostMapping("/black-litterman")
    public ResponseEntity<?> blackLittermanOptimization(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        Double riskTolerance = riskTolerance(request.get("riskTolerance"));
        if (riskTolerance == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "riskTolerance must be a number between 0 and 1"));
        }

        Map<String, Double> userViews = new HashMap<>();
        if (request.get("userViews") instanceof Map<?, ?> views) {
            for (Map.Entry<?, ?> view : views.entrySet()) {
                if (!(view.getValue() instanceof Number value)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "View for " + view.getKey() + " must be a number"));
                }
                userViews.put(String.valueOf(view.getKey()), value.doubleValue());
            }
        }

        PortfolioRebalancing result = rebalancingService.calculateBlackLittermanAllocation(
            user, riskTolerance, userViews);
        return ResponseEntity.ok(result);
    }

    /**
     * Check if portfolio needs rebalancing
     */
    @GetMapping("/needs-rebalancing/{username}")
    public ResponseEntity<?> checkRebalancingNeeded(
            @PathVariable String username,
            @RequestParam(defaultValue = "0.05") double threshold,
            @RequestParam(defaultValue = "0.5") double riskTolerance,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (!user.getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only access your own portfolio"));
        }

        boolean needsRebalancing = rebalancingService.needsRebalancing(user, threshold, riskTolerance);
        return ResponseEntity.ok(Map.of(
            "needsRebalancing", needsRebalancing,
            "threshold", threshold,
            "riskTolerance", riskTolerance,
            "username", username
        ));
    }

    /**
     * Get rebalancing summary for dashboard
     */
    @GetMapping("/summary/{username}")
    public ResponseEntity<?> getRebalancingSummary(
            @PathVariable String username,
            @RequestParam(defaultValue = "0.05") double threshold,
            @RequestParam(defaultValue = "0.5") double riskTolerance,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (!user.getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only access your own portfolio"));
        }
        return ResponseEntity.ok(rebalancingService.getRebalancingSummary(user, riskTolerance, threshold));
    }

    /**
     * Get current portfolio allocation
     */
    @GetMapping("/allocation/{username}")
    public ResponseEntity<?> getCurrentAllocation(
            @PathVariable String username,
            Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        if (!user.getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only access your own portfolio"));
        }
        return ResponseEntity.ok(Map.of(
            "username", username,
            "allocation", rebalancingService.getCurrentAllocation(user)
        ));
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

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return userService.findByUsername(authentication.getName());
    }

    /** Accepts any JSON number (0.5 or 1), returns null if missing or outside [0, 1]. */
    private Double riskTolerance(Object value) {
        if (!(value instanceof Number number)) return null;
        double v = number.doubleValue();
        return v >= 0 && v <= 1 ? v : null;
    }
}
