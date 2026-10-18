package com.portfolio.tracker.analytics;

import java.time.LocalDate;
import java.util.List;

/**
 * A portfolio's current holdings aggregated per coin, together with aligned daily price history.
 *
 * @param coinIds      CoinGecko ID of each position
 * @param names        display name of each position (the name the user gave the asset)
 * @param quantities   total units held per position
 * @param values       current market value per position
 * @param weights      value weights (sum to 1)
 * @param totalValue   total portfolio value
 * @param dates        dates of the aligned price observations, oldest first
 * @param prices       prices[t][i] = price of position i on dates[t]
 * @param returns      returns[t][i] = simple daily return of position i from dates[t] to dates[t + 1]
 * @param benchmark    benchmark simple daily returns over the same days, or null if not requested
 */
public record PortfolioSnapshot(
        List<String> coinIds,
        List<String> names,
        double[] quantities,
        double[] values,
        double[] weights,
        double totalValue,
        List<LocalDate> dates,
        double[][] prices,
        double[][] returns,
        double[] benchmark) {

    public int size() {
        return coinIds.size();
    }

    public int observations() {
        return returns.length;
    }

    /** Daily simple returns of the portfolio held at today's weights. */
    public double[] portfolioReturns() {
        double[] rp = new double[returns.length];
        for (int t = 0; t < returns.length; t++) rp[t] = RiskMath.dot(weights, returns[t]);
        return rp;
    }

    /** Daily log returns per position, for lognormal simulation. */
    public double[][] logReturns() {
        double[][] out = new double[returns.length][size()];
        for (int t = 0; t < returns.length; t++) {
            for (int i = 0; i < size(); i++) out[t][i] = Math.log1p(returns[t][i]);
        }
        return out;
    }

    /** Value path of the current holdings priced at each historical date. */
    public double[] historicalValuePath() {
        double[] path = new double[prices.length];
        for (int t = 0; t < prices.length; t++) path[t] = RiskMath.dot(quantities, prices[t]);
        return path;
    }

    public int indexOf(String coinId) {
        return coinIds.indexOf(coinId);
    }
}
