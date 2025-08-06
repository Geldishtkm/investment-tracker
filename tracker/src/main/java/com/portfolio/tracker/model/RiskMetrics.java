package com.portfolio.tracker.model;

public class RiskMetrics {
    private double volatility;
    private double maxDrawdown;
    private double beta;
    private double diversificationScore;

    public RiskMetrics(double volatility, double maxDrawdown, double beta, double diversificationScore) {
        this.volatility = volatility;
        this.maxDrawdown = maxDrawdown;
        this.beta = beta;
        this.diversificationScore = diversificationScore;
    }

    public double getVolatility() {
        return volatility;
    }

    public double getMaxDrawdown() {
        return maxDrawdown;
    }

    public double getBeta() {
        return beta;
    }

    public double getDiversificationScore() {
        return diversificationScore;
    }

    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    public void setMaxDrawdown(double maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }

    public void setDiversificationScore(double diversificationScore) {
        this.diversificationScore = diversificationScore;
    }
}
