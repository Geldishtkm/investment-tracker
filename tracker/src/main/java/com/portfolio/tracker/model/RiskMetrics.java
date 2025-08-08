package com.portfolio.tracker.model;

public class RiskMetrics {
    private double volatility;
    private double maxDrawdown;
    private double beta;
    private double diversificationScore;
    private double roi;
    private double sharpeRatio;

    // Default constructor
    public RiskMetrics() {
    }

    // Constructor for backward compatibility
    public RiskMetrics(double volatility, double maxDrawdown, double beta, double diversificationScore) {
        this.volatility = volatility;
        this.maxDrawdown = maxDrawdown;
        this.beta = beta;
        this.diversificationScore = diversificationScore;
        this.roi = 0.0;
        this.sharpeRatio = 0.0;
    }

    // Builder constructor
    public RiskMetrics(double volatility, double maxDrawdown, double beta, double diversificationScore, double roi, double sharpeRatio) {
        this.volatility = volatility;
        this.maxDrawdown = maxDrawdown;
        this.beta = beta;
        this.diversificationScore = diversificationScore;
        this.roi = roi;
        this.sharpeRatio = sharpeRatio;
    }

    // Builder pattern
    public static RiskMetricsBuilder builder() {
        return new RiskMetricsBuilder();
    }

    public static class RiskMetricsBuilder {
        private double volatility;
        private double maxDrawdown;
        private double beta;
        private double diversificationScore;
        private double roi;
        private double sharpeRatio;

        public RiskMetricsBuilder volatility(double volatility) {
            this.volatility = volatility;
            return this;
        }

        public RiskMetricsBuilder maxDrawdown(double maxDrawdown) {
            this.maxDrawdown = maxDrawdown;
            return this;
        }

        public RiskMetricsBuilder beta(double beta) {
            this.beta = beta;
            return this;
        }

        public RiskMetricsBuilder diversificationScore(double diversificationScore) {
            this.diversificationScore = diversificationScore;
            return this;
        }

        public RiskMetricsBuilder roi(double roi) {
            this.roi = roi;
            return this;
        }

        public RiskMetricsBuilder sharpeRatio(double sharpeRatio) {
            this.sharpeRatio = sharpeRatio;
            return this;
        }

        public RiskMetrics build() {
            return new RiskMetrics(volatility, maxDrawdown, beta, diversificationScore, roi, sharpeRatio);
        }
    }

    // Getters and setters
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

    public double getRoi() {
        return roi;
    }

    public double getSharpeRatio() {
        return sharpeRatio;
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

    public void setRoi(double roi) {
        this.roi = roi;
    }

    public void setSharpeRatio(double sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }
}