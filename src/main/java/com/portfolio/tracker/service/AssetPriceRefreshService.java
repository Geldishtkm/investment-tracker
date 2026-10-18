package com.portfolio.tracker.service;

import java.time.Duration;
import java.time.Instant;

import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Updates the current price of every asset in a portfolio from CoinGecko using a single batched
 * spot price request, rather than one request per asset.
 */
@Service
public class AssetPriceRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(AssetPriceRefreshService.class);

    private final AssetRepository assetRepository;
    private final CoinMarketDataService coinMarketDataService;
    private final CryptoPriceService cryptoPriceService;

    public AssetPriceRefreshService(AssetRepository assetRepository,
                                    CoinMarketDataService coinMarketDataService,
                                    CryptoPriceService cryptoPriceService) {
        this.assetRepository = assetRepository;
        this.coinMarketDataService = coinMarketDataService;
        this.cryptoPriceService = cryptoPriceService;
    }

    /** A price older than this counts as stale (live quotes are at most a few minutes old). */
    public static final Duration STALE_AFTER = Duration.ofMinutes(15);

    /**
     * What a refresh changed. stale: holdings still valued at an old (or never refreshed) price;
     * oldestPriceAt: the oldest price quote among the holdings, null if one was never quoted.
     */
    public record Result(int updated, List<String> unpriced, List<String> stale, Instant oldestPriceAt) {
    }

    /**
     * Refresh before valuing a portfolio, never failing the caller: if CoinGecko is unavailable
     * the stored prices are used and reported as stale.
     */
    public Result refreshQuietly(User user) {
        try {
            return refreshPrices(user);
        } catch (Exception e) {
            logger.warn("Price refresh for {} failed: {}", user.getUsername(), e.getMessage());
            return summarize(assetRepository.findByUser(user), 0, List.of());
        }
    }

    @Transactional
    public Result refreshPrices(User user) {
        List<Asset> assets = assetRepository.findByUser(user);
        Map<Asset, String> coinIds = new LinkedHashMap<>();
        List<String> unpriced = new ArrayList<>();
        for (Asset asset : assets) {
            try {
                coinIds.put(asset, coinMarketDataService.resolveCoinId(asset.getName()));
            } catch (PortfolioDataException e) {
                unpriced.add(asset.getName()); // not a CoinGecko coin, or lookup unavailable right now
            }
        }

        Map<String, CryptoPriceService.Quote> quotes = cryptoPriceService.getQuotes(new LinkedHashSet<>(coinIds.values()));

        int updated = 0;
        for (Map.Entry<Asset, String> entry : coinIds.entrySet()) {
            Asset asset = entry.getKey();
            CryptoPriceService.Quote quote = quotes.get(entry.getValue());
            if (quote == null) {
                unpriced.add(asset.getName());
                continue;
            }
            // Never replace a price with an older quote (e.g. an in-memory fallback older than the stored one)
            boolean newer = asset.getPriceUpdatedAt() == null || quote.asOf().isAfter(asset.getPriceUpdatedAt());
            if (newer) {
                if (quote.usd() != asset.getPricePerUnit()) updated++;
                asset.setPricePerUnit(quote.usd());
                asset.setPriceUpdatedAt(quote.asOf());
                assetRepository.save(asset);
            }
        }
        if (updated > 0) {
            logger.info("Updated prices of {} asset(s) for {}", updated, user.getUsername());
        }
        return summarize(assets, updated, unpriced);
    }

    private static Result summarize(List<Asset> assets, int updated, List<String> unpriced) {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        List<String> stale = new ArrayList<>();
        Instant oldest = null;
        boolean neverQuoted = false;
        for (Asset asset : assets) {
            Instant at = asset.getPriceUpdatedAt();
            if (at == null) neverQuoted = true;
            else if (oldest == null || at.isBefore(oldest)) oldest = at;
            if (at == null || at.isBefore(cutoff)) stale.add(asset.getName());
        }
        return new Result(updated, unpriced, stale, neverQuoted ? null : oldest);
    }
}
