import React, { useState, useEffect } from 'react';
import { 
  TrendingDown, 
  AlertTriangle, 
  BarChart3, 
  Calculator, 
  Target,
  Shield,
  Activity,
  PieChart,
  LineChart,
  Zap,
  Info
} from 'lucide-react';

interface VaRCalculation {
  id?: number;
  userId: string;
  calculationDate: string;
  confidenceLevel: number;
  timeHorizon: number;
  portfolioValue: number;
  historicalVaR: number;
  parametricVaR: number;
  monteCarloVaR: number;
  conditionalVaR: number;
  volatility: number;
  skewness: number;
  kurtosis: number;
  expectedReturn: number;
  assetWeights: Record<string, number>;
  assetReturns: Record<string, number>;
  historicalReturns: number[];
}

interface VaRSummary {
  portfolioValue: number;
  confidenceLevel: number;
  timeHorizon: number;
  riskLevel: string;
  varResults: {
    historicalVaR: number;
    parametricVaR: number;
    monteCarloVaR: number;
    conditionalVaR: number;
    historicalVaRPercentage: number;
    parametricVaRPercentage: number;
    monteCarloVaRPercentage: number;
  };
  riskMetrics: {
    volatility: number;
    skewness: number;
    kurtosis: number;
    expectedReturn: number;
  };
}

