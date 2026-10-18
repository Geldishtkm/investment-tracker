package com.portfolio.tracker.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Model inputs for the risk and optimization pipelines, bound from application.properties
 * (portfolio.* keys) so none of them are buried in calculation code.
 */
@Component
public class AnalyticsProperties {

    /** Days of daily price history used to estimate returns, volatility and covariance. */
    @Value("${portfolio.analytics.lookback-days}")
    private int lookbackDays;

    /** Fewest aligned daily returns accepted before a statistic is reported. */
    @Value("${portfolio.analytics.min-observations}")
    private int minObservations;

    /** Return periods per year used to annualize daily statistics (crypto trades every day). */
    @Value("${portfolio.analytics.periods-per-year}")
    private int periodsPerYear;

    /** Annual risk-free rate used for Sharpe ratio and the Black-Litterman market premium. */
    @Value("${portfolio.analytics.risk-free-rate}")
    private double riskFreeRate;

    /** CoinGecko ID of the market benchmark used for beta. */
    @Value("${portfolio.analytics.benchmark-coin-id}")
    private String benchmarkCoinId;

    /** How long fetched price history is reused before it is downloaded again. */
    @Value("${portfolio.analytics.price-cache-ttl-minutes}")
    private long priceCacheTtlMinutes;

    /** Number of Monte Carlo scenarios for Monte Carlo VaR. */
    @Value("${portfolio.var.monte-carlo-simulations}")
    private int monteCarloSimulations;

    /** Proportional trading cost charged on each rebalancing trade's notional value. */
    @Value("${portfolio.rebalancing.transaction-cost-rate}")
    private double transactionCostRate;

    /** Weight changes smaller than this are not turned into trade recommendations. */
    @Value("${portfolio.rebalancing.min-trade-weight}")
    private double minTradeWeight;

    /** Market risk aversion (delta) used to reverse-optimize Black-Litterman equilibrium returns. */
    @Value("${portfolio.black-litterman.risk-aversion}")
    private double blackLittermanRiskAversion;

    public double getBlackLittermanRiskAversion() { return blackLittermanRiskAversion; }
    public int getLookbackDays() { return lookbackDays; }
    public int getMinObservations() { return minObservations; }
    public int getPeriodsPerYear() { return periodsPerYear; }
    public double getRiskFreeRate() { return riskFreeRate; }
    public String getBenchmarkCoinId() { return benchmarkCoinId; }
    public long getPriceCacheTtlMinutes() { return priceCacheTtlMinutes; }
    public int getMonteCarloSimulations() { return monteCarloSimulations; }
    public double getTransactionCostRate() { return transactionCostRate; }
    public double getMinTradeWeight() { return minTradeWeight; }
}
