package com.portfolio.tracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "esg_scores")
public class ESGScore {

    @Id
    @Column(name = "ticker", nullable = false, unique = true)
    private String ticker; // Example: "AAPL"

    @Column(name = "environmental_score")
    private double environmentalScore;

    @Column(name = "social_score")
    private double socialScore;

    @Column(name = "governance_score")
    private double governanceScore;

    @Column(name = "total_score")
    private double totalScore;

    @OneToOne
    @JoinColumn(name = "asset_id")
    private Asset asset;

    // --- Constructors ---

    public ESGScore() {
    }

    public ESGScore(String ticker, double environmentalScore, double socialScore,
                    double governanceScore, double totalScore, Asset asset) {
        this.ticker = ticker;
        this.environmentalScore = environmentalScore;
        this.socialScore = socialScore;
        this.governanceScore = governanceScore;
        this.totalScore = totalScore;
        this.asset = asset;
    }

    // --- Getters and Setters ---

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public double getEnvironmentalScore() {
        return environmentalScore;
    }

    public void setEnvironmentalScore(double environmentalScore) {
        this.environmentalScore = environmentalScore;
    }

    public double getSocialScore() {
        return socialScore;
    }

    public void setSocialScore(double socialScore) {
        this.socialScore = socialScore;
    }

    public double getGovernanceScore() {
        return governanceScore;
    }

    public void setGovernanceScore(double governanceScore) {
        this.governanceScore = governanceScore;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    // --- Optional: toString() ---

    @Override
    public String toString() {
        return "ESGScore{" +
                "ticker='" + ticker + '\'' +
                ", environmentalScore=" + environmentalScore +
                ", socialScore=" + socialScore +
                ", governanceScore=" + governanceScore +
                ", totalScore=" + totalScore +
                ", asset=" + (asset != null ? asset.getId() : "null") +
                '}';
    }
}
