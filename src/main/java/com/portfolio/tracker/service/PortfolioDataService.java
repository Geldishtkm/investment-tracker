package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.analytics.PortfolioSnapshot;
import com.portfolio.tracker.model.Asset;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Builds {@link PortfolioSnapshot}s: aggregates a list of assets per coin, and aligns their daily
 * price history from CoinGecko on the dates every coin (and the benchmark) has a price for.
 */
@Service
public class PortfolioDataService {

    private final CoinMarketDataService coinMarketDataService;
    private final PriceHistoryService priceHistoryService;
    private final AnalyticsProperties properties;

    public PortfolioDataService(CoinMarketDataService coinMarketDataService,
                                PriceHistoryService priceHistoryService,
                                AnalyticsProperties properties) {
        this.coinMarketDataService = coinMarketDataService;
        this.priceHistoryService = priceHistoryService;
        this.properties = properties;
    }

    public PortfolioSnapshot snapshot(List<Asset> assets, boolean includeBenchmark) {
        // Aggregate positions per CoinGecko ID so two "Bitcoin" entries count as one holding
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Double> quantities = new LinkedHashMap<>();
        Map<String, Double> values = new LinkedHashMap<>();
        for (Asset asset : assets) {
            double value = asset.getQuantity() * asset.getPricePerUnit();
            if (asset.getQuantity() <= 0 || value <= 0) continue;
            String id = coinMarketDataService.resolveCoinId(asset.getName());
            names.putIfAbsent(id, asset.getName());
            quantities.merge(id, asset.getQuantity(), Double::sum);
            values.merge(id, value, Double::sum);
        }
        if (values.isEmpty()) throw PortfolioDataException.noAssets();

        List<String> coinIds = new ArrayList<>(values.keySet());
        int n = coinIds.size();
        double total = values.values().stream().mapToDouble(Double::doubleValue).sum();
        double[] q = new double[n], v = new double[n], w = new double[n];
        for (int i = 0; i < n; i++) {
            q[i] = quantities.get(coinIds.get(i));
            v[i] = values.get(coinIds.get(i));
            w[i] = v[i] / total;
        }

        List<String> toFetch = new ArrayList<>(coinIds);
        String benchmarkId = properties.getBenchmarkCoinId();
        if (includeBenchmark && !toFetch.contains(benchmarkId)) toFetch.add(benchmarkId);
        Map<String, SortedMap<LocalDate, Double>> history = fetchHistories(toFetch);

        // Keep only dates on which every series has a price
        SortedSet<LocalDate> common = new TreeSet<>(history.get(toFetch.get(0)).keySet());
        for (String id : toFetch) common.retainAll(history.get(id).keySet());
        List<LocalDate> dates = new ArrayList<>(common);

        // Returns are only taken between consecutive calendar days
        List<Integer> returnStarts = new ArrayList<>();
        for (int t = 0; t + 1 < dates.size(); t++) {
            if (dates.get(t).plusDays(1).equals(dates.get(t + 1))) returnStarts.add(t);
        }
        if (returnStarts.size() < properties.getMinObservations()) {
            throw new PortfolioDataException(PortfolioDataException.Reason.INSUFFICIENT_HISTORY,
                    "Not enough shared price history: " + returnStarts.size() + " daily returns are available "
                            + "for all your coins together, but at least " + properties.getMinObservations()
                            + " are needed. A recently listed coin limits the history for the whole portfolio.");
        }

        double[][] prices = new double[dates.size()][n];
        for (int t = 0; t < dates.size(); t++) {
            for (int i = 0; i < n; i++) prices[t][i] = history.get(coinIds.get(i)).get(dates.get(t));
        }
        double[][] returns = new double[returnStarts.size()][n];
        double[] benchmark = includeBenchmark ? new double[returnStarts.size()] : null;
        SortedMap<LocalDate, Double> benchmarkPrices = includeBenchmark ? history.get(benchmarkId) : null;
        for (int k = 0; k < returnStarts.size(); k++) {
            int t = returnStarts.get(k);
            for (int i = 0; i < n; i++) returns[k][i] = prices[t + 1][i] / prices[t][i] - 1.0;
            if (includeBenchmark) {
                benchmark[k] = benchmarkPrices.get(dates.get(t + 1)) / benchmarkPrices.get(dates.get(t)) - 1.0;
            }
        }

        return new PortfolioSnapshot(coinIds, coinIds.stream().map(names::get).toList(),
                q, v, w, total, dates, prices, returns, benchmark);
    }

    private Map<String, SortedMap<LocalDate, Double>> fetchHistories(List<String> coinIds) {
        int days = properties.getLookbackDays();
        Map<String, CompletableFuture<SortedMap<LocalDate, Double>>> futures = new LinkedHashMap<>();
        for (String id : coinIds) {
            futures.put(id, CompletableFuture.supplyAsync(() -> priceHistoryService.getDailyPrices(id, days)));
        }
        Map<String, SortedMap<LocalDate, Double>> result = new HashMap<>();
        try {
            for (Map.Entry<String, CompletableFuture<SortedMap<LocalDate, Double>>> e : futures.entrySet()) {
                result.put(e.getKey(), e.getValue().join());
            }
        } catch (CompletionException e) {
            if (e.getCause() instanceof PortfolioDataException pde) throw pde;
            throw e;
        }
        return result;
    }
}
