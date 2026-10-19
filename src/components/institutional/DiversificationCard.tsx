import React from 'react';
import { Layers } from 'lucide-react';
import { PortfolioDiversification, ScoreComponent } from '../../types';

const DIVERSIFICATION_KEYS = ['concentration', 'correlation', 'independent-bets'];
/** Matrices beyond this size are listed as pairs only; the grid gets unreadable. */
const MAX_MATRIX = 10;

/** Background for a correlation cell: red when moving together, slate near zero, teal when offsetting. */
export function correlationColor(rho: number): string {
  const a = Math.min(1, Math.abs(rho));
  return rho >= 0 ? `rgba(200, 70, 79, ${0.12 + 0.68 * a})` : `rgba(63, 165, 134, ${0.12 + 0.68 * a})`;
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
    <section className="rounded-lg border border-term-line bg-term-panel p-4"
             aria-labelledby="diversification-heading" data-testid="diversification-card">
      <div className="flex flex-wrap items-baseline justify-between gap-2 mb-1">
        <h3 id="diversification-heading" className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-term-muted">
          <Layers size={14} className="text-term-accent" /> Risk &amp; Diversification
        </h3>
        <span className="num text-lg text-term-text">
          {Math.round(d.score)}<span className="text-sm text-term-dim"> / 100</span>
        </span>
      </div>
      <p className="text-xs text-term-dim mb-5">
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
        <p className="text-sm text-term-warn bg-term-warn-soft border border-term-warn/40 rounded-md p-3 mb-5">
          Correlation not measured: {d.correlationReason} Diversification is judged on concentration only.
        </p>
      ) : d.averageCorrelation != null && (
        <div className="mb-5">
          <div className="flex justify-between text-sm mb-1">
            <span className="text-term-muted">Average pairwise correlation (ρ̄)</span>
            <span className="num text-term-text">{fmt(d.averageCorrelation)}</span>
          </div>
          <div className="relative h-2 rounded-full bg-gradient-to-r from-[#3FA586]/70 via-[#2E3647] to-[#C8464F]/80"
               role="img" aria-label={`Average correlation ${fmt(d.averageCorrelation)} on a scale from −1 to 1`}>
            <div className="absolute -top-1 h-4 w-1 rounded bg-term-text"
                 style={{ left: `calc(${((d.averageCorrelation + 1) / 2) * 100}% - 2px)` }} />
          </div>
          <div className="flex justify-between text-[11px] text-term-dim mt-1">
            <span>−1 offsetting</span><span>0 independent</span><span>+1 lockstep</span>
          </div>
        </div>
      )}

      {/* Score components */}
      <ul className="space-y-2 mb-5">
        {parts.map(c => (
          <li key={c.key} className="flex items-start justify-between gap-3 text-sm" title={c.detail}>
            <div>
              <div className={c.available ? 'text-term-text' : 'text-term-dim'}>{c.name}</div>
              <div className="text-xs text-term-dim">{c.available ? c.displayValue : c.detail}</div>
            </div>
            <div className={`shrink-0 num ${c.available ? 'text-term-muted' : 'text-term-dim'}`}>
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
                {d.positions.map(p => <th key={p.id} scope="col" className="px-1 font-medium text-term-muted max-w-[5rem] truncate">{p.name}</th>)}
              </tr>
            </thead>
            <tbody>
              {d.positions.map((row, i) => (
                <tr key={row.id}>
                  <th scope="row" className="pr-2 text-left font-medium text-term-muted whitespace-nowrap">
                    {row.name} <span className="text-term-dim">{pct(row.weight)}</span>
                  </th>
                  {d.correlations![i].map((rho, j) => (
                    <td key={j} className="w-12 h-8 text-center num text-term-text rounded"
                        style={{ background: i === j ? 'rgba(46, 54, 71, 0.6)' : correlationColor(rho) }}>
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
            <div className="text-term-muted mb-1">Moving in lockstep (ρ ≥ 0.80)</div>
            {lockstep.length === 0 ? <div className="text-term-up">None</div> : (
              <ul className="space-y-0.5">
                {lockstep.slice(0, 4).map(p => (
                  <li key={`${p.a}-${p.b}`} className="text-term-down">{p.a} / {p.b}: {fmt(p.correlation)}</li>
                ))}
              </ul>
            )}
          </div>
          {leastCorrelated && (
            <div>
              <div className="text-term-muted mb-1">Best diversifying pair</div>
              <div className={leastCorrelated.correlation < 0.5 ? 'text-term-up' : 'text-term-muted'}>
                {leastCorrelated.a} / {leastCorrelated.b}: {fmt(leastCorrelated.correlation)}
              </div>
            </div>
          )}
        </div>
      )}

      <p className="text-xs text-term-dim mt-4">
        Independent bets = (Σ wσ / σ<sub>portfolio</sub>)², the diversification ratio squared: equal to the effective
        holdings when coins are uncorrelated, closer to 1 when they move together, and higher when they offset each other.
        {d.observations > 0 && !d.correlationReason ? ` Measured over ${d.observations} daily returns.` : ''}
      </p>
    </section>
  );
};

const Stat: React.FC<{ label: string; value: string; note: string }> = ({ label, value, note }) => (
  <div className="rounded-md border border-term-line bg-term-bg p-3">
    <div className="text-xs text-term-muted">{label}</div>
    <div className="num text-2xl text-term-text">{value}</div>
    <div className="text-[11px] text-term-dim">{note}</div>
  </div>
);

export default DiversificationCard;
