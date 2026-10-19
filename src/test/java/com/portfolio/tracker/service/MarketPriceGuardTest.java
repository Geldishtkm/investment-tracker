package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Paper trades execute at market prices")
class MarketPriceGuardTest {

    @Mock private CoinMarketDataService coinMarketDataService;
    @Mock private CryptoPriceService cryptoPriceService;
    @Mock private PriceHistoryService priceHistoryService;
    @Mock private AnalyticsProperties properties;

    private MarketPriceGuard guard;
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        guard = new MarketPriceGuard(coinMarketDataService, cryptoPriceService, priceHistoryService, properties);
        when(properties.getLookbackDays()).thenReturn(365);
        when(coinMarketDataService.resolveCoinId("Bitcoin")).thenReturn("bitcoin");
        when(cryptoPriceService.getQuotes(List.of("bitcoin"))).thenReturn(Map.of("bitcoin", new CryptoPriceService.Quote(80_000, Instant.now())));
        TreeMap<LocalDate, Double> daily = new TreeMap<>();
        daily.put(today.minusDays(30), 60_000.0);
        daily.put(today.minusDays(28), 64_000.0);
        when(priceHistoryService.getDailyPrices("bitcoin", 365)).thenReturn(daily);
    }

    @Test
    @DisplayName("Today's trades must be within ±10% of the live price")
    void live() {
        guard.verify("Bitcoin", 80_000, today);
        guard.verify("Bitcoin", 72_500, today);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> guard.verify("Bitcoin", 1, today));
        assertTrue(e.getMessage().startsWith("$1.00 is 100% below Bitcoin's market price ($80,000.00, the current market price)"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> guard.verify("Bitcoin", 90_000, today));
    }

    @Test
    @DisplayName("Earlier trades must be within ±25% of that day's close, or the last close before it")
    void daily() {
        guard.verify("Bitcoin", 70_000, today.minusDays(30));
        assertThrows(IllegalArgumentException.class, () -> guard.verify("Bitcoin", 80_000, today.minusDays(30)));
        guard.verify("Bitcoin", 64_000, today.minusDays(27)); // no close that day: the 28-days-ago close is used
        IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                () -> guard.verify("Bitcoin", 30_000, today.minusDays(400)));
        assertTrue(old.getMessage().contains("at most 364 days back"), old.getMessage());
    }

    @Test
    @DisplayName("Unknown coins are refused; an unreachable market is a 503, not a pass")
    void unknownOrUnavailable() {
        when(coinMarketDataService.resolveCoinId("MyCoin")).thenThrow(
                new PortfolioDataException(PortfolioDataException.Reason.UNKNOWN_ASSET, "Could not find \"MyCoin\" on CoinGecko."));
        assertThrows(IllegalArgumentException.class, () -> guard.verify("MyCoin", 1, today));

        when(cryptoPriceService.getQuotes(List.of("bitcoin"))).thenReturn(Map.of());
        AccountService.AccountException e = assertThrows(AccountService.AccountException.class,
                () -> guard.verify("Bitcoin", 80_000, today));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }
}
