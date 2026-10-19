import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { RefreshCw } from 'lucide-react';
import { API_BASE_URL } from '../config';
import { authService } from '../services/authService';
import PortfolioEmptyState from './PortfolioEmptyState';
import { SLICE_COLORS } from './PortfolioPage';
import Term from './ui/Term';
import { Badge, Metric, Panel, ShareBar, Skeleton, SkeletonMetrics, btn } from './ui/kit';
import { num, pct, usd } from './ui/format';

/** GET /api/var/portfolio: the full calculation, including the daily return history it used. */
export interface VaRCalculation {
  confidenceLevel: number;
  timeHorizon: number;
  portfolioValue: number;
  historicalVaR: number;
  parametricVaR: number;
  monteCarloVaR: number;
  conditionalVaR: number;
  /** Daily standard deviation of portfolio returns. */
  volatility: number;
  annualizedVolatility?: number;
  skewness: number;
  kurtosis: number;
  /** Mean daily portfolio return. */
  expectedReturn: number;
  assetWeights: Record<string, number>;
  /** Mean daily return per asset. */
  assetReturns: Record<string, number>;
  historicalReturns: number[];
  numberOfSimulations?: number;
  observations?: number;
  historyStart?: string;
  historyEnd?: string;
  historicalVaRMethod?: string;
}

const CONFIDENCE = [0.9, 0.95, 0.99];
const HORIZONS = [1, 7, 30];
const SHOCKS = [-0.1, -0.3, -0.5];

/** Same thresholds as the backend's risk level: historical VaR above 10% of value is high, above 5% medium. */
export function riskLevel(varFraction: number): 'LOW' | 'MEDIUM' | 'HIGH' {
  return varFraction > 0.10 ? 'HIGH' : varFraction > 0.05 ? 'MEDIUM' : 'LOW';
}

/** Linearly interpolated quantile of sorted data, the same method the backend uses for historical VaR. */
export function quantile(sorted: number[], p: number): number {
  const h = (sorted.length - 1) * p;
  const lo = Math.floor(h), hi = Math.ceil(h);
  return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
}

/** Equal-width histogram of returns; each bin knows whether it lies in the tail beyond the cutoff. */
export function histogram(returns: number[], bins: number, cutoff: number) {
  if (returns.length === 0) return [];
  const min = Math.min(...returns), max = Math.max(...returns);
  const width = (max - min) / bins || 1e-9;
  const counts = Array.from({ length: bins }, (_, i) => ({ from: min + i * width, to: min + (i + 1) * width, count: 0, tail: false }));
  for (const r of returns) counts[Math.min(bins - 1, Math.floor((r - min) / width))].count++;
  counts.forEach(b => { b.tail = b.to <= cutoff + 1e-12; });
  return counts.map(b => ({ ...b, mid: (b.from + b.to) / 2 }));
}

interface VaRDashboardProps {
  /** Opens the add-asset flow; shown when the portfolio turns out to be empty. */
  onAddAsset: () => void;
}

const segment = (active: boolean) =>
  `num rounded px-2.5 py-1 text-xs ${active ? 'bg-term-raised text-term-text' : 'text-term-muted hover:text-term-text'}`;

