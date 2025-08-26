package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Unified service for fetching and caching cryptocurrency price history data
 * Provides historical price data for portfolio analysis, charts, and VaR calculations
 * Consolidates functionality from both PriceHistoryService and HistoricalPriceService
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

    // CoinGecko ID mapping for assets not directly supported
    private static final Map<String, String> COIN_GECKO_MAPPING = Map.of(
        "BUIDL", "usd-coin", // Map BUIDL to USDC for price data
        "USDC", "usd-coin",
        "USDT", "tether",
        "BTC", "bitcoin",
        "ETH", "ethereum",
        "SOL", "solana",
        "ADA", "cardano",
        "DOT", "polkadot",
        "LINK", "chainlink",
        "MATIC", "matic-network"
    );

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
     * Get historical prices for VaR calculations (returns just prices, not timestamps)
     * @param assetName the asset name
     * @param days number of days of history
     * @return list of historical prices
     */
    @Cacheable(value = "historicalPrices", key = "#assetName")
    public List<Double> getHistoricalPrices(String assetName, int days) {
        try {
            List<double[]> priceData = getPriceHistory(assetName, days);
            return priceData.stream()
                .map(data -> data[1]) // Extract just the price (index 1)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to get historical prices for {}, using fallback: {}", assetName, e.getMessage());
            return generateFallbackPrices(assetName, days);
        }
    }

    /**
     * Calculate historical returns for VaR calculations
     * @param assetName the asset name
     * @param days number of days of history
     * @return list of historical returns
     */
    public List<Double> calculateHistoricalReturns(String assetName, int days) {
        List<Double> prices = getHistoricalPrices(assetName, days);
        if (prices.size() < 2) {
            return new ArrayList<>();
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            double previousPrice = prices.get(i - 1);
            double currentPrice = prices.get(i);
            if (previousPrice > 0) {
                double returnValue = (currentPrice - previousPrice) / previousPrice;
                returns.add(returnValue);
            }
        }
        return returns;
    }

    /**
     * Calculate real volatility for VaR calculations
     * @param assetName the asset name
     * @param days number of days of history
     * @return calculated volatility
     */
    public double calculateRealVolatility(String assetName, int days) {
        List<Double> returns = calculateHistoricalReturns(assetName, days);
        if (returns.isEmpty()) {
            return 0.0;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance * 252); // Annualized volatility
    }

    /**
     * Get portfolio historical returns for multiple assets (parallel processing)
     * @param assetNames list of asset names
     * @param days number of days of history
     * @return combined historical returns
     */
    public List<Double> getPortfolioHistoricalReturns(List<String> assetNames, int days) {
        if (assetNames.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Fetch returns for all assets in parallel
            List<CompletableFuture<List<Double>>> futures = assetNames.stream()
                .map(assetName -> CompletableFuture.supplyAsync(() -> 
                    calculateHistoricalReturns(assetName, days)))
                .collect(Collectors.toList());

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Combine all returns
            List<List<Double>> allReturns = futures.stream()
                .map(CompletableFuture::join)
                .filter(list -> !list.isEmpty())
                .collect(Collectors.toList());

            return combineReturns(allReturns);
        } catch (Exception e) {
            logger.error("Error calculating portfolio returns: {}", e.getMessage());
            return new ArrayList<>();
        }
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
     * Generate fallback prices for assets not found on CoinGecko
     * @param assetName the asset name
     * @param days number of days
     * @return list of fallback prices
     */
    private List<Double> generateFallbackPrices(String assetName, int days) {
        double basePrice = getBasePrice(assetName);
        List<Double> prices = new ArrayList<>();
        
        // Generate synthetic prices with some volatility
        Random random = new Random(assetName.hashCode()); // Deterministic for consistency
        double currentPrice = basePrice;
        
        for (int i = 0; i < days; i++) {
            // Add some random variation (±5% daily)
            double variation = 1.0 + (random.nextDouble() - 0.5) * 0.1;
            currentPrice *= variation;
            prices.add(currentPrice);
        }
        
        logger.info("Generated {} fallback prices for {}", prices.size(), assetName);
        return prices;
    }

    /**
     * Get base price for fallback generation
     * @param assetName the asset name
     * @return base price
     */
    private double getBasePrice(String assetName) {
        if (assetName == null || assetName.isEmpty()) {
            return 100.0;
        }
        String prefix = assetName.toLowerCase() + CACHE_KEY_SEPARATOR;
        // Prefer the most recent cached series if present
        double lastObserved = Double.NaN;
        int bestDays = -1;
        for (Map.Entry<String, List<double[]>> entry : priceHistoryCache.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                // Parse days from key suffix when possible
                int days = -1;
                try {
                    String[] parts = key.split(CACHE_KEY_SEPARATOR);
                    days = Integer.parseInt(parts[parts.length - 1]);
                } catch (Exception ignored) {}
                List<double[]> series = entry.getValue();
                if (series != null && !series.isEmpty()) {
                    double[] last = series.get(series.size() - 1);
                    double price = last.length > 1 ? last[1] : Double.NaN;
                    if (!Double.isNaN(price) && days > bestDays) {
                        bestDays = days;
                        lastObserved = price;
                    }
                }
            }
        }
        if (!Double.isNaN(lastObserved)) {
            return lastObserved;
        }
        // Neutral default when nothing is cached
        return 100.0;
    }

    /**
     * Combine returns from multiple assets
     * @param allReturns list of return lists from different assets
     * @return combined returns
     */
    private List<Double> combineReturns(List<List<Double>> allReturns) {
        if (allReturns.isEmpty()) {
            return new ArrayList<>();
        }

        // Find the minimum length to avoid index out of bounds
        int minLength = allReturns.stream()
            .mapToInt(List::size)
            .min()
            .orElse(0);

        if (minLength == 0) {
            return new ArrayList<>();
        }

        List<Double> combinedReturns = new ArrayList<>();
        for (int i = 0; i < minLength; i++) {
            double combinedReturn = 0.0;
            int validAssets = 0;
            
            for (List<Double> assetReturns : allReturns) {
                if (i < assetReturns.size()) {
                    combinedReturn += assetReturns.get(i);
                    validAssets++;
                }
            }
            
            if (validAssets > 0) {
                combinedReturns.add(combinedReturn / validAssets);
            }
        }
        
        return combinedReturns;
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
     * Scheduled cache eviction for Spring cache
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @CacheEvict(value = "historicalPrices", allEntries = true)
    public void clearSpringCache() {
        logger.info("Cleared Spring cache for historical prices");
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

