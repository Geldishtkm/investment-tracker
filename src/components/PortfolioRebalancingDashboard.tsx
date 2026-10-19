import React, { useEffect, useMemo, useState } from 'react';
import { RefreshCw, RotateCcw, Scale } from 'lucide-react';
import { API_BASE_URL } from '../config';
import { assetService } from '../services/api';
import { authService } from '../services/authService';
import { AssetWithPrice } from '../types';
import { todayLocal } from '../utils/dates';
import PortfolioEmptyState from './PortfolioEmptyState';
import { SLICE_COLORS } from './PortfolioPage';
import Term from './ui/Term';
import { Badge, Metric, Modal, Panel, SkeletonRows, btn } from './ui/kit';
import { pct, qty as fmtQty, usd } from './ui/format';

/** POST /api/portfolio-rebalancing/optimize and /black-litterman. */
interface OptimizationResult {
  currentAllocation: Record<string, number>;
  targetAllocation: Record<string, number>;
  currentRisk: number;
  targetRisk: number;
  expectedReturn: number;
  riskReduction: number;
  totalTransactionCost: number;
  optimizationMethod: string;
  portfolioStatus: string;
}

export interface SuggestedTrade {
  asset: AssetWithPrice;
  side: 'BUY' | 'SELL';
  quantity: number;
  value: number;
  price: number;
}

/** Weights below this drift are left alone, so tiny differences don't generate trades. */
const DEFAULT_BAND = 0.01;

/**
 * Trades that move each holding from its current weight to its target. Sizes are relative to the
 * holdings' value, so sells fund buys. Sells come first; a target of 0 sells the whole position.
 */
export function suggestTrades(assets: AssetWithPrice[], targets: Record<string, number>, band: number): SuggestedTrade[] {
  const priced = assets.map(a => ({ a, price: a.currentPrice || a.pricePerUnit, value: a.quantity * (a.currentPrice || a.pricePerUnit) }));
  const total = priced.reduce((s, p) => s + p.value, 0);
  if (!(total > 0)) return [];
  const trades: SuggestedTrade[] = [];
  for (const { a, price, value } of priced) {
    const target = targets[a.name] ?? 0;
    const drift = value / total - target;
    if (Math.abs(drift) < band || !(price > 0)) continue;
    const deltaValue = target * total - value;
    const side = deltaValue > 0 ? 'BUY' : 'SELL';
    // Round buys down and sells to the holding, so rounding never needs more cash or coins than exist
    let quantity = Math.abs(deltaValue) / price;
    quantity = target === 0 && side === 'SELL' ? a.quantity : Math.floor(quantity * 1e8) / 1e8;
    if (side === 'SELL') quantity = Math.min(quantity, a.quantity);
    if (quantity <= 0) continue;
    trades.push({ asset: a, side, quantity, value: quantity * price, price });
  }
  return trades.sort((x, y) => (x.side === y.side ? y.value - x.value : x.side === 'SELL' ? -1 : 1));
}

interface Props {
  /** Opens the add-asset flow; shown when the portfolio is empty. */
  onAddAsset: () => void;
  /** Reload holdings and cash after trades were executed. */
  onTraded?: () => void;
  onShowToast?: (type: 'success' | 'error' | 'info', message: string) => void;
}

type Source = 'Current' | 'MVO' | 'Black-Litterman' | 'Manual';

