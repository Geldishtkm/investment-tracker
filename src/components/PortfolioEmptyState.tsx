import React from 'react';
import { AlertTriangle, Plus, RefreshCw, Wallet } from 'lucide-react';
import { btn } from './ui/kit';

interface PortfolioEmptyStateProps {
  /** Opens the add-asset flow (the market grid). */
  onAddAsset: () => void;
}

/**
 * Shown by the portfolio, risk and analytics sections when there are no positions. An empty book is a
 * normal starting point, not an error, so this points the user to the market grid.
 */
const PortfolioEmptyState: React.FC<PortfolioEmptyStateProps> = ({ onAddAsset }) => (
  <div className="mx-auto max-w-xl rounded-lg border border-term-line bg-term-panel px-8 py-12 text-center">
    <div className="mx-auto mb-5 flex h-12 w-12 items-center justify-center rounded-md border border-term-line2 bg-term-raised">
      <Wallet size={22} className="text-term-accent" />
    </div>
    <h3 className="text-base font-semibold text-term-text">Your portfolio is currently empty</h3>
    <p className="mx-auto mt-2 max-w-md text-sm text-term-muted">
      Add coins or tokens to your portfolio to view real-time risk metrics (VaR, Sharpe ratio, and volatility).
    </p>
    <div className="mt-5 flex flex-wrap justify-center gap-2 text-[11px] uppercase tracking-wider text-term-dim">
      {['Value at Risk', 'Sharpe ratio', 'Volatility'].map(label => (
        <span key={label} className="rounded border border-term-line px-2 py-1">{label}</span>
      ))}
    </div>
    <button type="button" onClick={onAddAsset} className={`${btn.primary} mt-6`}>
      <Plus size={16} />
      Add Asset
    </button>
  </div>
);

/** Shown instead of the empty state when the portfolio itself could not be loaded. */
export const AssetsLoadError: React.FC<{ message: string; onRetry: () => void }> = ({ message, onRetry }) => (
  <div className="mx-auto max-w-xl rounded-lg border border-term-down/40 bg-term-panel px-8 py-10 text-center" role="alert">
    <AlertTriangle size={24} className="mx-auto mb-3 text-term-down" />
    <h3 className="text-base font-semibold text-term-text">Couldn't load your portfolio</h3>
    <p className="mt-2 text-sm text-term-muted">{message}</p>
    <button type="button" onClick={onRetry} className={`${btn.secondary} mt-5`}>
      <RefreshCw size={14} />
      Try again
    </button>
  </div>
);

export default PortfolioEmptyState;
