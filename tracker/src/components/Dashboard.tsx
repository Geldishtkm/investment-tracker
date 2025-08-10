import React, { useState, useEffect } from 'react';
import { 
  DollarSign, TrendingUp, TrendingDown, Activity, Calendar, 
  ArrowUpRight, ArrowDownRight, PieChart, BarChart3, Eye, EyeOff,
  RefreshCw, Plus, Minus, LogOut
} from 'lucide-react';
import { Asset } from '../types';

interface DashboardProps {
  assets: Asset[];
  totalValue: number;
  onRefresh: () => void;
}

const Dashboard: React.FC<DashboardProps> = ({ assets, totalValue, onRefresh }) => {
  const [showBalance, setShowBalance] = useState(true);
  const [isLoading, setIsLoading] = useState(false);

  const calculateMetrics = () => {
    if (!assets || assets.length === 0) {
      return {
        totalGain: 0,
        totalGainPercent: 0,
        assetDistribution: [],
        totalInvestment: 0
      };
    }

    const totalInvestment = assets.reduce((sum, asset) => sum + (asset.initialInvestment || 0), 0);
    const totalGain = totalValue - totalInvestment;
    const totalGainPercent = totalInvestment > 0 ? (totalGain / totalInvestment) * 100 : 0;

    const assetDistribution = assets.map(asset => {
      const currentValue = asset.quantity * asset.pricePerUnit;
      const percentage = totalValue > 0 ? (currentValue / totalValue) * 100 : 0;
      return {
        name: asset.name,
        value: Math.round(percentage * 100) / 100,
        color: getAssetColor(asset.name)
      };
    }).sort((a, b) => b.value - a.value);

    return {
      totalGain,
      totalGainPercent,
      assetDistribution,
      totalInvestment
    };
  };

  const getAssetColor = (assetName: string): string => {
    const colors = [
      '#f7931a', '#627eea', '#14f195', '#0033ad', '#6c757d',
      '#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#feca57'
    ];
    const index = assetName.length % colors.length;
    return colors[index];
  };

  const metrics = calculateMetrics();

  const handleRefresh = async () => {
    setIsLoading(true);
    await onRefresh();
    setTimeout(() => setIsLoading(false), 1000);
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(amount);
  };

  const formatPercent = (percent: number) => {
    return `${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%`;
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 text-white">
      {/* Top Navigation */}
      <nav className="bg-gray-800/50 backdrop-blur-sm border-b border-gray-700/50 px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-gradient-to-r from-green-500 to-emerald-600 rounded-lg flex items-center justify-center">
              <DollarSign size={20} className="text-white" />
            </div>
            <h1 className="text-xl font-bold bg-gradient-to-r from-green-400 to-emerald-400 bg-clip-text text-transparent">
              Investment Tracker
            </h1>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={handleRefresh}
              disabled={isLoading}
              className="p-2 rounded-lg bg-gray-700/50 hover:bg-gray-600/50 transition-colors"
            >
              <RefreshCw size={20} className={`${isLoading ? 'animate-spin' : ''}`} />
            </button>
            <button className="px-4 py-2 bg-gray-700/50 hover:bg-gray-600/50 rounded-lg transition-colors">
              <LogOut size={20} />
            </button>
          </div>
        </div>
      </nav>

      <div className="p-6">
        {/* Balance Overview */}
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 mb-8">
          {/* Total Balance */}
          <div className="lg:col-span-2 bg-gradient-to-br from-gray-800/50 to-gray-700/50 rounded-2xl p-6 border border-gray-600/30 backdrop-blur-sm">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-gray-300">Total Balance</h2>
              <button
                onClick={() => setShowBalance(!showBalance)}
                className="p-2 rounded-lg bg-gray-700/50 hover:bg-gray-600/50 transition-colors"
              >
                {showBalance ? <Eye size={16} /> : <EyeOff size={16} />}
              </button>
            </div>
            <div className="text-3xl font-bold mb-2">
              {showBalance ? formatCurrency(totalValue) : '••••••••'}
            </div>
            <div className="flex items-center gap-2 text-green-400">
              <TrendingUp size={16} />
              <span className="font-medium">{formatPercent(metrics.totalGainPercent)}</span>
              <span className="text-gray-400">({formatCurrency(metrics.totalGain)})</span>
            </div>
          </div>

          {/* Total Investment */}
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-700/50 rounded-2xl p-6 border border-gray-600/30 backdrop-blur-sm">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 bg-blue-500/20 rounded-lg flex items-center justify-center">
                <DollarSign size={20} className="text-blue-400" />
              </div>
              <div>
                <h3 className="text-sm text-gray-400">Total Investment</h3>
                <div className="text-lg font-bold">{formatCurrency(metrics.totalInvestment)}</div>
              </div>
            </div>
          </div>

          {/* Asset Count */}
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-700/50 rounded-2xl p-6 border border-gray-600/30 backdrop-blur-sm">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 bg-purple-500/20 rounded-lg flex items-center justify-center">
                <PieChart size={20} className="text-purple-400" />
              </div>
              <div>
                <h3 className="text-sm text-gray-400">Assets</h3>
                <div className="text-lg font-bold">{assets.length}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Asset Distribution */}
        {assets.length > 0 && (
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-700/50 rounded-2xl p-6 border border-gray-600/30 backdrop-blur-sm mb-8">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 bg-orange-500/20 rounded-lg flex items-center justify-center">
                <PieChart size={20} className="text-orange-400" />
              </div>
              <h3 className="text-lg font-semibold">Asset Distribution</h3>
            </div>
            <div className="space-y-4">
              {metrics.assetDistribution.map((asset, index) => (
                <div key={index} className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div 
                      className="w-4 h-4 rounded-full"
                      style={{ backgroundColor: asset.color }}
                    ></div>
                    <span className="text-sm font-medium">{asset.name}</span>
                  </div>
                  <span className="text-sm font-bold">{asset.value.toFixed(1)}%</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Assets List */}
        {assets.length > 0 && (
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-700/50 rounded-2xl p-6 border border-gray-600/30 backdrop-blur-sm">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 bg-indigo-500/20 rounded-lg flex items-center justify-center">
                <Activity size={20} className="text-indigo-400" />
              </div>
              <h3 className="text-lg font-semibold">Your Assets</h3>
            </div>
            <div className="space-y-4">
                             {assets.map((asset) => {
                 const currentValue = asset.quantity * asset.pricePerUnit;
                 const assetGain = currentValue - (asset.initialInvestment || 0);
                 const assetGainPercent = (asset.initialInvestment || 0) > 0 ? (assetGain / (asset.initialInvestment || 0)) * 100 : 0;
                
                return (
                  <div key={asset.id} className="flex items-center justify-between p-4 bg-gray-700/30 rounded-lg border border-gray-600/20">
                    <div className="flex items-center gap-4">
                      <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                        assetGain >= 0 ? 'bg-green-500/20' : 'bg-red-500/20'
                      }`}>
                        {assetGain >= 0 ? (
                          <TrendingUp size={16} className="text-green-400" />
                        ) : (
                          <TrendingDown size={16} className="text-red-400" />
                        )}
                      </div>
                      <div>
                        <div className="font-medium">{asset.name}</div>
                        <div className="text-sm text-gray-400">
                          {asset.quantity} units @ {formatCurrency(asset.pricePerUnit)}
                        </div>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="font-medium">{formatCurrency(currentValue)}</div>
                      <div className={`text-sm ${assetGain >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                        {formatPercent(assetGainPercent)}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* No Assets Message */}
        {assets.length === 0 && (
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-700/50 rounded-2xl p-12 border border-gray-600/30 backdrop-blur-sm text-center">
            <div className="w-16 h-16 bg-gray-600/30 rounded-full flex items-center justify-center mx-auto mb-4">
              <DollarSign size={32} className="text-gray-400" />
            </div>
            <h3 className="text-xl font-semibold text-gray-300 mb-2">No Assets Yet</h3>
            <p className="text-gray-400">Start building your portfolio by adding your first asset.</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Dashboard; 