const VaRDashboard: React.FC<VaRDashboardProps> = ({ onAddAsset }) => {
  const [calc, setCalc] = useState<VaRCalculation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [portfolioEmpty, setPortfolioEmpty] = useState(false);
  const [confidenceLevel, setConfidenceLevel] = useState(0.95);
  const [timeHorizon, setTimeHorizon] = useState(1);

  const calculateVaR = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await authService.authFetch(
        `${API_BASE_URL}/api/var/portfolio?confidenceLevel=${confidenceLevel}&timeHorizon=${timeHorizon}`);
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        // An empty portfolio is a normal state, not an error; data problems are explained in "error"
        if (body?.code === 'NO_ASSETS') { setPortfolioEmpty(true); return; }
        throw new Error(body?.error || `Failed to calculate VaR (HTTP ${response.status})`);
      }
      setPortfolioEmpty(false);
      setCalc(body as VaRCalculation);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to calculate VaR');
    } finally {
      setLoading(false);
    }
  }, [confidenceLevel, timeHorizon]);

  useEffect(() => { void calculateVaR(); }, [calculateVaR]);

  const daily = useMemo(() => {
    const r = calc?.historicalReturns ?? [];
    if (r.length === 0) return null;
    const sorted = [...r].sort((a, b) => a - b);
    const cutoff = quantile(sorted, 1 - confidenceLevel);
    const tail = sorted.filter(x => x <= cutoff);
    return {
      cutoff,
      shortfall: tail.reduce((s, x) => s + x, 0) / tail.length,
      worst: sorted[0],
      best: sorted[sorted.length - 1],
      bins: histogram(r, 48, cutoff),
    };
  }, [calc, confidenceLevel]);

  const exposures = useMemo(() => {
    if (!calc) return [];
    return Object.entries(calc.assetWeights ?? {})
      .map(([name, weight]) => ({ name, weight, value: weight * calc.portfolioValue, meanDaily: calc.assetReturns?.[name] ?? null }))
      .sort((a, b) => b.weight - a.weight);
  }, [calc]);

  if (portfolioEmpty) return <PortfolioEmptyState onAddAsset={onAddAsset} />;

  const v = calc?.portfolioValue || 0;
  const level = calc ? riskLevel(calc.historicalVaR / (v || 1)) : null;
  const methods = calc ? [
    { key: 'Historical VaR', value: calc.historicalVaR },
    { key: 'Parametric VaR', value: calc.parametricVaR },
    { key: 'Monte Carlo VaR', value: calc.monteCarloVaR },
    { key: 'CVaR', value: calc.conditionalVaR },
  ] : [];
  const maxMethod = Math.max(1, ...methods.map(m => m.value));

  return (
    <div className="space-y-4">
      {/* Parameters */}
      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-term-line bg-term-panel px-4 py-2.5">
        <span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-term-dim">Confidence</span>
        <div className="flex gap-1 rounded-md border border-term-line bg-term-bg p-1" role="group" aria-label="Confidence level">
          {CONFIDENCE.map(c => (
            <button key={c} type="button" aria-pressed={confidenceLevel === c} onClick={() => setConfidenceLevel(c)} className={segment(confidenceLevel === c)}>
              {pct(c, 0)}
            </button>
          ))}
        </div>
        <span className="ml-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-term-dim">Horizon</span>
        <div className="flex gap-1 rounded-md border border-term-line bg-term-bg p-1" role="group" aria-label="Time horizon">
          {HORIZONS.map(h => (
            <button key={h} type="button" aria-pressed={timeHorizon === h} onClick={() => setTimeHorizon(h)} className={segment(timeHorizon === h)}>
              {h}D
            </button>
          ))}
        </div>
        <div className="ml-auto flex items-center gap-3">
          {level && <Badge tone={level === 'HIGH' ? 'down' : level === 'MEDIUM' ? 'warn' : 'up'}>{level} risk</Badge>}
          <button type="button" className={btn.secondary} onClick={() => void calculateVaR()} disabled={loading} aria-label="Recalculate">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Recalculate
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-term-down/40 bg-term-down-soft px-4 py-3 text-sm text-term-down" role="alert">{error}</div>
      )}

      {!calc && loading ? (
        <><SkeletonMetrics count={5} /><Skeleton className="h-72 w-full" /></>
      ) : calc && (
        <div className={`space-y-4 transition-opacity ${loading ? 'opacity-60' : ''}`}>
          <div className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-term-line bg-term-line md:grid-cols-5" data-testid="var-headline">
            <div className="bg-term-panel p-4"><Metric label="Portfolio value" term="Equity" value={usd(v)} sub={`${pct(confidenceLevel, 0)} · ${timeHorizon}-day`} /></div>
            {methods.map(m => (
              <div key={m.key} className="bg-term-panel p-4">
                <Metric label={m.key === 'CVaR' ? 'CVaR (ES)' : m.key} term={m.key} value={usd(-m.value)} tone="down"
                        sub={`${pct(v ? m.value / v : 0, 2)} of value`} />
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <Panel title="Daily return distribution" className="xl:col-span-2"
                   subtitle={`${calc.observations ?? calc.historicalReturns.length} daily returns${calc.historyStart ? ` · ${calc.historyStart} → ${calc.historyEnd}` : ''}`}>
              {daily ? (
                <>
                  <div className="h-64" role="img" aria-label={`Histogram of daily returns; the ${pct(1 - confidenceLevel, 0)} tail lies below ${pct(daily.cutoff, 2)}`}>
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={daily.bins} margin={{ top: 22, right: 8, bottom: 0, left: 0 }} barCategoryGap={1}>
                        <CartesianGrid stroke="#222938" vertical={false} />
                        <XAxis dataKey="mid" type="number" domain={['dataMin', 'dataMax']} tickFormatter={x => pct(x, 0)}
                               stroke="#5D667A" tick={{ fontSize: 11, fontFamily: 'JetBrains Mono' }} tickLine={false} axisLine={false} />
                        <YAxis allowDecimals={false} width={32} stroke="#5D667A" tick={{ fontSize: 11, fontFamily: 'JetBrains Mono' }} tickLine={false} axisLine={false} />
                        <Tooltip cursor={{ fill: 'rgba(255,255,255,0.03)' }}
                                 contentStyle={{ background: '#171C27', border: '1px solid #2E3647', borderRadius: 6, fontSize: 12 }}
                                 labelFormatter={(_, p) => p?.[0] ? `${pct(p[0].payload.from, 2)} to ${pct(p[0].payload.to, 2)}` : ''}
                                 formatter={(n: number) => [n, 'days']} />
                        <Bar dataKey="count" isAnimationActive={false}>
                          {daily.bins.map((b, i) => <Cell key={i} fill={b.tail ? '#C8464F' : b.mid < 0 ? '#5D667A' : '#3FA586'} fillOpacity={b.tail ? 0.9 : 0.6} />)}
                        </Bar>
                        <ReferenceLine x={daily.cutoff} stroke="#C8464F" strokeDasharray="4 3"
                                       label={{ value: `VaR ${pct(confidenceLevel, 0)}`, fill: '#C8464F', fontSize: 11, position: 'top' }} />
                        <ReferenceLine x={daily.shortfall} stroke="#C99A3E" strokeDasharray="2 3"
                                       label={{ value: 'CVaR', fill: '#C99A3E', fontSize: 11, position: 'insideTopLeft' }} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-4 text-xs md:grid-cols-4">
                    <Metric size="sm" label="1-day VaR" term="Historical VaR" value={pct(daily.cutoff, 2)} tone="down" />
                    <Metric size="sm" label="1-day CVaR" term="CVaR" value={pct(daily.shortfall, 2)} tone="down" />
                    <Metric size="sm" label="Worst day" term="Historical VaR" value={pct(daily.worst, 2)} tone="down" />
                    <Metric size="sm" label="Best day" term="Expected return" value={pct(daily.best, 2, true)} tone="up" />
                  </div>
                </>
              ) : <p className="text-sm text-term-muted">No return history available.</p>}
            </Panel>

            <div className="space-y-4">
              <Panel title="Method comparison" subtitle={`${timeHorizon}-day loss at ${pct(confidenceLevel, 0)}`}>
                <ul className="space-y-3">
                  {methods.map(m => (
                    <li key={m.key}>
                      <div className="flex justify-between text-xs">
                        <Term term={m.key} className="text-term-muted">{m.key}</Term>
                        <span className="num text-term-text">{usd(m.value)}</span>
                      </div>
                      <ShareBar value={m.value / maxMethod} tone={m.key === 'CVaR' ? 'down' : 'muted'} className="mt-1 h-1.5" />
                    </li>
                  ))}
                </ul>
                {calc.historicalVaRMethod && <p className="mt-3 text-[11px] text-term-dim">Historical: {calc.historicalVaRMethod}.</p>}
              </Panel>
              <Panel title="Return profile">
                <div className="grid grid-cols-2 gap-4">
                  <Metric size="sm" label="Volatility" value={pct(calc.annualizedVolatility ?? calc.volatility * Math.sqrt(365), 1)} sub={`${pct(calc.volatility, 2)} daily`} />
                  <Metric size="sm" label="Expected return" value={pct(calc.expectedReturn, 3, true)} sub="mean daily" tone={calc.expectedReturn >= 0 ? 'up' : 'down'} />
                  <Metric size="sm" label="Skewness" value={num(calc.skewness, 2)} tone={calc.skewness < -0.5 ? 'down' : undefined}
                          sub={calc.skewness < 0 ? 'left tail heavier' : 'right tail heavier'} />
                  <Metric size="sm" label="Kurtosis" value={num(calc.kurtosis, 2)} tone={calc.kurtosis > 3 ? 'down' : undefined}
                          sub={calc.kurtosis > 3 ? 'fat tails' : 'near-normal tails'} />
                </div>
              </Panel>
            </div>
          </div>

          <Panel flush title="Exposure breakdown" subtitle="Current weights, and the loss each position would cause under a price shock">
            <div className="overflow-auto">
              <table className="w-full min-w-[820px] text-sm" aria-label="Exposure breakdown">
                <thead className="text-[11px] uppercase tracking-wider text-term-dim">
                  <tr className="border-b border-term-line">
                    <th scope="col" className="px-3 py-2 text-left font-medium">Asset</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">Weight</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">Exposure</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium"><Term term="Expected return">Mean daily return</Term></th>
                    {SHOCKS.map(s => <th key={s} scope="col" className="px-3 py-2 text-right font-medium">{pct(s, 0)} shock</th>)}
                  </tr>
                </thead>
                <tbody className="divide-y divide-term-line">
                  {exposures.map((e, i) => (
                    <tr key={e.name} className="hover:bg-term-hover">
                      <td className="px-3 py-2.5">
                        <span className="flex items-center gap-2 font-medium text-term-text">
                          <span className="h-2 w-2 rounded-sm" style={{ background: SLICE_COLORS[i % SLICE_COLORS.length] }} />{e.name}
                        </span>
                      </td>
                      <td className="px-3 py-2.5"><div className="ml-auto w-24"><div className="num text-right text-xs">{pct(e.weight, 1)}</div><ShareBar value={e.weight} className="mt-1" /></div></td>
                      <td className="num px-3 py-2.5 text-right text-term-text">{usd(e.value)}</td>
                      <td className={`num px-3 py-2.5 text-right ${e.meanDaily == null ? 'text-term-dim' : e.meanDaily >= 0 ? 'text-term-up' : 'text-term-down'}`}>
                        {pct(e.meanDaily, 3, true)}
                      </td>
                      {SHOCKS.map(s => <td key={s} className="num px-3 py-2.5 text-right text-term-down">{usd(e.value * s)}</td>)}
                    </tr>
                  ))}
                </tbody>
                <tfoot className="border-t border-term-line2">
                  <tr>
                    <td className="px-3 py-2.5 text-xs font-semibold uppercase tracking-wider text-term-dim" colSpan={2}>Portfolio</td>
                    <td className="num px-3 py-2.5 text-right font-semibold text-term-text">{usd(v)}</td>
                    <td />
                    {SHOCKS.map(s => <td key={s} className="num px-3 py-2.5 text-right font-semibold text-term-down">{usd(v * s)}</td>)}
                  </tr>
                </tfoot>
              </table>
            </div>
          </Panel>

          <p className="text-xs text-term-dim">
            Monte Carlo uses {calc.numberOfSimulations ? num(calc.numberOfSimulations, 0) : 'simulated'} paths. VaR is the loss not exceeded with the
            chosen confidence; CVaR is the average loss when it is.
          </p>
        </div>
      )}
    </div>
  );
};

export default VaRDashboard;