const PortfolioRebalancingDashboard: React.FC<Props> = ({ onAddAsset, onTraded, onShowToast }) => {
  const [assets, setAssets] = useState<AssetWithPrice[]>([]);
  const [loading, setLoading] = useState(true);
  const [optimizing, setOptimizing] = useState<null | 'MVO' | 'Black-Litterman'>(null);
  const [error, setError] = useState<string | null>(null);
  const [targets, setTargets] = useState<Record<string, number>>({});
  const [source, setSource] = useState<Source>('Current');
  const [result, setResult] = useState<OptimizationResult | null>(null);
  const [riskTolerance, setRiskTolerance] = useState(0.5);
  const [views, setViews] = useState<Record<string, number>>({});
  const [showViews, setShowViews] = useState(false);
  const [band, setBand] = useState(DEFAULT_BAND);
  const [confirming, setConfirming] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [execLog, setExecLog] = useState<{ label: string; ok: boolean; message?: string }[] | null>(null);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      // Largest first, matching the colours used on the other pages
      const data = [...await assetService.getAllAssetsWithPrices()]
        .sort((x, y) => y.quantity * (y.currentPrice || y.pricePerUnit) - x.quantity * (x.currentPrice || x.pricePerUnit));
      setAssets(data);
      setTargets(currentWeights(data));
      setSource('Current');
      setResult(null);
    } catch (err) {
      setError('Failed to load portfolio data');
      console.error('Error loading portfolio data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const current = useMemo(() => currentWeights(assets), [assets]);
  const total = assets.reduce((s, a) => s + a.quantity * (a.currentPrice || a.pricePerUnit), 0);
  const targetSum = Object.values(targets).reduce((s, w) => s + w, 0);
  const sumOk = Math.abs(targetSum - 1) < 0.005;
  const trades = useMemo(() => (sumOk ? suggestTrades(assets, targets, band) : []), [assets, targets, band, sumOk]);
  const turnover = trades.reduce((s, t) => s + t.value, 0) / 2;
  const maxDrift = Math.max(0, ...assets.map(a => Math.abs((current[a.name] ?? 0) - (targets[a.name] ?? 0))));

  const optimize = async (method: 'MVO' | 'Black-Litterman') => {
    setOptimizing(method);
    setError(null);
    try {
      const path = method === 'MVO' ? 'optimize' : 'black-litterman';
      const response = await authService.authFetch(`${API_BASE_URL}/api/portfolio-rebalancing/${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'current_user', riskTolerance, ...(method === 'Black-Litterman' ? { userViews: views } : {}) }),
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        // Assets removed elsewhere: show the empty state, not an error
        if (body?.code === 'NO_ASSETS') { setAssets([]); return; }
        throw new Error(body?.error || `Optimization failed (HTTP ${response.status})`);
      }
      const r = body as OptimizationResult;
      setResult(r);
      const next: Record<string, number> = {};
      assets.forEach(a => { next[a.name] = r.targetAllocation?.[a.name] ?? 0; });
      setTargets(next);
      setSource(method);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Optimization failed. Please try again.');
    } finally {
      setOptimizing(null);
    }
  };

  const setTarget = (name: string, weight: number) => {
    setTargets(t => ({ ...t, [name]: Math.max(0, Math.min(1, weight)) }));
    setSource('Manual');
  };

  const normalize = () => {
    if (!(targetSum > 0)) return;
    setTargets(t => Object.fromEntries(Object.entries(t).map(([k, w]) => [k, w / targetSum])));
  };

  const execute = async () => {
    setExecuting(true);
    const log: { label: string; ok: boolean; message?: string }[] = [];
    // Sells first, so their proceeds pay for the buys
    for (const t of trades) {
      const label = `${t.side === 'BUY' ? 'Buy' : 'Sell'} ${fmtQty(t.quantity)} ${t.asset.name}`;
      try {
        await assetService.recordTrade(t.asset.id, { side: t.side, quantity: t.quantity, price: t.price, date: todayLocal() });
        log.push({ label, ok: true });
      } catch (err) {
        log.push({ label, ok: false, message: err instanceof Error ? err.message : 'failed' });
      }
    }
    setExecLog(log);
    setExecuting(false);
    const failed = log.filter(l => !l.ok).length;
    onShowToast?.(failed ? 'error' : 'success',
      failed ? `${log.length - failed} of ${log.length} rebalancing trades executed; ${failed} failed.` : `Rebalanced with ${log.length} trades.`);
    onTraded?.();
    await load();
  };

  if (!loading && !error && assets.length === 0) return <PortfolioEmptyState onAddAsset={onAddAsset} />;

  return (
    <div className="space-y-4">
      {/* Optimizer controls */}
      <Panel title="Optimizer" subtitle="Generate target weights, then fine-tune them below">
        <div className="flex flex-wrap items-end gap-6">
          <label className="block w-64">
            <span className="flex justify-between text-xs text-term-muted">
              <Term term="Risk tolerance" />
              <span className="num text-term-text">{riskTolerance.toFixed(2)}</span>
            </span>
            <input type="range" min={0} max={1} step={0.05} value={riskTolerance} onChange={e => setRiskTolerance(parseFloat(e.target.value))}
                   className="mt-1 w-full" aria-label="Risk tolerance" />
            <span className="flex justify-between text-[10px] uppercase tracking-wider text-term-dim"><span>Min variance</span><span>Max return</span></span>
          </label>
          <div className="flex flex-wrap gap-2">
            <button type="button" className={btn.primary} disabled={!!optimizing} onClick={() => void optimize('MVO')}>
              {optimizing === 'MVO' ? 'Optimizing…' : 'Optimize (MVO)'}
            </button>
            <button type="button" className={btn.secondary} disabled={!!optimizing} onClick={() => void optimize('Black-Litterman')}>
              {optimizing === 'Black-Litterman' ? 'Optimizing…' : 'Black-Litterman'}
            </button>
            <button type="button" className={btn.ghost} aria-expanded={showViews} onClick={() => setShowViews(s => !s)}>
              {showViews ? 'Hide views' : `Market views${Object.keys(views).length ? ` (${Object.keys(views).length})` : ''}`}
            </button>
          </div>
          {result && (
            <div className="ml-auto grid grid-cols-2 gap-6 md:grid-cols-4">
              <Metric size="sm" label="Expected return" value={pct(result.expectedReturn, 2)} />
              <Metric size="sm" label="Current risk" term="Volatility" value={pct(result.currentRisk, 2)} />
              <Metric size="sm" label="Target risk" term="Volatility" value={pct(result.targetRisk, 2)} />
              <Metric size="sm" label="Est. costs" term="Drift" value={usd(result.totalTransactionCost)} />
            </div>
          )}
        </div>

        {showViews && (
          <div className="mt-4 border-t border-term-line pt-4" data-testid="views-panel">
            <p className="mb-3 text-xs text-term-muted">
              <Term term="Black-Litterman" />: tilt the market-cap starting point with your own conviction. Only assets you switch on carry a view.
            </p>
            <div className="grid grid-cols-1 gap-x-8 gap-y-2 md:grid-cols-2 2xl:grid-cols-3">
              {assets.map(a => {
                const on = a.name in views;
                return (
                  <div key={a.id} className="flex items-center gap-3">
                    <label className="flex w-40 shrink-0 items-center gap-2 text-sm text-term-text">
                      <input type="checkbox" checked={on}
                             onChange={e => setViews(v => { const next = { ...v }; if (e.target.checked) next[a.name] = 0.5; else delete next[a.name]; return next; })} />
                      {a.name}
                    </label>
                    <input type="range" min={0} max={1} step={0.1} disabled={!on} value={views[a.name] ?? 0.5} aria-label={`${a.name} view`}
                           onChange={e => setViews(v => ({ ...v, [a.name]: parseFloat(e.target.value) }))} className="flex-1 disabled:opacity-30" />
                    <span className={`num w-16 text-right text-xs ${!on ? 'text-term-dim' : (views[a.name] ?? 0.5) > 0.5 ? 'text-term-up' : (views[a.name] ?? 0.5) < 0.5 ? 'text-term-down' : 'text-term-muted'}`}>
                      {!on ? 'off' : (views[a.name] ?? 0.5) > 0.5 ? 'bullish' : (views[a.name] ?? 0.5) < 0.5 ? 'bearish' : 'neutral'}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </Panel>

      {error && <div className="rounded-lg border border-term-down/40 bg-term-down-soft px-4 py-3 text-sm text-term-down" role="alert">{error}</div>}

      {/* Target vs actual */}
      <Panel flush title="Target vs actual"
             subtitle={<>Target source: <span className="text-term-text">{source}</span> · Portfolio {usd(total)} · Max <Term term="Drift">drift</Term> {pct(maxDrift, 1)}</>}
             actions={<>
               <span className={`num text-xs ${sumOk ? 'text-term-muted' : 'text-term-warn'}`} data-testid="target-sum">Σ targets {pct(targetSum, 1)}</span>
               {!sumOk && <button type="button" className={btn.secondary} onClick={normalize}><Scale size={14} /> Normalize</button>}
               <button type="button" className={btn.ghost} onClick={() => { setTargets(currentWeights(assets)); setSource('Current'); setResult(null); }}>
                 <RotateCcw size={14} /> Reset
               </button>
               <button type="button" className={btn.ghost} onClick={() => void load()} aria-label="Reload positions"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /></button>
             </>}>
        {loading && assets.length === 0 ? <SkeletonRows rows={4} cols={6} /> : (
          <div className="overflow-auto">
            <table className="w-full min-w-[980px] text-sm" aria-label="Target versus actual weights">
              <thead className="text-[11px] uppercase tracking-wider text-term-dim">
                <tr className="border-b border-term-line">
                  <th scope="col" className="px-3 py-2 text-left font-medium">Asset</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">Actual</th>
                  <th scope="col" className="w-[34%] px-3 py-2 text-left font-medium">Target</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium"><Term term="Drift" /></th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">Value → target</th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">Suggested trade</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-term-line">
                {assets.map((a, i) => {
                  const actual = current[a.name] ?? 0;
                  const target = targets[a.name] ?? 0;
                  const drift = actual - target;
                  const trade = trades.find(t => t.asset.id === a.id);
                  const value = a.quantity * (a.currentPrice || a.pricePerUnit);
                  return (
                    <tr key={a.id} className="hover:bg-term-hover" data-testid={`target-row-${a.name}`}>
                      <td className="px-3 py-3">
                        <span className="flex items-center gap-2 font-medium text-term-text">
                          <span className="h-2 w-2 rounded-sm" style={{ background: SLICE_COLORS[i % SLICE_COLORS.length] }} />{a.name}
                        </span>
                      </td>
                      <td className="num px-3 py-3 text-right text-term-muted">{pct(actual, 1)}</td>
                      <td className="px-3 py-3">
                        <div className="flex items-center gap-3">
                          <div className="relative flex-1">
                            {/* Custom track: actual weight in grey, the move to target in its direction's colour */}
                            <div className="pointer-events-none absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 overflow-hidden rounded-full bg-term-line">
                              <div className="absolute inset-y-0 left-0 bg-term-dim" style={{ width: `${Math.min(actual, target) * 100}%` }} />
                              <div className={`absolute inset-y-0 ${target > actual ? 'bg-term-up/70' : 'bg-term-down/70'}`}
                                   style={{ left: `${Math.min(actual, target) * 100}%`, width: `${Math.abs(target - actual) * 100}%` }} />
                            </div>
                            <span className="pointer-events-none absolute top-1/2 h-3 w-0.5 -translate-y-1/2 bg-term-muted" style={{ left: `${actual * 100}%` }}
                                  title={`Actual ${pct(actual, 1)}`} aria-hidden="true" />
                            <input type="range" min={0} max={100} step={0.5} value={Math.round(target * 1000) / 10}
                                   onChange={e => setTarget(a.name, parseFloat(e.target.value) / 100)}
                                   aria-label={`${a.name} target weight`} className="range-clear relative w-full" />
                          </div>
                          <input type="number" min={0} max={100} step={0.1} value={Math.round(target * 1000) / 10}
                                 onChange={e => setTarget(a.name, (parseFloat(e.target.value) || 0) / 100)}
                                 aria-label={`${a.name} target percent`}
                                 className="num w-20 rounded border border-term-line2 bg-term-bg px-2 py-1 text-right text-sm text-term-text focus:border-term-accent focus:outline-none" />
                          <span className="text-xs text-term-dim">%</span>
                        </div>
                      </td>
                      <td className={`num px-3 py-3 text-right ${Math.abs(drift) < band ? 'text-term-muted' : drift > 0 ? 'text-term-warn' : 'text-term-accent'}`}>
                        {drift > 0 ? '+' : ''}{pct(drift, 1)}
                      </td>
                      <td className="num px-3 py-3 text-right text-term-muted">{usd(value, 0)} → <span className="text-term-text">{usd(target * total, 0)}</span></td>
                      <td className="px-3 py-3 text-right">
                        {trade ? (
                          <span className="inline-flex items-center gap-2">
                            <Badge tone={trade.side === 'BUY' ? 'up' : 'down'}>{trade.side}</Badge>
                            <span className="num text-term-text">{fmtQty(trade.quantity)}</span>
                            <span className="num text-xs text-term-muted">{usd(trade.value)}</span>
                          </span>
                        ) : <span className="text-xs text-term-dim">{sumOk ? 'within band' : '—'}</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {/* Execution */}
      <Panel title="Execution" subtitle="Paper trades at current market prices, recorded in your trade ledger">
        <div className="flex flex-wrap items-center gap-6">
          <label className="text-xs text-term-muted">
            Rebalance band
            <select value={band} onChange={e => setBand(parseFloat(e.target.value))}
                    className="num ml-2 rounded border border-term-line2 bg-term-bg px-2 py-1 text-sm text-term-text">
              {[0.005, 0.01, 0.02, 0.05].map(b => <option key={b} value={b}>±{pct(b, 1)}</option>)}
            </select>
          </label>
          <Metric size="sm" label="Trades" term="Drift" value={trades.length} />
          <Metric size="sm" label="Turnover" term="Drift" value={usd(turnover)} sub={total ? `${pct(turnover / total, 1)} of portfolio` : undefined} />
          <div className="ml-auto flex items-center gap-3">
            {!sumOk && <span className="text-xs text-term-warn">Targets must add up to 100% before trading.</span>}
            <button type="button" className={btn.primary} disabled={!trades.length || executing} onClick={() => setConfirming(true)}>
              {executing ? 'Executing…' : `Execute ${trades.length} trade${trades.length === 1 ? '' : 's'}`}
            </button>
          </div>
        </div>
        {execLog && (
          <ul className="mt-4 space-y-1 border-t border-term-line pt-3 text-sm" aria-label="Execution results">
            {execLog.map((l, i) => (
              <li key={i} className={l.ok ? 'text-term-up' : 'text-term-down'}>
                {l.ok ? '✓' : '✕'} {l.label}{l.message ? ` — ${l.message}` : ''}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {confirming && (
        <Modal title="Confirm rebalancing trades" labelledBy="rebalance-confirm" onClose={() => setConfirming(false)} width="max-w-lg">
          <p className="mb-3 text-sm text-term-muted">These paper trades execute at current market prices, sells first.</p>
          <table className="w-full text-sm" aria-label="Trades to execute">
            <tbody className="divide-y divide-term-line">
              {trades.map(t => (
                <tr key={t.asset.id}>
                  <td className="py-1.5"><Badge tone={t.side === 'BUY' ? 'up' : 'down'}>{t.side}</Badge></td>
                  <td className="py-1.5 text-term-text">{t.asset.name}</td>
                  <td className="num py-1.5 text-right text-term-text">{fmtQty(t.quantity)}</td>
                  <td className="num py-1.5 text-right text-term-muted">@ {usd(t.price)}</td>
                  <td className="num py-1.5 text-right text-term-text">{usd(t.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-4 flex justify-end gap-2">
            <button type="button" className={btn.ghost} onClick={() => setConfirming(false)}>Cancel</button>
            <button type="button" className={btn.primary} onClick={() => { setConfirming(false); void execute(); }}>Execute trades</button>
          </div>
        </Modal>
      )}
    </div>
  );
};

function currentWeights(assets: AssetWithPrice[]): Record<string, number> {
  const total = assets.reduce((s, a) => s + a.quantity * (a.currentPrice || a.pricePerUnit), 0);
  return Object.fromEntries(assets.map(a => [a.name, total > 0 ? (a.quantity * (a.currentPrice || a.pricePerUnit)) / total : 0]));
}

export default PortfolioRebalancingDashboard;
