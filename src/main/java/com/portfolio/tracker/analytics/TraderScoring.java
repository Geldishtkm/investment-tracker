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
 *   <li>hard caps: stagnant portfolios stay at or below Tier 8 (≤ 399); provisional scores, no
 *       recorded sells, a thin trade history, or no positive return on capital stay at or below
 *       Tier 7 (≤ 499)</li>
 *   <li>tier gates: Tier 5 and above, Tier 3 and above, and Tier 1 each require minimum return on
 *       capital, profit factor, Sharpe ratio and trade history; failing a gate caps the score just
 *       below it</li>
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

    // Behavioral detection thresholds
    static final double FOMO_RUN_UP = 0.15;          // bought after a 7-day rally of at least +15%
    static final int FOMO_LOOKBACK_DAYS = 7;
    static final double PANIC_DROP = -0.08;          // sold on a day the coin fell 8% or more in 24h...
    static final int PANIC_WINDOW_DAYS = 3;          // ...at the lowest price within ±3 days
    static final double PANIC_TROUGH_TOLERANCE = 0.01;
    static final int MIN_EVALUABLE_TRADES = 2;
    static final int BETA_WINDOW = 30;
    static final int BETA_STEP = 5;
    static final int MIN_BETA_WINDOWS = 3;

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
     * @param riskTargetVolatility     the user's target annual volatility, or null if not set
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
                         LocalDate today) {}

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

    /** One requirement of a tier gate, with the user's actual value. */
    public record Requirement(String label, boolean met, String actual) {}

    /** Requirements that must all be met before scores of minScore and above are allowed. */
    public record TierGate(int minScore, String name, boolean unlocked, List<Requirement> requirements) {}

    /**
     * score is the final, capped score; rawScore the weighted total before evidence caps.
     * caps lists every cap that applies, lowest first. tier is null only when no pillar could be scored.
     */
    public record Evaluation(int score, int rawScore, boolean provisional, int pillarsScored, Tier tier,
                             List<Pillar> pillars, List<ScoreCap> caps, List<TierGate> gates,
                             List<Flag> flags, List<String> strengths, Coverage coverage) {}

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

        List<Pillar> pillars = List.of(
                pillar("risk", "Risk Control & Tail Risk", riskComponents(in, flags)),
                pillar("behavior", "Behavioral Psychology", behaviorComponents(in, book, flags)),
                pillar("consistency", "Consistency & Return Quality", consistencyComponents(in, book, flags)),
                pillar("execution", "Execution Discipline", executionComponents(in, flags)));

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

        Evidence evidence = evidence(in, book);
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
                new Coverage(in.trades().size(), dated, book.closed().size(), observations));
    }

    // ---------------------------------------------------------------------
    // Evidence caps and tier gates
    // ---------------------------------------------------------------------

    /**
     * The facts the caps and gates are judged on. Values are NaN when unknown; an unknown value never
     * meets a requirement.
     */
    record Evidence(double volatility, double sharpe, double bankrollReturn, double profitFactor,
                    int sells, int datedTrades, long activeDays, int holdings) {}

    static Evidence evidence(Inputs in, LotBook book) {
        double[] rp = in.portfolioReturns();
        double vol = Double.NaN, sharpe = Double.NaN;
        if (rp != null && rp.length > 1) {
            vol = RiskMath.sampleStdDev(rp) * Math.sqrt(in.periodsPerYear());
            if (vol > 0) sharpe = (RiskMath.mean(rp) * in.periodsPerYear() - in.riskFreeRate()) / vol;
        }
        List<TradeEvent> manual = in.trades().stream().filter(t -> !t.imported() && t.dateKnown()).toList();
        long activeDays = manual.isEmpty() ? 0 : ChronoUnit.DAYS.between(
                manual.stream().map(TradeEvent::date).min(Comparator.naturalOrder()).orElseThrow(), in.today()) + 1;
        int sells = (int) manual.stream().filter(t -> t.side() == Side.SELL).count();
        return new Evidence(vol, sharpe, bankrollReturn(in, book).orElse(Double.NaN),
                profitFactor(in, book).orElse(Double.NaN), sells, manual.size(), activeDays, in.holdings().size());
    }

    /** Gate thresholds: {min score, return on S0, profit factor, Sharpe, dated trades, active days}. */
    private static final double[][] GATES = {
            {600, 0.05, 1.2, 0.5, 10, 90},
            {750, 0.15, 1.5, 1.0, 20, 180},
            {900, 0.30, 2.0, 1.5, 40, 365},
    };

    static List<TierGate> tierGates(Evidence e) {
        // Judge each value at the precision it is shown with, so "1.50" never fails "at least 1.5"
        double ret = round(e.bankrollReturn(), 3), pf = round(e.profitFactor(), 2), sharpe = round(e.sharpe(), 2);
        List<TierGate> gates = new ArrayList<>();
        for (double[] g : GATES) {
            int min = (int) g[0];
            List<Requirement> reqs = List.of(
                    new Requirement("Return on capital (S₀) of at least " + pct0(g[1]), ret >= g[1],
                            Double.isNaN(e.bankrollReturn()) ? "no data" : signedPct1(e.bankrollReturn())),
                    new Requirement("Profit factor of at least " + fmt1(g[2]) + " on closed trades", pf >= g[2],
                            Double.isNaN(e.profitFactor()) ? "fewer than " + MIN_SELLS + " sells"
                                    : Double.isInfinite(e.profitFactor()) ? "∞ (no losses)" : fmt2(e.profitFactor())),
                    new Requirement("Sharpe ratio of at least " + fmt1(g[3]), sharpe >= g[3],
                            Double.isNaN(e.sharpe()) ? "no data" : fmt2(e.sharpe())),
                    new Requirement("At least " + (int) g[4] + " dated trades over " + (int) g[5] + "+ days",
                            e.datedTrades() >= g[4] && e.activeDays() >= g[5],
                            e.datedTrades() + " trades over " + e.activeDays() + " days"));
            gates.add(new TierGate(min, tierFor(min).rank() == 1 ? "Tier 1" : "Tier " + tierFor(min).rank() + " and above",
                    reqs.stream().allMatch(Requirement::met), reqs));
        }
        return gates;
    }

    static List<ScoreCap> scoreCaps(Evidence e, int pillarsScored, int pillarCount, List<TierGate> gates) {
        List<ScoreCap> caps = new ArrayList<>();
        boolean flat = (!Double.isNaN(e.volatility()) && e.volatility() < STAGNANT_VOLATILITY)
                || (!Double.isNaN(e.bankrollReturn()) && Math.abs(e.bankrollReturn()) < STAGNANT_RETURN && e.sells() == 0);
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
                    .map(r -> r.label() + " (now " + r.actual() + ")").toList());
            caps.add(new ScoreCap("gate-" + gate.minScore(), gate.minScore() - 1, gate.name() + " locked",
                    "Requires: " + missing + "."));
            break; // higher gates are implied by the first locked one
        }
        caps.sort(Comparator.comparingInt(ScoreCap::maxScore));
        return caps;
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

    static List<Component> riskComponents(Inputs in, List<Flag> flags) {
        List<Component> out = new ArrayList<>();
        double[] rp = in.portfolioReturns();
        int periods = in.periodsPerYear();
        String noHistory = in.historyUnavailableReason() != null ? in.historyUnavailableReason()
                : "Price history for your holdings is unavailable.";
        double volatility = rp == null ? Double.NaN : RiskMath.sampleStdDev(rp) * Math.sqrt(periods);
        boolean hasHistory = rp != null && volatility > 0;
        double annualReturn = rp == null ? Double.NaN : RiskMath.mean(rp) * periods;

        // Sharpe ratio
        if (hasHistory) {
            double sharpe = (annualReturn - in.riskFreeRate()) / volatility;
            out.add(component("sharpe", "Sharpe ratio", 70, sharpe, fmt2(sharpe), lin(sharpe, -0.5, 2.0),
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
            out.add(unavailable("sharpe", "Sharpe ratio", 70, noHistory));
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
                out.add(component("sortino", "Sortino ratio", 60, sortino, fmt2(sortino), lin(sortino, -0.5, 3.0),
                        "(annualized return − risk-free) / annualized downside deviation " + pct1(downside)));
            } else {
                out.add(component("sortino", "Sortino ratio", 60, null, "∞ (no down days)", excess > 0 ? 1.0 : 0.0,
                        "No daily return fell below the risk-free rate"));
            }
        } else {
            out.add(unavailable("sortino", "Sortino ratio", 60, noHistory));
        }

        // Volatility level
        if (hasHistory) {
            out.add(component("volatility", "Annualized volatility", 40, volatility, pct1(volatility),
                    inv(volatility, 0.20, 1.20), "Sample standard deviation of daily returns × √" + periods));
            if (volatility > 0.5) {
                flags.add(new Flag("high-volatility", volatility > 0.8 ? Severity.HIGH : Severity.MEDIUM, "risk",
                        "High Volatility", "Annualized volatility is " + pct1(volatility)
                        + ": a typical year could see the portfolio swing by this much in either direction.",
                        "Reduce position sizes or blend in less volatile assets until volatility matches what you can hold through."));
            }
        } else {
            out.add(unavailable("volatility", "Annualized volatility", 40, noHistory));
        }

        // Beta stability: how much the market exposure drifts between rolling windows
        double[] bench = in.benchmarkReturns();
        List<Double> betas = rp == null || bench == null ? List.of() : rollingBetas(rp, bench);
        if (betas.size() >= MIN_BETA_WINDOWS) {
            double[] b = betas.stream().mapToDouble(Double::doubleValue).toArray();
            double spread = RiskMath.sampleStdDev(b);
            out.add(component("beta-stability", "Beta stability", 40, spread, "β " + fmt2(RiskMath.mean(b)) + " ± " + fmt2(spread),
                    inv(spread, 0.10, 0.60), "Std. deviation of " + b.length + " rolling " + BETA_WINDOW + "-day betas"));
            if (spread > 0.4) {
                flags.add(new Flag("unstable-beta", Severity.LOW, "risk", "Unstable Market Exposure",
                        "Rolling beta varied by ±" + fmt2(spread) + " around " + fmt2(RiskMath.mean(b))
                                + ": the portfolio's sensitivity to the market keeps changing.",
                        "Keep a consistent mix instead of rotating between high- and low-beta coins."));
            }
        } else {
            out.add(unavailable("beta-stability", "Beta stability", 40, rp == null ? noHistory
                    : "Needs at least " + (BETA_WINDOW + BETA_STEP * (MIN_BETA_WINDOWS - 1)) + " days of shared history."));
        }

        // Concentration: Herfindahl–Hirschman index of current weights
        double total = in.holdings().stream().mapToDouble(Holding::value).sum();
        if (total > 0) {
            double hhi = 0.0;
            Holding top = null;
            for (Holding h : in.holdings()) {
                double w = h.value() / total;
                hhi += w * w;
                if (top == null || h.value() > top.value()) top = h;
            }
            double topWeight = top.value() / total;
            out.add(component("concentration", "Concentration (HHI)", 40, hhi, fmt2(hhi) + " · " + fmt1(1.0 / hhi) + " effective holdings",
                    inv(hhi, 0.20, 1.0), "Sum of squared portfolio weights; 1.00 means a single holding"));
            if (hhi >= 0.5) {
                flags.add(new Flag("concentration", topWeight >= 0.8 ? Severity.HIGH : Severity.MEDIUM, "risk",
                        "Concentration Risk Flagged", pct1(topWeight) + " of the portfolio is in " + top.name()
                        + " (HHI " + fmt2(hhi) + ").",
                        "Cap single positions at a fixed share of the portfolio (for example 25–40%) so one coin can't decide the outcome."));
            }
        } else {
            out.add(unavailable("concentration", "Concentration (HHI)", 40, "No current holdings."));
        }
        return out;
    }

    static List<Double> rollingBetas(double[] rp, double[] bench) {
        List<Double> betas = new ArrayList<>();
        for (int start = 0; start + BETA_WINDOW <= rp.length; start += BETA_STEP) {
            double[] x = Arrays.copyOfRange(rp, start, start + BETA_WINDOW);
            double[] m = Arrays.copyOfRange(bench, start, start + BETA_WINDOW);
            double var = RiskMath.sampleVariance(m);
            if (var > 0) betas.add(RiskMath.covariance(x, m) / var);
        }
        return betas;
    }

    // ---------------------------------------------------------------------
    // Pillar 2: Behavioral Psychology
    // ---------------------------------------------------------------------

    static List<Component> behaviorComponents(Inputs in, LotBook book, List<Flag> flags) {
        List<Component> out = new ArrayList<>();

        // Disposition effect: losers held longer than winners (Shefrin & Statman, 1985)
        Optional<Disposition> disposition = disposition(book.closed());
        if (disposition.isPresent()) {
            Disposition d = disposition.get();
            out.add(component("disposition", "Disposition effect", 100, d.ratio(), fmt1(d.ratio()) + "× loser/winner hold time",
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
            out.add(unavailable("disposition", "Disposition effect", 100,
                    "Needs at least one winning and one losing sell with known buy and sell dates."));
        }

        // FOMO chasing: buys made right after a sharp rally
        int evaluableBuys = 0, fomoBuys = 0;
        double fomoRunUpSum = 0.0;
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
            }
        }
        if (evaluableBuys >= MIN_EVALUABLE_TRADES) {
            double rate = (double) fomoBuys / evaluableBuys;
            out.add(component("fomo", "FOMO chasing", 75, rate, fomoBuys + " of " + evaluableBuys + " buys (" + pct0(rate) + ")",
                    inv(rate, 0.0, 0.5), "Buys made after the coin rose " + pct0(FOMO_RUN_UP) + "+ in the previous "
                            + FOMO_LOOKBACK_DAYS + " days"));
            if (fomoBuys > 0) {
                flags.add(new Flag("fomo", rate >= 0.25 ? Severity.MEDIUM : Severity.LOW, "behavior", "FOMO Chasing Flagged",
                        fomoBuys + " of " + evaluableBuys + " buys came after a " + FOMO_LOOKBACK_DAYS + "-day rally of "
                                + pct0(FOMO_RUN_UP) + " or more (average +" + pct1(fomoRunUpSum / fomoBuys) + ").",
                        "Wait for a pullback or scale in over several days instead of buying into a spike; "
                                + "write down the entry reason before placing the order."));
            }
        } else {
            out.add(unavailable("fomo", "FOMO chasing", 75, "Needs at least " + MIN_EVALUABLE_TRADES
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
            out.add(component("panic", "Panic capitulation", 75, rate, panicSells + " of " + evaluableSells + " sells (" + pct0(rate) + ")",
                    inv(rate, 0.0, 0.5), "Sells on a day the coin fell " + pct0(-PANIC_DROP) + "+ in 24h, at its lowest price within ±"
                            + PANIC_WINDOW_DAYS + " days"));
            if (panicSells > 0) {
                flags.add(new Flag("panic", rate >= 0.25 ? Severity.HIGH : Severity.MEDIUM, "behavior", "Panic Capitulation Flagged",
                        panicSells + " of " + evaluableSells + " sells happened during a 24h drop of " + pct0(-PANIC_DROP)
                                + " or more, at the local low.",
                        "Set exit levels in advance and avoid selling during a crash; if you must act, reduce in steps rather than all at once."));
            }
        } else {
            out.add(unavailable("panic", "Panic capitulation", 75, "Needs at least " + MIN_EVALUABLE_TRADES
                    + " dated sells within the last year of price history."));
        }
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
            } else {
                out.add(component("profit-factor", "Profit factor", 80, null, "∞ (no losing sells)", profit > 0 ? 1.0 : 0.5,
                        "Gross realized profit " + money(profit) + ", no realized losses"));
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

    static double bankroll(Inputs in) {
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

    static List<Component> executionComponents(Inputs in, List<Flag> flags) {
        List<Component> out = new ArrayList<>();

        // Overtrading velocity: trades per 30 days since the first recorded trade
        List<TradeEvent> manual = in.trades().stream().filter(t -> !t.imported() && t.dateKnown()).toList();
        if (!manual.isEmpty()) {
            LocalDate first = manual.stream().map(TradeEvent::date).min(Comparator.naturalOrder()).orElseThrow();
            long days = Math.max(30, ChronoUnit.DAYS.between(first, in.today()) + 1);
            double per30 = manual.size() * 30.0 / days;
            // Best between 2 and 8 trades a month; overtrading falls to 0 at 40, and near-inactivity
            // (under 2) earns at most 40%, since a handful of trades shows little discipline
            double velocityScore = per30 < 2 ? 0.4 + 0.6 * lin(per30, 0.5, 2.0) : inv(per30, 8, 40);
            out.add(component("velocity", "Trading velocity", 100, per30, fmt1(per30) + " trades / 30 days", velocityScore,
                    manual.size() + " trades over " + days + " days"));
            if (per30 > 20) {
                flags.add(new Flag("overtrading", per30 > 40 ? Severity.HIGH : Severity.MEDIUM, "execution", "Overtrading Flagged",
                        fmt1(per30) + " trades every 30 days: frequent trading adds costs and decisions made under pressure.",
                        "Trade only from a written plan, with a limit on trades per week."));
            }
        } else {
            out.add(unavailable("velocity", "Trading velocity", 100, "Record buys and sells to measure trading frequency."));
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
            out.add(component("liquidity", "Liquidity impact", 75, worst, "largest trade " + pct2(worst) + " of 24h volume",
                    scoreSum / measured, measured + " trades compared with each coin's current 24h volume"));
            if (worst > 0.01) {
                flags.add(new Flag("liquidity", worst > 0.05 ? Severity.HIGH : Severity.MEDIUM, "execution", "Liquidity Impact Flagged",
                        "A " + worstName + " trade was " + pct2(worst) + " of the coin's daily trading volume: an order this size moves the price against you.",
                        "Split large orders over time (or use limit orders) so each piece stays well under 1% of daily volume."));
            }
        } else {
            out.add(unavailable("liquidity", "Liquidity impact", 75, "Trading volume for your coins is unavailable right now."));
        }

        // Risk target adherence: actual volatility versus the user's target
        Double target = in.riskTargetVolatility();
        double[] rp = in.portfolioReturns();
        if (target != null && target > 0 && rp != null) {
            double vol = RiskMath.sampleStdDev(rp) * Math.sqrt(in.periodsPerYear());
            double deviation = Math.abs(vol - target) / target;
            out.add(component("risk-target", "Risk target adherence", 75, deviation,
                    pct1(vol) + " actual vs " + pct1(target) + " target", inv(deviation, 0.10, 1.0),
                    "Relative gap between annualized volatility and your target"));
            if (deviation > 0.25) {
                flags.add(new Flag("risk-target", deviation > 0.5 ? Severity.MEDIUM : Severity.LOW, "execution", "Off Risk Target",
                        "Volatility is " + pct1(vol) + " against your " + pct1(target) + " target (" + pct0(deviation) + " off).",
                        vol > target ? "Reduce the most volatile positions to bring risk back to target."
                                : "You're taking less risk than planned; adjust the target or the allocation deliberately."));
            }
        } else if (target == null || target <= 0) {
            out.add(unavailable("risk-target", "Risk target adherence", 75, "No risk target set."));
            flags.add(new Flag("no-risk-target", Severity.INFO, "execution", "Set a Risk Target",
                    "Institutional desks run to a volatility budget; you haven't set one yet.",
                    "Set a target annual volatility (for example 40%) to have adherence scored."));
        } else {
            out.add(unavailable("risk-target", "Risk target adherence", 75,
                    in.historyUnavailableReason() != null ? in.historyUnavailableReason() : "Price history for your holdings is unavailable."));
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

    private static String money(double v) {
        return (v < 0 ? "-$" : "$") + String.format(Locale.US, "%,.0f", Math.abs(v));
    }
}
