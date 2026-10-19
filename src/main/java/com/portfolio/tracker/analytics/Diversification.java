package com.portfolio.tracker.analytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Portfolio diversification measured two ways, as used in quantitative asset management:
 * <ul>
 *   <li><b>Concentration</b>: the Herfindahl–Hirschman index HHI = Σ wᵢ² of the value weights, and the
 *       effective number of constituents N_eff = 1 / HHI. Five coins with 90% of the capital in one
 *       give N_eff ≈ 1.2, not 5.</li>
 *   <li><b>Correlation</b>: the weighted average pairwise correlation ρ̄ = Σᵢ<ⱼ wᵢwⱼρᵢⱼ / Σᵢ<ⱼ wᵢwⱼ, and
 *       the effective number of independent bets N_ρ = DR², where DR = Σ wᵢσᵢ / σₚ is the
 *       diversification ratio (Choueifaty &amp; Coignard, 2008). For equal volatilities and a common
 *       correlation ρ this is N_eff / (1 + (N_eff − 1)ρ): it equals N_eff when holdings are
 *       uncorrelated, falls towards 1 as they move in lockstep, and rises above N_eff when they are
 *       negatively correlated.</li>
 * </ul>
 * Pure functions only, so the formulas can be tested directly.
 */
public final class Diversification {

    /** Fewer daily returns than this and correlations are too noisy to judge. */
    public static final int MIN_OBSERVATIONS = 30;
    /** A pair at or above this correlation moves in near-lockstep. */
    public static final double HIGH_CORRELATION = 0.80;

    // Score bands (0 at worst, 1 at best)
    static final double HHI_BEST = 0.20, HHI_WORST = 1.0;                  // N_eff 5 → full marks
    static final double CORRELATION_BEST = -0.20, CORRELATION_WORST = 0.90;
    static final double BETS_WORST = 1.0, BETS_BEST = 4.0;

    private Diversification() {
    }

    /** One position: weight is its share of the portfolio value, volatility annualized (null without history). */
    public record Position(String id, String name, double weight, Double volatility) {}

    public record Pair(String a, String b, double correlation, double combinedWeight) {}

    /**
     * @param correlationReason why the correlation measures are missing, or null when they are present
     * @param averageCorrelation    ρ̄, null when unavailable or with a single holding
     * @param effectiveBets         N_ρ = DR², null when unavailable
     * @param diversificationRatio  DR, null when unavailable
     * @param correlations          correlation matrix in position order, null when unavailable
     * @param pairs                 every pair, most correlated first
     * @param score                 0–100: the mean of the concentration, correlation and independent-bet scores
     *                              that have data
     */
    public record Result(int holdings, double hhi, double effectiveHoldings,
                         Double averageCorrelation, Double effectiveBets, Double diversificationRatio,
                         double concentrationScore, Double correlationScore, Double betsScore, double score,
                         List<Position> positions, double[][] correlations, List<Pair> pairs,
                         int observations, String correlationReason) {

        public boolean correlationAvailable() {
            return correlationReason == null;
        }
    }

