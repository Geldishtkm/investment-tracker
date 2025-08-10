package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for fetching and caching cryptocurrency price history data
 * Provides historical price data for portfolio analysis and charts
 */
@Service
public class PriceHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(PriceHistoryService.class);
    
    // API configuration
    private static final String COINGECKO_API_URL_TEMPLATE = 
            "https://api.coingecko.com/api/v3/coins/%s/market_chart?vs_currency=usd&days=%d&interval=daily";
    
    // Cache configuration
    private static final int MAX_CACHE_SIZE = 100; // Maximum number of cached entries
    private static final String CACHE_KEY_SEPARATOR = "_";

    private final Map<String, List<double[]>> priceHistoryCache = new HashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get price history for a cryptocurrency, using cache if available
     * @param coinId the cryptocurrency identifier
     * @param days number of days of history to retrieve
     * @return list of price data points [timestamp, price]
     */
    public List<double[]> getPriceHistory(String coinId, int days) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }
        if (days <= 0 || days > 365) {
            throw new IllegalArgumentException("Days must be between 1 and 365");
        }

        String cacheKey = buildCacheKey(coinId, days);
        
        // Check cache first
        if (priceHistoryCache.containsKey(cacheKey)) {
            List<double[]> cachedData = priceHistoryCache.get(cacheKey);
            logger.debug("Returning {} data points from cache for {} ({} days)", 
                       cachedData.size(), coinId, days);
            return new ArrayList<>(cachedData); // Return copy to prevent modification
        }

        // Fetch from API and cache
        return fetchAndCachePriceHistory(coinId, days);
    }

    /**
     * Fetch price history from API and store in cache
     * @param coinId the cryptocurrency identifier
     * @param days number of days of history to retrieve
     * @return list of price data points [timestamp, price]
     */
    public List<double[]> fetchAndCachePriceHistory(String coinId, int days) {
        try {
            String url = String.format(COINGECKO_API_URL_TEMPLATE, coinId, days);
            logger.debug("Fetching price history for {} over {} days", coinId, days);
            
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.trim().isEmpty()) {
                logger.warn("Empty response from API for coin: {}", coinId);
                return Collections.emptyList();
            }

            List<double[]> priceHistory = parsePriceHistoryResponse(response);
            String cacheKey = buildCacheKey(coinId, days);
            
            // Manage cache size and store new data
            manageCacheSize();
            priceHistoryCache.put(cacheKey, new ArrayList<>(priceHistory));
            
            logger.info("Successfully cached {} data points for {} ({} days)", 
                       priceHistory.size(), coinId, days);
            return priceHistory;
            
        } catch (Exception e) {
            logger.error("Error fetching price history for {} ({} days): {}", 
                        coinId, days, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Parse the API response to extract price history data
     * @param response JSON response from the API
     * @return list of price data points [timestamp, price]
     */
    private List<double[]> parsePriceHistoryResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode pricesNode = root.path("prices");
        
        if (!pricesNode.isArray()) {
            throw new RuntimeException("Invalid response format: prices array not found");
        }

        List<double[]> priceHistory = new ArrayList<>();
        for (JsonNode entry : pricesNode) {
            if (entry.isArray() && entry.size() >= 2) {
                try {
                    double timestamp = entry.get(0).asDouble();
                    double price = entry.get(1).asDouble();
                    priceHistory.add(new double[]{timestamp, price});
                } catch (Exception e) {
                    logger.warn("Skipping invalid price entry: {}", entry.toString());
                }
            }
        }
        
        return priceHistory;
    }

    /**
     * Refresh price history for a specific coin and time period
     * @param coinId the cryptocurrency identifier
     * @param days number of days of history to retrieve
     */
    public void refreshPriceHistory(String coinId, int days) {
        if (coinId == null || coinId.trim().isEmpty()) {
            logger.warn("Cannot refresh price history: coin ID is null or empty");
            return;
        }
        
        logger.info("Refreshing price history for {} ({} days)", coinId, days);
        fetchAndCachePriceHistory(coinId, days);
    }

    /**
     * Get cache status for a specific coin
     * @param coinId the cryptocurrency identifier
     * @return map containing cache keys and data point counts for the coin
     */
    public Map<String, Object> getCacheStatus(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> status = new HashMap<>();
        for (String key : priceHistoryCache.keySet()) {
            if (key.startsWith(coinId + CACHE_KEY_SEPARATOR)) {
                List<double[]> data = priceHistoryCache.get(key);
                status.put(key, data != null ? data.size() : 0);
            }
        }
        
        logger.debug("Cache status for {}: {} entries", coinId, status.size());
        return status;
    }

    /**
     * Get overall service cache status
     * @return map containing all cache keys and their data point counts
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        for (Map.Entry<String, List<double[]>> entry : priceHistoryCache.entrySet()) {
            status.put(entry.getKey(), entry.getValue().size());
        }
        
        logger.debug("Service cache status: {} total entries", status.size());
        return status;
    }

    /**
     * Clear all cached data
     */
    public void clearCache() {
        int size = priceHistoryCache.size();
        priceHistoryCache.clear();
        logger.info("Cleared cache with {} entries", size);
    }

    /**
     * Build cache key for a coin and time period
     * @param coinId the cryptocurrency identifier
     * @param days number of days
     * @return cache key string
     */
    private String buildCacheKey(String coinId, int days) {
        return coinId.toLowerCase() + CACHE_KEY_SEPARATOR + days;
    }

    /**
     * Manage cache size to prevent memory issues
     * Removes oldest entries if cache exceeds maximum size
     */
    private void manageCacheSize() {
        if (priceHistoryCache.size() >= MAX_CACHE_SIZE) {
            // Remove oldest entries (simple FIFO approach)
            Iterator<String> iterator = priceHistoryCache.keySet().iterator();
            int removedCount = 0;
            
            while (iterator.hasNext() && priceHistoryCache.size() >= MAX_CACHE_SIZE / 2) {
                iterator.next();
                iterator.remove();
                removedCount++;
            }
            
            if (removedCount > 0) {
                logger.info("Cache size limit reached, removed {} old entries", removedCount);
            }
        }
    }
}

