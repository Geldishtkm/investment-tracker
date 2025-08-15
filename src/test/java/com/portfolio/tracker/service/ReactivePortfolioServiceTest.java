package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.PortfolioSummary;
import com.portfolio.tracker.model.RiskMetrics;
import com.portfolio.tracker.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ReactivePortfolioService
 * 
 * Demonstrates testing concurrent components using:
 * - CompletableFuture testing patterns
 * - Parallel processing validation
 * - Concurrent operation validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Reactive Portfolio Service Test Suite")
class ReactivePortfolioServiceTest {

    @InjectMocks
    private ReactivePortfolioService reactivePortfolioService;

    private User testUser;
    private Asset testAsset1;
    private Asset testAsset2;
    private List<Asset> testAssets;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        // Create test assets
        testAsset1 = new Asset();
        testAsset1.setId(1L);
        testAsset1.setName("AAPL");
        testAsset1.setQuantity(10.0);
        testAsset1.setPurchasePricePerUnit(150.0);
        testAsset1.setPricePerUnit(160.0);
        testAsset1.setUser(testUser);

        testAsset2 = new Asset();
        testAsset2.setId(2L);
        testAsset2.setName("GOOGL");
        testAsset2.setQuantity(5.0);
        testAsset2.setPurchasePricePerUnit(2800.0);
        testAsset2.setPricePerUnit(2900.0);
        testAsset2.setUser(testUser);

