package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.PortfolioSummary;
import com.portfolio.tracker.model.RiskMetrics;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private RiskMetricsService riskMetricsService;

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
        double currentValue = asset.getQuantity() * asset.getPricePerUnit();
        double costBasis = asset.getQuantity() * asset.getPurchasePricePerUnit();
        return new AssetMetrics(asset.getId(), currentValue, costBasis);
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
     * Calculate risk metrics reactively with multiple concurrent calculations
     * Shows how complex financial calculations can be parallelized
     */
    public CompletableFuture<RiskMetrics> calculateRiskMetricsReactive(List<Asset> assets) {
        // Risk depends on how the holdings move together, so it is computed on the portfolio as a whole
        return CompletableFuture.supplyAsync(() -> riskMetricsService.calculate(assets), executorService);
    }

    /**
     * Aggregate portfolio data from individual asset calculations
     */
    private PortfolioSummary aggregatePortfolioData(List<AssetMetrics> assetMetrics) {
        double totalValue = assetMetrics.stream().mapToDouble(AssetMetrics::getCurrentValue).sum();
        double totalCost = assetMetrics.stream().mapToDouble(AssetMetrics::getCostBasis).sum();
        // Portfolio ROI = (total value - total cost) / total cost; a simple average of per-asset
        // ROIs would weight a $10 position the same as a $10,000 one
        double roi = totalCost > 0 ? (totalValue - totalCost) / totalCost * 100 : 0.0;
        return new PortfolioSummary(totalValue, roi, assetMetrics.size());
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
        private final double costBasis;

        public AssetMetrics(Long assetId, double currentValue, double costBasis) {
            this.assetId = assetId;
            this.currentValue = currentValue;
            this.costBasis = costBasis;
        }

        public Long getAssetId() { return assetId; }
        public double getCurrentValue() { return currentValue; }
        public double getCostBasis() { return costBasis; }
    }
}
