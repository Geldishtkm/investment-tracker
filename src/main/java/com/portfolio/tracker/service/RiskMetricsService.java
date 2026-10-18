package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.analytics.PortfolioSnapshot;
import com.portfolio.tracker.analytics.RiskMath;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.RiskMetrics;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portfolio risk metrics computed from the daily return history of the coins actually held,
 * at today's weights:
 * <ul>
 *   <li>volatility: annualized sample standard deviation of daily portfolio returns</li>
 *   <li>Sharpe ratio: (annualized mean return - risk-free rate) / annualized volatility</li>
 *   <li>beta: cov(portfolio, benchmark) / var(benchmark) on daily returns</li>
 *   <li>diversification score: 100 x (1 - Herfindahl-Hirschman index of the weights)</li>
 * </ul>
 */
@Service
public class RiskMetricsService {

    private final PortfolioDataService portfolioDataService;
    private final AnalyticsProperties properties;

    public RiskMetricsService(PortfolioDataService portfolioDataService, AnalyticsProperties properties) {
        this.portfolioDataService = portfolioDataService;
        this.properties = properties;
    }

    /** Core metrics in the RiskMetrics model (ROI is filled in by the caller from cost basis). */
    public RiskMetrics calculate(List<Asset> assets) {
        Result r = compute(assets);
        return RiskMetrics.builder()
                .sharpeRatio(r.sharpeRatio())
                .volatility(r.volatility())
                .beta(r.beta())
                .diversificationScore(r.diversificationScore())
                .build();
    }

    /** Every metric plus the data it was computed from, for the analytics endpoint. */
    public Map<String, Object> calculateDetailed(List<Asset> assets) {
        Result r = compute(assets);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sharpeRatio", r.sharpeRatio());
        out.put("volatility", r.volatility());
        out.put("annualizedReturn", r.annualizedReturn());
        out.put("beta", r.beta());
        out.put("diversificationScore", r.diversificationScore());
        out.put("effectiveNumberOfAssets", r.effectiveAssets());
        out.put("diversificationRatio", r.diversificationRatio());
        out.put("riskFreeRate", properties.getRiskFreeRate());
        out.put("benchmark", properties.getBenchmarkCoinId());
        out.put("observations", r.observations());
        out.put("historyStart", r.historyStart());
        out.put("historyEnd", r.historyEnd());
        return out;
    }

    private record Result(double sharpeRatio, double volatility, double annualizedReturn, double beta,
                          double diversificationScore, double effectiveAssets,
                          double diversificationRatio, int observations, String historyStart, String historyEnd) {
    }

    private Result compute(List<Asset> assets) {
        if (assets.isEmpty()) throw PortfolioDataException.noAssets();
        PortfolioSnapshot s = portfolioDataService.snapshot(assets, true);
        int periods = properties.getPeriodsPerYear();
        double[] rp = s.portfolioReturns();

        double volatility = RiskMath.sampleStdDev(rp) * Math.sqrt(periods);
        double annualReturn = RiskMath.mean(rp) * periods;
        if (volatility == 0.0) {
            throw new PortfolioDataException(PortfolioDataException.Reason.INSUFFICIENT_HISTORY,
                    "The portfolio's value did not change over the lookback window, so risk metrics are undefined.");
        }
        double sharpe = (annualReturn - properties.getRiskFreeRate()) / volatility;

        double benchmarkVariance = RiskMath.sampleVariance(s.benchmark());
        if (benchmarkVariance == 0.0) {
            throw new PortfolioDataException(PortfolioDataException.Reason.INSUFFICIENT_HISTORY,
                    "Beta is undefined because the benchmark (" + properties.getBenchmarkCoinId()
                            + ") did not move over the lookback window. Choose a different benchmark.");
        }
        double beta = RiskMath.covariance(rp, s.benchmark()) / benchmarkVariance;

        double hhi = 0.0;
        for (double w : s.weights()) hhi += w * w;
        double diversificationScore = 100.0 * (1.0 - hhi);

        // Diversification ratio (Choueifaty & Coignard, 2008): weighted average volatility / portfolio volatility
        double[][] cov = RiskMath.covarianceMatrix(s.returns());
        double weightedVol = 0.0;
        for (int i = 0; i < s.size(); i++) weightedVol += s.weights()[i] * Math.sqrt(cov[i][i]);
        double diversificationRatio = weightedVol / Math.sqrt(RiskMath.quadraticForm(s.weights(), cov));

        return new Result(sharpe, volatility, annualReturn, beta, diversificationScore, 1.0 / hhi,
                diversificationRatio, s.observations(), s.dates().get(0).toString(),
                s.dates().get(s.dates().size() - 1).toString());
    }
}
