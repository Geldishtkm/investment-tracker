package com.portfolio.tracker.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class CryptoPriceService {

    private final RestTemplate restTemplate;

    public CryptoPriceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public double getCryptoPriceInUSD(String coinId) {
        // Example: coinId = "bitcoin", "ethereum", "solana", etc.
        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + coinId + "&vs_currencies=usd";

        // Use Map<String, Map<String, Object>> to handle both Integer and Double values
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
}

