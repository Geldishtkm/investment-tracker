import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { RefreshCw } from 'lucide-react';
import { API_BASE_URL } from '../config';
import { authService } from '../services/authService';
import PortfolioEmptyState from './PortfolioEmptyState';
import Term from './ui/Term';
import InfoTip from './ui/InfoTip';
import { Badge, Metric, Panel, ShareBar, Skeleton, SkeletonMetrics, btn, segmented } from './ui/kit';
import { num, pct, usd } from './ui/format';

/** GET /api/var/portfolio: the full calculation, including the daily return history it used. */
export interface VaRCalculation {
  confidenceLevel: number;
  timeHorizon: number;
  /** The whole account: holdings plus cash. */
  portfolioValue: number;
  /** Market value of the coin holdings. */
  investedValue?: number;
  /** Uninvested cash from the starting bankroll (riskless). */
  cashValue?: number;
  /** Holdings' share of the account. */
  exposure?: number;
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

/**
 * Results by data version, confidence and horizon. The Monte Carlo run happens on request only: when the
 * page opens, after a trade (new version) or on Recalculate. Within FRESH_MS the same inputs are served from
 * memory, and identical requests in flight are shared.
 */
export const VAR_FRESH_MS = 30_000;
const varCache = new Map<string, { at: number; data: VaRCalculation }>();
const varInflight = new Map<string, Promise<VaRCalculation>>();

export function resetVaRCache() {
  varCache.clear();
  varInflight.clear();
}

/** Thrown for an empty portfolio, which the page shows as the empty state. */
class NoAssetsError extends Error {}

function fetchVaR(version: number, confidence: number, horizon: number, force: boolean): Promise<VaRCalculation> {
  const key = `${version}|${confidence}|${horizon}`;
  const hit = varCache.get(key);
  if (!force && hit && Date.now() - hit.at < VAR_FRESH_MS) return Promise.resolve(hit.data);
  const pending = varInflight.get(key);
  if (!force && pending) return pending;
  const promise = authService.authFetch(`${API_BASE_URL}/api/var/portfolio?confidenceLevel=${confidence}&timeHorizon=${horizon}`)
    .then(async response => {
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        if (body?.code === 'NO_ASSETS') throw new NoAssetsError();
        throw new Error(body?.error || `Failed to calculate VaR (HTTP ${response.status})`);
      }
      varCache.set(key, { at: Date.now(), data: body as VaRCalculation });
      return body as VaRCalculation;
    })
    .finally(() => { if (varInflight.get(key) === promise) varInflight.delete(key); });
  varInflight.set(key, promise);
  return promise;
}
const HORIZONS = [1, 7, 30];

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

export const VOLATILITY_INFO = "Measures how wildly your portfolio's value swings day-to-day. Lower volatility indicates smoother, more predictable capital growth.";
export const EXPECTED_RETURN_INFO = 'The average daily statistical growth rate of your portfolio based on recent price performance and asset weights.';

/** One figure of the return profile: label with an info tooltip, a large high-contrast value, and its basis. */
const ProfileCard: React.FC<{ label: string; info: string; value: string; sub: string; tone?: 'up' | 'down' }> = ({ label, info, value, sub, tone }) => (
  <div className="rounded-lg border border-term-line bg-black/20 p-3" data-testid={`profile-${label}`}>
    <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-[0.1em] text-term-muted">
      {label}
      <InfoTip title={label} what={info} />
    </div>
    <div className={`num mt-1.5 text-2xl font-medium ${tone === 'up' ? 'text-term-up' : tone === 'down' ? 'text-term-down' : 'text-term-text'}`}>{value}</div>
    <div className="mt-0.5 text-[11px] text-term-dim">{sub}</div>
  </div>
);

interface VaRDashboardProps {
  /** Opens the add-asset flow; shown when the portfolio turns out to be empty. */
  onAddAsset: () => void;
  /** Changes after every trade, so the figures recalculate on the new positions. */
  refreshKey?: number;
}

const segment = segmented.option;

