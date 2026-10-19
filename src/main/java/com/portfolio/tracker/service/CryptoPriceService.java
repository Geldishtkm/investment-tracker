package com.portfolio.tracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Current cryptocurrency market data from CoinGecko: the top coins list (refreshed on a schedule)
 * and spot prices. Spot prices for several coins are fetched in one batched /simple/price call,
 * reused for a short TTL, and taken from the top coins list when it already has them. When
 * CoinGecko rate limits us, previously fetched values are served.
 */
@Service
public class CryptoPriceService {

    private static final Logger logger = LoggerFactory.getLogger(CryptoPriceService.class);

    // Cache configuration
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000L; // 10 minutes
    private static final int TOP_COINS_LIMIT = 100;
    private static final String CURRENCY_USD = "usd";
    // CoinGecko accepts many IDs per /simple/price call; stay well under URL length limits
    private static final int MAX_IDS_PER_BATCH = 200;

    private final CoinGeckoClient coinGecko;
    private final Duration spotPriceTtl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache for top coins with timestamp tracking
    private volatile List<Map<String, Object>> cachedTopCoins;
    private volatile long lastCacheTime = 0;

    private record SpotPrice(double usd, Instant fetchedAt) {
    }

    private final Map<String, SpotPrice> spotPrices = new ConcurrentHashMap<>();

    public CryptoPriceService(CoinGeckoClient coinGecko,
                              @Value("${coingecko.spot-price-ttl-seconds}") long spotPriceTtlSeconds) {
        this.coinGecko = coinGecko;
        this.spotPriceTtl = Duration.ofSeconds(spotPriceTtlSeconds);
        logger.info("CryptoPriceService initialized with cache duration: {} ms", CACHE_DURATION_MS);
    }

    /**
     * Scheduled task to refresh the top coins cache every 10 minutes. Each coin includes its 7-day
     * hourly sparkline and 24h / 7d price changes for the market grid. On failure (including a
     * rate limit) the previous list is kept.
     */
    @Scheduled(fixedRate = CACHE_DURATION_MS)
    public synchronized void refreshCache() {
        try {
            String body = coinGecko.get(String.format(
                    "/coins/markets?vs_currency=%s&order=market_cap_desc&per_page=%d&page=1&sparkline=true"
                            + "&price_change_percentage=24h,7d&locale=en",
                    CURRENCY_USD, TOP_COINS_LIMIT));
            List<Map<String, Object>> coins = objectMapper.readValue(body, new TypeReference<>() {});
            if (coins == null || coins.isEmpty()) {
                logger.warn("Received empty response when refreshing top coins");
                return;
            }
            cachedTopCoins = coins;
            lastCacheTime = System.currentTimeMillis();
            logger.info("Successfully refreshed cache with {} coins", coins.size());
        } catch (CoinGeckoClient.RateLimitedException e) {
            logger.warn("Top coins not refreshed: {}; keeping the cached list", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to refresh top coins cache: {}", e.getMessage());
        }
    }

    /**
     * Get the top 100 cryptocurrencies by market cap
     * @return list of top coins with their market data (possibly stale if CoinGecko is unavailable)
     * @throws RuntimeException if CoinGecko is unavailable and nothing has been cached yet
     */
    public List<Map<String, Object>> getTopCoins() {
        if (isCacheValid(System.currentTimeMillis())) {
            return cachedTopCoins;
        }
        synchronized (this) {
            // Another request may have refreshed it while we waited
            if (!isCacheValid(System.currentTimeMillis())) refreshCache();
        }
        if (cachedTopCoins == null || cachedTopCoins.isEmpty()) {
            throw new RuntimeException("Cryptocurrency data is temporarily unavailable. Please try again shortly.");
        }
        return cachedTopCoins;
    }

    /** A USD price and when CoinGecko quoted it. An old asOf means CoinGecko couldn't be reached. */
    public record Quote(double usd, Instant asOf) {}

    /**
     * Current USD prices for several coins using at most one CoinGecko call per batch of IDs.
     * Prices are taken from the cached top coins list or the spot price cache when fresh enough.
     * @return price per CoinGecko ID; IDs CoinGecko doesn't know (or can't price right now and
     *         were never priced before) are absent
     */
    public Map<String, Double> getPricesInUSD(Collection<String> coinIds) {
        Map<String, Double> prices = new LinkedHashMap<>();
        getQuotes(coinIds).forEach((id, quote) -> prices.put(id, quote.usd()));
        return prices;
    }

    /**
     * Like {@link #getPricesInUSD} but with the time of each quote, so callers can tell a live price
     * from the last known one served while CoinGecko is unavailable.
     */
    public Map<String, Quote> getQuotes(Collection<String> coinIds) {
        Instant now = Instant.now();
        Map<String, Quote> result = new LinkedHashMap<>();
        Set<String> wanted = coinIds.stream()
                .filter(Objects::nonNull)
                .map(id -> id.trim().toLowerCase(Locale.ROOT))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> toFetch = new ArrayList<>();
        Map<String, Double> topCoinPrices = freshTopCoinPrices();
        Instant topCoinsAsOf = Instant.ofEpochMilli(lastCacheTime);
        for (String id : wanted) {
            SpotPrice spot = spotPrices.get(id);
            if (spot != null && spot.fetchedAt().plus(spotPriceTtl).isAfter(now)) {
                result.put(id, new Quote(spot.usd(), spot.fetchedAt()));
            } else if (topCoinPrices.containsKey(id)) {
                result.put(id, new Quote(topCoinPrices.get(id), topCoinsAsOf));
            } else {
                toFetch.add(id);
            }
        }

        for (int start = 0; start < toFetch.size(); start += MAX_IDS_PER_BATCH) {
            List<String> batch = toFetch.subList(start, Math.min(start + MAX_IDS_PER_BATCH, toFetch.size()));
            try {
                Map<String, Double> fetched = fetchSimplePrices(batch);
                fetched.forEach((id, price) -> {
                    spotPrices.put(id, new SpotPrice(price, now));
                    result.put(id, new Quote(price, now));
                });
            } catch (Exception e) {
                logger.warn("Batch price fetch for {} coin(s) failed ({}); using last known prices", batch.size(), e.getMessage());
                for (String id : batch) {
                    SpotPrice stale = spotPrices.get(id);
                    if (stale != null) result.put(id, new Quote(stale.usd(), stale.fetchedAt()));
                }
            }
        }
        return result;
    }

    /**
     * Get current USD price for a specific cryptocurrency
     * @param coinId the cryptocurrency identifier (e.g., bitcoin, ethereum)
     * @return current price in USD
     * @throws RuntimeException if the price is unavailable
     */
    public double getCryptoPriceInUSD(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }
        Double price = getPricesInUSD(List.of(coinId)).get(coinId.trim().toLowerCase(Locale.ROOT));
        if (price == null) {
            throw new RuntimeException("Price for " + coinId + " is unavailable");
        }
        return price;
    }

