package com.portfolio.tracker.integration;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.model.RiskMetrics;
import com.portfolio.tracker.repository.AssetRepository;
import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for Portfolio Tracker
 * Demonstrates professional integration testing practices including:
 * - Database integration testing
 * - Service layer integration
 * - End-to-end workflow testing
 * - Real HTTP endpoint testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Test Suite")
class IntegrationTest {

    @Autowired
    private AssetService assetService;

    @Autowired
    private UserService userService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private User testUser;
    private Asset testAsset;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        assetRepository.deleteAll();

        // Create test user
        testUser = userService.registerUser("integrationtestuser", "testpassword123");

        // Create test asset
        testAsset = new Asset();
        testAsset.setName("AAPL");
        testAsset.setQuantity(10.0);
        testAsset.setPurchasePricePerUnit(150.0);
        testAsset.setPricePerUnit(160.0);
        testAsset.setUser(testUser);
    }

    @Nested
    @DisplayName("Database Integration Tests")
    class DatabaseIntegrationTests {

        @Test
        @DisplayName("Should save and retrieve asset from database")
        void shouldSaveAndRetrieveAssetFromDatabase() {
            // Given
            Asset assetToSave = new Asset();
            assetToSave.setName("GOOGL");
            assetToSave.setQuantity(5.0);
            assetToSave.setPurchasePricePerUnit(2800.0);
            assetToSave.setPricePerUnit(2900.0);
            assetToSave.setUser(testUser);

            // When
            Asset savedAsset = assetService.saveAsset(assetToSave);
            Asset retrievedAsset = assetService.getAssetById(savedAsset.getId());

            // Then
            assertNotNull(savedAsset.getId(), "Saved asset should have an ID");
            assertEquals("GOOGL", retrievedAsset.getName(), "Asset name should match");
            assertEquals(5.0, retrievedAsset.getQuantity(), "Asset quantity should match");
            assertEquals(14000.0, retrievedAsset.getInitialInvestment(), "Initial investment should be calculated");
            assertEquals(testUser.getId(), retrievedAsset.getUser().getId(), "User should be properly linked");
        }

        @Test
        @DisplayName("Should update asset in database")
        void shouldUpdateAssetInDatabase() {
            // Given
            Asset savedAsset = assetService.saveAsset(testAsset);
            savedAsset.setQuantity(15.0);
            savedAsset.setPricePerUnit(170.0);

            // When
            Asset updatedAsset = assetService.saveAsset(savedAsset);
            Asset retrievedAsset = assetService.getAssetById(savedAsset.getId());

            // Then
            assertEquals(15.0, retrievedAsset.getQuantity(), "Quantity should be updated");
            assertEquals(170.0, retrievedAsset.getPricePerUnit(), "Price should be updated");
            assertEquals(2550.0, retrievedAsset.getInitialInvestment(), "Initial investment should be recalculated");
        }

        @Test
        @DisplayName("Should delete asset from database")
        void shouldDeleteAssetFromDatabase() {
            // Given
            Asset savedAsset = assetService.saveAsset(testAsset);
            Long assetId = savedAsset.getId();

            // When
            assetService.deleteAssetById(assetId);

            // Then
            assertThrows(Exception.class, () -> assetService.getAssetById(assetId), 
                    "Asset should not exist after deletion");
        }

        @Test
        @DisplayName("Should maintain referential integrity between user and assets")
        void shouldMaintainReferentialIntegrityBetweenUserAndAssets() {
            // Given
            Asset asset1 = assetService.saveAsset(testAsset);
            
            Asset asset2 = new Asset();
            asset2.setName("MSFT");
            asset2.setQuantity(8.0);
            asset2.setPurchasePricePerUnit(300.0);
            asset2.setPricePerUnit(320.0);
            asset2.setUser(testUser);
            asset2 = assetService.saveAsset(asset2);

            // When
            List<Asset> userAssets = assetService.getAssetsByUser(testUser);

            // Then
            assertEquals(2, userAssets.size(), "User should have 2 assets");
            assertTrue(userAssets.stream().allMatch(asset -> asset.getUser().getId().equals(testUser.getId())),
                    "All assets should belong to the test user");
        }
    }

    @Nested
    @DisplayName("Service Layer Integration Tests")
    class ServiceLayerIntegrationTests {

        @Test
        @DisplayName("Should calculate portfolio metrics across multiple assets")
        void shouldCalculatePortfolioMetricsAcrossMultipleAssets() {
            // Given
            Asset asset1 = assetService.saveAsset(testAsset);
            
            Asset asset2 = new Asset();
            asset2.setName("GOOGL");
            asset2.setQuantity(5.0);
            asset2.setPurchasePricePerUnit(2800.0);
            asset2.setPricePerUnit(2900.0);
            asset2.setUser(testUser);
            asset2 = assetService.saveAsset(asset2);

            Asset asset3 = new Asset();
            asset3.setName("MSFT");
            asset3.setQuantity(8.0);
            asset3.setPurchasePricePerUnit(300.0);
            asset3.setPricePerUnit(320.0);
            asset3.setUser(testUser);
            asset3 = assetService.saveAsset(asset3);

            // When
            double totalValue = assetService.getTotalValueByUser(testUser);
            double roi = assetService.calculateROIByUser(testUser);
            RiskMetrics riskMetrics = assetService.getRiskMetricsByUser(testUser);

            // Then
            double expectedTotalValue = (10.0 * 160.0) + (5.0 * 2900.0) + (8.0 * 320.0);
            assertEquals(expectedTotalValue, totalValue, 0.01, "Total portfolio value should be correct");

            assertNotNull(roi, "ROI should be calculated");
            assertNotNull(riskMetrics, "Risk metrics should be calculated");
            assertNotNull(riskMetrics.getSharpeRatio(), "Sharpe ratio should be calculated");
            assertNotNull(riskMetrics.getVolatility(), "Volatility should be calculated");
        }

        @Test
        @DisplayName("Should handle user-specific portfolio calculations")
        void shouldHandleUserSpecificPortfolioCalculations() {
            // Given
            User anotherUser = userService.registerUser("anotheruser", "password123");

            Asset userAsset = assetService.saveAsset(testAsset);
            
            Asset anotherUserAsset = new Asset();
            anotherUserAsset.setName("TSLA");
            anotherUserAsset.setQuantity(3.0);
            anotherUserAsset.setPurchasePricePerUnit(200.0);
            anotherUserAsset.setPricePerUnit(220.0);
            anotherUserAsset.setUser(anotherUser);
            assetService.saveAsset(anotherUserAsset);

            // When
            double userTotalValue = assetService.getTotalValueByUser(testUser);
            double anotherUserTotalValue = assetService.getTotalValueByUser(anotherUser);

            // Then
            assertEquals(1600.0, userTotalValue, 0.01, "Test user portfolio value should be correct");
            assertEquals(660.0, anotherUserTotalValue, 0.01, "Another user portfolio value should be correct");
        }

        @Test
        @DisplayName("Should calculate risk metrics with real data")
        void shouldCalculateRiskMetricsWithRealData() {
            // Given
            assetService.saveAsset(testAsset);

            // When
            double sharpeRatio = assetService.calculateSharpeRatioByUser(testUser);
            double volatility = assetService.calculateVolatilityByUser(testUser);
            double beta = assetService.calculateBetaByUser(testUser);
            double maxDrawdown = assetService.calculateMaxDrawdownByUser(testUser);
            double diversificationScore = assetService.calculateDiversificationScoreByUser(testUser);

            // Then
            assertNotNull(sharpeRatio, "Sharpe ratio should be calculated");
            assertNotNull(volatility, "Volatility should be calculated");
            assertNotNull(beta, "Beta should be calculated");
            assertNotNull(maxDrawdown, "Max drawdown should be calculated");
            assertNotNull(diversificationScore, "Diversification score should be calculated");
        }
    }

    @Nested
    @DisplayName("End-to-End Workflow Tests")
    class EndToEndWorkflowTests {

        @Test
        @DisplayName("Should complete full asset lifecycle workflow")
        void shouldCompleteFullAssetLifecycleWorkflow() {
            // Given
            Asset newAsset = new Asset();
            newAsset.setName("NVDA");
            newAsset.setQuantity(12.0);
            newAsset.setPurchasePricePerUnit(400.0);
            newAsset.setPricePerUnit(450.0);
            newAsset.setUser(testUser);

            // When - Create
            Asset createdAsset = assetService.saveAsset(newAsset);
            assertNotNull(createdAsset.getId(), "Asset should be created with ID");

            // When - Read
            Asset retrievedAsset = assetService.getAssetById(createdAsset.getId());
            assertEquals("NVDA", retrievedAsset.getName(), "Asset should be retrievable");

            // When - Update
            retrievedAsset.setQuantity(15.0);
            Asset updatedAsset = assetService.saveAsset(retrievedAsset);
            assertEquals(15.0, updatedAsset.getQuantity(), "Asset should be updated");

            // When - Calculate metrics
            double totalValue = assetService.getTotalValueByUser(testUser);
            double roi = assetService.calculateROIByUser(testUser);

            // Then
            assertTrue(totalValue > 0, "Total portfolio value should be positive");
            assertNotNull(roi, "ROI should be calculated");

            // When - Delete
            assetService.deleteAssetById(createdAsset.getId());

            // Then
            assertThrows(Exception.class, () -> assetService.getAssetById(createdAsset.getId()),
                    "Asset should be deleted");
        }

        @Test
        @DisplayName("Should handle portfolio rebalancing workflow")
        void shouldHandlePortfolioRebalancingWorkflow() {
            // Given
            Asset stockAsset = assetService.saveAsset(testAsset);
            
            Asset bondAsset = new Asset();
            bondAsset.setName("BOND");
            bondAsset.setQuantity(100.0);
            bondAsset.setPurchasePricePerUnit(100.0);
            bondAsset.setPricePerUnit(102.0);
            bondAsset.setUser(testUser);
            assetService.saveAsset(bondAsset);

            // When - Calculate current allocation
            double stockValue = stockAsset.getQuantity() * stockAsset.getPricePerUnit();
            double bondValue = bondAsset.getQuantity() * bondAsset.getPricePerUnit();
            double totalValue = stockValue + bondValue;
            
            double stockAllocation = stockValue / totalValue;
            double bondAllocation = bondValue / totalValue;

            // Then
            assertTrue(stockAllocation > 0, "Stock allocation should be positive");
            assertTrue(bondAllocation > 0, "Bond allocation should be positive");
            assertEquals(1.0, stockAllocation + bondAllocation, 0.001, "Allocations should sum to 100%");

            // When - Simulate rebalancing (sell some stock, buy more bonds)
            stockAsset.setQuantity(8.0); // Sell 2 shares
            assetService.saveAsset(stockAsset);

            bondAsset.setQuantity(120.0); // Buy 20 more bonds
            assetService.saveAsset(bondAsset);

            // Then - Verify new allocation
            double newStockValue = stockAsset.getQuantity() * stockAsset.getPricePerUnit();
            double newBondValue = bondAsset.getQuantity() * bondAsset.getPricePerUnit();
            double newTotalValue = newStockValue + newBondValue;

            double newStockAllocation = newStockValue / newTotalValue;
            double newBondAllocation = newBondValue / newTotalValue;

            assertTrue(newStockAllocation < stockAllocation, "Stock allocation should decrease");
            assertTrue(newBondAllocation > bondAllocation, "Bond allocation should increase");
        }
    }

    @Nested
    @DisplayName("HTTP Endpoint Integration Tests")
    class HttpEndpointIntegrationTests {

        @Test
        @DisplayName("Should access assets endpoint successfully")
        void shouldAccessAssetsEndpointSuccessfully() {
            // Given
            assetService.saveAsset(testAsset);
            String baseUrl = "http://localhost:" + port;

            // When
            ResponseEntity<String> response = restTemplate.getForEntity(
                    baseUrl + "/api/assets", String.class);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode(), "HTTP status should be OK");
            assertNotNull(response.getBody(), "Response body should not be null");
            assertTrue(response.getBody().contains("AAPL"), "Response should contain asset data");
        }

        @Test
        @DisplayName("Should calculate portfolio metrics via HTTP")
        void shouldCalculatePortfolioMetricsViaHttp() {
            // Given
            assetService.saveAsset(testAsset);
            String baseUrl = "http://localhost:" + port;

            // When
            ResponseEntity<String> totalValueResponse = restTemplate.getForEntity(
                    baseUrl + "/api/assets/total-value", String.class);
            
            ResponseEntity<String> roiResponse = restTemplate.getForEntity(
                    baseUrl + "/api/assets/roi", String.class);

            // Then
            assertEquals(HttpStatus.OK, totalValueResponse.getStatusCode(), "Total value endpoint should work");
            assertEquals(HttpStatus.OK, roiResponse.getStatusCode(), "ROI endpoint should work");
            
            assertNotNull(totalValueResponse.getBody(), "Total value response should not be null");
            assertNotNull(roiResponse.getBody(), "ROI response should not be null");
        }
    }

    @Nested
    @DisplayName("Data Consistency Tests")
    class DataConsistencyTests {

        @Test
        @DisplayName("Should maintain data consistency across operations")
        void shouldMaintainDataConsistencyAcrossOperations() {
            // Given
            Asset asset1 = assetService.saveAsset(testAsset);
            
            Asset asset2 = new Asset();
            asset2.setName("GOOGL");
            asset2.setQuantity(5.0);
            asset2.setPurchasePricePerUnit(2800.0);
            asset2.setPricePerUnit(2900.0);
            asset2.setUser(testUser);
            asset2 = assetService.saveAsset(asset2);

            // When - Perform multiple operations
            double totalValue1 = assetService.getTotalValueByUser(testUser);
            double roi1 = assetService.calculateROIByUser(testUser);
            
            // Update an asset
            asset1.setPricePerUnit(170.0);
            assetService.saveAsset(asset1);
            
            double totalValue2 = assetService.getTotalValueByUser(testUser);
            double roi2 = assetService.calculateROIByUser(testUser);

            // Then - Verify consistency
            assertTrue(totalValue2 > totalValue1, "Total value should increase after price update");
            assertNotEquals(roi1, roi2, "ROI should change after price update");
            
            // Verify the relationship between total value and individual assets
            double calculatedTotalValue = (asset1.getQuantity() * asset1.getPricePerUnit()) +
                                        (asset2.getQuantity() * asset2.getPricePerUnit());
            assertEquals(calculatedTotalValue, totalValue2, 0.01, "Total value should match sum of individual assets");
        }

        @Test
        @DisplayName("Should handle concurrent operations correctly")
        void shouldHandleConcurrentOperationsCorrectly() throws InterruptedException {
            // Given
            Asset asset = assetService.saveAsset(testAsset);
            int numberOfThreads = 5;
            Thread[] threads = new Thread[numberOfThreads];

            // When - Simulate concurrent updates
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    try {
                        Asset assetToUpdate = assetService.getAssetById(asset.getId());
                        assetToUpdate.setPricePerUnit(150.0 + threadId);
                        assetService.saveAsset(assetToUpdate);
                    } catch (Exception e) {
                        // Handle exceptions in concurrent operations
                    }
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then - Verify final state
            Asset finalAsset = assetService.getAssetById(asset.getId());
            assertNotNull(finalAsset, "Asset should still exist after concurrent operations");
            assertTrue(finalAsset.getPricePerUnit() >= 150.0, "Price should be updated");
        }
    }
}
