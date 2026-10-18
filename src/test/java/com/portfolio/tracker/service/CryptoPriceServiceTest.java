package com.portfolio.tracker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spot prices: batching, caching and rate-limit fallback")
class CryptoPriceServiceTest {

    @Mock
    private CoinGeckoClient coinGecko;

    @Test
    @DisplayName("Prices for several coins come from one /simple/price request")
    void batchesPrices() {
        when(coinGecko.get("/simple/price?ids=bitcoin,ethereum,binancecoin&vs_currencies=usd"))
                .thenReturn("{\"bitcoin\":{\"usd\":81000},\"ethereum\":{\"usd\":3000},\"binancecoin\":{\"usd\":600}}");
        CryptoPriceService service = new CryptoPriceService(coinGecko, 60);

        Map<String, Double> prices = service.getPricesInUSD(List.of("bitcoin", "Ethereum", "binancecoin", "bitcoin"));

        assertEquals(Map.of("bitcoin", 81000.0, "ethereum", 3000.0, "binancecoin", 600.0), prices);
        verify(coinGecko, times(1)).get(anyString());
    }

    @Test
    @DisplayName("Repeated requests within the TTL are served from cache")
    void cachesSpotPrices() {
        when(coinGecko.get(anyString())).thenReturn("{\"bitcoin\":{\"usd\":81000}}");
        CryptoPriceService service = new CryptoPriceService(coinGecko, 60);

        service.getPricesInUSD(List.of("bitcoin"));
        service.getPricesInUSD(List.of("bitcoin"));
        assertEquals(81000.0, service.getCryptoPriceInUSD("bitcoin"));

        verify(coinGecko, times(1)).get(anyString());
    }

    @Test
    @DisplayName("When rate limited, the last known prices are returned")
    void servesLastKnownPriceWhenRateLimited() {
        when(coinGecko.get(anyString()))
                .thenReturn("{\"bitcoin\":{\"usd\":81000}}")
                .thenThrow(new CoinGeckoClient.RateLimitedException(60));
        CryptoPriceService service = new CryptoPriceService(coinGecko, 0); // TTL 0: always try to refresh

        service.getPricesInUSD(List.of("bitcoin"));
        Map<String, Double> prices = service.getPricesInUSD(List.of("bitcoin", "ethereum"));

        assertEquals(81000.0, prices.get("bitcoin"));
        assertFalse(prices.containsKey("ethereum")); // never priced before, so nothing to fall back to
    }

    @Test
    @DisplayName("Quotes carry their time: live now, or the old time when only the last known price is available")
    void quotesCarryTheirTime() throws Exception {
        when(coinGecko.get(anyString()))
                .thenReturn("{\"bitcoin\":{\"usd\":81000}}")
                .thenThrow(new CoinGeckoClient.RateLimitedException(60));
        CryptoPriceService service = new CryptoPriceService(coinGecko, 0);

        java.time.Instant before = java.time.Instant.now();
        CryptoPriceService.Quote live = service.getQuotes(List.of("bitcoin")).get("bitcoin");
        assertFalse(live.asOf().isBefore(before));

        Thread.sleep(5);
        CryptoPriceService.Quote fallback = service.getQuotes(List.of("bitcoin")).get("bitcoin");
        assertEquals(81000.0, fallback.usd());
        assertEquals(live.asOf(), fallback.asOf(), "a fallback keeps the time it was really quoted");
    }
}
