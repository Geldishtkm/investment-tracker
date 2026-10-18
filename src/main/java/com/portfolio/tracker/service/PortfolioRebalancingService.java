package com.portfolio.tracker.service;

import com.portfolio.tracker.analytics.AnalyticsProperties;
import com.portfolio.tracker.analytics.PortfolioDataException;
import com.portfolio.tracker.analytics.PortfolioSnapshot;
import com.portfolio.tracker.analytics.RiskMath;
import com.portfolio.tracker.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Portfolio rebalancing with Markowitz mean-variance optimization and the Black-Litterman model.
 *
 * Inputs are estimated from the user's own holdings: annualized sample mean returns and the
 * sample covariance matrix of aligned daily CoinGecko returns. Portfolios are long-only and
 * fully invested. The risk tolerance in [0, 1] selects a point on the efficient frontier between
 * the minimum-variance portfolio (0) and the maximum expected return portfolio (1).
 */
@Service
public class PortfolioRebalancingService {

    private final AssetService assetService;
    private final PortfolioDataService portfolioDataService;
    private final CoinMarketDataService coinMarketDataService;
    private final AnalyticsProperties properties;
    private final AssetPriceRefreshService priceRefreshService;

    public PortfolioRebalancingService(AssetService assetService,
                                       PortfolioDataService portfolioDataService,
                                       CoinMarketDataService coinMarketDataService,
                                       AnalyticsProperties properties,
                                       AssetPriceRefreshService priceRefreshService) {
        this.assetService = assetService;
        this.portfolioDataService = portfolioDataService;
        this.coinMarketDataService = coinMarketDataService;
        this.properties = properties;
        this.priceRefreshService = priceRefreshService;
    }

    /** Annualized estimates for the user's holdings. */
    private record Estimates(PortfolioSnapshot snapshot, double[] mu, double[][] sigma) {
    }

    private Estimates estimate(User user) {
        priceRefreshService.refreshQuietly(user); // weights from current prices, not the last session's
        List<Asset> assets = assetService.getAssetsByUser(user);
        if (assets.isEmpty()) throw PortfolioDataException.noAssets();
        PortfolioSnapshot snapshot = portfolioDataService.snapshot(assets, false);
        int periods = properties.getPeriodsPerYear();
        double[] mu = RiskMath.scale(RiskMath.columnMeans(snapshot.returns()), periods);
        double[][] sigma = RiskMath.scale(RiskMath.covarianceMatrix(snapshot.returns()), periods);
        return new Estimates(snapshot, mu, sigma);
    }

    /**
     * Calculate optimal portfolio allocation using Mean-Variance Optimization
     */
    public PortfolioRebalancing calculateOptimalAllocation(User user, double riskTolerance) {
        Estimates est = estimate(user);
        double[] target = RiskMath.efficientFrontierPortfolio(est.mu(), est.sigma(), riskTolerance);
        return buildResult(user, riskTolerance, "MVO", est, est.mu(), target);
    }

