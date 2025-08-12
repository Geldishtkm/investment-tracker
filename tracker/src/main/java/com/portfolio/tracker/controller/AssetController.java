package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.RiskMetrics;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/assets")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Tag(name = "Asset Management", description = "APIs for managing portfolio assets and performing risk calculations")
public class AssetController {

    private final AssetService assetService;
    private final UserService userService;

    @Autowired
    public AssetController(AssetService assetService, UserService userService) {
        this.assetService = assetService;
        this.userService = userService;
    }

    /**
     * Test endpoint to verify the API is accessible without authentication
     * @return simple test message
     */
    @GetMapping("/test")
    public ResponseEntity<?> testEndpoint() {
        return ResponseEntity.ok(Map.of(
            "message", "Asset API is accessible",
            "timestamp", System.currentTimeMillis(),
            "status", "OK"
        ));
    }

    /**
     * Debug endpoint to show authentication status
     * @return authentication information
     */
    @GetMapping("/debug")
    public ResponseEntity<?> debugAuth() {
        try {
            // Get current authentication context
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return ResponseEntity.ok(Map.of(
                    "message", "User is authenticated",
                    "username", auth.getName(),
                    "authorities", auth.getAuthorities().toString(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "message", "User is NOT authenticated",
                    "authentication", auth != null ? auth.getName() : "null",
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "message", "Error checking authentication",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        try {
            List<Asset> assets = assetService.getAllAssets();
            return ResponseEntity.ok("Database connection OK. Total assets: " + assets.size());
        } catch (Exception e) {
            System.err.println("Health check failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Database connection failed: " + e.getMessage());
        }
    }

    @Operation(
        summary = "Get all assets for authenticated user",
        description = "Retrieves all portfolio assets belonging to the currently authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assets retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<?> getAllAssets(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            List<Asset> assets = assetService.getAssetsByUser(user);
            
            List<Map<String, Object>> assetList = assets.stream()
                .map(asset -> {
                    Map<String, Object> assetMap = new HashMap<>();
                    assetMap.put("id", asset.getId());
                    assetMap.put("name", asset.getName());
                    assetMap.put("quantity", asset.getQuantity());
                    assetMap.put("pricePerUnit", asset.getPricePerUnit());
                    assetMap.put("purchasePricePerUnit", asset.getPurchasePricePerUnit());
                    assetMap.put("initialInvestment", asset.getInitialInvestment());
                    return assetMap;
                })
                .toList();
            
            return ResponseEntity.ok(assetList);
        } catch (Exception e) {
            System.err.println("Error getting assets: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get assets: " + e.getMessage()));
        }
    }

    @Operation(
        summary = "Create a new asset",
        description = "Creates a new portfolio asset for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Asset created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid asset data"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<?> createAsset(@RequestBody Asset asset, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            asset.setUser(user);
            
            // Basic validation
            if (asset.getName() == null || asset.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Asset name is required");
            }
            if (asset.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body("Quantity must be greater than 0");
            }
            if (asset.getPricePerUnit() <= 0) {
                return ResponseEntity.badRequest().body("Price per unit must be greater than 0");
            }
            if (asset.getPurchasePricePerUnit() <= 0) {
                return ResponseEntity.badRequest().body("Purchase price per unit must be greater than 0");
            }
            
            Asset savedAsset = assetService.saveAsset(asset);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", savedAsset.getId(),
                "name", savedAsset.getName(),
                "quantity", savedAsset.getQuantity(),
                "pricePerUnit", savedAsset.getPricePerUnit(),
                "purchasePricePerUnit", savedAsset.getPurchasePricePerUnit(),
                "initialInvestment", savedAsset.getInitialInvestment(),
                "message", "Asset created successfully"
            ));
        } catch (Exception e) {
            System.err.println("Error creating asset: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create asset: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Asset> getAssetById(@PathVariable Long id, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            Asset asset = assetService.getAssetByIdAndUser(id, user);
            return ResponseEntity.ok(asset);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Asset> updateAsset(@PathVariable Long id, @RequestBody Asset asset, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            Asset existingAsset = assetService.getAssetByIdAndUser(id, user);
            
            existingAsset.setName(asset.getName());
            existingAsset.setQuantity(asset.getQuantity());
            existingAsset.setPricePerUnit(asset.getPricePerUnit());
            existingAsset.setPurchasePricePerUnit(asset.getPurchasePricePerUnit());
            existingAsset.setInitialInvestment(asset.getInitialInvestment());
            
            Asset updatedAsset = assetService.saveAsset(existingAsset);
            return ResponseEntity.ok(updatedAsset);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            assetService.deleteAssetByIdAndUser(id, user);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            Map<String, Object> analytics = new HashMap<>();
            
            List<Asset> userAssets = assetService.getAssetsByUser(user);
            double totalValue = assetService.getTotalValueByUser(user);
            double totalInitialInvestment = userAssets.stream()
                .mapToDouble(Asset::getInitialInvestment)
                .sum();
            
            analytics.put("totalValue", totalValue);
            analytics.put("totalInitialInvestment", totalInitialInvestment);
            analytics.put("totalAssets", userAssets.size());
            
            // Try to get risk metrics, fallback to defaults if calculation fails
            try {
                RiskMetrics riskMetrics = assetService.getRiskMetricsByUser(user);
                analytics.put("riskMetrics", riskMetrics);
            } catch (Exception e) {
                Map<String, Object> fallbackMetrics = new HashMap<>();
                fallbackMetrics.put("roi", 0.0);
                fallbackMetrics.put("sharpeRatio", 1.0);
                fallbackMetrics.put("volatility", 0.0);
                fallbackMetrics.put("maxDrawdown", 0.0);
                fallbackMetrics.put("beta", 1.0);
                fallbackMetrics.put("diversificationScore", 0.0);
                analytics.put("riskMetrics", fallbackMetrics);
            }
            
            // Calculate individual metrics with fallbacks
            analytics.put("roi", safeCalculate(() -> assetService.calculateROIByUser(user), 0.0));
            analytics.put("sharpeRatio", safeCalculate(() -> assetService.calculateSharpeRatioByUser(user), 1.0));
            analytics.put("volatility", safeCalculate(() -> assetService.calculateVolatilityByUser(user), 0.0));
            analytics.put("maxDrawdown", safeCalculate(() -> assetService.calculateMaxDrawdownByUser(user), 0.0));
            analytics.put("beta", safeCalculate(() -> assetService.calculateBetaByUser(user), 1.0));
            analytics.put("diversificationScore", safeCalculate(() -> assetService.calculateDiversificationScoreByUser(user), 0.0));
            
            // Build asset breakdown
            List<Map<String, Object>> assetBreakdown = userAssets.stream()
                .map(asset -> {
                    Map<String, Object> assetData = new HashMap<>();
                    assetData.put("id", asset.getId());
                    assetData.put("name", asset.getName());
                    assetData.put("quantity", asset.getQuantity());
                    assetData.put("currentPrice", asset.getPricePerUnit());
                    assetData.put("purchasePrice", asset.getPurchasePricePerUnit());
                    assetData.put("currentValue", asset.getQuantity() * asset.getPricePerUnit());
                    assetData.put("initialInvestment", asset.getInitialInvestment());
                    
                    double individualRoi = asset.getInitialInvestment() > 0 ? 
                        ((asset.getQuantity() * asset.getPricePerUnit() - asset.getInitialInvestment()) / asset.getInitialInvestment()) * 100 : 0.0;
                    assetData.put("individualRoi", individualRoi);
                    
                    return assetData;
                })
                .toList();
            
            analytics.put("assetBreakdown", assetBreakdown);
            
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            System.err.println("Error getting analytics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to load analytics data: " + e.getMessage()));
        }
    }

    @GetMapping("/portfolio-summary")
    public ResponseEntity<?> getPortfolioSummary(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            Map<String, Object> summary = new HashMap<>();
            
            List<Asset> userAssets = assetService.getAssetsByUser(user);
            double totalValue = assetService.getTotalValueByUser(user);
            double totalInitialInvestment = userAssets.stream()
                .mapToDouble(Asset::getInitialInvestment)
                .sum();
            
            double totalGain = totalValue - totalInitialInvestment;
            double totalGainPercentage = totalInitialInvestment > 0 ? 
                (totalGain / totalInitialInvestment) * 100 : 0.0;
            
            summary.put("totalValue", totalValue);
            summary.put("totalInitialInvestment", totalInitialInvestment);
            summary.put("totalAssets", userAssets.size());
            summary.put("totalGain", totalGain);
            summary.put("totalGainPercentage", totalGainPercentage);
            
            // Build asset list with gains
            List<Map<String, Object>> assets = userAssets.stream()
                .map(asset -> {
                    Map<String, Object> assetData = new HashMap<>();
                    assetData.put("id", asset.getId());
                    assetData.put("name", asset.getName());
                    assetData.put("quantity", asset.getQuantity());
                    assetData.put("currentPrice", asset.getPricePerUnit());
                    assetData.put("purchasePrice", asset.getPurchasePricePerUnit());
                    assetData.put("currentValue", asset.getQuantity() * asset.getPricePerUnit());
                    assetData.put("initialInvestment", asset.getInitialInvestment());
                    
                    double gain = (asset.getQuantity() * asset.getPricePerUnit()) - asset.getInitialInvestment();
                    double gainPercentage = asset.getInitialInvestment() > 0 ? 
                        (gain / asset.getInitialInvestment()) * 100 : 0.0;
                    
                    assetData.put("gain", gain);
                    assetData.put("gainPercentage", gainPercentage);
                    
                    return assetData;
                })
                .toList();
            
            summary.put("assets", assets);
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to load portfolio summary: " + e.getMessage()));
        }
    }

