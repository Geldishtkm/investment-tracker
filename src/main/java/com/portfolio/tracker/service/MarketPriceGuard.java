package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

/**
 * Paper trades execute at market prices. A trade price the user types must be close to the coin's
 * real price on the trade date, otherwise buying low on paper (or selling high) would create money
 * that was never there.
 */
@Service
public class MarketPriceGuard {

    /** Today's trades are compared with the live price. */
    static final double LIVE_TOLERANCE = 0.10;
    /** Earlier trades are compared with that day's closing price; crypto moves a lot within a day. */
    static final double DAILY_TOLERANCE = 0.25;

    private final CoinMarketDataService coinMarketDataService;
    private final CryptoPriceService cryptoPriceService;
    private final PriceHistoryService priceHistoryService;
    private final AnalyticsProperties properties;

    public MarketPriceGuard(CoinMarketDataService coinMarketDataService, CryptoPriceService cryptoPriceService,
                            PriceHistoryService priceHistoryService, AnalyticsProperties properties) {
        this.coinMarketDataService = coinMarketDataService;
        this.cryptoPriceService = cryptoPriceService;
        this.priceHistoryService = priceHistoryService;
        this.properties = properties;
    }

    /**
     * @throws IllegalArgumentException if the coin is unknown, the date is too old to check, or the price is
     *                                  outside the allowed range of the market price
     * @throws AccountService.AccountException (503) if market prices can't be reached to check it
     */
    public void verify(String assetName, double price, LocalDate date) {
        String coinId;
        try {
            coinId = coinMarketDataService.resolveCoinId(assetName);
        } catch (PortfolioDataException e) {
            if (e.getReason() == PortfolioDataException.Reason.UNKNOWN_ASSET) {
                throw new IllegalArgumentException("Paper trades need a coin listed on CoinGecko, so its price can be "
                        + "checked. " + e.getMessage());
            }
            throw unavailable();
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!date.isBefore(today)) {
            CryptoPriceService.Quote quote = cryptoPriceService.getQuotes(List.of(coinId)).get(coinId);
            if (quote == null) throw unavailable();
            check(assetName, price, quote.usd(), LIVE_TOLERANCE, "the current market price");
            return;
        }

        int lookback = properties.getLookbackDays();
        if (date.isBefore(today.minusDays(lookback - 1))) {
            throw new IllegalArgumentException("Paper trades can be dated at most " + (lookback - 1)
                    + " days back, the price history available to check them against.");
        }
        SortedMap<LocalDate, Double> daily;
        try {
            daily = priceHistoryService.getDailyPrices(coinId, lookback);
        } catch (PortfolioDataException e) {
            throw unavailable();
        }
        // The close on the trade date, or the last one before it when CoinGecko skipped that day
        SortedMap<LocalDate, Double> upTo = daily.headMap(date.plusDays(1));
        if (upTo.isEmpty()) throw unavailable();
        Map.Entry<LocalDate, Double> close = Map.entry(upTo.lastKey(), upTo.get(upTo.lastKey()));
        check(assetName, price, close.getValue(), DAILY_TOLERANCE, "its closing price on " + close.getKey());
    }

    private static void check(String assetName, double price, double market, double tolerance, String reference) {
        if (!(market > 0)) return;
        double deviation = price / market - 1.0;
        if (Math.abs(deviation) > tolerance) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "$%s is %.0f%% %s %s's market price ($%s, %s). Paper trades execute at market prices: "
                            + "use a price within ±%.0f%%.",
                    format(price), Math.abs(deviation) * 100, deviation < 0 ? "below" : "above", assetName,
                    format(market), reference, tolerance * 100));
        }
    }

    private static AccountService.AccountException unavailable() {
        return new AccountService.AccountException(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_CHECK_UNAVAILABLE",
                "Market prices can't be reached to check this trade's price. Please try again shortly.");
    }

    private static String format(double v) {
        return v >= 1 ? String.format(Locale.US, "%,.2f", v)
                : java.math.BigDecimal.valueOf(v).round(new java.math.MathContext(4)).stripTrailingZeros().toPlainString();
    }
}