const VaRDashboard: React.FC = () => {
  const [varCalculation, setVarCalculation] = useState<VaRCalculation | null>(null);
  const [varSummary, setVarSummary] = useState<VaRSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confidenceLevel, setConfidenceLevel] = useState(0.95);
  const [timeHorizon, setTimeHorizon] = useState(1);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    calculateVaR();
  }, [confidenceLevel, timeHorizon]);

  const calculateVaR = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const token = localStorage.getItem('token');
      if (!token) {
        setError('Authentication required');
        return;
      }

      const response = await fetch(`/api/var/calculate?confidenceLevel=${confidenceLevel}&timeHorizon=${timeHorizon}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setVarSummary(data);
      
      // Also fetch detailed calculation
      const detailedResponse = await fetch(`/api/var/portfolio?confidenceLevel=${confidenceLevel}&timeHorizon=${timeHorizon}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (detailedResponse.ok) {
        const detailedData = await detailedResponse.json();
        setVarCalculation(detailedData);
      }
      
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to calculate VaR');
    } finally {
      setLoading(false);
    }
  };

  const getRiskLevelColor = (riskLevel: string) => {
    switch (riskLevel?.toUpperCase()) {
      case 'HIGH': return 'text-red-400 bg-red-600/20 border-red-600/30';
      case 'MEDIUM': return 'text-yellow-400 bg-yellow-600/20 border-yellow-600/30';
      case 'LOW': return 'text-green-400 bg-green-600/20 border-green-600/30';
      default: return 'text-gray-400 bg-gray-600/20 border-gray-600/30';
    }
  };

  const getConfidenceLevelLabel = (level: number) => {
    return `${(level * 100).toFixed(0)}%`;
  };

  const getTimeHorizonLabel = (horizon: number) => {
    if (horizon === 1) return '1 Day';
    if (horizon === 7) return '1 Week';
    if (horizon === 30) return '1 Month';
    if (horizon === 90) return '3 Months';
    if (horizon === 365) return '1 Year';
    return `${horizon} Days`;
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-blue-300 text-lg">Calculating Value at Risk...</p>
          <p className="text-blue-200/70 text-sm">Running Monte Carlo simulations and risk analysis</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black flex items-center justify-center">
        <div className="text-center p-8 bg-gradient-to-r from-red-600/20 to-red-700/20 backdrop-blur-sm border border-red-600/40 rounded-2xl">
          <AlertTriangle className="w-16 h-16 text-red-400 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-red-200 mb-2">Error Calculating VaR</h2>
          <p className="text-red-300 mb-4">{error}</p>
          <button
            onClick={calculateVaR}
            className="px-6 py-3 bg-gradient-to-r from-red-600 to-red-700 rounded-xl text-white font-medium hover:from-red-700 hover:to-red-800 transition-all duration-300"
          >
            Retry Calculation
          </button>
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
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-3 mb-4">
            <div className="w-16 h-16 bg-gradient-to-br from-red-600 to-red-700 rounded-2xl flex items-center justify-center shadow-lg shadow-red-600/40">
              <TrendingDown size={32} className="text-white" />
            </div>
            <div>
              <h1 className="text-4xl font-bold bg-gradient-to-r from-red-400 to-red-500 bg-clip-text text-transparent">
                Value at Risk (VaR) Dashboard
              </h1>
              <p className="text-red-300/70 text-lg">Professional Portfolio Risk Analysis</p>
            </div>
          </div>
          
          <p className="text-blue-200/80 max-w-3xl mx-auto">
            Advanced risk management using Historical Simulation, Parametric VaR, and Monte Carlo methods. 
            Monitor your portfolio's potential losses with institutional-grade risk metrics.
          </p>
          
          {/* Data Source Indicator */}
          <div className="mt-6 p-4 bg-gradient-to-r from-green-600/20 to-emerald-600/20 border border-green-500/30 rounded-xl max-w-2xl mx-auto">
            <div className="flex items-center gap-3 justify-center">
              <div className="w-8 h-8 bg-green-500/20 rounded-full flex items-center justify-center">
                <BarChart3 size={16} className="text-green-400" />
              </div>
              <div className="text-center">
                <p className="text-green-300 font-medium">📊 Real Market Data Active</p>
                <p className="text-green-200/70 text-sm">VaR calculations use actual historical prices from CoinGecko API</p>
              </div>
            </div>
          </div>
        </div>

        {/* Controls */}
        <div className="bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 mb-8">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">Confidence Level</label>
              <select
                value={confidenceLevel}
                onChange={(e) => setConfidenceLevel(parseFloat(e.target.value))}
                className="w-full px-4 py-3 bg-gradient-to-r from-gray-800/80 to-gray-700/80 border border-gray-600/50 rounded-xl text-black focus:outline-none focus:ring-2 focus:ring-red-500/50 focus:border-red-500/50"
              >
                <option value={0.80}>80%</option>
                <option value={0.85}>85%</option>
                <option value={0.90}>90%</option>
                <option value={0.95}>95%</option>
                <option value={0.975}>97.5%</option>
                <option value={0.99}>99%</option>
              </select>
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">Time Horizon</label>
              <select
                value={timeHorizon}
                onChange={(e) => setTimeHorizon(parseInt(e.target.value))}
                className="w-full px-4 py-3 bg-gradient-to-r from-gray-800/80 to-gray-700/80 border border-gray-600/50 rounded-xl text-black focus:outline-none focus:ring-2 focus:ring-red-500/50 focus:border-red-500/50"
              >
                <option value={1}>1 Day</option>
                <option value={7}>1 Week</option>
                <option value={30}>1 Month</option>
                <option value={90}>3 Months</option>
                <option value={365}>1 Year</option>
              </select>
            </div>
            
            <div className="flex items-end">
              <button
                onClick={calculateVaR}
                className="w-full px-6 py-3 bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 rounded-xl text-white font-medium transition-all duration-300 flex items-center justify-center gap-2"
              >
                <Calculator size={20} />
                Recalculate VaR
              </button>
            </div>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex space-x-1 mb-8 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-2">
          {[
            { id: 'overview', label: 'Overview', icon: BarChart3 },
            { id: 'methodologies', label: 'VaR Methods', icon: Calculator },
            { id: 'risk-metrics', label: 'Risk Metrics', icon: Activity },
            { id: 'portfolio', label: 'Portfolio', icon: PieChart }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl font-medium transition-all duration-300 ${
                activeTab === tab.id
                  ? 'bg-gradient-to-r from-red-600/20 to-red-700/20 text-red-300 border border-red-600/40'
                  : 'text-gray-400 hover:text-gray-300 hover:bg-gray-700/50'
              }`}
            >
              <tab.icon size={18} />
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab Content */}
        {activeTab === 'overview' && varSummary && (
          <div className="space-y-6">
            {/* Risk Level Card */}
            <div className="bg-gradient-to-r from-red-600/20 to-red-700/20 backdrop-blur-sm border border-red-600/40 rounded-2xl p-6">
              <div className="flex items-center gap-4 mb-4">
                <div className="w-12 h-12 bg-gradient-to-br from-red-600 to-red-700 rounded-xl flex items-center justify-center">
                  <Shield size={24} className="text-white" />
                </div>
                <div>
                  <h3 className="text-xl font-semibold text-white">Portfolio Risk Assessment</h3>
                  <p className="text-red-300/70">Current risk level based on VaR calculations</p>
                </div>
              </div>
              
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="text-center">
                  <div className={`inline-flex items-center gap-2 px-4 py-2 rounded-full border ${getRiskLevelColor(varSummary.riskLevel)}`}>
                    <Target size={16} />
                    <span className="font-medium">{varSummary.riskLevel}</span>
                  </div>
                  <p className="text-sm text-gray-400 mt-2">Risk Level</p>
                </div>
                
                <div className="text-center">
                  <div className="text-2xl font-bold text-white">
                    ${varSummary.portfolioValue.toLocaleString()}
                  </div>
                  <p className="text-sm text-gray-400">Portfolio Value</p>
                </div>
                
                <div className="text-center">
                  <div className="text-2xl font-bold text-red-400">
                    {getConfidenceLevelLabel(varSummary.confidenceLevel)}
                  </div>
                  <p className="text-sm text-gray-400">Confidence Level</p>
                </div>
              </div>
            </div>

            {/* VaR Results Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              {[
                {
                  title: 'Historical VaR',
                  value: varSummary.varResults.historicalVaR,
                  percentage: varSummary.varResults.historicalVaRPercentage,
                  icon: BarChart3,
                  color: 'from-blue-600/20 to-blue-700/20',
                  borderColor: 'border-blue-600/40'
                },
                {
                  title: 'Parametric VaR',
                  value: varSummary.varResults.parametricVaR,
                  percentage: varSummary.varResults.parametricVaRPercentage,
                  icon: Calculator,
                  color: 'from-green-600/20 to-green-700/20',
                  borderColor: 'border-green-600/40'
                },
                {
                  title: 'Monte Carlo VaR',
                  value: varSummary.varResults.monteCarloVaR,
                  percentage: varSummary.varResults.monteCarloVaRPercentage,
                  icon: Zap,
                  color: 'from-purple-600/20 to-purple-700/20',
                  borderColor: 'border-purple-600/40'
                },
                {
                  title: 'Conditional VaR',
                  value: varSummary.varResults.conditionalVaR,
                  percentage: (varSummary.varResults.conditionalVaR / varSummary.portfolioValue) * 100,
                  icon: AlertTriangle,
                  color: 'from-orange-600/20 to-orange-700/20',
                  borderColor: 'border-orange-600/40'
                }
              ].map((metric, index) => (
                <div key={index} className={`bg-gradient-to-r ${metric.color} backdrop-blur-sm border ${metric.borderColor} rounded-2xl p-6`}>
                  <div className="flex items-center gap-3 mb-4">
                    <div className={`w-10 h-10 bg-gradient-to-br ${metric.color.replace('/20', '/40')} rounded-xl flex items-center justify-center`}>
                      <metric.icon size={20} className="text-white" />
                    </div>
                    <div className="text-sm text-gray-300 font-medium">{metric.title}</div>
                  </div>
                  
                  <div className="text-2xl font-bold text-white mb-2">
                    ${metric.value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </div>
                  
                  <div className="text-sm text-gray-400">
                    {metric.percentage.toFixed(2)}% of Portfolio
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'methodologies' && varCalculation && (
          <div className="space-y-6">
            <div className="bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6">
              <h3 className="text-xl font-semibold text-white mb-4">VaR Methodology Comparison</h3>
              <p className="text-gray-300 mb-6">
                Compare different Value at Risk calculation methods to understand your portfolio's risk profile.
              </p>
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div className="space-y-4">
                  <h4 className="text-lg font-medium text-blue-300">Historical Simulation</h4>
                  <p className="text-gray-400 text-sm">
                    Uses actual historical returns to estimate potential losses. Most accurate for stable markets.
                  </p>
                  <div className="text-2xl font-bold text-blue-400">
                    ${varCalculation.historicalVaR.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </div>
                </div>
                
                <div className="space-y-4">
                  <h4 className="text-lg font-medium text-green-300">Parametric VaR</h4>
                  <p className="text-gray-400 text-sm">
                    Assumes normal distribution of returns. Fast calculation but may underestimate tail risk.
                  </p>
                  <div className="text-2xl font-bold text-green-400">
                    ${varCalculation.parametricVaR.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </div>
                </div>
                
                <div className="space-y-4">
                  <h4 className="text-lg font-medium text-purple-300">Monte Carlo Simulation</h4>
                  <p className="text-gray-400 text-sm">
                    Generates thousands of possible scenarios. Most comprehensive but computationally intensive.
                  </p>
                  <div className="text-2xl font-bold text-purple-400">
                    ${varCalculation.monteCarloVaR.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </div>
                </div>
                
                <div className="space-y-4">
                  <h4 className="text-lg font-medium text-orange-300">Conditional VaR</h4>
                  <p className="text-gray-400 text-sm">
                    Expected loss beyond VaR threshold. Provides insight into extreme loss scenarios.
                  </p>
                  <div className="text-2xl font-bold text-orange-400">
                    ${varCalculation.conditionalVaR.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'risk-metrics' && varCalculation && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              {[
                {
                  title: 'Volatility',
                  value: varCalculation.volatility,
                  description: 'Standard deviation of returns',
                  icon: Activity,
                  color: 'from-blue-600/20 to-blue-700/20',
                  borderColor: 'border-blue-600/40',
                  format: (val: number) => `${(val * 100).toFixed(2)}%`
                },
                {
                  title: 'Skewness',
                  value: varCalculation.skewness,
                  description: 'Distribution asymmetry',
                  icon: BarChart3,
                  color: 'from-green-600/20 to-green-700/20',
                  borderColor: 'border-green-600/40',
                  format: (val: number) => val.toFixed(3)
                },
                {
                  title: 'Kurtosis',
                  value: varCalculation.kurtosis,
                  description: 'Distribution peakedness',
                  icon: LineChart,
                  color: 'from-purple-600/20 to-purple-700/20',
                  borderColor: 'border-purple-600/40',
                  format: (val: number) => val.toFixed(2)
                },
                {
                  title: 'Expected Return',
                  value: varCalculation.expectedReturn,
                  description: 'Annualized return expectation',
                  icon: TrendingDown,
                  color: 'from-orange-600/20 to-orange-700/20',
                  borderColor: 'border-orange-600/40',
                  format: (val: number) => `${(val * 100).toFixed(2)}%`
                }
              ].map((metric, index) => (
                <div key={index} className={`bg-gradient-to-r ${metric.color} backdrop-blur-sm border ${metric.borderColor} rounded-2xl p-6`}>
                  <div className="flex items-center gap-3 mb-4">
                    <div className={`w-10 h-10 bg-gradient-to-br ${metric.color.replace('/20', '/40')} rounded-xl flex items-center justify-center`}>
                      <metric.icon size={20} className="text-white" />
                    </div>
                    <div>
                      <div className="text-sm text-gray-300 font-medium">{metric.title}</div>
                      <div className="text-xs text-gray-400">{metric.description}</div>
                    </div>
                  </div>
                  
                  <div className="text-2xl font-bold text-white mb-2">
                    {metric.format(metric.value)}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'portfolio' && varCalculation && (
          <div className="space-y-6">
            <div className="bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6">
              <h3 className="text-xl font-semibold text-white mb-4">Portfolio Composition & Weights</h3>
              
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div>
                  <h4 className="text-lg font-medium text-blue-300 mb-4">Asset Allocation</h4>
                  <div className="space-y-3">
                    {Object.entries(varCalculation.assetWeights).map(([asset, weight]) => (
                      <div key={asset} className="flex items-center justify-between p-3 bg-gray-800/50 rounded-xl">
                        <span className="text-white font-medium">{asset}</span>
                        <span className="text-blue-400 font-mono">{(weight * 100).toFixed(2)}%</span>
                      </div>
                    ))}
                  </div>
                </div>
                
                <div>
                  <h4 className="text-lg font-medium text-green-300 mb-4">Calculation Details</h4>
                  <div className="space-y-3">
                    <div className="flex items-center justify-between p-3 bg-gray-800/50 rounded-xl">
                      <span className="text-gray-300">Confidence Level</span>
                      <span className="text-green-400 font-mono">{getConfidenceLevelLabel(varCalculation.confidenceLevel)}</span>
                    </div>
                    <div className="flex items-center justify-between p-3 bg-gray-800/50 rounded-xl">
                      <span className="text-gray-300">Time Horizon</span>
                      <span className="text-green-400 font-mono">{getTimeHorizonLabel(varCalculation.timeHorizon)}</span>
                    </div>
                    <div className="flex items-center justify-between p-3 bg-gray-800/50 rounded-xl">
                      <span className="text-gray-300">Calculation Date</span>
                      <span className="text-green-400 font-mono">
                        {new Date(varCalculation.calculationDate).toLocaleDateString()}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Info Section */}
        <div className="mt-12 bg-gradient-to-r from-blue-600/10 to-indigo-600/10 backdrop-blur-sm border border-blue-600/20 rounded-2xl p-6">
          <div className="flex items-start gap-4">
            <div className="w-10 h-10 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-xl flex items-center justify-center flex-shrink-0">
              <Info size={20} className="text-white" />
            </div>
            <div>
              <h4 className="text-lg font-semibold text-blue-300 mb-2">About Value at Risk (VaR)</h4>
              <p className="text-blue-200/80 text-sm leading-relaxed">
                Value at Risk (VaR) is a statistical measure that quantifies the potential loss in value of a portfolio 
                over a defined period for a given confidence interval. This dashboard provides three different VaR methodologies:
              </p>
              <ul className="text-blue-200/70 text-sm mt-3 space-y-1">
                <li>• <strong>Historical VaR:</strong> Based on actual historical price movements</li>
                <li>• <strong>Parametric VaR:</strong> Assumes normal distribution of returns</li>
                <li>• <strong>Monte Carlo VaR:</strong> Uses random simulations to model portfolio behavior</li>
              </ul>
              <p className="text-blue-200/60 text-xs mt-3">
                <strong>Note:</strong> VaR is a risk measure, not a guarantee. Past performance does not predict future results.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VaRDashboard;
