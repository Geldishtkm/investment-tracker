import React, { useMemo, useState } from 'react';
import { MoreHorizontal, Plus, RefreshCw } from 'lucide-react';
import { Account, AssetWithPrice, TradeRequest } from '../types';
import { assetService } from '../services/api';
import { findCoin, useMarketData } from '../hooks/useMarketData';
import TradeModal from './TradeModal';
import AssetActionSheet from './AssetActionSheet';
import PortfolioEmptyState, { AssetsLoadError } from './PortfolioEmptyState';
import Term from './ui/Term';
import { CoinIcon, Delta, Metric, Modal, Panel, ShareBar, SkeletonMetrics, SkeletonRows, Sparkline, btn, input } from './ui/kit';
import { pct, pctPoints, qty, toneOf, usd } from './ui/format';

/** Palette for allocation slices: steady, distinguishable, not neon. */
export const SLICE_COLORS = ['#7C83FF', '#4FD1A5', '#4CC9F0', '#D4B062', '#B58CFF', '#F2937A', '#8A9BB8', '#E879A6', '#8FD16B', '#5EA8FF'];

export interface HoldingRow {
  asset: AssetWithPrice;
  price: number;
  value: number;
  cost: number;
  pnl: number;
  pnlPct: number | null;
  weight: number;
  change24h: number | null;
  spark: number[] | null;
  image?: string;
  symbol?: string;
}

type SortKey = 'value' | 'pnl' | 'change24h' | 'weight' | 'name';

interface Props {
  assets: AssetWithPrice[];
  account: Account | null;
  loading: boolean;
  loadError: string | null;
  onRetry: () => void;
  /** Opens the market grid to buy a new coin. */
  onAddAsset: () => void;
  /** Reload holdings and cash after a change. */
  onChanged: () => void;
  onDelete: (id: number, name: string) => Promise<void> | void;
  onShowToast: (type: 'success' | 'error' | 'info', message: string) => void;
}

/** Every ~4th hourly point: 42 points over 7 days is plenty for a sparkline. */
function thin(series: number[] | undefined | null): number[] | null {
  if (!series || series.length < 2) return null;
  return series.filter((_, i) => i % 4 === 0 || i === series.length - 1);
}

