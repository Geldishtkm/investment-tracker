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

    // Data provenance
    private double annualizedVolatility;
    private double investedValue;
    private double cashValue;
    private double exposure = 1.0;
    private int observations; // daily returns the estimates are based on
    private String historyStart;
    private String historyEnd;
    private String historicalVaRMethod; // how the historical VaR reaches the time horizon

    // Constructor
    public VaRCalculation() {
        this.calculationDate = LocalDateTime.now();
    }

    // Getters and Setters
    /** Market value of the coin holdings. */
    public double getInvestedValue() { return investedValue; }
    public void setInvestedValue(double investedValue) { this.investedValue = investedValue; }
    /** Uninvested cash from the starting bankroll; it carries no market risk. */
    public double getCashValue() { return cashValue; }
    public void setCashValue(double cashValue) { this.cashValue = cashValue; }
    /** Share of the account in coins: investedValue / portfolioValue. */
    public double getExposure() { return exposure; }
    public void setExposure(double exposure) { this.exposure = exposure; }

    public double getAnnualizedVolatility() { return annualizedVolatility; }
    public void setAnnualizedVolatility(double annualizedVolatility) { this.annualizedVolatility = annualizedVolatility; }

    public int getObservations() { return observations; }
    public void setObservations(int observations) { this.observations = observations; }

    public String getHistoryStart() { return historyStart; }
    public void setHistoryStart(String historyStart) { this.historyStart = historyStart; }

    public String getHistoryEnd() { return historyEnd; }
    public void setHistoryEnd(String historyEnd) { this.historyEnd = historyEnd; }

    public String getHistoricalVaRMethod() { return historicalVaRMethod; }
    public void setHistoricalVaRMethod(String historicalVaRMethod) { this.historicalVaRMethod = historicalVaRMethod; }

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
