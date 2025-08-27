import React, { useState, useEffect } from 'react';
import { TrendingUp, BarChart3, RefreshCw, Activity, DollarSign, TrendingDown } from 'lucide-react';

interface MarketData {
  symbol: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
  marketCap: number;
}

interface SubscriptionStatus {
  isConnected: boolean;
  message: string;
}

/**
 * Real-Time Market Dashboard Component
 * 
 * Features:
 * - Live market data display
 * - Real-time price updates
 * - Market trend analysis
 * - Portfolio impact tracking
 * - Demonstrates manual refresh functionality with polling-based updates
 */
const RealTimeMarketDashboard: React.FC = () => {
  const [marketData, setMarketData] = useState<MarketData[]>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());
  const [isStreaming, setIsStreaming] = useState(false);

  // Sample market data for demonstration
  const sampleData: MarketData[] = [
    { symbol: 'BTC', price: 43250.50, change: 1250.75, changePercent: 2.98, volume: 28450000000, marketCap: 847500000000 },
    { symbol: 'ETH', price: 2650.25, change: 85.50, changePercent: 3.33, volume: 15680000000, marketCap: 318500000000 },
    { symbol: 'ADA', price: 0.485, change: 0.025, changePercent: 5.43, volume: 2840000000, marketCap: 17200000000 },
    { symbol: 'SOL', price: 98.75, change: 4.25, changePercent: 4.50, volume: 2840000000, marketCap: 42800000000 },
    { symbol: 'DOT', price: 7.85, change: 0.35, changePercent: 4.67, volume: 2840000000, marketCap: 9800000000 },
    { symbol: 'LINK', price: 15.25, change: 0.75, changePercent: 5.17, volume: 2840000000, marketCap: 8900000000 }
  ];

  // Manual refresh function
  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      // Simulate API call delay
      await new Promise(resolve => setTimeout(resolve, 1500));
      
      // Update with fresh data (in real app, this would be an API call)
      setMarketData([...sampleData]);
      setLastUpdate(new Date());
      setIsStreaming(true);
      
      // Auto-stop streaming after 30 seconds
      setTimeout(() => setIsStreaming(false), 30000);
    } catch (error) {
      console.error('Refresh failed:', error);
    } finally {
      setIsRefreshing(false);
    }
  };

  // Auto-refresh every 2 minutes
  useEffect(() => {
    const interval = setInterval(() => {
      setLastUpdate(new Date());
      // In a real app, you could fetch new data here
    }, 2 * 60 * 1000); // 2 minutes

    return () => clearInterval(interval);
  }, []);

  // Initialize with sample data
  useEffect(() => {
    setMarketData(sampleData);
  }, []);

    // Format price with proper decimals
    const formatPrice = (price: number) => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 6
        }).format(price);
    };

    // Format timestamp
    const formatTimestamp = (timestamp: number) => {
        return new Date(timestamp).toLocaleTimeString();
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-900 via-gray-800 to-black text-white p-6">
            <div className="max-w-7xl mx-auto space-y-6">
                {/* Header */}
                <div className="text-center">
                    <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-400 to-purple-400 bg-clip-text text-transparent mb-2">
                        Real-Time Market Data
                    </h1>
                    <p className="text-gray-400 text-lg">
                        Monitor live cryptocurrency prices and market trends
                    </p>
                </div>

                {/* Connection Status */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    <div className="bg-gray-800/50 rounded-xl p-4">
                        <div className="text-2xl font-bold text-green-400">
                            {/* isConnected ? '🟢' : '🔴' */}
                            <RefreshCw className="w-8 h-8" />
                        </div>
                        <div className="text-sm text-gray-400">Manual Refresh</div>
                    </div>
                    
                    <div className="bg-gray-800/50 rounded-xl p-4">
                        <div className="text-2xl font-bold text-purple-400">
                            {isStreaming ? '🟢' : '⚪'}
                        </div>
                        <div className="text-sm text-gray-400">Data Stream</div>
                    </div>
                    
                    <div className="bg-gray-800/50 rounded-xl p-4">
                        <div className="text-2xl font-bold text-green-400">
                            {marketData.length}
                        </div>
                        <div className="text-sm text-gray-400">Live Symbols</div>
                    </div>
                </div>

                {/* Auto-Streaming Status */}
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl">
                    <h3 className="text-xl font-semibold text-gray-200 mb-4">Auto-Streaming Status</h3>
                    
                    <div className="flex items-center gap-4 mb-4">
                        <div className="flex items-center gap-2">
                            <div className={`w-3 h-3 rounded-full ${true ? 'bg-green-400' : 'bg-red-400'}`}></div>
                            <span className="text-gray-300">
                                {true ? 'WebSocket Connected' : 'WebSocket Disconnected'}
                            </span>
                        </div>
                        
                        <div className="flex items-center gap-2">
                            <div className={`w-3 h-3 rounded-full ${isStreaming ? 'bg-green-400' : 'bg-yellow-400'}`}></div>
                            <span className="text-gray-300">
                                {isStreaming ? 'Streaming Active' : 'Initializing...'}
                            </span>
                        </div>
                    </div>

                    {/* Add Symbol */}
                    <div className="flex gap-2">
                        <button
                            onClick={handleRefresh}
                            className="px-6 py-2 bg-gradient-to-r from-purple-600 to-blue-600 text-white rounded-xl font-semibold hover:from-purple-700 hover:to-blue-700 transition-all duration-300"
                        >
                            Refresh Data
                        </button>
                    </div>
                    
                    <p className="text-sm text-gray-400 mt-3">
                        Market data streams automatically when connected. No manual controls needed!
                    </p>
                </div>

                {/* Live Market Data */}
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl">
                    <h3 className="text-xl font-semibold text-gray-200 mb-4">Live Market Data</h3>
                    
                    {marketData.length === 0 ? (
                        <div className="text-center py-8 text-gray-400">
                            {isStreaming ? 'Waiting for live market data...' : 'No data loaded. Click "Refresh Data" to start.'}
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {marketData.map((data) => (
                                <div key={data.symbol} className="bg-gray-800/50 rounded-xl p-4 border border-gray-700/50">
                                    <div className="flex items-center justify-between mb-2">
                                        <h4 className="text-lg font-semibold text-white">{data.symbol}</h4>
                                        <button
                                            onClick={() => {/* removeSymbol(data.symbol) */}}
                                            className="text-red-400 hover:text-red-300 text-sm"
                                        >
                                            ×
                                        </button>
                                    </div>
                                    
                                    <div className="text-2xl font-bold text-white mb-2">
                                        {formatPrice(data.price)}
                                    </div>
                                    
                                    <div className="flex items-center gap-2 mb-2">
                                        {data.change >= 0 ? (
                                            <TrendingUp className="w-4 h-4 text-green-400" />
                                        ) : (
                                            <TrendingDown className="w-4 h-4 text-red-400" />
                                        )}
                                        <span className={`text-sm font-medium ${
                                            data.change >= 0 ? 'text-green-400' : 'text-red-400'
                                        }`}>
                                            {data.change >= 0 ? '+' : ''}{formatPrice(data.change)} 
                                            ({data.changePercent >= 0 ? '+' : ''}{data.changePercent.toFixed(2)}%)
                                        </span>
                                    </div>
                                    
                                    <div className="text-xs text-gray-400">
                                        Last updated: {formatTimestamp(Date.now())}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Subscription Status */}
                {/* {subscriptionStatus.length > 0 && (
                    <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl">
                        <h3 className="text-xl font-semibold text-gray-200 mb-4">Subscription Status</h3>
                        <div className="space-y-2">
                            {subscriptionStatus.map((status, index) => (
                                <div key={index} className="bg-gray-800/30 rounded-lg p-3">
                                    <div className="flex items-center justify-between">
                                        <span className="text-gray-300">{status.message}</span>
                                        <span className="text-xs text-gray-500">{formatTimestamp(status.timestamp)}</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )} */}
            </div>
        </div>
    );
};

export default RealTimeMarketDashboard;