const PortfolioPage: React.FC<Props> = ({ assets, account, loading, loadError, onRetry, onAddAsset, onChanged, onDelete, onShowToast }) => {
  const market = useMarketData();
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'value', desc: true });
  const [ticket, setTicket] = useState<{ asset: AssetWithPrice; price: number; side: TradeRequest['side'] } | null>(null);
  const [editing, setEditing] = useState<AssetWithPrice | null>(null);
  const [menuFor, setMenuFor] = useState<number | null>(null);
  const [actionFor, setActionFor] = useState<HoldingRow | null>(null);

  const rows = useMemo<HoldingRow[]>(() => {
    const priced = assets.map(asset => {
      const coin = findCoin(market.coins, asset.name);
      const price = asset.currentPrice || asset.pricePerUnit || coin?.current_price || 0;
      const value = asset.quantity * price;
      const avg = asset.purchasePricePerUnit || asset.pricePerUnit;
      const cost = asset.quantity * avg;
      return {
        asset, price, value, cost, pnl: value - cost, pnlPct: cost > 0 ? value / cost - 1 : null, weight: 0,
        change24h: coin?.price_change_percentage_24h ?? null, spark: thin(coin?.sparkline_in_7d?.price),
        image: coin?.image, symbol: coin?.symbol?.toUpperCase(),
      };
    });
    const total = priced.reduce((s, r) => s + r.value, 0);
    priced.forEach(r => { r.weight = total > 0 ? r.value / total : 0; });
    const dir = sort.desc ? -1 : 1;
    return priced.sort((a, b) => {
      if (sort.key === 'name') return dir * a.asset.name.localeCompare(b.asset.name);
      return dir * ((a[sort.key] ?? -Infinity) - (b[sort.key] ?? -Infinity));
    });
  }, [assets, market.coins, sort]);

  const holdingsValue = rows.reduce((s, r) => s + r.value, 0);
  const costBasis = rows.reduce((s, r) => s + r.cost, 0);
  const unrealized = holdingsValue - costBasis;
  const cash = account?.cash ?? null;
  const s0 = account?.initialBankroll ?? null;
  const equity = cash != null ? cash + holdingsValue : null;
  // Realized P&L from closed trades: with S₀, cash = S₀ − buys + sells, so cash + open cost basis − S₀ is
  // exactly what the sells made over the average cost of the units sold
  const realized = cash != null && s0 != null ? cash + costBasis - s0 : null;
  const change24h = rows.reduce((s, r) => s + (r.change24h == null ? 0 : r.value - r.value / (1 + r.change24h / 100)), 0);
  const has24h = rows.some(r => r.change24h != null);

  const header = (key: SortKey, label: React.ReactNode, align = 'text-right') => (
    <th scope="col" className={`sticky top-0 z-10 bg-term-panel px-3 py-2 font-medium ${align}`}
        aria-sort={sort.key === key ? (sort.desc ? 'descending' : 'ascending') : 'none'}>
      <button type="button" className="inline-flex items-center gap-1 uppercase hover:text-term-text"
              onClick={() => setSort(s => ({ key, desc: s.key === key ? !s.desc : true }))}>
        {label}{sort.key === key && <span aria-hidden="true">{sort.desc ? '↓' : '↑'}</span>}
      </button>
    </th>
  );

  const remove = async (row: HoldingRow) => {
    setMenuFor(null);
    // Removing is for holdings added by mistake; a real exit is a sell, which keeps the trade history
    if (!window.confirm(`Remove ${row.asset.name} and its trade history? To record that you sold it, use Sell instead.`)) return;
    await onDelete(row.asset.id, row.asset.name);
  };

  return (
    <div className="space-y-4">
      {/* Account strip */}
      {loading && assets.length === 0 ? <SkeletonMetrics count={6} /> : (
        <div className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-term-line bg-term-line sm:grid-cols-4 2xl:grid-cols-7"
             data-testid="account-strip">
          <div className="bg-term-panel p-4"><Metric label="Equity" value={usd(equity)} sub={s0 != null ? <>vs <Term term="S₀">S₀</Term> {usd(s0, 0)}</> : undefined} size="lg" /></div>
          <div className="bg-term-panel p-4"><Metric label="Return on S₀" term="S₀" value={equity != null && s0 ? pct(equity / s0 - 1, 2, true) : '—'}
                                                      tone={equity != null && s0 ? toneOf(equity - s0) : undefined} size="lg" /></div>
          <div className="bg-term-panel p-4"><Metric label="Cash" term="Equity" value={usd(cash)}
                                                      sub={equity ? `${pct((cash ?? 0) / equity, 1)} of equity` : undefined} size="lg" /></div>
          <div className="bg-term-panel p-4"><Metric label="Holdings" term="Allocation" value={usd(holdingsValue)} sub={`${rows.length} position${rows.length === 1 ? '' : 's'}`} size="lg" /></div>
          <div className="bg-term-panel p-4"><Metric label="Unrealized P&L" value={usd(unrealized)} tone={toneOf(unrealized, 0.005)}
                                                      sub={costBasis > 0 ? pct(unrealized / costBasis, 2, true) : undefined} size="lg" /></div>
          <div className="bg-term-panel p-4" data-testid="realized-pnl"><Metric label="Realized P&L" term="Unrealized P&L" value={usd(realized)}
                                                      tone={realized == null ? undefined : toneOf(realized, 0.005)} sub="closed trades, at average cost" size="lg" /></div>
          <div className="bg-term-panel p-4"><Metric label="24h change" term="Unrealized P&L" value={has24h ? usd(change24h) : '—'}
                                                      tone={has24h ? toneOf(change24h, 0.005) : undefined}
                                                      sub={has24h && holdingsValue > 0 ? pct(change24h / (holdingsValue - change24h), 2, true) : undefined} size="lg" /></div>
        </div>
      )}

      {loadError ? <AssetsLoadError message={loadError} onRetry={onRetry} /> :
        !loading && assets.length === 0 ? <PortfolioEmptyState onAddAsset={onAddAsset} /> : (
        <Panel flush title="Positions" subtitle={market.error ? 'Market data unavailable: 24h change and trends hidden.' : undefined}
               actions={<>
                 <button type="button" className={btn.secondary} onClick={onChanged} aria-label="Refresh positions">
                   <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
                 </button>
                 <button type="button" className={btn.primary} onClick={onAddAsset}><Plus size={14} /> New position</button>
               </>}>
          {/* Allocation */}
          {rows.length > 0 && (
            <div className="border-b border-term-line px-4 py-3">
              <div className="flex h-2 w-full overflow-hidden rounded-sm bg-term-line" role="img"
                   aria-label={`Allocation: ${rows.map(r => `${r.asset.name} ${pct(r.weight, 1)}`).join(', ')}`}>
                {rows.map((r, i) => (
                  <div key={r.asset.id} style={{ width: `${r.weight * 100}%`, background: SLICE_COLORS[i % SLICE_COLORS.length] }}
                       title={`${r.asset.name} ${pct(r.weight, 1)}`} />
                ))}
              </div>
              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-term-muted">
                {rows.map((r, i) => (
                  <span key={r.asset.id} className="inline-flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-sm" style={{ background: SLICE_COLORS[i % SLICE_COLORS.length] }} />
                    {r.asset.name} <span className="num text-term-text">{pct(r.weight, 1)}</span>
                  </span>
                ))}
              </div>
            </div>
          )}

          {loading && assets.length === 0 ? <SkeletonRows rows={5} cols={8} /> : (
            <div className="max-h-[70vh] overflow-auto">
              <table className="w-full min-w-[1080px] text-sm" aria-label="Positions">
                <thead className="text-[11px] tracking-wider text-term-dim">
                  <tr className="border-b border-term-line">
                    {header('name', 'Asset', 'text-left')}
                    <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-right font-medium uppercase">Qty</th>
                    <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-right font-medium uppercase"><Term term="Avg cost" /></th>
                    <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-right font-medium uppercase">Price</th>
                    {header('change24h', '24h')}
                    <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-center font-medium uppercase">7d</th>
                    {header('value', 'Value')}
                    {header('weight', 'Allocation')}
                    {header('pnl', 'Unrealized P&L')}
                    <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-right font-medium uppercase">Trade</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-term-line">
                  {rows.map((r, i) => (
                    <tr key={r.asset.id} className="group cursor-pointer transition-colors duration-100 hover:bg-white/[0.03] focus-visible:bg-white/[0.04] focus-visible:outline-none"
                        data-testid={`position-${r.asset.name}`} tabIndex={0} title={`Trade ${r.asset.name}`}
                        onClick={() => setActionFor(r)}
                        onKeyDown={e => { if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); setActionFor(r); } }}>
                      <td className="px-3 py-2.5">
                        <div className="flex items-center gap-2.5">
                          <span className="h-6 w-1 rounded-sm" style={{ background: SLICE_COLORS[i % SLICE_COLORS.length] }} aria-hidden="true" />
                          <CoinIcon src={r.image} name={r.asset.name} />
                          <div className="min-w-0">
                            <div className="truncate font-medium text-term-text">{r.asset.name}</div>
                            {r.symbol && <div className="text-[11px] text-term-dim">{r.symbol}</div>}
                          </div>
                        </div>
                      </td>
                      <td className="num px-3 py-2.5 text-right text-term-text">{qty(r.asset.quantity)}</td>
                      <td className="num px-3 py-2.5 text-right text-term-muted">{usd(r.asset.purchasePricePerUnit || r.asset.pricePerUnit)}</td>
                      <td className="num px-3 py-2.5 text-right text-term-text">{usd(r.price)}</td>
                      <td className="px-3 py-2.5 text-right"><Delta value={r.change24h} format={v => pctPoints(v)} /></td>
                      <td className="px-3 py-2.5"><div className="flex justify-center"><Sparkline data={r.spark} label={`${r.asset.name} 7-day trend`} /></div></td>
                      <td className="num px-3 py-2.5 text-right font-medium text-term-text">{usd(r.value)}</td>
                      <td className="px-3 py-2.5">
                        <div className="ml-auto w-28">
                          <div className="num text-right text-xs text-term-text">{pct(r.weight, 1)}</div>
                          <ShareBar value={r.weight} className="mt-1" />
                        </div>
                      </td>
                      <td className="px-3 py-2.5 text-right">
                        <div className={`num ${toneOf(r.pnl, 0.005) === 'up' ? 'text-term-up' : toneOf(r.pnl, 0.005) === 'down' ? 'text-term-down' : 'text-term-muted'}`}>{usd(r.pnl)}</div>
                        <div className="text-xs"><Delta value={r.pnlPct} format={v => pct(v, 2, true)} /></div>
                      </td>
                      <td className="px-3 py-2.5">
                        {/* Buttons act on their own, not as a row click */}
                        <div className="relative flex items-center justify-end gap-1.5" onClick={e => e.stopPropagation()} onKeyDown={e => e.stopPropagation()}>
                          <button type="button" className={btn.buy} aria-label={`Buy ${r.asset.name}`}
                                  onClick={() => setTicket({ asset: r.asset, price: r.price, side: 'BUY' })}>Buy</button>
                          <button type="button" className={btn.sell} aria-label={`Sell ${r.asset.name}`}
                                  onClick={() => setTicket({ asset: r.asset, price: r.price, side: 'SELL' })}>Sell</button>
                          <button type="button" className="rounded p-1 text-term-dim hover:bg-term-raised hover:text-term-text"
                                  aria-label={`More actions for ${r.asset.name}`} aria-expanded={menuFor === r.asset.id}
                                  onClick={() => setMenuFor(m => (m === r.asset.id ? null : r.asset.id))}>
                            <MoreHorizontal size={16} />
                          </button>
                          {menuFor === r.asset.id && (
                            <div role="menu" className="absolute right-0 top-8 z-20 w-44 overflow-hidden rounded-md border border-term-line2 bg-term-raised py-1 text-left shadow-xl shadow-black/50">
                              <button role="menuitem" type="button" className="block w-full px-3 py-1.5 text-left text-sm text-term-text hover:bg-term-hover"
                                      onClick={() => { setEditing(r.asset); setMenuFor(null); }}>Correct opening buy</button>
                              <button role="menuitem" type="button" className="block w-full px-3 py-1.5 text-left text-sm text-term-down hover:bg-term-hover"
                                      onClick={() => void remove(r)}>Remove position</button>
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
                {rows.length > 1 && (
                  <tfoot className="border-t border-term-line2 text-sm">
                    <tr>
                      <td className="px-3 py-2.5 text-xs font-semibold uppercase tracking-wider text-term-dim" colSpan={6}>Total</td>
                      <td className="num px-3 py-2.5 text-right font-semibold text-term-text">{usd(holdingsValue)}</td>
                      <td className="num px-3 py-2.5 text-right text-xs text-term-muted">100.0%</td>
                      <td className="px-3 py-2.5 text-right"><Delta value={unrealized} format={v => usd(v)} /></td>
                      <td />
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          )}
        </Panel>
      )}

      {actionFor && (
        <AssetActionSheet asset={actionFor.asset} price={actionFor.price} cash={cash}
                          onClose={() => setActionFor(null)}
                          onSaved={message => { setActionFor(null); onShowToast('success', message); onChanged(); }} />
      )}
      {ticket && (
        <TradeModal asset={ticket.asset} currentPrice={ticket.price} initialSide={ticket.side} cash={cash}
                    onClose={() => setTicket(null)}
                    onSaved={message => { setTicket(null); onShowToast('success', message); onChanged(); }} />
      )}
      {editing && (
        <EditHoldingModal asset={editing} onClose={() => setEditing(null)}
                          onSaved={() => { setEditing(null); onShowToast('success', `${editing.name} corrected.`); onChanged(); }} />
      )}
    </div>
  );
};

/** Corrects a holding's opening buy (a typo fix); holdings with more trades change through Buy and Sell. */
const EditHoldingModal: React.FC<{ asset: AssetWithPrice; onClose: () => void; onSaved: () => void }> = ({ asset, onClose, onSaved }) => {
  const [name, setName] = useState(asset.name);
  const [quantity, setQuantity] = useState(String(asset.quantity));
  const [price, setPrice] = useState(String(asset.purchasePricePerUnit || asset.pricePerUnit));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const q = parseFloat(quantity), p = parseFloat(price);
    if (!name.trim()) return setError('Asset name is required.');
    if (!(q > 0)) return setError('Quantity must be greater than 0.');
    if (!(p > 0)) return setError('Purchase price must be greater than 0.');
    setSaving(true);
    setError(null);
    try {
      await assetService.updateAsset(asset.id, {
        name: name.trim(), quantity: q, pricePerUnit: asset.pricePerUnit, purchasePricePerUnit: p, initialInvestment: q * p,
      });
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update the position');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`Correct ${asset.name}`} labelledBy="edit-title" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-3">
        <p className="text-xs text-term-muted">Fixes a typo in the opening buy. Once a position has other trades, use Buy or Sell.</p>
        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">Asset
          <input value={name} onChange={e => setName(e.target.value)} className={`mt-1 ${input}`} />
        </label>
        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">Quantity
          <input type="number" step="any" min="0" value={quantity} onChange={e => setQuantity(e.target.value)} className={`num mt-1 ${input}`} />
        </label>
        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">Purchase price per unit
          <input type="number" step="any" min="0" value={price} onChange={e => setPrice(e.target.value)} className={`num mt-1 ${input}`} />
        </label>
        {error && <p role="alert" className="text-sm text-term-down">{error}</p>}
        <div className="flex justify-end gap-2">
          <button type="button" className={btn.ghost} onClick={onClose}>Cancel</button>
          <button type="submit" className={btn.primary} disabled={saving}>{saving ? 'Saving…' : 'Save correction'}</button>
        </div>
      </form>
    </Modal>
  );
};

export default PortfolioPage;
