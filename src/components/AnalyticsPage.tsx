import React, { useEffect, useMemo, useState } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { RefreshCw } from 'lucide-react';
import { assetService } from '../services/api';
import { PortfolioAnalytics } from '../types';
import PortfolioEmptyState from './PortfolioEmptyState';
import { SLICE_COLORS } from './PortfolioPage';
import Term from './ui/Term';
import { Delta, Metric, Panel, ShareBar, SkeletonMetrics, SkeletonRows, btn } from './ui/kit';
import { num, pct, qty, toneOf, usd } from './ui/format';

interface AnalyticsPageProps {
  /** Opens the add-asset flow; shown when the portfolio is empty. */
  onAddAsset: () => void;
  /** Changes after every trade, so the figures reload on the new positions. */
  refreshKey?: number;
}

/** Risk-adjusted performance, diversification and per-asset attribution, all computed by the backend. */
const AnalyticsPage: React.FC<AnalyticsPageProps> = ({ onAddAsset, refreshKey = 0 }) => {
  const [data, setData] = useState<PortfolioAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      setData(await assetService.getAllAnalytics());
    } catch (err) {
      setError('Failed to load analytics data from backend');
      console.error('Analytics error:', err);
    } finally {
      setLoading(false);
    }
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps -- reload after trades
  useEffect(() => { void load(); }, [refreshKey]);

  const rows = useMemo(() => {
    if (!data) return [];
    const total = data.totalValue || 0;
    const totalCost = data.totalInitialInvestment || 0;
    return [...(data.assetBreakdown ?? [])]
      .map(a => {
        const value = a.currentValue || 0;
        const cost = a.initialInvestment || 0;
        return { ...a, value, cost, pnl: value - cost, roi: cost > 0 ? value / cost - 1 : null,
                 weight: total > 0 ? value / total : 0, contribution: totalCost > 0 ? (value - cost) / totalCost : null };
      })
      .sort((a, b) => b.value - a.value);
  }, [data]);

  if (loading && !data) {
    return (
      <div className="space-y-4">
        <SkeletonMetrics count={8} />
        <Panel title="Attribution" flush><SkeletonRows rows={5} cols={7} /></Panel>
      </div>
    );
  }
  if (error && !data) {
    return (
      <div className="rounded-lg border border-term-down/40 bg-term-panel p-8 text-center" role="alert">
        <p className="text-sm text-term-down">{error}</p>
        <button type="button" className={`${btn.secondary} mt-3`} onClick={() => void load()}>Try again</button>
      </div>
    );
  }
  if (!data || data.totalAssets === 0 || data.riskMetricsCode === 'NO_ASSETS') return <PortfolioEmptyState onAddAsset={onAddAsset} />;

  const risk = data.riskMetricsAvailable;
  const pnl = data.totalValue - data.totalInitialInvestment;
  const roi = data.totalInitialInvestment > 0 ? pnl / data.totalInitialInvestment : null;
  const divScore = data.diversificationScore ?? null;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-term-line bg-term-line md:grid-cols-4 2xl:grid-cols-8" data-testid="analytics-scorecard">
        <div className="bg-term-panel p-4"><Metric label="Holdings value" term="Allocation" value={usd(data.totalValue)} sub={`${data.totalAssets} positions`} /></div>
        <div className="bg-term-panel p-4"><Metric label="Cost basis" term="Avg cost" value={usd(data.totalInitialInvestment)} /></div>
        <div className="bg-term-panel p-4"><Metric label="Unrealized P&L" value={usd(pnl)} tone={toneOf(pnl, 0.005)} sub={pct(roi, 2, true)} /></div>
        <div className="bg-term-panel p-4"><Metric label="Annualized return" term="Expected return" value={risk ? pct(data.annualizedReturn, 1, true) : '—'}
                                                    tone={risk ? toneOf(data.annualizedReturn) : undefined} /></div>
        <div className="bg-term-panel p-4"><Metric label="Sharpe" value={risk ? num(data.sharpeRatio, 2) : '—'}
                                                    tone={risk ? toneOf(data.sharpeRatio) : undefined} /></div>
        <div className="bg-term-panel p-4"><Metric label="Volatility" value={risk ? pct(data.volatility, 1) : '—'} sub="annualized" /></div>
        <div className="bg-term-panel p-4"><Metric label="Beta" value={risk ? num(data.beta, 2) : '—'} sub={data.benchmark ? `vs ${data.benchmark}` : undefined} /></div>
        <div className="bg-term-panel p-4"><Metric label="Diversification" value={divScore != null && risk ? `${num(divScore, 0)}/100` : '—'}
                                                    tone={divScore == null || !risk ? undefined : divScore >= 60 ? 'up' : divScore < 35 ? 'down' : 'flat'} /></div>
      </div>

      {!risk && data.riskMetricsMessage && (
        <div className="rounded-lg border border-term-warn/40 bg-term-warn-soft px-4 py-3 text-sm text-term-warn" role="status">
          Risk metrics unavailable: {data.riskMetricsMessage}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Panel title="Allocation" className="xl:col-span-1">
          <div className="flex items-center gap-4">
            <div className="h-44 w-44 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={rows} dataKey="value" nameKey="name" innerRadius="62%" outerRadius="100%" stroke="#0E131F" strokeWidth={2} isAnimationActive={false}>
                    {rows.map((r, i) => <Cell key={r.id} fill={SLICE_COLORS[i % SLICE_COLORS.length]} />)}
                  </Pie>
                  <Tooltip contentStyle={{ background: '#141B2A', border: '1px solid #2A3447', borderRadius: 6, fontSize: 12 }}
                           formatter={(v: number, n: string) => [usd(v), n]} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <ul className="min-w-0 flex-1 space-y-1.5 text-sm">
              {rows.map((r, i) => (
                <li key={r.id} className="flex items-center justify-between gap-2">
                  <span className="flex min-w-0 items-center gap-2 truncate text-term-muted">
                    <span className="h-2 w-2 shrink-0 rounded-sm" style={{ background: SLICE_COLORS[i % SLICE_COLORS.length] }} />{r.name}
                  </span>
                  <span className="num text-term-text">{pct(r.weight, 1)}</span>
                </li>
              ))}
            </ul>
          </div>
        </Panel>

        <Panel title="Diversification" className="xl:col-span-2"
               subtitle="Concentration and correlation of current holdings">
          {risk ? (
            <div className="space-y-4">
              <div>
                <div className="mb-1 flex justify-between text-xs text-term-muted">
                  <Term term="Diversification">Diversification score</Term>
                  <span className="num text-term-text">{num(divScore, 1)} / 100</span>
                </div>
                <ShareBar value={(divScore ?? 0) / 100} tone={(divScore ?? 0) >= 60 ? 'up' : (divScore ?? 0) < 35 ? 'down' : 'accent'} className="h-1.5" />
              </div>
              <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
                <Metric label="HHI" value={num(data.herfindahlIndex, 3)} size="sm" sub="Σ w²" />
                <Metric label="Effective holdings" term="N_eff" value={num(data.effectiveNumberOfAssets, 2)} size="sm" sub={`of ${data.totalAssets}`} />
                <Metric label="Avg correlation" term="ρ̄" value={num(data.averageCorrelation, 2)} size="sm"
                        tone={data.averageCorrelation == null ? undefined : data.averageCorrelation >= 0.7 ? 'down' : data.averageCorrelation <= 0.3 ? 'up' : 'flat'} />
                <Metric label="Independent bets" value={num(data.effectiveIndependentBets, 2)} size="sm" sub="correlation-adjusted" />
                <Metric label="Diversification ratio" term="Independent bets" value={num(data.diversificationRatio, 2)} size="sm" sub="Σwσ / σₚ" />
              </div>
            </div>
          ) : <p className="text-sm text-term-muted">Needs price history for your holdings.</p>}
        </Panel>
      </div>

      <Panel flush title="Attribution" subtitle="Each position’s share of value and of total unrealized P&L"
             actions={<button type="button" className={btn.secondary} onClick={() => void load()} aria-label="Refresh analytics">
               <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
             </button>}>
        <div className="max-h-[60vh] overflow-auto">
          <table className="w-full min-w-[900px] text-sm" aria-label="Attribution">
            <thead className="text-[11px] uppercase tracking-wider text-term-dim">
              <tr className="border-b border-term-line">
                {['Asset', 'Qty', 'Avg cost', 'Price', 'Value', 'Weight', 'P&L', 'ROI', 'Contribution'].map((h, i) => (
                  <th key={h} scope="col" className={`sticky top-0 z-10 bg-term-panel px-3 py-2 font-medium ${i === 0 ? 'text-left' : 'text-right'}`}>
                    {h === 'Avg cost' ? <Term term="Avg cost" /> : h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-term-line">
              {rows.map((r, i) => (
                <tr key={r.id} className="hover:bg-term-hover">
                  <td className="px-3 py-2.5">
                    <span className="flex items-center gap-2 font-medium text-term-text">
                      <span className="h-2 w-2 rounded-sm" style={{ background: SLICE_COLORS[i % SLICE_COLORS.length] }} />{r.name}
                    </span>
                  </td>
                  <td className="num px-3 py-2.5 text-right text-term-muted">{qty(r.quantity)}</td>
                  <td className="num px-3 py-2.5 text-right text-term-muted">{usd(r.purchasePrice)}</td>
                  <td className="num px-3 py-2.5 text-right text-term-text">{usd(r.currentPrice)}</td>
                  <td className="num px-3 py-2.5 text-right text-term-text">{usd(r.value)}</td>
                  <td className="px-3 py-2.5"><div className="ml-auto w-24"><div className="num text-right text-xs">{pct(r.weight, 1)}</div><ShareBar value={r.weight} className="mt-1" /></div></td>
                  <td className="px-3 py-2.5 text-right"><Delta value={r.pnl} format={v => usd(v)} /></td>
                  <td className="px-3 py-2.5 text-right"><Delta value={r.roi} format={v => pct(v, 2, true)} /></td>
                  <td className="px-3 py-2.5 text-right"><Delta value={r.contribution} format={v => pct(v, 2, true)} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>

      {risk && (
        <p className="text-xs text-term-dim">
          Based on {data.observations ?? '—'} daily returns{data.historyStart ? ` (${data.historyStart} → ${data.historyEnd})` : ''},
          risk-free rate {pct(data.riskFreeRate, 1)}, benchmark {data.benchmark ?? '—'}. Weights are today’s; history is market prices, not your trades.
        </p>
      )}
    </div>
  );
};

export default AnalyticsPage;
