import React from 'react';
import { Layers } from 'lucide-react';
import { PortfolioDiversification, ScoreComponent } from '../../types';

const DIVERSIFICATION_KEYS = ['concentration', 'correlation', 'independent-bets'];
/** Matrices beyond this size are listed as pairs only; the grid gets unreadable. */
const MAX_MATRIX = 10;

/** Background for a correlation cell: red when moving together, slate near zero, teal when offsetting. */
export function correlationColor(rho: number): string {
  const a = Math.min(1, Math.abs(rho));
  return rho >= 0 ? `rgba(239, 68, 68, ${0.12 + 0.68 * a})` : `rgba(20, 184, 166, ${0.12 + 0.68 * a})`;
}

const fmt = (v: number, digits = 2) => v.toFixed(digits);
const pct = (v: number) => `${(v * 100).toFixed(1)}%`;

interface Props {
  diversification: PortfolioDiversification;
  /** The Risk pillar's components, for the points diversification earned. */
  riskComponents: ScoreComponent[];
}

/** Risk & Diversification: HHI, effective holdings, correlation-adjusted independent bets and the correlation matrix. */
const DiversificationCard: React.FC<Props> = ({ diversification: d, riskComponents }) => {
  const parts = riskComponents.filter(c => DIVERSIFICATION_KEYS.includes(c.key));
  const earned = parts.reduce((sum, c) => sum + c.points, 0);
  const possible = parts.reduce((sum, c) => sum + c.weight, 0);
  const lockstep = d.pairs.filter(p => p.correlation >= 0.8);
  const leastCorrelated = d.pairs.length > 1 ? d.pairs[d.pairs.length - 1] : null;

  return (
    <section className="bg-gradient-to-br from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6"
             aria-labelledby="diversification-heading" data-testid="diversification-card">
      <div className="flex flex-wrap items-baseline justify-between gap-2 mb-1">
        <h3 id="diversification-heading" className="text-lg font-semibold text-white flex items-center gap-2">
          <Layers size={20} className="text-cyan-300" /> Risk &amp; Diversification
        </h3>
        <span className="text-lg font-bold text-cyan-300">
          {Math.round(d.score)}<span className="text-sm text-gray-400"> / 100</span>
        </span>
      </div>
      <p className="text-xs text-gray-500 mb-5">
        Earns {Math.round(earned)} of {possible} points in the Risk Control pillar
        {possible > 0 && parts.some(c => !c.available) ? ' (metrics without history are left out)' : ''}.
      </p>

      {/* Holdings → effective holdings → independent bets */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
        <Stat label="Holdings" value={String(d.holdings)} note="coins in the portfolio" />
        <Stat label="Effective holdings" value={fmt(d.effectiveHoldings, 1)}
              note={`N = 1 / HHI · HHI ${fmt(d.hhi)}`} />
        <Stat label="Independent bets" value={d.effectiveBets == null ? '—' : fmt(d.effectiveBets, 1)}
              note={d.effectiveBets == null ? 'needs price history' : 'after adjusting for correlation'} />
      </div>

      {d.correlationReason ? (
        <p className="text-sm text-yellow-200/90 bg-yellow-950/20 border border-yellow-600/30 rounded-xl p-3 mb-5">
          Correlation not measured: {d.correlationReason} Diversification is judged on concentration only.
        </p>
      ) : d.averageCorrelation != null && (
        <div className="mb-5">
          <div className="flex justify-between text-sm mb-1">
            <span className="text-gray-300">Average pairwise correlation (ρ̄)</span>
            <span className="font-mono text-white">{fmt(d.averageCorrelation)}</span>
          </div>
          <div className="relative h-2 rounded-full bg-gradient-to-r from-teal-500/70 via-gray-600 to-red-500/80"
               role="img" aria-label={`Average correlation ${fmt(d.averageCorrelation)} on a scale from −1 to 1`}>
            <div className="absolute -top-1 h-4 w-1 rounded bg-white"
                 style={{ left: `calc(${((d.averageCorrelation + 1) / 2) * 100}% - 2px)` }} />
          </div>
          <div className="flex justify-between text-[11px] text-gray-500 mt-1">
            <span>−1 offsetting</span><span>0 independent</span><span>+1 lockstep</span>
          </div>
        </div>
      )}

      {/* Score components */}
      <ul className="space-y-2 mb-5">
        {parts.map(c => (
          <li key={c.key} className="flex items-start justify-between gap-3 text-sm" title={c.detail}>
            <div>
              <div className={c.available ? 'text-gray-200' : 'text-gray-500'}>{c.name}</div>
              <div className="text-xs text-gray-500">{c.available ? c.displayValue : c.detail}</div>
            </div>
            <div className={`shrink-0 font-mono ${c.available ? 'text-gray-300' : 'text-gray-600'}`}>
              {c.available ? `${c.points.toFixed(0)}/${c.weight}` : '—'}
            </div>
          </li>
        ))}
      </ul>

      {d.correlations && d.positions.length >= 2 && d.positions.length <= MAX_MATRIX && (
        <div className="overflow-x-auto mb-4">
          <table className="text-xs border-separate" style={{ borderSpacing: 2 }} aria-label="Correlation matrix">
            <thead>
              <tr>
                <th />
                {d.positions.map(p => <th key={p.id} scope="col" className="px-1 font-medium text-gray-400 max-w-[5rem] truncate">{p.name}</th>)}
              </tr>
            </thead>
            <tbody>
              {d.positions.map((row, i) => (
                <tr key={row.id}>
                  <th scope="row" className="pr-2 text-left font-medium text-gray-400 whitespace-nowrap">
                    {row.name} <span className="text-gray-600">{pct(row.weight)}</span>
                  </th>
                  {d.correlations![i].map((rho, j) => (
                    <td key={j} className="w-12 h-8 text-center font-mono text-white rounded"
                        style={{ background: i === j ? 'rgba(75, 85, 99, 0.4)' : correlationColor(rho) }}>
                      {i === j ? '—' : fmt(rho)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {d.pairs.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          <div>
            <div className="text-gray-400 mb-1">Moving in lockstep (ρ ≥ 0.80)</div>
            {lockstep.length === 0 ? <div className="text-emerald-300">None</div> : (
              <ul className="space-y-0.5">
                {lockstep.slice(0, 4).map(p => (
                  <li key={`${p.a}-${p.b}`} className="text-red-300">{p.a} / {p.b}: {fmt(p.correlation)}</li>
                ))}
              </ul>
            )}
          </div>
          {leastCorrelated && (
            <div>
              <div className="text-gray-400 mb-1">Best diversifying pair</div>
              <div className={leastCorrelated.correlation < 0.5 ? 'text-emerald-300' : 'text-gray-300'}>
                {leastCorrelated.a} / {leastCorrelated.b}: {fmt(leastCorrelated.correlation)}
              </div>
            </div>
          )}
        </div>
      )}

      <p className="text-xs text-gray-500 mt-4">
        Independent bets = (Σ wσ / σ<sub>portfolio</sub>)², the diversification ratio squared: equal to the effective
        holdings when coins are uncorrelated, closer to 1 when they move together, and higher when they offset each other.
        {d.observations > 0 && !d.correlationReason ? ` Measured over ${d.observations} daily returns.` : ''}
      </p>
    </section>
  );
};

const Stat: React.FC<{ label: string; value: string; note: string }> = ({ label, value, note }) => (
  <div className="rounded-xl bg-gray-800/60 border border-gray-700/50 p-3">
    <div className="text-xs text-gray-400">{label}</div>
    <div className="text-2xl font-bold text-white">{value}</div>
    <div className="text-[11px] text-gray-500">{note}</div>
  </div>
);

export default DiversificationCard;
