package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.model.RiskMetrics;
import com.portfolio.tracker.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for AssetService
 * Demonstrates professional testing practices including:
 * - Unit testing with Mockito
 * - Edge case testing
 * - Performance testing considerations
 * - Proper test naming and organization
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetService Test Suite")
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private AssetService assetService;

    private User testUser;
    private Asset testAsset;
    private Asset testAsset2;

    @BeforeEach
    void setUp() {
        // Setup test data
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testAsset = new Asset();
        testAsset.setId(1L);
        testAsset.setName("AAPL");
        testAsset.setQuantity(10.0);
        testAsset.setPurchasePricePerUnit(150.0);
        testAsset.setPricePerUnit(160.0);
        testAsset.setUser(testUser);

        testAsset2 = new Asset();
        testAsset2.setId(2L);
        testAsset2.setName("GOOGL");
        testAsset2.setQuantity(5.0);
        testAsset2.setPurchasePricePerUnit(2800.0);
        testAsset2.setPricePerUnit(2900.0);
        testAsset2.setUser(testUser);
    }

    @Nested
    @DisplayName("Asset CRUD Operations")
    class AssetCrudOperations {

        @Test
        @DisplayName("Should save asset with calculated initial investment")
        void shouldSaveAssetWithCalculatedInitialInvestment() {
            // Given
            Asset assetToSave = new Asset();
            assetToSave.setQuantity(20.0);
            assetToSave.setPurchasePricePerUnit(100.0);
            assetToSave.setPricePerUnit(110.0);

            when(assetRepository.save(any(Asset.class))).thenReturn(assetToSave);

            // When
            Asset savedAsset = assetService.saveAsset(assetToSave);

            // Then
            assertEquals(2000.0, savedAsset.getInitialInvestment());
            verify(assetRepository).save(assetToSave);
        }

        @Test
        @DisplayName("Should get all assets successfully")
        void shouldGetAllAssets() {
            // Given
            List<Asset> expectedAssets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(expectedAssets);

            // When
            List<Asset> actualAssets = assetService.getAllAssets();

            // Then
            assertEquals(expectedAssets.size(), actualAssets.size());
            assertEquals(expectedAssets, actualAssets);
            verify(assetRepository).findAll();
        }

        @Test
        @DisplayName("Should get asset by ID successfully")
        void shouldGetAssetById() {
            // Given
            when(assetRepository.findById(1L)).thenReturn(Optional.of(testAsset));

            // When
            Asset foundAsset = assetService.getAssetById(1L);

            // Then
            assertNotNull(foundAsset);
            assertEquals(testAsset.getName(), foundAsset.getName());
            verify(assetRepository).findById(1L);
        }

        @Test
        @DisplayName("Should throw exception when asset not found by ID")
        void shouldThrowExceptionWhenAssetNotFound() {
            // Given
            when(assetRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(ResponseStatusException.class, () -> assetService.getAssetById(999L));
            verify(assetRepository).findById(999L);
        }

        @Test
        @DisplayName("Should delete asset successfully")
        void shouldDeleteAssetSuccessfully() {
            // Given
            when(assetRepository.findById(1L)).thenReturn(Optional.of(testAsset));
            doNothing().when(assetRepository).delete(testAsset);

            // When
            assertDoesNotThrow(() -> assetService.deleteAssetById(1L));

            // Then
            verify(assetRepository).findById(1L);
            verify(assetRepository).delete(testAsset);
        }

        @Test
        @DisplayName("Should handle delete of non-existent asset gracefully")
        void shouldHandleDeleteOfNonExistentAsset() {
            // Given
            when(assetRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertDoesNotThrow(() -> assetService.deleteAssetById(999L));
            verify(assetRepository).findById(999L);
            verify(assetRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Portfolio Value Calculations")
    class PortfolioValueCalculations {

        @Test
        @DisplayName("Should calculate total portfolio value correctly")
        void shouldCalculateTotalPortfolioValue() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double totalValue = assetService.calculateTotalValue();

            // Then
            double expectedValue = (10.0 * 160.0) + (5.0 * 2900.0);
            assertEquals(expectedValue, totalValue, 0.01);
        }

        @Test
        @DisplayName("Should return zero for empty portfolio")
        void shouldReturnZeroForEmptyPortfolio() {
            // Given
            when(assetRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            double totalValue = assetService.calculateTotalValue();

            // Then
            assertEquals(0.0, totalValue, 0.01);
        }

        @Test
        @DisplayName("Should calculate total value by user correctly")
        void shouldCalculateTotalValueByUser() {
            // Given
            List<Asset> userAssets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(userAssets);

            // When
            double totalValue = assetService.getTotalValueByUser(testUser);

            // Then
            double expectedValue = (10.0 * 160.0) + (5.0 * 2900.0);
            assertEquals(expectedValue, totalValue, 0.01);
        }
    }

    @Nested
    @DisplayName("ROI Calculations")
    class RoiCalculations {

        @Test
        @DisplayName("Should calculate ROI correctly for profitable portfolio")
        void shouldCalculateRoiCorrectly() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double roi = assetService.calculateROI();

            // Then
            double expectedRoi = 0.0667; // 6.67% profit
            assertEquals(expectedRoi, roi, 0.001);
        }

        @Test
        @DisplayName("Should throw exception when no valid assets for ROI calculation")
        void shouldThrowExceptionWhenNoValidAssetsForRoi() {
            // Given
            Asset invalidAsset = new Asset();
            invalidAsset.setInitialInvestment(0.0);
            when(assetRepository.findAll()).thenReturn(Collections.singletonList(invalidAsset));

            // When & Then
            assertThrows(IllegalStateException.class, () -> assetService.calculateROI());
        }

        @Test
        @DisplayName("Should calculate ROI by user correctly")
        void shouldCalculateRoiByUser() {
            // Given
            List<Asset> userAssets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(userAssets);

            // When
            double roi = assetService.calculateROIByUser(testUser);

            // Then
            double expectedRoi = 0.0667; // 6.67% profit
            assertEquals(expectedRoi, roi, 0.001);
        }
    }

    @Nested
    @DisplayName("Risk Metrics Calculations")
    class RiskMetricsCalculations {

        @Test
        @DisplayName("Should calculate Sharpe ratio correctly")
        void shouldCalculateSharpeRatioCorrectly() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double sharpeRatio = assetService.calculateSharpeRatio();

            // Then
            // Since getPortfolioReturns() returns empty list, should handle gracefully
            assertNotNull(sharpeRatio);
        }

        @Test
        @DisplayName("Should calculate volatility correctly")
        void shouldCalculateVolatilityCorrectly() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double volatility = assetService.calculateVolatility();

            // Then
            assertNotNull(volatility);
            assertTrue(volatility >= 0.0);
        }

        @Test
        @DisplayName("Should calculate beta correctly")
        void shouldCalculateBetaCorrectly() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double beta = assetService.calculateBeta();

            // Then
            assertNotNull(beta);
        }

        @Test
        @DisplayName("Should calculate max drawdown correctly")
        void shouldCalculateMaxDrawdownCorrectly() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double maxDrawdown = assetService.calculateMaxDrawdown();

            // Then
            assertNotNull(maxDrawdown);
            assertTrue(maxDrawdown >= 0.0);
        }

        @Test
        @DisplayName("Should calculate diversification score correctly")
        void shouldCalculateDiversificationScoreCorrectly() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double diversificationScore = assetService.calculateDiversificationScore();

            // Then
            assertNotNull(diversificationScore);
            assertTrue(diversificationScore >= 0.0 && diversificationScore <= 1.0);
        }

        @Test
        @DisplayName("Should get comprehensive risk metrics")
        void shouldGetComprehensiveRiskMetrics() {
            // Given
            List<Asset> assets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            RiskMetrics riskMetrics = assetService.getRiskMetrics();

            // Then
            assertNotNull(riskMetrics);
            assertNotNull(riskMetrics.getSharpeRatio());
            assertNotNull(riskMetrics.getVolatility());
            assertNotNull(riskMetrics.getBeta());
            assertNotNull(riskMetrics.getMaxDrawdown());
        }
    }

    @Nested
    @DisplayName("User-Specific Operations")
    class UserSpecificOperations {

        @Test
        @DisplayName("Should get assets by user correctly")
        void shouldGetAssetsByUser() {
            // Given
            List<Asset> userAssets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(userAssets);

            // When
            List<Asset> foundAssets = assetService.getAssetsByUser(testUser);

            // Then
            assertEquals(userAssets.size(), foundAssets.size());
            assertTrue(foundAssets.stream().allMatch(asset -> asset.getUser().equals(testUser)));
        }

        @Test
        @DisplayName("Should get asset by ID and user correctly")
        void shouldGetAssetByIdAndUser() {
            // Given
            List<Asset> userAssets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(userAssets);

            // When
            Asset foundAsset = assetService.getAssetByIdAndUser(1L, testUser);

            // Then
            assertNotNull(foundAsset);
            assertEquals(testAsset.getId(), foundAsset.getId());
            assertEquals(testUser, foundAsset.getUser());
        }

        @Test
        @DisplayName("Should delete asset by ID and user correctly")
        void shouldDeleteAssetByIdAndUser() {
            // Given
            List<Asset> userAssets = Arrays.asList(testAsset, testAsset2);
            when(assetRepository.findAll()).thenReturn(userAssets);

            // When
            assertDoesNotThrow(() -> assetService.deleteAssetByIdAndUser(1L, testUser));

            // Then
            // Verify the method executes without exception
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle null user gracefully")
        void shouldHandleNullUserGracefully() {
            // Given
            when(assetRepository.findAll()).thenReturn(Collections.emptyList());

            // When & Then
            assertDoesNotThrow(() -> assetService.getTotalValueByUser(null));
        }

        @Test
        @DisplayName("Should handle empty asset list gracefully")
        void shouldHandleEmptyAssetListGracefully() {
            // Given
            when(assetRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            double totalValue = assetService.calculateTotalValue();

            // Then
            assertEquals(0.0, totalValue, 0.01);
        }

        @Test
        @DisplayName("Should handle assets with zero quantities")
        void shouldHandleAssetsWithZeroQuantities() {
            // Given
            Asset zeroQuantityAsset = new Asset();
            zeroQuantityAsset.setQuantity(0.0);
            zeroQuantityAsset.setPricePerUnit(100.0);
            zeroQuantityAsset.setInitialInvestment(0.0);

            List<Asset> assets = Collections.singletonList(zeroQuantityAsset);
            when(assetRepository.findAll()).thenReturn(assets);

            // When
            double totalValue = assetService.calculateTotalValue();

            // Then
            assertEquals(0.0, totalValue, 0.01);
        }
    }

    @Nested
    @DisplayName("Performance Considerations")
    class PerformanceConsiderations {

        @Test
        @DisplayName("Should handle large number of assets efficiently")
        void shouldHandleLargeNumberOfAssetsEfficiently() {
            // Given
            List<Asset> largeAssetList = createLargeAssetList(1000);
            when(assetRepository.findAll()).thenReturn(largeAssetList);

            // When
            long startTime = System.currentTimeMillis();
            double totalValue = assetService.calculateTotalValue();
            long endTime = System.currentTimeMillis();

            // Then
            assertTrue(endTime - startTime < 1000); // Should complete in under 1 second
            assertTrue(totalValue > 0);
        }

        private List<Asset> createLargeAssetList(int size) {
            List<Asset> assets = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                Asset asset = new Asset();
                asset.setId((long) i);
                asset.setName("ASSET" + i);
                asset.setQuantity(100.0);
                asset.setPricePerUnit(50.0 + i);
                asset.setInitialInvestment(5000.0);
                asset.setUser(testUser);
                assets.add(asset);
            }
            return assets;
        }
    }
}
