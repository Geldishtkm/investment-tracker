import React, { useMemo, useState } from 'react';
import { LineChart, RefreshCw, Search } from 'lucide-react';
import { Coin } from '../types';
import { NewAsset } from '../services/api';
import { useMarketData } from '../hooks/useMarketData';
import { todayLocal } from '../utils/dates';
import PriceHistoryChart from './PriceHistoryChart';
import Term from './ui/Term';
import { Badge, Delta, Modal, Panel, ShareBar, SkeletonRows, Sparkline, btn, input } from './ui/kit';
import { compactUsd, pct, pctPoints, qty as fmtQty, usd } from './ui/format';

type SortKey = 'rank' | 'price' | 'change24h' | 'change7d' | 'marketCap' | 'volume' | 'turnover';

interface CoinsPageProps {
  /** Cash available, for the order ticket. */
  cash: number | null;
  /** Buys a new position; rejects with the server's reason (insufficient cash, price check, ...). */
  onBuy: (asset: NewAsset) => Promise<void>;
  /** Names of current holdings, to mark coins already held. */
  heldNames?: string[];
}

const sortValue = (c: Coin, key: SortKey): number => {
  switch (key) {
    case 'rank': return -(c.market_cap_rank ?? 9999);
    case 'price': return c.current_price ?? 0;
    case 'change24h': return c.price_change_percentage_24h ?? -Infinity;
    case 'change7d': return c.price_change_percentage_7d_in_currency ?? -Infinity;
    case 'marketCap': return c.market_cap ?? 0;
    case 'volume': return c.total_volume ?? 0;
    case 'turnover': return c.market_cap ? (c.total_volume ?? 0) / c.market_cap : 0;
  }
};

