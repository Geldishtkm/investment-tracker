package com.portfolio.tracker.controller;

import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.RiskMetrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public List<Asset> getAllAssets() {
        return assetService.getAllAssets();
    }

    @PostMapping
    public Asset createAsset(@RequestBody Asset asset) {
        return assetService.saveAsset(asset);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Asset> getAssetById(@PathVariable Long id) {
        try {
            Asset asset = assetService.getAssetById(id);
            return ResponseEntity.ok(asset);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        try {
            assetService.deleteAssetById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/roi")
    public ResponseEntity<?> getROI() {
        try {
            double roi = assetService.calculateROI();
            return ResponseEntity.ok(roi);
        } catch (IllegalStateException | ArithmeticException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/sharpe-ratio")
    public ResponseEntity<?> getSharpeRatio() {
        try {
            double sharpe = assetService.calculateSharpeRatio();
            return ResponseEntity.ok(sharpe);
        } catch (IllegalStateException | ArithmeticException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/volatility")
    public ResponseEntity<?> getVolatility() {
        try {
            double vol = assetService.calculateVolatility();
            return ResponseEntity.ok(vol);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/max-drawdown")
    public ResponseEntity<?> getMaxDrawdown() {
        try {
            double maxDD = assetService.calculateMaxDrawdown();
            return ResponseEntity.ok(maxDD);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/beta")
    public ResponseEntity<?> getBeta() {
        try {
            double beta = assetService.calculateBeta();
            return ResponseEntity.ok(beta);
        } catch (IllegalStateException | ArithmeticException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/diversification-score")
    public double getDiversificationScore() {
        return assetService.calculateDiversificationScore();
    }

    @GetMapping("/risk-metrics")
    public ResponseEntity<?> getRiskMetrics() {
        try {
            RiskMetrics metrics = assetService.getRiskMetrics();
            return ResponseEntity.ok(metrics);
        } catch (IllegalStateException | ArithmeticException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
