package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.analytics.PortfolioSnapshot;
import com.portfolio.tracker.analytics.RiskMath;
import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.VaRCalculation;
import com.portfolio.tracker.model.User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Value at Risk (VaR) calculation service.
 *
 * All methods measure the loss on today's holdings, estimated from aligned daily CoinGecko
 * returns of the coins actually held:
 * <ul>
 *   <li>Historical simulation: empirical loss quantile of the portfolio's daily returns, using
 *       overlapping compounded h-day returns when the history is long enough.</li>
 *   <li>Parametric (variance-covariance): normal model with the sample mean vector and
 *       covariance matrix, VaR = V (z sigma_p sqrt(h) - mu_p h).</li>
 *   <li>Monte Carlo: correlated lognormal (GBM) scenarios drawn through the Cholesky factor of
 *       the log-return covariance matrix.</li>
 *   <li>Conditional VaR (Expected Shortfall): mean loss beyond the historical VaR.</li>
 * </ul>
 */
@Service
public class VaRCalculationService {

    private final AssetService assetService;
    private final PortfolioDataService portfolioDataService;
    private final AnalyticsProperties properties;
    private final AssetPriceRefreshService priceRefreshService;

    public VaRCalculationService(AssetService assetService,
                                 PortfolioDataService portfolioDataService,
                                 AnalyticsProperties properties,
                                 AssetPriceRefreshService priceRefreshService) {
        this.assetService = assetService;
        this.portfolioDataService = portfolioDataService;
        this.properties = properties;
        this.priceRefreshService = priceRefreshService;
    }

    /**
     * Calculate comprehensive VaR for a user's portfolio
     * @throws PortfolioDataException if the portfolio is empty or lacks price history
     */
    public VaRCalculation calculatePortfolioVaR(User user, double confidenceLevel, int timeHorizon) {
        priceRefreshService.refreshQuietly(user); // value the portfolio at current prices
        List<Asset> assets = assetService.getAssetsByUser(user);
        if (assets.isEmpty()) {
            throw PortfolioDataException.noAssets();
        }
        PortfolioSnapshot snapshot = portfolioDataService.snapshot(assets, false);
        double value = snapshot.totalValue();
        double[] portfolioReturns = snapshot.portfolioReturns();

        VaRCalculation varCalculation = new VaRCalculation();
        varCalculation.setUserId(user.getUsername());
        varCalculation.setConfidenceLevel(confidenceLevel);
        varCalculation.setTimeHorizon(timeHorizon);
        varCalculation.setPortfolioValue(value);
        varCalculation.setNumberOfSimulations(properties.getMonteCarloSimulations());
        varCalculation.setRiskFreeRate(properties.getRiskFreeRate());
        varCalculation.setObservations(snapshot.observations());
        varCalculation.setHistoryStart(snapshot.dates().get(0).toString());
        varCalculation.setHistoryEnd(snapshot.dates().get(snapshot.dates().size() - 1).toString());

        Map<String, Double> weights = new LinkedHashMap<>();
        Map<String, Double> meanReturns = new LinkedHashMap<>();
        double[] assetMeans = RiskMath.columnMeans(snapshot.returns());
        for (int i = 0; i < snapshot.size(); i++) {
            weights.put(snapshot.names().get(i), snapshot.weights()[i]);
            meanReturns.put(snapshot.names().get(i), assetMeans[i]);
        }
        varCalculation.setAssetWeights(weights);
        varCalculation.setAssetReturns(meanReturns);
        varCalculation.setHistoricalReturns(Arrays.stream(portfolioReturns).boxed().toList());

        // Daily distribution statistics of the portfolio
        double dailyVol = RiskMath.sampleStdDev(portfolioReturns);
        varCalculation.setVolatility(dailyVol);
        varCalculation.setAnnualizedVolatility(dailyVol * Math.sqrt(properties.getPeriodsPerYear()));
        varCalculation.setExpectedReturn(RiskMath.mean(portfolioReturns));
        varCalculation.setSkewness(RiskMath.skewness(portfolioReturns));
        varCalculation.setKurtosis(RiskMath.kurtosis(portfolioReturns));

        HorizonReturns horizon = horizonReturns(portfolioReturns, timeHorizon);
        varCalculation.setHistoricalVaRMethod(horizon.method());
        double historicalVaR = historicalVaR(horizon, confidenceLevel) * value;
        varCalculation.setHistoricalVaR(historicalVaR);
        varCalculation.setConditionalVaR(expectedShortfall(horizon, confidenceLevel) * value);
        varCalculation.setParametricVaR(parametricVaR(snapshot, confidenceLevel, timeHorizon) * value);
        varCalculation.setMonteCarloVaR(monteCarloVaR(snapshot, confidenceLevel, timeHorizon) * value);

        return varCalculation;
    }

    /**
     * Portfolio returns over the requested horizon. Overlapping compounded h-day returns keep the
     * empirical fat tails; when the history is too short for that, 1-day returns are used and the
     * resulting loss is scaled by sqrt(h) (the Basel square-root-of-time rule).
     */
    private record HorizonReturns(double[] returns, double scale, String method) {
    }

    private HorizonReturns horizonReturns(double[] daily, int horizon) {
        if (horizon == 1) {
            return new HorizonReturns(daily, 1.0, "1-day historical returns");
        }
        int windows = daily.length - horizon + 1;
        if (windows >= properties.getMinObservations()) {
            double[] compounded = new double[windows];
            for (int start = 0; start < windows; start++) {
                double growth = 1.0;
                for (int k = 0; k < horizon; k++) growth *= 1.0 + daily[start + k];
                compounded[start] = growth - 1.0;
            }
            return new HorizonReturns(compounded, 1.0,
                    "overlapping " + horizon + "-day compounded returns (" + windows + " windows)");
        }
        return new HorizonReturns(daily, Math.sqrt(horizon),
                "1-day historical returns scaled by sqrt(" + horizon + ")");
    }