/** Market grid: the top coins by market cap with trends, volume, and a quick-buy ticket. */
const CoinsPage: React.FC<CoinsPageProps> = ({ cash, onBuy, heldNames = [] }) => {
  const { coins, loading, error, asOf, refresh } = useMarketData();
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'rank', desc: true });
  const [buying, setBuying] = useState<Coin | null>(null);
  const [charting, setCharting] = useState<Coin | null>(null);

  const held = useMemo(() => new Set(heldNames.map(n => n.trim().toLowerCase())), [heldNames]);
  const isHeld = (c: Coin) => held.has(c.name.toLowerCase()) || held.has(c.id) || held.has(c.symbol.toLowerCase());

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = q ? coins.filter(c => c.name.toLowerCase().includes(q) || c.symbol.toLowerCase().includes(q) || c.id.includes(q)) : coins;
    const dir = sort.desc ? -1 : 1;
    return [...filtered].sort((a, b) => dir * (sortValue(a, sort.key) - sortValue(b, sort.key)));
  }, [coins, search, sort]);

  const maxVolume = Math.max(1, ...coins.map(c => c.total_volume ?? 0));
  const totalCap = coins.reduce((s, c) => s + (c.market_cap ?? 0), 0);
  const totalVolume = coins.reduce((s, c) => s + (c.total_volume ?? 0), 0);
  const advancers = coins.filter(c => (c.price_change_percentage_24h ?? 0) > 0).length;
  const btc = coins.find(c => c.id === 'bitcoin');

  const th = (key: SortKey | null, label: React.ReactNode, align = 'text-right') => (
    <th scope="col" className={`sticky top-0 z-10 bg-term-panel px-3 py-2 font-medium ${align}`}
        aria-sort={key && sort.key === key ? (sort.desc ? 'descending' : 'ascending') : undefined}>
      {key ? (
        <button type="button" className="inline-flex items-center gap-1 uppercase hover:text-term-text"
                onClick={() => setSort(s => ({ key, desc: s.key === key ? !s.desc : true }))}>
          {label}{sort.key === key && <span aria-hidden="true">{sort.desc ? '↓' : '↑'}</span>}
        </button>
      ) : <span className="uppercase">{label}</span>}
    </th>
  );

  return (
    <div className="space-y-4">
      {/* Market tape */}
      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-term-line bg-term-line md:grid-cols-4" data-testid="market-tape">
        {[
          ['Top-100 market cap', compactUsd(totalCap), 'Market cap'],
          ['24h volume', compactUsd(totalVolume), 'Volume 24h'],
          ['BTC dominance', totalCap && btc ? pct(btc.market_cap / totalCap, 1) : '—', 'Market cap'],
          ['Advancers / decliners', coins.length ? `${advancers} / ${coins.length - advancers}` : '—', ''],
        ].map(([label, value, term]) => (
          <div key={label} className="bg-term-panel px-4 py-3">
            <div className="text-[11px] font-medium uppercase tracking-[0.1em] text-term-dim"><Term term={term}>{label}</Term></div>
            <div className="num mt-1 text-lg text-term-text">{loading && !coins.length ? '…' : value}</div>
          </div>
        ))}
      </div>

      <Panel flush title="Top 100 by market cap"
             subtitle={asOf ? `Updated ${new Date(asOf).toLocaleTimeString()}` : undefined}
             actions={<>
               <div className="relative">
                 <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-term-dim" />
                 <input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search coins"
                        aria-label="Search coins" className={`${input} w-56 py-1.5 pl-8`} />
               </div>
               <button type="button" className={btn.secondary} onClick={() => void refresh(true)} aria-label="Refresh market data">
                 <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
               </button>
             </>}>
        {error && !coins.length ? (
          <div className="p-8 text-center" role="alert">
            <p className="text-sm text-term-down">{error}</p>
            <button type="button" className={`${btn.secondary} mt-3`} onClick={() => void refresh(true)}>Try again</button>
          </div>
        ) : loading && !coins.length ? <SkeletonRows rows={12} cols={8} /> : (
          <div className="max-h-[72vh] overflow-auto">
            <table className="w-full min-w-[1100px] text-sm" aria-label="Markets">
              <thead className="text-[11px] tracking-wider text-term-dim">
                <tr className="border-b border-term-line">
                  {th('rank', '#', 'text-left')}
                  {th(null, 'Coin', 'text-left')}
                  {th('price', 'Price')}
                  {th('change24h', '24h')}
                  {th('change7d', '7d')}
                  <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-center font-medium uppercase">7d trend</th>
                  {th('marketCap', 'Market cap')}
                  {th('volume', 'Volume 24h')}
                  {th('turnover', 'Vol / Cap')}
                  <th scope="col" className="sticky top-0 z-10 bg-term-panel px-3 py-2 text-right font-medium uppercase">Trade</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-term-line">
                {rows.length === 0 && (
                  <tr><td colSpan={10} className="px-3 py-8 text-center text-sm text-term-muted">No coins match “{search}”.</td></tr>
                )}
                {rows.map(c => {
                  const turnover = c.market_cap ? (c.total_volume ?? 0) / c.market_cap : null;
                  return (
                    <tr key={c.id} className="hover:bg-term-hover" data-testid={`coin-${c.id}`}>
                      <td className="num px-3 py-2 text-left text-term-dim">{c.market_cap_rank ?? '—'}</td>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2.5">
                          <img src={c.image} alt="" className="h-5 w-5 rounded-full" loading="lazy" />
                          <span className="font-medium text-term-text">{c.name}</span>
                          <span className="text-xs uppercase text-term-dim">{c.symbol}</span>
                          {isHeld(c) && <Badge tone="accent">Held</Badge>}
                        </div>
                      </td>
                      <td className="num px-3 py-2 text-right text-term-text">{usd(c.current_price)}</td>
                      <td className="px-3 py-2 text-right"><Delta value={c.price_change_percentage_24h} format={v => pctPoints(v)} /></td>
                      <td className="px-3 py-2 text-right"><Delta value={c.price_change_percentage_7d_in_currency} format={v => pctPoints(v)} /></td>
                      <td className="px-3 py-1"><div className="flex justify-center">
                        <Sparkline data={c.sparkline_in_7d?.price?.filter((_, i) => i % 4 === 0)} width={110} label={`${c.name} 7-day trend`} />
                      </div></td>
                      <td className="num px-3 py-2 text-right text-term-text">{compactUsd(c.market_cap)}</td>
                      <td className="px-3 py-2">
                        <div className="ml-auto w-32">
                          <div className="num text-right text-term-text">{compactUsd(c.total_volume)}</div>
                          <ShareBar value={(c.total_volume ?? 0) / maxVolume} tone="muted" className="mt-1" />
                        </div>
                      </td>
                      <td className="num px-3 py-2 text-right text-term-muted">{turnover == null ? '—' : pct(turnover, 1)}</td>
                      <td className="px-3 py-2">
                        <div className="flex justify-end gap-1.5">
                          <button type="button" className="rounded p-1 text-term-dim hover:bg-term-raised hover:text-term-text"
                                  aria-label={`${c.name} price chart`} onClick={() => setCharting(c)}>
                            <LineChart size={15} />
                          </button>
                          <button type="button" className={btn.buy} aria-label={`Buy ${c.name}`} onClick={() => setBuying(c)}>Buy</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {buying && <QuickBuyModal coin={buying} cash={cash} onBuy={onBuy} onClose={() => setBuying(null)} />}
      {charting && <PriceHistoryChart selectedCoin={charting} onClose={() => setCharting(null)} />}
    </div>
  );
};

/** Order ticket for a coin: size by quantity or by USD amount, at the market price, dated today by default. */
export const QuickBuyModal: React.FC<{ coin: Coin; cash: number | null; onBuy: (a: NewAsset) => Promise<void>; onClose: () => void }> = ({
  coin, cash, onBuy, onClose,
}) => {
  const [mode, setMode] = useState<'usd' | 'qty'>('usd');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(todayLocal());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const price = coin.current_price;
  const entered = parseFloat(amount) || 0;
  const quantity = mode === 'usd' ? (price > 0 ? entered / price : 0) : entered;
  const cost = quantity * price;
  const today = todayLocal();

  const presets = cash != null && cash > 0 ? [0.01, 0.05, 0.1, 0.25] : [];

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!(quantity > 0)) return setError('Enter an amount greater than 0.');
    if (cash != null && cost > cash + 0.005) return setError(`Not enough cash: this costs ${usd(cost)} and you have ${usd(cash)}.`);
    if (!date || date > today) return setError('Choose a trade date that is not in the future.');
    setSaving(true);
    setError(null);
    try {
      // Round to 8 decimals: enough precision for any coin, and avoids float noise in the ledger
      const q = Math.round(quantity * 1e8) / 1e8;
      await onBuy({ name: coin.name, quantity: q, pricePerUnit: price, purchasePricePerUnit: price, initialInvestment: q * price, purchaseDate: date });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The buy failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={`Buy ${coin.name}`} labelledBy="buy-title" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-3">
        <div className="flex items-center justify-between rounded-md border border-term-line bg-term-bg px-3 py-2 text-sm">
          <span className="flex items-center gap-2"><img src={coin.image} alt="" className="h-4 w-4 rounded-full" />{coin.symbol.toUpperCase()} / USD</span>
          <span className="num text-term-text">{usd(price)}</span>
        </div>

        <div className="flex gap-1 rounded-md border border-term-line bg-term-bg p-1" role="group" aria-label="Order size in">
          {(['usd', 'qty'] as const).map(m => (
            <button key={m} type="button" aria-pressed={mode === m} onClick={() => { setMode(m); setAmount(''); }}
                    className={`flex-1 rounded py-1 text-xs font-semibold ${mode === m ? 'bg-term-raised text-term-text' : 'text-term-muted hover:text-term-text'}`}>
              {m === 'usd' ? 'Amount (USD)' : `Quantity (${coin.symbol.toUpperCase()})`}
            </button>
          ))}
        </div>

        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">
          {mode === 'usd' ? 'Amount (USD)' : 'Quantity'}
          <input type="number" min="0" step="any" value={amount} onChange={e => setAmount(e.target.value)} autoFocus
                 className={`num mt-1 ${input}`} />
        </label>
        {presets.length > 0 && (
          <div className="flex gap-1.5">
            {presets.map(p => (
              <button key={p} type="button" className="num flex-1 rounded border border-term-line py-1 text-xs text-term-muted hover:border-term-line2 hover:text-term-text"
                      onClick={() => { setMode('usd'); setAmount((Math.floor((cash ?? 0) * p * 100) / 100).toString()); }}>
                {pct(p, 0)} of cash
              </button>
            ))}
          </div>
        )}
        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">
          Trade date
          <input type="date" value={date} max={today} onChange={e => setDate(e.target.value)} className={`num mt-1 ${input}`} />
        </label>

        <dl className="grid grid-cols-2 gap-1.5 rounded-md border border-term-line bg-term-bg px-3 py-2 text-xs">
          <dt className="text-term-dim">Quantity</dt><dd className="num text-right text-term-text">{fmtQty(quantity)}</dd>
          <dt className="text-term-dim">Order value</dt><dd className="num text-right text-term-text">{usd(cost)}</dd>
          {cash != null && (<>
            <dt className="text-term-dim">Cash after</dt>
            <dd className={`num text-right ${cost > cash ? 'text-term-down' : 'text-term-muted'}`}>{usd(cash - cost)}</dd>
            <dt className="text-term-dim">Share of cash</dt>
            <dd className={`num text-right ${cash > 0 && cost / cash > 0.25 ? 'text-term-warn' : 'text-term-muted'}`}>{cash > 0 ? pct(cost / cash, 1) : '—'}</dd>
          </>)}
        </dl>
        {cash != null && cash > 0 && cost / cash > 0.25 && (
          <p className="text-xs text-term-warn">Large single orders and same-day deployment lower your Evaluation score.</p>
        )}
        {error && <p role="alert" className="text-sm text-term-down">{error}</p>}
        <button type="submit" disabled={saving} className="w-full rounded-md bg-term-up py-2 text-sm font-semibold text-white hover:bg-[#4BB896] disabled:opacity-50">
          {saving ? 'Placing…' : `Buy ${coin.symbol.toUpperCase()}`}
        </button>
      </form>
    </Modal>
  );
};

export default CoinsPage;
