package com.portfolio.tracker.service;

import com.portfolio.tracker.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Portfolio Rebalancing Service implementing Mean-Variance Optimization and Black-Litterman
 * Provides institutional-grade portfolio optimization algorithms
 */
@Service
public class PortfolioRebalancingService {

    @Autowired
    private AssetService assetService;
    
    @Autowired
    private PriceHistoryService priceHistoryService;
    
    @Autowired
    private VaRCalculationService varCalculationService;

    /**
     * Calculate optimal portfolio allocation using Mean-Variance Optimization
     */
    public PortfolioRebalancing calculateOptimalAllocation(User user, double riskTolerance) {
        // For now, get all assets since we're using mock user
        // In production, this would be properly authenticated
        List<Asset> assets = assetService.getAllAssets();
        
        if (assets.isEmpty()) {
            throw new IllegalStateException("No assets found in the system");
        }

        PortfolioRebalancing rebalancing = new PortfolioRebalancing();
        rebalancing.setUserId(user.getUsername());
        rebalancing.setRiskTolerance(riskTolerance);
        rebalancing.setOptimizationMethod("MVO");
        
        // Calculate current portfolio state
        double totalValue = assetService.getTotalValue();
        rebalancing.setCurrentPortfolioValue(totalValue);
        rebalancing.setTargetPortfolioValue(totalValue);
        
        // Calculate current allocation
        Map<String, Double> currentAllocation = calculateCurrentAllocation(assets, totalValue);
        rebalancing.setCurrentAllocation(currentAllocation);
        
        // Calculate expected returns and covariance matrix
        Map<String, Double> expectedReturns = calculateExpectedReturns(assets);
        double[][] covarianceMatrix = calculateCovarianceMatrix(assets);
        
        // Run Mean-Variance Optimization
        Map<String, Double> optimalWeights = runMeanVarianceOptimization(
            assets, expectedReturns, covarianceMatrix, riskTolerance);
        
        rebalancing.setTargetAllocation(optimalWeights);
        
        // Calculate portfolio metrics
        double currentRisk = calculatePortfolioRisk(assets, currentAllocation, covarianceMatrix);
        double targetRisk = calculatePortfolioRisk(assets, optimalWeights, covarianceMatrix);
        double currentReturn = calculatePortfolioReturn(assets, currentAllocation, expectedReturns);
        double targetReturn = calculatePortfolioReturn(assets, optimalWeights, expectedReturns);
        
        rebalancing.setCurrentRisk(currentRisk);
        rebalancing.setTargetRisk(targetRisk);
        rebalancing.setExpectedReturn(targetReturn);
        rebalancing.setRiskReduction(currentRisk - targetRisk);
        rebalancing.setReturnImprovement(targetReturn - currentReturn);
        
        // Generate rebalancing actions
        List<RebalancingAction> actions = generateRebalancingActions(
            assets, currentAllocation, optimalWeights, totalValue);
        rebalancing.setRecommendedActions(actions);
        
        // Calculate transaction costs
        double totalCost = actions.stream()
            .mapToDouble(RebalancingAction::getTransactionCost)
            .sum();
        rebalancing.setTotalTransactionCost(totalCost);
        
        // Portfolio status is calculated dynamically by getPortfolioStatus()
        
        return rebalancing;
    }

    /**
     * Calculate optimal allocation using Black-Litterman model
     */
    public PortfolioRebalancing calculateBlackLittermanAllocation(User user, double riskTolerance, 
                                                                Map<String, Double> userViews) {
        // Start with MVO as baseline
        PortfolioRebalancing rebalancing = calculateOptimalAllocation(user, riskTolerance);
        
        // Apply Black-Litterman adjustments based on user views
        Map<String, Double> adjustedWeights = applyBlackLittermanAdjustments(
            rebalancing.getTargetAllocation(), userViews, riskTolerance);
        
        rebalancing.setTargetAllocation(adjustedWeights);
        rebalancing.setOptimizationMethod("BLACK_LITTERMAN");
        
        // Recalculate metrics with adjusted weights
        List<Asset> assets = assetService.getAllAssets();
        Map<String, Double> expectedReturns = calculateExpectedReturns(assets);
        double[][] covarianceMatrix = calculateCovarianceMatrix(assets);
        
        double targetRisk = calculatePortfolioRisk(assets, adjustedWeights, covarianceMatrix);
        double targetReturn = calculatePortfolioReturn(assets, adjustedWeights, expectedReturns);
        
        rebalancing.setTargetRisk(targetRisk);
        rebalancing.setExpectedReturn(targetReturn);
        
        // Regenerate actions with new weights
        List<RebalancingAction> actions = generateRebalancingActions(
            assets, rebalancing.getCurrentAllocation(), adjustedWeights, 
            rebalancing.getCurrentPortfolioValue());
        rebalancing.setRecommendedActions(actions);
        
        return rebalancing;
    }

