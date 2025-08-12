import React, { useState, useEffect, useCallback } from 'react';
import { TrendingUp, TrendingDown, Wifi, WifiOff } from 'lucide-react';
import websocketService from '../services/websocketService';

interface MarketDataUpdate {
    symbol: string;
    price: number;
    priceChange: number;
    priceChangePercent: number;
    timestamp: number;
    messageId: number;
}

interface SubscriptionStatus {
    status: string;
    message: string;
    symbol: string;
    timestamp: number;
}

/**
 * Real-time Market Data Dashboard
 * Demonstrates WebSocket functionality with live price updates
 * NOW WITH AUTOMATIC STREAMING - No manual controls needed!
 */
const RealTimeMarketDashboard: React.FC = () => {
    const [isConnected, setIsConnected] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [subscriptionStatus, setSubscriptionStatus] = useState<SubscriptionStatus[]>([]);
    const [liveData, setLiveData] = useState<Map<string, MarketDataUpdate>>(new Map());
    const [symbols, setSymbols] = useState<string[]>(['BTC', 'ETH', 'ADA', 'DOT', 'LINK']);
    const [newSymbol, setNewSymbol] = useState('');
    const [connectionAttempts, setConnectionAttempts] = useState(0);

    // Handle live data updates
    const handleMarketDataUpdate = useCallback((data: any) => {
        console.log('Received market data update:', data);
        
        // Check if this is a market data message
        if (data.type === 'market-data') {
            setLiveData(prev => {
                const newMap = new Map(prev);
                newMap.set(data.symbol, {
                    symbol: data.symbol,
                    price: data.price,
                    priceChange: data.priceChange,
                    priceChangePercent: data.priceChangePercent,
                    timestamp: data.timestamp,
                    messageId: data.messageId
                });
                return newMap;
            });
        } else if (data.type === 'subscription-status') {
            // Handle subscription status updates
            setSubscriptionStatus(prev => [data, ...prev.slice(0, 9)]); // Keep last 10
        } else if (data.type === 'connection-status') {
            // Handle connection status updates
            console.log('Connection status:', data);
        }
    }, []);

    // Initialize WebSocket connection and start streaming automatically
    useEffect(() => {
        const initializeConnection = async () => {
            try {
                console.log('Attempting to connect to WebSocket...');
                await websocketService.connect();
                console.log('WebSocket connected successfully');
                setIsConnected(true);
                
                // Subscribe to status updates
                websocketService.subscribeToStatusUpdates((status: SubscriptionStatus) => {
                    console.log('Received status update:', status);
                    setSubscriptionStatus(prev => [status, ...prev.slice(0, 9)]); // Keep last 10
                });
                
                // AUTO-START STREAMING - No manual control needed!
                console.log('Auto-starting market data streaming...');
                setIsStreaming(true);
                
                // Subscribe to all default symbols automatically
                symbols.forEach(symbol => {
                    websocketService.subscribeToMarketData(symbol, handleMarketDataUpdate);
                });
                
            } catch (error) {
                console.error('Failed to connect to WebSocket:', error);
                setConnectionAttempts(prev => prev + 1);
            }
        };

        initializeConnection();

        // Cleanup on unmount
        return () => {
            console.log('Component unmounting, disconnecting WebSocket');
            websocketService.disconnect();
        };
    }, [symbols, handleMarketDataUpdate]);

    // Add new symbol to watch
    const addSymbol = () => {
        if (newSymbol.trim() && !symbols.includes(newSymbol.trim().toUpperCase())) {
            const symbol = newSymbol.trim().toUpperCase();
            setSymbols(prev => [...prev, symbol]);
            setNewSymbol('');
            
            // Subscribe to new symbol if streaming
            if (isStreaming) {
                websocketService.subscribeToMarketData(symbol, handleMarketDataUpdate);
            }
        }
    };

    // Remove symbol from watch list
    const removeSymbol = (symbolToRemove: string) => {
        setSymbols(prev => prev.filter(s => s !== symbolToRemove));
        
        // Unsubscribe if streaming
        if (isStreaming) {
            websocketService.unsubscribeFromMarketData(symbolToRemove);
        }
        
        // Remove from live data
        setLiveData(prev => {
            const newMap = new Map(prev);
            newMap.delete(symbolToRemove);
            return newMap;
        });
    };

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
                        🔄 Real-Time Market Data
                    </h1>
                    <p className="text-gray-400 text-lg">
                        Monitor live cryptocurrency prices and market trends
                    </p>
                </div>

                {/* Connection Status */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    <div className="bg-gray-800/50 rounded-xl p-4">
                        <div className="text-2xl font-bold text-green-400">
                            {isConnected ? '🟢' : '🔴'}
                        </div>
                        <div className="text-sm text-gray-400">WebSocket</div>
                    </div>
                    
                    <div className="bg-gray-800/50 rounded-xl p-4">
                        <div className="text-2xl font-bold text-purple-400">
                            {isStreaming ? '🟢' : '⚪'}
                        </div>
                        <div className="text-sm text-gray-400">Data Stream</div>
                    </div>
                    
                    <div className="bg-gray-800/50 rounded-xl p-4">
                        <div className="text-2xl font-bold text-green-400">
                            {liveData.size}
                        </div>
                        <div className="text-sm text-gray-400">Live Symbols</div>
                    </div>
                </div>

                {connectionAttempts > 0 && !isConnected && (
                    <div className="mt-4 p-3 bg-red-900/20 border border-red-800/30 rounded-xl">
                        <p className="text-red-400 text-sm">
                            Connection attempts: {connectionAttempts}. Trying to reconnect...
                        </p>
                    </div>
                )}

                {/* Auto-Streaming Status */}
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl">
                    <h3 className="text-xl font-semibold text-gray-200 mb-4">🚀 Auto-Streaming Status</h3>
                    
                    <div className="flex items-center gap-4 mb-4">
                        <div className="flex items-center gap-2">
                            <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
                            <span className="text-gray-300">
                                {isConnected ? 'WebSocket Connected' : 'WebSocket Disconnected'}
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
                        <input
                            type="text"
                            value={newSymbol}
                            onChange={(e) => setNewSymbol(e.target.value.toUpperCase())}
                            placeholder="Add symbol (e.g., SOL)"
                            className="flex-1 px-4 py-2 bg-gray-800/50 border border-gray-700/50 rounded-xl text-white placeholder-gray-400 focus:outline-none focus:border-blue-500"
                            onKeyPress={(e) => e.key === 'Enter' && addSymbol()}
                        />
                        <button
                            onClick={addSymbol}
                            className="px-6 py-2 bg-gradient-to-r from-purple-600 to-blue-600 text-white rounded-xl font-semibold hover:from-purple-700 hover:to-blue-700 transition-all duration-300"
                        >
                            Add
                        </button>
                    </div>
                    
                    <p className="text-sm text-gray-400 mt-3">
                        💡 Market data streams automatically when connected. No manual controls needed!
                    </p>
                </div>

                {/* Live Market Data */}
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl">
                    <h3 className="text-xl font-semibold text-gray-200 mb-4">Live Market Data</h3>
                    
                    {liveData.size === 0 ? (
                        <div className="text-center py-8 text-gray-400">
                            {isStreaming ? '🔄 Waiting for live market data...' : '🚀 Connecting to WebSocket...'}
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {Array.from(liveData.values()).map((data) => (
                                <div key={data.symbol} className="bg-gray-800/50 rounded-xl p-4 border border-gray-700/50">
                                    <div className="flex items-center justify-between mb-2">
                                        <h4 className="text-lg font-semibold text-white">{data.symbol}</h4>
                                        <button
                                            onClick={() => removeSymbol(data.symbol)}
                                            className="text-red-400 hover:text-red-300 text-sm"
                                        >
                                            ×
                                        </button>
                                    </div>
                                    
                                    <div className="text-2xl font-bold text-white mb-2">
                                        {formatPrice(data.price)}
                                    </div>
                                    
                                    <div className="flex items-center gap-2 mb-2">
                                        {data.priceChange >= 0 ? (
                                            <TrendingUp className="w-4 h-4 text-green-400" />
                                        ) : (
                                            <TrendingDown className="w-4 h-4 text-red-400" />
                                        )}
                                        <span className={`text-sm font-medium ${
                                            data.priceChange >= 0 ? 'text-green-400' : 'text-red-400'
                                        }`}>
                                            {data.priceChange >= 0 ? '+' : ''}{formatPrice(data.priceChange)} 
                                            ({data.priceChangePercent >= 0 ? '+' : ''}{data.priceChangePercent.toFixed(2)}%)
                                        </span>
                                    </div>
                                    
                                    <div className="text-xs text-gray-400">
                                        Last updated: {formatTimestamp(data.timestamp)}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Subscription Status */}
                {subscriptionStatus.length > 0 && (
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
                )}
            </div>
        </div>
    );
};

export default RealTimeMarketDashboard;
