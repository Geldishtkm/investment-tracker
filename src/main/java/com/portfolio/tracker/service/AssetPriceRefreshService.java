package com.portfolio.tracker.service;

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

    /** What a refresh changed. */
    public record Result(int updated, List<String> unpriced) {
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

        Map<String, Double> prices = cryptoPriceService.getPricesInUSD(new LinkedHashSet<>(coinIds.values()));

        int updated = 0;
        for (Map.Entry<Asset, String> entry : coinIds.entrySet()) {
            Asset asset = entry.getKey();
            Double price = prices.get(entry.getValue());
            if (price == null) {
                unpriced.add(asset.getName());
            } else if (price != asset.getPricePerUnit()) {
                asset.setPricePerUnit(price);
                assetRepository.save(asset);
                updated++;
            }
        }
        if (updated > 0) {
            logger.info("Updated prices of {} asset(s) for {}", updated, user.getUsername());
        }
        return new Result(updated, unpriced);
    }
}
