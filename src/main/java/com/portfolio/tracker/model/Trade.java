package com.portfolio.tracker.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One buy or sell in the user's trade ledger. Holdings in the assets table are the running result
 * of these trades; the ledger keeps the history the behavioral analytics need.
 */
@Entity
@Table(name = "trades")
public class Trade {

    public enum Side { BUY, SELL }

    public enum Source { MANUAL, IMPORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private double price;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "date_known", nullable = false)
    private boolean dateKnown = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source = Source.MANUAL;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Trade() {
    }

    public Trade(Long userId, Long assetId, String assetName, Side side, double quantity, double price,
                 LocalDate tradeDate, boolean dateKnown, Source source) {
        this.userId = userId;
        this.assetId = assetId;
        this.assetName = assetName;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.tradeDate = tradeDate;
        this.dateKnown = dateKnown;
        this.source = source;
        this.createdAt = LocalDateTime.now();
    }

    /** Fix the quantity and price of a holding's opening buy when the holding itself is corrected. */
    public void correct(String assetName, double quantity, double price) {
        this.assetName = assetName;
        this.quantity = quantity;
        this.price = price;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAssetId() { return assetId; }
    public String getAssetName() { return assetName; }
    public Side getSide() { return side; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public LocalDate getTradeDate() { return tradeDate; }
    public boolean isDateKnown() { return dateKnown; }
    public Source getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
