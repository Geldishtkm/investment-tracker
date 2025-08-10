package com.portfolio.tracker.model;

/**
 * RiskMetrics represents the risk assessment metrics for a portfolio
 * including volatility, drawdown, beta, diversification, ROI, and Sharpe ratio
 */
public class RiskMetrics {
    private double volatility;
    private double maxDrawdown;
    private double beta;
    private double diversificationScore;
    private double roi;
    private double sharpeRatio;

    // Default constructor
    public RiskMetrics() {
        this.volatility = 0.0;
        this.maxDrawdown = 0.0;
        this.beta = 1.0;
        this.diversificationScore = 0.0;
        this.roi = 0.0;
        this.sharpeRatio = 0.0;
    }

    // Constructor with core metrics
    public RiskMetrics(double volatility, double maxDrawdown, double beta, double diversificationScore) {
        this.volatility = Math.max(0.0, volatility); // Volatility cannot be negative
        this.maxDrawdown = Math.max(0.0, Math.min(1.0, maxDrawdown)); // Drawdown between 0 and 1
        this.beta = beta;
        this.diversificationScore = Math.max(0.0, Math.min(100.0, diversificationScore)); // Score between 0 and 100
        this.roi = 0.0;
        this.sharpeRatio = 0.0;
    }

    // Full constructor with all metrics
    public RiskMetrics(double volatility, double maxDrawdown, double beta, double diversificationScore, double roi, double sharpeRatio) {
        this.volatility = Math.max(0.0, volatility);
        this.maxDrawdown = Math.max(0.0, Math.min(1.0, maxDrawdown));
        this.beta = beta;
        this.diversificationScore = Math.max(0.0, Math.min(100.0, diversificationScore));
        this.roi = roi;
        this.sharpeRatio = sharpeRatio;
    }

    // Builder pattern for easy construction
    public static RiskMetricsBuilder builder() {
        return new RiskMetricsBuilder();
    }

    // Getters
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

    // Setters with validation
    public void setVolatility(double volatility) {
        this.volatility = Math.max(0.0, volatility);
    }

    public void setMaxDrawdown(double maxDrawdown) {
        this.maxDrawdown = Math.max(0.0, Math.min(1.0, maxDrawdown));
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }

    public void setDiversificationScore(double diversificationScore) {
        this.diversificationScore = Math.max(0.0, Math.min(100.0, diversificationScore));
    }

    public void setRoi(double roi) {
        this.roi = roi;
    }

    public void setSharpeRatio(double sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    // Helper methods for risk assessment
    public boolean isHighRisk() {
        return volatility > 0.3 || maxDrawdown > 0.5 || beta > 1.5;
    }

    public boolean isLowRisk() {
        return volatility < 0.1 && maxDrawdown < 0.2 && beta < 0.8;
    }

    public boolean isWellDiversified() {
        return diversificationScore > 70.0;
    }

    public String getRiskLevel() {
        if (isHighRisk()) return "HIGH";
        if (isLowRisk()) return "LOW";
        return "MEDIUM";
    }

    public String getDiversificationLevel() {
        if (diversificationScore >= 80) return "EXCELLENT";
        if (diversificationScore >= 60) return "GOOD";
        if (diversificationScore >= 40) return "FAIR";
        return "POOR";
    }

    @Override
    public String toString() {
        return "RiskMetrics{" +
                "volatility=" + String.format("%.2f", volatility) +
                ", maxDrawdown=" + String.format("%.2f", maxDrawdown) +
                ", beta=" + String.format("%.2f", beta) +
                ", diversificationScore=" + String.format("%.1f", diversificationScore) +
                ", roi=" + String.format("%.2f", roi) +
                ", sharpeRatio=" + String.format("%.2f", sharpeRatio) +
                ", riskLevel='" + getRiskLevel() + '\'' +
                '}';
    }

    // Builder class for fluent API
    public static class RiskMetricsBuilder {
        private double volatility = 0.0;
        private double maxDrawdown = 0.0;
        private double beta = 1.0;
        private double diversificationScore = 0.0;
        private double roi = 0.0;
        private double sharpeRatio = 0.0;

        public RiskMetricsBuilder volatility(double volatility) {
            this.volatility = Math.max(0.0, volatility);
            return this;
        }

        public RiskMetricsBuilder maxDrawdown(double maxDrawdown) {
            this.maxDrawdown = Math.max(0.0, Math.min(1.0, maxDrawdown));
            return this;
        }

        public RiskMetricsBuilder beta(double beta) {
            this.beta = beta;
            return this;
        }

        public RiskMetricsBuilder diversificationScore(double diversificationScore) {
            this.diversificationScore = Math.max(0.0, Math.min(100.0, diversificationScore));
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
}