    /**
     * @param ids             position identifiers
     * @param names           display names, same order
     * @param values          current market value per position (non-negative, positive total)
     * @param returns         returns[t][i] = daily return of position i, aligned across positions; null when no
     *                        history is available
     * @param periodsPerYear  periods used to annualize volatility
     * @param noHistoryReason shown when returns is null
     */
    public static Result analyze(List<String> ids, List<String> names, double[] values, double[][] returns,
                                 int periodsPerYear, String noHistoryReason) {
        int n = values.length;
        if (n == 0 || ids.size() != n || names.size() != n) {
            throw new IllegalArgumentException("Need matching ids, names and values for at least one position");
        }
        double total = 0.0;
        for (double v : values) total += v;
        if (!(total > 0)) throw new IllegalArgumentException("Portfolio value must be positive");

        double[] w = new double[n];
        double hhi = 0.0;
        for (int i = 0; i < n; i++) {
            w[i] = values[i] / total;
            hhi += w[i] * w[i];
        }
        double effectiveHoldings = 1.0 / hhi;
        double concentrationScore = 1.0 - lin(hhi, HHI_BEST, HHI_WORST);

        String reason = null;
        if (n == 1) {
            // Nothing to diversify against: one independent bet, and correlation is undefined
            List<Position> positions = List.of(new Position(ids.get(0), names.get(0), 1.0,
                    returns == null || returns.length < 2 ? null : stdDev(column(returns, 0)) * Math.sqrt(periodsPerYear)));
            double betsScore = 0.0;
            return new Result(1, 1.0, 1.0, null, 1.0, 1.0, concentrationScore, null, betsScore,
                    100.0 * (concentrationScore + betsScore) / 2.0, positions, null, List.of(),
                    returns == null ? 0 : returns.length, null);
        }
        if (returns == null) {
            reason = noHistoryReason != null ? noHistoryReason : "Price history for your holdings is unavailable.";
        } else if (returns.length < MIN_OBSERVATIONS) {
            reason = "Correlations need at least " + MIN_OBSERVATIONS + " days of shared price history; "
                    + returns.length + " available.";
        } else if (returns[0].length != n) {
            throw new IllegalArgumentException("Return columns don't match the positions");
        }

        if (reason != null) {
            List<Position> positions = new ArrayList<>();
            for (int i = 0; i < n; i++) positions.add(new Position(ids.get(i), names.get(i), w[i], null));
            return new Result(n, hhi, effectiveHoldings, null, null, null, concentrationScore, null, null,
                    100.0 * concentrationScore, positions, null, List.of(), returns == null ? 0 : returns.length, reason);
        }

        double[][] cov = RiskMath.covarianceMatrix(returns);
        double[] sigma = new double[n];
        for (int i = 0; i < n; i++) sigma[i] = Math.sqrt(Math.max(cov[i][i], 0.0));

        // A position whose price never moved (e.g. a pegged stablecoin) has no co-movement: correlation 0
        double[][] rho = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) rho[i][j] = 1.0;
                else if (sigma[i] > 0 && sigma[j] > 0) rho[i][j] = clamp(cov[i][j] / (sigma[i] * sigma[j]));
            }
        }

        double weightedSum = 0.0, pairWeight = 0.0;
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                weightedSum += w[i] * w[j] * rho[i][j];
                pairWeight += w[i] * w[j];
                pairs.add(new Pair(names.get(i), names.get(j), rho[i][j], w[i] + w[j]));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::correlation).reversed());
        Double averageCorrelation = pairWeight > 0 ? weightedSum / pairWeight : null;

        double weightedVol = 0.0;
        for (int i = 0; i < n; i++) weightedVol += w[i] * sigma[i];
        double portfolioVariance = RiskMath.quadraticForm(w, cov);
        Double ratio = null, bets = null;
        if (portfolioVariance > 0 && weightedVol > 0) {
            ratio = weightedVol / Math.sqrt(portfolioVariance);
            bets = ratio * ratio;
        }

        Double correlationScore = averageCorrelation == null ? null
                : 1.0 - lin(averageCorrelation, CORRELATION_BEST, CORRELATION_WORST);
        Double betsScore = bets == null ? null : lin(bets, BETS_WORST, BETS_BEST);
        double sum = concentrationScore;
        int parts = 1;
        if (correlationScore != null) { sum += correlationScore; parts++; }
        if (betsScore != null) { sum += betsScore; parts++; }

        List<Position> positions = new ArrayList<>();
        double annualize = Math.sqrt(periodsPerYear);
        for (int i = 0; i < n; i++) positions.add(new Position(ids.get(i), names.get(i), w[i], sigma[i] * annualize));
        return new Result(n, hhi, effectiveHoldings, averageCorrelation, bets, ratio,
                concentrationScore, correlationScore, betsScore, 100.0 * sum / parts,
                positions, rho, pairs, returns.length, null);
    }

    static double lin(double x, double worst, double best) {
        return Math.max(0.0, Math.min(1.0, (x - worst) / (best - worst)));
    }

    private static double clamp(double rho) {
        return Math.max(-1.0, Math.min(1.0, rho));
    }

    private static double[] column(double[][] m, int col) {
        double[] out = new double[m.length];
        for (int t = 0; t < m.length; t++) out[t] = m[t][col];
        return out;
    }

    private static double stdDev(double[] x) {
        return x.length < 2 ? 0.0 : RiskMath.sampleStdDev(x);
    }
}
