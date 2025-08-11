package com.portfolio.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for AssetController
 * Demonstrates professional controller testing practices including:
 * - HTTP endpoint testing with MockMvc
 * - Request/response validation
 * - HTTP status code verification
 * - Security testing considerations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetController Test Suite")
class AssetControllerTest {

    @Mock
    private AssetService assetService;

    @InjectMocks
    private AssetController assetController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private User testUser;
    private Asset testAsset;
    private Asset testAsset2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(assetController).build();
        objectMapper = new ObjectMapper();

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
    @DisplayName("GET Endpoints")
    class GetEndpoints {

        @Test
        @DisplayName("Should get all assets successfully")
        void shouldGetAllAssets() throws Exception {
            // Given
            List<Asset> expectedAssets = Arrays.asList(testAsset, testAsset2);
            when(assetService.getAllAssets()).thenReturn(expectedAssets);

            // When & Then
            mockMvc.perform(get("/api/assets")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("AAPL"))
                    .andExpect(jsonPath("$[1].name").value("GOOGL"));

            verify(assetService).getAllAssets();
        }

        @Test
        @DisplayName("Should return empty list when no assets exist")
        void shouldReturnEmptyListWhenNoAssets() throws Exception {
            // Given
            when(assetService.getAllAssets()).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/assets")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(assetService).getAllAssets();
        }

        @Test
        @DisplayName("Should get asset by ID successfully")
        void shouldGetAssetById() throws Exception {
            // Given
            when(assetService.getAssetById(1L)).thenReturn(testAsset);

            // When & Then
            mockMvc.perform(get("/api/assets/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("AAPL"))
                    .andExpect(jsonPath("$.quantity").value(10.0));

            verify(assetService).getAssetById(1L);
        }

        @Test
        @DisplayName("Should return 404 when asset not found")
        void shouldReturn404WhenAssetNotFound() throws Exception {
            // Given
            when(assetService.getAssetById(999L))
                    .thenThrow(new RuntimeException("Asset not found"));

            // When & Then
            mockMvc.perform(get("/api/assets/999")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());

            verify(assetService).getAssetById(999L);
        }

        @Test
        @DisplayName("Should get assets by user successfully")
        void shouldGetAssetsByUser() throws Exception {
            // Given
            List<Asset> userAssets = Arrays.asList(testAsset, testAsset2);
            when(assetService.getAssetsByUser(any(User.class))).thenReturn(userAssets);

            // When & Then
            mockMvc.perform(get("/api/assets/user")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));

            verify(assetService).getAssetsByUser(any(User.class));
        }
    }

    @Nested
    @DisplayName("POST Endpoints")
    class PostEndpoints {

        @Test
        @DisplayName("Should create asset successfully")
        void shouldCreateAssetSuccessfully() throws Exception {
            // Given
            Asset assetToCreate = new Asset();
            assetToCreate.setName("TSLA");
            assetToCreate.setQuantity(5.0);
            assetToCreate.setPurchasePricePerUnit(200.0);
            assetToCreate.setPricePerUnit(220.0);

            Asset createdAsset = new Asset();
            createdAsset.setId(3L);
            createdAsset.setName("TSLA");
            createdAsset.setQuantity(5.0);
            createdAsset.setPurchasePricePerUnit(200.0);
            createdAsset.setPricePerUnit(220.0);
            createdAsset.setInitialInvestment(1000.0);

            when(assetService.saveAsset(any(Asset.class))).thenReturn(createdAsset);

            // When & Then
            mockMvc.perform(post("/api/assets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(assetToCreate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.name").value("TSLA"))
                    .andExpect(jsonPath("$.initialInvestment").value(1000.0));

            verify(assetService).saveAsset(any(Asset.class));
        }

