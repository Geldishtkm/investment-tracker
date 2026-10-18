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
            int[][] cases = {{1000, 1}, {900, 1}, {899, 2}, {800, 2}, {799, 3}, {750, 3}, {749, 4}, {700, 4},
                    {699, 5}, {600, 5}, {599, 6}, {500, 6}, {499, 7}, {400, 7}, {399, 8}, {300, 8},
                    {299, 9}, {150, 9}, {149, 10}, {0, 10}};
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
            assertEquals((annual - 0.04) / vol, component(e, "sharpe").value(), 1e-12);
            assertEquals(vol, component(e, "volatility").value(), 1e-12);

            double rfDaily = 0.04 / 365, sumSq = 0;
            for (double x : r) sumSq += Math.pow(Math.min(x - rfDaily, 0), 2);
            double downside = Math.sqrt(sumSq / r.length) * Math.sqrt(365);
            assertEquals((annual - 0.04) / downside, component(e, "sortino").value(), 1e-12);
        }

        @Test
        @DisplayName("A flat return series leaves the ratios unscored instead of dividing by zero")
        void zeroVolatility() {
            double[] flat = new double[120];
            Evaluation e = TraderScoring.evaluate(inputs(equalHoldings(4), flat, flat, List.of(), Map.of(), Map.of(), null));
            assertFalse(component(e, "sharpe").available());
            assertFalse(component(e, "sortino").available());
            assertFalse(component(e, "volatility").available());
            assertFalse(component(e, "beta-stability").available()); // the benchmark never moved either
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

        @Test
        @DisplayName("Beta that keeps changing between windows scores lower than a steady beta")
        void betaStability() {
            Random rnd = new Random(7);
            double[] bench = new double[150], steady = new double[150], shifting = new double[150];
            for (int t = 0; t < 150; t++) {
                bench[t] = rnd.nextGaussian() * 0.03;
                steady[t] = 1.0 * bench[t];
                shifting[t] = ((t / 30) % 2 == 0 ? 0.3 : 2.0) * bench[t];
            }
            Evaluation a = TraderScoring.evaluate(inputs(equalHoldings(4), steady, bench, List.of(), Map.of(), Map.of(), null));
            Evaluation b = TraderScoring.evaluate(inputs(equalHoldings(4), shifting, bench, List.of(), Map.of(), Map.of(), null));
            assertEquals(0.0, component(a, "beta-stability").value(), 1e-9);
            assertTrue(component(b, "beta-stability").score() < component(a, "beta-stability").score());
            assertTrue(flag(b, "unstable-beta").isPresent());
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
            assertEquals(50.0, c.points(), EPS);
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

        @Test
        @DisplayName("FOMO: a buy after a +20% week counts; a buy in a flat market doesn't")
        void fomo() {
            LocalDate surgeDay = TODAY.minusDays(40), calmDay = TODAY.minusDays(100);
            Map<LocalDate, Double> overrides = new HashMap<>();
            overrides.put(surgeDay, 120.0); // 7 days earlier the price was 100: +20%
            Map<String, SortedMap<LocalDate, Double>> p = Map.of("btc", prices(overrides));
            List<TradeEvent> trades = List.of(buy(1, "btc", 1, 120, surgeDay), buy(2, "btc", 1, 100, calmDay));

            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades, p, Map.of(), null));
            Component c = component(e, "fomo");
            assertEquals(0.5, c.value(), EPS);
            assertEquals(0.0, c.score(), EPS);
            assertTrue(flag(e, "fomo").orElseThrow().detail().startsWith("1 of 2 buys came after a 7-day rally"));
        }

        @Test
        @DisplayName("FOMO is unscored with fewer than two buys inside the price history")
        void fomoNeedsTrades() {
            List<TradeEvent> trades = List.of(buy(1, "btc", 1, 100, TODAY.minusDays(10)),
                    buy(2, "btc", 1, 100, TODAY.minusDays(900))); // older than the history
            Evaluation e = TraderScoring.evaluate(inputs(List.of(), null, null, trades,
                    Map.of("btc", prices(Map.of())), Map.of(), null));
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
    @DisplayName("Execution Discipline")
    class Execution {
        @Test
        @DisplayName("60 trades in 30 days is overtrading; 4 a month is disciplined")
        void velocity() {
            List<TradeEvent> busy = new ArrayList<>();
            for (int i = 0; i < 60; i++) busy.add(buy(i, "btc", 1, 100, TODAY.minusDays(i % 30)));
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
        @DisplayName("Risk target: adherence is measured when set, and suggested when missing")
        void riskTarget() {
            double[] r = returns(200, 0.0, 0.02);
            double vol = RiskMath.sampleStdDev(r) * Math.sqrt(365);
            Evaluation onTarget = TraderScoring.evaluate(inputs(equalHoldings(4), r, r, List.of(), Map.of(), Map.of(), vol));
            assertEquals(0.0, component(onTarget, "risk-target").value(), 1e-12);
            assertEquals(1.0, component(onTarget, "risk-target").score(), EPS);

            Evaluation none = TraderScoring.evaluate(inputs(equalHoldings(4), r, r, List.of(), Map.of(), Map.of(), null));
            assertFalse(component(none, "risk-target").available());
            assertEquals(Severity.INFO, flag(none, "no-risk-target").orElseThrow().severity());
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
    @DisplayName("Evidence caps and tier gates")
    class Caps {
        @Test
        @DisplayName("One stablecoin held untouched: stagnant, Tier 8 or below")
        void stagnantStablecoin() {
            double[] flat = returns(364, 0.0, 0.0002);       // about 0.4% annual volatility
            Evaluation e = TraderScoring.evaluate(inputs(List.of(new Holding("tether", "Tether", 10_000)), flat, returns(364, 0, 0.03),
                    List.of(imported(1, "tether", 10_000, 1.0)), Map.of(), Map.of("tether", 1e11), null));

            assertTrue(e.score() <= 399, "score " + e.score());
            assertTrue(e.tier().rank() >= 8);
            assertEquals("stagnant", e.caps().get(0).id());
        }

        @Test
        @DisplayName("Two assets with little price action stay in the retail tiers even with a decent raw score")
        void twoStagnantAssets() {
            double[] calm = returns(364, 0.0003, 0.004);      // ≈ 7.6% volatility, 11% a year
            List<Holding> two = List.of(new Holding("a", "Asset A", 5000), new Holding("b", "Asset B", 5000));
            Evaluation e = TraderScoring.evaluate(inputs(two, calm, calm,
                    List.of(imported(1, "a", 50, 100), imported(2, "b", 50, 100)), Map.of(), Map.of("a", 1e11, "b", 1e11), 0.08));

            assertTrue(e.rawScore() > 399, "the raw metrics look respectable: " + e.rawScore());
            assertTrue(e.score() <= 399);
            assertTrue(e.tier().rank() >= 8);
        }

        @Test
        @DisplayName("A volatile portfolio that has gone nowhere on its capital is also stagnant")
        void noMovementOnCapital() {
            double[] r = returns(364, 0.0, 0.03);
            Inputs in = new Inputs(equalHoldings(4), r, r, null, List.of(imported(1, "coin0", 10, 100)), Map.of(),
                    Map.of("coin0", 101.0), Map.of(), null, 0.04, 365, TODAY);   // +1% on capital
            Evaluation e = TraderScoring.evaluate(in);
            assertTrue(e.caps().stream().anyMatch(c -> c.id().equals("stagnant")));
            assertTrue(e.score() <= 399);
        }

        @Test
        @DisplayName("Great numbers but no sells: capped at Tier 7, whatever the raw score")
        void noSells() {
            List<TradeEvent> buysOnly = new ArrayList<>();
            for (int i = 0; i < 12; i++) buysOnly.add(buy(i, "btc", 1, 100, TODAY.minusDays(300 - i * 20L)));
            Inputs in = new Inputs(equalHoldings(5), returns(364, 0.004, 0.01), returns(364, 0.004, 0.01), null, buysOnly,
                    Map.of("btc", prices(Map.of())), Map.of("btc", 160.0), Map.of("btc", 1e12), 0.19, 0.04, 365, TODAY);
            Evaluation e = TraderScoring.evaluate(in);

            assertTrue(e.rawScore() > 600, "raw " + e.rawScore());
            assertTrue(e.score() <= 499);
            assertTrue(e.caps().stream().anyMatch(c -> c.id().equals("no-sells") && c.title().equals("No sells recorded")));
        }

        @Test
        @DisplayName("Losing money on capital caps the score at Tier 7")
        void negativeReturn() {
            List<TradeEvent> losing = roundTrips(12, 15, 250, 90, 0);    // every trade sells at 90 after buying at 100
            Evaluation e = TraderScoring.evaluate(proven(losing, returns(364, 0.004, 0.01)));
            assertTrue(e.score() <= 499);
            assertTrue(e.caps().stream().anyMatch(c -> c.id().equals("no-growth")));
        }

        @Test
        @DisplayName("Tier 5 needs a real profit factor: 1.1 keeps the score at 599 and says why")
        void tier5NeedsProfitFactor() {
            // 6 winners at +5.5 and 6 losers at −5 give a profit factor of 1.1; an open ETH position up 40%
            // keeps the return on capital clearly positive, so only the profit factor fails the gate
            List<TradeEvent> trades = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                LocalDate d = TODAY.minusDays(300 - i * 20L);
                trades.add(buy(2L * i, "btc", 1, 100, d));
                trades.add(sell(2L * i + 1, "btc", 1, i % 2 == 0 ? 105.5 : 95, d.plusDays(i % 2 == 0 ? 18 : 5)));
            }
            trades.add(buy(100, "eth", 10, 100, TODAY.minusDays(200)));                 // open, now +40%
            Inputs in = new Inputs(equalHoldings(5), returns(364, 0.004, 0.01), returns(364, 0.004, 0.01), null, trades,
                    Map.of("btc", prices(Map.of()), "eth", prices(Map.of())), Map.of("eth", 140.0), Map.of("btc", 1e12, "eth", 1e12),
                    0.19, 0.04, 365, TODAY);
            Evaluation e = TraderScoring.evaluate(in);

            assertEquals(1.1, TraderScoring.profitFactor(in, TraderScoring.matchLots(trades)).orElseThrow(), 1e-9);
            assertEquals(599, e.score());
            ScoreCap cap = e.caps().get(0);
            assertEquals("gate-600", cap.id());
            assertTrue(cap.detail().contains("Profit factor of at least 1.2"), cap.detail());
            assertFalse(cap.detail().contains("Sharpe"), "only the failed requirements are listed");
        }

        @Test
        @DisplayName("Meeting the Tier 5 gate but not Tier 3 stops at 749")
        void intermediate() {
            Evaluation e = TraderScoring.evaluate(proven(roundTrips(6, 20, 130, 160, 5), returns(364, 0.004, 0.01)));
            assertTrue(e.gates().get(0).unlocked());
            assertFalse(e.gates().get(1).unlocked());          // 12 trades over ~131 days: needs 20 over 180
            assertEquals(749, e.score());
            assertEquals(4, e.tier().rank());
        }

        @Test
        @DisplayName("A proven, active, profitable trader can still reach Tier 1")
        void elite() {
            Evaluation e = TraderScoring.evaluate(proven(roundTrips(20, 20, 400, 160, 5), returns(364, 0.004, 0.01)));
            assertTrue(e.caps().isEmpty(), "caps " + e.caps());
            assertTrue(e.gates().stream().allMatch(TierGate::unlocked));
            assertTrue(e.score() >= 900, "score " + e.score());
            assertEquals(1, e.tier().rank());
            assertEquals(e.rawScore(), e.score());
        }

        @Test
        @DisplayName("Requirements are judged at their displayed precision: 1.4996 shows as 1.50 and meets 1.5")
        void displayedPrecision() {
            var e = new TraderScoring.Evidence(0.4, 1.0, 0.15, 1.4996, 4, 25, 200, 5);
            TierGate tier3 = TraderScoring.tierGates(e).get(1);
            assertTrue(tier3.unlocked(), tier3.requirements().toString());
            var below = new TraderScoring.Evidence(0.4, 1.0, 0.15, 1.494, 4, 25, 200, 5);
            assertFalse(TraderScoring.tierGates(below).get(1).unlocked());
        }

        @Test
        @DisplayName("Unknown evidence never satisfies a requirement")
        void unknownFailsGates() {
            Evaluation e = TraderScoring.evaluate(holdingsOnly(equalHoldings(5)));
            for (TierGate g : e.gates()) {
                assertFalse(g.unlocked());
                g.requirements().forEach(r -> assertFalse(r.met(), r.label()));
            }
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

    // ---- total ----

    @Nested
    @DisplayName("Total score")
    class Total {
        @Test
        @DisplayName("Missing pillars are left out and the total is rescaled and marked provisional")
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
            double expected = 1000 * (pillar(e, "risk").points() + pillar(e, "consistency").points()) / 500.0;
            assertEquals(Math.round(expected), e.rawScore());
            assertTrue(e.score() <= 499, "a provisional score stays at Tier 7 or below");
            assertTrue(e.caps().stream().anyMatch(c -> c.id().equals("provisional")));
            assertEquals(TraderScoring.tierFor(e.score()), e.tier());
            assertTrue(flag(e, "missing-behavior").isPresent());
        }

        @Test
        @DisplayName("One minor metric can't stand in for a whole pillar")
        void minimumCoverage() {
            // Only concentration (40 of 250 risk points) has data: well-diversified, but too little to score on
            Evaluation e = TraderScoring.evaluate(holdingsOnly(equalHoldings(5)));
            assertEquals(1.0, component(e, "concentration").score(), EPS);
            assertFalse(pillar(e, "risk").available());
            assertEquals(0, e.pillarsScored());
            assertNull(e.tier());
            assertEquals(0, e.score());
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
        @DisplayName("Every pillar scored: the raw total is the plain sum of pillar points, not provisional")
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
            double sum = e.pillars().stream().mapToDouble(Pillar::points).sum();
            assertEquals(Math.round(sum), e.rawScore());
            // 5 trades are far short of the 10 that Tier 5 needs
            assertEquals(Math.min(e.rawScore(), 599), e.score());
            assertEquals("gate-600", e.caps().get(0).id());
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
