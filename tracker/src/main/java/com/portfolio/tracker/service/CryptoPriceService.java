package com.portfolio.tracker.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class CryptoPriceService {

    private final RestTemplate restTemplate;

    public CryptoPriceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ✅ Get current price of a coin in USD
    public double getCryptoPriceInUSD(String coinId) {
        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + coinId + "&vs_currencies=usd";
        Map<String, Map<String, Object>> response = restTemplate.getForObject(url, Map.class);

        if (response != null && response.containsKey(coinId)) {
            Object priceObj = response.get(coinId).get("usd");
            if (priceObj instanceof Number) {
                return ((Number) priceObj).doubleValue();
            } else {
                throw new RuntimeException("Price is not a number for coin: " + coinId);
            }
        } else {
            throw new RuntimeException("Coin price not found for: " + coinId);
        }
    }

    // ✅ Get basic info (id, symbol, name, image) for a single coin
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

    // ✅ Get top 300 coins by market cap (includes image, price, etc.)
    public List<Map<String, Object>> getTopCoins() {
        String url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=50&page=1&sparkline=false";
        return restTemplate.getForObject(url, List.class);
    }

    // (Optional) Get all 17k+ coins - not recommended for frontend
    public List<Map<String, String>> getAllCoins() {
        String url = "https://api.coingecko.com/api/v3/coins/list";
        return restTemplate.getForObject(url, List.class);
    }
}

