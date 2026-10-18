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

    /**
     * Fail at startup on settings that would make the statistics undefined (NaN) or meaningless,
     * rather than serving bad numbers later.
     */
    @jakarta.annotation.PostConstruct
    void validate() {
        require(lookbackDays >= 2 && lookbackDays <= 365, "portfolio.analytics.lookback-days must be 2-365 (CoinGecko's free limit)");
        // Sample kurtosis needs at least 4 observations; anything under ~30 is statistically unreliable
        require(minObservations >= 4, "portfolio.analytics.min-observations must be at least 4");
        require(minObservations < lookbackDays, "portfolio.analytics.min-observations must be below lookback-days");
        require(periodsPerYear > 0, "portfolio.analytics.periods-per-year must be positive");
        require(riskFreeRate > -1 && riskFreeRate < 1, "portfolio.analytics.risk-free-rate is an annual fraction, e.g. 0.04");
        require(benchmarkCoinId != null && !benchmarkCoinId.isBlank(), "portfolio.analytics.benchmark-coin-id is required");
        require(priceCacheTtlMinutes >= 0, "portfolio.analytics.price-cache-ttl-minutes cannot be negative");
        require(monteCarloSimulations >= 1000, "portfolio.var.monte-carlo-simulations must be at least 1000 for a stable tail quantile");
        require(transactionCostRate >= 0 && transactionCostRate < 1, "portfolio.rebalancing.transaction-cost-rate must be in [0, 1)");
        require(minTradeWeight >= 0 && minTradeWeight < 1, "portfolio.rebalancing.min-trade-weight must be in [0, 1)");
        require(blackLittermanRiskAversion > 0, "portfolio.black-litterman.risk-aversion must be positive");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Invalid analytics configuration: " + message);
    }

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
