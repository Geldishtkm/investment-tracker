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
    private final PriceHistoryService priceHistoryService;

    @Autowired
    public AssetService(AssetRepository assetRepository, PriceHistoryService priceHistoryService) {
        this.assetRepository = assetRepository;
        this.priceHistoryService = priceHistoryService;
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset saveAsset(Asset asset) {
        // Calculate initial investment automatically
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

    public double getTotalValue() {
        return calculateTotalValue();
    }

    public double getTotalValueByUser(User user) {
        return getAssetsByUser(user)
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

    public List<Double> getPortfolioReturns() {
        List<Double> returns = fetchHistoricalReturnsFromDB();
        return returns != null ? returns : Collections.emptyList();
    }

    private List<Double> fetchHistoricalReturnsFromDB() {
        // TODO: Implement actual historical data fetching
        return Collections.emptyList();
    }

    public double calculateSharpeRatio() {
        List<Double> returns = getPortfolioReturns();

        if (returns.isEmpty()) {
            throw new IllegalStateException("No historical return data available to calculate Sharpe Ratio.");
        }

        double riskFreeRate = 0.04; // 4% risk-free rate
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

    private List<Double> getPortfolioValues() {
        // TODO: Implement actual portfolio value history
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

    private List<Double> getAssetReturns() {
        // TODO: Implement actual asset return history
        return Collections.emptyList();
    }

    private List<Double> getMarketReturns() {
        // TODO: Implement actual market return history
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
                return calculateSharpeRatioFromAssetType(userAssets, roi, riskFreeRate);
            }
            
            return (roi - riskFreeRate) / volatility;
        } catch (Exception e) {
            return calculateSharpeRatioFromAssetType(userAssets, 0.0, 2.0);
        }
    }

    private double calculateSharpeRatioFromAssetType(List<Asset> assets, double roi, double riskFreeRate) {
        double totalValue = assets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
        
        double weightedSharpe = 0.0;
        double totalWeight = 0.0;
        
        for (Asset asset : assets) {
            double assetValue = asset.getQuantity() * asset.getPricePerUnit();
            double weight = assetValue / totalValue;
            
            double assetSharpe = estimateSharpeByAsset(asset);
            weightedSharpe += assetSharpe * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedSharpe / totalWeight : 1.0;
    }

    private double estimateSharpeByAsset(Asset asset) {
        List<Double> prices = priceHistoryService.getHistoricalPrices(asset.getName(), 252);
        if (prices == null || prices.size() < 3) {
            // Not enough data to compute meaningful Sharpe
            return 0.0;
        }

        List<Double> returns = computeDailyReturns(prices);
        if (returns.isEmpty()) {
            return 0.0;
        }

        double riskFreeDaily = 0.04 / 252.0; // align with class-level Sharpe calc risk-free rate (4% annual)
        return computeSharpe(returns, riskFreeDaily);
    }

    private List<Double> computeDailyReturns(List<Double> prices) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            double prev = prices.get(i - 1);
            double curr = prices.get(i);
            if (prev != 0) {
                returns.add((curr - prev) / prev);
            }
        }
        return returns;
    }

    private double computeSharpe(List<Double> returns, double riskFreeDaily) {
        if (returns.size() < 2) return 0.0;
        double avg = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream().mapToDouble(r -> {
            return Math.pow(r - avg, 2);
        }).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0) return 0.0;
        return (avg - riskFreeDaily) / stdDev;
    }

    public double calculateVolatilityByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        double totalValue = userAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
        
        double weightedVolatility = 0.0;
        double totalWeight = 0.0;
        
        for (Asset asset : userAssets) {
            double assetValue = asset.getQuantity() * asset.getPricePerUnit();
            double weight = assetValue / totalValue;
            
            double assetVolatility = estimateVolatilityFromHistory(asset.getName());
            weightedVolatility += assetVolatility * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedVolatility : 0.15; // Default to 15% if no assets
    }

    private double estimateVolatilityFromHistory(String assetName) {
        List<Double> prices = priceHistoryService.getHistoricalPrices(assetName, 252);
        if (prices.size() < 2) return 0.0;
        List<Double> returns = computeDailyReturns(prices);
        if (returns.isEmpty()) return 0.0;
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance); // daily volatility
    }

    public double calculateMaxDrawdownByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        double totalValue = userAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
        
        double weightedMaxDrawdown = 0.0;
        double totalWeight = 0.0;
        
        for (Asset asset : userAssets) {
            double assetValue = asset.getQuantity() * asset.getPricePerUnit();
            double weight = assetValue / totalValue;

            List<Double> prices = priceHistoryService.getHistoricalPrices(asset.getName(), 252);
            double assetMaxDrawdown = prices.isEmpty() ? 0.0 : calculateMaxDrawdown(prices);
            weightedMaxDrawdown += assetMaxDrawdown * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedMaxDrawdown : 0.20; // Default to 20% if no assets
    }

    public double calculateMaxDrawdown(List<Double> prices) {
    double maxDrawdown = 0.0;
    double peak = prices.get(0);

    for (double price : prices) {
        if (price > peak) {
            peak = price; // new peak
        }
        double drawdown = (peak - price) / peak;
        if (drawdown > maxDrawdown) {
            maxDrawdown = drawdown;
        }
    }
    return maxDrawdown;
}

    

    public double calculateBetaByUser(User user) {
        List<Asset> userAssets = getAssetsByUser(user);
        if (userAssets.isEmpty()) {
            throw new IllegalStateException("No assets found for user");
        }
        
        double totalValue = userAssets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
        
        // Use portfolio combined historical returns as benchmark
        List<String> assetNames = new ArrayList<>();
        for (Asset a : userAssets) assetNames.add(a.getName());
        List<Double> benchmarkReturns = priceHistoryService.getPortfolioHistoricalReturns(assetNames, 252);
        
        double weightedBeta = 0.0;
        double totalWeight = 0.0;
        
        for (Asset asset : userAssets) {
            double assetValue = asset.getQuantity() * asset.getPricePerUnit();
            double weight = assetValue / totalValue;
            
            List<Double> assetReturns = priceHistoryService.calculateHistoricalReturns(asset.getName(), 252);
            double assetBeta = computeBeta(assetReturns, benchmarkReturns);
            weightedBeta += assetBeta * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedBeta : 1.0; // Fallback to 1.0 if no assets
    }

    private double computeBeta(List<Double> assetReturns, List<Double> benchmarkReturns) {
        if (assetReturns == null || benchmarkReturns == null) return 1.0;
        int n = Math.min(assetReturns.size(), benchmarkReturns.size());
        if (n < 2) return 1.0;
        double assetMean = assetReturns.subList(0, n).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double benchMean = benchmarkReturns.subList(0, n).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double cov = 0.0;
        double var = 0.0;
        for (int i = 0; i < n; i++) {
            double a = assetReturns.get(i) - assetMean;
            double b = benchmarkReturns.get(i) - benchMean;
            cov += a * b;
            var += b * b;
        }
        if (var == 0.0) return 1.0;
        return cov / var;
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
        return Math.round(diversityRatio * 100); // Convert to percentage
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
            // Return default metrics if calculations fail
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