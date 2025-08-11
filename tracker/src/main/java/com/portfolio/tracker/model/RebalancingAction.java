package com.portfolio.tracker.model;

/**
 * Individual rebalancing action for portfolio optimization
 * Represents a specific buy/sell recommendation
 */
public class RebalancingAction {
    
    private String assetName;
    private String actionType; // "BUY" or "SELL"
    private double currentQuantity;
    private double targetQuantity;
    private double quantityChange;
    private double currentValue;
    private double targetValue;
    private double valueChange;
    private double currentAllocation;
    private double targetAllocation;
    private double allocationChange;
    
    // Transaction details
    private double estimatedPrice;
    private double transactionCost;
    private double taxImpact;
    private double netImpact;
    
    // Priority and urgency
    private int priority; // 1-5 (1 = highest priority)
    private String urgency; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    
    // Constructor
    public RebalancingAction() {}
    
    public RebalancingAction(String assetName, String actionType, double quantityChange, double valueChange) {
        this.assetName = assetName;
        this.actionType = actionType;
        this.quantityChange = quantityChange;
        this.valueChange = valueChange;
    }
    
    // Getters and Setters
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    
    public double getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(double currentQuantity) { this.currentQuantity = currentQuantity; }
    
    public double getTargetQuantity() { return targetQuantity; }
    public void setTargetQuantity(double targetQuantity) { this.targetQuantity = targetQuantity; }
    
    public double getQuantityChange() { return quantityChange; }
    public void setQuantityChange(double quantityChange) { this.quantityChange = quantityChange; }
    
    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
    
    public double getTargetValue() { return targetValue; }
    public void setTargetValue(double targetValue) { this.targetValue = targetValue; }
    
    public double getValueChange() { return valueChange; }
    public void setValueChange(double valueChange) { this.valueChange = valueChange; }
    
    public double getCurrentAllocation() { return currentAllocation; }
    public void setCurrentAllocation(double currentAllocation) { this.currentAllocation = currentAllocation; }
    
    public double getTargetAllocation() { return targetAllocation; }
    public void setTargetAllocation(double targetAllocation) { this.targetAllocation = targetAllocation; }
    
    public double getAllocationChange() { return allocationChange; }
    public void setAllocationChange(double allocationChange) { this.allocationChange = allocationChange; }
    
    public double getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(double estimatedPrice) { this.estimatedPrice = estimatedPrice; }
    
    public double getTransactionCost() { return transactionCost; }
    public void setTransactionCost(double transactionCost) { this.transactionCost = transactionCost; }
    
    public double getTaxImpact() { return taxImpact; }
    public void setTaxImpact(double taxImpact) { this.taxImpact = taxImpact; }
    
    public double getNetImpact() { return netImpact; }
    public void setNetImpact(double netImpact) { this.netImpact = netImpact; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    
    /**
     * Calculate the net impact of this action
     */
    public void calculateNetImpact() {
        this.netImpact = this.valueChange - this.transactionCost - this.taxImpact;
    }
    
    /**
     * Get a human-readable description of the action
     */
    public String getActionDescription() {
        if (actionType.equals("BUY")) {
            return String.format("Buy %.4f %s (%.2f%%)", 
                Math.abs(quantityChange), assetName, Math.abs(allocationChange) * 100);
        } else {
            return String.format("Sell %.4f %s (%.2f%%)", 
                Math.abs(quantityChange), assetName, Math.abs(allocationChange) * 100);
        }
    }
    
    /**
     * Get the action icon for UI display
     */
    public String getActionIcon() {
        return actionType.equals("BUY") ? "📈" : "📉";
    }
    
    /**
     * Get the action color for UI display
     */
    public String getActionColor() {
        return actionType.equals("BUY") ? "text-green-500" : "text-red-500";
    }
}
