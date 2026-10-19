import { InstitutionalScore, PortfolioDiversification, ScorePillar } from '../../types';

const TIERS = [
  [1, 'Sovereign Quantitative Director', 900, 1000], [2, 'Chief Risk Officer (CRO)', 800, 899],
  [3, 'Institutional Portfolio Manager', 750, 799], [4, 'Proprietary Desk Trader', 700, 749],
  [5, 'Disciplined Quantitative Analyst', 600, 699], [6, 'Systematic Swing Trader', 500, 599],
  [7, 'Tactical Retail Trader', 400, 499], [8, 'Speculative Momentum Chaser', 300, 399],
  [9, 'Unhedged Emotional Gambler', 150, 299], [10, 'Reckless Liquidation Hazard', 0, 149],
].map(([rank, name, minScore, maxScore]) => ({ rank: rank as number, name: name as string, minScore: minScore as number, maxScore: maxScore as number }));

const pillar = (key: ScorePillar['key'], name: string, points: number, available = true): ScorePillar => ({
  key, name, available, points: available ? points : 0, maxPoints: 250, coverage: available ? 1 : 0,
  components: [{
    key: `${key}-metric`, name: `${name} metric`, available, value: available ? 1 : null,
    displayValue: available ? 'shown value' : 'Not enough data', weight: 250, score: points / 250,
    points: available ? points : 0, detail: available ? 'how it is computed' : 'Needs dated sells.',
  }],
});

export function scoreFixture(overrides: Partial<InstitutionalScore> = {}): InstitutionalScore {
  return {
    score: 499, rawScore: 566, maxScore: 1000, provisional: true, pillarsScored: 3,
    caps: [
      { id: 'no-sells', maxScore: 499, title: 'No sells recorded', detail: 'Gains are only proven when trades are closed.' },
      { id: 'provisional', maxScore: 499, title: 'Provisional score', detail: 'Only 3 of 4 pillars could be scored.' },
    ],
    gates: [
      { minScore: 600, name: 'Tier 5 and above', unlocked: false, requirements: [
        { label: 'Return on capital (S₀) of at least 5%', met: true, actual: '+17.0%', name: 'Return on Capital', target: '≥ +5.0%', progress: 1 },
        { label: 'At least 10 recorded trades', met: false, actual: '4 trades', name: 'Trade Count', target: '≥ 10 trades', progress: 0.4 },
      ] },
      { minScore: 750, name: 'Tier 3 and above', unlocked: false, requirements: [] },
      { minScore: 900, name: 'Tier 1', unlocked: false, requirements: [] },
    ],
    tier: TIERS[6], tiers: TIERS,
    pillars: [
      pillar('risk', 'Risk Control & Tail Risk', 97),
      pillar('behavior', 'Behavioral Psychology', 0, false),
      pillar('consistency', 'Consistency & Return Quality', 78),
      pillar('execution', 'Execution Discipline', 250),
    ],
    flags: [
      { id: 'disposition', severity: 'HIGH', pillar: 'behavior', title: 'Disposition Effect Flagged',
        detail: 'Losers held 3.2x longer than winners (32.0 vs 10.0 days on average).', recommendation: 'Decide the exit before you enter.' },
      { id: 'missing-behavior', severity: 'INFO', pillar: 'behavior', title: 'Behavioral Psychology Not Scored Yet',
        detail: 'Left out of the total.', recommendation: 'Record your buys and sells with their dates.' },
    ],
    strengths: ['Liquidity impact: largest trade 0.00% of 24h volume'],
    coverage: { trades: 4, datedTrades: 0, closedLots: 0, observations: 364 },
    diversification: diversificationFixture(),
    dataFreshness: { pricesAsOf: '2026-10-18T19:59:00Z', stalePrices: [], historyThrough: '2026-10-18', historyStale: false },
    generatedAt: '2026-10-18T20:00:00Z',
    ...overrides,
  };
}

export function diversificationFixture(overrides: Partial<PortfolioDiversification> = {}): PortfolioDiversification {
  return {
    holdings: 3, hhi: 0.38, effectiveHoldings: 2.63, averageCorrelation: 0.82, effectiveBets: 1.21,
    diversificationRatio: 1.1, concentrationScore: 0.775, correlationScore: 0.073, betsScore: 0.07, score: 30.6,
    positions: [
      { id: 'bitcoin', name: 'Bitcoin', weight: 0.5, volatility: 0.45 },
      { id: 'ethereum', name: 'Ethereum', weight: 0.3, volatility: 0.6 },
      { id: 'solana', name: 'Solana', weight: 0.2, volatility: 0.8 },
    ],
    correlations: [[1, 0.86, 0.79], [0.86, 1, 0.84], [0.79, 0.84, 1]],
    pairs: [
      { a: 'Bitcoin', b: 'Ethereum', correlation: 0.86, combinedWeight: 0.8 },
      { a: 'Ethereum', b: 'Solana', correlation: 0.84, combinedWeight: 0.5 },
      { a: 'Bitcoin', b: 'Solana', correlation: 0.79, combinedWeight: 0.7 },
    ],
    observations: 364, correlationReason: null,
    ...overrides,
  };
}
