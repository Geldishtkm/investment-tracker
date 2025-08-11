import React, { useState, useEffect } from 'react';
import {
  TrendingUp,
  Target,
  BarChart3,
  RefreshCw,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Info,
  Calculator,
  PieChart,
  ArrowRight,
  DollarSign,
  Shield,
  Zap,
  Loader2
} from 'lucide-react';
import { assetService } from '../services/api';
import { authService } from '../services/authService';
import { AssetWithPrice } from '../types';

interface RebalancingAction {
  assetName: string;
  actionType: 'BUY' | 'SELL';
  quantityChange: number;
  valueChange: number;
  currentAllocation: number;
  targetAllocation: number;
  allocationChange: number;
  transactionCost: number;
  priority: number;
  urgency: string;
  actionDescription: string;
  actionIcon: string;
  actionColor: string;
}

interface PortfolioRebalancing {
  userId: string;
  currentPortfolioValue: number;
  targetPortfolioValue: number;
  riskTolerance: number;
  currentAllocation: Record<string, number>;
  targetAllocation: Record<string, number>;
  recommendedActions: RebalancingAction[];
  currentRisk: number;
  targetRisk: number;
  expectedReturn: number;
  riskReduction: number;
  returnImprovement: number;
  totalTransactionCost: number;
  optimizationMethod: string;
  portfolioStatus: string;
}

