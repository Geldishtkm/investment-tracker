package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PriceHistoryService {

    private final Map<String, List<double[]>> priceHistoryCache = new HashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String COINGECKO_API_URL_TEMPLATE =
            "https://api.coingecko.com/api/v3/coins/%s/market_chart?vs_currency=usd&days=%d&interval=daily";

    public List<double[]> getPriceHistory(String coinId, int days) {
        String cacheKey = coinId + "_" + days;

        if (priceHistoryCache.containsKey(cacheKey)) {
            return priceHistoryCache.get(cacheKey);
        }

        return fetchAndCachePriceHistory(coinId, days);
    }

    public List<double[]> fetchAndCachePriceHistory(String coinId, int days) {
        try {
            String url = String.format(COINGECKO_API_URL_TEMPLATE, coinId, days);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode pricesNode = root.path("prices");

            List<double[]> priceHistory = new ArrayList<>();
            for (JsonNode entry : pricesNode) {
                double timestamp = entry.get(0).asDouble();
                double price = entry.get(1).asDouble();
                priceHistory.add(new double[]{timestamp, price});
            }

            // Cache the result
            priceHistoryCache.put(coinId + "_" + days, priceHistory);
            return priceHistory;
        } catch (Exception e) {
            System.err.println("⚠️ Error fetching price history for " + coinId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void refreshPriceHistory(String coinId, int days) {
        fetchAndCachePriceHistory(coinId, days);
    }

    public Map<String, Object> getCacheStatus(String coinId) {
        Map<String, Object> status = new HashMap<>();
        for (String key : priceHistoryCache.keySet()) {
            if (key.startsWith(coinId + "_")) {
                status.put(key, priceHistoryCache.get(key).size());
            }
        }
        return status;
    }

    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        for (Map.Entry<String, List<double[]>> entry : priceHistoryCache.entrySet()) {
            status.put(entry.getKey(), entry.getValue().size());
        }
        return status;
    }
}