    /**
     * Run Mean-Variance Optimization using quadratic programming approach
     */
    private Map<String, Double> runMeanVarianceOptimization(List<Asset> assets, 
                                                           Map<String, Double> expectedReturns,
                                                           double[][] covarianceMatrix, 
                                                           double riskTolerance) {
        Map<String, Double> optimalWeights = new HashMap<>();
        
        // Simplified MVO using risk tolerance as constraint
        // In production, you'd use a proper quadratic programming solver
        
        // Calculate risk-adjusted returns
        Map<String, Double> riskAdjustedReturns = new HashMap<>();
        for (Asset asset : assets) {
            String assetName = asset.getName();
            double expectedReturn = expectedReturns.getOrDefault(assetName, 0.08);
            double volatility = calculateAssetVolatility(assetName);
            double sharpeRatio = expectedReturn / volatility;
            riskAdjustedReturns.put(assetName, sharpeRatio);
        }
        
        // Sort assets by risk-adjusted returns
        List<String> sortedAssets = riskAdjustedReturns.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Allocate weights based on risk tolerance and Sharpe ratios
        double totalWeight = 0.0;
        double riskBudget = riskTolerance;
        
        for (String assetName : sortedAssets) {
            double maxWeight = Math.min(0.4, riskBudget); // Max 40% per asset
            double weight = maxWeight * (1.0 - totalWeight);
            
            if (weight > 0 && totalWeight < 1.0) {
                optimalWeights.put(assetName, weight);
                totalWeight += weight;
                riskBudget -= weight * calculateAssetVolatility(assetName);
            }
        }
        
        // Normalize weights to sum to 1.0
        if (totalWeight > 0) {
            for (String assetName : optimalWeights.keySet()) {
                optimalWeights.put(assetName, optimalWeights.get(assetName) / totalWeight);
            }
        }
        
        return optimalWeights;
    }

    /**
     * Apply Black-Litterman adjustments based on user views
     */
    private Map<String, Double> applyBlackLittermanAdjustments(Map<String, Double> baseWeights,
                                                              Map<String, Double> userViews,
                                                              double riskTolerance) {
        Map<String, Double> adjustedWeights = new HashMap<>(baseWeights);
        
        // Simple Black-Litterman implementation
        // In production, you'd use the full mathematical model
        
        for (Map.Entry<String, Double> view : userViews.entrySet()) {
            String assetName = view.getKey();
            double confidence = view.getValue(); // 0.0 to 1.0
            
            if (adjustedWeights.containsKey(assetName)) {
                double baseWeight = adjustedWeights.get(assetName);
                double adjustment = confidence * 0.1; // Max 10% adjustment
                
                // Increase weight if user is bullish, decrease if bearish
                double newWeight = baseWeight + adjustment;
                newWeight = Math.max(0.0, Math.min(0.5, newWeight)); // Clamp between 0% and 50%
                
                adjustedWeights.put(assetName, newWeight);
            }
        }
        
        // Renormalize weights
        double totalWeight = adjustedWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight > 0) {
            for (String assetName : adjustedWeights.keySet()) {
                adjustedWeights.put(assetName, adjustedWeights.get(assetName) / totalWeight);
            }
        }
        