    /**
     * Get detailed information about a specific cryptocurrency
     * @param coinId the cryptocurrency identifier
     * @return map containing coin details (id, symbol, name, image)
     * @throws RuntimeException if coin details cannot be fetched
     */
    public Map<String, Object> getCoinDetails(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }
        String id = coinId.trim().toLowerCase(Locale.ROOT);

        // The cached top coins list already has these fields for the most common coins
        if (cachedTopCoins != null) {
            for (Map<String, Object> coin : cachedTopCoins) {
                if (id.equals(coin.get("id"))) {
                    return Map.of("id", coin.get("id"), "symbol", coin.get("symbol"),
                            "name", coin.get("name"), "image", Objects.toString(coin.get("image"), ""));
                }
            }
        }

        try {
            JsonNode response = objectMapper.readTree(coinGecko.get("/coins/" + encode(id)
                    + "?localization=false&tickers=false&market_data=false&community_data=false&developer_data=false"));
            return Map.of(
                    "id", response.path("id").asText(),
                    "symbol", response.path("symbol").asText(),
                    "name", response.path("name").asText(),
                    "image", response.path("image").path("large").asText(""));
        } catch (Exception e) {
            logger.error("Failed to get details for coin {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch details for " + id + ": " + e.getMessage(), e);
        }
    }

    private Map<String, Double> fetchSimplePrices(List<String> ids) throws Exception {
        String joined = ids.stream().map(CryptoPriceService::encode).collect(Collectors.joining(","));
        JsonNode body = objectMapper.readTree(coinGecko.get("/simple/price?ids=" + joined + "&vs_currencies=" + CURRENCY_USD));
        Map<String, Double> prices = new LinkedHashMap<>();
        for (String id : ids) {
            JsonNode usd = body.path(id).path(CURRENCY_USD);
            if (usd.isNumber() && usd.asDouble() > 0) prices.put(id, usd.asDouble());
        }
        logger.debug("Fetched {} of {} spot prices in one batch", prices.size(), ids.size());
        return prices;
    }

    /** Current prices from the top coins list, if that list is still fresh. */
    private Map<String, Double> freshTopCoinPrices() {
        Map<String, Double> prices = new HashMap<>();
        if (!isCacheValid(System.currentTimeMillis())) return prices;
        for (Map<String, Object> coin : cachedTopCoins) {
            if (coin.get("id") instanceof String id && coin.get("current_price") instanceof Number p && p.doubleValue() > 0) {
                prices.put(id, p.doubleValue());
            }
        }
        return prices;
    }

    /**
     * Check if the cache is still valid
     * @param currentTime current timestamp
     * @return true if cache is valid, false otherwise
     */
    private boolean isCacheValid(long currentTime) {
        List<Map<String, Object>> coins = cachedTopCoins;
        return coins != null && !coins.isEmpty() && (currentTime - lastCacheTime) <= CACHE_DURATION_MS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
