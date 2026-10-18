package com.portfolio.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.analytics.PortfolioDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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

    private static final String SEARCH_URL = "https://api.coingecko.com/api/v3/search";
    private static final String MARKETS_URL = "https://api.coingecko.com/api/v3/coins/markets";

    private final RestTemplate restTemplate;
    private final CryptoPriceService cryptoPriceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> resolvedIds = new ConcurrentHashMap<>();

    public CoinMarketDataService(RestTemplate restTemplate, CryptoPriceService cryptoPriceService) {
        this.restTemplate = restTemplate;
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
        Map<String, Double> caps = new HashMap<>();
        // The top-100 list is already cached (with market caps); only fetch coins outside it
        try {
            for (Map<String, Object> coin : cryptoPriceService.getTopCoins()) {
                String id = String.valueOf(coin.get("id"));
                if (coinIds.contains(id) && coin.get("market_cap") instanceof Number cap && cap.doubleValue() > 0) {
                    caps.put(id, cap.doubleValue());
                }
            }
        } catch (Exception e) {
            logger.debug("Top coins unavailable for market caps: {}", e.getMessage());
        }
        List<String> toFetch = coinIds.stream().filter(id -> !caps.containsKey(id)).toList();
        if (toFetch.isEmpty()) return caps;

        String url = UriComponentsBuilder.fromHttpUrl(MARKETS_URL)
                .queryParam("vs_currency", "usd")
                .queryParam("ids", String.join(",", toFetch))
                .queryParam("per_page", toFetch.size())
                .toUriString();
        try {
            JsonNode markets = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            for (JsonNode coin : markets) {
                JsonNode cap = coin.get("market_cap");
                if (cap != null && cap.isNumber() && cap.asDouble() > 0) {
                    caps.put(coin.get("id").asText(), cap.asDouble());
                }
            }
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
        String url = UriComponentsBuilder.fromHttpUrl(SEARCH_URL).queryParam("query", key).toUriString();
        try {
            JsonNode coins = objectMapper.readTree(restTemplate.getForObject(url, String.class)).path("coins");
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