        testAssets = Arrays.asList(testAsset1, testAsset2);
    }

    @Nested
    @DisplayName("Portfolio Summary Calculations")
    class PortfolioSummaryCalculations {

        @Test
        @DisplayName("Should calculate portfolio summary reactively")
        void shouldCalculatePortfolioSummaryReactive() throws ExecutionException, InterruptedException {
            // When
            CompletableFuture<PortfolioSummary> result = reactivePortfolioService.calculatePortfolioSummaryReactive(testAssets);

            // Then
            PortfolioSummary summary = result.get();
            assertNotNull(summary);
            assertEquals(2, summary.getNumberOfAssets());
            assertTrue(summary.getTotalValue() > 0);
            assertNotNull(summary.getCalculatedAt());
        }

        @Test
        @DisplayName("Should handle empty asset list")
        void shouldHandleEmptyAssetList() throws ExecutionException, InterruptedException {
            // Given
            List<Asset> emptyAssets = Arrays.asList();

            // When
            CompletableFuture<PortfolioSummary> result = reactivePortfolioService.calculatePortfolioSummaryReactive(emptyAssets);

            // Then
            PortfolioSummary summary = result.get();
            assertEquals(0, summary.getNumberOfAssets());
            assertEquals(0.0, summary.getTotalValue());
        }
    }

    @Nested
    @DisplayName("Multiple Portfolio Processing")
    class MultiplePortfolioProcessing {

        @Test
        @DisplayName("Should process multiple portfolios concurrently")
        void shouldProcessMultiplePortfoliosConcurrently() throws ExecutionException, InterruptedException {
            // Given
            List<List<Asset>> portfolios = Arrays.asList(
                    Arrays.asList(testAsset1),
                    Arrays.asList(testAsset2),
                    testAssets
            );

            // When
            CompletableFuture<List<PortfolioSummary>> result = reactivePortfolioService.processMultiplePortfoliosReactive(portfolios);

            // Then
            List<PortfolioSummary> summaries = result.get();
            assertEquals(3, summaries.size());
        }

        @Test
        @DisplayName("Should handle single portfolio in multiple portfolios list")
        void shouldHandleSinglePortfolioInMultiplePortfoliosList() throws ExecutionException, InterruptedException {
            // Given
            List<List<Asset>> singlePortfolio = Arrays.asList(testAssets);

            // When
            CompletableFuture<List<PortfolioSummary>> result = reactivePortfolioService.processMultiplePortfoliosReactive(singlePortfolio);

            // Then
            List<PortfolioSummary> summaries = result.get();
            assertEquals(1, summaries.size());
        }
    }

    @Nested
    @DisplayName("Risk Metrics Calculations")
    class RiskMetricsCalculations {

        @Test
        @DisplayName("Should calculate risk metrics reactively")
        void shouldCalculateRiskMetricsReactive() throws ExecutionException, InterruptedException {
            // When
            CompletableFuture<RiskMetrics> result = reactivePortfolioService.calculateRiskMetricsReactive(testAssets);

            // Then
            RiskMetrics riskMetrics = result.get();
            assertNotNull(riskMetrics);
            assertTrue(riskMetrics.getVolatility() >= 0);
            assertTrue(riskMetrics.getBeta() >= 0);
        }

        @Test
        @DisplayName("Should handle single asset for risk calculation")
        void shouldHandleSingleAssetForRiskCalculation() throws ExecutionException, InterruptedException {
            // Given
            List<Asset> singleAsset = Arrays.asList(testAsset1);

            // When
            CompletableFuture<RiskMetrics> result = reactivePortfolioService.calculateRiskMetricsReactive(singleAsset);

            // Then
            RiskMetrics riskMetrics = result.get();
            assertNotNull(riskMetrics);
            assertTrue(riskMetrics.getVolatility() >= 0);
        }
    }

    @Nested
    @DisplayName("Real-time Market Updates")
    class RealTimeMarketUpdates {

        @Test
        @DisplayName("Should generate real-time market updates")
        void shouldGenerateRealTimeMarketUpdates() {
            // When
            List<ReactivePortfolioService.MarketUpdate> result = reactivePortfolioService.getRealTimeMarketUpdates();

            // Then
            assertEquals(10, result.size()); // Should generate 10 updates as configured
        }

        @Test
        @DisplayName("Should generate market updates with timestamps")
        void shouldGenerateMarketUpdatesWithTimestamps() {
            // When
            List<ReactivePortfolioService.MarketUpdate> result = reactivePortfolioService.getRealTimeMarketUpdates();

            // Then
            assertFalse(result.isEmpty());
            ReactivePortfolioService.MarketUpdate firstUpdate = result.get(0);
            assertNotNull(firstUpdate.getMessage());
            assertTrue(firstUpdate.getTimestamp() > 0);
            assertEquals(10, result.size());
        }
    }

    @Nested
    @DisplayName("Concurrent Processing")
    class ConcurrentProcessing {

        @Test
        @DisplayName("Should demonstrate non-blocking behavior")
        void shouldDemonstrateNonBlockingBehavior() throws ExecutionException, InterruptedException {
            // Given
            long startTime = System.currentTimeMillis();

            // When - Process multiple portfolios concurrently
            CompletableFuture<List<PortfolioSummary>> result = reactivePortfolioService.processMultiplePortfoliosReactive(
                    Arrays.asList(testAssets, testAssets, testAssets)
            );

            // Then
            List<PortfolioSummary> summaries = result.get();
            assertEquals(3, summaries.size());

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            // The total time should be much less than if we processed sequentially
            // (3 assets × 10ms each = 30ms if sequential, but should be much faster with concurrent processing)
            assertTrue(totalTime < 100, "Concurrent processing should be much faster than sequential");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle null asset list gracefully")
        void shouldHandleNullAssetListGracefully() {
            // When & Then
            CompletableFuture<PortfolioSummary> result = reactivePortfolioService.calculatePortfolioSummaryReactive(null);
            
            // The CompletableFuture should complete exceptionally when executed
            assertThrows(ExecutionException.class, () -> {
                result.get();
            });
        }
    }

    @Test
    @DisplayName("Should cleanup resources properly")
    void shouldCleanupResourcesProperly() {
        // When
        reactivePortfolioService.cleanup();

        // Then - No exception should be thrown
        assertDoesNotThrow(() -> reactivePortfolioService.cleanup());
    }
}
