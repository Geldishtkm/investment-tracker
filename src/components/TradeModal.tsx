import React, { useState } from 'react';
import { X } from 'lucide-react';
import { Asset, TradeRequest } from '../types';
import { assetService } from '../services/api';
import { todayLocal } from '../utils/dates';

interface TradeModalProps {
  asset: Asset;
  currentPrice: number;
  onClose: () => void;
  /** Called after the trade is recorded, with a message for the user. */
  onSaved: (message: string) => void;
}

/** Records a buy or sell on a holding, with its price and date, in the trade ledger. */
const TradeModal: React.FC<TradeModalProps> = ({ asset, currentPrice, onClose, onSaved }) => {
  const [side, setSide] = useState<TradeRequest['side']>('BUY');
  const [quantity, setQuantity] = useState('');
  const [price, setPrice] = useState(currentPrice > 0 ? String(currentPrice) : '');
  const [date, setDate] = useState(todayLocal());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const today = todayLocal();

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

  const toggle = (value: TradeRequest['side'], active: string) =>
    `flex-1 py-2 rounded-lg font-semibold transition-colors ${side === value ? active : 'bg-gray-800 text-gray-400 hover:text-white'}`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true"
         aria-labelledby="trade-title">
      <form onSubmit={submit} className="w-full max-w-md bg-gray-900 border border-gray-700 rounded-2xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h3 id="trade-title" className="text-lg font-bold text-white">Trade {asset.name}</h3>
          <button type="button" onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-white"><X size={20} /></button>
        </div>

        <div className="flex gap-2" role="group" aria-label="Trade side">
          <button type="button" aria-pressed={side === 'BUY'} onClick={() => setSide('BUY')} className={toggle('BUY', 'bg-emerald-600 text-white')}>Buy</button>
          <button type="button" aria-pressed={side === 'SELL'} onClick={() => setSide('SELL')} className={toggle('SELL', 'bg-red-600 text-white')}>Sell</button>
        </div>

        <label className="block text-sm text-gray-300">
          Quantity{side === 'SELL' && <span className="text-gray-500"> (you hold {asset.quantity})</span>}
          <input type="number" step="any" min="0" value={quantity} onChange={e => setQuantity(e.target.value)}
                 className="mt-1 w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white" />
        </label>
        <label className="block text-sm text-gray-300">
          Price per unit (USD)
          <input type="number" step="any" min="0" value={price} onChange={e => setPrice(e.target.value)}
                 className="mt-1 w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white" />
        </label>
        <label className="block text-sm text-gray-300">
          Trade date
          <input type="date" value={date} max={today} onChange={e => setDate(e.target.value)}
                 className="mt-1 w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white" />
        </label>

        {error && <p className="text-sm text-red-300" role="alert">{error}</p>}

        <p className="text-xs text-gray-500">Dated trades feed the Institutional Evaluation (disposition, FOMO and panic analysis).</p>
        <button type="submit" disabled={saving}
                className={`w-full py-3 rounded-xl font-semibold text-white disabled:opacity-50 ${side === 'BUY' ? 'bg-emerald-600 hover:bg-emerald-500' : 'bg-red-600 hover:bg-red-500'}`}>
          {saving ? 'Saving…' : side === 'BUY' ? 'Record buy' : 'Record sell'}
        </button>
      </form>
    </div>
  );
};

export default TradeModal;
