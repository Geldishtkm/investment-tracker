package com.portfolio.tracker.model;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Portfolio Rebalancing model for Mean-Variance Optimization and Black-Litterman
 * Represents the current state, target allocation, and rebalancing recommendations
 */
public class PortfolioRebalancing {
    
    private String userId;
    private double currentPortfolioValue;
    private double targetPortfolioValue;
    private double riskTolerance; // 0.0 to 1.0 (conservative to aggressive)
    
    // Current allocation percentages
    private Map<String, Double> currentAllocation;
    
    // Target allocation percentages (optimal weights)
    private Map<String, Double> targetAllocation;
    
    // Rebalancing recommendations
    private List<RebalancingAction> recommendedActions;
    
    // Portfolio metrics
    private double currentRisk;
    private double targetRisk;
    private double expectedReturn;
    private double riskReduction;
    private double returnImprovement;
    
    // Rebalancing costs
    private double totalTransactionCost;
    private double taxImpact;
    
    // Optimization method used
    private String optimizationMethod; // "MVO", "BLACK_LITTERMAN", "CUSTOM"
    
    // Timestamp
    private long lastRebalanced;
    private long nextRebalanceDue;
    
    // Constructor
    public PortfolioRebalancing() {
        this.currentAllocation = new HashMap<>();
        this.targetAllocation = new HashMap<>();
        this.recommendedActions = new ArrayList<>();
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public double getCurrentPortfolioValue() { return currentPortfolioValue; }
    public void setCurrentPortfolioValue(double currentPortfolioValue) { this.currentPortfolioValue = currentPortfolioValue; }
    
    public double getTargetPortfolioValue() { return targetPortfolioValue; }
    public void setTargetPortfolioValue(double targetPortfolioValue) { this.targetPortfolioValue = targetPortfolioValue; }
    
    public double getRiskTolerance() { return riskTolerance; }
    public void setRiskTolerance(double riskTolerance) { this.riskTolerance = riskTolerance; }
    
    public Map<String, Double> getCurrentAllocation() { return currentAllocation; }
    public void setCurrentAllocation(Map<String, Double> currentAllocation) { this.currentAllocation = currentAllocation; }
    
    public Map<String, Double> getTargetAllocation() { return targetAllocation; }
    public void setTargetAllocation(Map<String, Double> targetAllocation) { this.targetAllocation = targetAllocation; }
    
    public List<RebalancingAction> getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(List<RebalancingAction> recommendedActions) { this.recommendedActions = recommendedActions; }
    
    public double getCurrentRisk() { return currentRisk; }
    public void setCurrentRisk(double currentRisk) { this.currentRisk = currentRisk; }
    
    public double getTargetRisk() { return targetRisk; }
    public void setTargetRisk(double targetRisk) { this.targetRisk = targetRisk; }
    
    public double getExpectedReturn() { return expectedReturn; }
    public void setExpectedReturn(double expectedReturn) { this.expectedReturn = expectedReturn; }
    
    public double getRiskReduction() { return riskReduction; }
    public void setRiskReduction(double riskReduction) { this.riskReduction = riskReduction; }
    
    public double getReturnImprovement() { return returnImprovement; }
    public void setReturnImprovement(double returnImprovement) { this.returnImprovement = returnImprovement; }
    
    public double getTotalTransactionCost() { return totalTransactionCost; }
    public void setTotalTransactionCost(double totalTransactionCost) { this.totalTransactionCost = totalTransactionCost; }
    
    public double getTaxImpact() { return taxImpact; }
    public void setTaxImpact(double taxImpact) { this.taxImpact = taxImpact; }
    
    public String getOptimizationMethod() { return optimizationMethod; }
    public void setOptimizationMethod(String optimizationMethod) { this.optimizationMethod = optimizationMethod; }
    
    public long getLastRebalanced() { return lastRebalanced; }
    public void setLastRebalanced(long lastRebalanced) { this.lastRebalanced = lastRebalanced; }
    
    public long getNextRebalanceDue() { return nextRebalanceDue; }
    public void setNextRebalanceDue(long nextRebalanceDue) { this.nextRebalanceDue = nextRebalanceDue; }
    
    /**
     * Calculate the drift from target allocation
     */
    public double calculateAllocationDrift() {
        double totalDrift = 0.0;
        for (String asset : currentAllocation.keySet()) {
            double current = currentAllocation.getOrDefault(asset, 0.0);
            double target = targetAllocation.getOrDefault(asset, 0.0);
            totalDrift += Math.abs(current - target);
        }
        return totalDrift;
    }
    
    /**
     * Check if rebalancing is needed based on drift threshold
     */
    public boolean isRebalancingNeeded(double threshold) {
        return calculateAllocationDrift() > threshold;
    }
    
    /**
     * Get the status of the portfolio
     */
    public String getPortfolioStatus() {
        double drift = calculateAllocationDrift();
        if (drift < 0.05) return "BALANCED";
        if (drift < 0.10) return "NEEDS_REBALANCING";
        if (drift < 0.20) return "NEEDS_REBALANCING";
        return "CRITICAL";
    }
}
