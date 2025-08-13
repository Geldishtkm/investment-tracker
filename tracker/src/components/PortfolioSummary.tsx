import React, { useState, useEffect } from 'react';
import { DollarSign, Package, BarChart3, Target, Activity } from 'lucide-react';
import { AssetWithPrice } from '../types';
import websocketService from '../services/websocketService';

interface PortfolioSummaryProps {
  assets: AssetWithPrice[];
}

const PortfolioSummary: React.FC<PortfolioSummaryProps> = ({ assets }) => {
  const [isConnected, setIsConnected] = useState(false);
  const [livePrices, setLivePrices] = useState<Map<string, number>>(new Map());
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());

  const assetCount = assets.length;
  const totalValue = assets.reduce((sum, asset) => {
    const currentPrice = livePrices.get(asset.name) || asset.currentPrice || asset.pricePerUnit;
    return sum + (asset.quantity * currentPrice);
  }, 0);
  const averageValue = assetCount > 0 ? totalValue / assetCount : 0;
  
  // Calculate additional statistics
  const totalQuantity = assets.reduce((sum, asset) => sum + asset.quantity, 0);
  const averagePrice = assets.length > 0 
    ? assets.reduce((sum, asset) => sum + asset.pricePerUnit, 0) / assets.length 
    : 0;
  
  // Find highest and lowest value assets
  const highestValueAsset = assets.length > 0 
    ? assets.reduce((max, asset) => {
        const currentPrice = livePrices.get(asset.name) || asset.currentPrice || asset.pricePerUnit;
        const maxPrice = livePrices.get(max.name) || max.currentPrice || max.pricePerUnit;
        return (asset.quantity * currentPrice) > (max.quantity * maxPrice) ? asset : max;
      }) 
    : null;
  
  const lowestValueAsset = assets.length > 0 
    ? assets.reduce((min, asset) => {
        const currentPrice = livePrices.get(min.name) || min.currentPrice || min.pricePerUnit;
        const minPrice = livePrices.get(min.name) || min.currentPrice || min.pricePerUnit;
        return (asset.quantity * currentPrice) < (min.quantity * minPrice) ? asset : min;
      }) 
    : null;

  // Initialize WebSocket connection and start streaming automatically
  useEffect(() => {
    const initializeConnection = async () => {
      try {
        console.log('PortfolioSummary: Initializing WebSocket connection...');
        await websocketService.connect();
        setIsConnected(true);
        
        // Subscribe to market data for all user assets automatically
        assets.forEach(asset => {
          websocketService.subscribeToMarketData(asset.name, (data: any) => {
            if (data.type === 'market-data' && data.symbol === asset.name) {
              setLivePrices(prev => {
                const newMap = new Map(prev);
                newMap.set(asset.name, data.price);
                return newMap;
              });
              setLastUpdate(new Date());
            }
          });
        });
        
      } catch (error) {
        console.error('PortfolioSummary: Failed to connect to WebSocket:', error);
        setIsConnected(false);
      }
    };

    if (assets.length > 0) {
      initializeConnection();
    }

    // Cleanup on unmount
    return () => {
      if (assets.length > 0) {
        websocketService.disconnect();
      }
    };
  }, [assets]);

  return (
    <div className="space-y-8">
      {/* Real-Time Market Data Status */}
      {assets.length > 0 && (
        <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-4 shadow-xl shadow-black/20">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-400' : 'bg-red-400'}`}></div>
              <span className="text-gray-300 text-sm">
                {isConnected ? '🟢 Live Market Data' : '🔴 Market Data Offline'}
              </span>
            </div>
            <div className="text-xs text-gray-500">
              Last update: {lastUpdate.toLocaleTimeString()}
            </div>
          </div>
          <div className="mt-2 text-xs text-gray-400">
            💡 Real-time prices automatically update your portfolio value
          </div>
        </div>
      )}

      {/* Main Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Total Portfolio Value */}
        <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-green-600/50 hover:shadow-xl hover:shadow-green-600/20 transition-all duration-300 transform hover:-translate-y-1">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-12 h-12 bg-gradient-to-br from-green-600 to-emerald-700 rounded-2xl flex items-center justify-center shadow-lg shadow-green-600/40">
              <DollarSign size={24} className="text-white" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-white group-hover:text-green-300 transition-colors">Total Value</h3>
              <p className="text-sm text-gray-400">Portfolio worth</p>
            </div>
          </div>
          <div className="text-3xl font-bold text-green-400 group-hover:text-green-300 transition-colors">
            ${totalValue.toLocaleString('en-US', { 
              minimumFractionDigits: 2, 
              maximumFractionDigits: 2 
            })}
          </div>
        </div>

        {/* Number of Assets */}
        <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-blue-600/50 hover:shadow-xl hover:shadow-blue-600/20 transition-all duration-300 transform hover:-translate-y-1">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-12 h-12 bg-gradient-to-br from-blue-600 to-indigo-700 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
              <Package size={24} className="text-white" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-white group-hover:text-blue-300 transition-colors">Assets</h3>
              <p className="text-sm text-gray-400">Total holdings</p>
            </div>
          </div>
          <div className="text-3xl font-bold text-blue-400 group-hover:text-blue-300 transition-colors">
            {assetCount}
          </div>
        </div>

        {/* Average Value */}
        <div className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:border-purple-600/50 hover:shadow-xl hover:shadow-purple-600/20 transition-all duration-300 transform hover:-translate-y-1">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-12 h-12 bg-gradient-to-br from-purple-600 to-pink-700 rounded-2xl flex items-center justify-center shadow-lg shadow-purple-600/40">
              <BarChart3 size={24} className="text-white" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-white group-hover:text-purple-300 transition-colors">Average</h3>
              <p className="text-sm text-gray-400">Per asset</p>
            </div>
          </div>
          <div className="text-3xl font-bold text-purple-400 group-hover:text-purple-300 transition-colors">
            ${averageValue.toLocaleString('en-US', { 
              minimumFractionDigits: 2, 
              maximumFractionDigits: 2 
            })}
          </div>
        </div>
      </div>

      {/* Additional Statistics */}
      {assets.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Detailed Stats */}
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-3">
              <div className="w-8 h-8 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-xl flex items-center justify-center">
                <Activity size={16} className="text-white" />
              </div>
              Portfolio Statistics
            </h3>
            <div className="space-y-4">
              <div className="flex justify-between items-center py-3 border-b border-gray-700/50 hover:bg-gray-700/20 rounded-lg px-3 transition-colors">
                <span className="text-gray-400">Total Quantity</span>
                <span className="font-semibold text-white">
                  {totalQuantity.toLocaleString('en-US', { 
                    minimumFractionDigits: 0, 
                    maximumFractionDigits: 6 
                  })}
                </span>
              </div>
              <div className="flex justify-between items-center py-3 border-b border-gray-700/50 hover:bg-gray-700/20 rounded-lg px-3 transition-colors">
                <span className="text-gray-400">Average Price/Unit</span>
                <span className="font-semibold text-white">
                  ${averagePrice.toLocaleString('en-US', { 
                    minimumFractionDigits: 2, 
                    maximumFractionDigits: 2 
                  })}
                </span>
              </div>
              <div className="flex justify-between items-center py-3 hover:bg-gray-700/20 rounded-lg px-3 transition-colors">
                <span className="text-gray-400">Portfolio Diversity</span>
                <span className="font-semibold text-white">
                  {assetCount} {assetCount === 1 ? 'Asset' : 'Assets'}
                </span>
              </div>
            </div>
          </div>

          {/* Top Holdings */}
          <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
            <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-3">
              <div className="w-8 h-8 bg-gradient-to-br from-green-600 to-emerald-600 rounded-xl flex items-center justify-center">
                <Target size={16} className="text-white" />
              </div>
              Top Holdings
            </h3>
            <div className="space-y-4">
              {highestValueAsset && (
                <div className="flex justify-between items-center py-3 border-b border-gray-700/50 hover:bg-gray-700/20 rounded-lg px-3 transition-colors">
                  <div>
                    <span className="text-gray-400">Highest Value</span>
                    <div className="text-sm text-gray-500">{highestValueAsset.name}</div>
                  </div>
                  <span className="font-semibold text-green-400">
                    ${(highestValueAsset.quantity * highestValueAsset.pricePerUnit).toLocaleString('en-US', { 
                      minimumFractionDigits: 2, 
                      maximumFractionDigits: 2 
                    })}
                  </span>
                </div>
              )}
              {lowestValueAsset && lowestValueAsset.id !== highestValueAsset?.id && (
                <div className="flex justify-between items-center py-3 hover:bg-gray-700/20 rounded-lg px-3 transition-colors">
                  <div>
                    <span className="text-gray-400">Lowest Value</span>
                    <div className="text-sm text-gray-500">{lowestValueAsset.name}</div>
                  </div>
                  <span className="font-semibold text-orange-400">
                    ${(lowestValueAsset.quantity * lowestValueAsset.pricePerUnit).toLocaleString('en-US', { 
                      minimumFractionDigits: 2, 
                      maximumFractionDigits: 2 
                    })}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PortfolioSummary; 