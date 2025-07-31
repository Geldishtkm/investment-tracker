package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.repository.AssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    @Autowired
    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll(); // Gets all assets from database
    }

    public Asset saveAsset(Asset asset) {
        return assetRepository.save(asset); // Saves asset to database
    }

    public double calculateTotalValue() {
        return assetRepository.findAll()
                .stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit()) // Calculate total value
                .sum(); // Adds up all asset total values
    }
} 