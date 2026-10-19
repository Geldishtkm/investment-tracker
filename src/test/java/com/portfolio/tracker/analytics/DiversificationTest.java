package com.portfolio.tracker.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DiversificationTest {

    private static final double EPS = 1e-9;

    private static List<String> ids(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> "coin" + i).toList();
    }

    /** n assets sharing a common market factor with the given loading, plus independent noise. */
    private static double[][] factorReturns(int days, int n, double marketShare, long seed) {
        Random rnd = new Random(seed);
        double[][] r = new double[days][n];
        for (int t = 0; t < days; t++) {
            double market = rnd.nextGaussian();
            for (int i = 0; i < n; i++) {
                r[t][i] = 0.03 * (Math.sqrt(marketShare) * market + Math.sqrt(1 - marketShare) * rnd.nextGaussian());
            }
        }
        return r;
    }

    private static Diversification.Result analyze(double[] values, double[][] returns) {
        return Diversification.analyze(ids(values.length), ids(values.length), values, returns, 365, null);
    }

    @Nested
    @DisplayName("Concentration (HHI and effective number of constituents)")
    class Concentration {

        @Test
        @DisplayName("HHI is the sum of squared weights and N_eff its inverse")
        void hhi() {
            Diversification.Result r = analyze(new double[]{50, 30, 20}, null);
            assertEquals(0.25 + 0.09 + 0.04, r.hhi(), EPS);
            assertEquals(1.0 / 0.38, r.effectiveHoldings(), EPS);
        }

        @Test
        @DisplayName("Five coins with 90% in one count as about 1.2 holdings, not 5")
        void dominatedPortfolio() {
            Diversification.Result r = analyze(new double[]{90, 2.5, 2.5, 2.5, 2.5}, null);
            assertEquals(5, r.holdings());
            assertEquals(0.81 + 4 * 0.000625, r.hhi(), EPS);
            assertEquals(1.0 / 0.8125, r.effectiveHoldings(), EPS);
            assertTrue(r.effectiveHoldings() < 1.3);
            assertTrue(r.concentrationScore() < 0.25, "score " + r.concentrationScore());
        }

        @Test
        @DisplayName("Five equal holdings reach full concentration marks; one holding gets none")
        void bands() {
            assertEquals(1.0, analyze(new double[]{1, 1, 1, 1, 1}, null).concentrationScore(), EPS);
            Diversification.Result single = analyze(new double[]{1000}, null);
            assertEquals(0.0, single.concentrationScore(), EPS);
            assertEquals(1.0, single.effectiveBets(), EPS);
            assertEquals(0.0, single.score(), EPS);
        }

        @Test
        @DisplayName("Without history only concentration is scored, and the reason is given")
        void noHistory() {
            Diversification.Result r = Diversification.analyze(ids(2), ids(2), new double[]{1, 1}, null, 365, "No data.");
            assertFalse(r.correlationAvailable());
            assertEquals("No data.", r.correlationReason());
            assertNull(r.averageCorrelation());
            assertEquals(100.0 * r.concentrationScore(), r.score(), EPS);
        }

        @Test
        @DisplayName("A short history is too noisy to judge correlations")
        void shortHistory() {
            Diversification.Result r = analyze(new double[]{1, 1}, factorReturns(10, 2, 0.5, 1));
            assertFalse(r.correlationAvailable());
            assertTrue(r.correlationReason().contains("30 days"));
        }
    }

    @Nested
    @DisplayName("Correlation adjustment")
    class Correlation {

        @Test
        @DisplayName("Identical return series: ρ̄ = 1 and the portfolio is a single bet")
        void lockstep() {
            double[][] one = factorReturns(200, 1, 1.0, 3);
            double[][] r = new double[200][4];
            for (int t = 0; t < 200; t++) java.util.Arrays.fill(r[t], one[t][0]);
            Diversification.Result d = analyze(new double[]{1, 1, 1, 1}, r);
            assertEquals(1.0, d.averageCorrelation(), 1e-9);
            assertEquals(1.0, d.effectiveBets(), 1e-9);
            assertEquals(0.0, d.correlationScore(), EPS);
            assertEquals(0.0, d.betsScore(), EPS);
            assertEquals(4.0, d.effectiveHoldings(), EPS);
        }

        @Test
        @DisplayName("Uncorrelated equal-volatility holdings: independent bets ≈ N_eff")
        void uncorrelated() {
            Diversification.Result d = analyze(new double[]{1, 1, 1, 1}, factorReturns(3000, 4, 0.0, 5));
            assertEquals(0.0, d.averageCorrelation(), 0.05);
            assertEquals(4.0, d.effectiveBets(), 0.3);
            assertTrue(d.score() > 85, "score " + d.score());
        }

        @Test
        @DisplayName("Matches N_eff / (1 + (N_eff − 1)ρ) for a common correlation")
        void closedForm() {
            Diversification.Result d = analyze(new double[]{1, 1, 1, 1, 1}, factorReturns(5000, 5, 0.7, 11));
            double rho = d.averageCorrelation();
            assertEquals(0.7, rho, 0.03);
            assertEquals(5.0 / (1 + 4 * rho), d.effectiveBets(), 0.1);
        }

        @Test
        @DisplayName("Five lockstep layer-1 coins score below two uncorrelated holdings")
        void correlatedCoinsVsUncorrelatedPair() {
            Diversification.Result layer1s = analyze(new double[]{1, 1, 1, 1, 1}, factorReturns(365, 5, 0.85, 13));
            Diversification.Result pair = analyze(new double[]{1, 1}, factorReturns(365, 2, 0.0, 17));
            assertTrue(layer1s.effectiveHoldings() > pair.effectiveHoldings());
            assertTrue(layer1s.effectiveBets() < pair.effectiveBets(),
                    layer1s.effectiveBets() + " vs " + pair.effectiveBets());
            assertTrue(layer1s.correlationScore() < 0.15);
            assertTrue(layer1s.pairs().get(0).correlation() >= layer1s.pairs().get(layer1s.pairs().size() - 1).correlation());
        }

        @Test
        @DisplayName("A negatively correlated hedge is rewarded beyond an uncorrelated one")
        void negativeCorrelation() {
            Random rnd = new Random(19);
            double[][] hedged = new double[400][2], independent = new double[400][2];
            for (int t = 0; t < 400; t++) {
                double a = rnd.nextGaussian() * 0.03, b = rnd.nextGaussian() * 0.03;
                hedged[t][0] = a;
                hedged[t][1] = -0.6 * a + 0.8 * b;
                independent[t][0] = a;
                independent[t][1] = b;
            }
            Diversification.Result h = analyze(new double[]{1, 1}, hedged);
            Diversification.Result u = analyze(new double[]{1, 1}, independent);
            assertTrue(h.averageCorrelation() < -0.4);
            assertEquals(1.0, h.correlationScore(), EPS);
            assertTrue(h.effectiveBets() > u.effectiveBets());
            assertTrue(h.effectiveBets() > h.effectiveHoldings());
        }

        @Test
        @DisplayName("A position that never moves counts as uncorrelated, not as an error")
        void flatPosition() {
            double[][] r = factorReturns(100, 2, 0.0, 23);
            for (double[] row : r) row[1] = 0.0;
            Diversification.Result d = analyze(new double[]{1, 1}, r);
            assertEquals(0.0, d.averageCorrelation(), EPS);
            assertEquals(1.0, d.effectiveBets(), 1e-9); // all of the risk is in one position
            assertEquals(0.0, d.positions().get(1).volatility(), EPS);
        }
    }
}