    /** Historical VaR as a fraction of portfolio value. */
    private double historicalVaR(HorizonReturns horizon, double confidenceLevel) {
        double lossQuantile = -RiskMath.quantile(horizon.returns(), 1.0 - confidenceLevel);
        return Math.max(0.0, lossQuantile * horizon.scale());
    }

    /** Expected Shortfall: average loss in the tail at or beyond the historical VaR. */
    private double expectedShortfall(HorizonReturns horizon, double confidenceLevel) {
        double threshold = RiskMath.quantile(horizon.returns(), 1.0 - confidenceLevel);
        double sum = 0.0;
        int count = 0;
        for (double r : horizon.returns()) {
            if (r <= threshold) {
                sum += r;
                count++;
            }
        }
        double tailLoss = -(sum / count);
        return Math.max(0.0, tailLoss * horizon.scale());
    }

    /** Variance-covariance VaR as a fraction of portfolio value. */
    private double parametricVaR(PortfolioSnapshot snapshot, double confidenceLevel, int horizon) {
        double[] w = snapshot.weights();
        double[][] cov = RiskMath.covarianceMatrix(snapshot.returns());
        double[] mu = RiskMath.columnMeans(snapshot.returns());
        double sigmaP = Math.sqrt(RiskMath.quadraticForm(w, cov));
        double muP = RiskMath.dot(w, mu);
        double z = RiskMath.inverseNormalCdf(confidenceLevel);
        return Math.max(0.0, z * sigmaP * Math.sqrt(horizon) - muP * horizon);
    }

    /**
     * Monte Carlo VaR under correlated geometric Brownian motion: h-day log returns are drawn
     * from N(h * mu, h * Sigma) estimated on daily log returns, so simulated prices stay positive
     * and losses are bounded at 100%.
     */
    private double monteCarloVaR(PortfolioSnapshot snapshot, double confidenceLevel, int horizon) {
        double[][] logReturns = snapshot.logReturns();
        double[] mu = RiskMath.columnMeans(logReturns);
        double[][] chol = RiskMath.cholesky(RiskMath.covarianceMatrix(logReturns));
        double[] w = snapshot.weights();
        int n = w.length;
        int simulations = properties.getMonteCarloSimulations();
        double sqrtH = Math.sqrt(horizon);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double[] portfolioReturns = new double[simulations];
        double[] z = new double[n];
        for (int s = 0; s < simulations; s++) {
            for (int i = 0; i < n; i++) z[i] = random.nextGaussian();
            double rp = 0.0;
            for (int i = 0; i < n; i++) {
                double shock = 0.0;
                for (int k = 0; k <= i; k++) shock += chol[i][k] * z[k];
                double logReturn = mu[i] * horizon + shock * sqrtH;
                rp += w[i] * Math.expm1(logReturn);
            }
            portfolioReturns[s] = rp;
        }
        return Math.max(0.0, -RiskMath.quantile(portfolioReturns, 1.0 - confidenceLevel));
    }

    /**
     * Get VaR calculation summary
     */
    public Map<String, Object> getVaRSummary(VaRCalculation varCalculation) {
        Map<String, Object> summary = new HashMap<>();
        double value = varCalculation.getPortfolioValue();

        summary.put("portfolioValue", value);
        summary.put("confidenceLevel", varCalculation.getConfidenceLevel());
        summary.put("timeHorizon", varCalculation.getTimeHorizon());
        summary.put("riskLevel", varCalculation.getRiskLevel());
        summary.put("observations", varCalculation.getObservations());
        summary.put("historyStart", varCalculation.getHistoryStart());
        summary.put("historyEnd", varCalculation.getHistoryEnd());
        summary.put("historicalVaRMethod", varCalculation.getHistoricalVaRMethod());
        summary.put("numberOfSimulations", varCalculation.getNumberOfSimulations());

        // VaR Results
        Map<String, Object> varResults = new HashMap<>();
        varResults.put("historicalVaR", varCalculation.getHistoricalVaR());
        varResults.put("parametricVaR", varCalculation.getParametricVaR());
        varResults.put("monteCarloVaR", varCalculation.getMonteCarloVaR());
        varResults.put("conditionalVaR", varCalculation.getConditionalVaR());
        varResults.put("historicalVaRPercentage", (varCalculation.getHistoricalVaR() / value) * 100);
        varResults.put("parametricVaRPercentage", (varCalculation.getParametricVaR() / value) * 100);
        varResults.put("monteCarloVaRPercentage", (varCalculation.getMonteCarloVaR() / value) * 100);
        varResults.put("conditionalVaRPercentage", (varCalculation.getConditionalVaR() / value) * 100);

        summary.put("varResults", varResults);

        // Risk Metrics
        Map<String, Object> riskMetrics = new HashMap<>();
        riskMetrics.put("volatility", varCalculation.getVolatility());
        riskMetrics.put("annualizedVolatility", varCalculation.getAnnualizedVolatility());
        riskMetrics.put("skewness", varCalculation.getSkewness());
        riskMetrics.put("kurtosis", varCalculation.getKurtosis());
        riskMetrics.put("expectedReturn", varCalculation.getExpectedReturn());

        summary.put("riskMetrics", riskMetrics);

        return summary;
    }
}
