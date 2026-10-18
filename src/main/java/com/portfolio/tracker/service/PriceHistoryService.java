package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches daily cryptocurrency price history from CoinGecko for charts and for the
 * risk and optimization calculations. Only real market data is returned: when CoinGecko has no
 * data for a coin the caller gets an error instead of synthetic prices.
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
    // CoinGecko's public API serves at most one year of history
    private static final int MAX_DAYS = 365;

    private final Map<String, CachedHistory> priceHistoryCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalyticsProperties properties;

    public PriceHistoryService(AnalyticsProperties properties) {
        this.properties = properties;
    }

    private record CachedHistory(List<double[]> points, Instant fetchedAt) {
    }

    /**
     * Get price history for a cryptocurrency, using cache if available
     * @param coinId the CoinGecko coin ID
     * @param days number of days of history to retrieve
     * @return list of price data points [timestamp, price]
     */
    public List<double[]> getPriceHistory(String coinId, int days) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }
        if (days <= 0 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Days must be between 1 and " + MAX_DAYS);
        }

        String cacheKey = buildCacheKey(coinId, days);
        CachedHistory cached = priceHistoryCache.get(cacheKey);
        if (cached != null && !isExpired(cached)) {
            logger.debug("Returning {} data points from cache for {} ({} days)",
                       cached.points().size(), coinId, days);
            return new ArrayList<>(cached.points()); // Return copy to prevent modification
        }

        return fetchAndCachePriceHistory(coinId, days);
    }

    /**
     * Daily prices keyed by UTC date, using the first quote of each day (CoinGecko's 00:00 UTC
     * snapshot) so consecutive points are exactly one day apart.
     * @throws PortfolioDataException if CoinGecko has no history for the coin
     */
    public SortedMap<LocalDate, Double> getDailyPrices(String coinId, int days) {
        List<double[]> points;
        try {
            points = getPriceHistory(coinId, Math.min(days, MAX_DAYS));
        } catch (PortfolioDataException e) {
            throw e;
        } catch (Exception e) {
            throw new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                    "Price history for " + coinId + " is temporarily unavailable. Please try again shortly.");
        }
        SortedMap<LocalDate, Double> daily = new TreeMap<>();
        for (double[] point : points) {
            LocalDate date = Instant.ofEpochMilli((long) point[0]).atZone(ZoneOffset.UTC).toLocalDate();
            if (point[1] > 0) daily.putIfAbsent(date, point[1]);
        }
        if (daily.isEmpty()) {
            throw new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                    "CoinGecko returned no price history for " + coinId + ".");
        }
        return daily;
    }

    /**
     * Fetch price history from API and store in cache
     * @param coinId the CoinGecko coin ID
     * @param days number of days of history to retrieve
     * @return list of price data points [timestamp, price]
     * @throws PortfolioDataException if the coin is unknown or CoinGecko cannot be reached
     */
    public List<double[]> fetchAndCachePriceHistory(String coinId, int days) {
        String url = String.format(COINGECKO_API_URL_TEMPLATE, coinId, days);
        logger.debug("Fetching price history for {} over {} days", coinId, days);
        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.trim().isEmpty()) {
                throw new IllegalStateException("Empty response");
            }

            List<double[]> priceHistory = parsePriceHistoryResponse(response);
            manageCacheSize();
            priceHistoryCache.put(buildCacheKey(coinId, days), new CachedHistory(priceHistory, Instant.now()));

            logger.info("Successfully cached {} data points for {} ({} days)",
                       priceHistory.size(), coinId, days);
            return new ArrayList<>(priceHistory);
        } catch (HttpClientErrorException.NotFound e) {
            throw new PortfolioDataException(PortfolioDataException.Reason.UNKNOWN_ASSET,
                    "CoinGecko has no coin with ID \"" + coinId + "\".");
        } catch (Exception e) {
            logger.error("Error fetching price history for {} ({} days): {}", coinId, days, e.getMessage());
            // Serve stale data rather than nothing if CoinGecko is rate limiting us
            CachedHistory stale = priceHistoryCache.get(buildCacheKey(coinId, days));
            if (stale != null) {
                logger.warn("Using cached price history for {} from {}", coinId, stale.fetchedAt());
                return new ArrayList<>(stale.points());
            }
            throw new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                    "Price history for " + coinId + " is temporarily unavailable. Please try again shortly.");
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
     * @param coinId the CoinGecko coin ID
     * @param days number of days of history to retrieve
     */
    public void refreshPriceHistory(String coinId, int days) {
        if (coinId == null || coinId.trim().isEmpty()) {
            logger.warn("Cannot refresh price history: coin ID is null or empty");
            return;
        }

        logger.info("Refreshing price history for {} ({} days)", coinId, days);
        priceHistoryCache.remove(buildCacheKey(coinId, days));
        fetchAndCachePriceHistory(coinId, days);
    }

    /**
     * Get cache status for a specific coin
     * @param coinId the CoinGecko coin ID
     * @return map containing cache keys and data point counts for the coin
     */
    public Map<String, Object> getCacheStatus(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> status = new HashMap<>();
        String prefix = coinId.toLowerCase() + CACHE_KEY_SEPARATOR;
        for (Map.Entry<String, CachedHistory> entry : priceHistoryCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                status.put(entry.getKey(), entry.getValue().points().size());
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
        for (Map.Entry<String, CachedHistory> entry : priceHistoryCache.entrySet()) {
            status.put(entry.getKey(), entry.getValue().points().size());
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

    private boolean isExpired(CachedHistory cached) {
        return cached.fetchedAt().plusSeconds(properties.getPriceCacheTtlMinutes() * 60).isBefore(Instant.now());
    }

    /**
     * Build cache key for a coin and time period
     * @param coinId the CoinGecko coin ID
     * @param days number of days
     * @return cache key string
     */
    private String buildCacheKey(String coinId, int days) {
        return coinId.toLowerCase() + CACHE_KEY_SEPARATOR + days;
    }

    /**
     * Manage cache size to prevent memory issues
     * Removes the oldest entries if cache exceeds maximum size
     */
    private void manageCacheSize() {
        if (priceHistoryCache.size() < MAX_CACHE_SIZE) return;
        List<Map.Entry<String, CachedHistory>> byAge = new ArrayList<>(priceHistoryCache.entrySet());
        byAge.sort(Comparator.comparing(e -> e.getValue().fetchedAt()));
        int toRemove = priceHistoryCache.size() - MAX_CACHE_SIZE / 2;
        for (int i = 0; i < toRemove; i++) priceHistoryCache.remove(byAge.get(i).getKey());
        logger.info("Cache size limit reached, removed {} old entries", toRemove);
    }
}
