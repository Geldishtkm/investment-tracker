package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.VaRCalculation;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.VaRCalculationService;
import com.portfolio.tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Value at Risk (VaR) calculations
 * Provides endpoints for portfolio risk analysis
 */
@RestController
@RequestMapping("/api/var")
@CrossOrigin(origins = "*")
public class VaRController {

    @Autowired
    private VaRCalculationService varCalculationService;

    @Autowired
    private UserService userService;

    /**
     * Calculate comprehensive VaR for the authenticated user's portfolio
     */
    @PostMapping("/calculate")
    public ResponseEntity<?> calculatePortfolioVaR(
            @RequestParam(defaultValue = "0.95") double confidenceLevel,
            @RequestParam(defaultValue = "1") int timeHorizon) {
        
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            // Validate parameters
            if (confidenceLevel < 0.5 || confidenceLevel > 0.999) {
                return ResponseEntity.badRequest().body(Map.of("error", "Confidence level must be between 0.5 and 0.999"));
            }
            
            if (timeHorizon < 1 || timeHorizon > 365) {
                return ResponseEntity.badRequest().body(Map.of("error", "Time horizon must be between 1 and 365 days"));
            }

            // Calculate VaR
            VaRCalculation varCalculation = varCalculationService.calculatePortfolioVaR(user, confidenceLevel, timeHorizon);
            
            // Get summary for response
            Map<String, Object> summary = varCalculationService.getVaRSummary(varCalculation);
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to calculate VaR: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get VaR calculation with custom parameters
     */
    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolioVaR(
            @RequestParam(defaultValue = "0.95") double confidenceLevel,
            @RequestParam(defaultValue = "1") int timeHorizon) {
        
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            // Calculate VaR
            VaRCalculation varCalculation = varCalculationService.calculatePortfolioVaR(user, confidenceLevel, timeHorizon);
            
            // Return detailed VaR calculation
            return ResponseEntity.ok(varCalculation);
            
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get VaR: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get VaR summary for different confidence levels
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getVaRSummary() {
        
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            // Calculate VaR for different confidence levels
            Map<String, Object> summary = new HashMap<>();
            
            // 95% confidence level (1 day)
            VaRCalculation var95 = varCalculationService.calculatePortfolioVaR(user, 0.95, 1);
            summary.put("var95_1day", varCalculationService.getVaRSummary(var95));
            
            // 99% confidence level (1 day)
            VaRCalculation var99 = varCalculationService.calculatePortfolioVaR(user, 0.99, 1);
            summary.put("var99_1day", varCalculationService.getVaRSummary(var99));
            
            // 95% confidence level (10 days)
            VaRCalculation var95_10day = varCalculationService.calculatePortfolioVaR(user, 0.95, 10);
            summary.put("var95_10day", varCalculationService.getVaRSummary(var95_10day));
            
            // 99% confidence level (10 days)
            VaRCalculation var99_10day = varCalculationService.calculatePortfolioVaR(user, 0.99, 10);
            summary.put("var99_10day", varCalculationService.getVaRSummary(var99_10day));
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get VaR summary: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get risk metrics breakdown
     */
    @GetMapping("/risk-metrics")
    public ResponseEntity<?> getRiskMetrics() {
        
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            // Calculate VaR with default parameters
            VaRCalculation varCalculation = varCalculationService.calculatePortfolioVaR(user, 0.95, 1);
            
            // Extract risk metrics
            Map<String, Object> riskMetrics = new HashMap<>();
            riskMetrics.put("volatility", varCalculation.getVolatility());
            riskMetrics.put("skewness", varCalculation.getSkewness());
            riskMetrics.put("kurtosis", varCalculation.getKurtosis());
            riskMetrics.put("expectedReturn", varCalculation.getExpectedReturn());
            riskMetrics.put("portfolioValue", varCalculation.getPortfolioValue());
            riskMetrics.put("assetWeights", varCalculation.getAssetWeights());
            
            return ResponseEntity.ok(riskMetrics);
            
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get risk metrics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Compare VaR methodologies
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareVaRMethodologies() {
        
        try {
            // Get authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            // Calculate VaR using different methodologies
            VaRCalculation varCalculation = varCalculationService.calculatePortfolioVaR(user, 0.95, 1);
            
            // Create comparison
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("portfolioValue", varCalculation.getPortfolioValue());
            comparison.put("confidenceLevel", varCalculation.getConfidenceLevel());
            comparison.put("timeHorizon", varCalculation.getTimeHorizon());
            
            Map<String, Object> varComparison = new HashMap<>();
            varComparison.put("historical", Map.of(
                "value", varCalculation.getHistoricalVaR(),
                "percentage", (varCalculation.getHistoricalVaR() / varCalculation.getPortfolioValue()) * 100,
                "methodology", "Historical Simulation"
            ));
            
            varComparison.put("parametric", Map.of(
                "value", varCalculation.getParametricVaR(),
                "percentage", (varCalculation.getParametricVaR() / varCalculation.getPortfolioValue()) * 100,
                "methodology", "Parametric (Normal Distribution)"
            ));
            
            varComparison.put("monteCarlo", Map.of(
                "value", varCalculation.getMonteCarloVaR(),
                "percentage", (varCalculation.getMonteCarloVaR() / varCalculation.getPortfolioValue()) * 100,
                "methodology", "Monte Carlo Simulation"
            ));
            
            varComparison.put("conditional", Map.of(
                "value", varCalculation.getConditionalVaR(),
                "percentage", (varCalculation.getConditionalVaR() / varCalculation.getPortfolioValue()) * 100,
                "methodology", "Conditional VaR (Expected Shortfall)"
            ));
            
            comparison.put("varComparison", varComparison);
            
            return ResponseEntity.ok(comparison);
            
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to compare VaR methodologies: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "VaR Calculation Service");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
