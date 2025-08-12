package com.portfolio.tracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Portfolio Summary Model
 * 
 * Represents aggregated portfolio data including:
 * - Total portfolio value
 * - Average return on investment
 * - Number of assets
 * - Timestamp of calculation
 */
@Entity
@Table(name = "portfolio_summaries")
public class PortfolioSummary {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private double totalValue;
    private double averageROI;
    private int numberOfAssets;
    private LocalDateTime calculatedAt;
    
    // Default constructor for JPA
    public PortfolioSummary() {
        this.calculatedAt = LocalDateTime.now();
    }
    
    // Constructor for reactive service
    public PortfolioSummary(double totalValue, double averageROI, int numberOfAssets) {
        this.totalValue = totalValue;
        this.averageROI = averageROI;
        this.numberOfAssets = numberOfAssets;
        this.calculatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public double getTotalValue() {
        return totalValue;
    }
    
    public void setTotalValue(double totalValue) {
        this.totalValue = totalValue;
    }
    
    public double getAverageROI() {
        return averageROI;
    }
    
    public void setAverageROI(double averageROI) {
        this.averageROI = averageROI;
    }
    
    public int getNumberOfAssets() {
        return numberOfAssets;
    }
    
    public void setNumberOfAssets(int numberOfAssets) {
        this.numberOfAssets = numberOfAssets;
    }
    
    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }
    
    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
    
    @Override
    public String toString() {
        return "PortfolioSummary{" +
                "id=" + id +
                ", totalValue=" + totalValue +
                ", averageROI=" + averageROI +
                ", numberOfAssets=" + numberOfAssets +
                ", calculatedAt=" + calculatedAt +
                '}';
    }
}
