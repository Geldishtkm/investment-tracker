package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.analytics.PortfolioSnapshot;
import com.portfolio.tracker.analytics.TraderScoring;
import com.portfolio.tracker.analytics.TraderScoring.Evaluation;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.Trade;
import com.portfolio.tracker.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Gathers a user's holdings, trade ledger and market data and runs the Institutional Trader
 * Evaluation ({@link TraderScoring}). Market data that can't be loaded leaves the metrics that
 * need it unscored, rather than failing the whole evaluation.
 */
@Service
public class BehavioralAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(BehavioralAnalyticsService.class);

    private final AssetService assetService;
    private final TradeService tradeService;
    private final PortfolioDataService portfolioDataService;
    private final PriceHistoryService priceHistoryService;
    private final CoinMarketDataService coinMarketDataService;
    private final AnalyticsProperties properties;

    public BehavioralAnalyticsService(AssetService assetService, TradeService tradeService,
                                      PortfolioDataService portfolioDataService, PriceHistoryService priceHistoryService,
                                      CoinMarketDataService coinMarketDataService, AnalyticsProperties properties) {
        this.assetService = assetService;
        this.tradeService = tradeService;
        this.portfolioDataService = portfolioDataService;
        this.priceHistoryService = priceHistoryService;
        this.coinMarketDataService = coinMarketDataService;
        this.properties = properties;
    }

    public Evaluation evaluate(User user) {
        List<Asset> assets = assetService.getAssetsByUser(user);
        List<Trade> trades = tradeService.getTrades(user);
        if (assets.isEmpty() && trades.isEmpty()) throw PortfolioDataException.noAssets();

        Map<String, String> coinIds = new HashMap<>();

        // Current holdings per coin (unresolvable names stay separate holdings for concentration)
        Map<String, double[]> holdingValues = new LinkedHashMap<>();
        Map<String, String> holdingNames = new HashMap<>();
        Map<String, Double> currentPrices = new HashMap<>();
        for (Asset a : assets) {
            double value = a.getQuantity() * a.getPricePerUnit();
            if (a.getQuantity() <= 0 || value <= 0) continue;
            String id = coinIdOrNull(a.getName(), coinIds);
            String key = id != null ? id : "name:" + a.getName().trim().toLowerCase(Locale.ROOT);
            holdingValues.computeIfAbsent(key, k -> new double[1])[0] += value;
            holdingNames.putIfAbsent(key, a.getName());
            if (id != null) currentPrices.put(id, a.getPricePerUnit());
        }
        List<TraderScoring.Holding> holdings = holdingValues.entrySet().stream()
                .map(e -> new TraderScoring.Holding(e.getKey(), holdingNames.get(e.getKey()), e.getValue()[0])).toList();

        // Daily return history of the current holdings
        double[] portfolioReturns = null, benchmarkReturns = null;
        String historyReason = null;
        if (holdings.isEmpty()) {
            historyReason = "You have no current holdings.";
        } else {
            try {
                PortfolioSnapshot snapshot = portfolioDataService.snapshot(assets, true);
                portfolioReturns = snapshot.portfolioReturns();
                benchmarkReturns = snapshot.benchmark();
            } catch (PortfolioDataException e) {
                historyReason = e.getMessage();
            }
        }

        // Ledger trades, with the market history needed to judge each one
        List<TraderScoring.TradeEvent> events = new ArrayList<>();
        for (Trade t : trades) {
            String id = coinIdOrNull(t.getAssetName(), coinIds);
            if (id == null) continue; // a coin CoinGecko doesn't know can't be compared with the market
            events.add(new TraderScoring.TradeEvent(t.getId(), id, t.getAssetName(),
                    t.getSide() == Trade.Side.BUY ? TraderScoring.Side.BUY : TraderScoring.Side.SELL,
                    t.getQuantity(), t.getPrice(), t.getTradeDate(), t.isDateKnown(), t.getSource() == Trade.Source.IMPORTED));
        }
        Set<String> tradedCoins = new LinkedHashSet<>();
        for (TraderScoring.TradeEvent e : events) tradedCoins.add(e.coinId());

        Map<String, SortedMap<LocalDate, Double>> dailyPrices = new HashMap<>();
        for (String id : tradedCoins) {
            try {
                dailyPrices.put(id, priceHistoryService.getDailyPrices(id, properties.getLookbackDays()));
            } catch (PortfolioDataException e) {
                logger.debug("No price history for {}: {}", id, e.getMessage());
            }
        }
        Map<String, Double> volumes = tradedCoins.isEmpty() ? Map.of() : coinMarketDataService.getTotalVolumes(tradedCoins);

        return TraderScoring.evaluate(new TraderScoring.Inputs(holdings, portfolioReturns, benchmarkReturns, historyReason,
                events, dailyPrices, currentPrices, volumes, user.getRiskTargetVolatility(),
                properties.getRiskFreeRate(), properties.getPeriodsPerYear(), LocalDate.now(ZoneOffset.UTC)));
    }

    private String coinIdOrNull(String name, Map<String, String> cache) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (cache.containsKey(key)) return cache.get(key);
        String id;
        try {
            id = coinMarketDataService.resolveCoinId(name);
        } catch (PortfolioDataException e) {
            id = null;
        }
        cache.put(key, id);
        return id;
    }
}
