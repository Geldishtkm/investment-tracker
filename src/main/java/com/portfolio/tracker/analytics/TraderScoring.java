package com.portfolio.tracker.analytics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Institutional Trader Index Score (TIS): a 0–1000 evaluation built from four pillars of 250
 * points each. Pure functions only (no I/O), so every formula can be tested directly.
 *
 * <p>Each pillar is made of weighted components. A component scores 0–1 through a linear band
 * between a "worst" and a "best" value, and earns weight × score points. Components without the
 * data they need are left out and the pillar is scaled over the components that remain; pillars
 * without enough usable components (less than {@link #MIN_PILLAR_COVERAGE} of their points) are left
 * out and the total is scaled over the pillars that remain, marked provisional. Nothing is ever
 * filled in with assumed values.
 *
 * <p>The weighted total (the raw score) is then capped by evidence rules, so a high rank has to be
 * earned with proven results rather than inferred from little or flat data:
 * <ul>
 *   <li>allocation caps (risk cooling periods): a single speculative buy of half the capital or more
 *       caps the score in Tier 10 (≤ 149); a speculative buy of 25%+, any buy of 50%+, or a peak of 75%+
 *       of the capital deployed in one day caps it in Tier 9 (≤ 299). A cap applies as soon as the trade
 *       is made and lifts once the user has made 5 further disciplined trades (each under 10% of
 *       capital), or after 90 days</li>
 *   <li>hard caps: stagnant portfolios stay at or below Tier 8 (≤ 399); provisional scores, no
 *       recorded sells, a thin trade history, or no positive return on capital stay at or below
 *       Tier 7 (≤ 499)</li>
 *   <li>tier gates: Tier 5 and above needs a minimum return on capital and number of trades, with no
 *       waiting period. Tier 3 and Tier 1 also need discipline: a minimum Sharpe ratio, a capped largest
 *       position and one-day deployment, and profits that don't hang on a single trade. Failing a gate caps
 *       the score just below it</li>
 * </ul>
 */
public final class TraderScoring {

    public static final double PILLAR_POINTS = 250.0;
    public static final int MAX_SCORE = 1000;
    /** Share of a pillar's points that must have data for the pillar to count, so one minor metric can't stand in for it. */
    public static final double MIN_PILLAR_COVERAGE = 0.40;

    // Evidence caps
    static final int STAGNANT_CAP = 399;                 // Tier 8 and below
    static final int UNPROVEN_CAP = 499;                 // Tier 7 and below
    static final double STAGNANT_VOLATILITY = 0.10;      // annualized; crypto typically runs at 40%+
    static final double STAGNANT_RETURN = 0.02;          // |return on S0| below 2% counts as no movement
    static final int MIN_DATED_TRADES = 5;
    static final int MIN_SELLS = 2;
    /** A portfolio this new hasn't had time to move, so a flat return doesn't make it stagnant. */
    static final int NEW_PORTFOLIO_DAYS = 30;

    // Allocation caps, judged on buys in the last ALLOCATION_WINDOW_DAYS
    static final int RECKLESS_CAP = 149;                 // Tier 10
    static final int IMPULSIVE_CAP = 299;                // Tier 9 and below
    static final int ALLOCATION_WINDOW_DAYS = 90;
    static final double RECKLESS_SPECULATIVE_SHARE = 0.50;
    static final double IMPULSIVE_SPECULATIVE_SHARE = 0.25;
    static final double IMPULSIVE_TRADE_SHARE = 0.50;
    static final double IMPULSIVE_DAY_SHARE = 0.75;
    /** A cooling-period cap lifts after this many later trades, each under DISCIPLINED_SHARE of capital. */
    static final int COOLING_RELEASE_TRADES = 5;
    static final double DISCIPLINED_SHARE = 0.10;

    // Behavioral detection thresholds
    static final double FOMO_RUN_UP = 0.15;          // bought after a 7-day rally of at least +15%
    static final int FOMO_LOOKBACK_DAYS = 7;
    static final double PANIC_DROP = -0.08;          // sold on a day the coin fell 8% or more in 24h...
    static final int PANIC_WINDOW_DAYS = 3;          // ...at the lowest price within ±3 days
    static final double PANIC_TROUGH_TOLERANCE = 0.01;
    static final int MIN_EVALUABLE_TRADES = 2;
    /** The benchmark beta looks at the most recent week of daily returns (at least 3 are needed). */
    public static final int BETA_DAYS = 7;
    static final int MIN_BETA_DAYS = 3;

    // Position sizing: penalties scale with each trade's share of the capital base
    static final double DUST_SHARE = 0.01;            // under 1% of capital: negligible, no penalty
    static final double FULL_IMPACT_SHARE = 0.10;     // a trade of 10%+ counts in full...
    static final double ESCALATION_STEP = 0.05;       // ...and in a speculative asset, each further 5% adds one more
    static final double MAX_IMPACT = 4.0;             // (a 25% speculative trade counts four times)
    static final double MICRO_CAP = 250e6;            // market capitalisation below $250M
    static final double SPECULATIVE_VOLATILITY = 1.0; // annualized volatility of 100% or more
    /**
     * Daily returns needed before any volatility-based figure (volatility, Sharpe, Sortino, beta, a coin's
     * own volatility) is computed: fewer make annualized figures swing wildly, so the metric is left out.
     */
    static final int MIN_RISK_OBSERVATIONS = 60;
    static final int MIN_VOLATILITY_DAYS = MIN_RISK_OBSERVATIONS;
    /** Daily returns further than this many robust standard deviations from the median are trimmed for volatility. */
    static final double VOLATILITY_TRIM_SIGMAS = 5.0;

    private TraderScoring() {
    }

    // ---------------------------------------------------------------------
    // Inputs and results
    // ---------------------------------------------------------------------

    public enum Side { BUY, SELL }

    public enum Severity { HIGH, MEDIUM, LOW, INFO }

    /** One ledger trade; imported trades are holdings from before the ledger, with unknown dates. */
    public record TradeEvent(long id, String coinId, String name, Side side, double quantity, double price,
                             LocalDate date, boolean dateKnown, boolean imported) {
        double notional() { return quantity * price; }
    }

    /** A current holding at market value. */
    public record Holding(String coinId, String name, double value) {}

    /**
     * @param portfolioReturns         daily portfolio returns at today's weights, or null if unavailable
     * @param benchmarkReturns         benchmark daily returns over the same days, or null
     * @param historyUnavailableReason why the return series is missing (shown to the user)
     * @param dailyPrices              daily price per coin, for evaluating trades around their dates
     * @param currentPrices            current price per coin, for valuing open lots
     * @param dailyVolumes             current 24h trading volume in USD per coin
     * @param assetReturns             assetReturns[t][i] = daily return of holdings[i] on the same days as
     *                                 portfolioReturns, for correlations; null if unavailable
     * @param marketCaps               current market capitalisation in USD per coin; a coin with a volume in
     *                                 dailyVolumes but no cap here is one CoinGecko lists without a verified cap
     * @param initialBankroll          the user's starting capital S₀, or null when not set (S₀ is then the sum of
     *                                 all buys)
     * @param recentMarketReturns      the market's (50/50 BTC/ETH) daily returns over the same most recent days as
     *                                 the end of portfolioReturns, for the 7-day beta; null to use benchmarkReturns
     * @param riskTargetVolatility     no longer scored; kept so callers don't change
     */
    public record Inputs(List<Holding> holdings,
                         double[] portfolioReturns,
                         double[] benchmarkReturns,
                         String historyUnavailableReason,
                         List<TradeEvent> trades,
                         Map<String, SortedMap<LocalDate, Double>> dailyPrices,
                         Map<String, Double> currentPrices,
                         Map<String, Double> dailyVolumes,
                         Double riskTargetVolatility,
                         double riskFreeRate,
                         int periodsPerYear,
                         LocalDate today,
                         double[][] assetReturns,
                         Map<String, Double> marketCaps,
                         Double initialBankroll,
                         double[] recentMarketReturns) {

        /** Inputs without per-holding returns: diversification is then judged on concentration alone. */
        public Inputs(List<Holding> holdings, double[] portfolioReturns, double[] benchmarkReturns,
                      String historyUnavailableReason, List<TradeEvent> trades,
                      Map<String, SortedMap<LocalDate, Double>> dailyPrices, Map<String, Double> currentPrices,
                      Map<String, Double> dailyVolumes, Double riskTargetVolatility, double riskFreeRate,
                      int periodsPerYear, LocalDate today) {
            this(holdings, portfolioReturns, benchmarkReturns, historyUnavailableReason, trades, dailyPrices,
                    currentPrices, dailyVolumes, riskTargetVolatility, riskFreeRate, periodsPerYear, today, null, Map.of(), null, null);
        }

        /** Inputs without a separate recent market series (the benchmark is used for the 7-day beta). */
        public Inputs(List<Holding> holdings, double[] portfolioReturns, double[] benchmarkReturns,
                      String historyUnavailableReason, List<TradeEvent> trades,
                      Map<String, SortedMap<LocalDate, Double>> dailyPrices, Map<String, Double> currentPrices,
                      Map<String, Double> dailyVolumes, Double riskTargetVolatility, double riskFreeRate,
                      int periodsPerYear, LocalDate today, double[][] assetReturns, Map<String, Double> marketCaps,
                      Double initialBankroll) {
            this(holdings, portfolioReturns, benchmarkReturns, historyUnavailableReason, trades, dailyPrices,
                    currentPrices, dailyVolumes, riskTargetVolatility, riskFreeRate, periodsPerYear, today, assetReturns,
                    marketCaps, initialBankroll, null);
        }

        /** Inputs without a configured bankroll. */
        public Inputs(List<Holding> holdings, double[] portfolioReturns, double[] benchmarkReturns,
                      String historyUnavailableReason, List<TradeEvent> trades,
                      Map<String, SortedMap<LocalDate, Double>> dailyPrices, Map<String, Double> currentPrices,
                      Map<String, Double> dailyVolumes, Double riskTargetVolatility, double riskFreeRate,
                      int periodsPerYear, LocalDate today, double[][] assetReturns, Map<String, Double> marketCaps) {
            this(holdings, portfolioReturns, benchmarkReturns, historyUnavailableReason, trades, dailyPrices,
                    currentPrices, dailyVolumes, riskTargetVolatility, riskFreeRate, periodsPerYear, today, assetReturns,
                    marketCaps, null, null);
        }
    }

    /** A scored metric. score is 0–1 and points = weight × score; both are 0 when unavailable. */
    public record Component(String key, String name, boolean available, Double value, String displayValue,
                            double weight, double score, double points, String detail) {}

    /** coverage: share of the pillar's points whose metrics had data (0–1). */
    public record Pillar(String key, String name, boolean available, double points, double maxPoints,
                         double coverage, List<Component> components) {}

    public record Flag(String id, Severity severity, String pillar, String title, String detail,
                       String recommendation) {}

    public record Tier(int rank, String name, int minScore, int maxScore) {}

    public record Coverage(int trades, int datedTrades, int closedLots, int observations) {}

    /** A limit on the score with the reason, e.g. "no sells recorded" capping at 499. */
    public record ScoreCap(String id, int maxScore, String title, String detail) {}

    /**
     * One requirement of a tier gate. name and target read as a checklist line ("Return on Capital:
     * +0.4% (Target: ≥ +5.0%)"); progress is how far the actual value is towards the target, 0–1.
     * label is the whole requirement in one sentence.
     */
    public record Requirement(String label, boolean met, String actual, String name, String target, double progress) {}

    /** Requirements that must all be met before scores of minScore and above are allowed. */
    public record TierGate(int minScore, String name, boolean unlocked, List<Requirement> requirements) {}

    /**
     * score is the final, capped score; rawScore the weighted total before evidence caps.
     * caps lists every cap that applies, lowest first. tier is null only when no pillar could be scored.
     * diversification is null when there are no current holdings.
     */
    public record Evaluation(int score, int rawScore, boolean provisional, int pillarsScored, Tier tier,
                             List<Pillar> pillars, List<ScoreCap> caps, List<TierGate> gates,
                             List<Flag> flags, List<String> strengths, Coverage coverage,
                             Diversification.Result diversification) {}

    public static final List<Tier> TIERS = List.of(
            new Tier(1, "Sovereign Quantitative Director", 900, 1000),
            new Tier(2, "Chief Risk Officer (CRO)", 800, 899),
            new Tier(3, "Institutional Portfolio Manager", 750, 799),
            new Tier(4, "Proprietary Desk Trader", 700, 749),
            new Tier(5, "Disciplined Quantitative Analyst", 600, 699),
            new Tier(6, "Systematic Swing Trader", 500, 599),
            new Tier(7, "Tactical Retail Trader", 400, 499),
            new Tier(8, "Speculative Momentum Chaser", 300, 399),
            new Tier(9, "Unhedged Emotional Gambler", 150, 299),
            new Tier(10, "Reckless Liquidation Hazard", 0, 149));

    public static Tier tierFor(int score) {
        for (Tier tier : TIERS) {
            if (score >= tier.minScore() && score <= tier.maxScore()) return tier;
        }
        throw new IllegalArgumentException("Score out of range: " + score);
    }

    // ---------------------------------------------------------------------
    // Evaluation
    // ---------------------------------------------------------------------

    public static Evaluation evaluate(Inputs in) {
        List<Flag> flags = new ArrayList<>();
        LotBook book = matchLots(in.trades());
        Diversification.Result diversification = diversification(in);
        Sizing sizing = sizing(in);

        List<Pillar> pillars = List.of(
                pillar("risk", "Risk Control & Tail Risk", riskComponents(in, diversification, sizing, flags)),
                pillar("behavior", "Behavioral Psychology", behaviorComponents(in, book, sizing, flags)),
                pillar("consistency", "Consistency & Return Quality", consistencyComponents(in, book, flags)),
                pillar("execution", "Execution Discipline", executionComponents(in, sizing, flags)));

        int scored = 0;
        double points = 0.0;
        for (Pillar p : pillars) {
            if (!p.available()) {
                flags.add(missingPillarFlag(p));
                continue;
            }
            scored++;
            points += p.points();
        }
        int rawScore = scored == 0 ? 0 : (int) Math.round(MAX_SCORE * points / (PILLAR_POINTS * scored));
        rawScore = Math.max(0, Math.min(MAX_SCORE, rawScore));

        Evidence evidence = evidence(in, book, sizing);
        List<TierGate> gates = tierGates(evidence);
        List<ScoreCap> caps = scoreCaps(evidence, scored, pillars.size(), gates);
        int score = rawScore;
        for (ScoreCap cap : caps) score = Math.min(score, cap.maxScore());

        List<String> strengths = new ArrayList<>();
        for (Pillar p : pillars) {
            for (Component c : p.components()) {
                if (c.available() && c.score() >= 0.8) strengths.add(c.name() + ": " + c.displayValue());
            }
        }
        flags.sort(Comparator.comparing(Flag::severity));

        int dated = (int) in.trades().stream().filter(TradeEvent::dateKnown).count();
        int observations = in.portfolioReturns() == null ? 0 : in.portfolioReturns().length;
        return new Evaluation(score, rawScore, scored < pillars.size(), scored, scored == 0 ? null : tierFor(score),
                pillars, caps, gates, flags, strengths,
                new Coverage(in.trades().size(), dated, book.closed().size(), observations), diversification);
    }

    // ---------------------------------------------------------------------
    // Evidence caps and tier gates
    // ---------------------------------------------------------------------

    /**
     * The facts the caps and gates are judged on. Values are NaN when unknown; an unknown value never
     * meets a requirement.
     */
    record Evidence(double volatility, double sharpe, double bankrollReturn, double profitFactor,
                    int sells, int datedTrades, long activeDays, int holdings, Allocation allocation,
                    double largestPositionShare, double peakDayShare, double topWinShare) {

        /** Evidence without the discipline facts (unknown, so the high gates stay locked). */
        Evidence(double volatility, double sharpe, double bankrollReturn, double profitFactor, int sells,
                 int datedTrades, long activeDays, int holdings, Allocation allocation) {
            this(volatility, sharpe, bankrollReturn, profitFactor, sells, datedTrades, activeDays, holdings, allocation,
                    Double.NaN, Double.NaN, Double.NaN);
        }
    }

    /**
     * The largest recent buys as shares of capital, for the allocation caps: the largest single buy, the
     * largest buy of a speculative asset, and the most capital deployed on one day. Each keeps the trade
     * (or day) it came from for the explanation.
     */
    record Allocation(TradeSize largestBuy, TradeSize largestSpeculativeBuy, LocalDate busiestDay,
                      double busiestDayShare, int busiestDayBuys,
                      int disciplinedSinceLargest, int disciplinedSinceSpeculative, int disciplinedSinceBusiestDay) {
        static final Allocation NONE = new Allocation(null, null, null, 0.0, 0, 0, 0, 0);
    }

    /** The most capital put to work on one day, and how many buys that day took. */
    /** peakTradeId: the trade at which the day's deployment peaked; discipline is counted from there. */
    record DayDeployment(LocalDate date, double peakShare, int buys, long peakTradeId, List<TradeSize> buysMade) {}

    /**
     * Per day, the peak net capital deployed: buys add their share of capital and sells take theirs back,
     * in the order they were placed. Recycling the same money in and out the same day doesn't add up;
     * putting most of the bankroll to work at once does.
     */
    static Map<LocalDate, DayDeployment> dailyDeployment(Inputs in, Sizing sizing, LocalDate since) {
        Map<LocalDate, List<TradeEvent>> byDay = new TreeMap<>();
        for (TradeEvent t : in.trades()) {
            if (!t.dateKnown() || t.imported() || (since != null && t.date().isBefore(since))) continue;
            byDay.computeIfAbsent(t.date(), d -> new ArrayList<>()).add(t);
        }
        Map<LocalDate, DayDeployment> out = new TreeMap<>();
        for (Map.Entry<LocalDate, List<TradeEvent>> e : byDay.entrySet()) {
            List<TradeEvent> day = new ArrayList<>(e.getValue());
            day.sort(Comparator.comparingLong(TradeEvent::id));
            double running = 0.0, peak = 0.0;
            long peakId = day.get(0).id();
            List<TradeSize> buys = new ArrayList<>();
            for (TradeEvent t : day) {
                TradeSize size = sizing.of(t);
                if (t.side() == Side.BUY) {
                    running += size.share();
                    buys.add(size);
                } else {
                    running = Math.max(0.0, running - size.share());
                }
                if (running > peak) {
                    peak = running;
                    peakId = t.id();
                }
            }
            if (!buys.isEmpty()) out.put(e.getKey(), new DayDeployment(e.getKey(), peak, buys.size(), peakId, buys));
        }
        return out;
    }

    /** Later trades (by recording order) that each moved less than DISCIPLINED_SHARE of capital. */
    static int disciplinedTradesAfter(Inputs in, Sizing sizing, long tradeId) {
        int count = 0;
        for (TradeEvent t : in.trades()) {
            if (t.imported() || !t.dateKnown() || t.id() <= tradeId) continue;
            if (sizing.of(t).share() < DISCIPLINED_SHARE) count++;
        }
        return count;
    }

    static Evidence evidence(Inputs in, LotBook book, Sizing sizing) {
        double[] rp = in.portfolioReturns();
        double vol = Double.NaN, sharpe = Double.NaN;
        if (rp != null && rp.length >= MIN_RISK_OBSERVATIONS) {
            vol = RiskMath.sampleStdDev(rp) * Math.sqrt(in.periodsPerYear());
            if (vol > 0) sharpe = (RiskMath.mean(rp) * in.periodsPerYear() - in.riskFreeRate()) / vol;
        }
        List<TradeEvent> manual = in.trades().stream().filter(t -> !t.imported() && t.dateKnown()).toList();
        long activeDays = manual.isEmpty() ? 0 : ChronoUnit.DAYS.between(
                manual.stream().map(TradeEvent::date).min(Comparator.naturalOrder()).orElseThrow(), in.today()) + 1;
        int sells = (int) manual.stream().filter(t -> t.side() == Side.SELL).count();
        // Discipline facts for the high tiers: biggest position, busiest day, and how much of the profit one trade made
        double peakDay = dailyDeployment(in, sizing, null).values().stream().mapToDouble(DayDeployment::peakShare).max().orElse(Double.NaN);
        double gross = 0.0, best = 0.0;
        for (ClosedLot lot : book.closed()) {
            if (lot.pnl() > 0) {
                gross += lot.pnl();
                best = Math.max(best, lot.pnl());
            }
        }
        return new Evidence(vol, sharpe, bankrollReturn(in, book).orElse(Double.NaN),
                profitFactor(in, book).orElse(Double.NaN), sells, manual.size(), activeDays, in.holdings().size(),
                allocation(in, sizing), largestPosition(in, sizing).map(PositionShare::share).orElse(Double.NaN),
                peakDay, gross > 0 ? best / gross : Double.NaN);
    }

    static Allocation allocation(Inputs in, Sizing sizing) {
        if (!(sizing.capitalBase() > 0)) return Allocation.NONE;
        LocalDate since = in.today().minusDays(ALLOCATION_WINDOW_DAYS);
        TradeSize largest = null, largestSpeculative = null;
        for (TradeEvent t : in.trades()) {
            if (t.side() != Side.BUY || !t.dateKnown() || t.imported() || t.date().isBefore(since)) continue;
            TradeSize size = sizing.of(t);
            if (largest == null || size.share() > largest.share()) largest = size;
            if (size.asset().speculative() && (largestSpeculative == null || size.share() > largestSpeculative.share())) {
                largestSpeculative = size;
            }
        }
        DayDeployment busiest = null;
        for (DayDeployment d : dailyDeployment(in, sizing, since).values()) {
            if (busiest == null || d.peakShare() > busiest.peakShare()) busiest = d;
        }
        return new Allocation(largest, largestSpeculative,
                busiest == null ? null : busiest.date(), busiest == null ? 0.0 : busiest.peakShare(), busiest == null ? 0 : busiest.buys(),
                largest == null ? 0 : disciplinedTradesAfter(in, sizing, largest.trade().id()),
                largestSpeculative == null ? 0 : disciplinedTradesAfter(in, sizing, largestSpeculative.trade().id()),
                busiest == null ? 0 : disciplinedTradesAfter(in, sizing, busiest.peakTradeId()));
    }

    /**
     * Gate thresholds: {min score, return on S0, recorded trades, Sharpe, largest position, peak day deployment,
     * best trade's share of profits}. Tier 5 needs results and activity only, reachable as soon as they are
     * earned. Tier 3 and Tier 1 also need discipline: risk-adjusted returns, controlled position sizes, measured
     * deployment, and profits that don't hang on one lucky trade (NaN: not required).
     */
    private static final double[][] GATES = {
            {600, 0.05, 10, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
            {750, 0.15, 20, 1.0, 0.30, 0.50, 0.50},
            {900, 0.30, 40, 1.5, 0.20, 0.25, 0.35},
    };

    static List<TierGate> tierGates(Evidence e) {
        // Judge each value at the precision it is shown with, so "+5.0%" never fails "at least 5%"
        double ret = round(e.bankrollReturn(), 3), sharpe = round(e.sharpe(), 2);
        List<TierGate> gates = new ArrayList<>();
        for (double[] g : GATES) {
            int min = (int) g[0];
            int trades = (int) g[2];
            List<Requirement> reqs = new ArrayList<>(List.of(
                    new Requirement("Return on capital (S₀) of at least " + pct0(g[1]), ret >= g[1],
                            Double.isNaN(e.bankrollReturn()) ? "no data" : signedPct1(e.bankrollReturn()),
                            "Return on Capital", "≥ " + signedPct1(g[1]), progress(ret, g[1])),
                    new Requirement("At least " + trades + " recorded trades", e.datedTrades() >= trades,
                            e.datedTrades() + " trade" + (e.datedTrades() == 1 ? "" : "s"),
                            "Trade Count", "≥ " + trades + " trades", progress(e.datedTrades(), trades))));
            if (!Double.isNaN(g[3])) {
                reqs.add(new Requirement("Sharpe ratio of at least " + fmt1(g[3]), sharpe >= g[3],
                        Double.isNaN(e.sharpe()) ? "no data" : fmt2(e.sharpe()),
                        "Risk-Adjusted Return (Sharpe)", "≥ " + fmt2(g[3]), progress(sharpe, g[3])));
                reqs.add(atMost("Largest position at most " + pct0(g[4]) + " of capital", "Position Sizing",
                        e.largestPositionShare(), g[4], "largest position ", "no open positions"));
                reqs.add(atMost("At most " + pct0(g[5]) + " of capital deployed in one day", "Deployment Pacing",
                        e.peakDayShare(), g[5], "busiest day ", "no dated buys"));
                reqs.add(atMost("No single trade above " + pct0(g[6]) + " of total profit", "Profit Concentration",
                        e.topWinShare(), g[6], "best trade made ", "no profitable closed trades"));
            }
            gates.add(new TierGate(min, tierFor(min).rank() == 1 ? "Tier 1" : "Tier " + tierFor(min).rank() + " and above",
                    reqs.stream().allMatch(Requirement::met), List.copyOf(reqs)));
        }
        return gates;
    }

    /** A "no more than" requirement, met at or below the limit; progress falls as the actual value exceeds it. */
    private static Requirement atMost(String label, String name, double actual, double limit, String prefix, String unknown) {
        double shown = round(actual, 3);
        boolean met = !Double.isNaN(actual) && shown <= limit;
        String text = Double.isNaN(actual) ? unknown : prefix + pct0(actual) + (name.equals("Profit Concentration") ? " of profits" : " of capital");
        double progress = Double.isNaN(actual) ? 0.0 : met ? 1.0 : Math.max(0.0, Math.min(1.0, limit / actual));
        return new Requirement(label, met, text, name, "≤ " + pct0(limit), progress);
    }

    /** Share of the way from zero to the target, 0–1; unknown or negative values count as no progress. */
    static double progress(double actual, double target) {
        if (Double.isNaN(actual) || !(target > 0)) return 0.0;
        return Math.max(0.0, Math.min(1.0, actual / target));
    }

    /** "Tier 5 Advancement Locked" for the gate guarding scores of minScore and above. */
    static String lockTitle(int minScore) {
        return "Tier " + tierFor(minScore).rank() + " Advancement Locked";
    }

    static List<ScoreCap> scoreCaps(Evidence e, int pillarsScored, int pillarCount, List<TierGate> gates) {
        List<ScoreCap> caps = new ArrayList<>(allocationCaps(e.allocation()));
        // A flat return only shows stagnation once the portfolio has had time to move
        boolean young = e.datedTrades() > 0 && e.activeDays() <= NEW_PORTFOLIO_DAYS;
        boolean flat = (!Double.isNaN(e.volatility()) && e.volatility() < STAGNANT_VOLATILITY)
                || (!young && !Double.isNaN(e.bankrollReturn()) && Math.abs(e.bankrollReturn()) < STAGNANT_RETURN && e.sells() == 0);
        if (flat) {
            caps.add(new ScoreCap("stagnant", STAGNANT_CAP, "Stagnant portfolio",
                    "The portfolio has barely moved (" + (Double.isNaN(e.volatility()) ? "" : pct1(e.volatility()) + " annual volatility, ")
                            + (Double.isNaN(e.bankrollReturn()) ? "no return data" : signedPct1(e.bankrollReturn()) + " on capital")
                            + "). Holding still proves nothing about skill, so the score stays at Tier 8 or below."));
        }
        if (pillarsScored < pillarCount) {
            caps.add(new ScoreCap("provisional", UNPROVEN_CAP, "Provisional score",
                    "Only " + pillarsScored + " of " + pillarCount + " pillars could be scored; the rest is not assumed."));
        }
        if (e.sells() < MIN_SELLS) {
            caps.add(new ScoreCap("no-sells", UNPROVEN_CAP, e.sells() == 0 ? "No sells recorded" : "Only one sell recorded",
                    "Gains are only proven when trades are closed. Record at least " + MIN_SELLS + " sells to lift this cap."));
        }
        if (e.datedTrades() < MIN_DATED_TRADES) {
            caps.add(new ScoreCap("thin-history", UNPROVEN_CAP, "Limited trade history",
                    e.datedTrades() + " dated trade" + (e.datedTrades() == 1 ? "" : "s") + " recorded; at least "
                            + MIN_DATED_TRADES + " are needed to judge execution."));
        }
        if (!(e.bankrollReturn() > 0)) {
            caps.add(new ScoreCap("no-growth", UNPROVEN_CAP, "No positive return on capital",
                    Double.isNaN(e.bankrollReturn()) ? "Return on capital can't be measured yet."
                            : "The portfolio is " + signedPct1(e.bankrollReturn()) + " on the capital put in."));
        }
        for (TierGate gate : gates) {
            if (gate.unlocked()) continue;
            String missing = String.join("; ", gate.requirements().stream().filter(r -> !r.met())
                    .map(r -> r.name() + " " + r.actual() + " (target " + r.target() + ")").toList());
            caps.add(new ScoreCap("gate-" + gate.minScore(), gate.minScore() - 1, lockTitle(gate.minScore()),
                    "Still needed: " + missing + "."));
            break; // higher gates are implied by the first locked one
        }
        caps.sort(Comparator.comparingInt(ScoreCap::maxScore));
        return caps;
    }

    /** Caps for oversized or impulsive recent buys: Tier 10 for reckless speculative bets, Tier 9 for over-allocation. */
    static List<ScoreCap> allocationCaps(Allocation a) {
        List<ScoreCap> caps = new ArrayList<>();
        TradeSize spec = a.largestSpeculativeBuy();
        boolean specCooling = spec != null && a.disciplinedSinceSpeculative() < COOLING_RELEASE_TRADES;
        if (specCooling && spec.share() >= RECKLESS_SPECULATIVE_SHARE) {
            caps.add(new ScoreCap("reckless-allocation", RECKLESS_CAP, "Oversized Speculative Position",
                    "Putting " + pct0(RECKLESS_SPECULATIVE_SHARE) + " or more of capital into one speculative coin triggers a "
                            + "risk cooling period to protect the account. " + release(a.disciplinedSinceSpeculative()) + " On "
                            + spec.trade().date() + ", " + pct1(spec.share()) + " went into " + spec.trade().name() + ", a "
                            + spec.asset().label() + "."));
        } else if (specCooling && spec.share() >= IMPULSIVE_SPECULATIVE_SHARE) {
            caps.add(new ScoreCap("speculative-over-allocation", IMPULSIVE_CAP, "Speculative Concentration Warning",
                    "Putting over " + pct0(IMPULSIVE_SPECULATIVE_SHARE) + " of capital into one speculative coin triggers a "
                            + "risk cooling period to encourage disciplined sizing. " + release(a.disciplinedSinceSpeculative())
                            + " On " + spec.trade().date() + ", " + pct1(spec.share()) + " went into " + spec.trade().name()
                            + ", a " + spec.asset().label() + "."));
        }
        TradeSize largest = a.largestBuy();
        if (largest != null && largest.share() >= IMPULSIVE_TRADE_SHARE && a.disciplinedSinceLargest() < COOLING_RELEASE_TRADES) {
            caps.add(new ScoreCap("over-allocation", IMPULSIVE_CAP, "Position Concentration Warning",
                    "A single buy of " + pct0(IMPULSIVE_TRADE_SHARE) + " or more of capital triggers a risk cooling period "
                            + "to encourage disciplined sizing. " + release(a.disciplinedSinceLargest()) + " On "
                            + largest.trade().date() + ", " + pct1(largest.share()) + " went into " + largest.trade().name() + "."));
        }
        if (a.busiestDay() != null && a.busiestDayShare() >= IMPULSIVE_DAY_SHARE
                && a.disciplinedSinceBusiestDay() < COOLING_RELEASE_TRADES) {
            caps.add(new ScoreCap("impulsive-deployment", IMPULSIVE_CAP, "Rapid Capital Deployment Warning",
                    "Deploying over " + pct0(IMPULSIVE_DAY_SHARE) + " of capital in a single day triggers a risk cooling "
                            + "period to encourage disciplined scaling. " + release(a.disciplinedSinceBusiestDay()) + " On "
                            + a.busiestDay() + ", " + pct1(a.busiestDayShare()) + " went into " + a.busiestDayBuys() + " buy"
                            + (a.busiestDayBuys() == 1 ? "" : "s") + "."));
        }
        return caps;
    }

    /** How a cooling period ends: by trading with discipline, or with time. */
    private static String release(int disciplinedSoFar) {
        return "It lifts after " + COOLING_RELEASE_TRADES + " further trades each under " + pct0(DISCIPLINED_SHARE)
                + " of capital (" + Math.min(disciplinedSoFar, COOLING_RELEASE_TRADES) + " of " + COOLING_RELEASE_TRADES
                + " so far), or after " + ALLOCATION_WINDOW_DAYS + " days.";
    }

    private static Pillar pillar(String key, String name, List<Component> components) {
        double weight = 0.0, earned = 0.0;
        for (Component c : components) {
            if (!c.available()) continue;
            weight += c.weight();
            earned += c.points();
        }
        double coverage = weight / PILLAR_POINTS;
        boolean available = coverage >= MIN_PILLAR_COVERAGE - 1e-9;
        double points = available ? PILLAR_POINTS * earned / weight : 0.0;
        return new Pillar(key, name, available, points, PILLAR_POINTS, coverage, components);
    }

    // ---------------------------------------------------------------------
    // Pillar 1: Risk Control & Tail Risk
    // ---------------------------------------------------------------------

    static List<Component> riskComponents(Inputs in, Diversification.Result div, Sizing sizing, List<Flag> flags) {
        List<Component> out = new ArrayList<>();
        double[] rp = in.portfolioReturns();
        int periods = in.periodsPerYear();
        String noHistory = in.historyUnavailableReason() != null ? in.historyUnavailableReason()
                : "Price history for your holdings is unavailable.";
        double[] allReturns = rp; // the 7-day beta uses the latest week even when the long history is short
        // Annualizing a handful of days makes volatility (and everything built on it) meaningless
        if (rp != null && rp.length < MIN_RISK_OBSERVATIONS) {
            noHistory = "Needs at least " + MIN_RISK_OBSERVATIONS + " daily returns of price history; " + rp.length + " available.";
            rp = null;
        }
        double volatility = rp == null ? Double.NaN : RiskMath.sampleStdDev(rp) * Math.sqrt(periods);
        boolean hasHistory = rp != null && volatility > 0;
        double annualReturn = rp == null ? Double.NaN : RiskMath.mean(rp) * periods;

        // Sharpe ratio
        if (hasHistory) {
            double sharpe = (annualReturn - in.riskFreeRate()) / volatility;
            out.add(component("sharpe", "Sharpe ratio", 60, sharpe, fmt2(sharpe), lin(sharpe, -0.5, 2.0),
                    "(annualized return − " + pct1(in.riskFreeRate()) + " risk-free) / annualized volatility, "
                            + rp.length + " daily returns"));
            if (sharpe < 0) {
                flags.add(new Flag("negative-sharpe", Severity.MEDIUM, "risk", "Negative Risk-Adjusted Return",
                        "Sharpe ratio " + fmt2(sharpe) + ": over the last " + rp.length + " days the portfolio returned "
                                + pct1(annualReturn) + " a year, below the " + pct1(in.riskFreeRate()) + " risk-free rate.",
                        "The risk taken hasn't been paid for. Review which positions drag on returns and size them down, "
                                + "or hold part of the portfolio in lower-volatility assets."));
            }
        } else {
            out.add(unavailable("sharpe", "Sharpe ratio", 60, noHistory));
        }

        // Sortino ratio: only downside deviation (returns below the risk-free rate) counts as risk
        if (hasHistory) {
            double rfDaily = in.riskFreeRate() / periods;
            double sumSq = 0.0;
            for (double r : rp) {
                double shortfall = Math.min(r - rfDaily, 0.0);
                sumSq += shortfall * shortfall;
            }
            double downside = Math.sqrt(sumSq / rp.length) * Math.sqrt(periods);
            double excess = annualReturn - in.riskFreeRate();
            if (downside > 0) {
                double sortino = excess / downside;
                out.add(component("sortino", "Sortino ratio", 50, sortino, fmt2(sortino), lin(sortino, -0.5, 3.0),
                        "(annualized return − risk-free) / annualized downside deviation " + pct1(downside)));
            } else {
                out.add(component("sortino", "Sortino ratio", 50, null, "∞ (no down days)", excess > 0 ? 1.0 : 0.0,
                        "No daily return fell below the risk-free rate"));
            }
        } else {
            out.add(unavailable("sortino", "Sortino ratio", 50, noHistory));
        }

        // Volatility level: one-day outliers are trimmed first, then scored on a smooth curve
        if (hasHistory) {
            double robustVol = RiskMath.sampleStdDev(trimOutliers(rp)) * Math.sqrt(periods);
            out.add(component("volatility", "Annualized volatility", 30, robustVol, pct1(robustVol), volatilityScore(robustVol),
                    "Standard deviation of " + rp.length + " daily returns × √" + periods + ", with one-day outliers beyond "
                            + fmt1(VOLATILITY_TRIM_SIGMAS) + " robust standard deviations trimmed; full marks up to 25%, "
                            + "about half at 70%, near zero above 150%"));
            if (robustVol > 0.5) {
                flags.add(new Flag("high-volatility", robustVol > 0.8 ? Severity.HIGH : Severity.MEDIUM, "risk",
                        "High Volatility", "Annualized volatility is " + pct1(robustVol)
                        + ": a typical year could see the portfolio swing by this much in either direction.",
                        "Reduce position sizes or blend in less volatile assets until volatility matches what you can hold through."));
            }
        } else {
            out.add(unavailable("volatility", "Annualized volatility", 30, noHistory));
        }

        // 7-day benchmark beta: sensitivity to the market (BTC/ETH) over the most recent week
        benchmarkBetaComponent(in, allReturns, noHistory, flags, out);

        diversificationComponents(div, flags, out);
        positionSizingComponent(in, sizing, flags, out);
        return out;
    }

    /**
     * Diversification, worth 60 of the pillar's 250 points: concentration (HHI and effective number of
     * holdings), the weighted average pairwise correlation, and the correlation-adjusted effective number
     * of independent bets. Concentration needs only current values; the other two need price history.
     */
    static void diversificationComponents(Diversification.Result div, List<Flag> flags, List<Component> out) {
        if (div == null) {
            out.add(unavailable("concentration", "Concentration (HHI)", 20, "No current holdings."));
            out.add(unavailable("correlation", "Average pairwise correlation", 20, "No current holdings."));
            out.add(unavailable("independent-bets", "Independent bets (correlation-adjusted)", 20, "No current holdings."));
            return;
        }
        double hhi = div.hhi();
        out.add(component("concentration", "Concentration (HHI)", 20, hhi,
                fmt2(hhi) + " · " + fmt1(div.effectiveHoldings()) + " effective of " + div.holdings(),
                div.concentrationScore(), "HHI = Σ w² of portfolio weights; effective holdings N = 1 / HHI. "
                        + "1.00 means a single holding; 0.20 (5 equal holdings) or lower scores full marks"));
        Diversification.Position top = div.positions().stream()
                .max(Comparator.comparingDouble(Diversification.Position::weight)).orElseThrow();
        if (hhi >= 0.5) {
            flags.add(new Flag("concentration", top.weight() >= 0.8 ? Severity.HIGH : Severity.MEDIUM, "risk",
                    "Concentration Risk Flagged", pct1(top.weight()) + " of the portfolio is in " + top.name()
                    + " (HHI " + fmt2(hhi) + ", " + fmt1(div.effectiveHoldings()) + " effective holdings out of "
                    + div.holdings() + ").",
                    "Cap single positions at a fixed share of the portfolio (for example 25–40%) so one coin can't decide the outcome."));
        }

        if (div.holdings() == 1) {
            // Correlation is undefined for one coin; the concentration and independent-bets scores already
            // reflect having a single position, so it is left out rather than scored as zero
            out.add(unavailable("correlation", "Average pairwise correlation", 20, "Needs two or more holdings."));
            out.add(component("independent-bets", "Independent bets (correlation-adjusted)", 20, 1.0, "1.0",
                    lin(1.0, Diversification.BETS_WORST, Diversification.BETS_BEST), "A single holding is one bet"));
            return;
        }
        if (!div.correlationAvailable()) {
            out.add(unavailable("correlation", "Average pairwise correlation", 20, div.correlationReason()));
            out.add(unavailable("independent-bets", "Independent bets (correlation-adjusted)", 20, div.correlationReason()));
            return;
        }
        double rho = div.averageCorrelation();
        out.add(component("correlation", "Average pairwise correlation", 20, rho, "ρ̄ " + fmt2(rho),
                div.correlationScore(), "Weight-averaged correlation of daily returns across all " + div.pairs().size()
                        + " pairs over " + div.observations() + " days; −0.20 or lower scores full marks, 0.90 or higher none"));
        if (div.effectiveBets() != null) {
            double bets = div.effectiveBets();
            out.add(component("independent-bets", "Independent bets (correlation-adjusted)", 20, bets,
                    fmt1(bets) + " of " + fmt1(div.effectiveHoldings()) + " effective holdings",
                    div.betsScore(), "Diversification ratio² = (Σ wσ / σ_portfolio)²: how many uncorrelated positions "
                            + "the portfolio's risk is equivalent to; 4 or more scores full marks"));
        } else {
            out.add(unavailable("independent-bets", "Independent bets (correlation-adjusted)", 20,
                    "The portfolio's value didn't move over the history, so its risk can't be decomposed."));
        }

        // Lockstep pairs can hide behind a small uncorrelated holding that pulls the average down, so the
        // share of capital in them counts as well as the average
        List<Diversification.Pair> lockstep = div.pairs().stream()
                .filter(p -> p.correlation() >= Diversification.HIGH_CORRELATION).toList();
        Set<String> lockstepNames = new HashSet<>();
        for (Diversification.Pair p : lockstep) { lockstepNames.add(p.a()); lockstepNames.add(p.b()); }
        double lockstepWeight = div.positions().stream().filter(p -> lockstepNames.contains(p.name()))
                .mapToDouble(Diversification.Position::weight).sum();
        if (rho >= 0.7 || lockstepWeight >= 0.5) {
            Double bets = div.effectiveBets();
            String pairs = lockstep.isEmpty() ? "" : " Most correlated: " + String.join(", ", lockstep.stream().limit(3)
                    .map(p -> p.a() + " / " + p.b() + " (" + fmt2(p.correlation()) + ")").toList()) + ".";
            String share = lockstep.isEmpty() ? "" : " " + pct0(lockstepWeight) + " of the capital is in holdings that move in lockstep.";
            String betsText = bets == null ? "" : " Your " + div.holdings() + " holdings behave like "
                    + fmt1(bets) + " independent bet" + (bets < 1.05 ? "" : "s") + ".";
            boolean severe = rho >= 0.85 || (bets != null && bets < 1.25);
            flags.add(new Flag("correlated-holdings", severe ? Severity.HIGH : Severity.MEDIUM, "risk",
                    "Holdings Move Together", "Average pairwise correlation is " + fmt2(rho) + "." + share + betsText + pairs,
                    "Adding more coins that follow the same market adds little protection. Diversify across assets "
                            + "with different return drivers (low or negative correlation), or hold a stable reserve."));
        }
    }

    /** Diversification of the current holdings, with correlations when aligned per-holding returns are supplied. */
    static Diversification.Result diversification(Inputs in) {
        List<Holding> held = in.holdings().stream().filter(h -> h.value() > 0).toList();
        if (held.isEmpty()) return null;
        double[][] returns = in.assetReturns();
        boolean aligned = returns != null && held.size() == in.holdings().size()
                && (returns.length == 0 || returns[0].length == held.size());
        String reason = in.historyUnavailableReason() != null ? in.historyUnavailableReason()
                : "Price history for your holdings is unavailable.";
        return Diversification.analyze(held.stream().map(Holding::coinId).toList(), held.stream().map(Holding::name).toList(),
                held.stream().mapToDouble(Holding::value).toArray(), aligned ? returns : null, in.periodsPerYear(), reason);
    }

    /**
     * Beta over the most recent BETA_DAYS daily returns against the market (50/50 BTC/ETH when supplied, else the
     * benchmark). Within ±1 it scores in full, falling smoothly to zero at ±2.5.
     */
    static void benchmarkBetaComponent(Inputs in, double[] rp, String noHistory, List<Flag> flags, List<Component> out) {
        double[] market = in.recentMarketReturns();
        String against = "BTC/ETH";
        if (market == null && in.benchmarkReturns() != null) {
            market = in.benchmarkReturns();
            against = "BTC";
        }
        if (rp == null || market == null) {
            out.add(unavailable("benchmark-beta", "7-day benchmark beta", 25, noHistory));
            return;
        }
        int days = Math.min(BETA_DAYS, Math.min(rp.length, market.length));
        if (days < MIN_BETA_DAYS) {
            out.add(unavailable("benchmark-beta", "7-day benchmark beta", 25, "Needs at least " + MIN_BETA_DAYS + " days of returns."));
            return;
        }
        double[] p = Arrays.copyOfRange(rp, rp.length - days, rp.length);
        double[] m = Arrays.copyOfRange(market, market.length - days, market.length);
        double var = RiskMath.sampleVariance(m);
        if (!(var > 0)) {
            out.add(unavailable("benchmark-beta", "7-day benchmark beta", 25, "The market didn't move over the last " + days + " days."));
            return;
        }
        double beta = RiskMath.covariance(p, m) / var;
        double excess = Math.max(0.0, Math.abs(beta) - 1.0);
        out.add(component("benchmark-beta", "7-day benchmark beta", 25, beta, "β " + fmt2(beta) + " · " + days + "d vs " + against,
                inv(excess, 0.0, 1.5), "Covariance with the market over the last " + days + " daily returns ÷ the market's variance; "
                        + "within ±1 scores full marks, ±2.5 or beyond none"));
        if (Math.abs(beta) > 1.5) {
            flags.add(new Flag("high-beta", Math.abs(beta) > 2.0 ? Severity.MEDIUM : Severity.LOW, "risk", "High Market Sensitivity",
                    "Over the last " + days + " days the portfolio moved " + fmt2(Math.abs(beta)) + "× as much as the market ("
                            + against + ").",
                    "Blend in steadier assets or trim the most aggressive coins so a market drop doesn't hit the portfolio harder than the market."));
        }
    }

    /** Daily returns with one-day outliers (beyond VOLATILITY_TRIM_SIGMAS robust deviations of the median) clamped. */
    static double[] trimOutliers(double[] r) {
        double[] sorted = r.clone();
        Arrays.sort(sorted);
        double median = RiskMath.quantile(sorted, 0.5);
        double[] dev = new double[r.length];
        for (int i = 0; i < r.length; i++) dev[i] = Math.abs(r[i] - median);
        double mad = RiskMath.quantile(dev, 0.5) * 1.4826; // matches the standard deviation for normal data
        if (!(mad > 0)) return r;
        double lo = median - VOLATILITY_TRIM_SIGMAS * mad, hi = median + VOLATILITY_TRIM_SIGMAS * mad;
        double[] out = new double[r.length];
        for (int i = 0; i < r.length; i++) out[i] = Math.max(lo, Math.min(hi, r[i]));
        return out;
    }

    /** Smooth volatility curve: full marks up to 25% a year, about 0.84 at 50%, 0.36 at 85%, near zero above 150%. */
    static double volatilityScore(double annualVol) {
        double excess = Math.max(0.0, annualVol - 0.25) / 0.6;
        return Math.exp(-excess * excess);
    }

    // ---------------------------------------------------------------------
    // Pillar 2: Behavioral Psychology
    // ---------------------------------------------------------------------

    static List<Component> behaviorComponents(Inputs in, LotBook book, Sizing sizing, List<Flag> flags) {
        List<Component> out = new ArrayList<>();

        // Disposition effect: losers held longer than winners (Shefrin & Statman, 1985)
        Optional<Disposition> disposition = disposition(book.closed());
        if (disposition.isPresent()) {
            Disposition d = disposition.get();
            out.add(component("disposition", "Disposition effect", 75, d.ratio(), fmt1(d.ratio()) + "× loser/winner hold time",
                    inv(d.ratio(), 1.0, 3.0), "Average hold: losers " + fmt1(d.loserDays()) + " days ("
                            + d.losers() + "), winners " + fmt1(d.winnerDays()) + " days (" + d.winners() + ")"));
            if (d.ratio() >= 1.5) {
                flags.add(new Flag("disposition", d.ratio() >= 2.5 ? Severity.HIGH : Severity.MEDIUM, "behavior",
                        "Disposition Effect Flagged", "Losers held " + fmt1(d.ratio()) + "x longer than winners ("
                        + fmt1(d.loserDays()) + " vs " + fmt1(d.winnerDays()) + " days on average).",
                        "Decide the exit before you enter: set a stop-loss or a review date for every position, "
                                + "and judge a losing trade on its prospects, not on getting back to break-even."));
            }
        } else {
            out.add(unavailable("disposition", "Disposition effect", 75,
                    "Needs at least one winning and one losing sell with known buy and sell dates."));
        }

        // FOMO chasing: buys made right after a sharp rally, weighted by how much capital each one committed
        int evaluableBuys = 0, fomoBuys = 0;
        double fomoRunUpSum = 0.0, fomoImpact = 0.0;
        TradeSize largestFomo = null;
        for (TradeEvent t : in.trades()) {
            if (t.side() != Side.BUY || !t.dateKnown()) continue;
            Double now = priceOn(in, t.coinId(), t.date());
            Double before = priceOn(in, t.coinId(), t.date().minusDays(FOMO_LOOKBACK_DAYS));
            if (now == null || before == null || before <= 0) continue;
            evaluableBuys++;
            double runUp = now / before - 1.0;
            if (runUp >= FOMO_RUN_UP) {
                fomoBuys++;
                fomoRunUpSum += runUp;
                TradeSize size = sizing.of(t);
                fomoImpact += size.impact();
                if (largestFomo == null || size.share() > largestFomo.share()) largestFomo = size;
            }
        }
        if (evaluableBuys >= MIN_EVALUABLE_TRADES) {
            double rate = fomoImpact / evaluableBuys;
            out.add(component("fomo", "FOMO chasing", 50, rate, fomoBuys + " of " + evaluableBuys + " buys · size-weighted "
                            + fmt2(rate), inv(rate, 0.0, 0.5),
                    "Buys made after the coin rose " + pct0(FOMO_RUN_UP) + "+ in the previous " + FOMO_LOOKBACK_DAYS
                            + " days, each counted by its share of capital (" + SIZING_RULE + "), per evaluable buy"));
            if (fomoBuys > 0 && fomoImpact == 0.0) {
                flags.add(new Flag("fomo", Severity.INFO, "behavior", "FOMO Buys Too Small to Matter",
                        fomoBuys + " of " + evaluableBuys + " buys came after a rally of " + pct0(FOMO_RUN_UP)
                                + " or more, but each was under " + pct0(DUST_SHARE) + " of your capital, so they aren't penalized.",
                        "Small experiments are fine; keep rally-chasing buys this small if you make them at all."));
            } else if (fomoBuys > 0) {
                boolean oversized = largestFomo.share() >= FULL_IMPACT_SHARE;
                Severity severity = rate >= 0.5 || (oversized && largestFomo.asset().speculative()) ? Severity.HIGH
                        : rate >= 0.25 || oversized ? Severity.MEDIUM : Severity.LOW;
                flags.add(new Flag("fomo", severity, "behavior", "FOMO Chasing Flagged",
                        fomoBuys + " of " + evaluableBuys + " buys came after a " + FOMO_LOOKBACK_DAYS + "-day rally of "
                                + pct0(FOMO_RUN_UP) + " or more (average +" + pct1(fomoRunUpSum / fomoBuys) + "). "
                                + "Position sizing: the largest put " + pct1(largestFomo.share()) + " of your capital into "
                                + largestFomo.trade().name() + (largestFomo.asset().speculative()
                                        ? ", a " + largestFomo.asset().label() : "") + ".",
                        "Wait for a pullback or scale in over several days instead of buying into a spike; "
                                + "if you do chase, keep the position to 1–2% of capital and write down the entry reason first."));
            }
        } else {
            out.add(unavailable("fomo", "FOMO chasing", 50, "Needs at least " + MIN_EVALUABLE_TRADES
                    + " dated buys within the last year of price history."));
        }

        // Panic capitulation: selling into a sharp 24h fall, at the local low
        int evaluableSells = 0, panicSells = 0;
        for (TradeEvent t : in.trades()) {
            if (t.side() != Side.SELL || !t.dateKnown()) continue;
            Double day = priceOn(in, t.coinId(), t.date());
            Double prior = priceOn(in, t.coinId(), t.date().minusDays(1));
            if (day == null || prior == null || prior <= 0) continue;
            evaluableSells++;
            double drop = day / prior - 1.0;
            double low = day;
            for (int k = -PANIC_WINDOW_DAYS; k <= PANIC_WINDOW_DAYS; k++) {
                Double p = priceOn(in, t.coinId(), t.date().plusDays(k));
                if (p != null) low = Math.min(low, p);
            }
            if (drop <= PANIC_DROP && day <= low * (1 + PANIC_TROUGH_TOLERANCE)) panicSells++;
        }
        if (evaluableSells >= MIN_EVALUABLE_TRADES) {
            double rate = (double) panicSells / evaluableSells;
            out.add(component("panic", "Panic capitulation", 45, rate, panicSells + " of " + evaluableSells + " sells (" + pct0(rate) + ")",
                    inv(rate, 0.0, 0.5), "Sells on a day the coin fell " + pct0(-PANIC_DROP) + "+ in 24h, at its lowest price within ±"
                            + PANIC_WINDOW_DAYS + " days"));
            if (panicSells > 0) {
                flags.add(new Flag("panic", rate >= 0.25 ? Severity.HIGH : Severity.MEDIUM, "behavior", "Panic Capitulation Flagged",
                        panicSells + " of " + evaluableSells + " sells happened during a 24h drop of " + pct0(-PANIC_DROP)
                                + " or more, at the local low.",
                        "Set exit levels in advance and avoid selling during a crash; if you must act, reduce in steps rather than all at once."));
            }
        } else {
            out.add(unavailable("panic", "Panic capitulation", 45, "Needs at least " + MIN_EVALUABLE_TRADES
                    + " dated sells within the last year of price history."));
        }

        speculativeAccumulationComponent(sizing, flags, out);
        deploymentPacingComponent(in, sizing, flags, out);
        return out;
    }

    record Disposition(double ratio, double loserDays, double winnerDays, int losers, int winners) {}

    static Optional<Disposition> disposition(List<ClosedLot> closed) {
        double winDays = 0, loseDays = 0;
        int winners = 0, losers = 0;
        for (ClosedLot lot : closed) {
            if (!lot.datesKnown()) continue;
            if (lot.pnl() > 0) {
                winners++;
                winDays += lot.holdDays();
            } else if (lot.pnl() < 0) {
                losers++;
                loseDays += lot.holdDays();
            }
        }
        if (winners == 0 || losers == 0) return Optional.empty();
        double w = winDays / winners, l = loseDays / losers;
        return Optional.of(new Disposition(l / w, l, w, losers, winners));
    }

    // ---------------------------------------------------------------------
    // Position sizing (Risk and Behavioral pillars)
    // ---------------------------------------------------------------------

    static final String SIZING_RULE = "under 1% of capital counts 0, 10% or more counts 1, and in a speculative asset "
            + "every 5% above 10% counts one more";

    /**
     * What is known about a coin's risk. speculative: a market cap under $250M, a listing without a verified
     * market cap, or annualized volatility of 100% or more. Unknown values never make a coin speculative.
     */
    record AssetRisk(String coinId, Double marketCap, Double volatility, boolean assessed, boolean speculative) {
        String label() {
            List<String> why = new ArrayList<>();
            if (marketCap != null && marketCap < MICRO_CAP) why.add(compactMoney(marketCap) + " market cap");
            if (marketCap == null && assessed && speculative && (volatility == null || volatility < SPECULATIVE_VOLATILITY)) {
                why.add("no verified market cap");
            }
            if (volatility != null && volatility >= SPECULATIVE_VOLATILITY) why.add(pct0(volatility) + " annual volatility");
            String kind = marketCap != null && marketCap < MICRO_CAP ? "micro-cap asset"
                    : marketCap == null && assessed ? "unverified asset" : "high-volatility asset";
            return why.isEmpty() ? kind : kind + " (" + String.join(", ", why) + ")";
        }
    }

    /** A trade's share of the capital base and the weight it carries in penalties. */
    record TradeSize(TradeEvent trade, double share, double impact, AssetRisk asset) {}

    /**
     * Every trade's size as a share of the capital base. With a starting bankroll S₀ that is
     * max(S₀, cash + holdings at market). Without one it is max(peak capital invested, current portfolio value).
     * Peak capital invested is the largest cost basis of open positions at any point in the ledger: the
     * bankroll actually put to work. Unlike S₀ (all buys added up) it doesn't grow when the same money is
     * traded in and out, so frequent traders' trades aren't made to look small.
     */
    record Sizing(double capitalBase, boolean fromBankroll, Map<Long, TradeSize> trades) {
        TradeSize of(TradeEvent t) {
            return trades.get(t.id());
        }
    }

    static Sizing sizing(Inputs in) {
        double holdingsValue = in.holdings().stream().mapToDouble(Holding::value).sum();
        Double s0 = in.initialBankroll();
        boolean fromBankroll = s0 != null && s0 > 0;
        double base;
        if (fromBankroll) {
            // Account value = cash left from S₀ plus the holdings at market
            double netInvested = 0.0;
            for (TradeEvent t : in.trades()) netInvested += t.side() == Side.BUY ? t.notional() : -t.notional();
            base = Math.max(s0, s0 - netInvested + holdingsValue);
        } else {
            base = Math.max(peakCapitalInvested(in.trades()), holdingsValue);
        }
        Map<String, AssetRisk> assets = new HashMap<>();
        Map<Long, TradeSize> sizes = new HashMap<>();
        for (TradeEvent t : in.trades()) {
            AssetRisk asset = assets.computeIfAbsent(t.coinId(), id -> assetRisk(in, id));
            double share = base > 0 ? t.notional() / base : 0.0;
            sizes.put(t.id(), new TradeSize(t, share, impact(share, asset.speculative()), asset));
        }
        return new Sizing(base, fromBankroll, sizes);
    }

    /** Largest cost basis of open positions over the ledger, with sells removing lots first-in, first-out. */
    static double peakCapitalInvested(List<TradeEvent> trades) {
        List<TradeEvent> ordered = new ArrayList<>(trades);
        ordered.sort(Comparator.comparing((TradeEvent t) -> t.dateKnown() ? t.date() : LocalDate.MIN)
                .thenComparingLong(TradeEvent::id));
        Map<String, Deque<double[]>> lots = new HashMap<>(); // [quantity, price]
        double invested = 0.0, peak = 0.0;
        for (TradeEvent t : ordered) {
            Deque<double[]> queue = lots.computeIfAbsent(t.coinId(), k -> new ArrayDeque<>());
            if (t.side() == Side.BUY) {
                queue.addLast(new double[]{t.quantity(), t.price()});
                invested += t.notional();
                peak = Math.max(peak, invested);
                continue;
            }
            double toSell = t.quantity();
            while (toSell > 1e-12 && !queue.isEmpty()) {
                double[] lot = queue.peekFirst();
                double used = Math.min(lot[0], toSell);
                invested -= used * lot[1];
                lot[0] -= used;
                toSell -= used;
                if (lot[0] <= 1e-12) queue.pollFirst();
            }
        }
        return peak;
    }

    /**
     * Penalty weight of a trade: 0 below 1% of capital, rising linearly to 1 at 10%. Above 10% a trade in a
     * speculative asset escalates by one for every further 5%, up to 4 at 25%.
     */
    static double impact(double share, boolean speculative) {
        if (share < DUST_SHARE) return 0.0;
        if (share <= FULL_IMPACT_SHARE || !speculative) return lin(share, DUST_SHARE, FULL_IMPACT_SHARE);
        return Math.min(MAX_IMPACT, 1.0 + (share - FULL_IMPACT_SHARE) / ESCALATION_STEP);
    }

    static AssetRisk assetRisk(Inputs in, String coinId) {
        Double cap = in.marketCaps() == null ? null : in.marketCaps().get(coinId);
        boolean listed = in.dailyVolumes().containsKey(coinId); // CoinGecko returned market data for it
        Double volatility = null;
        SortedMap<LocalDate, Double> prices = in.dailyPrices().get(coinId);
        if (prices != null && prices.size() > MIN_VOLATILITY_DAYS) {
            List<Double> series = new ArrayList<>(prices.values());
            double[] logReturns = new double[series.size() - 1];
            boolean valid = true;
            for (int i = 1; i < series.size(); i++) {
                if (!(series.get(i - 1) > 0 && series.get(i) > 0)) { valid = false; break; }
                logReturns[i - 1] = Math.log(series.get(i) / series.get(i - 1));
            }
            if (valid) volatility = RiskMath.sampleStdDev(logReturns) * Math.sqrt(in.periodsPerYear());
        }
        boolean assessed = cap != null || listed || volatility != null;
        boolean speculative = (cap != null && cap < MICRO_CAP) || (cap == null && listed)
                || (volatility != null && volatility >= SPECULATIVE_VOLATILITY);
        return new AssetRisk(coinId, cap, volatility, assessed, speculative);
    }

    /** The largest current position as a share of the capital base, and whether its coin is speculative. */
    record PositionShare(Holding holding, double share, boolean speculative) {}

    static Optional<PositionShare> largestPosition(Inputs in, Sizing sizing) {
        if (!(sizing.capitalBase() > 0)) return Optional.empty();
        return in.holdings().stream().filter(h -> h.value() > 0)
                .max(Comparator.comparingDouble(Holding::value))
                .map(h -> new PositionShare(h, h.value() / sizing.capitalBase(), assetRisk(in, h.coinId()).speculative()));
    }

    /**
     * Position-sizing curve on a share of capital: up to 15% scores in full (25 of 25 points), falling linearly
     * to 0.6 (15 points) at 30% and 0.2 (5 points) at 50%; above 50% scores zero.
     */
    static double positionSizingScore(double share) {
        if (share <= 0.15) return 1.0;
        if (share <= 0.30) return 1.0 - 0.4 * (share - 0.15) / 0.15;
        if (share <= 0.50) return 0.6 - 0.4 * (share - 0.30) / 0.20;
        return 0.0;
    }

    /**
     * Risk pillar: position sizing (25 points), from the largest current position as a share of capital. A
     * speculative coin counts double. Oversized buys are also flagged, even after they've been sold.
     */
    static void positionSizingComponent(Inputs in, Sizing sizing, List<Flag> flags, List<Component> out) {
        Optional<PositionShare> largest = largestPosition(in, sizing);
        if (largest.isEmpty()) {
            out.add(unavailable("position-sizing", "Position sizing", 25, "No open positions."));
        } else {
            PositionShare p = largest.get();
            double effective = p.speculative() ? Math.min(1.0, 2 * p.share()) : p.share();
            out.add(component("position-sizing", "Position sizing", 25, p.share(),
                    "largest " + pct1(p.share()) + " of capital in " + p.holding().name() + (p.speculative() ? " (speculative, counts double)" : ""),
                    positionSizingScore(effective), "Largest position as a share of capital (" + capitalBaseText(sizing)
                            + "): up to 15% full marks, 30% → 15 points, 50% → 5, above 50% none"));
        }

        // Flags for oversized buys, judged by the worst one
        List<TradeSize> buys = sizing.trades().values().stream().filter(s -> s.trade().side() == Side.BUY).toList();
        if (buys.isEmpty() || !(sizing.capitalBase() > 0)) return;
        TradeSize worst = null;
        double worstScore = 2.0;
        for (TradeSize b : buys) {
            double score = sizingScore(b);
            if (score < worstScore || (score == worstScore && b.share() > worst.share())) {
                worst = b;
                worstScore = score;
            }
        }
        double share = worst.share();
        boolean speculative = worst.asset().speculative();
        TradeEvent t = worst.trade();
        if (speculative && share >= 0.05) {
            flags.add(new Flag("position-sizing", share >= FULL_IMPACT_SHARE ? Severity.HIGH : Severity.MEDIUM, "risk",
                    "High-Risk Concentration & Impulse",
                    "High concentration risk: a single trade allocated " + pct1(share) + " of your total portfolio ("
                            + money(t.notional()) + " of " + money(sizing.capitalBase()) + ") to " + t.name() + ", a "
                            + worst.asset().label() + (t.dateKnown() ? ", on " + t.date() : "") + ".",
                    "Size speculative positions at 1–2% of capital and never more than 5%: a micro-cap can fall 80–90% "
                            + "quickly, and at " + pct0(share) + " that would cost " + pct0(share * 0.85)
                            + " of the whole portfolio."));
        } else if (!speculative && share >= 0.25) {
            flags.add(new Flag("position-sizing", share >= 0.40 ? Severity.HIGH : Severity.MEDIUM, "risk",
                    "Over-Allocated Position",
                    "High concentration risk: a single trade allocated " + pct1(share) + " of your total portfolio ("
                            + money(t.notional()) + " of " + money(sizing.capitalBase()) + ") to " + t.name()
                            + (t.dateKnown() ? " on " + t.date() : "") + ".",
                    "Keep any single position to 10–20% of capital and build it in steps, so one coin's drawdown "
                            + "can't decide the whole portfolio."));
        }
    }

    static double sizingScore(TradeSize b) {
        return b.asset().speculative() ? inv(b.share(), 0.02, 0.15) : inv(b.share(), 0.10, 0.40);
    }

    private static String capitalBaseText(Sizing sizing) {
        return (sizing.fromBankroll() ? "the larger of your starting bankroll and current account value, "
                : "the larger of peak capital invested and current value, ") + money(sizing.capitalBase());
    }

    /**
     * Behavioral pillar: deployment pacing (40 points). The most capital put to work on a single day,
     * across all buys that day; full marks at 25% or less and none at 75%.
     */
    static void deploymentPacingComponent(Inputs in, Sizing sizing, List<Flag> flags, List<Component> out) {
        Map<LocalDate, DayDeployment> byDay = dailyDeployment(in, sizing, null);
        if (byDay.isEmpty() || !(sizing.capitalBase() > 0)) {
            out.add(unavailable("deployment-pacing", "Deployment pacing", 40, "Needs dated buys."));
            return;
        }
        DayDeployment top = null;
        for (DayDeployment d : byDay.values()) {
            if (top == null || d.peakShare() > top.peakShare()) top = d;
        }
        LocalDate busiest = top.date();
        double busiestShare = top.peakShare();
        List<TradeSize> day = top.buysMade();
        out.add(component("deployment-pacing", "Deployment pacing", 40, busiestShare,
                "most in one day: " + pct1(busiestShare) + " of capital (" + busiest + ")", inv(busiestShare, 0.25, 0.75),
                "Most capital deployed at once on a single day (buys less sells, in order); 25% or less scores full marks, 75% none"));
        if (busiestShare >= 0.5) {
            List<String> names = day.stream().map(s -> s.trade().name()).distinct().toList();
            flags.add(new Flag("impulsive-deployment", busiestShare >= IMPULSIVE_DAY_SHARE ? Severity.HIGH : Severity.MEDIUM,
                    "behavior", "Impulsive Capital Deployment",
                    "On " + busiest + " you put " + pct1(busiestShare) + " of your capital into " + day.size() + " buy"
                            + (day.size() == 1 ? "" : "s") + " (" + String.join(", ", names) + ") in a single day.",
                    "Scale into positions over weeks (for example in thirds) and keep a cash reserve, so one day's "
                            + "prices and mood don't set the whole portfolio."));
        }
    }

    /**
     * Behavioral pillar: repeated buying of speculative assets, as the sum of those buys' penalty weights
     * (40 points; full marks at 0, none at 3, e.g. three 10% buys or one 20% buy).
     */
    static void speculativeAccumulationComponent(Sizing sizing, List<Flag> flags, List<Component> out) {
        List<TradeSize> buys = sizing.trades().values().stream()
                .filter(s -> s.trade().side() == Side.BUY && s.asset().assessed()).toList();
        if (buys.isEmpty() || !(sizing.capitalBase() > 0)) {
            out.add(unavailable("speculative-accumulation", "Speculative accumulation", 40,
                    "Needs recorded buys of coins with market data or price history."));
            return;
        }
        List<TradeSize> speculative = buys.stream().filter(s -> s.asset().speculative()).toList();
        double load = speculative.stream().mapToDouble(TradeSize::impact).sum();
        double share = speculative.stream().mapToDouble(TradeSize::share).sum();
        out.add(component("speculative-accumulation", "Speculative accumulation", 40, load,
                speculative.isEmpty() ? "no speculative buys"
                        : speculative.size() + (speculative.size() == 1 ? " buy · " : " buys · ") + pct1(share) + " of capital · load " + fmt2(load),
                inv(load, 0.0, 3.0), "Sum of size weights of buys in micro-cap, unverified or high-volatility assets ("
                        + SIZING_RULE + "); 3 or more scores zero"));
        long meaningful = speculative.stream().filter(s -> s.impact() > 0).count();
        if (meaningful >= 2 && load >= 1.0) {
            List<String> names = speculative.stream().filter(s -> s.impact() > 0).map(s -> s.trade().name()).distinct().toList();
            flags.add(new Flag("speculative-accumulation", load >= 2.0 ? Severity.HIGH : Severity.MEDIUM, "behavior",
                    "Speculative Accumulation Flagged",
                    meaningful + " buys put " + pct1(share) + " of your capital into speculative assets ("
                            + String.join(", ", names) + "): position sizing that compounds risk with every purchase.",
                    "Set a total budget for speculative coins (for example 5–10% of capital) and stop adding once it's used."));
        }
    }

    // ---------------------------------------------------------------------
    // Pillar 3: Consistency & Return Quality
    // ---------------------------------------------------------------------

    static List<Component> consistencyComponents(Inputs in, LotBook book, List<Flag> flags) {
        List<Component> out = new ArrayList<>();

        // Return on initial bankroll S0 = all capital put in through buys
        double bankroll = bankroll(in);
        if (bankroll > 0) {
            double realized = book.closed().stream().mapToDouble(ClosedLot::pnl).sum();
            double unrealized = unrealized(in, book);
            double r = bankrollReturn(in, book).orElseThrow();
            out.add(component("bankroll-return", "Return on bankroll (S₀)", 90, r, signedPct1(r), lin(r, -0.30, 0.50),
                    "(realized " + money(realized) + " + unrealized " + money(unrealized) + ") / S₀ " + money(bankroll)));
            if (r < -0.10) {
                flags.add(new Flag("bankroll-loss", r < -0.30 ? Severity.HIGH : Severity.MEDIUM, "consistency", "Bankroll Drawdown",
                        "The portfolio is " + signedPct1(r) + " on the " + money(bankroll) + " put in.",
                        "Cut position sizes until performance recovers, and stop adding to positions that keep losing."));
            }
        } else {
            out.add(unavailable("bankroll-return", "Return on bankroll (S₀)", 90, "No buys recorded."));
        }

        // Profit factor = gross realized profit / gross realized loss
        long sells = in.trades().stream().filter(t -> t.side() == Side.SELL).count();
        if (sells >= MIN_EVALUABLE_TRADES && !book.closed().isEmpty()) {
            double profit = 0.0, loss = 0.0;
            for (ClosedLot lot : book.closed()) {
                if (lot.pnl() > 0) profit += lot.pnl();
                else loss -= lot.pnl();
            }
            if (loss > 0) {
                double pf = profit / loss;
                out.add(component("profit-factor", "Profit factor", 80, pf, fmt2(pf), lin(pf, 0.5, 2.0),
                        "Gross realized profit " + money(profit) + " / gross realized loss " + money(loss)));
                if (pf < 1.0) {
                    flags.add(new Flag("profit-factor", Severity.MEDIUM, "consistency", "Losing Trade Expectancy",
                            "Profit factor " + fmt2(pf) + ": closed trades lost " + money(loss) + " against " + money(profit) + " gained.",
                            "Let winners run longer and cut losers sooner; review whether losing trades shared an entry pattern."));
                }
            } else if (profit > 0) {
                // No losses: the ratio is unbounded, at or beyond the top of the band
                out.add(component("profit-factor", "Profit factor", 80, null, "∞ (no losing sells)", lin(Double.POSITIVE_INFINITY, 0.5, 2.0),
                        "Gross realized profit " + money(profit) + ", no realized losses"));
            } else {
                out.add(unavailable("profit-factor", "Profit factor", 80,
                        "Every closed trade broke even, so profit factor (profit ÷ loss) is undefined."));
            }
        } else {
            out.add(unavailable("profit-factor", "Profit factor", 80, "Needs at least " + MIN_EVALUABLE_TRADES + " sells."));
        }

        // Equity curve smoothness: R² of a straight-line fit to the cumulative log return
        double[] rp = in.portfolioReturns();
        Optional<double[]> fit = rp == null ? Optional.empty() : equityCurveFit(rp);
        if (fit.isPresent()) {
            double slope = fit.get()[0], r2 = fit.get()[1];
            boolean rising = slope > 0;
            out.add(component("smoothness", "Equity curve smoothness (R²)", 80, r2,
                    fmt2(r2) + (rising ? " · rising" : " · falling"), rising ? lin(r2, 0.2, 0.9) : 0.0,
                    "Linear fit of cumulative log return over " + rp.length + " days; a falling curve scores 0"));
            if (!rising || r2 < 0.3) {
                flags.add(new Flag("equity-curve", Severity.LOW, "consistency",
                        rising ? "Erratic Equity Curve" : "Falling Equity Curve",
                        rising ? "Growth has been uneven (R² " + fmt2(r2) + "): returns come in bursts rather than steadily."
                                : "The portfolio's value has trended down over the last " + rp.length + " days (R² " + fmt2(r2) + ").",
                        "Steadier results come from consistent position sizing and diversification rather than a few large bets."));
            }
        } else {
            out.add(unavailable("smoothness", "Equity curve smoothness (R²)", 80,
                    in.historyUnavailableReason() != null ? in.historyUnavailableReason() : "Price history for your holdings is unavailable."));
        }
        return out;
    }

    /** S₀: the configured starting bankroll, or (without one) the sum of all buys. */
    static double bankroll(Inputs in) {
        if (in.initialBankroll() != null && in.initialBankroll() > 0) return in.initialBankroll();
        double bankroll = 0.0;
        for (TradeEvent t : in.trades()) if (t.side() == Side.BUY) bankroll += t.notional();
        return bankroll;
    }

    static double unrealized(Inputs in, LotBook book) {
        double unrealized = 0.0;
        for (OpenLot lot : book.open()) {
            Double price = currentPrice(in, lot.coinId());
            if (price != null) unrealized += lot.quantity() * (price - lot.buyPrice());
        }
        return unrealized;
    }

    /** (realized + unrealized P&L) / S0, or empty when nothing was bought. */
    static OptionalDouble bankrollReturn(Inputs in, LotBook book) {
        double bankroll = bankroll(in);
        if (!(bankroll > 0)) return OptionalDouble.empty();
        double realized = book.closed().stream().mapToDouble(ClosedLot::pnl).sum();
        return OptionalDouble.of((realized + unrealized(in, book)) / bankroll);
    }

    /**
     * Gross realized profit / gross realized loss; +∞ with profits and no losses, 0 with neither.
     * Empty with fewer than {@link #MIN_EVALUABLE_TRADES} sells.
     */
    static OptionalDouble profitFactor(Inputs in, LotBook book) {
        long sells = in.trades().stream().filter(t -> t.side() == Side.SELL).count();
        if (sells < MIN_EVALUABLE_TRADES || book.closed().isEmpty()) return OptionalDouble.empty();
        double profit = 0.0, loss = 0.0;
        for (ClosedLot lot : book.closed()) {
            if (lot.pnl() > 0) profit += lot.pnl();
            else loss -= lot.pnl();
        }
        if (loss > 0) return OptionalDouble.of(profit / loss);
        return OptionalDouble.of(profit > 0 ? Double.POSITIVE_INFINITY : 0.0);
    }

    /** Slope and R² of an ordinary least-squares line through the cumulative log return path. */
    static Optional<double[]> equityCurveFit(double[] returns) {
        int n = returns.length + 1;
        if (n < 3) return Optional.empty();
        double[] y = new double[n];
        for (int t = 1; t < n; t++) y[t] = y[t - 1] + Math.log1p(returns[t - 1]);
        double meanX = (n - 1) / 2.0, meanY = RiskMath.mean(y);
        double sxy = 0, sxx = 0, syy = 0;
        for (int t = 0; t < n; t++) {
            double dx = t - meanX, dy = y[t] - meanY;
            sxy += dx * dy;
            sxx += dx * dx;
            syy += dy * dy;
        }
        if (syy == 0) return Optional.empty();
        return Optional.of(new double[]{sxy / sxx, sxy * sxy / (sxx * syy)});
    }

    // ---------------------------------------------------------------------
    // Pillar 4: Execution Discipline
    // ---------------------------------------------------------------------

    static List<Component> executionComponents(Inputs in, Sizing sizing, List<Flag> flags) {
        List<Component> out = new ArrayList<>();

        // Overtrading velocity: trades per 30 days since the first recorded trade. Activity is judged on the raw
        // count; overtrading on the size-weighted count, so dust trades don't add to it and large speculative
        // ones add more than one each
        List<TradeEvent> manual = in.trades().stream().filter(t -> !t.imported() && t.dateKnown()).toList();
        if (!manual.isEmpty()) {
            LocalDate first = manual.stream().map(TradeEvent::date).min(Comparator.naturalOrder()).orElseThrow();
            long days = Math.max(30, ChronoUnit.DAYS.between(first, in.today()) + 1);
            double per30 = manual.size() * 30.0 / days;
            double weightedPer30 = manual.stream().mapToDouble(t -> sizing.of(t).impact()).sum() * 30.0 / days;
            // Best between 2 and 8 trades a month; overtrading falls to 0 at 40, and near-inactivity
            // (under 2) earns at most 40%, since a handful of trades shows little discipline
            double velocityScore = per30 < 2 ? 0.4 + 0.6 * lin(per30, 0.5, 2.0) : inv(weightedPer30, 8, 40);
            out.add(component("velocity", "Trading velocity", 143, weightedPer30, fmt1(per30) + " trades / 30 days · "
                            + fmt1(weightedPer30) + " size-weighted", velocityScore,
                    manual.size() + " trades over " + days + " days; overtrading is judged on the size-weighted rate ("
                            + SIZING_RULE + ")"));
            if (weightedPer30 > 20) {
                long large = manual.stream().filter(t -> sizing.of(t).share() >= FULL_IMPACT_SHARE).count();
                long speculative = manual.stream().filter(t -> sizing.of(t).share() >= FULL_IMPACT_SHARE
                        && sizing.of(t).asset().speculative()).count();
                flags.add(new Flag("overtrading", weightedPer30 > 40 ? Severity.HIGH : Severity.MEDIUM, "execution", "Overtrading Flagged",
                        fmt1(per30) + " trades every 30 days (" + fmt1(weightedPer30) + " weighted by size): " + large + " of "
                                + manual.size() + " trades moved " + pct0(FULL_IMPACT_SHARE) + " or more of your capital"
                                + (speculative > 0 ? ", " + speculative + " of them in speculative assets" : "")
                                + ". Frequent large trades add costs and decisions made under pressure.",
                        "Trade only from a written plan, with a limit on trades per week, and keep each trade to a fixed small share of capital."));
            }
        } else {
            out.add(unavailable("velocity", "Trading velocity", 143, "Record buys and sells to measure trading frequency."));
        }

        // Liquidity impact: trade size as a share of the coin's 24h trading volume
        double scoreSum = 0.0, worst = 0.0;
        int measured = 0;
        String worstName = null;
        for (TradeEvent t : in.trades()) {
            Double volume = in.dailyVolumes().get(t.coinId());
            if (volume == null || volume <= 0) continue;
            double participation = t.notional() / volume;
            scoreSum += inv(participation, 0.001, 0.05);
            measured++;
            if (participation > worst) {
                worst = participation;
                worstName = t.name();
            }
        }
        if (measured > 0) {
            out.add(component("liquidity", "Liquidity impact", 107, worst, "largest trade " + pct2(worst) + " of 24h volume",
                    scoreSum / measured, measured + " trades compared with each coin's current 24h volume"));
            if (worst > 0.01) {
                flags.add(new Flag("liquidity", worst > 0.05 ? Severity.HIGH : Severity.MEDIUM, "execution", "Liquidity Impact Flagged",
                        "A " + worstName + " trade was " + pct2(worst) + " of the coin's daily trading volume: an order this size moves the price against you.",
                        "Split large orders over time (or use limit orders) so each piece stays well under 1% of daily volume."));
            }
        } else {
            out.add(unavailable("liquidity", "Liquidity impact", 107, "Trading volume for your coins is unavailable right now."));
        }

        return out;
    }

    private static Flag missingPillarFlag(Pillar p) {
        String reasons = String.join(" ", p.components().stream().filter(c -> !c.available())
                .map(Component::detail).distinct().toList());
        return new Flag("missing-" + p.key(), Severity.INFO, p.key(), p.name() + " Not Scored Yet",
                "This pillar is left out of the total, which is provisional until it can be scored. " + reasons,
                switch (p.key()) {
                    case "behavior" -> "Record your buys and sells with their dates (Buy / Sell on a holding) to unlock disposition, FOMO and panic analysis.";
                    case "execution" -> "Record trades and set a risk target to unlock execution scoring.";
                    default -> "Add holdings with enough price history to score this pillar.";
                });
    }

    // ---------------------------------------------------------------------
    // FIFO lot matching
    // ---------------------------------------------------------------------

    public record ClosedLot(String coinId, double quantity, double buyPrice, double sellPrice,
                            LocalDate buyDate, LocalDate sellDate, boolean datesKnown) {
        public double pnl() { return quantity * (sellPrice - buyPrice); }

        public long holdDays() { return Math.max(1, ChronoUnit.DAYS.between(buyDate, sellDate)); }
    }

    public record OpenLot(String coinId, double quantity, double buyPrice, LocalDate buyDate, boolean dateKnown) {}

    /** unmatchedSellQuantity: units sold beyond the recorded buys (e.g. after a manual holding edit). */
    public record LotBook(List<ClosedLot> closed, List<OpenLot> open, double unmatchedSellQuantity) {}

    /** Matches sells to the oldest open buys of the same coin. Imported holdings count as the oldest lots. */
    public static LotBook matchLots(List<TradeEvent> trades) {
        List<TradeEvent> ordered = new ArrayList<>(trades);
        ordered.sort(Comparator.comparing((TradeEvent t) -> t.dateKnown() ? t.date() : LocalDate.MIN)
                .thenComparingLong(TradeEvent::id));
        Map<String, Deque<double[]>> lots = new HashMap<>();       // [quantity, price]
        Map<String, Deque<TradeEvent>> lotTrades = new HashMap<>();
        List<ClosedLot> closed = new ArrayList<>();
        double unmatched = 0.0;
        for (TradeEvent t : ordered) {
            Deque<double[]> queue = lots.computeIfAbsent(t.coinId(), k -> new ArrayDeque<>());
            Deque<TradeEvent> sources = lotTrades.computeIfAbsent(t.coinId(), k -> new ArrayDeque<>());
            if (t.side() == Side.BUY) {
                queue.addLast(new double[]{t.quantity(), t.price()});
                sources.addLast(t);
                continue;
            }
            double toSell = t.quantity();
            while (toSell > 1e-12 && !queue.isEmpty()) {
                double[] lot = queue.peekFirst();
                TradeEvent buy = sources.peekFirst();
                double used = Math.min(lot[0], toSell);
                closed.add(new ClosedLot(t.coinId(), used, lot[1], t.price(), buy.date(), t.date(),
                        buy.dateKnown() && t.dateKnown()));
                lot[0] -= used;
                toSell -= used;
                if (lot[0] <= 1e-12) {
                    queue.pollFirst();
                    sources.pollFirst();
                }
            }
            unmatched += Math.max(0.0, toSell);
        }
        List<OpenLot> open = new ArrayList<>();
        for (Map.Entry<String, Deque<double[]>> e : lots.entrySet()) {
            Iterator<TradeEvent> sources = lotTrades.get(e.getKey()).iterator();
            for (double[] lot : e.getValue()) {
                TradeEvent buy = sources.next();
                open.add(new OpenLot(e.getKey(), lot[0], lot[1], buy.date(), buy.dateKnown()));
            }
        }
        return new LotBook(closed, open, unmatched);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Linear 0–1 band: 0 at or below worst, 1 at or above best. */
    static double lin(double x, double worst, double best) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, (x - worst) / (best - worst)));
    }

    /** Inverted band for "lower is better" metrics: 1 at or below best, 0 at or above worst. */
    static double inv(double x, double best, double worst) {
        return 1.0 - lin(x, best, worst);
    }

    private static Component component(String key, String name, double weight, Double value, String display,
                                       double score, String detail) {
        double s = Math.max(0.0, Math.min(1.0, score));
        return new Component(key, name, true, value, display, weight, s, weight * s, detail);
    }

    private static Component unavailable(String key, String name, double weight, String reason) {
        return new Component(key, name, false, null, "Not enough data", weight, 0.0, 0.0, reason);
    }

    private static Double priceOn(Inputs in, String coinId, LocalDate date) {
        SortedMap<LocalDate, Double> prices = in.dailyPrices().get(coinId);
        return prices == null ? null : prices.get(date);
    }

    private static Double currentPrice(Inputs in, String coinId) {
        Double price = in.currentPrices().get(coinId);
        if (price != null && price > 0) return price;
        SortedMap<LocalDate, Double> prices = in.dailyPrices().get(coinId);
        return prices == null || prices.isEmpty() ? null : prices.get(prices.lastKey());
    }

    /** Rounds half up to the given decimals; NaN and infinities pass through. */
    static double round(double v, int decimals) {
        if (!Double.isFinite(v)) return v;
        return java.math.BigDecimal.valueOf(v).setScale(decimals, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private static String fmt1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private static String fmt2(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String pct0(double v) { return String.format(Locale.ROOT, "%.0f%%", v * 100); }
    private static String pct1(double v) { return String.format(Locale.ROOT, "%.1f%%", v * 100); }
    private static String pct2(double v) { return String.format(Locale.ROOT, "%.2f%%", v * 100); }
    private static String signedPct1(double v) { return String.format(Locale.ROOT, "%+.1f%%", v * 100); }

    /** $61.2M, $1.4B: for market capitalisations. */
    private static String compactMoney(double v) {
        if (v >= 1e9) return String.format(Locale.ROOT, "$%.1fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.ROOT, "$%.1fM", v / 1e6);
        return money(v);
    }

    private static String money(double v) {
        return (v < 0 ? "-$" : "$") + String.format(Locale.US, "%,.0f", Math.abs(v));
    }
}
