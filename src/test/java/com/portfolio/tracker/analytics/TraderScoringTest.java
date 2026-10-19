package com.portfolio.tracker.analytics;

import com.portfolio.tracker.analytics.TraderScoring.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Institutional Trader Index Score")
class TraderScoringTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
    private static final double EPS = 1e-9;

    // ---- fixtures ----

    private static Inputs inputs(List<Holding> holdings, double[] returns, double[] bench, List<TradeEvent> trades,
                                 Map<String, SortedMap<LocalDate, Double>> prices, Map<String, Double> volumes,
                                 Double riskTarget) {
        return new Inputs(holdings, returns, bench, returns == null ? "No price history." : null, trades, prices,
                Map.of(), volumes, riskTarget, 0.04, 365, TODAY);
    }

    /** The four pillar totals as displayed (rounded), added up. */
    private static int sumOfPillars(Evaluation e) {
        long sum = e.pillars().stream().filter(Pillar::available).mapToLong(p -> Math.round(p.points())).sum();
        return (int) Math.round(sum * 4.0 / e.pillarsScored());
    }

    private static Inputs holdingsOnly(List<Holding> holdings) {
        return inputs(holdings, null, null, List.of(), Map.of(), Map.of(), null);
    }

    private static TradeEvent buy(long id, String coin, double qty, double price, LocalDate date) {
        return new TradeEvent(id, coin, coin, Side.BUY, qty, price, date, true, false);
    }

    private static TradeEvent sell(long id, String coin, double qty, double price, LocalDate date) {
        return new TradeEvent(id, coin, coin, Side.SELL, qty, price, date, true, false);
    }

    private static TradeEvent imported(long id, String coin, double qty, double price) {
        return new TradeEvent(id, coin, coin, Side.BUY, qty, price, TODAY, false, true);
    }

    /** Daily prices: flat 100 except the given overrides. */
    private static SortedMap<LocalDate, Double> prices(Map<LocalDate, Double> overrides) {
        SortedMap<LocalDate, Double> p = new TreeMap<>();
        for (LocalDate d = TODAY.minusDays(365); !d.isAfter(TODAY); d = d.plusDays(1)) p.put(d, 100.0);
        p.putAll(overrides);
        return p;
    }

    /** Alternating returns around a mean, so volatility is exactly known and non-zero. */
    private static double[] returns(int n, double mean, double swing) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = mean + (i % 2 == 0 ? swing : -swing);
        return r;
    }

    private static Pillar pillar(Evaluation e, String key) {
        return e.pillars().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }

    private static Component component(Evaluation e, String key) {
        return e.pillars().stream().flatMap(p -> p.components().stream())
                .filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    private static Optional<Flag> flag(Evaluation e, String id) {
        return e.flags().stream().filter(f -> f.id().equals(id)).findFirst();
    }

    private static List<Holding> equalHoldings(int n) {
        List<Holding> h = new ArrayList<>();
        for (int i = 0; i < n; i++) h.add(new Holding("coin" + i, "Coin " + i, 1000));
        return h;
    }

    // ---- tiers ----

    @Nested
    @DisplayName("Rank tiers")
    class Tiers {
        @Test
        @DisplayName("Every boundary maps to the specified tier")
        void boundaries() {
            int[][] cases = {{1000, 1}, {900, 1}, {899, 2}, {825, 2}, {824, 3}, {750, 3}, {749, 4}, {675, 4},
                    {674, 5}, {600, 5}, {599, 6}, {525, 6}, {524, 7}, {450, 7}, {449, 8}, {375, 8},
                    {374, 9}, {300, 9}, {299, 10}, {0, 10}};
            for (int[] c : cases) assertEquals(c[1], TraderScoring.tierFor(c[0]).rank(), "score " + c[0]);
            assertEquals("Sovereign Quantitative Director", TraderScoring.tierFor(950).name());
            assertEquals("Reckless Liquidation Hazard", TraderScoring.tierFor(42).name());
        }

        @Test
        @DisplayName("Tiers cover 0–1000 without gaps or overlaps")
        void contiguous() {
            for (int s = 0; s <= 1000; s++) {
                final int score = s;
                assertEquals(1, TraderScoring.TIERS.stream()
                        .filter(t -> score >= t.minScore() && score <= t.maxScore()).count(), "score " + s);
            }
            assertThrows(IllegalArgumentException.class, () -> TraderScoring.tierFor(1001));
            assertThrows(IllegalArgumentException.class, () -> TraderScoring.tierFor(-1));
        }
    }

    @Test
    @DisplayName("Scoring bands clamp to 0–1 and treat NaN as 0")
    void bands() {
        assertEquals(0.0, TraderScoring.lin(-5, 0, 1));
        assertEquals(0.5, TraderScoring.lin(0.5, 0, 1), EPS);
        assertEquals(1.0, TraderScoring.lin(7, 0, 1));
        assertEquals(0.0, TraderScoring.lin(Double.NaN, 0, 1));
        assertEquals(1.0, TraderScoring.inv(0.1, 0.2, 1.0));
        assertEquals(0.0, TraderScoring.inv(1.5, 0.2, 1.0));
    }

    // ---- lots ----

    @Nested
    @DisplayName("FIFO lot matching")
    class Lots {
        @Test
        @DisplayName("A sell closes the oldest lots first, splitting a lot when needed")
        void fifo() {
            LotBook book = TraderScoring.matchLots(List.of(
                    buy(1, "btc", 1, 100, TODAY.minusDays(30)),
                    buy(2, "btc", 1, 200, TODAY.minusDays(20)),
                    sell(3, "btc", 1.5, 300, TODAY.minusDays(10))));

            assertEquals(2, book.closed().size());
            assertEquals(1.0, book.closed().get(0).quantity(), EPS);
            assertEquals(200.0, book.closed().get(0).pnl(), EPS);   // 1 × (300 − 100)
            assertEquals(0.5, book.closed().get(1).quantity(), EPS);
            assertEquals(50.0, book.closed().get(1).pnl(), EPS);    // 0.5 × (300 − 200)
            assertEquals(20, book.closed().get(0).holdDays());
            assertEquals(1, book.open().size());
            assertEquals(0.5, book.open().get(0).quantity(), EPS);
        }

        @Test
        @DisplayName("Imported holdings (unknown date) are the oldest lots, and their closes have no known hold time")
        void importedFirst() {
            LotBook book = TraderScoring.matchLots(List.of(
                    buy(2, "eth", 1, 500, TODAY.minusDays(5)),
                    imported(1, "eth", 1, 100),
                    sell(3, "eth", 1, 400, TODAY.minusDays(1))));

            assertEquals(100.0, book.closed().get(0).buyPrice(), EPS);
            assertFalse(book.closed().get(0).datesKnown());
        }

        @Test
        @DisplayName("Selling more than was bought is recorded as unmatched, not as a lot")
        void oversell() {
            LotBook book = TraderScoring.matchLots(List.of(
                    buy(1, "sol", 2, 10, TODAY.minusDays(3)),
                    sell(2, "sol", 3, 12, TODAY.minusDays(1))));
            assertEquals(1.0, book.unmatchedSellQuantity(), EPS);
            assertEquals(2.0, book.closed().get(0).quantity(), EPS);
        }
    }

    // ---- Pillar 1 ----

    @Nested
    @DisplayName("Risk Control & Tail Risk")
    class Risk {
        @Test
        @DisplayName("Sharpe, Sortino and volatility follow their formulas")
        void ratios() {
            double[] r = returns(200, 0.002, 0.02);
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(4), r, r, List.of(), Map.of(), Map.of(), null));

            double vol = RiskMath.sampleStdDev(r) * Math.sqrt(365);
            double annual = RiskMath.mean(r) * 365;
            // Sharpe takes the higher of the full and the last 30 days' volatility (no outliers to trim here)
            TraderScoring.SharpeInputs si = TraderScoring.sharpeInputs(r, 365);
            assertEquals(annual, si.annualReturn(), 1e-12);
            assertEquals(Math.max(vol, si.recentVolatility()), si.volatility(), 1e-12);
            assertEquals((annual - 0.04) / si.volatility(), component(e, "sharpe").value(), 1e-12);
            assertEquals(vol, component(e, "volatility").value(), 1e-12);

            double rfDaily = 0.04 / 365, sumSq = 0;
            for (double x : r) sumSq += Math.pow(Math.min(x - rfDaily, 0), 2);
            double downside = Math.sqrt(sumSq / r.length) * Math.sqrt(365);
            assertEquals((annual - 0.04) / downside, component(e, "sortino").value(), 1e-12);
        }

        @Test
        @DisplayName("Sharpe weighs recent volatility: a calm year then a volatile month raises the risk at once")
        void sharpeUsesRecentVolatility() {
            Random rnd = new Random(5);
            double[] r = new double[364];
            for (int t = 0; t < 364; t++) r[t] = 0.002 + rnd.nextGaussian() * (t < 334 ? 0.01 : 0.05);
            TraderScoring.SharpeInputs si = TraderScoring.sharpeInputs(r, 365);
            assertTrue(si.recentVolatility() > 2 * si.fullVolatility(), si.toString());
            assertEquals(si.recentVolatility(), si.volatility(), EPS);
            Component c = component(TraderScoring.evaluate(inputs(equalHoldings(4), r, r, List.of(), Map.of(), Map.of(), null)), "sharpe");
            assertEquals((si.annualReturn() - 0.04) / si.recentVolatility(), c.value(), 1e-9);
        }

        @Test
        @DisplayName("A one-day pump doesn't inflate the Sharpe ratio")
        void sharpeIgnoresOneDayPump() {
            double[] base = returns(364, 0.0005, 0.02);
            double[] pumped = base.clone();
            pumped[363] = 0.80;                                       // +80% on the last day
            double s0 = component(TraderScoring.evaluate(inputs(equalHoldings(4), base, base, List.of(), Map.of(), Map.of(), null)), "sharpe").value();
            double s1 = component(TraderScoring.evaluate(inputs(equalHoldings(4), pumped, base, List.of(), Map.of(), Map.of(), null)), "sharpe").value();
            assertTrue(Math.abs(s1 - s0) < 0.25, s0 + " → " + s1);
        }

        @Test
        @DisplayName("Full marks need a higher Sharpe the more volatile the portfolio: 2.0 up to 50%, 3.0 from 150%")
        void sharpeTargetScales() {
            assertEquals(2.0, TraderScoring.sharpeTarget(0.40), EPS);
            assertEquals(2.5, TraderScoring.sharpeTarget(1.00), EPS);
            assertEquals(3.0, TraderScoring.sharpeTarget(2.00), EPS);

            // A high-volatility coin pumping over its last week: nowhere near 60/60
            Random rnd = new Random(9);
            double[] r = new double[364];
            for (int t = 0; t < 364; t++) r[t] = t < 357 ? rnd.nextGaussian() * 0.06 : 0.12;
            Component c = component(TraderScoring.evaluate(inputs(equalHoldings(1), r, r, List.of(), Map.of(), Map.of(), null)), "sharpe");
            assertTrue(c.points() < 40, c.points() + " · " + c.displayValue());
            assertTrue(c.displayValue().contains("target"), c.displayValue());
        }

        @Test
        @DisplayName("A flat return series leaves the ratios unscored instead of dividing by zero")
        void zeroVolatility() {
            double[] flat = new double[120];
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(4), flat, flat, List.of(), Map.of(), Map.of(), null));
            assertFalse(component(e, "sharpe").available());
            assertFalse(component(e, "sortino").available());
            assertFalse(component(e, "volatility").available());
            assertFalse(component(e, "benchmark-beta").available()); // the benchmark never moved either
            for (Pillar p : e.pillars()) assertFalse(Double.isNaN(p.points()), p.key());
        }

        @Test
        @DisplayName("A single holding is maximally concentrated and flagged HIGH")
        void singleHolding() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(List.of(new Holding("eth", "Ethereum", 5000))));
            assertEquals(1.0, component(e, "concentration").value(), EPS);
            assertEquals(0.0, component(e, "concentration").score(), EPS);
            Flag f = flag(e, "concentration").orElseThrow();
            assertEquals(Severity.HIGH, f.severity());
            assertTrue(f.detail().contains("100.0% of the portfolio is in Ethereum"), f.detail());
        }

        @Test
        @DisplayName("Five equal holdings reach the best concentration band")
        void diversified() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(equalHoldings(5)));
            assertEquals(0.2, component(e, "concentration").value(), EPS);
            assertEquals(1.0, component(e, "concentration").score(), EPS);
            assertTrue(flag(e, "concentration").isEmpty());
        }

        /** n holdings driven by one market factor with the given share of variance; returns per holding and portfolio. */
        private Inputs correlatedInputs(int n, double marketShare, long seed) {
            Random rnd = new Random(seed);
            double[][] assets = new double[365][n];
            double[] portfolio = new double[365], bench = new double[365];
            for (int t = 0; t < 365; t++) {
                double market = rnd.nextGaussian();
                bench[t] = 0.03 * market;
                for (int i = 0; i < n; i++) {
                    assets[t][i] = 0.001 + 0.03 * (Math.sqrt(marketShare) * market + Math.sqrt(1 - marketShare) * rnd.nextGaussian());
                    portfolio[t] += assets[t][i] / n;
                }
            }
            return new Inputs(equalHoldings(n), portfolio, bench, null, List.of(), Map.of(), Map.of(), Map.of(), null,
                    0.04, 365, TODAY, assets, Map.of());
        }

        @Test
        @DisplayName("Diversification is worth 60 of the 250 risk points, split across three components")
        void diversificationWeights() {
            Evaluation e = TraderScoring.evaluate(correlatedInputs(4, 0.3, 29));
            double total = pillar(e, "risk").components().stream().mapToDouble(Component::weight).sum();
            assertEquals(250.0, total, EPS);
            assertEquals(20.0, component(e, "concentration").weight(), EPS);
            assertEquals(20.0, component(e, "correlation").weight(), EPS);
            assertEquals(20.0, component(e, "independent-bets").weight(), EPS);
            assertNotNull(e.diversification());
            assertEquals(4, e.diversification().holdings());
            assertEquals(6, e.diversification().pairs().size());
        }

        @Test
        @DisplayName("Holdings that move in lockstep lose risk points and are flagged, despite equal weights")
        void correlatedHoldingsPenalized() {
            Evaluation lockstep = TraderScoring.evaluate(correlatedInputs(5, 0.9, 31));
            Evaluation independent = TraderScoring.evaluate(correlatedInputs(5, 0.05, 31));
            assertEquals(1.0, component(lockstep, "concentration").score(), EPS); // same weights...
            assertTrue(component(lockstep, "correlation").score() < 0.1);       // ...but no real diversification
            assertTrue(component(lockstep, "independent-bets").value() < 1.3);
            assertTrue(component(independent, "independent-bets").value() > 3.5);
            assertTrue(component(lockstep, "correlation").points() + component(lockstep, "independent-bets").points()
                    < component(independent, "correlation").points() + component(independent, "independent-bets").points() - 25);
            Flag f = flag(lockstep, "correlated-holdings").orElseThrow();
            assertEquals(Severity.HIGH, f.severity());
            assertTrue(f.detail().contains("independent bet"), f.detail());
            assertTrue(flag(independent, "correlated-holdings").isEmpty());
        }

        @Test
        @DisplayName("Lockstep coins are flagged even when a small uncorrelated holding lowers the average")
        void lockstepBehindStablecoin() {
            Random rnd = new Random(37);
            double[][] assets = new double[365][4];
            double[] portfolio = new double[365];
            double[] w = {0.30, 0.30, 0.30, 0.10};
            for (int t = 0; t < 365; t++) {
                double market = rnd.nextGaussian();
                for (int i = 0; i < 3; i++) assets[t][i] = 0.03 * (Math.sqrt(0.85) * market + Math.sqrt(0.15) * rnd.nextGaussian());
                assets[t][3] = 0.0005 * rnd.nextGaussian(); // a stablecoin
                for (int i = 0; i < 4; i++) portfolio[t] += w[i] * assets[t][i];
            }
            List<Holding> holdings = List.of(new Holding("btc", "Bitcoin", 300), new Holding("eth", "Ethereum", 300),
                    new Holding("sol", "Solana", 300), new Holding("usdc", "USDC", 100));
            Evaluation e = TraderScoring.evaluate(new Inputs(holdings, portfolio, portfolio, null,
                    List.of(), Map.of(), Map.of(), Map.of(), null, 0.04, 365, TODAY, assets, Map.of()));
            assertTrue(e.diversification().averageCorrelation() < 0.7, "ρ̄ " + e.diversification().averageCorrelation());
            Flag f = flag(e, "correlated-holdings").orElseThrow();
            assertTrue(f.detail().contains("90% of the capital is in holdings that move in lockstep"), f.detail());
            assertEquals(Severity.HIGH, f.severity());
        }

        @Test
        @DisplayName("A single holding: correlation is undefined and left out; it is one independent bet")
        void singleHoldingDiversification() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(List.of(new Holding("eth", "Ethereum", 5000))));
            assertFalse(component(e, "correlation").available(), "no fixed zero for an undefined metric");
            assertEquals("Needs two or more holdings.", component(e, "correlation").detail());
            assertEquals(1.0, component(e, "independent-bets").value(), EPS);
            assertEquals(0.0, component(e, "independent-bets").points(), EPS); // 1 bet is the bottom of the band
        }

        @Test
        @DisplayName("Fewer than 60 daily returns: volatility, Sharpe, Sortino and beta are left out, not annualized from noise")
        void shortHistoryLeftOut() {
            double[] few = new double[6];
            for (int i = 0; i < few.length; i++) few[i] = i % 2 == 0 ? 0.20 : -0.18;   // would annualize to ~370%
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(3), few, few, List.of(), Map.of(), Map.of(), null));
            for (String key : List.of("sharpe", "sortino", "volatility")) {
                Component c = component(e, key);
                assertFalse(c.available(), key);
                assertTrue(c.detail().contains("at least 60 daily returns"), c.detail());
            }
            assertTrue(flag(e, "high-volatility").isEmpty(), "no 'astronomical volatility' flag from 6 days");
            // The 7-day beta needs only the latest week, so a new account gets it straight away
            assertTrue(component(e, "benchmark-beta").available());
            assertEquals(1.0, component(e, "benchmark-beta").value(), 1e-9);
        }

        @Test
        @DisplayName("7-day beta: within ±1 scores in full, falling smoothly to zero at ±2.5; uses BTC/ETH when given")
        void benchmarkBeta() {
            double[] market = returns(120, 0.0, 0.02);
            double[] calm = new double[120], amplified = new double[120];
            for (int t = 0; t < 120; t++) {
                calm[t] = 0.8 * market[t];
                amplified[t] = 2.0 * market[t];
            }
            Evaluation a = TraderScoring.evaluate(inputs(equalHoldings(4), calm, market, List.of(), Map.of(), Map.of(), null));
            Component beta = component(a, "benchmark-beta");
            assertEquals(0.8, beta.value(), 1e-9);
            assertEquals(1.0, beta.score(), EPS);
            assertTrue(beta.displayValue().endsWith("7d vs BTC"), beta.displayValue());
            assertEquals(25.0, beta.weight(), EPS);

            Evaluation b = TraderScoring.evaluate(inputs(equalHoldings(4), amplified, market, List.of(), Map.of(), Map.of(), null));
            assertEquals(2.0, component(b, "benchmark-beta").value(), 1e-9);
            assertEquals(1.0 - 1.0 / 1.5, component(b, "benchmark-beta").score(), 1e-9);   // continuous, not 0
            assertTrue(flag(b, "high-beta").isPresent());

            // Only the last 7 days count, and the BTC/ETH series is used when supplied
            double[] recent = Arrays.copyOfRange(market, 113, 120);
            Inputs withEth = new Inputs(equalHoldings(4), calm, market, null, List.of(), Map.of(), Map.of(), Map.of(), null,
                    0.04, 365, TODAY, null, Map.of(), null, recent);
            Component c = component(TraderScoring.evaluate(withEth), "benchmark-beta");
            assertTrue(c.displayValue().endsWith("7d vs BTC/ETH"), c.displayValue());
            assertFalse(c.displayValue().contains("±"), "no multi-month error margin");
        }

        @Test
        @DisplayName("Without per-holding returns the correlation metrics are left out, not guessed")
        void correlationNeedsHistory() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(equalHoldings(3)));
            assertFalse(component(e, "correlation").available());
            assertFalse(component(e, "independent-bets").available());
            assertTrue(component(e, "concentration").available());
            assertFalse(e.diversification().correlationAvailable());
        }

        @Test
        @DisplayName("Volatility is scored on a smooth curve, and one-day outliers don't blow it up")
        void volatilityCurve() {
            assertEquals(1.0, TraderScoring.volatilityScore(0.20), EPS);
            assertEquals(Math.exp(-Math.pow(0.25 / 0.6, 2)), TraderScoring.volatilityScore(0.50), 1e-12);
            assertTrue(TraderScoring.volatilityScore(0.85) > 0.3 && TraderScoring.volatilityScore(0.85) < 0.4);
            assertTrue(TraderScoring.volatilityScore(1.5) < 0.02);
            for (double v = 0.2; v < 2.0; v += 0.05) {
                assertTrue(TraderScoring.volatilityScore(v + 0.05) <= TraderScoring.volatilityScore(v), "monotone at " + v);
            }

            double[] r = returns(200, 0.0, 0.02);
            double[] spiked = r.clone();
            spiked[100] = 3.0;                                     // a one-day +300% data glitch
            Evaluation clean = TraderScoring.evaluate(inputs(equalHoldings(4), r, r, List.of(), Map.of(), Map.of(), null));
            Evaluation glitch = TraderScoring.evaluate(inputs(equalHoldings(4), spiked, r, List.of(), Map.of(), Map.of(), null));
            double v0 = component(clean, "volatility").value(), v1 = component(glitch, "volatility").value();
            assertTrue(v1 < v0 * 1.2, v0 + " → " + v1);           // without trimming it would be several times higher
        }
    }

    // ---- Pillar 2 ----

    @Nested
    @DisplayName("Behavioral Psychology")
    class Behavior {
        @Test
        @DisplayName("Disposition effect: losers held 3.2x longer than winners is flagged with that ratio")
        void disposition() {
            LocalDate s = TODAY.minusDays(100);
            List<TradeEvent> trades = List.of(
                    buy(1, "btc", 1, 100, s), sell(2, "btc", 1, 150, s.plusDays(10)),        // winner, 10 days
                    buy(3, "eth", 1, 100, s), sell(4, "eth", 1, 60, s.plusDays(32)));        // loser, 32 days
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of(), Map.of(), null));

            Component c = component(e, "disposition");
            assertEquals(3.2, c.value(), EPS);
            assertEquals(0.0, c.score(), EPS); // 3.2x is beyond the 3x worst band edge
            Flag f = flag(e, "disposition").orElseThrow();
            assertEquals("Disposition Effect Flagged", f.title());
            assertTrue(f.detail().startsWith("Losers held 3.2x longer than winners"), f.detail());
            assertEquals(Severity.HIGH, f.severity());
        }

        @Test
        @DisplayName("Disposition scores linearly between 1x (best) and 3x (worst)")
        void dispositionBand() {
            LocalDate s = TODAY.minusDays(100);
            List<TradeEvent> trades = List.of(
                    buy(1, "btc", 1, 100, s), sell(2, "btc", 1, 150, s.plusDays(10)),
                    buy(3, "eth", 1, 100, s), sell(4, "eth", 1, 60, s.plusDays(20)));     // 2.0x
            Component c = component(TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of(), Map.of(), null)), "disposition");
            assertEquals(2.0, c.value(), EPS);
            assertEquals(0.5, c.score(), EPS);
            assertEquals(37.5, c.points(), EPS);
        }

        @Test
        @DisplayName("Disposition needs both a winning and a losing sell with known dates")
        void dispositionNeedsBoth() {
            LocalDate s = TODAY.minusDays(50);
            List<TradeEvent> onlyWinners = List.of(buy(1, "btc", 1, 100, s), sell(2, "btc", 1, 150, s.plusDays(5)));
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, onlyWinners, Map.of(), Map.of(), null));
            assertFalse(component(e, "disposition").available());

            List<TradeEvent> importedLoser = List.of(imported(1, "eth", 1, 100), sell(2, "eth", 1, 50, TODAY.minusDays(2)),
                    buy(3, "btc", 1, 100, s), sell(4, "btc", 1, 150, s.plusDays(5)));
            e = TraderScoring.evaluate(inputs(List.of(), null, null, importedLoser, Map.of(), Map.of(), null));
            assertFalse(component(e, "disposition").available(), "a lot with an unknown buy date has no hold time");
        }

        /** One dated buy of 10% of a $10,000 bankroll at the given price, the day after a $100 close. */
        private Evaluation singleBuy(double price, double qty) {
            Map<LocalDate, Double> overrides = new HashMap<>();
            overrides.put(TODAY.minusDays(11), 100.0);
            List<TradeEvent> trades = List.of(buy(1, "btc", qty, price, TODAY.minusDays(10)));
            return TraderScoring.evaluate(new Inputs(List.of(), null, null, "No price history.", trades,
                    Map.of("btc", prices(overrides)), Map.of(), Map.of(), null, 0.04, 365, TODAY, null, Map.of(), 10_000.0));
        }

        @Test
        @DisplayName("FOMO bands: a clean entry costs nothing; one full-size chase in a short history is a graduated deduction")
        void fomoBands() {
            assertEquals(50.0, component(singleBuy(104, 10), "fomo").points(), 1e-9);            // +4%: clean
            assertEquals(37.5, component(singleBuy(110, 1000.0 / 110), "fomo").points(), 1e-9);  // +10%, 10% of capital
            Evaluation pump = singleBuy(130, 1000.0 / 130);                                        // +30%, 10% of capital
            assertEquals(25.0, component(pump, "fomo").points(), 1e-9);                           // not 0 for one chase
            Flag f = flag(pump, "fomo").orElseThrow();
            assertEquals(Severity.MEDIUM, f.severity());
            assertEquals("Severe FOMO Chase", f.title());
            assertTrue(f.detail().contains("after a +30.0% run-up in 24 hours (previous day's close)"), f.detail());
            assertEquals(1.0, TraderScoring.fomoBandScore(0.05), EPS);
            assertEquals(0.5, TraderScoring.fomoBandScore(0.15), EPS);
            assertEquals(0.0, TraderScoring.fomoBandScore(0.1501), EPS);
        }

        @Test
        @DisplayName("A chase costs in proportion to its size: 1% of capital barely, 10% or more in full")
        void fomoSizeWeighted() {
            assertEquals(47.5, component(singleBuy(130, 100.0 / 130), "fomo").points(), 1e-6);  // 1% into +30%
            assertEquals(37.5, component(singleBuy(130, 500.0 / 130), "fomo").points(), 1e-6);  // 5% into +30%
            assertEquals(25.0, component(singleBuy(130, 2500.0 / 130), "fomo").points(), 1e-6); // 25% (counts as full size)
            assertEquals(Severity.LOW, flag(singleBuy(130, 100.0 / 130), "fomo").orElseThrow().severity());
        }

        /** 11 dated buys of 10% of capital each: `severe` of them bought into a +30% run-up, the rest at the prior close. */
        private Component elevenBuys(int severe) {
            Map<LocalDate, Double> closes = new HashMap<>();
            List<TradeEvent> trades = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                LocalDate day = TODAY.minusDays(100 - 7L * i);
                closes.put(day.minusDays(1), 100.0);
                double price = i < severe ? 130 : 100;
                trades.add(buy(i, "btc", 1000.0 / price, price, day));
            }
            return component(TraderScoring.evaluate(new Inputs(List.of(), null, null, "No price history.", trades,
                    Map.of("btc", prices(closes)), Map.of(), Map.of(), null, 0.04, 365, TODAY, null, Map.of(), 10_000.0)), "fomo");
        }

        @Test
        @DisplayName("Proportional: 1 full-size severe chase in 11 buys scores about 38/50; only repeated large chases reach 0")
        void fomoProportional() {
            assertEquals(50.0, elevenBuys(0).points(), 1e-6);
            assertEquals(50 * (1 - 2.5 / 11), elevenBuys(1).points(), 1e-6);   // ≈ 38.6
            assertTrue(elevenBuys(1).points() >= 35 && elevenBuys(1).points() <= 40);
            assertEquals(50 * (1 - 2.5 * 2 / 11), elevenBuys(2).points(), 1e-6); // ≈ 27.3
            assertTrue(elevenBuys(4).points() > 0, "four is heavy but not yet total");
            assertEquals(0.0, elevenBuys(5).points(), 1e-6);                     // a repeated pattern
            for (int k = 0; k < 6; k++) assertTrue(elevenBuys(k + 1).points() <= elevenBuys(k).points(), "monotone at " + k);
        }

        @Test
        @DisplayName("Hourly prices catch an intraday pump that daily closes miss, at the moment the buy was placed")
        void fomoUsesExecutionTime() {
            java.time.Instant placed = TODAY.atTime(15, 0).toInstant(java.time.ZoneOffset.UTC);
            NavigableMap<java.time.Instant, Double> hourly = new TreeMap<>();
            for (int h = 168; h >= 0; h--) {
                java.time.Instant at = placed.minus(java.time.Duration.ofHours(h));
                hourly.put(at, h > 3 ? 100.0 : 130.0);   // flat, then a +30% spike in the last 3 hours
            }
            TradeEvent pumpBuy = new TradeEvent(1, "btc", "btc", Side.BUY, 1000.0 / 130, 130, TODAY, true, false, placed);
            // Daily closes don't show it: yesterday's close was 130 too (as if the spike were already in the close)
            Map<LocalDate, Double> overrides = new HashMap<>();
            overrides.put(TODAY.minusDays(1), 130.0);
            SortedMap<LocalDate, Double> daily = prices(overrides);
            daily.remove(TODAY); // and today's close doesn't exist yet
            Inputs in = new Inputs(List.of(), null, null, "No price history.", List.of(pumpBuy), Map.of("btc", daily),
                    Map.of(), Map.of(), null, 0.04, 365, TODAY, null, Map.of(), 10_000.0, null, Map.of("btc", hourly));
            Evaluation e = TraderScoring.evaluate(in);
            assertEquals(25.0, component(e, "fomo").points(), 1e-9);   // one full-size severe chase
            assertTrue(flag(e, "fomo").orElseThrow().detail().contains("+30.0% run-up in 24 hours (hourly prices)"));

            // Without hourly data the same buy falls back to the previous close and sees no run-up
            Inputs daily0 = new Inputs(List.of(), null, null, "No price history.", List.of(pumpBuy), Map.of("btc", daily),
                    Map.of(), Map.of(), null, 0.04, 365, TODAY, null, Map.of(), 10_000.0);
            assertEquals(50.0, component(TraderScoring.evaluate(daily0), "fomo").points(), 1e-9);
        }

        @Test
        @DisplayName("One buy is enough to judge; without price history around it, FOMO is left out")
        void fomoNeedsTrades() {
            assertTrue(component(singleBuy(104, 10), "fomo").available());
            List<TradeEvent> trades = List.of(buy(1, "btc", 1, 100, TODAY.minusDays(400)));
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of("btc", prices(Map.of())), Map.of(), null));
            assertFalse(component(e, "fomo").available());
        }

        @Test
        @DisplayName("Panic: selling at the low of a −10% day counts; selling into a fall that kept going doesn't")
        void panic() {
            LocalDate d1 = TODAY.minusDays(60), d2 = TODAY.minusDays(30);
            Map<LocalDate, Double> o = new HashMap<>();
            o.put(d1, 88.0);                       // −12% in 24h, the lowest price within ±3 days
            o.put(d2, 90.0);                       // −10% in 24h...
            o.put(d2.plusDays(1), 80.0);           // ...but it fell further: not the trough
            o.put(d2.plusDays(2), 75.0);
            Map<String, SortedMap<LocalDate, Double>> p = Map.of("btc", prices(o));
            List<TradeEvent> trades = List.of(imported(1, "btc", 2, 50), sell(2, "btc", 1, 88, d1), sell(3, "btc", 1, 90, d2));

            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades, p, Map.of(), null));
            assertEquals(0.5, component(e, "panic").value(), EPS);
            assertEquals(Severity.HIGH, flag(e, "panic").orElseThrow().severity());
        }

        @Test
        @DisplayName("Imported holdings never count as FOMO buys")
        void importedNotBehavioral() {
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null,
                    List.of(imported(1, "btc", 1, 100), imported(2, "eth", 1, 100)), Map.of("btc", prices(Map.of())), Map.of(), null));
            assertFalse(component(e, "fomo").available());
            assertFalse(pillar(e, "behavior").available());
        }
    }

    // ---- Pillar 3 ----

    @Nested
    @DisplayName("Consistency & Return Quality")
    class Consistency {
        @Test
        @DisplayName("Return on bankroll adds realized and unrealized P&L over all capital bought")
        void bankroll() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 2, 100, TODAY.minusDays(20)), sell(2, "btc", 1, 150, TODAY.minusDays(5)));
            Inputs in = new Inputs(List.of(), null, null, null, trades, Map.of(), Map.of("btc", 130.0), Map.of(),
                    null, 0.04, 365, TODAY);
            Component c = component(TraderScoring.evaluate(in), "bankroll-return");
            // S0 = 200; realized 50; unrealized 1 × (130 − 100) = 30
            assertEquals(80.0 / 200.0, c.value(), EPS);
            assertEquals("+40.0%", c.displayValue());
        }

        @Test
        @DisplayName("Profit factor = gross profit / gross loss; no losses scores the top band")
        void profitFactor() {
            LocalDate s = TODAY.minusDays(40);
            List<TradeEvent> mixed = List.of(buy(1, "btc", 1, 100, s), sell(2, "btc", 1, 400, s.plusDays(1)),
                    buy(3, "eth", 1, 100, s), sell(4, "eth", 1, 0.01, s.plusDays(1)));
            Component pf = component(TraderScoring.evaluate(inputs(List.of(), null, null, mixed, Map.of(), Map.of(), null)), "profit-factor");
            assertEquals(300.0 / 99.99, pf.value(), 1e-9);
            assertEquals(1.0, pf.score(), EPS);

            List<TradeEvent> allWins = List.of(buy(1, "btc", 2, 100, s), sell(2, "btc", 1, 150, s.plusDays(1)),
                    sell(3, "btc", 1, 160, s.plusDays(2)));
            Component inf = component(TraderScoring.evaluate(inputs(List.of(), null, null, allWins, Map.of(), Map.of(), null)), "profit-factor");
            assertNull(inf.value());
            assertEquals(1.0, inf.score(), EPS);
            assertTrue(inf.displayValue().startsWith("∞"));
        }

        @Test
        @DisplayName("Profit factor needs at least two sells")
        void profitFactorNeedsSells() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 1, 100, TODAY.minusDays(9)), sell(2, "btc", 1, 50, TODAY.minusDays(1)));
            assertFalse(component(TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of(), Map.of(), null)),
                    "profit-factor").available());
        }

        @Test
        @DisplayName("Equity curve: steady growth has R² = 1; a steady fall scores 0 and is flagged")
        void smoothness() {
            double[] up = new double[100], down = new double[100];
            Arrays.fill(up, 0.004);
            Arrays.fill(down, -0.004);
            assertEquals(1.0, TraderScoring.equityCurveFit(up).orElseThrow()[1], 1e-12);

            Evaluation rising = TraderScoring.evaluate(inputs(equalHoldings(4), up, up, List.of(), Map.of(), Map.of(), null));
            assertEquals(1.0, component(rising, "smoothness").score(), EPS);
            Evaluation falling = TraderScoring.evaluate(inputs(equalHoldings(4), down, down, List.of(), Map.of(), Map.of(), null));
            assertEquals(0.0, component(falling, "smoothness").score(), EPS);
            assertEquals("Falling Equity Curve", flag(falling, "equity-curve").orElseThrow().title());
        }
    }

    // ---- Pillar 4 ----

    @Nested
    @DisplayName("Position sizing")
    class PositionSizing {
        private static final double LARGE_CAP = 500e9, MICRO = 40e6;

        /** A portfolio of BTC (large cap) plus the given trades, with market caps and listings supplied. */
        private Inputs sized(List<TradeEvent> trades, Map<String, Double> caps, Map<String, SortedMap<LocalDate, Double>> prices) {
            Map<String, Double> volumes = new HashMap<>();
            for (TradeEvent t : trades) volumes.put(t.coinId(), 1e12); // listed, and too liquid for a liquidity flag
            return new Inputs(List.of(), null, null, "No price history.", trades, prices, Map.of(), volumes, null,
                    0.04, 365, TODAY, null, caps);
        }

        /** Like sized, with a starting bankroll S₀. */
        private Inputs withBankroll(double s0, List<TradeEvent> trades, Map<String, Double> caps) {
            Map<String, Double> volumes = new HashMap<>();
            for (TradeEvent t : trades) volumes.put(t.coinId(), 1e12);
            return new Inputs(List.of(), null, null, "No price history.", trades, Map.of(), Map.of(), volumes, null,
                    0.04, 365, TODAY, null, caps, s0);
        }

        @Test
        @DisplayName("Position sizing curve: ≤15% → 25 pts, 30% → 15, 50% → 5, above 50% → 0, linear in between")
        void positionSizingCurve() {
            assertEquals(25.0, 25 * TraderScoring.positionSizingScore(0.10), EPS);
            assertEquals(25.0, 25 * TraderScoring.positionSizingScore(0.15), EPS);
            assertEquals(20.0, 25 * TraderScoring.positionSizingScore(0.225), 1e-9);
            assertEquals(15.0, 25 * TraderScoring.positionSizingScore(0.30), 1e-9);
            assertEquals(10.0, 25 * TraderScoring.positionSizingScore(0.40), 1e-9);
            assertEquals(5.0, 25 * TraderScoring.positionSizingScore(0.50), 1e-9);
            assertEquals(0.0, 25 * TraderScoring.positionSizingScore(0.51), EPS);
        }

        @Test
        @DisplayName("Position sizing reads the largest open position; a speculative coin counts double")
        void positionSizingFromHoldings() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 0.03, 80_000, TODAY.minusDays(30)), buy(2, "pepe", 1_200, 1, TODAY.minusDays(20)));
            List<Holding> holdings = List.of(new Holding("btc", "btc", 2_400), new Holding("pepe", "pepe", 1_200));
            Inputs in = new Inputs(holdings, null, null, "No price history.", trades, Map.of(), Map.of(),
                    Map.of("btc", 1e12, "pepe", 1e12), null, 0.04, 365, TODAY, null, Map.of("btc", LARGE_CAP, "pepe", MICRO), 10_000.0);
            Component c = component(TraderScoring.evaluate(in), "position-sizing");
            assertEquals(0.24, c.value(), 1e-9);                     // BTC: 2,400 of 10,000
            assertEquals(TraderScoring.positionSizingScore(0.24), c.score(), 1e-12);

            List<Holding> specHeavy = List.of(new Holding("btc", "btc", 1_000), new Holding("pepe", "pepe", 2_000));
            Inputs spec = new Inputs(specHeavy, null, null, "No price history.", trades, Map.of(), Map.of(),
                    Map.of("btc", 1e12, "pepe", 1e12), null, 0.04, 365, TODAY, null, Map.of("btc", LARGE_CAP, "pepe", MICRO), 10_000.0);
            Component s2 = component(TraderScoring.evaluate(spec), "position-sizing");
            assertEquals(0.20, s2.value(), 1e-9);
            assertEquals(TraderScoring.positionSizingScore(0.40), s2.score(), 1e-12); // 20% speculative counts as 40%
            assertTrue(s2.displayValue().contains("counts double"));
        }

        @Test
        @DisplayName("Impact: dust counts 0, 10% counts 1, and a speculative trade escalates by 1 per extra 5%")
        void impactScale() {
            assertEquals(0.0, TraderScoring.impact(0.005, true), EPS);
            assertEquals(0.0, TraderScoring.impact(0.01, true), EPS);
            assertEquals(0.5, TraderScoring.impact(0.055, false), EPS);
            assertEquals(1.0, TraderScoring.impact(0.10, true), EPS);
            assertEquals(1.0, TraderScoring.impact(0.25, false), EPS);
            assertEquals(3.0, TraderScoring.impact(0.20, true), EPS);
            assertEquals(4.0, TraderScoring.impact(0.25, true), EPS);
            assertEquals(4.0, TraderScoring.impact(0.90, true), EPS);
        }

        @Test
        @DisplayName("$5 of a micro-cap on a $10,000 portfolio is dust: no penalty")
        void dustMicroCap() {
            List<TradeEvent> trades = List.of(buy(2, "pepe", 5, 1, TODAY.minusDays(10)));
            Evaluation e = TraderScoring.evaluate(withBankroll(10_000, trades, Map.of("pepe", MICRO)));
            assertFalse(component(e, "position-sizing").available()); // no open positions in this input
            assertEquals(0.0, component(e, "speculative-accumulation").value(), EPS);
            assertEquals(1.0, component(e, "speculative-accumulation").score(), EPS);
            assertTrue(flag(e, "position-sizing").isEmpty());
        }

        @Test
        @DisplayName("$20 of the same micro-cap on a $100 bankroll is a 20% speculative stake: severe penalties")
        void oversizedMicroCap() {
            List<TradeEvent> trades = List.of(buy(2, "pepe", 20, 1, TODAY.minusDays(10)));
            Evaluation e = TraderScoring.evaluate(withBankroll(100, trades, Map.of("pepe", MICRO)));
            assertEquals(3.0, component(e, "speculative-accumulation").value(), 1e-9);
            assertEquals(0.0, component(e, "speculative-accumulation").points(), EPS);

            Flag f = flag(e, "position-sizing").orElseThrow();
            assertEquals(Severity.HIGH, f.severity());
            assertEquals("risk", f.pillar());
            assertEquals("High-Risk Concentration & Impulse", f.title());
            assertTrue(f.detail().startsWith("High concentration risk: a single trade allocated 20.0% of your total portfolio"
                    + " ($20 of $100) to pepe, a micro-cap asset ($40.0M market cap)"), f.detail());
            assertTrue(f.recommendation().contains("1–2% of capital"));
        }

        @Test
        @DisplayName("A coin listed without a market cap is unverified, and 100%+ volatility is speculative too")
        void speculativeClassification() {
            SortedMap<LocalDate, Double> wild = new TreeMap<>(), calm = new TreeMap<>();
            for (int d = 0; d < 200; d++) {
                wild.put(TODAY.minusDays(200 - d), d % 2 == 0 ? 100.0 : 112.0); // ±12% a day ≈ 200%+ a year
                calm.put(TODAY.minusDays(200 - d), 100.0 + d * 0.01);
            }
            List<TradeEvent> trades = List.of(buy(1, "btc", 1, 100, TODAY.minusDays(60)),
                    buy(2, "anon", 1, 15, TODAY.minusDays(30)), buy(3, "wild", 1, 15, TODAY.minusDays(20)));
            Inputs in = sized(trades, Map.of("btc", LARGE_CAP, "wild", LARGE_CAP),
                    Map.of("btc", calm, "wild", wild));
            TraderScoring.Sizing s = TraderScoring.sizing(in);
            assertFalse(s.of(trades.get(0)).asset().speculative());
            assertTrue(s.of(trades.get(1)).asset().speculative());
            assertEquals("unverified asset (no verified market cap)", s.of(trades.get(1)).asset().label());
            assertTrue(s.of(trades.get(2)).asset().speculative());
            assertTrue(s.of(trades.get(2)).asset().label().startsWith("high-volatility asset ("), s.of(trades.get(2)).asset().label());

            // Without any market data or history a coin is unassessed, never assumed speculative
            TraderScoring.Sizing unknown = TraderScoring.sizing(inputs(List.of(), null, null, trades, Map.of(), Map.of(), null));
            assertFalse(unknown.of(trades.get(1)).asset().assessed());
            assertFalse(unknown.of(trades.get(1)).asset().speculative());
        }

        @Test
        @DisplayName("Capital base is peak capital invested: trading the same money in and out doesn't shrink trades")
        void capitalBase() {
            List<TradeEvent> churn = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                churn.add(buy(2L * i, "btc", 10, 100, TODAY.minusDays(30 - 2L * i)));
                churn.add(sell(2L * i + 1, "btc", 10, 100, TODAY.minusDays(29 - 2L * i)));
            }
            assertEquals(1000.0, TraderScoring.peakCapitalInvested(churn), EPS);
            assertEquals(1.0, TraderScoring.sizing(inputs(List.of(), null, null, churn, Map.of(), Map.of(), null))
                    .of(churn.get(4)).share(), EPS);
            // Current value counts when the portfolio has grown past what was invested
            Inputs grown = inputs(List.of(new Holding("btc", "btc", 4000)), null, null, List.of(churn.get(0)), Map.of(), Map.of(), null);
            assertEquals(4000.0, TraderScoring.sizing(grown).capitalBase(), EPS);
        }

        @Test
        @DisplayName("60 dust trades a month aren't overtrading; the raw count still shows the activity")
        void dustIsNotOvertrading() {
            List<TradeEvent> trades = new ArrayList<>();
            trades.add(buy(0, "btc", 100, 100, TODAY.minusDays(29)));
            for (int i = 1; i <= 60; i++) trades.add(buy(i, "btc", 0.5, 100, TODAY.minusDays(i % 30)));
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of(), Map.of(), null));
            Component v = component(e, "velocity");
            assertTrue(v.displayValue().startsWith("61.0 trades / 30 days"), v.displayValue());
            assertEquals(1.0, v.value(), EPS); // only the opening buy carries weight
            assertEquals(1.0, v.score(), EPS);
            assertTrue(flag(e, "overtrading").isEmpty());
        }

        @Test
        @DisplayName("Repeated speculative buys are flagged as accumulation in the Behavioral pillar")
        void accumulation() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 7, 100, TODAY.minusDays(90)),
                    buy(2, "pepe", 100, 1, TODAY.minusDays(60)), buy(3, "bonk", 100, 1, TODAY.minusDays(30)),
                    buy(4, "wif", 100, 1, TODAY.minusDays(10)));
            Evaluation e = TraderScoring.evaluate(sized(trades,
                    Map.of("btc", LARGE_CAP, "pepe", MICRO, "bonk", MICRO, "wif", MICRO), Map.of()));
            assertEquals(3.0, component(e, "speculative-accumulation").value(), 1e-9);
            Flag f = flag(e, "speculative-accumulation").orElseThrow();
            assertEquals(Severity.HIGH, f.severity());
            assertEquals("behavior", f.pillar());
            assertTrue(f.detail().startsWith("3 buys put 30.0% of your capital into speculative assets (pepe, bonk, wif)"), f.detail());
        }

        @Test
        @DisplayName("Pillar weights still total 250 with the sizing components")
        void weights() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 1, 100, TODAY.minusDays(10)));
            Evaluation e = TraderScoring.evaluate(sized(trades, Map.of("btc", LARGE_CAP), Map.of()));
            for (String key : List.of("risk", "behavior", "consistency", "execution")) {
                assertEquals(250.0, pillar(e, key).components().stream().mapToDouble(Component::weight).sum(), EPS, key);
            }
            assertEquals(25.0, component(e, "position-sizing").weight(), EPS);
            assertEquals(40.0, component(e, "speculative-accumulation").weight(), EPS);
            assertEquals(40.0, component(e, "deployment-pacing").weight(), EPS);
        }
    }

    @Nested
    @DisplayName("Allocation penalties (in the pillar math, no caps)")
    class Allocation {
        private static final double LARGE_CAP = 500e9, MICRO = 40e6;

        /** A year of market history with a healthy rise, so the pillars that use it score well. */
        private Inputs account(double s0, List<Holding> holdings, List<TradeEvent> trades, Map<String, Double> caps) {
            double[] r = returns(364, 0.003, 0.02);
            Map<String, Double> volumes = new HashMap<>();
            for (TradeEvent t : trades) volumes.put(t.coinId(), 1e12);
            return new Inputs(holdings, r, r, null, trades, Map.of(), Map.of(), volumes, 0.4, 0.04, 365, TODAY,
                    null, caps, s0);
        }

        @Test
        @DisplayName("Three large same-day buys cost the full pacing points and are flagged; the score is the plain sum")
        void impulsiveThreeAssetBuy() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 0.04, 82_500, TODAY), buy(2, "eth", 1.3, 2_500, TODAY),
                    buy(3, "sol", 22, 150, TODAY));
            List<Holding> holdings = List.of(new Holding("btc", "btc", 3_300), new Holding("eth", "eth", 3_250),
                    new Holding("sol", "sol", 3_300));
            Evaluation e = TraderScoring.evaluate(account(10_000, holdings, trades,
                    Map.of("btc", LARGE_CAP, "eth", LARGE_CAP, "sol", LARGE_CAP)));

            assertEquals(0.0, component(e, "deployment-pacing").score(), EPS);
            Flag f = flag(e, "impulsive-deployment").orElseThrow();
            assertEquals(Severity.HIGH, f.severity());
            assertTrue(f.detail().contains("98.5% of your capital into 3 buys (btc, eth, sol) in a single day"), f.detail());
            assertEquals(sumOfPillars(e), e.score(), "no cap: the score is the pillar sum");
            assertEquals(TraderScoring.tierFor(e.score()), e.tier());
        }

        @Test
        @DisplayName("Over-allocation lowers the score through the pillars")
        void allInScoresLower() {
            List<Holding> holdings = List.of(new Holding("btc", "btc", 9_900));
            Evaluation allIn = TraderScoring.evaluate(account(10_000, holdings,
                    List.of(buy(1, "btc", 0.12, 82_500, TODAY)), Map.of("btc", LARGE_CAP)));
            List<TradeEvent> paced = new ArrayList<>();
            for (int i = 0; i < 10; i++) paced.add(buy(i, "btc", 0.012, 82_500, TODAY.minusDays(9L * i)));
            Evaluation scaledIn = TraderScoring.evaluate(account(10_000, holdings, paced, Map.of("btc", LARGE_CAP)));
            assertTrue(allIn.score() < scaledIn.score() - 30, allIn.score() + " vs " + scaledIn.score());
            assertEquals(0.0, component(allIn, "deployment-pacing").score(), EPS);
            assertEquals(1.0, component(scaledIn, "deployment-pacing").score(), EPS);
        }

        @Test
        @DisplayName("Recycling the same money in and out during a day isn't counted as deploying it")
        void roundTripsAreNotDeployment() {
            List<TradeEvent> trades = new ArrayList<>();
            for (int i = 0; i < 10; i++) {       // ten round trips of 10% of capital, all today
                trades.add(buy(2L * i, "btc", 0.0121, 82_500, TODAY));
                trades.add(sell(2L * i + 1, "btc", 0.0121, 82_500, TODAY));
            }
            Evaluation e = TraderScoring.evaluate(account(10_000, List.of(), trades, Map.of("btc", LARGE_CAP)));
            assertTrue(flag(e, "impulsive-deployment").isEmpty());
            assertEquals(0.0998, component(e, "deployment-pacing").value(), 1e-3);
        }

        @Test
        @DisplayName("S₀ is the configured bankroll: unspent cash counts in the return and in sizing")
        void bankrollIsS0() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 0.01, 80_000, TODAY.minusDays(40)));
            Inputs in = account(10_000, List.of(new Holding("btc", "btc", 880)), trades, Map.of("btc", LARGE_CAP));
            assertEquals(10_000, TraderScoring.bankroll(in), EPS);
            TraderScoring.Sizing sizing = TraderScoring.sizing(in);
            assertEquals(10_080, sizing.capitalBase(), EPS); // 9,200 cash + 880 holdings, above S₀
            assertEquals(800.0 / 10_080, sizing.of(trades.get(0)).share(), EPS);
            Inputs priced = new Inputs(in.holdings(), null, null, "No price history.", trades, Map.of(), Map.of("btc", 88_000.0),
                    Map.of(), null, 0.04, 365, TODAY, null, Map.of(), 10_000.0);
            // $80 gain on S₀ = $10,000, not on the $800 invested
            assertEquals(80.0 / 10_000, TraderScoring.bankrollReturn(priced, TraderScoring.matchLots(trades)).orElseThrow(), 1e-9);
        }

    }

    @Nested
    @DisplayName("Execution Discipline")
    class Execution {
        @Test
        @DisplayName("60 trades in 30 days is overtrading; 4 a month is disciplined")
        void velocity() {
            // 30 round trips of the whole bankroll: every trade moves 100% of the capital invested
            List<TradeEvent> busy = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                busy.add(i % 2 == 0 ? buy(i, "btc", 10, 100, TODAY.minusDays(29 - i / 2))
                        : sell(i, "btc", 10, 100, TODAY.minusDays(29 - i / 2)));
            }
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, busy, Map.of(), Map.of(), null));
            assertEquals(60.0, component(e, "velocity").value(), EPS);
            assertEquals(0.0, component(e, "velocity").score(), EPS);
            assertEquals(Severity.HIGH, flag(e, "overtrading").orElseThrow().severity());

            List<TradeEvent> calm = new ArrayList<>();
            for (int i = 0; i < 12; i++) calm.add(buy(i, "btc", 1, 100, TODAY.minusDays(i * 7L)));
            Evaluation c = TraderScoring.evaluate(inputs(List.of(), null, null, calm, Map.of(), Map.of(), null));
            assertEquals(1.0, component(c, "velocity").score(), EPS);
            assertTrue(flag(c, "overtrading").isEmpty());
        }

        @Test
        @DisplayName("Liquidity impact: a trade worth 2% of daily volume is flagged")
        void liquidity() {
            List<TradeEvent> trades = List.of(buy(1, "tiny", 200, 100, TODAY.minusDays(3)));  // $20,000
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of(), Map.of("tiny", 1_000_000.0), null));
            assertEquals(0.02, component(e, "liquidity").value(), EPS);
            assertEquals(Severity.MEDIUM, flag(e, "liquidity").orElseThrow().severity());
        }

        @Test
        @DisplayName("Risk target adherence is gone; its points went to velocity and liquidity")
        void executionWeights() {
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(4), returns(200, 0.0, 0.02), returns(200, 0.0, 0.02),
                    roundTrips(4, 7, 100, 110, 0), Map.of(), Map.of("btc", 1e12), 0.4));
            Pillar exec = pillar(e, "execution");
            assertEquals(List.of("velocity", "liquidity"), exec.components().stream().map(Component::key).toList());
            assertEquals(143.0, component(e, "velocity").weight(), EPS);
            assertEquals(107.0, component(e, "liquidity").weight(), EPS);
            assertEquals(250.0, exec.components().stream().mapToDouble(Component::weight).sum(), EPS);
            assertTrue(e.flags().stream().noneMatch(f -> f.id().contains("risk-target")));
        }
    }

    // ---- evidence caps and tier gates ----

    /** n buy/sell round trips in one coin, every loserEvery-th one a small quick loss, the rest winners. */
    private static List<TradeEvent> roundTrips(int n, int spacingDays, int startDaysAgo, double winnerSell, int loserEvery) {
        List<TradeEvent> trades = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean loser = loserEvery > 0 && i % loserEvery == 0;
            LocalDate bought = TODAY.minusDays(startDaysAgo - (long) i * spacingDays);
            trades.add(buy(2L * i, "btc", 1, 100, bought));
            trades.add(sell(2L * i + 1, "btc", 1, loser ? 95 : winnerSell, bought.plusDays(loser ? 5 : 18)));
        }
        return trades;
    }

    /** Strong, steady returns (≈ 146% a year at 19% volatility) on 5 equal holdings. */
    private static Inputs proven(List<TradeEvent> trades, double[] returns) {
        double vol = RiskMath.sampleStdDev(returns) * Math.sqrt(365);
        return new Inputs(equalHoldings(5), returns, returns, null, trades, Map.of("btc", prices(Map.of())),
                Map.of(), Map.of("btc", 1e12), vol, 0.04, 365, TODAY);
    }

    @Nested
    @DisplayName("Score and tiers (pure math, no caps)")
    class ScoreAndTiers {
        @Test
        @DisplayName("Scenarios that used to be capped now get exactly their pillar sum")
        void noCaps() {
            // No sells at all, but strong numbers elsewhere
            List<TradeEvent> buysOnly = new ArrayList<>();
            for (int i = 0; i < 12; i++) buysOnly.add(buy(i, "btc", 1, 100, TODAY.minusDays(300 - i * 20L)));
            Evaluation noSells = TraderScoring.evaluate(new Inputs(equalHoldings(5), returns(364, 0.004, 0.01),
                    returns(364, 0.004, 0.01), null, buysOnly, Map.of("btc", prices(Map.of())), Map.of("btc", 160.0),
                    Map.of("btc", 1e12), null, 0.04, 365, TODAY));
            assertEquals(sumOfPillars(noSells), noSells.score());
            assertTrue(noSells.score() > 600, "no 499 ceiling any more: " + noSells.score());

            // Losing money on capital: a lower score through the consistency pillar, not a cap
            Evaluation losing = TraderScoring.evaluate(proven(roundTrips(12, 15, 250, 90, 0), returns(364, 0.004, 0.01)));
            assertEquals(sumOfPillars(losing), losing.score());
        }

        @Test
        @DisplayName("Tiers are badges read off the score: 900 / 825 / 750 / 675 / 600 / 525 / 450 / 375 / 300 / 0")
        void tierRanges() {
            int[][] bounds = {{1000, 1}, {900, 1}, {899, 2}, {825, 2}, {824, 3}, {750, 3}, {749, 4}, {675, 4}, {674, 5},
                    {600, 5}, {599, 6}, {525, 6}, {524, 7}, {450, 7}, {449, 8}, {375, 8}, {374, 9}, {300, 9}, {299, 10}, {0, 10}};
            for (int[] b : bounds) assertEquals(b[1], TraderScoring.tierFor(b[0]).rank(), "score " + b[0]);
        }

        @Test
        @DisplayName("Tips list where points were lost, in score points, largest first, with an action")
        void tips() {
            Evaluation e = TraderScoring.evaluate(proven(roundTrips(6, 20, 130, 160, 5), returns(364, 0.004, 0.01)));
            assertFalse(e.tips().isEmpty());
            for (int i = 1; i < e.tips().size(); i++) assertTrue(e.tips().get(i - 1).gain() >= e.tips().get(i).gain());
            int totalGain = e.tips().stream().mapToInt(Tip::gain).sum();
            assertTrue(totalGain <= 1000 - e.score() + e.tips().size(), "gains never exceed what's missing");
            for (Tip t : e.tips()) {
                assertTrue(t.gain() >= 1);
                assertFalse(t.action().isBlank());
                Component c = component(e, t.key());
                assertTrue(c.score() < 1.0, t.key());
            }
        }

        @Test
        @DisplayName("A tip's gain is the metric's missing points, scaled through its pillar to the 1000-point score")
        void tipGainArithmetic() {
            List<Holding> holdings = List.of(new Holding("btc", "btc", 3_780), new Holding("eth", "eth", 1_000));
            Inputs in = new Inputs(holdings, returns(364, 0.004, 0.01), returns(364, 0.004, 0.01), null,
                    List.of(buy(1, "btc", 0.05, 75_600, TODAY.minusDays(40)), buy(2, "eth", 0.4, 2_500, TODAY.minusDays(30))),
                    Map.of(), Map.of(), Map.of(), null, 0.04, 365, TODAY, null, Map.of(), 10_000.0);
            Evaluation e = TraderScoring.evaluate(in);
            Tip sizing = e.tips().stream().filter(t -> t.key().equals("position-sizing")).findFirst().orElseThrow();
            Component c = component(e, "position-sizing");
            Pillar risk = pillar(e, "risk");
            double available = risk.components().stream().filter(Component::available).mapToDouble(Component::weight).sum();
            double expected = (c.weight() - c.points()) * (250 / available) * (1000.0 / (250 * e.pillarsScored()));
            assertEquals((int) Math.floor(expected), sizing.gain());
            assertTrue(sizing.action().startsWith("Reduce your largest position (38% of capital) to 15% or less"), sizing.action());
        }

        @Test
        @DisplayName("Tip actions follow the metric's value: too little trading and too much get different advice")
        void tipActionsFollowValue() {
            Component slow = new Component("velocity", "Trading velocity", true, 0.6, "", 143, 0.5, 71.5, "");
            Component fast = new Component("velocity", "Trading velocity", true, 30.0, "", 143, 0.3, 42.9, "");
            assertTrue(TraderScoring.action(slow).startsWith("Trade more regularly"), TraderScoring.action(slow));
            assertTrue(TraderScoring.action(fast).startsWith("Slow down"), TraderScoring.action(fast));
        }

        @Test
        @DisplayName("Milestone: points to the next tier and progress through the current one")
        void milestone() {
            Milestone m = TraderScoring.milestone(634, TraderScoring.tierFor(634));
            assertEquals(4, m.next().rank());
            assertEquals(41, m.pointsNeeded());
            assertEquals(34.0 / 75, m.progress(), 1e-9);
            assertNull(TraderScoring.milestone(950, TraderScoring.tierFor(950)));
        }

        @Test
        @DisplayName("Profit concentration: one trade carrying the profits costs Consistency points")
        void profitConcentration() {
            List<TradeEvent> spread = roundTrips(8, 10, 120, 120, 0);                  // 8 equal winners
            Component even = component(TraderScoring.evaluate(inputs(List.of(), null, null, spread, Map.of(), Map.of(), null)),
                    "profit-concentration");
            assertEquals(0.125, even.value(), 1e-9);
            assertEquals(1.0, even.score(), EPS);

            List<TradeEvent> lucky = new ArrayList<>(roundTrips(3, 10, 120, 101, 0)); // three +1 winners...
            lucky.add(buy(50, "btc", 1, 100, TODAY.minusDays(20)));
            lucky.add(sell(51, "btc", 1, 197, TODAY.minusDays(10)));                  // ...and one +97
            Component one = component(TraderScoring.evaluate(inputs(List.of(), null, null, lucky, Map.of(), Map.of(), null)),
                    "profit-concentration");
            assertEquals(0.97, one.value(), 1e-9);
            assertEquals(0.0, one.score(), EPS);
            assertEquals(40.0, one.weight(), EPS);
        }

        @Test
        @DisplayName("One lucky, all-in trade can't max out the score")
        void luckyTradeIsNotElite() {
            List<TradeEvent> trades = List.of(buy(1, "pepe", 9_000, 1, TODAY.minusDays(3)), sell(2, "pepe", 9_000, 2, TODAY.minusDays(1)),
                    buy(3, "pepe", 100, 2, TODAY.minusDays(1)), sell(4, "pepe", 100, 2.02, TODAY));
            Inputs in = new Inputs(List.of(new Holding("pepe", "pepe", 18_000)), returns(364, 0.004, 0.06), returns(364, 0.002, 0.02),
                    null, trades, Map.of(), Map.of(), Map.of("pepe", 1e9), null, 0.04, 365, TODAY, null, Map.of("pepe", 40e6), 10_000.0);
            Evaluation e = TraderScoring.evaluate(in);
            assertTrue(e.score() < 750, "score " + e.score());
            assertEquals(0.0, component(e, "position-sizing").score(), EPS);
            assertEquals(0.0, component(e, "profit-concentration").score(), EPS);
        }

        @Test
        @DisplayName("A proven, active, profitable trader can still reach Tier 1")
        void elite() {
            Evaluation e = TraderScoring.evaluate(proven(roundTrips(20, 20, 400, 160, 5), returns(364, 0.004, 0.01)));
            assertTrue(e.score() >= 900, "score " + e.score());
            assertEquals(1, e.tier().rank());
            assertEquals(sumOfPillars(e), e.score());
        }

        @Test
        @DisplayName("Closed trades that all broke even leave profit factor out instead of a made-up 0.5")
        void breakEvenProfitFactor() {
            List<TradeEvent> flat = roundTrips(3, 10, 100, 100, 0);   // every sell at the buy price
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, flat, Map.of(), Map.of(), null));
            assertFalse(component(e, "profit-factor").available());
            assertTrue(component(e, "profit-factor").detail().contains("broke even"));
        }

        @Test
        @DisplayName("Missing market volume data costs no points: the pillar is scaled over the metrics that have data")
        void coverageNormalization() {
            List<TradeEvent> trades = roundTrips(4, 7, 100, 110, 0);
            Evaluation withoutVolume = TraderScoring.evaluate(inputs(List.of(), null, null, trades, Map.of(), Map.of(), null));
            Pillar exec = pillar(withoutVolume, "execution");
            assertFalse(component(withoutVolume, "liquidity").available());
            assertEquals(143.0 / 250, exec.coverage(), 1e-9);       // only velocity (143 of 250) has data
            assertTrue(exec.available());
            assertEquals(250 * component(withoutVolume, "velocity").score(), exec.points(), 1e-9);

            // Real market data comes with both 24h volume and market cap (a listed coin without a cap counts as unverified)
            Evaluation withVolume = TraderScoring.evaluate(new Inputs(List.of(), null, null, "No price history.", trades, Map.of(),
                    Map.of(), Map.of("btc", 1e12), null, 0.04, 365, TODAY, null, Map.of("btc", 1.6e12)));
            assertEquals(1.0, component(withVolume, "liquidity").score(), EPS);
            // Adding a perfect liquidity score can only help; its absence never hurt
            assertTrue(pillar(withVolume, "execution").points() >= exec.points() - 1e-9,
                    pillar(withVolume, "execution") + " vs " + exec);
        }

        @Test
        @DisplayName("A long break counts: the same trades measured months later show a lower trading pace")
        void timeGapLowersVelocity() {
            List<TradeEvent> trades = roundTrips(8, 10, 100, 120, 0);
            Inputs now = inputs(List.of(), null, null, trades, Map.of(), Map.of(), null);
            Inputs later = new Inputs(List.of(), null, null, "No price history.", trades, Map.of(), Map.of(), Map.of(),
                    null, 0.04, 365, TODAY.plusDays(120));
            double paceNow = component(TraderScoring.evaluate(now), "velocity").value();
            double paceLater = component(TraderScoring.evaluate(later), "velocity").value();
            assertEquals(16 * 30.0 / 101, paceNow, 1e-9);
            assertEquals(16 * 30.0 / 221, paceLater, 1e-9);
        }

        @Test
        @DisplayName("Rare trading isn't rewarded as discipline: under 2 trades a month scores below an active pace")
        void inactivityNotRewarded() {
            Evaluation rare = TraderScoring.evaluate(inputs(List.of(), null, null,
                    List.of(buy(1, "btc", 1, 100, TODAY.minusDays(300)), sell(2, "btc", 1, 110, TODAY.minusDays(200))),
                    Map.of(), Map.of(), null));
            assertEquals(0.4, component(rare, "velocity").score(), 1e-9);
            Evaluation active = TraderScoring.evaluate(inputs(List.of(), null, null, roundTrips(8, 10, 160, 120, 0),
                    Map.of(), Map.of(), null));
            assertEquals(1.0, component(active, "velocity").score(), 1e-9);
        }

    }

    @Nested
    @DisplayName("Total score")
    class Total {
        @Test
        @DisplayName("A pillar without enough data is left out; the total is scaled over the others, provisional, never capped")
        void rescales() {
            double[] up = new double[200];
            Arrays.fill(up, 0.003);
            for (int i = 0; i < up.length; i += 2) up[i] += 0.01;
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(5), up, up, List.of(imported(1, "coin0", 10, 100)),
                    Map.of(), Map.of(), null));

            assertTrue(pillar(e, "risk").available());
            assertTrue(pillar(e, "consistency").available());   // bankroll + smoothness: 170 of 250 points
            assertFalse(pillar(e, "behavior").available());
            assertFalse(pillar(e, "execution").available());
            assertTrue(e.provisional());
            assertEquals(2, e.pillarsScored());
            long shown = Math.round(pillar(e, "risk").points()) + Math.round(pillar(e, "consistency").points());
            assertEquals(Math.round(shown * 4 / 2.0), e.score());   // the same average over the two scored pillars
            assertEquals(TraderScoring.tierFor(e.score()), e.tier());
            assertTrue(flag(e, "missing-behavior").isPresent());
        }

        @Test
        @DisplayName("A pillar counts as soon as it has data; pillars with none are left out and the score is provisional")
        void partialData() {
            // Holdings only: concentration and position sizing measure the risk pillar; nothing else has data
            Evaluation e = TraderScoring.evaluate(holdingsOnly(equalHoldings(5)));
            assertTrue(pillar(e, "risk").available());
            assertFalse(pillar(e, "behavior").available());
            assertEquals(1, e.pillarsScored());
            assertTrue(e.provisional());
            assertEquals(sumOfPillars(e), e.score());
        }

        @Test
        @DisplayName("Pillar points are rescaled over the metrics that have data")
        void pillarRescale() {
            double[] r = returns(200, 0.002, 0.02);
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(4), r, r, List.of(), Map.of(), Map.of(), null));
            Pillar risk = pillar(e, "risk");
            double weight = 0, earned = 0;
            for (Component c : risk.components()) if (c.available()) { weight += c.weight(); earned += c.points(); }
            assertEquals(250.0 * earned / weight, risk.points(), 1e-9);
            assertTrue(risk.points() >= 0 && risk.points() <= 250);
        }

        @Test
        @DisplayName("Every pillar scored: the score is the plain sum of the pillar points, not provisional")
        void complete() {
            LocalDate s = TODAY.minusDays(200);
            double[] r = returns(200, 0.002, 0.02);
            SortedMap<LocalDate, Double> flat = prices(Map.of());
            List<TradeEvent> trades = List.of(
                    buy(1, "btc", 1, 100, s), sell(2, "btc", 1, 120, s.plusDays(10)),
                    buy(3, "btc", 1, 100, s.plusDays(20)), sell(4, "btc", 1, 90, s.plusDays(25)),
                    buy(5, "eth", 1, 100, s.plusDays(30)));
            Inputs in = new Inputs(equalHoldings(4), r, r, null, trades, Map.of("btc", flat, "eth", flat),
                    Map.of("eth", 100.0), Map.of("btc", 1e10, "eth", 1e10), 0.4, 0.04, 365, TODAY);
            Evaluation e = TraderScoring.evaluate(in);

            assertEquals(4, e.pillarsScored());
            assertFalse(e.provisional());
            assertEquals(sumOfPillars(e), e.score());       // exactly the four pillar totals as shown, added up
        }

        @Test
        @DisplayName("No data at all: nothing scored and no tier, never NaN")
        void empty() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(List.of()));
            assertEquals(0, e.pillarsScored());
            assertNull(e.tier());
            for (Pillar p : e.pillars()) assertEquals(0.0, p.points());
        }

        @Test
        @DisplayName("Flags are ordered by severity, most serious first")
        void flagOrder() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(List.of(new Holding("eth", "Ethereum", 1))));
            for (int i = 1; i < e.flags().size(); i++) {
                assertTrue(e.flags().get(i - 1).severity().compareTo(e.flags().get(i).severity()) <= 0);
            }
        }
    }
}
