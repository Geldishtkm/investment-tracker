package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.service.CryptoPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assets")
@CrossOrigin(origins = "http://localhost:5173")
public class AssetController {

    private final AssetService assetService;
    private final CryptoPriceService cryptoPriceService;

    @Autowired
    public AssetController(AssetService assetService, CryptoPriceService cryptoPriceService) {
        this.assetService = assetService;
        this.cryptoPriceService = cryptoPriceService;
    }

    //Add new asset
    @PostMapping
    public Asset addAsset(@RequestBody Asset asset) {
        return assetService.saveAsset(asset);
    }

    //  Get all assets
    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    // Get total portfolio value
    @GetMapping("/total")
    public double getTotalValue() {
        return assetService.calculateTotalValue();
    }

    // Update one asset’s price
    @PutMapping("/{id}/update-price")
    public Asset updateAssetWithCurrentPrice(@PathVariable Long id) {
        Asset asset = assetService.getAssetById(id);
        if (asset != null) {
            String coinId = asset.getName().toLowerCase().replaceAll("\\s+", "");
            double currentPrice = cryptoPriceService.getCryptoPriceInUSD(coinId);
            asset.setPricePerUnit(currentPrice);
            return assetService.saveAsset(asset);
        }
        throw new RuntimeException("Asset not found with id: " + id);
    }
        @DeleteMapping("/{id}")  // delete
        public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAssetById(id);
        return ResponseEntity.noContent().build();
    }
    // ✅ Get all assets with current crypto prices
    @GetMapping("/with-prices")
    public List<Map<String, Object>> getAllAssetsWithPrices() {
        List<Asset> assets = assetService.getAllAssets();
        return assets.stream().map(asset -> {
            Map<String, Object> assetWithPrice = new HashMap<>();
            assetWithPrice.put("id", asset.getId());
            assetWithPrice.put("name", asset.getName());
            assetWithPrice.put("quantity", asset.getQuantity());
            assetWithPrice.put("pricePerUnit", asset.getPricePerUnit());

            try {
                String coinId = asset.getName().toLowerCase().replaceAll("\\s+", "");
                double currentPrice = cryptoPriceService.getCryptoPriceInUSD(coinId);
                assetWithPrice.put("currentPrice", currentPrice);
            } catch (Exception e) {
                assetWithPrice.put("currentPrice", null);
            }

            return assetWithPrice;
        }).collect(Collectors.toList());
    }
}
