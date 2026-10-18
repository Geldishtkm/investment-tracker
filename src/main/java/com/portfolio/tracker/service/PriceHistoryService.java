package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.model.PriceHistoryCacheEntry;
import com.portfolio.tracker.repository.PriceHistoryCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Daily cryptocurrency price history from CoinGecko for charts and the risk calculations.
 *
 * Each coin is fetched once as a full year of daily prices and every shorter window (charts,
 * VaR lookback) is cut from that, so one CoinGecko call serves all callers. Results are cached in
 * memory and in the database (surviving restarts) and reused until the TTL expires. Concurrent
 * requests for the same coin share a single in-flight call. If CoinGecko rate limits us (HTTP 429)
 * or is unreachable, the last saved history is served instead of an error, however old it is.
 * Only real market data is returned; there are no synthetic fallbacks.
 */
@Service
public class PriceHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(PriceHistoryService.class);

    private static final String VS_CURRENCY = "usd";
    // CoinGecko's public API serves at most one year of history
    private static final int MAX_DAYS = 365;
    private static final long DAY_MS = Duration.ofDays(1).toMillis();

    private final CoinGeckoClient coinGecko;
    private final PriceHistoryCacheRepository cacheRepository;
    private final AnalyticsProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, CachedHistory> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<CachedHistory>> inFlight = new ConcurrentHashMap<>();
    // IDs CoinGecko said don't exist, so repeated lookups of a bad ID don't each cost an API call
    private final Map<String, Instant> unknownCoins = new ConcurrentHashMap<>();
    // Coins currently served from an expired saved copy; refetched in the background once CoinGecko allows
    private final Set<String> pendingRefresh = ConcurrentHashMap.newKeySet();

    public PriceHistoryService(CoinGeckoClient coinGecko,
                               PriceHistoryCacheRepository cacheRepository,
                               AnalyticsProperties properties) {
        this.coinGecko = coinGecko;
        this.cacheRepository = cacheRepository;
        this.properties = properties;
    }

    /** A year of [timestamp ms, price] points and when they were fetched. */
    private record CachedHistory(List<double[]> points, Instant fetchedAt) {
    }

    /**
     * Price history for the last {@code days} days, served from cache when possible.
     * @return list of [timestamp ms, price] points, oldest first
     * @throws PortfolioDataException if there is no data and CoinGecko can't provide it right now
     */
    public List<double[]> getPriceHistory(String coinId, int days) {
        if (coinId == null || coinId.trim().isEmpty()) {
            throw new IllegalArgumentException("Coin ID cannot be null or empty");
        }
        if (days <= 0 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Days must be between 1 and " + MAX_DAYS);
        }
        return lastDays(load(coinId.trim().toLowerCase(Locale.ROOT), false).points(), days);
    }

    /**
     * Daily prices keyed by UTC date, using the first quote of each day (CoinGecko's 00:00 UTC
     * snapshot) so consecutive points are exactly one day apart.
     * @throws PortfolioDataException if CoinGecko has no history for the coin
     */
    public SortedMap<LocalDate, Double> getDailyPrices(String coinId, int days) {
        List<double[]> points = getPriceHistory(coinId, Math.min(days, MAX_DAYS));
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
     * Fetch fresh history now, bypassing the TTL (still subject to CoinGecko's rate limit).
     * @return true if fresh data was fetched, false if saved data was kept because CoinGecko was unavailable
     */
    public boolean refreshPriceHistory(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            logger.warn("Cannot refresh price history: coin ID is null or empty");
            return false;
        }
        String id = coinId.trim().toLowerCase(Locale.ROOT);
        Instant before = Optional.ofNullable(memoryCache.get(id)).map(CachedHistory::fetchedAt).orElse(Instant.EPOCH);
        return load(id, true).fetchedAt().isAfter(before);
    }

    /** Cache details for one coin. */
    public Map<String, Object> getCacheStatus(String coinId) {
        if (coinId == null || coinId.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        String id = coinId.trim().toLowerCase(Locale.ROOT);
        CachedHistory cached = memoryCache.get(id);
        if (cached == null) cached = readFromDatabase(id);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("coinId", id);
        status.put("cached", cached != null);
        if (cached != null) {
            status.put("dataPoints", cached.points().size());
            status.put("fetchedAt", cached.fetchedAt().toString());
            status.put("ageMinutes", Duration.between(cached.fetchedAt(), Instant.now()).toMinutes());
            status.put("fresh", !isExpired(cached));
        }
        return status;
    }

    /** Overall cache and rate limit status. */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> coins = new TreeMap<>();
        memoryCache.forEach((id, cached) -> coins.put(id, Map.of(
                "dataPoints", cached.points().size(),
                "ageMinutes", Duration.between(cached.fetchedAt(), Instant.now()).toMinutes(),
                "fresh", !isExpired(cached))));
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("cacheTtlMinutes", properties.getPriceCacheTtlMinutes());
        status.put("rateLimitedForSeconds", coinGecko.secondsUntilAvailable());
        status.put("apiKeyConfigured", coinGecko.hasApiKey());
        status.put("persistedCoins", cacheRepository.count());
        status.put("coins", coins);
        return status;
    }

    /**
     * Refetch, one coin at a time, histories that had to be served from an old saved copy (typically
     * when a user returns after a break and all their coins expire at once, hitting the rate limit).
     * Stops at the first failure and resumes on the next run, after the rate limit has cleared.
     */
    @Scheduled(fixedDelayString = "${portfolio.analytics.stale-history-retry-ms:30000}", initialDelay = 30000)
    public void refreshStaleHistories() {
        for (String id : List.copyOf(pendingRefresh)) {
            if (coinGecko.secondsUntilAvailable() > 0) return;
            load(id, true);
            if (pendingRefresh.contains(id)) return; // still failing: try again next run
            logger.info("Refreshed stale price history for {} in the background", id);
        }
    }

    /** Coins waiting for a background refresh of an outdated saved history. */
    public Set<String> getPendingRefresh() {
        return Set.copyOf(pendingRefresh);
    }

    /** Clear the in-memory cache (the database copy is kept as the stale-data fallback). */
    public void clearCache() {
        int size = memoryCache.size();
        memoryCache.clear();
        logger.info("Cleared in-memory price history cache with {} entries", size);
    }

    private CachedHistory load(String id, boolean forceRefresh) {
        Instant unknownSince = unknownCoins.get(id);
        if (unknownSince != null) {
            if (unknownSince.plus(Duration.ofMinutes(properties.getPriceCacheTtlMinutes())).isAfter(Instant.now())) {
                throw toDataException(id, HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));
            }
            unknownCoins.remove(id);
        }
        CachedHistory cached = memoryCache.get(id);
        if (cached == null) {
            cached = readFromDatabase(id);
            if (cached != null) memoryCache.put(id, cached);
        }
        if (!forceRefresh && cached != null && !isExpired(cached)) {
            return cached;
        }

        // Only one CoinGecko call per coin at a time; concurrent callers wait for it
        CompletableFuture<CachedHistory> mine = new CompletableFuture<>();
        CompletableFuture<CachedHistory> running = inFlight.putIfAbsent(id, mine);
        if (running != null) {
            return awaitOrStale(running, cached);
        }
        try {
            CachedHistory fresh = fetchFromCoinGecko(id);
            memoryCache.put(id, fresh);
            writeToDatabase(id, fresh);
            pendingRefresh.remove(id);
            mine.complete(fresh);
            return fresh;
        } catch (RuntimeException e) {
            PortfolioDataException failure = toDataException(id, e);
            if (failure.getReason() == PortfolioDataException.Reason.UNKNOWN_ASSET) {
                unknownCoins.put(id, Instant.now());
            }
            mine.completeExceptionally(failure);
            if (cached != null && failure.getReason() != PortfolioDataException.Reason.UNKNOWN_ASSET) {
                logger.warn("Serving saved price history for {} from {} ({})", id, cached.fetchedAt(), e.getMessage());
                pendingRefresh.add(id);
                return cached;
            }
            throw failure;
        } finally {
            inFlight.remove(id, mine);
        }
    }

    private CachedHistory awaitOrStale(CompletableFuture<CachedHistory> running, CachedHistory stale) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (stale != null) return stale;
            if (e.getCause() instanceof PortfolioDataException pde) throw pde;
            throw e;
        }
    }

    private CachedHistory fetchFromCoinGecko(String id) {
        String path = "/coins/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8) + "/market_chart?vs_currency=" + VS_CURRENCY + "&days=" + MAX_DAYS + "&interval=daily";
        logger.debug("Fetching price history for {}", id);
        String body = coinGecko.get(path);
        List<double[]> points = parsePrices(body);
        if (points.isEmpty()) {
            throw new IllegalStateException("CoinGecko returned no prices");
        }
        logger.info("Fetched {} daily prices for {} from CoinGecko", points.size(), id);
        return new CachedHistory(points, Instant.now());
    }

    private PortfolioDataException toDataException(String id, RuntimeException e) {
        if (e instanceof HttpClientErrorException.NotFound) {
            return new PortfolioDataException(PortfolioDataException.Reason.UNKNOWN_ASSET,
                    "CoinGecko has no coin with ID \"" + id + "\".");
        }
        if (e instanceof CoinGeckoClient.RateLimitedException rl) {
            return new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                    "CoinGecko is rate limiting requests and there is no saved price history for " + id
                            + " yet. Please try again in " + rl.getRetryAfterSeconds() + " seconds.");
        }
        logger.error("Error fetching price history for {}: {}", id, e.getMessage());
        return new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                "Price history for " + id + " is temporarily unavailable. Please try again shortly.");
    }

    private List<double[]> parsePrices(String json) {
        try {
            JsonNode prices = objectMapper.readTree(json).path("prices");
            List<double[]> points = new ArrayList<>();
            for (JsonNode entry : prices) {
                if (entry.isArray() && entry.size() >= 2 && entry.get(0).isNumber() && entry.get(1).isNumber()) {
                    points.add(new double[]{entry.get(0).asDouble(), entry.get(1).asDouble()});
                }
            }
            return points;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid price history response: " + e.getMessage(), e);
        }
    }

    /** Points within the last {@code days} days of the series (by its own latest timestamp). */
    private static List<double[]> lastDays(List<double[]> points, int days) {
        if (points.isEmpty()) return new ArrayList<>();
        double cutoff = points.get(points.size() - 1)[0] - days * (double) DAY_MS;
        List<double[]> out = new ArrayList<>();
        for (double[] p : points) {
            if (p[0] >= cutoff) out.add(p.clone());
        }
        return out;
    }

    private boolean isExpired(CachedHistory cached) {
        return cached.fetchedAt().plus(Duration.ofMinutes(properties.getPriceCacheTtlMinutes())).isBefore(Instant.now());
    }

    private CachedHistory readFromDatabase(String id) {
        try {
            return cacheRepository.findById(new PriceHistoryCacheEntry.Key(id, VS_CURRENCY))
                    .map(entry -> new CachedHistory(parseStoredPoints(entry.getPointsJson()),
                            entry.getFetchedAt().toInstant(ZoneOffset.UTC)))
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not read saved price history for {}: {}", id, e.getMessage());
            return null;
        }
    }

    private void writeToDatabase(String id, CachedHistory history) {
        try {
            cacheRepository.save(new PriceHistoryCacheEntry(id, VS_CURRENCY,
                    objectMapper.writeValueAsString(history.points()),
                    LocalDateTime.ofInstant(history.fetchedAt(), ZoneOffset.UTC)));
        } catch (Exception e) {
            // The in-memory copy still works; persistence is only the restart/stale fallback
            logger.warn("Could not save price history for {}: {}", id, e.getMessage());
        }
    }

    private List<double[]> parseStoredPoints(String json) {
        try {
            double[][] stored = objectMapper.readValue(json, double[][].class);
            return new ArrayList<>(Arrays.asList(stored));
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt saved price history: " + e.getMessage(), e);
        }
    }
}
