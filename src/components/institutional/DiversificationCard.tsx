import React from 'react';
import { Layers } from 'lucide-react';
import { PortfolioDiversification, ScoreComponent } from '../../types';

const DIVERSIFICATION_KEYS = ['concentration', 'correlation', 'independent-bets'];
/** Matrices beyond this size are listed as pairs only; the grid gets unreadable. */
const MAX_MATRIX = 10;

/** Background for a correlation cell: red when moving together, slate near zero, teal when offsetting. */
export function correlationColor(rho: number): string {
  const a = Math.min(1, Math.abs(rho));
  return rho >= 0 ? `rgba(224, 108, 117, ${0.12 + 0.68 * a})` : `rgba(79, 209, 165, ${0.12 + 0.68 * a})`;
}

const fmt = (v: number, digits = 2) => v.toFixed(digits);
const pct = (v: number) => `${(v * 100).toFixed(1)}%`;

interface Props {
  diversification: PortfolioDiversification;
  /** The Risk pillar's components, for the points diversification earned. */
  riskComponents: ScoreComponent[];
}

/** Plain-language reading of an average correlation. */
export function correlationLabel(rho: number): string {
  if (rho >= 0.7) return 'high: holdings move together';
  if (rho >= 0.4) return 'moderate';
  if (rho > 0.1) return 'low';
  return rho < -0.1 ? 'negative: holdings offset each other' : 'near zero: independent';
}

/**
 * Risk & Diversification: the three intuitive measures (HHI, effective holdings, average correlation),
 * the correlation matrix and the pairs worth acting on.
 */
const DiversificationCard: React.FC<Props> = ({ diversification: d, riskComponents }) => {
  const parts = riskComponents.filter(c => DIVERSIFICATION_KEYS.includes(c.key));
  const earned = parts.reduce((sum, c) => sum + c.points, 0);
  const possible = parts.reduce((sum, c) => sum + c.weight, 0);
  const lockstep = d.pairs.filter(p => p.correlation >= 0.8);
  const leastCorrelated = d.pairs.length > 1 ? d.pairs[d.pairs.length - 1] : null;
  const rho = d.averageCorrelation;

  return (
    <section className="rounded-xl border border-term-line bg-term-panel/75 p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.035)] backdrop-blur-md"
             aria-labelledby="diversification-heading" data-testid="diversification-card">
      <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
        <h3 id="diversification-heading" className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-term-muted">
          <Layers size={14} className="text-term-accent" /> Risk &amp; Diversification
        </h3>
        <span className="num text-lg text-term-text">
          {Math.round(d.score)}<span className="text-sm text-term-dim"> / 100</span>
        </span>
      </div>
      <p className="mb-5 text-xs text-term-dim">
        Earns {Math.round(earned)} of {possible} points in the Risk Control pillar
        {possible > 0 && parts.some(c => !c.available) ? ' (metrics without history are left out)' : ''}.
      </p>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Stat label="HHI" value={fmt(d.hhi)} note="Σ w² · 1.00 means a single holding" />
        <Stat label="Effective holdings" value={`${fmt(d.effectiveHoldings)} of ${d.holdings}`}
              note={`${d.holdings === 1 ? 'holding' : 'holdings'}, weighted by size (1 / HHI)`} />
        <Stat label="Average correlation" value={rho == null ? '—' : fmt(rho)}
              note={rho == null ? (d.holdings === 1 ? 'needs two or more holdings' : 'needs price history') : correlationLabel(rho)}
              tone={rho == null ? undefined : rho >= 0.7 ? 'down' : rho <= 0.3 ? 'up' : undefined} />
      </div>

      {d.correlationReason && (
        <p className="mb-5 rounded-md border border-term-warn/40 bg-term-warn-soft p-3 text-sm text-term-warn">
          Correlation not measured: {d.correlationReason} Diversification is judged on concentration only.
        </p>
      )}

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
                        style={{ background: i === j ? 'rgba(42, 52, 71, 0.6)' : correlationColor(rho) }}>
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

      {d.observations > 0 && !d.correlationReason && (
        <p className="mt-4 text-xs text-term-dim">Correlations measured over {d.observations} daily returns.</p>
      )}
    </section>
  );
};

const Stat: React.FC<{ label: string; value: string; note: string; tone?: 'up' | 'down' }> = ({ label, value, note, tone }) => (
  <div className="rounded-md border border-term-line bg-black/20 p-3" data-testid={`div-stat-${label}`}>
    <div className="text-xs text-term-muted">{label}</div>
    <div className={`num text-2xl ${tone === 'down' ? 'text-term-down' : tone === 'up' ? 'text-term-up' : 'text-term-text'}`}>{value}</div>
    <div className="text-[11px] text-term-dim">{note}</div>
  </div>
);

export default DiversificationCard;
