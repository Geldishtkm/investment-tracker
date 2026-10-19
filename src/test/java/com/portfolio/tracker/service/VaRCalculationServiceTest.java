package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioSnapshot;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.model.VaRCalculation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VaR is measured on the whole account: holdings plus cash")
class VaRCalculationServiceTest {

    @Mock private AssetService assetService;
    @Mock private PortfolioDataService portfolioDataService;
    @Mock private AnalyticsProperties properties;
    @Mock private AssetPriceRefreshService priceRefreshService;
    @Mock private AccountService accountService;

    private VaRCalculationService service;
    private final User user = new User();

    @BeforeEach
    void setUp() {
        service = new VaRCalculationService(assetService, portfolioDataService, properties, priceRefreshService, accountService);
        user.setId(1L);
        user.setUsername("trader");
        when(properties.getMonteCarloSimulations()).thenReturn(2000);
        when(properties.getPeriodsPerYear()).thenReturn(365);
        when(assetService.getAssetsByUser(user)).thenReturn(List.of(new Asset("Bitcoin", 0.0617, 82500, 82500)));

        // One coin worth $5,089 with a year of daily returns
        Random rnd = new Random(3);
        int days = 365;
        List<LocalDate> dates = new ArrayList<>();
        double[][] prices = new double[days][1], returns = new double[days - 1][1];
        double p = 80000;
        for (int t = 0; t < days; t++) {
            dates.add(LocalDate.of(2025, 10, 20).plusDays(t));
            if (t > 0) {
                double r = rnd.nextGaussian() * 0.03;
                returns[t - 1][0] = r;
                p *= 1 + r;
            }
            prices[t][0] = p;
        }
        PortfolioSnapshot snapshot = new PortfolioSnapshot(List.of("bitcoin"), List.of("Bitcoin"), new double[]{0.0617},
                new double[]{5089}, new double[]{1.0}, 5089, dates, prices, returns, null);
        when(portfolioDataService.snapshot(any(), anyBoolean())).thenReturn(snapshot);
    }

    private void cash(Double cash) {
        when(accountService.account(user)).thenReturn(new AccountService.Account("trader", "t@x.io",
                cash == null ? null : new BigDecimal("10000"), null, cash, 4911, 4911, true));
    }

    @Test
    @DisplayName("Portfolio value is equity: $5,089 of coins plus $4,911 cash = $10,000")
    void equityIncludesCash() {
        cash(4911.0);
        VaRCalculation v = service.calculatePortfolioVaR(user, 0.95, 1);
        assertEquals(10000.0, v.getPortfolioValue(), 1e-6);
        assertEquals(5089.0, v.getInvestedValue(), 1e-6);
        assertEquals(4911.0, v.getCashValue(), 1e-6);
        assertEquals(0.5089, v.getExposure(), 1e-9);
        assertEquals(0.5089, v.getAssetWeights().get("Bitcoin"), 1e-9);
    }

    @Test
    @DisplayName("Cash is riskless: dollar VaR is unchanged, percentages and volatility are relative to equity")
    void cashDilutesPercentagesNotDollars() {
        cash(0.0);
        VaRCalculation coinsOnly = service.calculatePortfolioVaR(user, 0.95, 1);
        cash(4911.0);
        VaRCalculation withCash = service.calculatePortfolioVaR(user, 0.95, 1);

        assertEquals(coinsOnly.getHistoricalVaR(), withCash.getHistoricalVaR(), 1e-6);
        assertEquals(coinsOnly.getConditionalVaR(), withCash.getConditionalVaR(), 1e-6);
        assertEquals(coinsOnly.getParametricVaR(), withCash.getParametricVaR(), 1e-6);
        assertEquals(coinsOnly.getMonteCarloVaR(), withCash.getMonteCarloVaR(), coinsOnly.getMonteCarloVaR() * 0.1); // simulated
        assertEquals(coinsOnly.getVolatility() * 0.5089, withCash.getVolatility(), 1e-12);
        assertEquals(coinsOnly.getHistoricalVaR() / 10000, withCash.getHistoricalVaR() / withCash.getPortfolioValue(), 1e-12);
        assertEquals(coinsOnly.getSkewness(), withCash.getSkewness(), 1e-9);
    }

    @Test
    @DisplayName("Without a bankroll there is no cash, and the account is the holdings")
    void noBankroll() {
        cash(null);
        VaRCalculation v = service.calculatePortfolioVaR(user, 0.95, 1);
        assertEquals(5089.0, v.getPortfolioValue(), 1e-6);
        assertEquals(1.0, v.getExposure(), 1e-12);
    }
}
