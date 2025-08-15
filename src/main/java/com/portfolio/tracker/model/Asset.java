package com.portfolio.tracker.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "price_per_unit", nullable = false)
    private double pricePerUnit;

    @Column(name = "purchase_price_per_unit", nullable = false)
    private double purchasePricePerUnit;

    @Column(name = "initial_investment")
    private double initialInvestment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    // Default constructor required by JPA
    public Asset() {}

    // Constructor for creating new assets
    public Asset(String name, double quantity, double pricePerUnit, double purchasePricePerUnit) {
        this.name = name;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.purchasePricePerUnit = purchasePricePerUnit;
        this.initialInvestment = quantity * purchasePricePerUnit;
    }

    // Constructor for creating assets with current price
    public Asset(String name, double quantity, double pricePerUnit) {
        this(name, quantity, pricePerUnit, pricePerUnit);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(double pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }

    public double getPurchasePricePerUnit() {
        return purchasePricePerUnit;
    }

    public void setPurchasePricePerUnit(double purchasePricePerUnit) {
        this.purchasePricePerUnit = purchasePricePerUnit;
    }

    public double getInitialInvestment() {
        return initialInvestment;
    }

    public void setInitialInvestment(double initialInvestment) {
        this.initialInvestment = initialInvestment;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    // Helper method to calculate current total value
    public double getCurrentTotalValue() {
        return quantity * pricePerUnit;
    }

    // Helper method to calculate total gain/loss
    public double getTotalGainLoss() {
        return getCurrentTotalValue() - initialInvestment;
    }

    // Helper method to calculate gain/loss percentage
    public double getGainLossPercentage() {
        if (initialInvestment == 0) return 0.0;
        return (getTotalGainLoss() / initialInvestment) * 100;
    }

    @Override
    public String toString() {
        return "Asset{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", currentPrice=" + pricePerUnit +
                ", purchasePrice=" + purchasePricePerUnit +
                ", initialInvestment=" + initialInvestment +
                '}';
    }
}
