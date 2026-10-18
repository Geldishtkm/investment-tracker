import { InstitutionalScore, ScorePillar } from '../../types';

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
    score: 566, maxScore: 1000, provisional: true, pillarsScored: 3,
    tier: TIERS[5], tiers: TIERS,
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
    riskTargetVolatility: null,
    generatedAt: '2026-10-18T20:00:00Z',
    ...overrides,
  };
}
