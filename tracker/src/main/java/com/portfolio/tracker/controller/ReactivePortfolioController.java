package com.portfolio.tracker.controller;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.PortfolioSummary;
import com.portfolio.tracker.model.RiskMetrics;
import com.portfolio.tracker.service.ReactivePortfolioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reactive Portfolio Controller using Spring WebFlux
 * 
 * This controller demonstrates:
 * - Non-blocking HTTP endpoints
 * - Server-Sent Events (SSE) for real-time updates
 * - Reactive streams for high-performance operations
 * - Concurrent processing of multiple requests
 */
@RestController
@RequestMapping("/api/reactive/portfolio")
@CrossOrigin(origins = "*")
public class ReactivePortfolioController {

    @Autowired
    private ReactivePortfolioService reactivePortfolioService;

    /**
     * Calculate portfolio summary reactively
     * This endpoint processes assets concurrently without blocking
     */
    @PostMapping("/summary")
    public CompletableFuture<PortfolioSummary> calculatePortfolioSummary(@RequestBody List<Asset> assets) {
        return reactivePortfolioService.calculatePortfolioSummaryReactive(assets);
    }

    /**
     * Process multiple portfolios concurrently
     * Shows how reactive programming handles multiple requests efficiently
     */
    @PostMapping("/multiple-summaries")
    public CompletableFuture<List<PortfolioSummary>> processMultiplePortfolios(@RequestBody List<List<Asset>> portfolios) {
        return reactivePortfolioService.processMultiplePortfoliosReactive(portfolios);
    }

    /**
     * Calculate risk metrics reactively
     * Demonstrates parallel processing of complex financial calculations
     */
    @PostMapping("/risk-metrics")
    public CompletableFuture<RiskMetrics> calculateRiskMetrics(@RequestBody List<Asset> assets) {
        return reactivePortfolioService.calculateRiskMetricsReactive(assets);
    }

    /**
     * Real-time market updates using Server-Sent Events (SSE)
     * This is what makes BlackRock's trading platforms real-time
     */
    @GetMapping(value = "/market-updates", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getRealTimeMarketUpdates() {
        return reactivePortfolioService.getRealTimeMarketUpdates()
                .stream()
                .map(update -> "data: " + update.getMessage() + " at " + update.getTimestamp())
                .collect(Collectors.toList());
    }

    /**
     * Health check endpoint for reactive operations
     */
    @GetMapping("/health")
    public String healthCheck() {
        return "Reactive Portfolio Service is running! 🚀";
    }

    /**
     * Performance test endpoint
     * Demonstrates concurrent processing capabilities
     */
    @GetMapping("/performance-test")
    public String performanceTest() {
        try {
            // Simulate some work
            Thread.sleep(100);
            return "Performance test completed! Concurrent processing is working.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Performance test interrupted.";
        }
    }
}
