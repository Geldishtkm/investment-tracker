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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private RiskMetricsService riskMetricsService;

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
        @DisplayName("Portfolio ROI weights each position by its cost, not a simple average")
        void shouldWeightRoiByCostBasis() throws ExecutionException, InterruptedException {
            // Asset 1: cost 10 x 150 = 1,500, value 1,600 (+6.67%)
            // Asset 2: cost 5 x 2,800 = 14,000, value 14,500 (+3.57%)
            // Portfolio: (16,100 - 15,500) / 15,500 = +3.87%, not the simple average +5.12%
            PortfolioSummary summary = reactivePortfolioService.calculatePortfolioSummaryReactive(testAssets).get();

            assertEquals(16100.0, summary.getTotalValue(), 1e-9);
            assertEquals((16100.0 - 15500.0) / 15500.0 * 100, summary.getAverageROI(), 1e-9);
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
            // Given
            RiskMetrics expected = RiskMetrics.builder().volatility(0.45).beta(0.9).build();
            when(riskMetricsService.calculate(testAssets)).thenReturn(expected);

            // When
            CompletableFuture<RiskMetrics> result = reactivePortfolioService.calculateRiskMetricsReactive(testAssets);

            // Then
            RiskMetrics riskMetrics = result.get();
            assertNotNull(riskMetrics);
            assertTrue(riskMetrics.getVolatility() >= 0);
            assertEquals(0.45, riskMetrics.getVolatility());
            assertEquals(0.9, riskMetrics.getBeta());
            verify(riskMetricsService).calculate(testAssets);
        }

        @Test
        @DisplayName("Should handle single asset for risk calculation")
        void shouldHandleSingleAssetForRiskCalculation() throws ExecutionException, InterruptedException {
            // Given
            List<Asset> singleAsset = Arrays.asList(testAsset1);
            when(riskMetricsService.calculate(singleAsset))
                    .thenReturn(RiskMetrics.builder().volatility(0.6).beta(1.0).build());

            // When
            CompletableFuture<RiskMetrics> result = reactivePortfolioService.calculateRiskMetricsReactive(singleAsset);

            // Then
            RiskMetrics riskMetrics = result.get();
            assertNotNull(riskMetrics);
            assertTrue(riskMetrics.getVolatility() >= 0);
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
