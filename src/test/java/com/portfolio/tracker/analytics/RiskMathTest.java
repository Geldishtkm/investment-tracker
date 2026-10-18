package com.portfolio.tracker.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiskMath numerical routines")
class RiskMathTest {

    private static final double[] SAMPLE = {2, 4, 4, 4, 5, 5, 7, 9};

    @Test
    @DisplayName("Inverse normal CDF matches standard z-scores for any confidence level")
    void inverseNormalCdf() {
        assertEquals(1.2815515655446006, RiskMath.inverseNormalCdf(0.90), 1e-8);
        assertEquals(1.6448536269514715, RiskMath.inverseNormalCdf(0.95), 1e-8);
        assertEquals(1.9599639845400534, RiskMath.inverseNormalCdf(0.975), 1e-8);
        assertEquals(2.3263478740408408, RiskMath.inverseNormalCdf(0.99), 1e-8);
        assertEquals(-RiskMath.inverseNormalCdf(0.99), RiskMath.inverseNormalCdf(0.01), 1e-12);
    }

    @Test
    @DisplayName("Sample moments match Excel STDEV.S, SKEW and KURT")
    void sampleMoments() {
        assertEquals(5.0, RiskMath.mean(SAMPLE), 1e-12);
        assertEquals(32.0 / 7.0, RiskMath.sampleVariance(SAMPLE), 1e-12);
        assertEquals(0.8184875533567997, RiskMath.skewness(SAMPLE), 1e-12);
        assertEquals(0.9406249999999998 + 3.0, RiskMath.kurtosis(SAMPLE), 1e-12);
    }

    @Test
    @DisplayName("Quantile uses linear interpolation (R type 7 / NumPy default)")
    void quantile() {
        double[] data = {10, 1, 9, 2, 8, 3, 7, 4, 6, 5};
        assertEquals(1.45, RiskMath.quantile(data, 0.05), 1e-12);
        assertEquals(5.5, RiskMath.quantile(data, 0.5), 1e-12);
        assertEquals(10.0, RiskMath.quantile(data, 1.0), 1e-12);
    }

    @Test
    @DisplayName("Covariance matrix is the unbiased sample covariance")
    void covarianceMatrix() {
        double[][] obs = {{1, 2}, {2, 4}, {3, 7}};
        double[][] cov = RiskMath.covarianceMatrix(obs);
        assertEquals(1.0, cov[0][0], 1e-12);
        assertEquals(2.5, cov[0][1], 1e-12);
        assertEquals(cov[0][1], cov[1][0], 1e-12);
        assertEquals(6.333333333333333, cov[1][1], 1e-12);
    }

    @Test
    @DisplayName("Max drawdown measures the deepest fall from a running peak")
    void maxDrawdown() {
        assertEquals(0.5, RiskMath.maxDrawdown(new double[]{100, 120, 90, 130, 65, 140}), 1e-12);
        assertEquals(0.0, RiskMath.maxDrawdown(new double[]{1, 2, 3}), 1e-12);
    }

    @Test
    @DisplayName("Cholesky factor and matrix inverse are exact on small matrices")
    void linearAlgebra() {
        double[][] l = RiskMath.cholesky(new double[][]{{4, 2}, {2, 3}});
        assertEquals(2.0, l[0][0], 1e-12);
        assertEquals(1.0, l[1][0], 1e-12);
        assertEquals(Math.sqrt(2), l[1][1], 1e-12);

        double[][] inv = RiskMath.invert(new double[][]{{4, 7}, {2, 6}});
        assertArrayEquals(new double[]{0.6, -0.7}, inv[0], 1e-12);
        assertArrayEquals(new double[]{-0.2, 0.4}, inv[1], 1e-12);
    }

    @Test
    @DisplayName("Cholesky handles a singular covariance (e.g. a stablecoin with zero variance)")
    void choleskySingular() {
        double[][] l = RiskMath.cholesky(new double[][]{{0.04, 0}, {0, 0}});
        assertEquals(0.2, l[0][0], 1e-9);
        assertTrue(l[1][1] >= 0);
    }

