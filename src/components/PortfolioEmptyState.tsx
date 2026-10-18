import React from 'react';
import { Wallet, Plus, Activity, TrendingUp, ShieldCheck, AlertTriangle, RefreshCw } from 'lucide-react';

interface PortfolioEmptyStateProps {
  /** Opens the add-asset flow (the Coins page). */
  onAddAsset: () => void;
}

/**
 * Shown by the risk and analytics dashboards when the portfolio has no assets. An empty wallet is
 * a normal starting point, not an error, so this guides the user to add their first asset.
 */
const PortfolioEmptyState: React.FC<PortfolioEmptyStateProps> = ({ onAddAsset }) => {
  const metrics = [
    { icon: ShieldCheck, label: 'Value at Risk' },
    { icon: TrendingUp, label: 'Sharpe ratio' },
    { icon: Activity, label: 'Volatility' },
  ];

  return (
    <div className="glass-card p-10 sm:p-12 text-center max-w-2xl mx-auto border border-blue-600/20">
      <div className="w-24 h-24 bg-gradient-to-br from-blue-500/20 to-indigo-600/20 rounded-full flex items-center justify-center mx-auto mb-6">
        <Wallet size={44} className="text-blue-400" />
      </div>

      <h3 className="text-2xl font-bold text-white mb-3">
        Your portfolio is currently empty
      </h3>

      <p className="text-gray-400 mb-8 max-w-md mx-auto">
        Add coins or tokens to your portfolio to view real-time risk metrics (VaR, Sharpe ratio, and volatility).
      </p>

      <div className="flex flex-wrap justify-center gap-3 mb-8">
        {metrics.map(({ icon: Icon, label }) => (
          <span
            key={label}
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-gray-800/60 border border-gray-700/60 text-gray-300 text-sm"
          >
            <Icon size={14} className="text-blue-400" />
            {label}
          </span>
        ))}
      </div>

      <button
        onClick={onAddAsset}
        className="btn btn-primary flex-center gap-2 mx-auto"
      >
        <Plus size={20} />
        Add Asset
      </button>
    </div>
  );
};

/** Shown instead of the empty state when the portfolio itself could not be loaded. */
export const AssetsLoadError: React.FC<{ message: string; onRetry: () => void }> = ({ message, onRetry }) => (
  <div className="glass-card p-10 text-center max-w-2xl mx-auto border border-red-600/30">
    <AlertTriangle size={40} className="text-red-400 mx-auto mb-4" />
    <h3 className="text-xl font-bold text-white mb-2">Couldn't load your portfolio</h3>
    <p className="text-gray-400 mb-6">{message}</p>
    <button onClick={onRetry} className="btn btn-primary flex-center gap-2 mx-auto">
      <RefreshCw size={18} />
      Try again
    </button>
  </div>
);

export default PortfolioEmptyState;
