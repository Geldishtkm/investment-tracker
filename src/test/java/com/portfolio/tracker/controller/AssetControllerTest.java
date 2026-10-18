package com.portfolio.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.service.AssetService;
import com.portfolio.tracker.service.RiskMetricsService;
import com.portfolio.tracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for AssetController. Every request is made as an authenticated user, and the
 * controller must only ever touch that user's assets.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetController Test Suite")
class AssetControllerTest {

    @Mock
    private AssetService assetService;

    @Mock
    private UserService userService;

    @Mock
    private RiskMetricsService riskMetricsService;

    @InjectMocks
    private AssetController assetController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Principal principal = new UsernamePasswordAuthenticationToken("testuser", null, List.of());

    private User testUser;
    private Asset bitcoin;
    private Asset ethereum;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(assetController).build();

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

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.principal(principal).contentType(MediaType.APPLICATION_JSON);
    }

    private void givenLoggedInUser() {
        when(userService.findByUsername("testuser")).thenReturn(testUser);
    }

    @Nested
    @DisplayName("GET Endpoints")
    class GetEndpoints {

        @Test
        @DisplayName("Lists the authenticated user's assets")
        void shouldGetAssetsOfUser() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetsByUser(testUser)).thenReturn(Arrays.asList(bitcoin, ethereum));

            mockMvc.perform(asUser(get("/api/assets")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Bitcoin"))
                    .andExpect(jsonPath("$[1].name").value("Ethereum"));

            verify(assetService).getAssetsByUser(testUser);
        }

        @Test
        @DisplayName("Returns an empty list for an empty portfolio")
        void shouldReturnEmptyListWhenNoAssets() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetsByUser(testUser)).thenReturn(Collections.emptyList());

            mockMvc.perform(asUser(get("/api/assets")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Returns one of the user's assets by ID")
        void shouldGetAssetById() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetByIdAndUser(1L, testUser)).thenReturn(bitcoin);

            mockMvc.perform(asUser(get("/api/assets/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Bitcoin"))
                    .andExpect(jsonPath("$.quantity").value(0.5));
        }

        @Test
        @DisplayName("Another user's or a missing asset is a 404")
        void shouldReturn404WhenAssetNotFound() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetByIdAndUser(999L, testUser))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: 999"));

            mockMvc.perform(asUser(get("/api/assets/999")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Assets with prices include value and profit/loss against cost basis")
        void shouldReturnAssetsWithProfitLoss() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetsByUser(testUser)).thenReturn(List.of(bitcoin));

            mockMvc.perform(asUser(get("/api/assets/with-prices")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].totalValue").value(30000.0))
                    .andExpect(jsonPath("$[0].profitLoss").value(5000.0));
        }
    }

    @Nested
    @DisplayName("POST Endpoints")
    class PostEndpoints {

        @Test
        @DisplayName("Creates an asset owned by the authenticated user")
        void shouldCreateAssetSuccessfully() throws Exception {
            givenLoggedInUser();
            when(assetService.saveAsset(any(Asset.class))).thenAnswer(inv -> {
                Asset saved = inv.getArgument(0);
                saved.setId(10L);
                saved.setInitialInvestment(saved.getQuantity() * saved.getPurchasePricePerUnit());
                return saved;
            });
            Asset input = asset(null, "Solana", 60.0, 150.0, 120.0);

            mockMvc.perform(asUser(post("/api/assets")).content(objectMapper.writeValueAsString(input)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.initialInvestment").value(7200.0));

            verify(assetService).saveAsset(argThat(a -> a.getUser() == testUser));
        }

        @Test
        @DisplayName("Rejects assets that can't be valued (zero quantity, non-positive prices, no name)")
        void shouldRejectInvalidAssets() throws Exception {
            givenLoggedInUser();
            List<Asset> invalid = List.of(
                    asset(null, "Solana", 0.0, 150.0, 120.0),
                    asset(null, "Solana", 60.0, -1.0, 120.0),
                    asset(null, "Solana", 60.0, 150.0, 0.0),
                    asset(null, " ", 60.0, 150.0, 120.0));

            for (Asset a : invalid) {
                mockMvc.perform(asUser(post("/api/assets")).content(objectMapper.writeValueAsString(a)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error").exists());
            }
            verify(assetService, never()).saveAsset(any());
        }
    }

    @Nested
    @DisplayName("PUT Endpoints")
    class PutEndpoints {

        @Test
        @DisplayName("Updates the user's asset")
        void shouldUpdateAssetSuccessfully() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetByIdAndUser(1L, testUser)).thenReturn(bitcoin);
            when(assetService.saveAsset(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
            Asset update = asset(null, "Bitcoin", 0.75, 61000.0, 50000.0);

            mockMvc.perform(asUser(put("/api/assets/1")).content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(0.75))
                    .andExpect(jsonPath("$.pricePerUnit").value(61000.0));
        }

        @Test
        @DisplayName("Rejects an update with a negative quantity")
        void shouldRejectInvalidUpdate() throws Exception {
            Asset update = asset(null, "Bitcoin", -1.0, 61000.0, 50000.0);

            mockMvc.perform(asUser(put("/api/assets/1")).content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isBadRequest());

            verify(assetService, never()).saveAsset(any());
        }

        @Test
        @DisplayName("Updating another user's asset is a 404")
        void shouldReturn404WhenUpdatingForeignAsset() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetByIdAndUser(2L, testUser))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: 2"));
            Asset update = asset(null, "Ethereum", 1.0, 3000.0, 2500.0);

            mockMvc.perform(asUser(put("/api/assets/2")).content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isNotFound());

            verify(assetService, never()).saveAsset(any());
        }
    }

    @Nested
    @DisplayName("DELETE Endpoints")
    class DeleteEndpoints {

        @Test
        @DisplayName("Deletes the user's asset")
        void shouldDeleteAssetSuccessfully() throws Exception {
            givenLoggedInUser();

            mockMvc.perform(asUser(delete("/api/assets/1")))
                    .andExpect(status().isNoContent());

            verify(assetService).deleteAssetByIdAndUser(1L, testUser);
        }

        @Test
        @DisplayName("Deleting a missing asset is a 404")
        void shouldHandleDeleteOfNonExistentAsset() throws Exception {
            givenLoggedInUser();
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found with id: 999"))
                    .when(assetService).deleteAssetByIdAndUser(999L, testUser);

            mockMvc.perform(asUser(delete("/api/assets/999")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Analytics Endpoints")
    class AnalyticsEndpoints {

        @Test
        @DisplayName("Empty portfolio reports why risk metrics are unavailable instead of placeholder numbers")
        void shouldReportEmptyPortfolio() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetsByUser(testUser)).thenReturn(Collections.emptyList());
            when(assetService.getTotalValueByUser(testUser)).thenReturn(0.0);
            when(assetService.calculateROIByUser(testUser)).thenThrow(new IllegalStateException("no cost basis"));
            when(riskMetricsService.calculateDetailed(Collections.emptyList())).thenThrow(PortfolioDataException.noAssets());

            mockMvc.perform(asUser(get("/api/assets/analytics")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAssets").value(0))
                    .andExpect(jsonPath("$.riskMetricsAvailable").value(false))
                    .andExpect(jsonPath("$.riskMetricsCode").value("NO_ASSETS"))
                    .andExpect(jsonPath("$.roi").doesNotExist())
                    .andExpect(jsonPath("$.sharpeRatio").doesNotExist());
        }

        @Test
        @DisplayName("Returns computed risk metrics and cost-basis ROI")
        void shouldReturnRiskMetrics() throws Exception {
            givenLoggedInUser();
            List<Asset> assets = List.of(bitcoin, ethereum);
            when(assetService.getAssetsByUser(testUser)).thenReturn(assets);
            when(assetService.getTotalValueByUser(testUser)).thenReturn(42000.0);
            when(assetService.calculateROIByUser(testUser)).thenReturn(20.0);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("sharpeRatio", -0.58);
            metrics.put("volatility", 0.45);
            when(riskMetricsService.calculateDetailed(assets)).thenReturn(metrics);

            mockMvc.perform(asUser(get("/api/assets/analytics")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.riskMetricsAvailable").value(true))
                    .andExpect(jsonPath("$.sharpeRatio").value(-0.58))
                    .andExpect(jsonPath("$.roi").value(20.0))
                    .andExpect(jsonPath("$.totalValue").value(42000.0));
        }
    }

    @Nested
    @DisplayName("Security Considerations")
    class SecurityConsiderations {

        @Test
        @DisplayName("Health check counts only the caller's assets")
        void shouldScopeHealthCheckToUser() throws Exception {
            givenLoggedInUser();
            when(assetService.getAssetsByUser(testUser)).thenReturn(List.of(bitcoin));

            mockMvc.perform(asUser(get("/api/assets/health")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Database connection OK. Your assets: 1"));
        }
    }
}
