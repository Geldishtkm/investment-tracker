package com.portfolio.tracker.model;

import jakarta.persistence.*;

@Entity // 📦 Tells Spring this is a database table
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 🔄 Auto-generates the ID
    private Long id;

    private String name; // 🏷️ Asset name (e.g. Bitcoin)

    private double quantity; // 📦 How much the user owns

    @Column(name = "price_per_unit") // 💵 Map camelCase to snake_case in database
    private double pricePerUnit;

    // 👉 Constructor with no arguments (required by Spring)
    public Asset() {}

    // 🧱 Constructor to create an asset easily
    public Asset(String name, double quantity, double pricePerUnit) {
        this.name = name;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
    }

    // 🧰 Getters and Setters (used to read/write values)
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
}
