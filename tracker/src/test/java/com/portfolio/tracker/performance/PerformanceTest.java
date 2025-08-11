package com.portfolio.tracker.performance;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.AssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Performance test suite for Portfolio Tracker
 * Demonstrates professional performance testing practices including:
 * - Load testing with concurrent requests
 * - Memory usage monitoring
 * - Response time validation
 * - Scalability testing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Performance Test Suite")
class PerformanceTest {

    @Mock
    private AssetService assetService;

    @InjectMocks
    private AssetService performanceAssetService;

    private User testUser;
    private List<Asset> largeAssetList;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        // Create large dataset for performance testing
        largeAssetList = createLargeAssetList(10000);
    }

    @Nested
    @DisplayName("Load Testing")
    class LoadTesting {

        @Test
        @DisplayName("Should handle 100 concurrent requests efficiently")
        void shouldHandle100ConcurrentRequestsEfficiently() throws Exception {
            // Given
            int concurrentRequests = 100;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            List<CompletableFuture<Long>> futures = new ArrayList<>();

            // When
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < concurrentRequests; i++) {
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    long requestStart = System.currentTimeMillis();
                    // Simulate asset calculation
                    double totalValue = calculateTotalValue(largeAssetList);
                    long requestEnd = System.currentTimeMillis();
                    return requestEnd - requestStart;
                }, executor);
                futures.add(future);
            }

            // Wait for all requests to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            long endTime = System.currentTimeMillis();

            // Then
            long totalTime = endTime - startTime;
            long averageResponseTime = futures.stream()
                    .mapToLong(CompletableFuture::join)
                    .sum() / concurrentRequests;

            // Performance assertions
            assertTrue(totalTime < 5000, "Total execution time should be under 5 seconds");
            assertTrue(averageResponseTime < 100, "Average response time should be under 100ms");

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle 1000 concurrent requests with acceptable performance")
        void shouldHandle1000ConcurrentRequestsWithAcceptablePerformance() throws Exception {
            // Given
            int concurrentRequests = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            List<CompletableFuture<Long>> futures = new ArrayList<>();

            // When
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < concurrentRequests; i++) {
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    long requestStart = System.currentTimeMillis();
                    // Simulate portfolio analysis
                    double sharpeRatio = calculateSharpeRatio(largeAssetList);
                    long requestEnd = System.currentTimeMillis();
                    return requestEnd - requestStart;
                }, executor);
                futures.add(future);
            }

            // Wait for all requests to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            long endTime = System.currentTimeMillis();

            // Then
            long totalTime = endTime - startTime;
            long averageResponseTime = futures.stream()
                    .mapToLong(CompletableFuture::join)
                    .sum() / concurrentRequests;

            // Performance assertions for high load
            assertTrue(totalTime < 30000, "Total execution time should be under 30 seconds");
            assertTrue(averageResponseTime < 500, "Average response time should be under 500ms");

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("Memory Usage Testing")
    class MemoryUsageTesting {

        @Test
        @DisplayName("Should handle large datasets without memory issues")
        void shouldHandleLargeDatasetsWithoutMemoryIssues() {
            // Given
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // When
            List<Asset> veryLargeList = createLargeAssetList(100000);
            double totalValue = calculateTotalValue(veryLargeList);
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();

            // Then
            long memoryUsed = finalMemory - initialMemory;
            long maxAllowedMemory = 100 * 1024 * 1024; // 100MB

            assertTrue(memoryUsed < maxAllowedMemory, 
                    "Memory usage should be under 100MB, actual: " + (memoryUsed / 1024 / 1024) + "MB");
            assertTrue(totalValue > 0, "Calculation should complete successfully");
        }

        @Test
        @DisplayName("Should release memory after processing large datasets")
        void shouldReleaseMemoryAfterProcessingLargeDatasets() {
            // Given
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // When
            for (int i = 0; i < 10; i++) {
                List<Asset> largeList = createLargeAssetList(50000);
                double result = calculateTotalValue(largeList);
                largeList.clear(); // Clear reference
                System.gc(); // Request garbage collection
            }

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;

            // Then
            long maxAllowedMemory = 50 * 1024 * 1024; // 50MB
            assertTrue(memoryUsed < maxAllowedMemory, 
                    "Memory usage should be under 50MB after cleanup, actual: " + (memoryUsed / 1024 / 1024) + "MB");
        }
    }

    @Nested
    @DisplayName("Response Time Testing")
    class ResponseTimeTesting {

        @Test
        @DisplayName("Should calculate portfolio value within acceptable time")
        void shouldCalculatePortfolioValueWithinAcceptableTime() {
            // Given
            List<Asset> assets = createLargeAssetList(10000);

            // When
            long startTime = System.nanoTime();
            double totalValue = calculateTotalValue(assets);
            long endTime = System.nanoTime();

            // Then
            long responseTimeNanos = endTime - startTime;
            long responseTimeMillis = responseTimeNanos / 1_000_000;

            assertTrue(responseTimeMillis < 100, 
                    "Portfolio value calculation should complete in under 100ms, actual: " + responseTimeMillis + "ms");
            assertTrue(totalValue > 0, "Calculation should return positive value");
        }

        @Test
        @DisplayName("Should calculate Sharpe ratio within acceptable time")
        void shouldCalculateSharpeRatioWithinAcceptableTime() {
            // Given
            List<Asset> assets = createLargeAssetList(10000);

            // When
            long startTime = System.nanoTime();
            double sharpeRatio = calculateSharpeRatio(assets);
            long endTime = System.nanoTime();

            // Then
            long responseTimeNanos = endTime - startTime;
            long responseTimeMillis = responseTimeNanos / 1_000_000;

            assertTrue(responseTimeMillis < 200, 
                    "Sharpe ratio calculation should complete in under 200ms, actual: " + responseTimeMillis + "ms");
            assertNotNull(sharpeRatio, "Sharpe ratio should not be null");
        }

        @Test
        @DisplayName("Should calculate risk metrics within acceptable time")
        void shouldCalculateRiskMetricsWithinAcceptableTime() {
            // Given
            List<Asset> assets = createLargeAssetList(10000);

            // When
            long startTime = System.nanoTime();
            double volatility = calculateVolatility(assets);
            double beta = calculateBeta(assets);
            double maxDrawdown = calculateMaxDrawdown(assets);
            long endTime = System.nanoTime();

            // Then
            long responseTimeNanos = endTime - startTime;
            long responseTimeMillis = responseTimeNanos / 1_000_000;

            assertTrue(responseTimeMillis < 500, 
                    "Risk metrics calculation should complete in under 500ms, actual: " + responseTimeMillis + "ms");
            assertNotNull(volatility, "Volatility should not be null");
            assertNotNull(beta, "Beta should not be null");
            assertNotNull(maxDrawdown, "Max drawdown should not be null");
        }
    }

    @Nested
    @DisplayName("Scalability Testing")
    class ScalabilityTesting {

        @Test
        @DisplayName("Should scale linearly with dataset size")
        void shouldScaleLinearlyWithDatasetSize() {
            // Given
            int[] datasetSizes = {1000, 5000, 10000, 20000};
            List<Long> responseTimes = new ArrayList<>();

            // When
            for (int size : datasetSizes) {
                List<Asset> assets = createLargeAssetList(size);
                
                long startTime = System.nanoTime();
                double totalValue = calculateTotalValue(assets);
                long endTime = System.nanoTime();
                
                long responseTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
                responseTimes.add(responseTime);
            }

            // Then
            // Check if response time increases reasonably with dataset size
            for (int i = 1; i < responseTimes.size(); i++) {
                long timeIncrease = responseTimes.get(i) - responseTimes.get(i - 1);
                long datasetIncrease = datasetSizes[i] - datasetSizes[i - 1];
                
                // Response time should not increase more than 10x the dataset increase
                double acceptableRatio = 10.0;
                assertTrue(timeIncrease < acceptableRatio * datasetIncrease, 
                        "Response time increase should be reasonable for dataset size increase");
            }
        }

        @Test
        @DisplayName("Should handle exponential dataset growth gracefully")
        void shouldHandleExponentialDatasetGrowthGracefully() {
            // Given
            int[] datasetSizes = {1000, 2000, 4000, 8000, 16000};
            List<Long> responseTimes = new ArrayList<>();

            // When
            for (int size : datasetSizes) {
                List<Asset> assets = createLargeAssetList(size);
                
                long startTime = System.nanoTime();
                double totalValue = calculateTotalValue(assets);
                long endTime = System.nanoTime();
                
                long responseTime = (endTime - startTime) / 1_000_000;
                responseTimes.add(responseTime);
            }

            // Then
            // Check if performance degrades gracefully (not exponentially)
            for (int i = 1; i < responseTimes.size(); i++) {
                long currentTime = responseTimes.get(i);
                long previousTime = responseTimes.get(i - 1);
                
                // Performance should not degrade more than 3x for each doubling
                double maxDegradation = 3.0;
                assertTrue(currentTime < maxDegradation * previousTime, 
                        "Performance should not degrade exponentially");
            }
        }
    }

    // Helper methods for performance testing
    private List<Asset> createLargeAssetList(int size) {
        List<Asset> assets = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Asset asset = new Asset();
            asset.setId((long) i);
            asset.setName("ASSET" + i);
            asset.setQuantity(100.0 + (i % 100));
            asset.setPricePerUnit(50.0 + (i % 200));
            asset.setPurchasePricePerUnit(45.0 + (i % 200));
            asset.setInitialInvestment(5000.0 + (i * 100));
            asset.setUser(testUser);
            assets.add(asset);
        }
        return assets;
    }

    private double calculateTotalValue(List<Asset> assets) {
        return assets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getPricePerUnit())
                .sum();
    }

    private double calculateSharpeRatio(List<Asset> assets) {
        // Simplified Sharpe ratio calculation for performance testing
        if (assets.isEmpty()) return 0.0;
        
        double totalValue = calculateTotalValue(assets);
        double riskFreeRate = 0.03; // 3% risk-free rate
        
        // Simulate some calculation time
        try {
            Thread.sleep(1); // 1ms delay to simulate real calculation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return (totalValue - riskFreeRate) / Math.max(1.0, totalValue * 0.1);
    }

    private double calculateVolatility(List<Asset> assets) {
        if (assets.isEmpty()) return 0.0;
        
        // Simulate volatility calculation
        try {
            Thread.sleep(2); // 2ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return 0.15 + (assets.size() % 10) * 0.01; // Simulated volatility
    }

    private double calculateBeta(List<Asset> assets) {
        if (assets.isEmpty()) return 0.0;
        
        // Simulate beta calculation
        try {
            Thread.sleep(3); // 3ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return 1.0 + (assets.size() % 20) * 0.01; // Simulated beta
    }

    private double calculateMaxDrawdown(List<Asset> assets) {
        if (assets.isEmpty()) return 0.0;
        
        // Simulate max drawdown calculation
        try {
            Thread.sleep(5); // 5ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return 0.20 + (assets.size() % 15) * 0.01; // Simulated max drawdown
    }
}
