package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.analytics.PortfolioDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps the names users give their assets (e.g. "Bitcoin", "BTC") to CoinGecko IDs, and fetches
 * market capitalisations for the Black-Litterman market portfolio.
 */
@Service
public class CoinMarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(CoinMarketDataService.class);

    private final CoinGeckoClient coinGecko;
    private final CryptoPriceService cryptoPriceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> resolvedIds = new ConcurrentHashMap<>();

    public CoinMarketDataService(CoinGeckoClient coinGecko, CryptoPriceService cryptoPriceService) {
        this.coinGecko = coinGecko;
        this.cryptoPriceService = cryptoPriceService;
    }

    /**
     * Resolve an asset name, symbol or ID to its CoinGecko ID.
     * @throws PortfolioDataException if no coin matches exactly
     */
    public String resolveCoinId(String assetName) {
        String key = assetName.trim().toLowerCase(Locale.ROOT);
        String cached = resolvedIds.get(key);
        if (cached != null) return cached;

        String id = findInTopCoins(key).orElseGet(() -> searchCoinGecko(key)
                .orElseThrow(() -> new PortfolioDataException(PortfolioDataException.Reason.UNKNOWN_ASSET,
                        "Could not find \"" + assetName + "\" on CoinGecko. Rename the asset to the coin's "
                                + "name or symbol (for example \"Bitcoin\" or \"BTC\").")));
        resolvedIds.put(key, id);
        return id;
    }

    /** Current market capitalisation in USD for each CoinGecko ID. */
    public Map<String, Double> getMarketCaps(Collection<String> coinIds) {
        try {
            Map<String, Double> caps = getMarketField(coinIds, "market_cap");
            List<String> missing = coinIds.stream().filter(id -> !caps.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                throw new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                        "CoinGecko has no market capitalisation for " + String.join(", ", missing)
                                + ", which Black-Litterman needs for the market portfolio.");
            }
            return caps;
        } catch (PortfolioDataException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to fetch market caps for {}: {}", coinIds, e.getMessage());
            throw new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                    "Market data is temporarily unavailable from CoinGecko. Please try again shortly.");
        }
    }

    /**
     * Current 24h trading volume in USD for each CoinGecko ID. Coins CoinGecko can't provide right
     * now are left out instead of failing the request.
     */
    public Map<String, Double> getTotalVolumes(Collection<String> coinIds) {
        try {
            return getMarketField(coinIds, "total_volume");
        } catch (Exception e) {
            logger.warn("Trading volumes unavailable for {}: {}", coinIds, e.getMessage());
            return Map.of();
        }
    }

    /** 24h volume and market capitalisation of each coin, keyed by field name. */
    public record MarketData(Map<String, Double> volumes, Map<String, Double> marketCaps) {}

    /**
     * Current 24h volume and market capitalisation in one lookup. A coin CoinGecko lists without a market
     * capitalisation has a volume but no cap; coins it can't provide right now are left out of both.
     */
    public MarketData getVolumesAndMarketCaps(Collection<String> coinIds) {
        try {
            Map<String, Map<String, Double>> fields = getMarketFields(coinIds, List.of("total_volume", "market_cap"));
            return new MarketData(fields.get("total_volume"), fields.get("market_cap"));
        } catch (Exception e) {
            logger.warn("Market data unavailable for {}: {}", coinIds, e.getMessage());
            return new MarketData(Map.of(), Map.of());
        }
    }

    /** A positive numeric /coins/markets field per coin, from the cached top-100 list where possible. */
    private Map<String, Double> getMarketField(Collection<String> coinIds, String field) throws Exception {
        return getMarketFields(coinIds, List.of(field)).get(field);
    }

    /**
     * Positive numeric /coins/markets fields per coin. A coin counts as found once CoinGecko returns it,
     * so a coin without one of the fields isn't fetched again.
     */
    private Map<String, Map<String, Double>> getMarketFields(Collection<String> coinIds, List<String> fields) throws Exception {
        Map<String, Map<String, Double>> byField = new HashMap<>();
        for (String field : fields) byField.put(field, new HashMap<>());
        Set<String> found = new HashSet<>();
        // The top-100 list is already cached (with market data); only fetch coins outside it
        try {
            for (Map<String, Object> coin : cryptoPriceService.getTopCoins()) {
                String id = String.valueOf(coin.get("id"));
                if (!coinIds.contains(id)) continue;
                found.add(id);
                for (String field : fields) {
                    if (coin.get(field) instanceof Number value && value.doubleValue() > 0) {
                        byField.get(field).put(id, value.doubleValue());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Top coins unavailable for {}: {}", fields, e.getMessage());
        }
        List<String> toFetch = coinIds.stream().filter(id -> !found.contains(id)).toList();
        if (toFetch.isEmpty()) return byField;

        String path = UriComponentsBuilder.fromPath("/coins/markets")
                .queryParam("vs_currency", "usd")
                .queryParam("ids", String.join(",", toFetch))
                .queryParam("per_page", toFetch.size())
                .encode()
                .toUriString();
        JsonNode markets = objectMapper.readTree(coinGecko.get(path));
        for (JsonNode coin : markets) {
            for (String field : fields) {
                JsonNode value = coin.get(field);
                if (value != null && value.isNumber() && value.asDouble() > 0) {
                    byField.get(field).put(coin.get("id").asText(), value.asDouble());
                }
            }
        }
        return byField;
    }

    private Optional<String> findInTopCoins(String key) {
        try {
            for (Map<String, Object> coin : cryptoPriceService.getTopCoins()) {
                if (matches(key, String.valueOf(coin.get("id")), String.valueOf(coin.get("name")),
                        String.valueOf(coin.get("symbol")))) {
                    return Optional.of(String.valueOf(coin.get("id")));
                }
            }
        } catch (Exception e) {
            logger.debug("Top coins unavailable while resolving {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> searchCoinGecko(String key) {
        String path = UriComponentsBuilder.fromPath("/search").queryParam("query", key).encode().toUriString();
        try {
            JsonNode coins = objectMapper.readTree(coinGecko.get(path)).path("coins");
            JsonNode best = null;
            for (JsonNode coin : coins) {
                if (!matches(key, coin.path("id").asText(), coin.path("name").asText(), coin.path("symbol").asText())) {
                    continue;
                }
                // Several coins can share a symbol; prefer the one with the highest market cap
                if (best == null || rank(coin) < rank(best)) best = coin;
            }
            return best == null ? Optional.empty() : Optional.of(best.path("id").asText());
        } catch (Exception e) {
            logger.error("CoinGecko search failed for {}: {}", key, e.getMessage());
            throw new PortfolioDataException(PortfolioDataException.Reason.MARKET_DATA_UNAVAILABLE,
                    "Market data is temporarily unavailable from CoinGecko. Please try again shortly.");
        }
    }

    private static boolean matches(String key, String id, String name, String symbol) {
        return key.equalsIgnoreCase(id) || key.equalsIgnoreCase(name) || key.equalsIgnoreCase(symbol);
    }

    private static int rank(JsonNode coin) {
        JsonNode r = coin.get("market_cap_rank");
        return r != null && r.isInt() ? r.asInt() : Integer.MAX_VALUE;
    }
}
