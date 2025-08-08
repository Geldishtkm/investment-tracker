package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AssetRepository;
import com.portfolio.tracker.model.RiskMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    @Autowired
    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset saveAsset(Asset asset) {
        try {
            System.out.println("Saving asset: " + asset.getName());
            System.out.println("Asset user: " + (asset.getUser() != null ? asset.getUser().getUsername() : "null"));
            
            // initialInvestment based on purchasePricePerUnit
            asset.setInitialInvestment(asset.getQuantity() * asset.getPurchasePricePerUnit());
            System.out.println("Calculated initial investment: " + asset.getInitialInvestment());
            
            Asset savedAsset = assetRepository.save(asset);
            System.out.println("Asset saved successfully with ID: " + savedAsset.getId());
            return savedAsset;
        } catch (Exception e) {
            System.err.println("Error saving asset: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void deleteAssetById(Long id) {
        Optional<Asset> assetOptional = assetRepository.findById(id);
        if (assetOptional.isPresent()) {
            assetRepository.delete(assetOptional.get());
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: " + id);
        }
    }

    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: " + id));
    }

    public double calculateTotalValue() {
        return assetRepository.findAll()
                .stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
    }

    public double calculateROI() {
        List<Asset> assets = assetRepository.findAll();

        List<Asset> validAssets = assets.stream()
                .filter(asset -> asset.getInitialInvestment() > 0)
                .toList();

        if (validAssets.isEmpty()) {
            throw new IllegalStateException("No assets with a valid initial investment.");
        }

        double totalInitialInvestment = validAssets.stream()
                .mapToDouble(Asset::getInitialInvestment)
                .sum();

        double totalCurrentValue = validAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();

        return (totalCurrentValue - totalInitialInvestment) / totalInitialInvestment;
    }

    // Real data fetching stub - replace with your real data logic
    public List<Double> getPortfolioReturns() {
        // Example: fetch historical returns from DB or an external API
        List<Double> returns = fetchHistoricalReturnsFromDB();
        if (returns == null || returns.isEmpty()) {
            return Collections.emptyList();
        }
        return returns;
    }

    // Stub method - implement real data retrieval here
    private List<Double> fetchHistoricalReturnsFromDB() {
        // TODO: Replace this with real fetching logic
        return Collections.emptyList();
    }

    public double calculateSharpeRatio() {
        List<Double> returns = getPortfolioReturns();

        if (returns.isEmpty()) {
            throw new IllegalStateException("No historical return data available to calculate Sharpe Ratio.");
        }

        double riskFreeRate = 0.04;
        double avgReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = calculateStandardDeviation(returns);

        if (stdDev == 0) {
            throw new ArithmeticException("Standard deviation is zero, Sharpe ratio is undefined");
        }

        return (avgReturn - riskFreeRate) / stdDev;
    }

    public double calculateVolatility() {
        List<Double> returns = getPortfolioReturns();

        if (returns.isEmpty()) {
            throw new IllegalStateException("No historical return data available to calculate Volatility.");
        }

        return calculateStandardDeviation(returns);
    }

    public double calculateMaxDrawdown() {
        List<Double> portfolioValues = getPortfolioValues();

        if (portfolioValues.isEmpty()) {
            throw new IllegalStateException("No historical portfolio value data available to calculate Max Drawdown.");
        }

        double peak = portfolioValues.get(0);
        double maxDrawdown = 0.0;

        for (double value : portfolioValues) {
            if (value > peak) peak = value;
            double drawdown = (peak - value) / peak;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
        }

        return maxDrawdown;
    }

    // Stub method - implement real data retrieval here
    private List<Double> getPortfolioValues() {
        // TODO: Replace with actual portfolio historical value retrieval
        return Collections.emptyList();
    }

    public double calculateBeta() {
        List<Double> assetReturns = getAssetReturns();
        List<Double> marketReturns = getMarketReturns();

        if (assetReturns.isEmpty() || marketReturns.isEmpty()) {
            throw new IllegalStateException("No historical data available to calculate Beta.");
        }

        if (assetReturns.size() != marketReturns.size()) {
            throw new IllegalArgumentException("Asset and market return sizes do not match.");
        }

        double avgAsset = assetReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgMarket = marketReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double covariance = 0.0;
        double variance = 0.0;

        for (int i = 0; i < assetReturns.size(); i++) {
            double assetDiff = assetReturns.get(i) - avgAsset;
            double marketDiff = marketReturns.get(i) - avgMarket;

            covariance += assetDiff * marketDiff;
            variance += marketDiff * marketDiff;
        }

        if (variance == 0) {
            throw new ArithmeticException("Market variance is zero, can't calculate Beta.");
        }

        return covariance / variance;
    }

    // Stub methods - replace with actual data retrieval logic
    private List<Double> getAssetReturns() {
        // TODO: fetch asset returns history
        return Collections.emptyList();
    }

    private List<Double> getMarketReturns() {
        // TODO: fetch market returns history
        return Collections.emptyList();
    }

    public double calculateDiversificationScore() {
        List<Asset> assets = assetRepository.findAll();
        Set<String> uniqueAssets = new HashSet<>();

        for (Asset asset : assets) {
            uniqueAssets.add(asset.getName().toLowerCase());
        }

        int totalAssets = assets.size();
        int uniqueCount = uniqueAssets.size();

        if (totalAssets == 0) return 0;

        double diversityRatio = (double) uniqueCount / totalAssets;
        return Math.round(diversityRatio * 10); // Score from 0 to 10
    }

    private double calculateStandardDeviation(List<Double> returns) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    public RiskMetrics getRiskMetrics() {
        double volatility = calculateVolatility();
        double maxDrawdown = calculateMaxDrawdown();
        double beta = calculateBeta();
        double diversificationScore = calculateDiversificationScore();

        return new RiskMetrics(volatility, maxDrawdown, beta, diversificationScore);
    }

    // ========== USER-SPECIFIC METHODS ==========

    public List<Asset> getAssetsByUser(User user) {
        return assetRepository.findByUser(user);
    }

    public Asset getAssetByIdAndUser(Long id, User user) {
        return assetRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: " + id));
    }

    public void deleteAssetByIdAndUser(Long id, User user) {
        Asset asset = getAssetByIdAndUser(id, user);
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

    public double calculateSharpeRatioByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        try {
            double roi = calculateROIByUser(user);
            double riskFreeRate = 2.0; // Assume 2% risk-free rate
            double volatility = calculateVolatilityByUser(user);
            
            if (volatility == 0) {
                throw new ArithmeticException("Volatility is zero");
            }
            
            return (roi - riskFreeRate) / volatility;
        } catch (Exception e) {
            // Fallback to simplified calculation
            return 1.0; // Default Sharpe ratio
        }
    }

    public double calculateVolatilityByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        // Simplified volatility calculation based on current portfolio value
        double totalValue = userAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
        
        // Assume 15% volatility for simplicity
        return totalValue * 0.15;
    }

    public double calculateMaxDrawdownByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        // Simplified max drawdown calculation
        double totalValue = userAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
        
        // Assume 20% max drawdown for simplicity
        return totalValue * 0.20;
    }

    public double calculateBetaByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        // Simplified beta calculation - assume market beta of 1.0
        return 1.0;
    }

    public double calculateDiversificationScoreByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            return 0.0;
        }
        
        Set<String> uniqueAssets = new HashSet<>();
        for (Asset asset : userAssets) {
            uniqueAssets.add(asset.getName().toLowerCase());
        }
        
        int totalAssets = userAssets.size();
        int uniqueCount = uniqueAssets.size();
        
        if (totalAssets == 0) return 0.0;
        
        double diversityRatio = (double) uniqueCount / totalAssets;
        return Math.round(diversityRatio * 100); // Score from 0 to 100
    }

    public RiskMetrics getRiskMetricsByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        try {
            return RiskMetrics.builder()
                    .roi(calculateROIByUser(user))
                    .sharpeRatio(calculateSharpeRatioByUser(user))
                    .volatility(calculateVolatilityByUser(user))
                    .maxDrawdown(calculateMaxDrawdownByUser(user))
                    .beta(calculateBetaByUser(user))
                    .diversificationScore(calculateDiversificationScoreByUser(user))
                    .build();
        } catch (Exception e) {
            // Fallback to simplified metrics
            return RiskMetrics.builder()
                    .roi(0.0)
                    .sharpeRatio(1.0)
                    .volatility(0.0)
                    .maxDrawdown(0.0)
                    .beta(1.0)
                    .diversificationScore(0.0)
                    .build();
        }
    }
}