    /**
     * Calculate optimal allocation using the Black-Litterman model.
     *
     * @param userViews asset name to sentiment in [0, 1]: 0.5 is neutral, 1 expects the asset to beat
     *                  its equilibrium return by one annual standard deviation, 0 to trail it by one
     */
    public PortfolioRebalancing calculateBlackLittermanAllocation(User user, double riskTolerance,
                                                                Map<String, Double> userViews) {
        Estimates est = estimate(user);
        PortfolioSnapshot snapshot = est.snapshot();
        int n = snapshot.size();
        double[][] sigma = est.sigma();

        // Market portfolio: the held coins weighted by market capitalisation
        Map<String, Double> caps = coinMarketDataService.getMarketCaps(snapshot.coinIds());
        double capTotal = snapshot.coinIds().stream().mapToDouble(caps::get).sum();
        double[] wMarket = new double[n];
        for (int i = 0; i < n; i++) wMarket[i] = caps.get(snapshot.coinIds().get(i)) / capTotal;

        // Market risk aversion delta is the model's ex-ante equilibrium input (expected market excess
        // return / market variance). It is configured rather than estimated from realised returns,
        // because a single year of crypto history is often negative, which would imply investors
        // pay to hold risk and make the equilibrium meaningless.
        double delta = properties.getBlackLittermanRiskAversion();

        // Implied equilibrium excess returns, converted back to total returns
        double[] pi = RiskMath.scale(RiskMath.multiply(sigma, wMarket), delta);
        for (int i = 0; i < n; i++) pi[i] += properties.getRiskFreeRate();

        // Uncertainty in the prior: tau = 1 / T, the standard error scaling of a sample mean
        double tau = 1.0 / snapshot.observations();

        List<int[]> viewAssets = new ArrayList<>();
        List<Double> viewReturns = new ArrayList<>();
        if (userViews != null) {
            for (Map.Entry<String, Double> view : userViews.entrySet()) {
                double sentiment = view.getValue();
                if (sentiment < 0 || sentiment > 1) {
                    throw new PortfolioDataException(PortfolioDataException.Reason.INVALID_VIEW,
                            "View for " + view.getKey() + " must be between 0 and 1.");
                }
                double tilt = 2 * sentiment - 1;
                if (tilt == 0.0) continue; // neutral: no view
                int i = indexOfHolding(snapshot, view.getKey());
                if (sigma[i][i] <= 0) {
                    // A view's uncertainty is proportional to the asset's variance; with none, the
                    // view matrix is singular and the view carries no information
                    throw new PortfolioDataException(PortfolioDataException.Reason.INVALID_VIEW,
                            "Can't set a view on " + view.getKey() + ": its price did not move over the lookback window.");
                }
                viewAssets.add(new int[]{i});
                viewReturns.add(pi[i] + tilt * Math.sqrt(sigma[i][i]));
            }
        }

        double[] muBL = pi;
        double[][] sigmaBL = sigma;
        int k = viewAssets.size();
        if (k > 0) {
            // P picks the asset of each absolute view; Omega = diag(P tau Sigma P') (He & Litterman, 1999)
            double[][] tauSigma = RiskMath.scale(sigma, tau);
            double[][] tauSigmaPt = new double[n][k];       // tau Sigma P'
            double[][] pTauSigmaPt = new double[k][k];      // P tau Sigma P'
            for (int v = 0; v < k; v++) {
                int a = viewAssets.get(v)[0];
                for (int r = 0; r < n; r++) tauSigmaPt[r][v] = tauSigma[r][a];
            }
            for (int v = 0; v < k; v++) {
                for (int u = 0; u < k; u++) pTauSigmaPt[v][u] = tauSigmaPt[viewAssets.get(v)[0]][u];
            }
            double[][] middle = new double[k][k];          // P tau Sigma P' + Omega
            for (int v = 0; v < k; v++) {
                System.arraycopy(pTauSigmaPt[v], 0, middle[v], 0, k);
                middle[v][v] += pTauSigmaPt[v][v];
            }
            double[][] middleInv = RiskMath.invert(middle);

            // Posterior mean: Pi + tau Sigma P' (P tau Sigma P' + Omega)^-1 (Q - P Pi)
            double[] surprise = new double[k];
            for (int v = 0; v < k; v++) surprise[v] = viewReturns.get(v) - pi[viewAssets.get(v)[0]];
            double[] adj = RiskMath.multiply(tauSigmaPt, RiskMath.multiply(middleInv, surprise));
            muBL = new double[n];
            for (int i = 0; i < n; i++) muBL[i] = pi[i] + adj[i];

            // Posterior covariance: Sigma + tau Sigma - tau Sigma P' (...)^-1 P tau Sigma
            sigmaBL = new double[n][n];
            for (int r = 0; r < n; r++) {
                double[] left = RiskMath.multiply(middleInv, tauSigmaPt[r]);
                for (int c = 0; c < n; c++) {
                    double correction = 0.0;
                    for (int v = 0; v < k; v++) correction += left[v] * tauSigmaPt[c][v];
                    sigmaBL[r][c] = sigma[r][c] + tauSigma[r][c] - correction;
                }
            }
        } else {
            // No views: posterior is the prior, with estimation uncertainty added to Sigma
            sigmaBL = new double[n][n];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) sigmaBL[r][c] = sigma[r][c] * (1 + tau);
            }
        }

        double[] target = RiskMath.efficientFrontierPortfolio(muBL, sigmaBL, riskTolerance);
        return buildResult(user, riskTolerance, "BLACK_LITTERMAN", est, muBL, target);
    }

    private PortfolioRebalancing buildResult(User user, double riskTolerance, String method,
                                             Estimates est, double[] expectedReturns, double[] target) {
        PortfolioSnapshot snapshot = est.snapshot();
        double[] current = snapshot.weights();
        double totalValue = snapshot.totalValue();

        PortfolioRebalancing rebalancing = new PortfolioRebalancing();
        rebalancing.setUserId(user.getUsername());
        rebalancing.setRiskTolerance(riskTolerance);
        rebalancing.setOptimizationMethod(method);
        rebalancing.setCurrentPortfolioValue(totalValue);
        rebalancing.setCurrentAllocation(toMap(snapshot, current));
        rebalancing.setTargetAllocation(toMap(snapshot, target));

        // Risk is measured on the sample covariance for both portfolios so they are comparable
        double currentRisk = Math.sqrt(RiskMath.quadraticForm(current, est.sigma()));
        double targetRisk = Math.sqrt(RiskMath.quadraticForm(target, est.sigma()));
        double currentReturn = RiskMath.dot(current, expectedReturns);
        double targetReturn = RiskMath.dot(target, expectedReturns);
        rebalancing.setCurrentRisk(currentRisk);
        rebalancing.setTargetRisk(targetRisk);
        rebalancing.setExpectedReturn(targetReturn);
        rebalancing.setRiskReduction(currentRisk - targetRisk);
        rebalancing.setReturnImprovement(targetReturn - currentReturn);

        List<RebalancingAction> actions = generateRebalancingActions(snapshot, current, target);
        rebalancing.setRecommendedActions(actions);
        double totalCost = actions.stream().mapToDouble(RebalancingAction::getTransactionCost).sum();
        rebalancing.setTotalTransactionCost(totalCost);
        rebalancing.setTargetPortfolioValue(totalValue - totalCost);
        return rebalancing;
    }

    /**
     * Trades that move each holding from its current to its target weight, largest drift first.
     */
    private List<RebalancingAction> generateRebalancingActions(PortfolioSnapshot snapshot,
                                                              double[] current, double[] target) {
        double totalValue = snapshot.totalValue();
        List<RebalancingAction> actions = new ArrayList<>();
        for (int i = 0; i < snapshot.size(); i++) {
            double drift = target[i] - current[i];
            if (Math.abs(drift) < properties.getMinTradeWeight()) continue;

            double price = snapshot.values()[i] / snapshot.quantities()[i];
            double currentValue = snapshot.values()[i];
            double targetValue = target[i] * totalValue;
            double valueChange = targetValue - currentValue;
            double quantityChange = Math.abs(valueChange) / price;

            RebalancingAction action = new RebalancingAction();
            action.setAssetName(snapshot.names().get(i));
            action.setActionType(valueChange > 0 ? "BUY" : "SELL");
            action.setQuantityChange(quantityChange);
            action.setCurrentQuantity(snapshot.quantities()[i]);
            action.setTargetQuantity(snapshot.quantities()[i] + Math.signum(valueChange) * quantityChange);
            action.setCurrentValue(currentValue);
            action.setTargetValue(targetValue);
            action.setValueChange(Math.abs(valueChange));
            action.setCurrentAllocation(current[i]);
            action.setTargetAllocation(target[i]);
            action.setAllocationChange(drift);
            action.setEstimatedPrice(price);
            action.setTransactionCost(Math.abs(valueChange) * properties.getTransactionCostRate());
            action.setTaxImpact(0.0); // tax lots are not tracked, so tax impact is not estimated
            action.calculateNetImpact();
            actions.add(action);
        }
        actions.sort(Comparator.comparingDouble((RebalancingAction a) -> -Math.abs(a.getAllocationChange())));
        for (int rank = 0; rank < actions.size(); rank++) actions.get(rank).setPriority(rank + 1);
        return actions;
    }

    private Map<String, Double> toMap(PortfolioSnapshot snapshot, double[] weights) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.size(); i++) map.put(snapshot.names().get(i), weights[i]);
        return map;
    }

    private int indexOfHolding(PortfolioSnapshot snapshot, String viewKey) {
        int byName = snapshot.names().indexOf(viewKey);
        if (byName >= 0) return byName;
        int byId = snapshot.indexOf(coinMarketDataService.resolveCoinId(viewKey));
        if (byId >= 0) return byId;
        throw new PortfolioDataException(PortfolioDataException.Reason.INVALID_VIEW,
                "You have a view on " + viewKey + ", but it is not in your portfolio.");
    }

    /**
     * Check if portfolio needs rebalancing: total absolute drift from the mean-variance target
     * for the given risk tolerance exceeds the threshold.
     */
    public boolean needsRebalancing(User user, double threshold, double riskTolerance) {
        return calculateOptimalAllocation(user, riskTolerance).calculateAllocationDrift() > threshold;
    }

    /**
     * Get rebalancing summary for dashboard
     */
    public Map<String, Object> getRebalancingSummary(User user, double riskTolerance, double threshold) {
        PortfolioRebalancing rebalancing = calculateOptimalAllocation(user, riskTolerance);
        Map<String, Object> summary = new HashMap<>();
        summary.put("needsRebalancing", rebalancing.isRebalancingNeeded(threshold));
        summary.put("currentRisk", rebalancing.getCurrentRisk());
        summary.put("targetRisk", rebalancing.getTargetRisk());
        summary.put("riskReduction", rebalancing.getRiskReduction());
        summary.put("returnImprovement", rebalancing.getReturnImprovement());
        summary.put("totalTransactionCost", rebalancing.getTotalTransactionCost());
        summary.put("portfolioStatus", rebalancing.getPortfolioStatus());
        summary.put("allocationDrift", rebalancing.calculateAllocationDrift());
        return summary;
    }

    /** Current value weights of the user's holdings. */
    public Map<String, Double> getCurrentAllocation(User user) {
        priceRefreshService.refreshQuietly(user);
        List<Asset> assets = assetService.getAssetsByUser(user);
        if (assets.isEmpty()) throw PortfolioDataException.noAssets();
        Map<String, Double> values = new LinkedHashMap<>();
        for (Asset a : assets) values.merge(a.getName(), a.getQuantity() * a.getPricePerUnit(), Double::sum);
        double total = values.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) throw PortfolioDataException.noAssets();
        values.replaceAll((k, v) -> v / total);
        return values;
    }
}
