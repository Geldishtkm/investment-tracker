package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.model.PriceHistoryCacheEntry;
import com.portfolio.tracker.repository.PriceHistoryCacheRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Price history caching and rate-limit fallback")
class PriceHistoryServiceTest {

    @Mock
    private CoinGeckoClient coinGecko;

    @Mock
    private PriceHistoryCacheRepository repository;

    @Mock
    private AnalyticsProperties properties;

    private PriceHistoryService service;

    private static final long DAY = Duration.ofDays(1).toMillis();

    @BeforeEach
    void setUp() {
        when(properties.getPriceCacheTtlMinutes()).thenReturn(30L);
        when(repository.findById(any())).thenReturn(Optional.empty());
        service = new PriceHistoryService(coinGecko, repository, properties);
    }

    /** A year of daily prices ending now, as CoinGecko's market_chart returns them. */
    private static String yearOfPrices(double price) {
        long end = Instant.now().toEpochMilli();
        StringBuilder json = new StringBuilder("{\"prices\":[");
        for (int d = 365; d >= 0; d--) {
            if (d < 365) json.append(',');
            json.append('[').append(end - d * DAY).append(',').append(price).append(']');
        }
        return json.append("]}").toString();
    }

    @Test
    @DisplayName("One CoinGecko call serves every time range until the TTL expires")
    void cachesAndSlicesOneYearFetch() {
        when(coinGecko.get(anyString())).thenReturn(yearOfPrices(100));

        assertEquals(366, service.getPriceHistory("bitcoin", 365).size());
        assertEquals(8, service.getPriceHistory("bitcoin", 7).size());
        assertEquals(31, service.getPriceHistory("Bitcoin", 30).size());
        service.getDailyPrices("bitcoin", 365);

        verify(coinGecko, times(1)).get(contains("/coins/bitcoin/market_chart?vs_currency=usd&days=365"));
        verify(repository, times(1)).save(any(PriceHistoryCacheEntry.class));
    }

    @Test
    @DisplayName("Rate limited with saved data from before a restart: serves the saved data")
    void servesDatabaseCopyWhenRateLimited() {
        String saved = "[[" + (Instant.now().toEpochMilli() - DAY) + ",95.0],[" + Instant.now().toEpochMilli() + ",96.0]]";
        PriceHistoryCacheEntry entry = new PriceHistoryCacheEntry("bitcoin", "usd", saved,
                LocalDateTime.ofInstant(Instant.now().minus(Duration.ofHours(5)), ZoneOffset.UTC));
        when(repository.findById(any())).thenReturn(Optional.of(entry));
        when(coinGecko.get(anyString())).thenThrow(new CoinGeckoClient.RateLimitedException(60));

        List<double[]> points = service.getPriceHistory("bitcoin", 30);

        assertEquals(2, points.size());
        assertEquals(96.0, points.get(1)[1]);
    }

    @Test
    @DisplayName("Fresh saved data is used without calling CoinGecko at all")
    void freshDatabaseCopyAvoidsApiCall() {
        String saved = "[[" + Instant.now().toEpochMilli() + ",42.0]]";
        when(repository.findById(any())).thenReturn(Optional.of(new PriceHistoryCacheEntry("bitcoin", "usd", saved,
                LocalDateTime.ofInstant(Instant.now().minus(Duration.ofMinutes(5)), ZoneOffset.UTC))));

        assertEquals(42.0, service.getPriceHistory("bitcoin", 7).get(0)[1]);
        verify(coinGecko, never()).get(anyString());
    }

    @Test
    @DisplayName("Rate limited with nothing saved: a clear 'try again' error, not a crash")
    void rateLimitedWithoutCache() {
        when(coinGecko.get(anyString())).thenThrow(new CoinGeckoClient.RateLimitedException(42));

        PortfolioDataException e = assertThrows(PortfolioDataException.class,
                () -> service.getPriceHistory("ethereum", 30));
        assertEquals(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE, e.getReason());
        assertTrue(e.getMessage().contains("42 seconds"));
    }

    @Test
    @DisplayName("Concurrent requests for the same coin share one CoinGecko call")
    void coalescesConcurrentRequests() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(coinGecko.get(anyString())).thenAnswer(inv -> {
            release.await(5, TimeUnit.SECONDS);
            return yearOfPrices(100);
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<double[]>>> results = List.of(
                    pool.submit(() -> service.getPriceHistory("solana", 30)),
                    pool.submit(() -> service.getPriceHistory("solana", 90)),
                    pool.submit(() -> service.getPriceHistory("solana", 365)),
                    pool.submit(() -> service.getPriceHistory("solana", 7)));
            Thread.sleep(200); // let all four reach the in-flight request
            release.countDown();
            for (Future<List<double[]>> r : results) assertFalse(r.get(5, TimeUnit.SECONDS).isEmpty());
        } finally {
            pool.shutdownNow();
        }
        verify(coinGecko, times(1)).get(anyString());
    }

    @Test
    @DisplayName("Unknown coin IDs are remembered so they don't cost repeated API calls")
    void remembersUnknownCoins() {
        when(coinGecko.get(anyString())).thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        for (int i = 0; i < 3; i++) {
            PortfolioDataException e = assertThrows(PortfolioDataException.class,
                    () -> service.getPriceHistory("not-a-coin", 30));
            assertEquals(PortfolioDataException.Reason.UNKNOWN_ASSET, e.getReason());
        }
        verify(coinGecko, times(1)).get(anyString());
    }
}