const VaRDashboard: React.FC<VaRDashboardProps> = ({ onAddAsset, refreshKey = 0 }) => {
  const [calc, setCalc] = useState<VaRCalculation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [portfolioEmpty, setPortfolioEmpty] = useState(false);
  const [confidenceLevel, setConfidenceLevel] = useState(0.95);
  const [timeHorizon, setTimeHorizon] = useState(1);

  const calculateVaR = useCallback(async (force = false) => {
    setLoading(true);
    setError(null);
    try {
      setCalc(await fetchVaR(refreshKey, confidenceLevel, timeHorizon, force));
      setPortfolioEmpty(false);
    } catch (err) {
      // An empty portfolio is a normal state, not an error
      if (err instanceof NoAssetsError) setPortfolioEmpty(true);
      else setError(err instanceof Error ? err.message : 'Failed to calculate VaR');
    } finally {
      setLoading(false);
    }
  }, [confidenceLevel, timeHorizon, refreshKey]);

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

  if (portfolioEmpty) return <PortfolioEmptyState onAddAsset={onAddAsset} />;

  const v = calc?.portfolioValue || 0;
  const invested = calc?.investedValue ?? v;
  const cash = calc?.cashValue ?? 0;
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
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-term-line bg-term-panel/75 px-4 py-2.5 backdrop-blur-md">
        <span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-term-dim">Confidence</span>
        <div className={segmented.track} role="group" aria-label="Confidence level">
          {CONFIDENCE.map(c => (
            <button key={c} type="button" aria-pressed={confidenceLevel === c} onClick={() => setConfidenceLevel(c)} className={segment(confidenceLevel === c)}>
              {pct(c, 0)}
            </button>
          ))}
        </div>
        <span className="ml-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-term-dim">Horizon</span>
        <div className={segmented.track} role="group" aria-label="Time horizon">
          {HORIZONS.map(h => (
            <button key={h} type="button" aria-pressed={timeHorizon === h} onClick={() => setTimeHorizon(h)} className={segment(timeHorizon === h)}>
              {h}D
            </button>
          ))}
        </div>
        <div className="ml-auto flex items-center gap-3">
          {level && <Badge tone={level === 'HIGH' ? 'down' : level === 'MEDIUM' ? 'warn' : 'up'}>{level} risk</Badge>}
          <button type="button" className={btn.secondary} onClick={() => void calculateVaR(true)} disabled={loading} aria-label="Recalculate">
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
            <div className="bg-term-panel p-4" data-testid="var-account">
              <Metric label="Account value" term="Equity" value={usd(v)}
                      sub={cash > 0 ? `coins ${usd(invested, 0)} · cash ${usd(cash, 0)}` : `${pct(confidenceLevel, 0)} · ${timeHorizon}-day`} />
            </div>
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
                        <CartesianGrid stroke="#1C2433" vertical={false} />
                        <XAxis dataKey="mid" type="number" domain={['dataMin', 'dataMax']} tickFormatter={x => pct(x, 0)}
                               stroke="#5F6B82" tick={{ fontSize: 11, fontFamily: 'JetBrains Mono' }} tickLine={false} axisLine={false} />
                        <YAxis allowDecimals={false} width={32} stroke="#5F6B82" tick={{ fontSize: 11, fontFamily: 'JetBrains Mono' }} tickLine={false} axisLine={false} />
                        <Tooltip cursor={{ fill: 'rgba(255,255,255,0.03)' }}
                                 contentStyle={{ background: '#141B2A', border: '1px solid #2A3447', borderRadius: 6, fontSize: 12 }}
                                 labelFormatter={(_, p) => p?.[0] ? `${pct(p[0].payload.from, 2)} to ${pct(p[0].payload.to, 2)}` : ''}
                                 formatter={(n: number) => [n, 'days']} />
                        <Bar dataKey="count" isAnimationActive={false}>
                          {daily.bins.map((b, i) => <Cell key={i} fill={b.tail ? '#E06C75' : b.mid < 0 ? '#5F6B82' : '#4FD1A5'} fillOpacity={b.tail ? 0.9 : 0.6} />)}
                        </Bar>
                        <ReferenceLine x={daily.cutoff} stroke="#E06C75" strokeDasharray="4 3"
                                       label={{ value: `VaR ${pct(confidenceLevel, 0)}`, fill: '#E06C75', fontSize: 11, position: 'top' }} />
                        <ReferenceLine x={daily.shortfall} stroke="#E3B062" strokeDasharray="2 3"
                                       label={{ value: 'CVaR', fill: '#E3B062', fontSize: 11, position: 'insideTopLeft' }} />
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

            <div className="flex flex-col gap-4">
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
              <Panel title="Return profile" className="flex-1">
                <div className="grid grid-cols-2 gap-3" data-testid="return-profile">
                  <ProfileCard label="Volatility" info={VOLATILITY_INFO}
                               value={pct(calc.annualizedVolatility ?? calc.volatility * Math.sqrt(365), 1)}
                               sub={`annualized · ${pct(calc.volatility, 2)} daily`} />
                  <ProfileCard label="Expected return" info={EXPECTED_RETURN_INFO}
                               value={pct(calc.expectedReturn, 3, true)}
                               sub={`mean daily · ${pct(calc.expectedReturn * 365, 1, true)} a year projected`}
                               tone={calc.expectedReturn >= 0 ? 'up' : 'down'} />
                </div>
              </Panel>
            </div>
          </div>

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
