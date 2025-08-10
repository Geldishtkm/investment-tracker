import React, { useState, useEffect } from 'react';
import { Search, ArrowLeft, Loader2, Coins, Plus, TrendingUp, Sparkles, Star, Zap, Target } from 'lucide-react';
import { Coin, Asset } from '../types';
import { assetService } from '../services/api';
import Toast from './Toast';
import PriceHistoryChart from './PriceHistoryChart';

interface CoinsPageProps {
  onBack: () => void;
  onAssetAdded: (asset: Omit<Asset, 'id'>) => void;
}

const CoinsPage: React.FC<CoinsPageProps> = ({ onBack, onAssetAdded }) => {
  const [coins, setCoins] = useState<Coin[]>([]);
  const [filteredCoins, setFilteredCoins] = useState<Coin[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const coinsPerPage = 50;
  const [quantities, setQuantities] = useState<{ [key: string]: string }>({});
  const [toast, setToast] = useState<{
    message: string;
    type: 'success' | 'error';
    isVisible: boolean;
  }>({
    message: '',
    type: 'success',
    isVisible: false
  });
  const [selectedCoinForChart, setSelectedCoinForChart] = useState<Coin | null>(null);

  // Fetch top 300 coins from backend
  const fetchCoins = async () => {
    try {
      setLoading(true);
      setError('');
      const coinsData = await assetService.getTopCoins();
      console.log('Raw coins data received:', coinsData);
      console.log('Type of coinsData:', typeof coinsData);
      console.log('Is array?', Array.isArray(coinsData));
      if (Array.isArray(coinsData)) {
        console.log('Number of coins:', coinsData.length);
        console.log('First coin sample:', coinsData[0]);
      }
      setCoins(coinsData);
      setFilteredCoins(coinsData);
      if (!Array.isArray(coinsData)) {
        console.error('Expected array but got:', typeof coinsData, coinsData);
        setError('Invalid data format received from server');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch top coins');
      console.error('Error fetching top coins:', err);
    } finally {
      setLoading(false);
    }
  };

  // Load coins on component mount
  useEffect(() => {
    fetchCoins();
  }, []);

  // Filter coins based on search term
  useEffect(() => {
    if (!Array.isArray(coins)) {
      setFilteredCoins([]);
      return;
    }
    
    if (searchTerm.trim() === '') {
      setFilteredCoins(coins);
    } else {
      const filtered = coins.filter(coin =>
        coin.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        coin.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
        coin.id.toLowerCase().includes(searchTerm.toLowerCase())
      );
      setFilteredCoins(filtered);
    }
    setCurrentPage(1); // Reset to first page when searching
  }, [searchTerm, coins]);

  // Calculate pagination
  const totalPages = Math.ceil((Array.isArray(filteredCoins) ? filteredCoins.length : 0) / coinsPerPage);
  const startIndex = (currentPage - 1) * coinsPerPage;
  const endIndex = startIndex + coinsPerPage;
  const currentCoins = Array.isArray(filteredCoins) ? filteredCoins.slice(startIndex, endIndex) : [];

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const closeToast = () => {
    setToast(prev => ({ ...prev, isVisible: false }));
  };

  const handleViewPriceHistory = (coin: Coin) => {
    setSelectedCoinForChart(coin);
  };

  const handleClosePriceHistory = () => {
    setSelectedCoinForChart(null);
  };

  const handleAddToPortfolio = async (coin: Coin) => {
    const quantity = quantities[coin.id] || '';
    
    if (!quantity.trim()) {
      setToast({
        message: 'Please enter a quantity first',
        type: 'error',
        isVisible: true
      });
      return;
    }

    try {
      const quantityNum = parseFloat(quantity);
      const currentPrice = coin.current_price || 0;
      const purchasePricePerUnit = currentPrice; // Use current market price as purchase price
      const initialInvestment = quantityNum * purchasePricePerUnit;

      // Ensure all required fields are present and valid
      const assetData = {
        name: coin.name,
        quantity: quantityNum,
        pricePerUnit: currentPrice,
        purchasePricePerUnit: purchasePricePerUnit,
        initialInvestment: initialInvestment
      };

      onAssetAdded(assetData);
      
      // Clear the form field
      setQuantities(prev => ({
        ...prev,
        [coin.id]: ''
      }));

      setToast({
        message: `${coin.name} added successfully!`,
        type: 'success',
        isVisible: true
      });
    } catch (error) {
      console.error('Error adding asset:', error);
      setToast({
        message: 'Error adding asset: ' + (error instanceof Error ? error.message : 'Unknown error'),
        type: 'error',
        isVisible: true
      });
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
        {/* Animated Background */}
        <div className="absolute inset-0 bg-gradient-to-r from-blue-600/20 via-indigo-600/20 to-purple-600/20 animate-pulse"></div>
        <div className="absolute top-0 left-0 w-full h-full bg-[radial-gradient(circle_at_50%_50%,rgba(59,130,246,0.3),transparent_50%)]"></div>
        
        <div className="relative z-10 max-w-7xl mx-auto p-6">
          {/* Header */}
          <div className="flex items-center gap-4 mb-8">
            <button
              onClick={onBack}
              className="group relative px-6 py-3 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl text-white font-medium transition-all duration-300 hover:from-gray-800/80 hover:to-gray-700/80 hover:border-gray-600/50 hover:shadow-lg hover:shadow-gray-900/50 hover:scale-105"
            >
              <div className="flex items-center gap-2">
                <ArrowLeft size={20} className="group-hover:-translate-x-1 transition-transform duration-300" />
                <span>Back to Portfolio</span>
              </div>
            </button>
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
                <Coins size={28} className="text-white" />
              </div>
              <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent">
                  Top Cryptocurrencies
                </h1>
                <p className="text-blue-300/70 text-sm font-medium">Loading market data...</p>
              </div>
            </div>
          </div>
          
          {/* Enhanced Loading Spinner */}
          <div className="flex flex-col justify-center items-center py-20">
            <div className="relative mb-8">
              <div className="w-20 h-20 border-4 border-blue-600/30 rounded-full animate-spin"></div>
              <div className="absolute top-0 left-0 w-20 h-20 border-4 border-transparent border-t-blue-400 rounded-full animate-spin"></div>
              <div className="absolute top-0 left-0 w-20 h-20 border-4 border-transparent border-r-indigo-400 rounded-full animate-spin" style={{animationDelay: '-0.5s'}}></div>
            </div>
            <div className="text-center">
              <h3 className="text-xl font-semibold text-white mb-2">Loading Cryptocurrency Data</h3>
              <p className="text-gray-400">Fetching the latest market information...</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-r from-red-600/20 via-purple-600/20 to-blue-600/20 animate-pulse"></div>
        
        <div className="relative z-10 max-w-7xl mx-auto p-6">
          {/* Header */}
          <div className="flex items-center gap-4 mb-8">
            <button
              onClick={onBack}
              className="group relative px-6 py-3 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl text-white font-medium transition-all duration-300 hover:from-gray-800/80 hover:to-gray-700/80 hover:border-gray-600/50 hover:shadow-lg hover:shadow-gray-900/50 hover:scale-105"
            >
              <div className="flex items-center gap-2">
                <ArrowLeft size={20} className="group-hover:-translate-x-1 transition-transform duration-300" />
                <span>Back to Portfolio</span>
              </div>
            </button>
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 bg-gradient-to-br from-red-600 to-purple-600 rounded-2xl flex items-center justify-center shadow-lg shadow-red-600/40">
                <Coins size={28} className="text-white" />
              </div>
              <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-red-400 via-purple-400 to-blue-400 bg-clip-text text-transparent">
                  Top Cryptocurrencies
                </h1>
                <p className="text-red-300/70 text-sm font-medium">Error loading data</p>
              </div>
            </div>
          </div>

          {/* Enhanced Error State */}
          <div className="max-w-2xl mx-auto">
            <div className="text-center p-12 bg-gradient-to-br from-red-600/10 to-purple-600/10 backdrop-blur-sm border border-red-600/30 rounded-3xl shadow-2xl shadow-red-600/20">
              <div className="w-24 h-24 bg-gradient-to-br from-red-600 to-purple-600 rounded-full flex items-center justify-center mx-auto mb-6 shadow-lg shadow-red-600/40">
                <Coins size={32} className="text-white" />
              </div>
              <h3 className="text-2xl font-bold text-white mb-4">
                Error Loading Top Coins
              </h3>
              <p className="text-gray-300 mb-8 text-lg leading-relaxed">{error}</p>
              <button
                onClick={fetchCoins}
                className="group px-8 py-4 bg-gradient-to-r from-red-600 to-purple-600 hover:from-red-700 hover:to-purple-700 text-white font-semibold rounded-2xl transition-all duration-300 transform hover:scale-105 hover:shadow-lg shadow-md border border-red-500/30 hover:border-red-400/50"
              >
                <div className="flex items-center gap-2">
                  <span className="group-hover:rotate-180 transition-transform duration-300">🔄</span>
                  <span>Try Again</span>
                </div>
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
      {/* Animated Background */}
      <div className="absolute inset-0 bg-gradient-to-r from-blue-600/20 via-indigo-600/20 to-purple-600/20 animate-pulse"></div>
      <div className="absolute top-0 left-0 w-full h-full bg-[radial-gradient(circle_at_50%_50%,rgba(59,130,246,0.3),transparent_50%)]"></div>
      
      <div className="relative z-10 max-w-7xl mx-auto p-6">
        {/* Header */}
        <div className="flex items-center gap-4 mb-8">
          <button
            onClick={onBack}
            className="group relative px-6 py-3 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl text-white font-medium transition-all duration-300 hover:from-gray-800/80 hover:to-gray-700/80 hover:border-gray-600/50 hover:shadow-lg hover:shadow-gray-900/50 hover:scale-105"
          >
            <div className="flex items-center gap-2">
              <ArrowLeft size={20} className="group-hover:-translate-x-1 transition-transform duration-300" />
              <span>Back to Portfolio</span>
            </div>
          </button>
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40">
              <Coins size={28} className="text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent">
                Top Cryptocurrencies
              </h1>
              <p className="text-blue-300/70 text-sm font-medium">Explore the latest market data & trends</p>
            </div>
          </div>
        </div>

        {/* Enhanced Search and Controls */}
        <div className="mb-8">
          <div className="relative max-w-lg mx-auto">
            <Search className="absolute left-4 top-1/2 transform -translate-y-1/2 text-gray-400" size={22} />
            <input
              type="text"
              placeholder="Search coins by name, symbol, or ID..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-12 pr-6 py-4 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 transition-all duration-300 shadow-lg shadow-gray-900/50 text-lg"
            />
            <div className="absolute right-4 top-1/2 transform -translate-y-1/2 text-xs text-gray-500 font-medium">
              {filteredCoins.length} results
            </div>
          </div>
        </div>

        {/* Enhanced Stats */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <div className="group p-6 bg-gradient-to-r from-blue-600/20 to-indigo-600/20 backdrop-blur-sm border border-blue-600/40 rounded-2xl hover:border-blue-500/50 hover:shadow-xl hover:shadow-blue-600/20 transition-all duration-300 transform hover:-translate-y-1">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-xl flex items-center justify-center">
                <Coins size={20} className="text-white" />
              </div>
              <div className="text-sm text-blue-300 font-medium">Total Coins</div>
            </div>
            <div className="text-3xl font-bold text-white group-hover:text-blue-300 transition-colors duration-300">
              {coins.length.toLocaleString()}
            </div>
            <div className="text-xs text-blue-400/70 mt-2">Available cryptocurrencies</div>
          </div>
          
          <div className="group p-6 bg-gradient-to-r from-indigo-600/20 to-purple-600/20 backdrop-blur-sm border border-indigo-600/40 rounded-2xl hover:border-indigo-500/50 hover:shadow-xl hover:shadow-indigo-600/20 transition-all duration-300 transform hover:-translate-y-1">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 bg-gradient-to-br from-indigo-600 to-purple-600 rounded-xl flex items-center justify-center">
                <Search size={20} className="text-white" />
              </div>
              <div className="text-sm text-indigo-300 font-medium">Filtered</div>
            </div>
            <div className="text-3xl font-bold text-white group-hover:text-indigo-300 transition-colors duration-300">
              {filteredCoins.length.toLocaleString()}
            </div>
            <div className="text-xs text-indigo-400/70 mt-2">Matching your search</div>
          </div>
          
          <div className="group p-6 bg-gradient-to-r from-purple-600/20 to-pink-600/20 backdrop-blur-sm border border-purple-600/40 rounded-2xl hover:border-purple-500/50 hover:shadow-xl hover:shadow-purple-600/20 transition-all duration-300 transform hover:-translate-y-1">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 bg-gradient-to-br from-purple-600 to-pink-600 rounded-xl flex items-center justify-center">
                <TrendingUp size={20} className="text-white" />
              </div>
              <div className="text-sm text-purple-300 font-medium">Page</div>
            </div>
            <div className="text-3xl font-bold text-white group-hover:text-purple-300 transition-colors duration-300">
              {currentPage}
            </div>
            <div className="text-xs text-purple-400/70 mt-2">of {totalPages} total</div>
          </div>
        </div>

        {/* Enhanced Error Display */}
        {error && (
          <div className="mb-8 p-6 bg-gradient-to-r from-red-600/20 to-red-700/20 backdrop-blur-sm border border-red-600/40 rounded-2xl text-red-300 shadow-xl shadow-red-600/20">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-gradient-to-br from-red-600 to-red-700 rounded-xl flex items-center justify-center">
                <span className="text-lg">⚠️</span>
              </div>
              <div>
                <h4 className="font-semibold text-red-200 mb-1">Error Loading Data</h4>
                <p className="text-red-300/80 text-sm">{error}</p>
              </div>
            </div>
          </div>
        )}

        {/* Coins Grid - Made Wider */}
        <div className="grid grid-cols-1 md:grid-cols-1 lg:grid-cols-2 xl:grid-cols-2 gap-8 mb-8">
          {currentCoins.map((coin) => (
            <div
              key={coin.id}
              className="group relative bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-8 hover:border-blue-600/50 hover:shadow-xl hover:shadow-blue-600/20 transition-all duration-300 transform hover:-translate-y-1 hover:scale-[1.02]"
            >
              {/* Coin Header */}
              <div className="flex items-start justify-between mb-6">
                <div className="flex items-center gap-4 flex-1 min-w-0">
                  <div className="w-16 h-16 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-2xl flex items-center justify-center shadow-lg shadow-blue-600/40 overflow-hidden flex-shrink-0">
                    {coin.image ? (
                      <img 
                        src={coin.image} 
                        alt={coin.name}
                        className="w-full h-full object-cover"
                        onError={(e) => {
                          // Fallback to symbol if image fails to load
                          const target = e.target as HTMLImageElement;
                          target.style.display = 'none';
                          target.nextElementSibling?.classList.remove('hidden');
                        }}
                      />
                    ) : null}
                    <span className={`text-2xl font-bold text-white ${coin.image ? 'hidden' : ''}`}>
                      {coin.symbol.charAt(0).toUpperCase()}
                    </span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <h3 className="text-lg font-semibold text-white group-hover:text-blue-300 transition-colors duration-300 truncate">
                      {coin.name}
                    </h3>
                    <p className="text-sm text-gray-400 font-mono">{coin.symbol.toUpperCase()}</p>
                  </div>
                </div>
                <div className="text-right flex-shrink-0 ml-4">
                  <div className="text-xs text-gray-500 font-medium">Current Price</div>
                  <div className="text-xl font-bold text-blue-400 group-hover:text-blue-300 transition-colors duration-300">
                    ${coin.current_price?.toLocaleString() || 'N/A'}
                  </div>
                </div>
              </div>

              {/* Price Info with Enhanced Design */}
              <div className="mb-6 p-5 bg-gradient-to-r from-gray-800/30 to-gray-700/30 rounded-xl border border-gray-600/20">
                <div className="text-3xl font-bold text-white mb-3 text-center">
                  ${coin.current_price?.toLocaleString() || 'N/A'}
                </div>
                <div className="flex items-center justify-center gap-2">
                  <span className={`text-sm font-medium px-3 py-2 rounded-full ${
                    coin.price_change_percentage_24h >= 0 
                      ? 'text-green-400 bg-green-600/20 border border-green-600/30' 
                      : 'text-red-400 bg-red-600/20 border border-red-600/30'
                  }`}>
                    {coin.price_change_percentage_24h >= 0 ? '↗' : '↘'} 
                    {Math.abs(coin.price_change_percentage_24h || 0).toFixed(2)}%
                  </span>
                  <span className="text-xs text-gray-500 font-medium">24h</span>
                </div>
              </div>

              {/* Market Cap with Enhanced Design */}
              <div className="mb-8 p-4 bg-gradient-to-r from-indigo-600/10 to-purple-600/10 rounded-xl border border-indigo-600/20">
                <div className="text-sm text-gray-400 mb-2 font-medium">Market Cap</div>
                <div className="text-xl font-semibold text-white">
                  ${(coin.market_cap / 1e9).toFixed(2)}B
                </div>
              </div>

              {/* Quantity Input Section - Removed White Stepper */}
              <div className="mb-6">
                <div className="text-sm text-gray-400 mb-3 font-medium">Quantity to Add</div>
                <div className="relative">
                  <input
                    type="number"
                    placeholder="0.00"
                    value={quantities[coin.id] || ''}
                    onChange={(e) => setQuantities(prev => ({
                      ...prev,
                      [coin.id]: e.target.value
                    }))}
                    className="w-full px-5 py-4 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-600/50 rounded-xl text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 transition-all duration-300 text-center font-mono text-lg"
                    step="0.000001"
                    min="0"
                    style={{
                      WebkitAppearance: 'none',
                      MozAppearance: 'textfield'
                    }}
                  />
                  <div className="absolute right-4 top-1/2 transform -translate-y-1/2 text-xs text-gray-500 font-medium">
                    {coin.symbol.toUpperCase()}
                  </div>
                </div>
                {quantities[coin.id] && (
                  <div className="mt-3 text-sm text-gray-500 text-center">
                    ≈ ${((parseFloat(quantities[coin.id]) || 0) * (coin.current_price || 0)).toFixed(2)} USD
                  </div>
                )}
              </div>

              {/* Enhanced Add to Portfolio Button */}
              <button
                onClick={() => handleAddToPortfolio(coin)}
                disabled={!quantities[coin.id] || parseFloat(quantities[coin.id]) <= 0}
                className="w-full group/btn relative px-6 py-4 bg-gradient-to-r from-blue-600/20 to-indigo-600/20 hover:from-blue-600/30 hover:to-indigo-600/30 backdrop-blur-sm border border-blue-600/40 rounded-xl text-blue-300 hover:text-blue-200 transition-all duration-300 hover:shadow-lg hover:shadow-blue-600/20 font-medium disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:from-blue-600/20 disabled:hover:to-indigo-600/20 text-lg"
              >
                <div className="flex items-center justify-center gap-3">
                  <Plus size={18} className="group-hover/btn:scale-110 transition-transform duration-200" />
                  <span>Add to Portfolio</span>
                </div>
              </button>
            </div>
          ))}
        </div>

        {/* Enhanced Pagination - Removed Page Numbers */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-6 mb-8">
            <button
              onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
              disabled={currentPage === 1}
              className="group px-8 py-4 rounded-2xl text-sm font-medium bg-gradient-to-r from-gray-700/50 to-gray-800/50 backdrop-blur-sm border border-gray-600/50 text-gray-300 hover:text-white hover:border-gray-500/50 hover:from-gray-600/50 hover:to-gray-700/50 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 hover:shadow-lg hover:shadow-gray-900/50 hover:scale-105"
            >
              <div className="flex items-center gap-2">
                <span className="group-hover:-translate-x-1 transition-transform duration-300">←</span>
                <span>Previous</span>
              </div>
            </button>
            
            <button
              onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
              disabled={currentPage === totalPages}
              className="group px-8 py-4 rounded-2xl text-sm font-medium bg-gradient-to-r from-gray-700/50 to-gray-800/50 backdrop-blur-sm border border-gray-600/50 text-gray-300 hover:text-white hover:border-gray-500/50 hover:from-gray-600/50 hover:to-gray-700/50 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 hover:shadow-lg hover:shadow-gray-900/50 hover:scale-105"
            >
              <div className="flex items-center gap-2">
                <span>Next</span>
                <span className="group-hover:translate-x-1 transition-transform duration-300">→</span>
              </div>
            </button>
          </div>
        )}

        {/* Toast Notification */}
        <Toast
          message={toast.message}
          type={toast.type}
          isVisible={toast.isVisible}
          onClose={closeToast}
        />

        {/* Price History Chart Modal */}
        {selectedCoinForChart && (
          <PriceHistoryChart
            selectedCoin={selectedCoinForChart}
            onClose={handleClosePriceHistory}
          />
        )}
      </div>
    </div>
  );
};

export default CoinsPage; 