    @GetMapping("/with-prices")
    public ResponseEntity<?> getAllAssetsWithPrices(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            List<Asset> userAssets = assetService.getAssetsByUser(user);
            
            List<Map<String, Object>> assetsWithPrices = userAssets.stream()
                .map(asset -> {
                    Map<String, Object> assetData = new HashMap<>();
                    assetData.put("id", asset.getId());
                    assetData.put("name", asset.getName());
                    assetData.put("quantity", asset.getQuantity());
                    assetData.put("currentPrice", asset.getPricePerUnit());
                    assetData.put("purchasePricePerUnit", asset.getPurchasePricePerUnit());
                    assetData.put("pricePerUnit", asset.getPricePerUnit());
                    assetData.put("initialInvestment", asset.getInitialInvestment());
                    assetData.put("totalValue", asset.getQuantity() * asset.getPricePerUnit());
                    
                    double profitLoss = asset.getInitialInvestment() > 0 ? 
                        (asset.getQuantity() * asset.getPricePerUnit()) - asset.getInitialInvestment() : 0.0;
                    assetData.put("profitLoss", profitLoss);
                    
                    return assetData;
                })
                .toList();
            
            return ResponseEntity.ok(assetsWithPrices);
            
        } catch (Exception e) {
            System.err.println("Error getting assets with prices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get assets with prices: " + e.getMessage()));
        }
    }
    
    // Helper method to safely calculate metrics with fallback values
    private double safeCalculate(CalculationSupplier supplier, double fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return fallback;
        }
    }
    
    @FunctionalInterface
    private interface CalculationSupplier {
        double get() throws Exception;
    }
}