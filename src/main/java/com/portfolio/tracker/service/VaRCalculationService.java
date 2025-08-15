package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.VaRCalculation;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.PriceHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Value at Risk (VaR) calculation service
 * Implements multiple VaR methodologies: Historical, Parametric, and Monte Carlo
 */
@Service
public class VaRCalculationService {

    @Autowired
    private AssetService assetService;
    
    @Autowired
    private PriceHistoryService priceHistoryService;

    /**
     * Calculate comprehensive VaR for a user's portfolio
     */
    public VaRCalculation calculatePortfolioVaR(User user, double confidenceLevel, int timeHorizon) {
        List<Asset> assets = assetService.getAssetsByUser(user);
        
        if (assets.isEmpty()) {
            throw new IllegalStateException("No assets found for user: " + user.getUsername());
        }

        VaRCalculation varCalculation = new VaRCalculation();
        varCalculation.setUserId(user.getUsername());
        varCalculation.setConfidenceLevel(confidenceLevel);
        varCalculation.setTimeHorizon(timeHorizon);
        
        // Calculate portfolio value and weights
        double totalValue = assetService.getTotalValueByUser(user);
        varCalculation.setPortfolioValue(totalValue);
        
        Map<String, Double> assetWeights = calculateAssetWeights(assets, totalValue);
        varCalculation.setAssetWeights(assetWeights);
        
        // Calculate historical returns using REAL data
        List<Double> historicalReturns = calculateRealHistoricalReturns(assets);
        varCalculation.setHistoricalReturns(historicalReturns);
        
        // Calculate risk metrics
        double volatility = calculateVolatility(historicalReturns);
        double skewness = calculateSkewness(historicalReturns);
        double kurtosis = calculateKurtosis(historicalReturns);
        double expectedReturn = calculateExpectedReturn(historicalReturns);
        
        varCalculation.setVolatility(volatility);
        varCalculation.setSkewness(skewness);
        varCalculation.setKurtosis(kurtosis);
        varCalculation.setExpectedReturn(expectedReturn);
        
        // Calculate different VaR methodologies
        double historicalVaR = calculateHistoricalVaR(historicalReturns, totalValue, confidenceLevel, timeHorizon);
        double parametricVaR = calculateParametricVaR(totalValue, volatility, expectedReturn, confidenceLevel, timeHorizon);
        double monteCarloVaR = calculateMonteCarloVaR(assets, totalValue, confidenceLevel, timeHorizon);
        double conditionalVaR = calculateConditionalVaR(historicalReturns, totalValue, confidenceLevel, timeHorizon);
        
        varCalculation.setHistoricalVaR(historicalVaR);
        varCalculation.setParametricVaR(parametricVaR);
        varCalculation.setMonteCarloVaR(monteCarloVaR);
        varCalculation.setConditionalVaR(conditionalVaR);
        
        return varCalculation;
    }

    /**
     * Calculate Historical VaR using historical simulation method
     */
    private double calculateHistoricalVaR(List<Double> returns, double portfolioValue, double confidenceLevel, int timeHorizon) {
        if (returns.size() < 30) {
            // Need at least 30 data points for meaningful VaR
            return portfolioValue * 0.05; // Default 5% VaR
        }
        
        // Sort returns in ascending order
        List<Double> sortedReturns = returns.stream()
                .sorted()
                .collect(Collectors.toList());
        
        // Calculate the percentile based on confidence level
        int index = (int) Math.ceil((1 - confidenceLevel) * returns.size()) - 1;
        index = Math.max(0, Math.min(index, returns.size() - 1));
        
        double varReturn = sortedReturns.get(index);
        
        // Apply time horizon adjustment (square root of time rule)
        double timeAdjustment = Math.sqrt(timeHorizon);
        
        return Math.abs(portfolioValue * varReturn * timeAdjustment);
    }

    /**
     * Calculate Parametric VaR using normal distribution assumption
     */
    private double calculateParametricVaR(double portfolioValue, double volatility, double expectedReturn, double confidenceLevel, int timeHorizon) {
        // Z-score for confidence level (95% = 1.645, 99% = 2.326)
        double zScore = getZScore(confidenceLevel);
        
        // Apply time horizon adjustment
        double timeAdjustment = Math.sqrt(timeHorizon);
        
        // Parametric VaR formula: VaR = Z * σ * √T * Portfolio Value
        double var = zScore * volatility * timeAdjustment * portfolioValue;
        
        return Math.abs(var);
    }

