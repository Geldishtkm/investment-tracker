package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.RiskMetrics;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.UserService;

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
public class AssetController {

    private final AssetService assetService;
    private final UserService userService;

    @Autowired
    public AssetController(AssetService assetService, UserService userService) {
        this.assetService = assetService;
        this.userService = userService;
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Asset controller is working!");
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        try {
            // Try to access the database
            List<Asset> assets = assetService.getAllAssets();
            return ResponseEntity.ok("Database connection OK. Total assets: " + assets.size());
        } catch (Exception e) {
            System.err.println("Health check failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Database connection failed: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllAssets(Authentication authentication) {
        try {
            // Get current user and return only their assets
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            List<Asset> assets = assetService.getAssetsByUser(user);
            
            // Convert to simple objects to avoid serialization issues
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
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get assets: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createAsset(@RequestBody Asset asset, Authentication authentication) {
        try {
            // Get current user and associate asset with them
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            asset.setUser(user);
            
            // Validate required fields
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
            
            // Create a simple response object to avoid serialization issues
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
            e.printStackTrace();
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
            
            // Update asset properties but keep the user relationship
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

    @GetMapping("/roi")
    public ResponseEntity<?> getROI(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            double roi = assetService.calculateROIByUser(user);
            return ResponseEntity.ok(roi);
        } catch (IllegalStateException | ArithmeticException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/sharpe-ratio")
    public ResponseEntity<?> getSharpeRatio(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            double sharpe = assetService.calculateSharpeRatioByUser(user);
            return ResponseEntity.ok(sharpe);
        } catch (IllegalStateException | ArithmeticException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/volatility")
    public ResponseEntity<?> getVolatility(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            double vol = assetService.calculateVolatilityByUser(user);
            return ResponseEntity.ok(vol);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/max-drawdown")
    public ResponseEntity<?> getMaxDrawdown(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            double maxDD = assetService.calculateMaxDrawdownByUser(user);
            return ResponseEntity.ok(maxDD);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/beta")
    public ResponseEntity<?> getBeta(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            double beta = assetService.calculateBetaByUser(user);
            return ResponseEntity.ok(beta);
        } catch (IllegalStateException | ArithmeticException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/diversification-score")
    public double getDiversificationScore(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        return assetService.calculateDiversificationScoreByUser(user);
    }

    @GetMapping("/risk-metrics")
    public ResponseEntity<?> getRiskMetrics(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            RiskMetrics metrics = assetService.getRiskMetricsByUser(user);
            return ResponseEntity.ok(metrics);
        } catch (IllegalStateException | ArithmeticException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}