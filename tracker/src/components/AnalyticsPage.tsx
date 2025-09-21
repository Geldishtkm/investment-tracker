import React, { useState, useEffect } from 'react';
import { assetService } from '../services/api';
import {
  TrendingUp,
  BarChart3,
  PieChart,
  Activity,
  DollarSign,
  ArrowDownRight
} from 'lucide-react';

interface PortfolioMetrics {
  totalValue: number;
  totalInvestment: number;
  roi: number;
  roiPercentage: number;
  sharpeRatio: number;
  volatility: number;
  assetCount: number;
  topPerformer: string;
  worstPerformer: string;
}

interface AssetPerformance {
  id: number;
  name: string;
  currentValue: number;
  initialInvestment: number;
  roi: number;
  roiPercentage: number;
  weight: number;
}

interface RiskMetrics {
  sharpeRatio: number;
  volatility: number;
  maxDrawdown: number;
  beta: number;
  diversificationScore: number;
}

const AnalyticsPage: React.FC = () => {
  const [metrics, setMetrics] = useState<PortfolioMetrics | null>(null);
  const [performance, setPerformance] = useState<AssetPerformance[]>([]);
  const [riskMetrics, setRiskMetrics] = useState<RiskMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadAnalytics();
  }, []);

  const loadAnalytics = async () => {
    try {
      setLoading(true);
      setError(null);
      
      const analyticsData = await assetService.getAllAnalytics();
      
      const {
        totalValue = 0,
        totalInitialInvestment = 0,
        totalAssets = 0,
        sharpeRatio = 0,
        volatility = 0,
        maxDrawdown = 0,
        beta = 0,
        diversificationScore = 0,
        assetBreakdown = []
      } = analyticsData;
      
      const portfolioMetrics: PortfolioMetrics = {
        totalValue,
        totalInvestment: totalInitialInvestment,
        roi: totalValue - totalInitialInvestment,
        roiPercentage: totalInitialInvestment > 0 ? ((totalValue - totalInitialInvestment) / totalInitialInvestment) * 100 : 0,
        sharpeRatio,
        volatility,
        assetCount: totalAssets,
        topPerformer: assetBreakdown[0]?.name || 'N/A',
        worstPerformer: assetBreakdown[assetBreakdown.length - 1]?.name || 'N/A'
      };
      
      setMetrics(portfolioMetrics);
      
      const assetPerformance: AssetPerformance[] = assetBreakdown.map((asset: any) => {
        const currentValue = asset.currentValue || 0;
        const initialInvestment = asset.initialInvestment || 0;
        const assetRoi = currentValue - initialInvestment;
        const assetRoiPercentage = initialInvestment > 0 ? (assetRoi / initialInvestment) * 100 : 0;
        const weight = totalValue > 0 ? (currentValue / totalValue) * 100 : 0;
        
        return {
          id: asset.id,
          name: asset.name,
          currentValue,
          initialInvestment,
          roi: assetRoi,
          roiPercentage: assetRoiPercentage,
          weight
        };
      });
      
      setPerformance(assetPerformance);
      
      const risk: RiskMetrics = {
        sharpeRatio,
        volatility,
        maxDrawdown,
        beta,
        diversificationScore
      };
      
      setRiskMetrics(risk);
      
    } catch (err) {
      setError('Failed to load analytics data from backend');
      console.error('Analytics error:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  };

  const formatPercentage = (value: number) => {
    return `${value >= 0 ? '+' : ''}${(value * 100).toFixed(2)}%`;
  };

  const COLORS = ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#14b8a6'];

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
        {/* Animated Background */}
        <div className="absolute inset-0 bg-gradient-to-r from-blue-600/20 via-indigo-600/20 to-purple-600/20 animate-pulse"></div>
        <div className="absolute top-0 left-0 w-full h-full bg-[radial-gradient(circle_at_50%_50%,rgba(59,130,246,0.3),transparent_50%)]"></div>
        
        <div className="relative z-10 max-w-7xl mx-auto p-6">
          {/* Header */}
          <div className="flex items-center gap-4 mb-8">
            <div className="w-12 h-12 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
              <BarChart3 size={24} className="text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 via-indigo-400 to-purple-400 bg-clip-text text-transparent">
                Portfolio Analytics
              </h1>
              <p className="text-blue-300/70 text-sm">Loading portfolio metrics...</p>
            </div>
          </div>
          
          {/* Loading Spinner */}
          <div className="flex justify-center items-center py-20">
            <div className="relative">
              <div className="w-16 h-16 border-4 border-blue-600/30 rounded-full animate-spin"></div>
              <div className="absolute top-0 left-0 w-16 h-16 border-4 border-transparent border-t-blue-400 rounded-full animate-spin"></div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
        {/* Animated Background */}
        <div className="absolute inset-0 bg-gradient-to-r from-red-600/20 via-purple-600/20 to-pink-600/20 animate-pulse"></div>
        
        <div className="relative z-10 max-w-7xl mx-auto p-6">
          {/* Header */}
          <div className="flex items-center gap-4 mb-8">
            <div className="w-12 h-12 bg-gradient-to-br from-red-600 to-purple-600 rounded-2xl flex items-center justify-center shadow-lg shadow-red-600/40">
              <BarChart3 size={24} className="text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold bg-gradient-to-r from-red-400 via-purple-400 to-pink-400 bg-clip-text text-transparent">
                Portfolio Analytics
              </h1>
              <p className="text-red-300/70 text-sm">Error loading data</p>
            </div>
          </div>
          
          {/* Error Display */}
          <div className="text-center">
            <div className="w-24 h-24 bg-gradient-to-r from-red-600/30 to-purple-600/30 rounded-3xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-red-600/40">
              <BarChart3 size={48} className="text-red-400" />
            </div>
            <div className="text-red-400 text-xl mb-6 p-6 bg-gradient-to-r from-red-600/20 to-purple-600/20 backdrop-blur-sm border border-red-600/40 rounded-2xl">
              {error}
            </div>
            <button 
              onClick={loadAnalytics}
              className="group relative px-8 py-4 bg-gradient-to-r from-blue-600/20 to-indigo-600/20 hover:from-blue-600/30 hover:to-indigo-600/30 backdrop-blur-sm border border-blue-600/40 rounded-2xl text-blue-300 hover:text-blue-200 transition-all duration-300 hover:shadow-lg hover:shadow-blue-600/20 font-medium"
            >
              <div className="flex items-center gap-2">
                <span>↻</span>
                <span>Retry</span>
              </div>
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-r from-blue-600/20 via-indigo-600/20 to-purple-600/20 animate-pulse"></div>
      <div className="absolute top-0 left-0 w-full h-full bg-[radial-gradient(circle_at_50%_50%,rgba(59,130,246,0.3),transparent_50%)]"></div>
      
      <div className="relative z-10 max-w-7xl mx-auto p-6">
        {/* Header */}
        <div className="flex items-center gap-4 mb-8">
          <div className="w-12 h-12 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
            <BarChart3 size={24} className="text-white" />
          </div>
          <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-400 via-indigo-400 to-purple-400 bg-clip-text text-transparent">
            Portfolio Analytics
          </h1>
        </div>
        
        {/* Portfolio Overview Cards */}
        {metrics && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-green-800/50 hover:shadow-xl hover:shadow-green-800/20 transition-all duration-300 transform hover:-translate-y-1">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 bg-gradient-to-br from-green-800 to-emerald-900 rounded-2xl flex items-center justify-center shadow-lg shadow-green-800/40">
                  <DollarSign size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium">Portfolio Value</div>
              </div>
              <div className="text-3xl font-bold text-green-700 group-hover:text-green-600 transition-colors duration-300">
                {formatCurrency(metrics.totalValue)}
              </div>
              <div className="text-xs text-gray-500 mt-2">Current total value</div>
            </div>
            
            <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-blue-600/50 hover:shadow-xl hover:shadow-blue-600/20 transition-all duration-300 transform hover:-translate-y-1">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 bg-gradient-to-br from-blue-600 to-indigo-700 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
                  <Activity size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium">Investment</div>
              </div>
              <div className="text-3xl font-bold text-blue-400 group-hover:text-blue-300 transition-colors duration-300">
                {formatCurrency(metrics.totalInvestment)}
              </div>
              <div className="text-xs text-gray-500 mt-2">Initial investment</div>
            </div>
            
            <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-indigo-600/50 hover:shadow-xl hover:shadow-indigo-600/20 transition-all duration-300 transform hover:-translate-y-1">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 bg-gradient-to-br from-indigo-600 to-purple-700 rounded-2xl flex items-center justify-center shadow-lg shadow-indigo-600/40">
                  <TrendingUp size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium">ROI</div>
              </div>
              <div className={`text-3xl font-bold group-hover:scale-105 transition-transform duration-300 ${
                metrics.roiPercentage >= 0 ? 'text-green-700 group-hover:text-green-600' : 'text-red-400 group-hover:text-red-300'
              }`}>
                {formatPercentage(metrics.roiPercentage)}
              </div>
              <div className="text-xs text-gray-500 mt-2">Return on investment</div>
            </div>
            
            <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-purple-600/50 hover:shadow-xl hover:shadow-purple-600/20 transition-all duration-300 transform hover:-translate-y-1">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 bg-gradient-to-br from-purple-600 to-pink-700 rounded-2xl flex items-center justify-center shadow-lg shadow-purple-600/40">
                  <Activity size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium">Sharpe Ratio</div>
              </div>
              <div className="text-3xl font-bold text-purple-400 group-hover:text-purple-300 transition-colors duration-300">
                {metrics.sharpeRatio.toFixed(3)}
              </div>
              <div className="text-xs text-gray-500 mt-2">Risk-adjusted return</div>
            </div>
          </div>
        )}

        {/* Risk Metrics */}
        {riskMetrics && (
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-8 mb-8 shadow-xl shadow-black/20">
            <div className="flex items-center gap-3 mb-8">
              <div className="w-12 h-12 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
                <Activity size={24} className="text-white" />
              </div>
              <h2 className="text-2xl font-bold bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent">
                Risk Metrics
              </h2>
              <span className="text-sm text-blue-400 bg-blue-600/30 px-3 py-1 rounded-full border border-blue-600/40">
                Backend Calculated
              </span>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-6">
              <div className="group text-center p-6 bg-gradient-to-r from-blue-600/20 to-indigo-600/20 backdrop-blur-sm rounded-2xl border border-blue-600/30 hover:border-blue-500/50 transition-all duration-300 transform hover:-translate-y-1">
                <div className="w-14 h-14 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-blue-600/40">
                  <TrendingUp size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium mb-2">Sharpe Ratio</div>
                <div className="text-xl font-bold text-blue-400 group-hover:text-blue-300 transition-colors duration-300">
                  {riskMetrics.sharpeRatio.toFixed(3)}
                </div>
                <div className="text-xs text-gray-500 mt-2">Risk-adjusted return</div>
              </div>
              <div className="group text-center p-6 bg-gradient-to-r from-indigo-600/20 to-purple-600/20 backdrop-blur-sm rounded-2xl border border-indigo-600/30 hover:border-indigo-500/50 transition-all duration-300 transform hover:-translate-y-1">
                <div className="w-14 h-14 bg-gradient-to-br from-indigo-600 to-purple-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-indigo-600/40">
                  <Activity size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium mb-2">Volatility</div>
                <div className="text-xl font-bold text-indigo-400 group-hover:text-indigo-300 transition-colors duration-300">
                  {formatPercentage(riskMetrics.volatility)}
                </div>
                <div className="text-xs text-gray-500 mt-2">Price fluctuation</div>
              </div>
              <div className="group text-center p-6 bg-gradient-to-r from-red-600/20 to-pink-600/20 backdrop-blur-sm rounded-2xl border border-red-600/30 hover:border-red-500/50 transition-all duration-300 transform hover:-translate-y-1">
                <div className="w-14 h-14 bg-gradient-to-br from-red-600 to-pink-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-red-600/40">
                  <ArrowDownRight size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium mb-2">Max Drawdown</div>
                <div className="text-xl font-bold text-red-400 group-hover:text-red-300 transition-colors duration-300">
                  {formatPercentage(riskMetrics.maxDrawdown)}
                </div>
                <div className="text-xs text-gray-500 mt-2">Largest decline</div>
              </div>
              <div className="group text-center p-6 bg-gradient-to-r from-purple-600/20 to-pink-600/20 backdrop-blur-sm rounded-2xl border border-purple-600/30 hover:border-purple-500/50 transition-all duration-300 transform hover:-translate-y-1">
                <div className="w-14 h-14 bg-gradient-to-br from-purple-600 to-pink-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-purple-600/40">
                  <Activity size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium mb-2">Beta</div>
                <div className="text-xl font-bold text-purple-400 group-hover:text-purple-300 transition-colors duration-300">
                  {riskMetrics.beta.toFixed(3)}
                </div>
                <div className="text-xs text-gray-500 mt-2">Market correlation</div>
              </div>
              <div className="group text-center p-6 bg-gradient-to-r from-green-800/20 to-emerald-900/20 backdrop-blur-sm rounded-2xl border border-green-800/30 hover:border-green-700/50 transition-all duration-300 transform hover:-translate-y-1">
                <div className="w-14 h-14 bg-gradient-to-br from-green-800 to-emerald-900 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-green-800/40">
                  <Activity size={24} className="text-white" />
                </div>
                <div className="text-gray-400 text-sm font-medium mb-2">Diversification</div>
                <div className="text-xl font-bold text-green-700 group-hover:text-green-600 transition-colors duration-300">
                  {riskMetrics.diversificationScore.toFixed(1)}/10
                </div>
                <div className="text-xs text-gray-500 mt-2">Portfolio spread</div>
              </div>
            </div>
          </div>
        )}

        {/* Asset Performance Table */}
        {performance.length > 0 && (
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-8 mb-8 shadow-xl shadow-black/20">
            <div className="flex items-center gap-3 mb-8">
              <div className="w-12 h-12 bg-gradient-to-br from-indigo-600 to-purple-600 rounded-2xl flex items-center justify-center shadow-lg shadow-indigo-600/40">
                <BarChart3 size={24} className="text-white" />
              </div>
              <h2 className="text-2xl font-bold bg-gradient-to-r from-indigo-400 to-purple-400 bg-clip-text text-transparent">
                Asset Performance
              </h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-indigo-600/40">
                    <th className="text-left py-4 px-6 text-indigo-300 font-semibold">Asset</th>
                    <th className="text-right py-4 px-6 text-indigo-300 font-semibold">Current Value</th>
                    <th className="text-right py-4 px-6 text-indigo-300 font-semibold">Initial Investment</th>
                    <th className="text-right py-4 px-6 text-indigo-300 font-semibold">ROI</th>
                    <th className="text-right py-4 px-6 text-indigo-300 font-semibold">Weight</th>
                  </tr>
                </thead>
                <tbody>
                  {performance.map((asset) => (
                    <tr key={asset.id} className="border-b border-indigo-600/20 hover:bg-indigo-600/10 transition-colors duration-300 group">
                      <td className="py-4 px-6 font-medium text-white group-hover:text-indigo-200 transition-colors">
                        {asset.name}
                      </td>
                      <td className="text-right py-4 px-6 text-green-700 font-semibold group-hover:text-green-600 transition-colors">
                        {formatCurrency(asset.currentValue)}
                      </td>
                      <td className="text-right py-4 px-6 text-gray-300 group-hover:text-gray-200 transition-colors">
                        {formatCurrency(asset.initialInvestment)}
                      </td>
                      <td className={`text-right py-4 px-6 font-bold ${
                        asset.roiPercentage >= 0 ? 'text-green-700 group-hover:text-green-600' : 'text-red-400 group-hover:text-red-300'
                      } transition-colors`}>
                        {formatPercentage(asset.roiPercentage)}
                      </td>
                      <td className="text-right py-4 px-6 text-indigo-400 font-semibold group-hover:text-indigo-300 transition-colors">
                        {asset.weight.toFixed(1)}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Portfolio Allocation Chart */}
        {performance.length > 0 && (
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-8 shadow-xl shadow-black/20">
            <div className="flex items-center gap-3 mb-8">
              <div className="w-12 h-12 bg-gradient-to-br from-purple-600 to-pink-600 rounded-2xl flex items-center justify-center shadow-lg shadow-purple-600/40">
                <PieChart size={24} className="text-white" />
              </div>
              <h2 className="text-2xl font-bold bg-gradient-to-r from-purple-400 to-pink-400 bg-clip-text text-transparent">
                Portfolio Allocation
              </h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {performance.map((asset, index) => (
                <div key={asset.id} className="group relative p-6 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm rounded-2xl border border-purple-600/30 hover:border-purple-500/50 transition-all duration-300 transform hover:-translate-y-1 hover:shadow-xl hover:shadow-purple-600/20">
                  <div className="flex items-center justify-between mb-4">
                    <span className="font-bold text-white group-hover:text-purple-300 transition-colors duration-300">
                      {asset.name}
                    </span>
                    <span className="text-sm text-purple-400 font-semibold bg-purple-600/30 px-3 py-1 rounded-full border border-purple-600/40">
                      {asset.weight.toFixed(1)}%
                    </span>
                  </div>
                  <div className="w-full bg-gray-700/50 rounded-full h-3 mb-3 overflow-hidden">
                    <div 
                      className="h-3 rounded-full transition-all duration-500 ease-out shadow-lg"
                      style={{ 
                        width: `${Math.min(asset.weight, 100)}%`,
                        backgroundColor: COLORS[index % COLORS.length],
                        boxShadow: `0 0 20px ${COLORS[index % COLORS.length]}60`
                      }}
                    ></div>
                  </div>
                  <div className="text-sm text-gray-400 group-hover:text-gray-300 transition-colors">
                    {formatCurrency(asset.currentValue)}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AnalyticsPage; 