    /**
     * Calculate Monte Carlo VaR using simulation
     */
    private double calculateMonteCarloVaR(List<Asset> assets, double portfolioValue, double confidenceLevel, int timeHorizon) {
        if (assets.isEmpty()) {
            return portfolioValue * 0.05;
        }
        
        Random random = new Random();
        List<Double> simulatedReturns = new ArrayList<>();
        
        // Get portfolio weights and individual asset volatilities
        Map<String, Double> weights = calculateAssetWeights(assets, portfolioValue);
        Map<String, Double> volatilities = getAssetVolatilities(assets);
        
        // Run Monte Carlo simulations
        for (int i = 0; i < 10000; i++) {
            double portfolioReturn = 0.0;
            
            for (Asset asset : assets) {
                String assetName = asset.getName();
                double weight = weights.getOrDefault(assetName, 0.0);
                double volatility = volatilities.getOrDefault(assetName, 0.3); // Default 30% volatility
                
                // Generate random return using normal distribution
                double assetReturn = random.nextGaussian() * volatility;
                portfolioReturn += weight * assetReturn;
            }
            
            simulatedReturns.add(portfolioReturn);
        }
        
        // Sort and find VaR
        simulatedReturns.sort(Double::compareTo);
        int index = (int) Math.ceil((1 - confidenceLevel) * simulatedReturns.size()) - 1;
        index = Math.max(0, Math.min(index, simulatedReturns.size() - 1));
        
        double varReturn = simulatedReturns.get(index);
        
        // Apply time horizon adjustment
        double timeAdjustment = Math.sqrt(timeHorizon);
        
        return Math.abs(portfolioValue * varReturn * timeAdjustment);
    }

    /**
     * Calculate Conditional VaR (Expected Shortfall)
     */
    private double calculateConditionalVaR(List<Double> returns, double portfolioValue, double confidenceLevel, int timeHorizon) {
        if (returns.size() < 30) {
            return portfolioValue * 0.07; // Default 7% CVaR
        }
        
        // Sort returns in ascending order
        List<Double> sortedReturns = returns.stream()
                .sorted()
                .collect(Collectors.toList());
        
        // Find the cutoff index for VaR
        int cutoffIndex = (int) Math.ceil((1 - confidenceLevel) * returns.size()) - 1;
        cutoffIndex = Math.max(0, Math.min(cutoffIndex, returns.size() - 1));
        
        // Calculate average of returns below VaR threshold
        double sum = 0.0;
        int count = 0;
        
        for (int i = 0; i <= cutoffIndex; i++) {
            sum += sortedReturns.get(i);
            count++;
        }
        
        double averageReturn = count > 0 ? sum / count : 0.0;
        
        // Apply time horizon adjustment
        double timeAdjustment = Math.sqrt(timeHorizon);
        
        return Math.abs(portfolioValue * averageReturn * timeAdjustment);
    }

    /**
     * Calculate portfolio volatility
     */
    private double calculateVolatility(List<Double> returns) {
        if (returns.size() < 2) {
            return 0.2; // Default 20% volatility
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0.0);
        
        return Math.sqrt(variance);
    }