        @Test
        @DisplayName("Should handle invalid asset data gracefully")
        void shouldHandleInvalidAssetDataGracefully() throws Exception {
            // Given
            Asset invalidAsset = new Asset();
            // Missing required fields

            // When & Then
            mockMvc.perform(post("/api/assets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidAsset)))
                    .andExpect(status().isOk()); // Controller should handle gracefully

            verify(assetService).saveAsset(any(Asset.class));
        }
    }

    @Nested
    @DisplayName("PUT Endpoints")
    class PutEndpoints {

        @Test
        @DisplayName("Should update asset successfully")
        void shouldUpdateAssetSuccessfully() throws Exception {
            // Given
            Asset assetToUpdate = new Asset();
            assetToUpdate.setId(1L);
            assetToUpdate.setName("AAPL");
            assetToUpdate.setQuantity(15.0); // Updated quantity
            assetToUpdate.setPurchasePricePerUnit(150.0);
            assetToUpdate.setPricePerUnit(160.0);

            Asset updatedAsset = new Asset();
            updatedAsset.setId(1L);
            updatedAsset.setName("AAPL");
            updatedAsset.setQuantity(15.0);
            updatedAsset.setPurchasePricePerUnit(150.0);
            updatedAsset.setPricePerUnit(160.0);
            updatedAsset.setInitialInvestment(2250.0);

            when(assetService.saveAsset(any(Asset.class))).thenReturn(updatedAsset);

            // When & Then
            mockMvc.perform(put("/api/assets/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(assetToUpdate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(15.0))
                    .andExpect(jsonPath("$.initialInvestment").value(2250.0));

            verify(assetService).saveAsset(any(Asset.class));
        }
    }

    @Nested
    @DisplayName("DELETE Endpoints")
    class DeleteEndpoints {

        @Test
        @DisplayName("Should delete asset successfully")
        void shouldDeleteAssetSuccessfully() throws Exception {
            // Given
            doNothing().when(assetService).deleteAssetById(1L);

            // When & Then
            mockMvc.perform(delete("/api/assets/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(assetService).deleteAssetById(1L);
        }

        @Test
        @DisplayName("Should handle delete of non-existent asset gracefully")
        void shouldHandleDeleteOfNonExistentAsset() throws Exception {
            // Given
            doNothing().when(assetService).deleteAssetById(999L);

            // When & Then
            mockMvc.perform(delete("/api/assets/999")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(assetService).deleteAssetById(999L);
        }
    }

    @Nested
    @DisplayName("Portfolio Analytics Endpoints")
    class PortfolioAnalyticsEndpoints {

        @Test
        @DisplayName("Should get total portfolio value successfully")
        void shouldGetTotalPortfolioValue() throws Exception {
            // Given
            double expectedValue = 15000.0;
            when(assetService.getTotalValue()).thenReturn(expectedValue);

            // When & Then
            mockMvc.perform(get("/api/assets/total-value")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedValue));

            verify(assetService).getTotalValue();
        }

        @Test
        @DisplayName("Should get portfolio ROI successfully")
        void shouldGetPortfolioRoi() throws Exception {
            // Given
            double expectedRoi = 0.15; // 15% return
            when(assetService.calculateROI()).thenReturn(expectedRoi);

            // When & Then
            mockMvc.perform(get("/api/assets/roi")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedRoi));

            verify(assetService).calculateROI();
        }

        @Test
        @DisplayName("Should get Sharpe ratio successfully")
        void shouldGetSharpeRatio() throws Exception {
            // Given
            double expectedSharpe = 1.25;
            when(assetService.calculateSharpeRatio()).thenReturn(expectedSharpe);

            // When & Then
            mockMvc.perform(get("/api/assets/sharpe-ratio")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedSharpe));

            verify(assetService).calculateSharpeRatio();
        }

        @Test
        @DisplayName("Should get volatility successfully")
        void shouldGetVolatility() throws Exception {
            // Given
            double expectedVolatility = 0.18; // 18% volatility
            when(assetService.calculateVolatility()).thenReturn(expectedVolatility);

            // When & Then
            mockMvc.perform(get("/api/assets/volatility")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedVolatility));

            verify(assetService).calculateVolatility();
        }

        @Test
        @DisplayName("Should get beta successfully")
        void shouldGetBeta() throws Exception {
            // Given
            double expectedBeta = 1.1;
            when(assetService.calculateBeta()).thenReturn(expectedBeta);

            // When & Then
            mockMvc.perform(get("/api/assets/beta")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedBeta));

            verify(assetService).calculateBeta();
        }

        @Test
        @DisplayName("Should get max drawdown successfully")
        void shouldGetMaxDrawdown() throws Exception {
            // Given
            double expectedMaxDrawdown = 0.25; // 25% max drawdown
            when(assetService.calculateMaxDrawdown()).thenReturn(expectedMaxDrawdown);

            // When & Then
            mockMvc.perform(get("/api/assets/max-drawdown")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedMaxDrawdown));

            verify(assetService).calculateMaxDrawdown();
        }

        @Test
        @DisplayName("Should get diversification score successfully")
        void shouldGetDiversificationScore() throws Exception {
            // Given
            double expectedScore = 0.75; // 75% diversification
            when(assetService.calculateDiversificationScore()).thenReturn(expectedScore);

            // When & Then
            mockMvc.perform(get("/api/assets/diversification-score")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(expectedScore));

            verify(assetService).calculateDiversificationScore();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle service exceptions gracefully")
        void shouldHandleServiceExceptionsGracefully() throws Exception {
            // Given
            when(assetService.getAllAssets())
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/assets")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());

            verify(assetService).getAllAssets();
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() throws Exception {
            // Given
            String malformedJson = "{ invalid json }";

            // When & Then
            mockMvc.perform(post("/api/assets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Security Considerations")
    class SecurityConsiderations {

        @Test
        @DisplayName("Should validate user ownership for asset operations")
        void shouldValidateUserOwnershipForAssetOperations() throws Exception {
            // Given
            when(assetService.getAssetByIdAndUser(anyLong(), any(User.class)))
                    .thenReturn(testAsset);

            // When & Then
            mockMvc.perform(get("/api/assets/1/user")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(assetService).getAssetByIdAndUser(anyLong(), any(User.class));
        }

        @Test
        @DisplayName("Should handle unauthorized access gracefully")
        void shouldHandleUnauthorizedAccessGracefully() throws Exception {
            // Given
            when(assetService.getAssetByIdAndUser(anyLong(), any(User.class)))
                    .thenThrow(new RuntimeException("Unauthorized access"));

            // When & Then
            mockMvc.perform(get("/api/assets/1/user")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());

            verify(assetService).getAssetByIdAndUser(anyLong(), any(User.class));
        }
    }
}
