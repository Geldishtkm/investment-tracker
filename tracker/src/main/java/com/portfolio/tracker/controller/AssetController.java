package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.service.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    @Autowired
    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    // ✅ Add new asset (POST)
    @PostMapping
    public Asset addAsset(@RequestBody Asset asset) {
    return assetService.saveAsset(asset);
}

    // ✅ Get all assets (GET)
    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    // ✅ Get total value of portfolio (GET)
    @GetMapping("/total")
    public double getTotalValue() {
        return assetService.calculateTotalValue();
    }
}
