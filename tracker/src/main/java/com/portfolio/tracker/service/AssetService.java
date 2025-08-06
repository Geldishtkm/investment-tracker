package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
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
        // initialInvestment based on purchasePricePerUnit
        asset.setInitialInvestment(asset.getQuantity() * asset.getPurchasePricePerUnit());
        return assetRepository.save(asset);
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
}