    /**
     * Calculate skewness (measure of distribution asymmetry)
     */
    private double calculateSkewness(List<Double> returns) {
        if (returns.size() < 3) {
            return 0.0;
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double stdDev = calculateVolatility(returns);
        
        if (stdDev == 0) return 0.0;
        
        double skewness = returns.stream()
                .mapToDouble(r -> Math.pow((r - mean) / stdDev, 3))
                .average()
                .orElse(0.0);
        
        return skewness;
    }

    /**
     * Calculate kurtosis (measure of distribution "peakedness")
     */
    private double calculateKurtosis(List<Double> returns) {
        if (returns.size() < 4) {
            return 3.0; // Normal distribution kurtosis
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double stdDev = calculateVolatility(returns);
        
        if (stdDev == 0) return 3.0;
        
        double kurtosis = returns.stream()
                .mapToDouble(r -> Math.pow((r - mean) / stdDev, 4))
                .average()
                .orElse(3.0);
        
        return kurtosis;
    }

    /**
     * Calculate expected return
     */
    private double calculateExpectedReturn(List<Double> returns) {
        if (returns.isEmpty()) {
            return 0.08; // Default 8% annual return
        }
        
        return returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.08);
    }

    /**
     * Calculate asset weights in portfolio
     */
    private Map<String, Double> calculateAssetWeights(List<Asset> assets, double totalValue) {
        Map<String, Double> weights = new HashMap<>();
        
        for (Asset asset : assets) {
            double assetValue = asset.getQuantity() * asset.getPricePerUnit();
            double weight = assetValue / totalValue;
            weights.put(asset.getName(), weight);
        }
        
        return weights;
    }

    /**
     * Get asset volatilities (estimated based on asset type)
     */
    private Map<String, Double> getAssetVolatilities(List<Asset> assets) {
        Map<String, Double> volatilities = new HashMap<>();
        
        for (Asset asset : assets) {
            String assetName = asset.getName();
            
            // Try to get real volatility from historical data first
                    try {
            double realVolatility = priceHistoryService.calculateRealVolatility(assetName, 252);
            if (realVolatility > 0) {
                volatilities.put(assetName, realVolatility);
                continue;
            }
        } catch (Exception e) {
            System.err.println("Failed to calculate real volatility for " + assetName + ", using fallback: " + e.getMessage());
        }
            
            // Fallback to hardcoded estimates if real data fails
            String lowerName = assetName.toLowerCase();
            double volatility;
            
            if (lowerName.contains("bitcoin") || lowerName.contains("btc")) {
                volatility = 0.80; // Bitcoin: 80% volatility
            } else if (lowerName.contains("ethereum") || lowerName.contains("eth")) {
                volatility = 0.75; // Ethereum: 75% volatility
            } else if (lowerName.contains("stable") || lowerName.contains("usdt") || lowerName.contains("usdc")) {
                volatility = 0.05; // Stablecoins: 5% volatility
            } else if (lowerName.contains("blackrock") || lowerName.contains("buidl")) {
                volatility = 0.15; // Institutional products: 15% volatility
            } else {
                volatility = 0.60; // Default crypto: 60% volatility
            }
            
            volatilities.put(assetName, volatility);
        }
        
        return volatilities;
    }

    /**
     * Calculate historical returns using REAL market data
     */
    private List<Double> calculateRealHistoricalReturns(List<Asset> assets) {
        if (assets.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Extract asset names for historical data fetching
        List<String> assetNames = assets.stream()
            .map(Asset::getName)
            .collect(Collectors.toList());
        
        // Get real historical returns from the price history service
        // Use 252 days (one trading year) for comprehensive analysis
        return priceHistoryService.getPortfolioHistoricalReturns(assetNames, 252);
    }

    /**
     * Calculate historical returns from asset data (LEGACY - kept for fallback)
     */
    private List<Double> calculateHistoricalReturns(List<Asset> assets) {
        List<Double> returns = new ArrayList<>();
        
        // For now, we'll generate synthetic returns based on asset types
        // In a real implementation, you'd fetch historical price data
        Random random = new Random();
        
        for (Asset asset : assets) {
            String assetName = asset.getName().toLowerCase();
            double baseReturn;
            double volatility;
            
            if (assetName.contains("bitcoin") || assetName.contains("btc")) {
                baseReturn = 0.001; // 0.1% daily return
                volatility = 0.04; // 4% daily volatility
            } else if (assetName.contains("ethereum") || assetName.contains("eth")) {
                baseReturn = 0.0012; // 0.12% daily return
                volatility = 0.045; // 4.5% daily volatility
            } else if (assetName.contains("stable") || assetName.contains("usdt") || assetName.contains("usdc")) {
                baseReturn = 0.0001; // 0.01% daily return
                volatility = 0.002; // 0.2% daily volatility
            } else if (assetName.contains("blackrock") || assetName.contains("buidl")) {
                baseReturn = 0.0005; // 0.05% daily return
                volatility = 0.015; // 1.5% daily volatility
            } else {
                baseReturn = 0.0008; // 0.08% daily return
                volatility = 0.03; // 3% daily volatility
            }
            
            // Generate 252 daily returns (one trading year)
            for (int i = 0; i < 252; i++) {
                double dailyReturn = baseReturn + random.nextGaussian() * volatility;
                returns.add(dailyReturn);
            }
        }
        
        return returns;
    }

    /**
     * Get Z-score for given confidence level
     */
    private double getZScore(double confidenceLevel) {
        switch (Double.toString(confidenceLevel)) {
            case "0.99": return 2.326;
            case "0.975": return 1.96;
            case "0.95": return 1.645;
            case "0.90": return 1.282;
            case "0.85": return 1.036;
            case "0.80": return 0.842;
            default: return 1.645; // Default to 95% confidence
        }
    }

    /**
     * Get VaR calculation summary
     */
    public Map<String, Object> getVaRSummary(VaRCalculation varCalculation) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("portfolioValue", varCalculation.getPortfolioValue());
        summary.put("confidenceLevel", varCalculation.getConfidenceLevel());
        summary.put("timeHorizon", varCalculation.getTimeHorizon());
        summary.put("riskLevel", varCalculation.getRiskLevel());
        
        // VaR Results
        Map<String, Object> varResults = new HashMap<>();
        varResults.put("historicalVaR", varCalculation.getHistoricalVaR());
        varResults.put("parametricVaR", varCalculation.getParametricVaR());
        varResults.put("monteCarloVaR", varCalculation.getMonteCarloVaR());
        varResults.put("conditionalVaR", varCalculation.getConditionalVaR());
        varResults.put("historicalVaRPercentage", (varCalculation.getHistoricalVaR() / varCalculation.getPortfolioValue()) * 100);
        varResults.put("parametricVaRPercentage", (varCalculation.getParametricVaR() / varCalculation.getPortfolioValue()) * 100);
        varResults.put("monteCarloVaRPercentage", (varCalculation.getMonteCarloVaR() / varCalculation.getPortfolioValue()) * 100);
        
        summary.put("varResults", varResults);
        
        // Risk Metrics
        Map<String, Object> riskMetrics = new HashMap<>();
        riskMetrics.put("volatility", varCalculation.getVolatility());
        riskMetrics.put("skewness", varCalculation.getSkewness());
        riskMetrics.put("kurtosis", varCalculation.getKurtosis());
        riskMetrics.put("expectedReturn", varCalculation.getExpectedReturn());
        
        summary.put("riskMetrics", riskMetrics);
        
        return summary;
    }
}
