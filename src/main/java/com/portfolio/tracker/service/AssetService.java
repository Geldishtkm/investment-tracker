package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    @Autowired
    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public Asset saveAsset(Asset asset) {
        // Calculate initial investment automatically
        asset.setInitialInvestment(asset.getQuantity() * asset.getPurchasePricePerUnit());
        return assetRepository.save(asset);
    }

    public double getTotalValueByUser(User user) {
        return getAssetsByUser(user)
                .stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
    }

    public List<Asset> getAssetsByUser(User user) {
        return assetRepository.findByUser(user);
    }

    public Asset getAssetByIdAndUser(Long id, User user) {
        return assetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: " + id));
    }

    @Transactional
    public void deleteAssetByIdAndUser(Long id, User user) {
        Asset asset = getAssetByIdAndUser(id, user);
        // User.assets cascades persist, so an asset still in its owner's collection would be
        // re-saved at flush and the delete silently dropped; detach it from the owner first
        if (asset.getUser() != null) {
            asset.getUser().getAssets().remove(asset);
        }
        assetRepository.delete(asset);
    }

    public double calculateROIByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        List<Asset> validAssets = userAssets.stream()
                .filter(asset -> asset.getInitialInvestment() > 0)
                .toList();

        if (validAssets.isEmpty()) {
            throw new IllegalStateException("No assets with a valid initial investment for user.");
        }

        double totalInitialInvestment = validAssets.stream()
                .mapToDouble(Asset::getInitialInvestment)
                .sum();

        double totalCurrentValue = validAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();

        if (totalInitialInvestment == 0) {
            throw new ArithmeticException("Total initial investment is zero");
        }

        return ((totalCurrentValue - totalInitialInvestment) / totalInitialInvestment) * 100;
    }
}
