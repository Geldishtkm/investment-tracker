package com.portfolio.tracker.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Value at Risk (VaR) calculation model
 * Supports multiple VaR methodologies: Historical, Parametric, and Monte Carlo
 */
public class VaRCalculation {
    private Long id;
    private String userId;
    private LocalDateTime calculationDate;
    private double confidenceLevel; // e.g., 0.95 for 95% confidence
    private int timeHorizon; // in days
    private double portfolioValue;
    
    // VaR Results
    private double historicalVaR;
    private double parametricVaR;
    private double monteCarloVaR;
    private double conditionalVaR; // Expected Shortfall
    
    // Risk Metrics
    private double volatility;
    private double skewness;
    private double kurtosis;
    private double expectedReturn;
    
    // Portfolio Composition
    private Map<String, Double> assetWeights;
    private Map<String, Double> assetReturns;
    private List<Double> historicalReturns;
    
    // Simulation Parameters
    private int numberOfSimulations;
    private double riskFreeRate;
    
    // Constructor
    public VaRCalculation() {
        this.calculationDate = LocalDateTime.now();
        this.confidenceLevel = 0.95;
        this.timeHorizon = 1;
        this.numberOfSimulations = 10000;
        this.riskFreeRate = 0.02; // 2% annual risk-free rate
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public LocalDateTime getCalculationDate() { return calculationDate; }
    public void setCalculationDate(LocalDateTime calculationDate) { this.calculationDate = calculationDate; }
    
    public double getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(double confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    
    public int getTimeHorizon() { return timeHorizon; }
    public void setTimeHorizon(int timeHorizon) { this.timeHorizon = timeHorizon; }
    
    public double getPortfolioValue() { return portfolioValue; }
    public void setPortfolioValue(double portfolioValue) { this.portfolioValue = portfolioValue; }
    
    public double getHistoricalVaR() { return historicalVaR; }
    public void setHistoricalVaR(double historicalVaR) { this.historicalVaR = historicalVaR; }
    
    public double getParametricVaR() { return parametricVaR; }
    public void setParametricVaR(double parametricVaR) { this.parametricVaR = parametricVaR; }
    
    public double getMonteCarloVaR() { return monteCarloVaR; }
    public void setMonteCarloVaR(double monteCarloVaR) { this.monteCarloVaR = monteCarloVaR; }
    
    public double getConditionalVaR() { return conditionalVaR; }
    public void setConditionalVaR(double conditionalVaR) { this.conditionalVaR = conditionalVaR; }
    
    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }
    
    public double getSkewness() { return skewness; }
    public void setSkewness(double skewness) { this.skewness = skewness; }
    
    public double getKurtosis() { return kurtosis; }
    public void setKurtosis(double kurtosis) { this.kurtosis = kurtosis; }
    
    public double getExpectedReturn() { return expectedReturn; }
    public void setExpectedReturn(double expectedReturn) { this.expectedReturn = expectedReturn; }
    
    public Map<String, Double> getAssetWeights() { return assetWeights; }
    public void setAssetWeights(Map<String, Double> assetWeights) { this.assetWeights = assetWeights; }
    
    public Map<String, Double> getAssetReturns() { return assetReturns; }
    public void setAssetReturns(Map<String, Double> assetReturns) { this.assetReturns = assetReturns; }
    
    public List<Double> getHistoricalReturns() { return historicalReturns; }
    public void setHistoricalReturns(List<Double> historicalReturns) { this.historicalReturns = historicalReturns; }
    
    public int getNumberOfSimulations() { return numberOfSimulations; }
    public void setNumberOfSimulations(int numberOfSimulations) { this.numberOfSimulations = numberOfSimulations; }
    
    public double getRiskFreeRate() { return riskFreeRate; }
    public void setRiskFreeRate(double riskFreeRate) { this.riskFreeRate = riskFreeRate; }
    
    // Helper methods
    public double getVaRPercentage() {
        return (historicalVaR / portfolioValue) * 100;
    }
    
    public String getRiskLevel() {
        double varPercentage = getVaRPercentage();
        if (varPercentage > 10) return "HIGH";
        if (varPercentage > 5) return "MEDIUM";
        return "LOW";
    }
    
    @Override
    public String toString() {
        return String.format("VaRCalculation{id=%d, userId='%s', historicalVaR=%.2f, parametricVaR=%.2f, monteCarloVaR=%.2f, riskLevel='%s'}", 
            id, userId, historicalVaR, parametricVaR, monteCarloVaR, getRiskLevel());
    }
}
