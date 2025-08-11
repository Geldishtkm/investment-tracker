import React, { useState, useEffect, useRef } from 'react';
import { 
  LineChart, 
  Line, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer,
  Area,
  AreaChart
} from 'recharts';
import { PriceHistoryPoint, Coin } from '../types';
import { priceHistoryService } from '../services/api';
import { TrendingUp, TrendingDown, Calendar, DollarSign, RefreshCw } from 'lucide-react';

interface PriceHistoryChartProps {
  selectedCoin: Coin | null;
  onClose: () => void;
}

const PriceHistoryChart: React.FC<PriceHistoryChartProps> = ({ selectedCoin, onClose }) => {
  const [priceHistory, setPriceHistory] = useState<PriceHistoryPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [timeRange, setTimeRange] = useState<number>(30); // days

  // Fetch price history when coin changes
  useEffect(() => {
    if (selectedCoin) {
      fetchPriceHistory();
    }
  }, [selectedCoin, timeRange]);

  const fetchPriceHistory = async () => {
    if (!selectedCoin) return;

    try {
      setLoading(true);
      setError(null);
      
      const startTime = Date.now();
      const data = await priceHistoryService.getPriceHistoryWithRange(selectedCoin.id, timeRange);
      const endTime = Date.now();
      
      if (data && data.length > 0) {
        setPriceHistory(data);
      } else {
        setError('No price history data available');
      }
    } catch (err) {
      setError('Failed to fetch price history');
      console.error('Error fetching price history:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    fetchPriceHistory();
  };

  const getPriceChange = () => {
    if (priceHistory.length < 2) return { change: 0, percentage: 0, isPositive: true };
    
    const firstPrice = priceHistory[0].price;
    const lastPrice = priceHistory[priceHistory.length - 1].price;
    const change = lastPrice - firstPrice;
    const percentage = (change / firstPrice) * 100;
    
    return {
      change,
      percentage,
      isPositive: change >= 0
    };
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 6
    }).format(price);
  };

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-gray-800 border border-gray-600 rounded-lg p-3 shadow-lg">
          <p className="text-gray-300 text-sm">{label}</p>
          <p className="text-white font-semibold">
            {formatPrice(payload[0].value)}
          </p>
        </div>
      );
    }
    return null;
  };

  if (!selectedCoin) {
    return null;
  }

  const priceChange = getPriceChange();

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-gray-900 border border-gray-700 rounded-2xl w-full max-w-6xl max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-700">
          <div className="flex items-center gap-4">
            {selectedCoin.image && (
              <img 
                src={selectedCoin.image} 
                alt={selectedCoin.name}
                className="w-10 h-10 rounded-full"
              />
            )}
            <div>
              <h2 className="text-2xl font-bold text-white">{selectedCoin.name}</h2>
              <p className="text-gray-400">{selectedCoin.symbol.toUpperCase()}</p>
            </div>
          </div>
          
          <div className="flex items-center gap-4">
            {/* Price Change Indicator */}
            <div className="flex items-center gap-2">
              {priceChange.isPositive ? (
                <TrendingUp className="w-5 h-5 text-green-400" />
              ) : (
                <TrendingDown className="w-5 h-5 text-red-400" />
              )}
              <span className={`font-semibold ${priceChange.isPositive ? 'text-green-400' : 'text-red-400'}`}>
                {priceChange.isPositive ? '+' : ''}{formatPrice(priceChange.change)} ({priceChange.isPositive ? '+' : ''}{priceChange.percentage.toFixed(2)}%)
              </span>
            </div>
            
            <button
              onClick={handleRefresh}
              disabled={loading}
              className="p-2 rounded-lg bg-gray-700 hover:bg-gray-600 transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-5 h-5 text-white ${loading ? 'animate-spin' : ''}`} />
            </button>
            
            <button
              onClick={onClose}
              className="p-2 rounded-lg bg-red-600 hover:bg-red-700 transition-colors"
            >
              <span className="text-white font-semibold">×</span>
            </button>
          </div>
        </div>

        {/* Controls */}
        <div className="flex items-center justify-between p-4 bg-gray-800/50 border-b border-gray-700">
          <div className="flex items-center gap-4">
            <Calendar className="w-5 h-5 text-gray-400" />
            <select
              value={timeRange}
              onChange={(e) => setTimeRange(Number(e.target.value))}
              className="bg-gray-700 border border-gray-600 rounded-lg px-3 py-2 text-white focus:ring-2 focus:ring-green-500 focus:border-transparent"
            >
              <option value={7}>7 Days</option>
              <option value={14}>14 Days</option>
              <option value={30}>30 Days</option>
              <option value={90}>90 Days</option>
              <option value={365}>1 Year</option>
            </select>
          </div>
          
          <div className="flex items-center gap-2 text-gray-400">
            <DollarSign className="w-4 h-4" />
            <span className="text-sm">
              Current: {selectedCoin.current_price ? formatPrice(selectedCoin.current_price) : 'N/A'}
            </span>
          </div>
        </div>

        {/* Chart Container */}
        <div className="p-6">
          {loading ? (
            <div className="flex items-center justify-center h-96">
              <div className="flex items-center gap-3">
                <RefreshCw className="w-6 h-6 animate-spin text-green-400" />
                <span className="text-white">Loading price history...</span>
              </div>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center h-96">
              <div className="text-center">
                <div className="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-4">
                  <TrendingDown className="w-8 h-8 text-red-400" />
                </div>
                <h3 className="text-xl font-semibold text-white mb-2">Error Loading Data</h3>
                <p className="text-gray-400 mb-4">{error}</p>
                <button
                  onClick={fetchPriceHistory}
                  className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg transition-colors"
                >
                  Try Again
                </button>
              </div>
            </div>
          ) : priceHistory.length === 0 ? (
            <div className="flex items-center justify-center h-96">
              <div className="text-center">
                <div className="w-16 h-16 bg-gray-700/50 rounded-full flex items-center justify-center mx-auto mb-4">
                  <TrendingUp className="w-8 h-8 text-gray-400" />
                </div>
                <h3 className="text-xl font-semibold text-white mb-2">No Data Available</h3>
                <p className="text-gray-400">No price history data found for this coin.</p>
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              {/* Main Chart */}
              <div className="bg-gray-800/30 rounded-xl p-6 border border-gray-700/50">
                <h3 className="text-lg font-semibold text-white mb-4">Price History</h3>
                <ResponsiveContainer width="100%" height={400}>
                  <AreaChart data={priceHistory}>
                    <defs>
                      <linearGradient id="priceGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                    <XAxis 
                      dataKey="timestamp" 
                      stroke="#9ca3af"
                      fontSize={12}
                      tickLine={false}
                      axisLine={false}
                      tickFormatter={(value) => new Date(value).toLocaleDateString()}
                    />
                    <YAxis 
                      stroke="#9ca3af"
                      fontSize={12}
                      tickLine={false}
                      axisLine={false}
                      tickFormatter={(value) => `$${value.toFixed(2)}`}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Area
                      type="monotone"
                      dataKey="price"
                      stroke="#10b981"
                      strokeWidth={2}
                      fill="url(#priceGradient)"
                      dot={false}
                      activeDot={{ r: 6, fill: '#10b981', stroke: '#fff', strokeWidth: 2 }}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>

              {/* Data Table */}
              <div className="bg-gray-800/30 rounded-xl p-6 border border-gray-700/50">
                <h3 className="text-lg font-semibold text-white mb-4">Recent Data Points</h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-700">
                        <th className="text-left py-3 px-4 text-gray-400 font-medium">Date</th>
                        <th className="text-right py-3 px-4 text-gray-400 font-medium">Price</th>
                      </tr>
                    </thead>
                    <tbody>
                      {priceHistory.slice(-10).reverse().map((point, index) => (
                        <tr key={index} className="border-b border-gray-700/30 hover:bg-gray-700/20">
                          <td className="py-3 px-4 text-gray-300">{new Date(point.timestamp).toLocaleDateString()}</td>
                          <td className="py-3 px-4 text-right text-white font-medium">
                            {formatPrice(point.price)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PriceHistoryChart; 