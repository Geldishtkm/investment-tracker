package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.service.RiskMetricsService;
import com.portfolio.tracker.service.AssetPriceRefreshService;
import com.portfolio.tracker.service.TradeService;
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
    private final RiskMetricsService riskMetricsService;
    private final AssetPriceRefreshService priceRefreshService;
    private final TradeService tradeService;

    @Autowired
    public AssetController(AssetService assetService, UserService userService,
                           RiskMetricsService riskMetricsService, AssetPriceRefreshService priceRefreshService,
                           TradeService tradeService) {
        this.assetService = assetService;
        this.userService = userService;
        this.riskMetricsService = riskMetricsService;
        this.priceRefreshService = priceRefreshService;
        this.tradeService = tradeService;
    }

    /**
     * Update the current price of all the user's assets with one batched CoinGecko request.
     * Cached prices are used when fresh, and last known prices if CoinGecko is rate limiting.
     */
    @PostMapping("/refresh-prices")
    public ResponseEntity<?> refreshPrices(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        AssetPriceRefreshService.Result result = priceRefreshService.refreshPrices(user);
        return ResponseEntity.ok(Map.of(
            "updated", result.updated(),
            "unpriced", result.unpriced()
        ));
    }

    /**
     * Health check endpoint (requires authentication)
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            // Count only the caller's assets; other users' data is not exposed
            User user = userService.findByUsername(authentication.getName());
            List<Asset> assets = assetService.getAssetsByUser(user);
            return ResponseEntity.ok("Database connection OK. Your assets: " + assets.size());
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
            
            String invalid = validateAsset(asset);
            if (invalid != null) {
                return ResponseEntity.badRequest().body(Map.of("error", invalid));
            }
            try {
                TradeService.validateDate(asset.getPurchaseDate());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
            
            Asset savedAsset = assetService.saveAsset(asset);
            // The holding's opening buy goes into the trade ledger for the behavioral analytics
            tradeService.recordOpeningBuy(savedAsset, asset.getPurchaseDate());
            
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
    public ResponseEntity<?> updateAsset(@PathVariable Long id, @RequestBody Asset asset, Authentication authentication) {
        String invalid = validateAsset(asset);
        if (invalid != null) {
            return ResponseEntity.badRequest().body(Map.of("error", invalid));
        }
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
            tradeService.syncCorrectedHolding(updatedAsset);
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
            
            // ROI from cost basis; undefined when no asset has a recorded purchase cost
            Double roi = null;
            try {
                roi = assetService.calculateROIByUser(user);
            } catch (IllegalStateException | ArithmeticException e) {
                // leave ROI empty rather than reporting a made-up 0%
            }
            analytics.put("roi", roi);
            
            // Risk metrics from real price history. When they cannot be computed (no assets,
            // too little shared history) the UI gets the reason instead of placeholder numbers.
            try {
                Map<String, Object> riskMetrics = riskMetricsService.calculateDetailed(userAssets);
                riskMetrics.put("roi", roi);
                analytics.put("riskMetrics", riskMetrics);
                analytics.putAll(riskMetrics);
                analytics.put("riskMetricsAvailable", true);
            } catch (PortfolioDataException e) {
                analytics.put("riskMetrics", null);
                analytics.put("riskMetricsAvailable", false);
                analytics.put("riskMetricsMessage", e.getMessage());
                analytics.put("riskMetricsCode", e.getReason().name());
            }
            
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

    /** Reasons an asset can't be valued; null when it is valid. Shared by create and update. */
    private static String validateAsset(Asset asset) {
        if (asset.getName() == null || asset.getName().trim().isEmpty()) return "Asset name is required";
        if (!(asset.getQuantity() > 0) || Double.isInfinite(asset.getQuantity())) return "Quantity must be greater than 0";
        if (!(asset.getPricePerUnit() > 0) || Double.isInfinite(asset.getPricePerUnit())) return "Price per unit must be greater than 0";
        if (!(asset.getPurchasePricePerUnit() > 0) || Double.isInfinite(asset.getPurchasePricePerUnit())) {
            return "Purchase price per unit must be greater than 0";
        }
        return null;
    }
}
