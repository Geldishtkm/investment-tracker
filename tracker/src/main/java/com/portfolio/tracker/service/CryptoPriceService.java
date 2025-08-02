package com.portfolio.tracker.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class CryptoPriceService {

    private final RestTemplate restTemplate;

    private List<Map<String, Object>> cachedTopCoins;
    private long lastCacheTime = 0;
    private final long cacheDuration = 10 * 60 * 1000; // 10 minutes

    public CryptoPriceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ✅ Scheduled cache refresh every 10 minutes
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void refreshCache() {
        try {
            String url = "https://api.coingecko.com/api/v3/coins/markets" +
                         "?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false";

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            cachedTopCoins = response.getBody();
            lastCacheTime = System.currentTimeMillis();
            System.out.println("✅ Cache refreshed successfully.");

        } catch (Exception e) {
            System.err.println("⚠️ Failed to refresh cache: " + e.getMessage());
        }
    }

    // ✅ Return cached or fetch if expired
    public List<Map<String, Object>> getTopCoins() {
        long now = System.currentTimeMillis();
        if (cachedTopCoins == null || (now - lastCacheTime) > cacheDuration) {
            refreshCache();
            if (cachedTopCoins == null) {
                throw new RuntimeException("❌ Failed to load top coins from API and cache is empty");
            }
        }
        return cachedTopCoins;
    }

    // ✅ Get current price of a coin in USD
    public double getCryptoPriceInUSD(String coinId) {
        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + coinId + "&vs_currencies=usd";
        ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        Map<String, Map<String, Object>> priceMap = response.getBody();

        if (priceMap != null && priceMap.containsKey(coinId)) {
            Object priceObj = priceMap.get(coinId).get("usd");
            if (priceObj instanceof Number) {
                return ((Number) priceObj).doubleValue();
            } else {
                throw new RuntimeException("Price is not a number for coin: " + coinId);
            }
        } else {
            throw new RuntimeException("Coin price not found for: " + coinId);
        }
    }

    // ✅ Get coin name, symbol, and image
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCoinDetails(String coinId) {
        String url = "https://api.coingecko.com/api/v3/coins/" + coinId;
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null) {
            throw new RuntimeException("Coin details not found for: " + coinId);
        }

        Map<String, Object> imageMap = (Map<String, Object>) response.get("image");

        return Map.of(
                "id", response.get("id"),
                "symbol", response.get("symbol"),
                "name", response.get("name"),
                "image", imageMap.get("large")
        );
    }
}
