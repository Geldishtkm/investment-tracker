import React, { useState } from 'react';
import { Asset, TradeRequest } from '../types';
import { ApiError, assetService } from '../services/api';
import { todayLocal } from '../utils/dates';
import { Modal, btn, focusRing, input, segmented } from './ui/kit';
import { qty as fmtQty, usd } from './ui/format';

interface TradeModalProps {
  asset: Asset;
  currentPrice: number;
  /** Which side the ticket opens on. */
  initialSide?: TradeRequest['side'];
  /** Cash available for buys, shown on the ticket; omitted when unknown. */
  cash?: number | null;
  onClose: () => void;
  /** Called after the trade is recorded, with a message for the user. */
  onSaved: (message: string) => void;
}

/** Order ticket: records a buy or sell on a holding, with its price and date, in the trade ledger. */
const TradeModal: React.FC<TradeModalProps> = ({ asset, currentPrice, initialSide = 'BUY', cash, onClose, onSaved }) => {
  const [side, setSideState] = useState<TradeRequest['side']>(initialSide);
  // A sell starts as the whole position; a buy starts empty
  const [quantity, setQuantity] = useState(initialSide === 'SELL' ? String(asset.quantity) : '');
  /** Set when a buy costs more than the cash: the balance shown in the banner. */
  const [shortOf, setShortOf] = useState<number | null>(null);
  const [price, setPrice] = useState(currentPrice > 0 ? String(currentPrice) : '');
  const [date, setDate] = useState(todayLocal());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const today = todayLocal();
  const notional = (parseFloat(quantity) || 0) * (parseFloat(price) || 0);
  const avgCost = asset.purchasePricePerUnit || asset.pricePerUnit;
  const sellQty = Math.min(parseFloat(quantity) || 0, asset.quantity);
  const realizedPreview = sellQty * ((parseFloat(price) || 0) - avgCost);
  const px = parseFloat(price) || 0;

  const setSide = (next: TradeRequest['side']) => {
    setSideState(next);
    setError(null);
    setShortOf(null);
    setQuantity(next === 'SELL' ? String(asset.quantity) : '');
  };

  /** Share of the position (sell) or of the cash (buy), rounded down to 8 decimals. */
  const fill = (share: number) => {
    setShortOf(null);
    if (side === 'SELL') {
      setQuantity(String(share === 1 ? asset.quantity : Math.floor(asset.quantity * share * 1e8) / 1e8));
    } else if (cash != null && px > 0) {
      setQuantity(String(Math.floor((cash * share / px) * 1e8) / 1e8));
    }
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const qty = parseFloat(quantity);
    const px = parseFloat(price);
    if (!(qty > 0)) return setError('Quantity must be greater than 0.');
    if (!(px > 0)) return setError('Price must be greater than 0.');
    if (side === 'SELL' && qty > asset.quantity * (1 + 1e-9)) {
      return setError(`You hold ${asset.quantity} ${asset.name}; you can't sell more than that.`);
    }
    if (side === 'BUY' && cash != null && qty * px > cash + 0.005) {
      setError(null);
      return setShortOf(cash);
    }
    if (!date || date > today) return setError('Choose a trade date that is not in the future.');

    setSaving(true);
    setError(null);
    setShortOf(null);
    try {
      const result = await assetService.recordTrade(asset.id, { side, quantity: qty, price: px, date });
      if (side === 'SELL') {
        // Proceeds go back to cash; P&L is against the average cost of the units sold
        const realized = qty * (px - avgCost);
        const pnl = `${realized >= 0 ? '+' : '-'}${usd(Math.abs(realized))}`;
        onSaved(result.positionClosed
          ? `Sold all ${asset.name}: ${usd(qty * px)} credited to cash (${pnl} realized). The position is closed and kept in your trade history.`
          : `Sold ${qty} ${asset.name}: ${usd(qty * px)} credited to cash (${pnl} realized).`);
      } else {
        onSaved(`Bought ${qty} ${asset.name}.`);
      }
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INSUFFICIENT_FUNDS') {
        setShortOf(typeof e.details.cash === 'number' ? e.details.cash : cash ?? 0);
      } else {
        setError(e instanceof Error ? e.message : 'Failed to record the trade');
      }
    } finally {
      setSaving(false);
    }
  };

  const tab = (value: TradeRequest['side'], active: string) =>
    `flex-1 rounded-[5px] py-1.5 text-[13px] font-medium transition-colors duration-150 ${focusRing} ${side === value ? active : 'text-term-muted hover:text-term-text'}`;

  return (
    <Modal title={`Trade ${asset.name}`} labelledBy="trade-title" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-3">
        <div className={`${segmented.track} flex w-full`} role="group" aria-label="Trade side">
          <button type="button" aria-pressed={side === 'BUY'} onClick={() => setSide('BUY')} className={tab('BUY', 'bg-term-up/15 text-term-up')}>Buy</button>
          <button type="button" aria-pressed={side === 'SELL'} onClick={() => setSide('SELL')} className={tab('SELL', 'bg-term-down/15 text-term-down')}>Sell</button>
        </div>

        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">
          Quantity{side === 'SELL' && <span className="normal-case tracking-normal text-term-muted"> (you hold {asset.quantity})</span>}
          <input type="number" step="any" min="0" value={quantity} onChange={e => { setQuantity(e.target.value); setShortOf(null); }}
                 className={`num mt-1 ${input}`} />
        </label>
        {(side === 'SELL' || (cash != null && cash > 0)) && (
          <div className="flex gap-1.5" role="group" aria-label={side === 'SELL' ? 'Share of position' : 'Share of cash'}>
            {[0.25, 0.5, 0.75, 1].map(share => (
              <button key={share} type="button" onClick={() => fill(share)}
                      className={`num flex-1 rounded-md border border-term-line py-1 text-xs text-term-muted transition-colors duration-150 hover:border-white/20 hover:text-term-text ${focusRing}`}>
                {side === 'SELL' ? (share === 1 ? '100% / Max' : `${share * 100}%`) : `${share * 100}% cash`}
              </button>
            ))}
          </div>
        )}
        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">
          Price per unit (USD)
          <input type="number" step="any" min="0" value={price} onChange={e => setPrice(e.target.value)}
                 className={`num mt-1 ${input}`} />
        </label>
        <label className="block text-xs font-medium uppercase tracking-wider text-term-dim">
          Trade date
          <input type="date" value={date} max={today} onChange={e => setDate(e.target.value)} className={`num mt-1 ${input}`} />
        </label>

        <dl className="grid grid-cols-2 gap-2 rounded-md border border-term-line bg-black/20 px-3 py-2 text-xs">
          <dt className="text-term-dim">{side === 'SELL' ? 'Proceeds to cash' : 'Order value'}</dt>
          <dd className="num text-right text-term-text" data-testid="ticket-notional">{usd(notional)}</dd>
          {side === 'BUY' && cash != null && (
            <>
              <dt className="text-term-dim">Cash available</dt>
              <dd className={`num text-right ${notional > cash ? 'text-term-down' : 'text-term-muted'}`}>{usd(cash)}</dd>
            </>
          )}
          {side === 'SELL' && (
            <>
              <dt className="text-term-dim">Realized P&amp;L</dt>
              <dd className={`num text-right ${realizedPreview > 0.005 ? 'text-term-up' : realizedPreview < -0.005 ? 'text-term-down' : 'text-term-muted'}`}
                  data-testid="ticket-realized">
                {realizedPreview >= 0 ? '+' : '-'}{usd(Math.abs(realizedPreview))}
              </dd>
              {cash != null && (
                <>
                  <dt className="text-term-dim">Cash after</dt>
                  <dd className="num text-right text-term-muted">{usd(cash + notional)}</dd>
                </>
              )}
              <dt className="text-term-dim">Position after</dt>
              <dd className="num text-right text-term-muted">{fmtQty(Math.max(0, asset.quantity - (parseFloat(quantity) || 0)))}</dd>
            </>
          )}
        </dl>

        {shortOf != null && (
          <div role="alert" className="rounded-md border border-term-down/40 bg-term-down/10 px-3 py-2 text-sm text-term-down">
            Insufficient cash available. Current cash balance: {usd(shortOf)}
          </div>
        )}
        {error && <p className="text-sm text-term-down" role="alert">{error}</p>}

        <p className="text-xs text-term-dim">Paper trades execute near the market price and feed the Institutional Evaluation.</p>
        <button type="submit" disabled={saving}
                className={side === 'BUY' ? btn.submitBuy : btn.submitSell}>
          {saving ? 'Saving…' : side === 'BUY' ? 'Record buy' : 'Record sell'}
        </button>
        <button type="button" onClick={onClose} className={`${btn.ghost} w-full`}>Cancel</button>
      </form>
    </Modal>
  );
};

export default TradeModal;
