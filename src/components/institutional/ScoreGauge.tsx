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

/** Semicircular 0–1000 gauge with the rank tier badge underneath. */
const ScoreGauge: React.FC<ScoreGaugeProps> = ({ score, maxScore, tier, provisional, pillarsScored }) => {
  const color = tierColor(tier.rank);
  const filled = Math.max(0, Math.min(maxScore, score));
  return (
    <div className="flex flex-col items-center">
      <svg viewBox="0 0 240 140" className="w-full max-w-xs" role="meter" aria-label="Trader Index Score"
           aria-valuemin={0} aria-valuemax={maxScore} aria-valuenow={score}>
        <path d="M 20 120 A 100 100 0 0 1 220 120" fill="none" stroke="#1f2937" strokeWidth="16" strokeLinecap="round" />
        <path d="M 20 120 A 100 100 0 0 1 220 120" fill="none" stroke={color.hex} strokeWidth="16" strokeLinecap="round"
              pathLength={maxScore} strokeDasharray={`${filled} ${maxScore}`} />
        <text x="120" y="100" textAnchor="middle" className="fill-white" fontSize="44" fontWeight="700">{score}</text>
        <text x="120" y="124" textAnchor="middle" fill="#9ca3af" fontSize="13">of {maxScore}</text>
        <text x="20" y="138" textAnchor="middle" fill="#6b7280" fontSize="10">0</text>
        <text x="220" y="138" textAnchor="middle" fill="#6b7280" fontSize="10">{maxScore}</text>
      </svg>

      <div className={`mt-3 px-5 py-3 rounded-2xl border bg-gradient-to-r ${color.badge} text-center`} data-testid="tier-badge">
        <div className={`text-xs uppercase tracking-widest ${color.text}`}>Tier {tier.rank}</div>
        <div className="text-lg font-bold text-white">{tier.name}</div>
        <div className="text-xs text-gray-400">{tier.minScore}–{tier.maxScore} points</div>
      </div>

      {provisional && (
        <div className="mt-3 text-xs px-3 py-1 rounded-full border border-yellow-500/40 bg-yellow-500/10 text-yellow-200">
          Provisional · based on {pillarsScored} of 4 pillars
        </div>
      )}
    </div>
  );
};

export default ScoreGauge;
