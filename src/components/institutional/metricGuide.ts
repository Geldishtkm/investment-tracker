/**
 * Plain-English help for every metric in the four Trader Index pillars, keyed by the component key the
 * scoring engine sends. Each entry says what the metric means and how to improve it.
 */
export interface MetricGuide {
  what: string;
  improve: string;
}

export const METRIC_GUIDE: Record<string, MetricGuide> = {
  // Risk Control & Tail Risk
  sharpe: {
    what: 'Measures how much return you earn for every unit of risk you take. Above 1 is good; below 0 means the risk wasn’t paid for.',
    improve: 'Trim positions that swing a lot without adding return, and keep part of the portfolio in steadier assets.',
  },
  sortino: {
    what: 'Like Sharpe, but only counts the bad swings: how much return you earn per unit of downside risk.',
    improve: 'Cut back the positions behind your worst down days, and set exit levels so losses stay small.',
  },
  volatility: {
    what: 'How much your portfolio’s value typically moves up or down in a year. Up to 25% earns full points, then the score eases down smoothly; one-day price glitches are ignored.',
    improve: 'Hold fewer high-volatility coins, size them smaller, or keep a cash or stablecoin reserve.',
  },
  'benchmark-beta': {
    what: 'How strongly your portfolio moved with the overall crypto market (Bitcoin and Ethereum) over the last 7 days. 1 means it moved in step with the market; 2 means twice as hard.',
    improve: 'Keep beta between −1 and 1: blend in steadier coins or a stablecoin reserve, and trim the most aggressive positions when your portfolio swings harder than the market.',
  },
  concentration: {
    what: 'How much of your money sits in your biggest positions (HHI). Spread evenly across 5 coins is ideal; one coin is the worst case.',
    improve: 'Cap any single coin at about 25–40% of the portfolio and spread the rest across several positions.',
  },
  correlation: {
    what: 'How closely your coins move together. Near 1 means they rise and fall in lockstep, so they don’t protect each other.',
    improve: 'Add assets that behave differently from the rest (for example a stablecoin reserve) rather than more of the same kind of coin.',
  },
  'independent-bets': {
    what: 'How many truly separate positions your portfolio’s risk adds up to, once you account for coins moving together.',
    improve: 'Combine coins that don’t move together; five coins that all follow Bitcoin count as little more than one bet.',
  },
  'position-sizing': {
    what: 'How big your largest position is compared with your capital. Up to 15% in one coin earns full points; 30% earns 15 of 25, 50% earns 5, and more earns none. Speculative coins count double.',
    improve: 'Keep any single coin at or below 15% of your capital; trim oversized positions and add new ones in steps.',
  },


  // Behavioral Psychology
  disposition: {
    what: 'Whether you hold losing trades longer than winning ones, a common habit of hoping losers come back.',
    improve: 'Decide an exit before you buy (a stop-loss or a review date) and stick to it, win or lose.',
  },
  fomo: {
    what: 'How often you buy right after a coin has already jumped 15% or more in a week (fear of missing out), weighted by how much you put in.',
    improve: 'Wait for a pullback or buy in smaller steps over several days instead of chasing a spike.',
  },
  panic: {
    what: 'How often you sell during a sharp one-day drop, right at the low point.',
    improve: 'Set your exit levels in advance and avoid selling in the middle of a crash; reduce in steps if you must.',
  },
  'speculative-accumulation': {
    what: 'How much capital you keep adding to small, unverified or very volatile coins over time.',
    improve: 'Set a fixed budget for speculative coins (for example 5–10% of capital) and stop buying once it’s used.',
  },
  'deployment-pacing': {
    what: 'The most capital you put to work on a single day. Going all in at once is impulsive.',
    improve: 'Avoid putting more than about 25% of your cash to work in one day; scale in over days or weeks.',
  },

  // Consistency & Return Quality
  'bankroll-return': {
    what: 'Your total gain or loss compared with your starting bankroll (S₀), counting both open and closed positions.',
    improve: 'Grow the account steadily: let winners run, cut losers early, and avoid large, risky bets.',
  },
  'profit-factor': {
    what: 'Money made on winning trades divided by money lost on losing ones. Above 1 means your wins outweigh your losses.',
    improve: 'Close losing trades sooner and give winning trades more room before taking profit.',
  },
  smoothness: {
    what: 'How steadily your portfolio has grown, rather than lurching up and down.',
    improve: 'Keep position sizes consistent and diversified, so no single trade causes a big jump or drop.',
  },

  // Execution Discipline
  velocity: {
    what: 'How often you trade, adjusted for trade size. Somewhere between 2 and 8 meaningful trades a month scores best.',
    improve: 'Trade from a plan with a weekly limit; avoid lots of large trades in quick succession, and avoid going inactive.',
  },
  liquidity: {
    what: 'How big your trades are compared with how much of each coin trades in a day. Large orders in thin markets move the price against you.',
    improve: 'Split big orders into smaller pieces and favour coins with plenty of daily trading volume.',
  },
};
