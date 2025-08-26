package com.portfolio.tracker.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Service for fetching cryptocurrency prices and details from external APIs
 * Includes caching mechanisms to reduce API calls and improve performance
 */
@Service
public class CryptoPriceService {

    private static final Logger logger = LoggerFactory.getLogger(CryptoPriceService.class);
    
    // API endpoints
    private static final String TOP_COINS_URL = "https://api.coingecko.com/api/v3/coins/markets";
    private static final String SIMPLE_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price";
    private static final String COIN_DETAILS_URL = "https://api.coingecko.com/api/v3/coins/";
    
    // Cache configuration
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000L; // 10 minutes
    private static final int TOP_COINS_LIMIT = 100;
    
    // API parameters
    private static final String CURRENCY_USD = "usd";
    private static final String ORDER_MARKET_CAP = "market_cap_desc";
    private static final String SPARKLINE_FALSE = "false";

    private final RestTemplate restTemplate;

    // Cache for top coins with timestamp tracking
    private List<Map<String, Object>> cachedTopCoins;
    private long lastCacheTime = 0;

    public CryptoPriceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        logger.info("CryptoPriceService initialized with cache duration: {} ms", CACHE_DURATION_MS);
    }

    /**
     * Scheduled task to refresh the top coins cache every 10 minutes
     */
    @Scheduled(fixedRate = CACHE_DURATION_MS)
    public void refreshCache() {
        try {
            logger.debug("Refreshing top coins cache...");
            
            String url = buildTopCoinsUrl();
            logger.info("Fetching from CoinGecko URL: {}", url);
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null) {
                List<Map<String, Object>> coins = response.getBody();
                logger.info("Received {} coins from CoinGecko", coins.size());
                
                // Log first few coins for debugging
                if (!coins.isEmpty()) {
                    Map<String, Object> firstCoin = coins.get(0);
                    logger.info("First coin data: id={}, name={}, current_price={}, price_change_24h={}", 
                        firstCoin.get("id"), 
                        firstCoin.get("name"), 
                        firstCoin.get("current_price"),
                        firstCoin.get("price_change_24h"));
                    
                }
                
                // Validate and fix prices before caching
                List<Map<String, Object>> validatedCoins = validateAndFixPrices(coins);
                cachedTopCoins = validatedCoins;
                lastCacheTime = System.currentTimeMillis();
                logger.info("Successfully refreshed cache with {} validated coins", cachedTopCoins.size());
            } else {
                logger.warn("Received empty response when refreshing cache");
            }

        } catch (Exception e) {
            logger.error("Failed to refresh top coins cache: {}", e.getMessage(), e);
            // Don't clear existing cache on failure - keep using stale data
        }
    }

    /**
     * Get the top 100 cryptocurrencies by market cap
     * @return list of top coins with their market data
     * @throws RuntimeException if both API and cache fail
     */
    public List<Map<String, Object>> getTopCoins() {
        long now = System.currentTimeMillis();
        
        // Check if cache is valid
        if (isCacheValid(now)) {
            logger.debug("Returning {} coins from cache", cachedTopCoins.size());
            return cachedTopCoins;
        }

        // Cache expired or empty, try to refresh
        logger.debug("Cache expired, attempting to refresh...");
        refreshCache();
        
        if (cachedTopCoins == null || cachedTopCoins.isEmpty()) {
            logger.error("Failed to load top coins from API and cache is empty");
            throw new RuntimeException("Unable to fetch cryptocurrency data");
        }
        
        return cachedTopCoins;
    }

    /**
     * Get current USD price for a specific cryptocurrency
     * @param coinId the cryptocurrency identifier (e.g., bitcoin, ethereum)
     * @return current price in USD
     * @throws RuntimeException if price cannot be fetched or parsed
     */
    public double getCryptoPriceInUSD(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }

        try {
            String url = buildSimplePriceUrl(coinId);
            logger.debug("Fetching price for coin: {}", coinId);
            
            ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Map<String, Object>> priceMap = response.getBody();
            if (priceMap == null || !priceMap.containsKey(coinId)) {
                throw new RuntimeException("Price data not found for coin: " + coinId);
            }

            Object priceObj = priceMap.get(coinId).get(CURRENCY_USD);
            if (priceObj instanceof Number) {
                double price = ((Number) priceObj).doubleValue();
                logger.debug("Retrieved price for {}: ${}", coinId, price);
                return price;
            } else {
                throw new RuntimeException("Invalid price format for coin: " + coinId);
            }
            
        } catch (Exception e) {
            logger.error("Failed to get price for coin {}: {}", coinId, e.getMessage());
            throw new RuntimeException("Failed to fetch price for " + coinId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get detailed information about a specific cryptocurrency
     * @param coinId the cryptocurrency identifier
     * @return map containing coin details (id, symbol, name, image)
     * @throws RuntimeException if coin details cannot be fetched
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCoinDetails(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }

        try {
            String url = COIN_DETAILS_URL + coinId;
            logger.debug("Fetching details for coin: {}", coinId);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                throw new RuntimeException("Coin details not found for: " + coinId);
            }

            Map<String, Object> imageMap = (Map<String, Object>) response.get("image");
            String imageUrl = imageMap != null ? (String) imageMap.get("large") : null;

            Map<String, Object> details = Map.of(
                    "id", response.get("id"),
                    "symbol", response.get("symbol"),
                    "name", response.get("name"),
                    "image", imageUrl != null ? imageUrl : ""
            );
            
            logger.debug("Retrieved details for coin: {}", coinId);
            return details;
            
        } catch (Exception e) {
            logger.error("Failed to get details for coin {}: {}", coinId, e.getMessage());
            throw new RuntimeException("Failed to fetch details for " + coinId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Validate and fix crypto price data
     * @param coins list of coins to validate
     * @return validated list of coins
     */
    private List<Map<String, Object>> validateAndFixPrices(List<Map<String, Object>> coins) {
        List<Map<String, Object>> validatedCoins = new ArrayList<>();
        
        for (Map<String, Object> coin : coins) {
            Map<String, Object> validatedCoin = new HashMap<>(coin);
            
            // Only validate for truly erroneous data, not legitimate high prices
            Object priceObj = coin.get("current_price");
            if (priceObj instanceof Number) {
                double price = ((Number) priceObj).doubleValue();
                
                // Only flag negative prices or extreme outliers (likely data errors)
                if (price < 0 || price > 1000000) { // Allow prices up to $1M (realistic for some tokens)
                    logger.warn("Invalid price detected: {} for coin {}. Skipping validation fix.", price, coin.get("name"));
                    // Don't attempt to "fix" - just log and keep original
                }
            }
            
            validatedCoins.add(validatedCoin);
        }
        
        return validatedCoins;
    }

    /**
     * Check if the cache is still valid
     * @param currentTime current timestamp
     * @return true if cache is valid, false otherwise
     */
    private boolean isCacheValid(long currentTime) {
        return cachedTopCoins != null && 
               !cachedTopCoins.isEmpty() && 
               (currentTime - lastCacheTime) <= CACHE_DURATION_MS;
    }

    /**
     * Build the URL for fetching top coins
     * @return complete URL with query parameters
     */
    private String buildTopCoinsUrl() {
        return String.format("%s?vs_currency=%s&order=%s&per_page=%d&page=1&sparkline=%s&locale=en",
                TOP_COINS_URL, CURRENCY_USD, ORDER_MARKET_CAP, TOP_COINS_LIMIT, SPARKLINE_FALSE);
    }

    /**
     * Build the URL for fetching simple price data
     * @param coinId the cryptocurrency identifier
     * @return complete URL with query parameters
     */
    private String buildSimplePriceUrl(String coinId) {
        return String.format("%s?ids=%s&vs_currencies=%s", SIMPLE_PRICE_URL, coinId, CURRENCY_USD);
    }
}