const PortfolioRebalancingDashboard: React.FC = () => {
  const [rebalancing, setRebalancing] = useState<PortfolioRebalancing | null>(null);
  const [riskTolerance, setRiskTolerance] = useState(0.5);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [portfolioAssets, setPortfolioAssets] = useState<AssetWithPrice[]>([]);
  const [portfolioValue, setPortfolioValue] = useState(0);

  // Load real portfolio data on component mount
  useEffect(() => {
    loadPortfolioData();
  }, []);

  const loadPortfolioData = async () => {
    try {
      setLoading(true);
      const assets = await assetService.getAllAssetsWithPrices();
      setPortfolioAssets(assets);

      // Calculate total portfolio value
      const totalValue = assets.reduce((sum, asset) => {
        return sum + ((asset.currentPrice || 0) * asset.quantity);
      }, 0);
      setPortfolioValue(totalValue);

      // Initialize with current allocation
      if (assets.length > 0) {
        const currentAllocation: Record<string, number> = {};
        assets.forEach(asset => {
          const assetValue = (asset.currentPrice || 0) * asset.quantity;
          currentAllocation[asset.name] = assetValue / totalValue;
        });

        setRebalancing({
          userId: 'current_user',
          currentPortfolioValue: totalValue,
          targetPortfolioValue: totalValue,
          riskTolerance: 0.5,
          currentAllocation,
          targetAllocation: { ...currentAllocation },
          recommendedActions: [],
          currentRisk: 0,
          targetRisk: 0,
          expectedReturn: 0,
          riskReduction: 0,
          returnImprovement: 0,
          totalTransactionCost: 0,
          optimizationMethod: 'MVO',
          portfolioStatus: 'BALANCED'
        });
      }
    } catch (err) {
      setError('Failed to load portfolio data');
      console.error('Error loading portfolio data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleOptimize = async () => {
    if (!rebalancing || portfolioAssets.length === 0) return;

    try {
      setLoading(true);
      setError(null);

      const response = await fetch('http://localhost:8080/api/portfolio-rebalancing/optimize', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...authService.getAuthHeader()
        },
        body: JSON.stringify({
          username: 'current_user',
          riskTolerance
        })
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result = await response.json();
      setRebalancing(result);
    } catch (err) {
      setError('Failed to optimize portfolio. Please try again.');
      console.error('Error optimizing portfolio:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleBlackLitterman = async () => {
    if (!rebalancing || portfolioAssets.length === 0) return;

    try {
      setLoading(true);
      setError(null);

      const response = await fetch('http://localhost:8080/api/portfolio-rebalancing/black-litterman', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...authService.getAuthHeader()
        },
        body: JSON.stringify({
          username: 'current_user',
          riskTolerance,
          userViews: {}
        })
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const result = await response.json();
      setRebalancing(result);
    } catch (err) {
      setError('Failed to apply Black-Litterman optimization. Please try again.');
      console.error('Error applying Black-Litterman:', err);
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'BALANCED': return 'text-green-400';
      case 'NEEDS_REBALANCING': return 'text-yellow-400';
      case 'CRITICAL': return 'text-red-400';
      default: return 'text-gray-400';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'BALANCED': return <CheckCircle className="w-5 h-5" />;
      case 'NEEDS_REBALANCING': return <AlertTriangle className="w-5 h-5" />;
      case 'CRITICAL': return <XCircle className="w-5 h-5" />;
      default: return <Info className="w-5 h-5" />;
    }
  };

  if (loading && !rebalancing) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 p-6">
        <div className="flex items-center justify-center h-96">
          <div className="text-center">
            <Loader2 className="w-12 h-12 animate-spin text-purple-400 mx-auto mb-4" />
            <p className="text-purple-200 text-lg">Loading portfolio data...</p>
          </div>
        </div>
      </div>
    );
  }

  if (!rebalancing || portfolioAssets.length === 0) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 p-6">
        <div className="text-center">
          <div className="w-24 h-24 bg-gradient-to-br from-orange-500 to-red-500 rounded-full flex items-center justify-center mx-auto mb-6">
            <Target className="w-12 h-12 text-white" />
          </div>
          <h2 className="text-2xl font-bold text-white mb-4">No Portfolio Data</h2>
          <p className="text-gray-300 mb-6">Add some assets to your portfolio to start rebalancing.</p>
          <button
            onClick={loadPortfolioData}
            className="px-6 py-3 bg-gradient-to-r from-orange-500 to-red-500 text-white rounded-xl font-medium hover:from-orange-600 hover:to-red-600 transition-all duration-300"
          >
            <RefreshCw className="w-5 h-5 inline mr-2" />
            Refresh Data
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 p-6">
      {/* Header */}
      <div className="text-center mb-8">
        <div className="w-20 h-20 bg-gradient-to-br from-orange-500 to-red-500 rounded-full flex items-center justify-center mx-auto mb-4">
          <Target className="w-10 h-10 text-white" />
        </div>
        <h1 className="text-4xl font-bold text-white mb-2">Portfolio Rebalancing</h1>
        <p className="text-gray-300 text-lg">Optimize your portfolio with institutional-grade algorithms</p>
      </div>

      {/* Portfolio Status */}
      <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 mb-8 shadow-xl shadow-black/20">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-2xl font-bold text-white">Portfolio Status</h2>
          <div className={`flex items-center gap-2 ${getStatusColor(rebalancing.portfolioStatus)}`}>
            {getStatusIcon(rebalancing.portfolioStatus)}
            <span className="font-medium">{rebalancing.portfolioStatus}</span>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="text-center">
            <div className="text-3xl font-bold text-green-400">${portfolioValue.toLocaleString()}</div>
            <div className="text-gray-400">Total Value</div>
          </div>
          <div className="text-center">
            <div className="text-3xl font-bold text-blue-400">{portfolioAssets.length}</div>
            <div className="text-gray-400">Assets</div>
          </div>
          <div className="text-center">
            <div className="text-3xl font-bold text-purple-400">{rebalancing.recommendedActions.length}</div>
            <div className="text-gray-400">Actions Needed</div>
          </div>
        </div>
      </div>

      {/* Risk Tolerance & Controls */}
      <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 mb-8 shadow-xl shadow-black/20">
        <div className="flex flex-col lg:flex-row gap-6 items-start lg:items-center">
          <div className="flex-1">
            <label className="block text-white font-medium mb-2">Risk Tolerance</label>
            <div className="flex items-center gap-4">
              <input
                type="range"
                min="0"
                max="1"
                step="0.1"
                value={riskTolerance}
                onChange={(e) => setRiskTolerance(parseFloat(e.target.value))}
                className="flex-1 h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer slider"
              />
              <span className="text-white font-medium min-w-[60px]">{Math.round(riskTolerance * 100)}%</span>
            </div>
            <div className="flex justify-between text-xs text-gray-400 mt-1">
              <span>Conservative</span>
              <span>Aggressive</span>
            </div>
          </div>

          <div className="flex gap-3">
            <button
              onClick={handleOptimize}
              disabled={loading}
              className="px-6 py-3 bg-gradient-to-r from-blue-500 to-indigo-500 text-white rounded-xl font-medium hover:from-blue-600 hover:to-indigo-600 transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Calculator className="w-4 h-4" />}
              MVO Optimize
            </button>

            <button
              onClick={handleBlackLitterman}
              disabled={loading}
              className="px-6 py-3 bg-gradient-to-r from-purple-500 to-pink-500 text-white rounded-xl font-medium hover:from-purple-600 hover:to-pink-600 transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <BarChart3 className="w-4 h-4" />}
              Black-Litterman
            </button>
          </div>
        </div>
      </div>

      {/* Error Display */}
      {error && (
        <div className="bg-gradient-to-r from-red-500/20 to-red-600/20 border border-red-500/30 rounded-xl p-4 mb-6">
          <div className="flex items-center gap-3">
            <AlertTriangle className="w-5 h-5 text-red-400" />
            <span className="text-red-200">{error}</span>
          </div>
        </div>
      )}

      {/* Current vs Target Allocation */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
          <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
            <PieChart className="w-5 h-5 text-blue-400" />
            Current Allocation
          </h3>
          <div className="space-y-3">
            {Object.entries(rebalancing.currentAllocation).map(([asset, allocation]) => (
              <div key={asset} className="flex items-center justify-between">
                <span className="text-gray-300">{asset}</span>
                <div className="flex items-center gap-3">
                  <div className="w-24 bg-gray-700 rounded-full h-2">
                    <div
                      className="bg-blue-500 h-2 rounded-full transition-all duration-300"
                      style={{ width: `${allocation * 100}%` }}
                    ></div>
                  </div>
                  <span className="text-white font-medium min-w-[60px]">{(allocation * 100).toFixed(1)}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
          <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
            <Target className="w-5 h-5 text-green-400" />
            Target Allocation
          </h3>
          <div className="space-y-3">
            {Object.entries(rebalancing.targetAllocation).map(([asset, allocation]) => (
              <div key={asset} className="flex items-center justify-between">
                <span className="text-gray-300">{asset}</span>
                <div className="flex items-center gap-3">
                  <div className="w-24 bg-gray-700 rounded-full h-2">
                    <div
                      className="bg-green-500 h-2 rounded-full transition-all duration-300"
                      style={{ width: `${allocation * 100}%` }}
                    ></div>
                  </div>
                  <span className="text-white font-medium min-w-[60px]">{(allocation * 100).toFixed(1)}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Recommended Actions */}
      {rebalancing.recommendedActions.length > 0 && (
        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 mb-8 shadow-xl shadow-black/20">
          <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
            <ArrowRight className="w-5 h-5 text-orange-400" />
            Recommended Actions
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {rebalancing.recommendedActions.map((action, index) => (
              <div key={index} className="bg-gradient-to-br from-gray-700/50 to-gray-800/50 border border-gray-600/50 rounded-xl p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-white">{action.assetName}</span>
                  <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                    action.actionType === 'BUY'
                      ? 'bg-green-500/20 text-green-400 border border-green-500/30'
                      : 'bg-red-500/20 text-red-400 border border-red-500/30'
                  }`}>
                    {action.actionType}
                  </span>
                </div>
                <div className="text-sm text-gray-300 mb-2">{action.actionDescription}</div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-gray-400">Value: ${Math.abs(action.valueChange).toLocaleString()}</span>
                  <span className="text-gray-400">Cost: ${action.transactionCost.toFixed(2)}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Performance Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-xl p-4 text-center shadow-xl shadow-black/20">
          <div className="w-12 h-12 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-xl flex items-center justify-center mx-auto mb-3">
            <TrendingUp className="w-6 h-6 text-white" />
          </div>
          <div className="text-2xl font-bold text-white mb-1">{(rebalancing.expectedReturn * 100).toFixed(2)}%</div>
          <div className="text-gray-400 text-sm">Expected Return</div>
        </div>

        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-xl p-4 text-center shadow-xl shadow-black/20">
          <div className="w-12 h-12 bg-gradient-to-br from-red-500 to-pink-600 rounded-xl flex items-center justify-center mx-auto mb-3">
            <Shield className="w-6 h-6 text-white" />
          </div>
          <div className="text-2xl font-bold text-white mb-1">{(rebalancing.currentRisk * 100).toFixed(2)}%</div>
          <div className="text-gray-400 text-sm">Current Risk</div>
        </div>

        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-xl p-4 text-center shadow-xl shadow-black/20">
          <div className="w-12 h-12 bg-gradient-to-br from-green-500 to-emerald-600 rounded-xl flex items-center justify-center mx-auto mb-3">
            <Zap className="w-6 h-6 text-white" />
          </div>
          <div className="text-2xl font-bold text-white mb-1">{(rebalancing.riskReduction * 100).toFixed(2)}%</div>
          <div className="text-gray-400 text-sm">Risk Reduction</div>
        </div>

        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-xl p-4 text-center shadow-xl shadow-black/20">
          <div className="w-12 h-12 bg-gradient-to-br from-purple-500 to-pink-600 rounded-xl flex items-center justify-center mx-auto mb-3">
            <DollarSign className="w-6 h-6 text-white" />
          </div>
          <div className="text-2xl font-bold text-white mb-1">${rebalancing.totalTransactionCost.toFixed(2)}</div>
          <div className="text-gray-400 text-sm">Transaction Cost</div>
        </div>
      </div>

      {/* Info Section */}
      <div className="bg-gradient-to-br from-blue-900/20 to-indigo-900/20 border border-blue-500/30 rounded-2xl p-6">
        <div className="flex items-start gap-3">
          <Info className="w-6 h-6 text-blue-400 mt-1 flex-shrink-0" />
          <div>
            <h3 className="text-lg font-semibold text-blue-200 mb-2">About Portfolio Rebalancing</h3>
            <div className="text-blue-100 space-y-2 text-sm">
              <p><strong>Mean-Variance Optimization (MVO):</strong> Uses historical data to find the optimal balance between risk and return based on your risk tolerance.</p>
              <p><strong>Black-Litterman Model:</strong> Combines market equilibrium with your personal views to create more personalized portfolio allocations.</p>
              <p><strong>Risk Tolerance:</strong> Lower values mean more conservative allocations, higher values mean more aggressive allocations.</p>
            </div>
          </div>
        </div>
      </div>

      <style>{`
        .slider::-webkit-slider-thumb {
          appearance: none;
          height: 20px;
          width: 20px;
          border-radius: 50%;
          background: linear-gradient(135deg, #f97316, #ef4444);
          cursor: pointer;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
        }

        .slider::-moz-range-thumb {
          height: 20px;
          width: 20px;
          border-radius: 50%;
          background: linear-gradient(135deg, #f97316, #ef4444);
          cursor: pointer;
          border: none;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
        }
      `}</style>
    </div>
  );
};

export default PortfolioRebalancingDashboard;
