import React, { useState } from 'react';
import { Asset, TradeRequest } from '../types';
import TradeModal from './TradeModal';
import { Modal, btn } from './ui/kit';
import { pct, qty, usd } from './ui/format';

interface Props {
  asset: Asset;
  /** Live market price per unit. */
  price: number;
  cash: number | null;
  onClose: () => void;
  /** Called after a trade, with a message for the user. */
  onSaved: (message: string) => void;
}

/**
 * Opened by clicking a position: a short summary, then 'Buy More' or 'Sell / Liquidate', which continue
 * to the order ticket on that side.
 */
const AssetActionSheet: React.FC<Props> = ({ asset, price, cash, onClose, onSaved }) => {
  const [side, setSide] = useState<TradeRequest['side'] | null>(null);
  if (side) {
    return <TradeModal asset={asset} currentPrice={price} initialSide={side} cash={cash} onClose={onClose} onSaved={onSaved} />;
  }

  const avg = asset.purchasePricePerUnit || asset.pricePerUnit;
  const value = asset.quantity * price;
  const pnl = asset.quantity * (price - avg);
  return (
    <Modal title={asset.name} labelledBy="asset-actions-title" onClose={onClose}>
      <dl className="mb-4 grid grid-cols-2 gap-x-4 gap-y-2 rounded-md border border-term-line bg-black/20 px-3 py-2.5 text-sm">
        <dt className="text-term-dim">Holding</dt><dd className="num text-right text-term-text">{qty(asset.quantity)}</dd>
        <dt className="text-term-dim">Market price</dt><dd className="num text-right text-term-text">{usd(price)}</dd>
        <dt className="text-term-dim">Value</dt><dd className="num text-right text-term-text">{usd(value)}</dd>
        <dt className="text-term-dim">Unrealized P&amp;L</dt>
        <dd className={`num text-right ${pnl > 0.005 ? 'text-term-up' : pnl < -0.005 ? 'text-term-down' : 'text-term-muted'}`}>
          {pnl >= 0 ? '+' : '-'}{usd(Math.abs(pnl))} <span className="text-xs">({pct(avg > 0 ? price / avg - 1 : 0, 2, true)})</span>
        </dd>
        {cash != null && (<><dt className="text-term-dim">Cash available</dt><dd className="num text-right text-term-muted">{usd(cash)}</dd></>)}
      </dl>
      <div className="grid grid-cols-2 gap-2">
        <button type="button" className={btn.submitBuy} onClick={() => setSide('BUY')} autoFocus>Buy More</button>
        <button type="button" className={btn.submitSell} onClick={() => setSide('SELL')}>Sell / Liquidate</button>
      </div>
    </Modal>
  );
};

export default AssetActionSheet;
