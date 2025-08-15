package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.PortfolioSummary;
import com.portfolio.tracker.model.RiskMetrics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Reactive Portfolio Service demonstrating Spring WebFlux capabilities
 * 
 * This service shows how reactive programming can handle:
 * - Multiple concurrent portfolio calculations
 * - Non-blocking market data processing
 * - Efficient resource utilization
 * - Scalable performance under load
 */
@Service
public class ReactivePortfolioService {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Calculate portfolio summary reactively - processes multiple assets simultaneously
     * Instead of waiting for each calculation to complete, it processes them in parallel
     */
    public CompletableFuture<PortfolioSummary> calculatePortfolioSummaryReactive(List<Asset> assets) {
        return CompletableFuture.supplyAsync(() -> {
            List<AssetMetrics> assetMetrics = assets.parallelStream()
                    .map(this::calculateAssetMetricsReactive)
                    .collect(Collectors.toList());
            return aggregatePortfolioData(assetMetrics);
        }, executorService);
    }

    /**
     * Calculate metrics for a single asset reactively
     * This simulates a potentially slow operation (like API calls) without blocking
     */
    private AssetMetrics calculateAssetMetricsReactive(Asset asset) {
        // Simulate some calculation work
        try {
            Thread.sleep(10); // Simulate 10ms of work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        double currentValue = asset.getQuantity() * asset.getPricePerUnit();
        double initialValue = asset.getQuantity() * asset.getPurchasePricePerUnit();
        double roi = ((currentValue - initialValue) / initialValue) * 100;
        
        return new AssetMetrics(asset.getId(), currentValue, roi);
    }

    /**
     * Process multiple portfolios concurrently - shows the power of reactive programming
     * This is what makes BlackRock's systems handle thousands of users simultaneously
     */
    public CompletableFuture<List<PortfolioSummary>> processMultiplePortfoliosReactive(List<List<Asset>> portfolios) {
        List<CompletableFuture<PortfolioSummary>> futures = portfolios.stream()
                .map(this::calculatePortfolioSummaryReactive)
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * Simulate real-time market data updates
     * This demonstrates how reactive streams can handle continuous data flow
     */
    public List<MarketUpdate> getRealTimeMarketUpdates() {
        // For demo purposes, generate 10 market updates
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(tick -> new MarketUpdate("Market Update " + tick, System.currentTimeMillis()))
                .collect(Collectors.toList());
    }

    /**
     * Calculate risk metrics reactively with multiple concurrent calculations
     * Shows how complex financial calculations can be parallelized
     */
    public CompletableFuture<RiskMetrics> calculateRiskMetricsReactive(List<Asset> assets) {
        return CompletableFuture.supplyAsync(() -> {
            List<AssetRisk> assetRisks = assets.parallelStream()
                    .map(this::calculateAssetRiskReactive)
                    .collect(Collectors.toList());
            return aggregateRiskMetrics(assetRisks);
        }, executorService);
    }

    /**
     * Calculate risk for individual asset (simulated complex calculation)
     */
    private AssetRisk calculateAssetRiskReactive(Asset asset) {
        // Simulate complex risk calculation
        double volatility = Math.random() * 0.3; // Simulated volatility
        double beta = 0.8 + Math.random() * 0.4; // Simulated beta
        
        return new AssetRisk(asset.getId(), volatility, beta);
    }

    /**
     * Aggregate portfolio data from individual asset calculations
     */
    private PortfolioSummary aggregatePortfolioData(List<AssetMetrics> assetMetrics) {
        double totalValue = assetMetrics.stream()
                .mapToDouble(AssetMetrics::getCurrentValue)
                .sum();
        
        double averageROI = assetMetrics.stream()
                .mapToDouble(AssetMetrics::getRoi)
                .average()
                .orElse(0.0);

        return new PortfolioSummary(totalValue, averageROI, assetMetrics.size());
    }

    /**
     * Aggregate risk metrics from individual asset risk calculations
     */
    private RiskMetrics aggregateRiskMetrics(List<AssetRisk> assetRisks) {
        double averageVolatility = assetRisks.stream()
                .mapToDouble(AssetRisk::getVolatility)
                .average()
                .orElse(0.0);
        
        double averageBeta = assetRisks.stream()
                .mapToDouble(AssetRisk::getBeta)
                .average()
                .orElse(0.0);

        return new RiskMetrics(averageVolatility, 0.0, averageBeta, 50.0);
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        executorService.shutdown();
    }

    // Helper classes for reactive operations
    public static class AssetMetrics {
        private final Long assetId;
        private final double currentValue;
        private final double roi;

        public AssetMetrics(Long assetId, double currentValue, double roi) {
            this.assetId = assetId;
            this.currentValue = currentValue;
            this.roi = roi;
        }

        public Long getAssetId() { return assetId; }
        public double getCurrentValue() { return currentValue; }
        public double getRoi() { return roi; }
    }

    public static class AssetRisk {
        private final Long assetId;
        private final double volatility;
        private final double beta;

        public AssetRisk(Long assetId, double volatility, double beta) {
            this.assetId = assetId;
            this.volatility = volatility;
            this.beta = beta;
        }

        public Long getAssetId() { return assetId; }
        public double getVolatility() { return volatility; }
        public double getBeta() { return beta; }
    }

    public static class MarketUpdate {
        private final String message;
        private final long timestamp;

        public MarketUpdate(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
    }
}