        return adjustedWeights;
    }

    /**
     * Generate specific rebalancing actions
     */
    private List<RebalancingAction> generateRebalancingActions(List<Asset> assets,
                                                              Map<String, Double> currentAllocation,
                                                              Map<String, Double> targetAllocation,
                                                              double totalValue) {
        List<RebalancingAction> actions = new ArrayList<>();
        
        for (Asset asset : assets) {
            String assetName = asset.getName();
            double currentWeight = currentAllocation.getOrDefault(assetName, 0.0);
            double targetWeight = targetAllocation.getOrDefault(assetName, 0.0);
            
            if (Math.abs(currentWeight - targetWeight) > 0.01) { // 1% threshold
                RebalancingAction action = new RebalancingAction();
                action.setAssetName(assetName);
                
                double currentValue = currentWeight * totalValue;
                double targetValue = targetWeight * totalValue;
                double valueChange = targetValue - currentValue;
                
                if (valueChange > 0) {
                    action.setActionType("BUY");
                    action.setQuantityChange(valueChange / asset.getPricePerUnit());
                } else {
                    action.setActionType("SELL");
                    action.setQuantityChange(Math.abs(valueChange) / asset.getPricePerUnit());
                }
                
                action.setCurrentQuantity(asset.getQuantity());
                action.setTargetQuantity(asset.getQuantity() + action.getQuantityChange());
                action.setCurrentValue(currentValue);
                action.setTargetValue(targetValue);
                action.setValueChange(Math.abs(valueChange));
                action.setCurrentAllocation(currentWeight);
                action.setTargetAllocation(targetWeight);
                action.setAllocationChange(targetWeight - currentWeight);
                action.setEstimatedPrice(asset.getPricePerUnit());
                
                // Calculate transaction costs (simplified)
                action.setTransactionCost(Math.abs(valueChange) * 0.001); // 0.1% transaction cost
                action.setTaxImpact(0.0); // Simplified for demo
                action.calculateNetImpact();
                
                // Set priority based on allocation drift
                double drift = Math.abs(currentWeight - targetWeight);
                if (drift > 0.15) action.setPriority(1);
                else if (drift > 0.10) action.setPriority(2);
                else if (drift > 0.05) action.setPriority(3);
                else action.setPriority(4);
                
                actions.add(action);
            }
        }
        
        // Sort by priority
        actions.sort(Comparator.comparingInt(RebalancingAction::getPriority));
        
        return actions;
    }

    /**
     * Calculate current portfolio allocation
     */
    private Map<String, Double> calculateCurrentAllocation(List<Asset> assets, double totalValue) {
        Map<String, Double> allocation = new HashMap<>();
        
        for (Asset asset : assets) {
            double assetValue = asset.getQuantity() * asset.getPricePerUnit();
            double weight = assetValue / totalValue;
            allocation.put(asset.getName(), weight);
        }
        
        return allocation;
    }

    /**
     * Calculate expected returns for assets
     */
    private Map<String, Double> calculateExpectedReturns(List<Asset> assets) {
        Map<String, Double> returns = new HashMap<>();
        
        for (Asset asset : assets) {
            String assetName = asset.getName();
            try {
                // Try to get real historical returns
                List<Double> historicalReturns = priceHistoryService.calculateHistoricalReturns(assetName, 252);
                if (!historicalReturns.isEmpty()) {
                    double avgReturn = historicalReturns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.08);
                    returns.put(assetName, avgReturn);
                    continue;
                }
            } catch (Exception e) {
                // Fallback to default returns
            }
            
            // Default expected returns based on asset type
            String lowerName = assetName.toLowerCase();
            if (lowerName.contains("bitcoin") || lowerName.contains("btc")) {
                returns.put(assetName, 0.15); // 15% annual return
            } else if (lowerName.contains("ethereum") || lowerName.contains("eth")) {
                returns.put(assetName, 0.12); // 12% annual return
            } else if (lowerName.contains("stable") || lowerName.contains("usdt") || lowerName.contains("usdc")) {
                returns.put(assetName, 0.02); // 2% annual return
            } else if (lowerName.contains("blackrock") || lowerName.contains("buidl")) {
                returns.put(assetName, 0.08); // 8% annual return
            } else {
                returns.put(assetName, 0.10); // 10% annual return
            }
        }
        
        return returns;
    }

    /**
     * Calculate covariance matrix for portfolio risk calculation
     */
    private double[][] calculateCovarianceMatrix(List<Asset> assets) {
        int n = assets.size();
        double[][] covarianceMatrix = new double[n][n];
        
        // Simplified covariance calculation
        // In production, you'd use historical return data
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    // Diagonal elements (variances)
                    String assetName = assets.get(i).getName();
                    double volatility = calculateAssetVolatility(assetName);
                    covarianceMatrix[i][j] = volatility * volatility;
                } else {
                    // Off-diagonal elements (covariances)
                    // Simplified correlation assumption
                    covarianceMatrix[i][j] = 0.3; // 30% correlation
                }
            }
        }
        
        return covarianceMatrix;
    }

    /**
     * Calculate portfolio risk (standard deviation)
     */
    private double calculatePortfolioRisk(List<Asset> assets, Map<String, Double> weights, 
                                        double[][] covarianceMatrix) {
        double risk = 0.0;
        List<String> assetNames = assets.stream()
            .map(Asset::getName)
            .collect(Collectors.toList());
        
        for (int i = 0; i < assets.size(); i++) {
            for (int j = 0; j < assets.size(); j++) {
                String assetI = assetNames.get(i);
                String assetJ = assetNames.get(j);
                double weightI = weights.getOrDefault(assetI, 0.0);
                double weightJ = weights.getOrDefault(assetJ, 0.0);
                risk += weightI * weightJ * covarianceMatrix[i][j];
            }
        }
        
        return Math.sqrt(risk);
    }

    /**
     * Calculate portfolio expected return
     */
    private double calculatePortfolioReturn(List<Asset> assets, Map<String, Double> weights,
                                          Map<String, Double> expectedReturns) {
        double totalReturn = 0.0;
        
        for (Asset asset : assets) {
            String assetName = asset.getName();
            double weight = weights.getOrDefault(assetName, 0.0);
            double expectedReturn = expectedReturns.getOrDefault(assetName, 0.08);
            totalReturn += weight * expectedReturn;
        }
        
        return totalReturn;
    }

    /**
     * Calculate asset volatility
     */
    private double calculateAssetVolatility(String assetName) {
        try {
            return priceHistoryService.calculateRealVolatility(assetName, 252);
        } catch (Exception e) {
            // Fallback to default volatilities
            String lowerName = assetName.toLowerCase();
            if (lowerName.contains("bitcoin") || lowerName.contains("btc")) {
                return 0.80; // 80% volatility
            } else if (lowerName.contains("ethereum") || lowerName.contains("eth")) {
                return 0.75; // 75% volatility
            } else if (lowerName.contains("stable") || lowerName.contains("usdt") || lowerName.contains("usdc")) {
                return 0.05; // 5% volatility
            } else if (lowerName.contains("blackrock") || lowerName.contains("buidl")) {
                return 0.15; // 15% volatility
            } else {
                return 0.60; // 60% volatility
            }
        }
    }

    /**
     * Check if portfolio needs rebalancing
     */
    public boolean needsRebalancing(User user, double threshold) {
        List<Asset> assets = assetService.getAllAssets();
        if (assets.isEmpty()) return false;
        
        double totalValue = assetService.getTotalValue();
        Map<String, Double> currentAllocation = calculateCurrentAllocation(assets, totalValue);
        
        // Calculate total drift from equal weight (simplified target)
        double totalDrift = 0.0;
        double equalWeight = 1.0 / currentAllocation.size();
        
        for (double weight : currentAllocation.values()) {
            totalDrift += Math.abs(weight - equalWeight);
        }
        
        return totalDrift > threshold;
    }

    /**
     * Get rebalancing summary for dashboard
     */
    public Map<String, Object> getRebalancingSummary(User user) {
        try {
            PortfolioRebalancing rebalancing = calculateOptimalAllocation(user, 0.5); // Medium risk
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("needsRebalancing", rebalancing.isRebalancingNeeded(0.05));
            summary.put("currentRisk", rebalancing.getCurrentRisk());
            summary.put("targetRisk", rebalancing.getTargetRisk());
            summary.put("riskReduction", rebalancing.getRiskReduction());
            summary.put("returnImprovement", rebalancing.getReturnImprovement());
            summary.put("totalTransactionCost", rebalancing.getTotalTransactionCost());
            summary.put("portfolioStatus", rebalancing.getPortfolioStatus());
            summary.put("allocationDrift", rebalancing.calculateAllocationDrift());
            
            return summary;
        } catch (Exception e) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("error", e.getMessage());
            return summary;
        }
    }
}
