package com.portfolio.tracker.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Last daily price history fetched from CoinGecko for a coin, stored as the raw [timestamp, price]
 * JSON array so it can be served again after a restart or during a rate limit.
 */
@Entity
@Table(name = "price_history_cache")
@IdClass(PriceHistoryCacheEntry.Key.class)
public class PriceHistoryCacheEntry {

    @Id
    @Column(name = "coin_id")
    private String coinId;

    @Id
    @Column(name = "vs_currency", length = 10)
    private String vsCurrency;

    @Column(name = "points_json", nullable = false, columnDefinition = "TEXT")
    private String pointsJson;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected PriceHistoryCacheEntry() {
    }

    public PriceHistoryCacheEntry(String coinId, String vsCurrency, String pointsJson, LocalDateTime fetchedAt) {
        this.coinId = coinId;
        this.vsCurrency = vsCurrency;
        this.pointsJson = pointsJson;
        this.fetchedAt = fetchedAt;
    }

    public String getCoinId() { return coinId; }
    public String getVsCurrency() { return vsCurrency; }
    public String getPointsJson() { return pointsJson; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }

    /** Composite primary key (coin, currency). */
    public static class Key implements Serializable {
        private String coinId;
        private String vsCurrency;

        public Key() {
        }

        public Key(String coinId, String vsCurrency) {
            this.coinId = coinId;
            this.vsCurrency = vsCurrency;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(coinId, k.coinId) && Objects.equals(vsCurrency, k.vsCurrency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(coinId, vsCurrency);
        }
    }
}
