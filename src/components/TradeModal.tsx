import React, { useState } from 'react';
import { Asset, TradeRequest } from '../types';
import { assetService } from '../services/api';
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
  const [side, setSide] = useState<TradeRequest['side']>(initialSide);
  const [quantity, setQuantity] = useState('');
  const [price, setPrice] = useState(currentPrice > 0 ? String(currentPrice) : '');
  const [date, setDate] = useState(todayLocal());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const today = todayLocal();
  const notional = (parseFloat(quantity) || 0) * (parseFloat(price) || 0);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const qty = parseFloat(quantity);
    const px = parseFloat(price);
    if (!(qty > 0)) return setError('Quantity must be greater than 0.');
    if (!(px > 0)) return setError('Price must be greater than 0.');
    if (side === 'SELL' && qty > asset.quantity * (1 + 1e-9)) {
      return setError(`You hold ${asset.quantity} ${asset.name}; you can't sell more than that.`);
    }
    if (!date || date > today) return setError('Choose a trade date that is not in the future.');

    setSaving(true);
    setError(null);
    try {
      const result = await assetService.recordTrade(asset.id, { side, quantity: qty, price: px, date });
      onSaved(result.positionClosed
        ? `Sold all ${asset.name}; the position is closed and kept in your trade history.`
        : `${side === 'BUY' ? 'Bought' : 'Sold'} ${qty} ${asset.name}.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to record the trade');
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
          <input type="number" step="any" min="0" value={quantity} onChange={e => setQuantity(e.target.value)}
                 className={`num mt-1 ${input}`} />
        </label>
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
          <dt className="text-term-dim">Order value</dt>
          <dd className="num text-right text-term-text">{usd(notional)}</dd>
          {side === 'BUY' && cash != null && (
            <>
              <dt className="text-term-dim">Cash available</dt>
              <dd className={`num text-right ${notional > cash ? 'text-term-down' : 'text-term-muted'}`}>{usd(cash)}</dd>
            </>
          )}
          {side === 'SELL' && (
            <>
              <dt className="text-term-dim">Position after</dt>
              <dd className="num text-right text-term-muted">{fmtQty(Math.max(0, asset.quantity - (parseFloat(quantity) || 0)))}</dd>
            </>
          )}
        </dl>

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
