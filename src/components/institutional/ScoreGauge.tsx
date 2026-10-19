import React from 'react';
import { RankTier } from '../../types';
import { tierColor } from './styles';

interface ScoreGaugeProps {
  score: number;
  maxScore: number;
  tier: RankTier;
  provisional: boolean;
  pillarsScored: number;
}

/** Semicircular 0–1000 gauge with tier boundaries as ticks, and the rank tier badge underneath. */
const ScoreGauge: React.FC<ScoreGaugeProps> = ({ score, maxScore, tier, provisional, pillarsScored }) => {
  const color = tierColor(tier.rank);
  const filled = Math.max(0, Math.min(maxScore, score));
  // Tier boundaries along the arc (150, 300, ... 900)
  const ticks = [150, 300, 400, 500, 600, 700, 750, 800, 900].map(v => {
    const a = Math.PI * (1 - v / maxScore);
    return { v, x1: 120 + 88 * Math.cos(a), y1: 120 - 88 * Math.sin(a), x2: 120 + 112 * Math.cos(a), y2: 120 - 112 * Math.sin(a) };
  });
  return (
    <div className="flex flex-col items-center">
      <svg viewBox="0 0 240 140" className="w-full max-w-[280px]" role="meter" aria-label="Trader Index Score"
           aria-valuemin={0} aria-valuemax={maxScore} aria-valuenow={score}>
        <path d="M 20 120 A 100 100 0 0 1 220 120" fill="none" stroke="#222938" strokeWidth="12" />
        <path d="M 20 120 A 100 100 0 0 1 220 120" fill="none" stroke={color.hex} strokeWidth="12"
              pathLength={maxScore} strokeDasharray={`${filled} ${maxScore}`} />
        {ticks.map(t => <line key={t.v} x1={t.x1} y1={t.y1} x2={t.x2} y2={t.y2} stroke="#0B0E14" strokeWidth="1.5" />)}
        <text x="120" y="102" textAnchor="middle" fill="#E4E8EF" fontSize="42" fontWeight="500" fontFamily="JetBrains Mono, monospace">{score}</text>
        <text x="120" y="124" textAnchor="middle" fill="#8C95A8" fontSize="11" fontFamily="JetBrains Mono, monospace">/ {maxScore} TIS</text>
        <text x="20" y="138" textAnchor="middle" fill="#5D667A" fontSize="9" fontFamily="JetBrains Mono, monospace">0</text>
        <text x="220" y="138" textAnchor="middle" fill="#5D667A" fontSize="9" fontFamily="JetBrains Mono, monospace">{maxScore}</text>
      </svg>

      <div className={`mt-3 w-full max-w-[280px] rounded-md border px-4 py-2.5 text-center ${color.badge}`} data-testid="tier-badge">
        <div className={`text-[10px] font-semibold uppercase tracking-[0.2em] ${color.text}`}>Tier {tier.rank}</div>
        <div className="text-sm font-semibold text-term-text">{tier.name}</div>
        <div className="num text-[11px] text-term-muted">{tier.minScore}–{tier.maxScore}</div>
      </div>

      {provisional && (
        <div className="mt-2 rounded border border-term-warn/40 bg-term-warn-soft px-2 py-0.5 text-[11px] text-term-warn">
          Provisional · based on {pillarsScored} of 4 pillars
        </div>
      )}
    </div>
  );
};

export default ScoreGauge;
