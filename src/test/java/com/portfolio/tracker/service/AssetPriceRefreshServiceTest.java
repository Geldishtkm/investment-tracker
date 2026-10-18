package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Holding prices after a long break: refreshed when possible, reported when not")
class AssetPriceRefreshServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private CoinMarketDataService coinMarketDataService;

    @Mock
    private CryptoPriceService cryptoPriceService;

    private AssetPriceRefreshService service;
    private User user;
    private Asset btc;
    private final Instant threeWeeksAgo = Instant.now().minus(Duration.ofDays(21));

    @BeforeEach
    void setUp() {
        service = new AssetPriceRefreshService(assetRepository, coinMarketDataService, cryptoPriceService);
        user = new User();
        user.setUsername("returning");
        btc = new Asset("Bitcoin", 1.0, 60_000, 50_000);
        btc.setPriceUpdatedAt(threeWeeksAgo);           // last priced in the previous session
        when(assetRepository.findByUser(user)).thenReturn(List.of(btc));
        when(coinMarketDataService.resolveCoinId("Bitcoin")).thenReturn("bitcoin");
    }

    @Test
    @DisplayName("Returning after three weeks: the old price is replaced by the live quote")
    void replacesOldPrice() {
        Instant now = Instant.now();
        when(cryptoPriceService.getQuotes(anyCollection())).thenReturn(Map.of("bitcoin", new CryptoPriceService.Quote(81_000, now)));

        AssetPriceRefreshService.Result r = service.refreshPrices(user);

        assertEquals(81_000, btc.getPricePerUnit());
        assertEquals(now, btc.getPriceUpdatedAt());
        assertEquals(1, r.updated());
        assertTrue(r.stale().isEmpty());
        assertEquals(now, r.oldestPriceAt());
        verify(assetRepository).save(btc);
    }

    @Test
    @DisplayName("An unchanged price still records that it was checked, so it isn't reported as stale")
    void unchangedPriceIsFresh() {
        Instant now = Instant.now();
        when(cryptoPriceService.getQuotes(anyCollection())).thenReturn(Map.of("bitcoin", new CryptoPriceService.Quote(60_000, now)));

        AssetPriceRefreshService.Result r = service.refreshPrices(user);

        assertEquals(0, r.updated());
        assertEquals(now, btc.getPriceUpdatedAt());
        assertTrue(r.stale().isEmpty());
    }

    @Test
    @DisplayName("CoinGecko unreachable: the stored price is kept and reported stale with its age")
    void reportsStaleWhenUnavailable() {
        when(cryptoPriceService.getQuotes(anyCollection())).thenReturn(Map.of());

        AssetPriceRefreshService.Result r = service.refreshPrices(user);

        assertEquals(60_000, btc.getPricePerUnit());
        assertEquals(List.of("Bitcoin"), r.unpriced());
        assertEquals(List.of("Bitcoin"), r.stale());
        assertEquals(threeWeeksAgo, r.oldestPriceAt());
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("A fallback quote older than the stored price never overwrites it")
    void olderQuoteIgnored() {
        Instant stored = Instant.now().minus(Duration.ofMinutes(2));
        btc.setPriceUpdatedAt(stored);
        when(cryptoPriceService.getQuotes(anyCollection())).thenReturn(
                Map.of("bitcoin", new CryptoPriceService.Quote(55_000, Instant.now().minus(Duration.ofHours(3)))));

        service.refreshPrices(user);

        assertEquals(60_000, btc.getPricePerUnit());
        assertEquals(stored, btc.getPriceUpdatedAt());
    }

    @Test
    @DisplayName("A holding that was never priced counts as stale and leaves oldestPriceAt unknown")
    void neverPriced() {
        btc.setPriceUpdatedAt(null);
        when(coinMarketDataService.resolveCoinId("Bitcoin")).thenThrow(
                new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE, "down"));

        AssetPriceRefreshService.Result r = service.refreshPrices(user);

        assertEquals(List.of("Bitcoin"), r.stale());
        assertNull(r.oldestPriceAt());
    }

    @Test
    @DisplayName("Valuation endpoints never fail because a refresh failed")
    void refreshQuietlyNeverThrows() {
        when(cryptoPriceService.getQuotes(anyCollection())).thenThrow(new RuntimeException("boom"));

        AssetPriceRefreshService.Result r = assertDoesNotThrow(() -> service.refreshQuietly(user));
        assertEquals(List.of("Bitcoin"), r.stale());
    }
}