    @Test
    @DisplayName("Simplex projection gives long-only weights that sum to one")
    void simplexProjection() {
        assertArrayEquals(new double[]{1.0, 0.0}, RiskMath.projectOntoSimplex(new double[]{2, 0}), 1e-12);
        double[] w = RiskMath.projectOntoSimplex(new double[]{0.5, 0.5, 0.5});
        assertArrayEquals(new double[]{1 / 3.0, 1 / 3.0, 1 / 3.0}, w, 1e-12);
    }

    @Test
    @DisplayName("Minimum variance weights of uncorrelated assets are proportional to 1 / variance")
    void minimumVariance() {
        double[] w = RiskMath.minimumVariance(new double[][]{{0.04, 0}, {0, 0.01}});
        assertArrayEquals(new double[]{0.2, 0.8}, w, 1e-6);
    }

    @Test
    @DisplayName("Minimum variance matches the closed form for correlated assets")
    void minimumVarianceCorrelated() {
        // w1 = (s2^2 - c) / (s1^2 + s2^2 - 2c)
        double s1 = 0.09, s2 = 0.04, c = 0.018;
        double[] w = RiskMath.minimumVariance(new double[][]{{s1, c}, {c, s2}});
        double expected = (s2 - c) / (s1 + s2 - 2 * c);
        assertEquals(expected, w[0], 1e-6);
        assertEquals(1 - expected, w[1], 1e-6);
    }

    @Test
    @DisplayName("Risk tolerance walks the efficient frontier from minimum variance to maximum return")
    void efficientFrontier() {
        double[] mu = {0.30, 0.10};
        double[][] sigma = {{0.16, 0.01}, {0.01, 0.04}};

        double[] conservative = RiskMath.efficientFrontierPortfolio(mu, sigma, 0.0);
        double[] aggressive = RiskMath.efficientFrontierPortfolio(mu, sigma, 1.0);
        double[] moderate = RiskMath.efficientFrontierPortfolio(mu, sigma, 0.5);

        assertArrayEquals(RiskMath.minimumVariance(sigma), conservative, 1e-9);
        assertArrayEquals(new double[]{1.0, 0.0}, aggressive, 1e-12);

        double volMin = Math.sqrt(RiskMath.quadraticForm(conservative, sigma));
        double volMax = Math.sqrt(RiskMath.quadraticForm(aggressive, sigma));
        double volMid = Math.sqrt(RiskMath.quadraticForm(moderate, sigma));
        assertEquals(volMin + 0.5 * (volMax - volMin), volMid, 1e-6);
        assertEquals(1.0, moderate[0] + moderate[1], 1e-12);
        assertTrue(RiskMath.dot(moderate, mu) > RiskMath.dot(conservative, mu));
    }

    @Test
    @DisplayName("Unconstrained-interior utility optimum matches w = Sigma^-1 (mu - g 1) / lambda")
    void utilityMatchesClosedForm() {
        double[] mu = {0.12, 0.10};
        double[][] sigma = {{0.04, 0.006}, {0.006, 0.09}};
        double lambda = 3.0;
        double[] w = RiskMath.maximizeUtility(mu, sigma, lambda);

        // Lagrangian solution with the budget constraint, valid while both weights are positive
        double[][] inv = RiskMath.invert(sigma);
        double[] ones = {1, 1};
        double[] invMu = RiskMath.multiply(inv, mu);
        double[] invOnes = RiskMath.multiply(inv, ones);
        double gamma = (RiskMath.dot(ones, invMu) - lambda) / RiskMath.dot(ones, invOnes);
        double[] expected = new double[2];
        for (int i = 0; i < 2; i++) expected[i] = (invMu[i] - gamma * invOnes[i]) / lambda;

        assertArrayEquals(expected, w, 1e-8);
    }
}
