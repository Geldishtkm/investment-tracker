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
import static org.mockito.ArgumentMatchers.contains;
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

    /** A /coins/markets page of `count` coins starting at rank `from`, each with a 168-point sparkline. */
    private static String marketsPage(int from, int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            int rank = from + i;
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"coin").append(rank).append("\",\"symbol\":\"c").append(rank)
              .append("\",\"name\":\"Coin ").append(rank).append("\",\"image\":\"https://img/coins/images/1/large/c.png\"")
              .append(",\"current_price\":").append(rank).append(",\"market_cap\":1,\"market_cap_rank\":").append(rank)
              .append(",\"ath\":5,\"roi\":null,\"sparkline_in_7d\":{\"price\":[");
            for (int p = 0; p < 168; p++) sb.append(p == 0 ? "" : ",").append(p);
            sb.append("]}}");
        }
        return sb.append(']').toString();
    }

    @Test
    @DisplayName("The catalog holds the top 500 coins, fetched as two pages of 250")
    void fetchesFiveHundredCoins() {
        when(coinGecko.get(contains("&page=1&"))).thenReturn(marketsPage(1, 250));
        when(coinGecko.get(contains("&page=2&"))).thenReturn(marketsPage(251, 250));
        CryptoPriceService service = new CryptoPriceService(coinGecko, 60);

        List<Map<String, Object>> coins = service.getTopCoins();
        assertEquals(500, coins.size());
        assertEquals("coin500", coins.get(499).get("id"));
        verify(coinGecko, times(2)).get(contains("/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=250"));
        assertEquals(123.0, service.getPricesInUSD(List.of("coin123")).get("coin123")); // priced from the catalog, no extra call
        verify(coinGecko, times(2)).get(anyString());
    }

    @Test
    @DisplayName("Browsers get a compact catalog: used fields, a 42-point sparkline and small icons")
    void compactCatalog() {
        when(coinGecko.get(contains("&page=1&"))).thenReturn(marketsPage(1, 250));
        when(coinGecko.get(contains("&page=2&"))).thenReturn(marketsPage(251, 250));
        CryptoPriceService service = new CryptoPriceService(coinGecko, 60);

        Map<String, Object> btc = service.getCatalog().get(0);
        assertFalse(btc.containsKey("ath"));
        assertFalse(btc.containsKey("roi"));
        assertEquals("https://img/coins/images/1/small/c.png", btc.get("image"));
        List<?> spark = (List<?>) ((Map<?, ?>) btc.get("sparkline_in_7d")).get("price");
        assertEquals(43, spark.size()); // every 4th hour, plus the latest
        assertEquals(167, ((Number) spark.get(spark.size() - 1)).intValue());
    }

    @Test
    @DisplayName("A rate-limited second page keeps the previous coins beyond rank 250")
    void secondPageFailureKeepsTail() {
        when(coinGecko.get(contains("&page=1&"))).thenReturn(marketsPage(1, 250));
        when(coinGecko.get(contains("&page=2&")))
                .thenReturn(marketsPage(251, 250))
                .thenThrow(new CoinGeckoClient.RateLimitedException(60));
        CryptoPriceService service = new CryptoPriceService(coinGecko, 60);
        service.refreshCache();
        service.refreshCache();
        assertEquals(500, service.getTopCoins().size());
        assertEquals("coin251", service.getTopCoins().get(250).get("id"));
    }
}
