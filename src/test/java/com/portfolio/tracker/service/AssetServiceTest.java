package com.portfolio.tracker.service;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
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
 * Unit tests for AssetService: CRUD scoped to the owning user, portfolio value and
 * cost-basis ROI.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetService Test Suite")
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private AssetService assetService;

    private User testUser;
    private Asset bitcoin;
    private Asset ethereum;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        bitcoin = asset(1L, "Bitcoin", 0.5, 60000.0, 50000.0);
        ethereum = asset(2L, "Ethereum", 4.0, 3000.0, 2500.0);
    }

    private Asset asset(Long id, String name, double quantity, double price, double purchasePrice) {
        Asset a = new Asset();
        a.setId(id);
        a.setName(name);
        a.setQuantity(quantity);
        a.setPricePerUnit(price);
        a.setPurchasePricePerUnit(purchasePrice);
        a.setInitialInvestment(quantity * purchasePrice);
        a.setUser(testUser);
        return a;
    }

    @Nested
    @DisplayName("Asset CRUD Operations")
    class AssetCrudOperations {

        @Test
        @DisplayName("Saving an asset sets the initial investment from quantity x purchase price")
        void shouldSaveAssetWithCalculatedInitialInvestment() {
            Asset input = asset(null, "Solana", 60.0, 150.0, 120.0);
            input.setInitialInvestment(0.0);
            when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

            Asset saved = assetService.saveAsset(input);

            assertEquals(60.0 * 120.0, saved.getInitialInvestment(), 1e-9);
            verify(assetRepository).save(input);
        }

        @Test
        @DisplayName("Finds an asset that belongs to the user")
        void shouldGetAssetByIdAndUser() {
            when(assetRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(bitcoin));

            Asset found = assetService.getAssetByIdAndUser(1L, testUser);

            assertEquals(bitcoin.getId(), found.getId());
            assertEquals(testUser, found.getUser());
        }

        @Test
        @DisplayName("Another user's or a missing asset is a 404")
        void shouldThrowNotFoundForAssetOfOtherUser() {
            when(assetRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> assetService.getAssetByIdAndUser(99L, testUser));
            assertEquals(404, e.getStatusCode().value());
        }

        @Test
        @DisplayName("Deletes the user's own asset")
        void shouldDeleteAssetByIdAndUser() {
            when(assetRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(bitcoin));

            assetService.deleteAssetByIdAndUser(1L, testUser);

            verify(assetRepository).delete(bitcoin);
        }

        @Test
        @DisplayName("Deleting a missing asset is a 404 and deletes nothing")
        void shouldHandleDeleteOfNonExistentAsset() {
            when(assetRepository.findByIdAndUser(999L, testUser)).thenReturn(Optional.empty());

            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> assetService.deleteAssetByIdAndUser(999L, testUser));
            assertEquals(404, e.getStatusCode().value());
            verify(assetRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Lists only the user's assets")
        void shouldGetAssetsByUser() {
            when(assetRepository.findByUser(testUser)).thenReturn(Arrays.asList(bitcoin, ethereum));

            List<Asset> found = assetService.getAssetsByUser(testUser);

            assertEquals(2, found.size());
            assertTrue(found.stream().allMatch(a -> a.getUser().equals(testUser)));
        }
    }

    @Nested
    @DisplayName("Portfolio Value Calculations")
    class PortfolioValueCalculations {

        @Test
        @DisplayName("Total value is the sum of quantity x current price")
        void shouldCalculateTotalValueByUser() {
            when(assetRepository.findByUser(testUser)).thenReturn(Arrays.asList(bitcoin, ethereum));

            double total = assetService.getTotalValueByUser(testUser);

            assertEquals(0.5 * 60000.0 + 4.0 * 3000.0, total, 1e-9);
        }

        @Test
        @DisplayName("Empty portfolio is worth zero")
        void shouldReturnZeroForEmptyPortfolio() {
            when(assetRepository.findByUser(testUser)).thenReturn(Collections.emptyList());

            assertEquals(0.0, assetService.getTotalValueByUser(testUser));
        }

        @Test
        @DisplayName("Zero-quantity positions contribute nothing")
        void shouldHandleAssetsWithZeroQuantities() {
            Asset empty = asset(3L, "Cardano", 0.0, 0.5, 0.4);
            when(assetRepository.findByUser(testUser)).thenReturn(Arrays.asList(bitcoin, empty));

            assertEquals(0.5 * 60000.0, assetService.getTotalValueByUser(testUser), 1e-9);
        }

        @Test
        @DisplayName("Values a large portfolio exactly and quickly")
        void shouldHandleLargeNumberOfAssetsEfficiently() {
            List<Asset> many = new ArrayList<>();
            for (int i = 0; i < 1000; i++) many.add(asset((long) i, "Coin" + i, 1.0, 100.0, 90.0));
            when(assetRepository.findByUser(testUser)).thenReturn(many);

            long start = System.nanoTime();
            double total = assetService.getTotalValueByUser(testUser);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertEquals(100_000.0, total, 1e-6);
            assertTrue(elapsedMs < 1000, "valuing 1000 assets took " + elapsedMs + " ms");
        }
    }

    @Nested
    @DisplayName("ROI Calculations")
    class RoiCalculations {

        @Test
        @DisplayName("ROI is (current value - cost basis) / cost basis, in percent")
        void shouldCalculateRoiByUser() {
            // Cost basis: 0.5 x 50,000 + 4 x 2,500 = 35,000; current value: 30,000 + 12,000 = 42,000
            when(assetRepository.findByUser(testUser)).thenReturn(Arrays.asList(bitcoin, ethereum));

            double roi = assetService.calculateROIByUser(testUser);

            assertEquals((42000.0 - 35000.0) / 35000.0 * 100, roi, 1e-9);
        }

        @Test
        @DisplayName("Positions without a cost basis are excluded from ROI")
        void shouldExcludeAssetsWithoutCostBasis() {
            Asset gifted = asset(3L, "Solana", 10.0, 150.0, 120.0);
            gifted.setInitialInvestment(0.0);
            when(assetRepository.findByUser(testUser)).thenReturn(Arrays.asList(bitcoin, gifted));

            double roi = assetService.calculateROIByUser(testUser);

            assertEquals((30000.0 - 25000.0) / 25000.0 * 100, roi, 1e-9);
        }

        @Test
        @DisplayName("ROI is undefined when no asset has a cost basis")
        void shouldThrowExceptionWhenNoValidAssetsForRoi() {
            Asset gifted = asset(3L, "Solana", 10.0, 150.0, 120.0);
            gifted.setInitialInvestment(0.0);
            when(assetRepository.findByUser(testUser)).thenReturn(List.of(gifted));

            assertThrows(IllegalStateException.class, () -> assetService.calculateROIByUser(testUser));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Null user yields an empty portfolio rather than an exception")
        void shouldHandleNullUserGracefully() {
            when(assetRepository.findByUser(null)).thenReturn(Collections.emptyList());

            assertEquals(0.0, assetService.getTotalValueByUser(null));
        }
    }
}
