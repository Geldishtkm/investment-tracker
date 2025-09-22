import React, { useState, useEffect } from 'react';
import { assetService } from './services/api';
import { authService } from './services/authService';
import { Asset, AssetWithPrice, User } from './types';
import AssetCard from './components/AssetCard';
import PortfolioSummary from './components/PortfolioSummary';
import EmptyState from './components/EmptyState';
import CoinsPage from './components/CoinsPage';
import AnalyticsPage from './components/AnalyticsPage';
import PortfolioRebalancingDashboard from './components/PortfolioRebalancingDashboard';
import LoginPage from './components/LoginPage';
import RegisterPage from './components/RegisterPage';


// Inline Toast component for App.tsx
const AppToast: React.FC<{
  type: 'success' | 'error' | 'info';
  message: string;
  onClose: () => void;
}> = ({ type, message, onClose }) => {
  const bgColor = type === 'success' 
    ? 'bg-gradient-to-r from-green-800/90 to-emerald-900/90' 
    : type === 'error'
    ? 'bg-gradient-to-r from-red-500/90 to-red-600/90'
    : 'bg-gradient-to-r from-blue-500/90 to-blue-600/90';
  
  const borderColor = type === 'success' 
    ? 'border-green-700/30' 
    : type === 'error'
    ? 'border-red-400/30'
    : 'border-blue-400/30';

  return (
    <div className={`${bgColor} backdrop-blur-sm border ${borderColor} rounded-xl p-4 shadow-2xl min-w-[320px] max-w-[400px] animate-in slide-in-from-right-full duration-300`}>
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <p className="text-white font-medium text-sm leading-relaxed">
            {message}
          </p>
        </div>
        <button
          onClick={onClose}
          className="flex-shrink-0 text-white/70 hover:text-white transition-colors p-1 rounded-lg hover:bg-white/10"
        >
          ✕
        </button>
      </div>
    </div>
  );
};

type Page = 'portfolio' | 'coins' | 'analytics' | 'rebalancing';
type AuthPage = 'login' | 'register';

interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'info';
  message: string;
}

function App() {
  const [currentPage, setCurrentPage] = useState<Page>('portfolio');
  const [currentAuthPage, setCurrentAuthPage] = useState<AuthPage>('login');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [assets, setAssets] = useState<Asset[]>([]);
  const [assetsWithPrices, setAssetsWithPrices] = useState<AssetWithPrice[]>([]);
  const [loading, setLoading] = useState(true);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [toastCounter, setToastCounter] = useState(0);

  // Check authentication on app load
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const authenticated = authService.isAuthenticated();
        
        if (authenticated) {
          try {
            // Test the token with the backend to ensure it's valid
            const tokenValid = await authService.testAuth();
            
            if (tokenValid) {
              const user = authService.getCurrentUser();
              setCurrentUser(user);
              
              await loadAssets();
              setIsAuthenticated(true);
            } else {
              console.log('Token is invalid or expired, redirecting to login');
              authService.removeToken();
              setIsAuthenticated(false);
              setCurrentUser(null);
              setAssets([]);
              setAssetsWithPrices([]);
            }
          } catch (error) {
            console.log('Token validation failed, redirecting to login:', error);
            authService.removeToken();
            setIsAuthenticated(false);
            setCurrentUser(null);
            setAssets([]);
            setAssetsWithPrices([]);
          }
        } else {
          console.log('User not authenticated, showing login page');
          setIsAuthenticated(false);
          setCurrentUser(null);
          setAssets([]);
          setAssetsWithPrices([]);
        }
      } catch (error) {
        console.error('Error checking authentication:', error);
        setIsAuthenticated(false);
        setCurrentUser(null);
        setAssets([]);
        setAssetsWithPrices([]);
      } finally {
        setLoading(false);
      }
    };

    checkAuth();
  }, []);

  const loadAssets = async () => {
    try {
      setLoading(true);
      
      const assetsData = await assetService.getAllAssets();
      setAssets(assetsData);
      
      // Load assets with current prices
      try {
        const assetsWithPricesData = await assetService.getAllAssetsWithPrices();
        
        // If we got assets with prices, use them; otherwise use regular assets
        if (assetsWithPricesData && assetsWithPricesData.length > 0) {
          setAssetsWithPrices(assetsWithPricesData);
        } else {
          // Fallback to regular assets
          setAssetsWithPrices(assetsData.map(asset => ({
            ...asset,
            currentPrice: asset.pricePerUnit,
            priceChange: 0,
            priceChangePercent: 0
          })));
        }
      } catch (pricesError) {
        console.log('Error loading assets with prices, using fallback:', pricesError);
        // Fallback to regular assets
        setAssetsWithPrices(assetsData.map(asset => ({
          ...asset,
          currentPrice: asset.pricePerUnit,
          priceChange: 0,
          priceChangePercent: 0
        })));
      }
    } catch (error) {
      console.error('Error loading assets:', error);
      showToast('error', 'Failed to load assets: ' + (error instanceof Error ? error.message : 'Unknown error'));
    } finally {
      setLoading(false);
    }
  };

  const handleAddAsset = async (asset: Omit<Asset, 'id'>) => {
    try {
      await assetService.addAsset(asset);
      await loadAssets();
      showToast('success', 'Asset added successfully!');
    } catch (error) {
      console.error('Error adding asset:', error);
      showToast('error', 'Failed to add asset: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  };

  const handleUpdateAsset = async (id: number, asset: Omit<Asset, 'id'>) => {
    try {
      await assetService.updateAsset(id, asset);
      await loadAssets();
      showToast('success', 'Asset updated successfully!');
    } catch (error) {
      console.error('Error updating asset:', error);
      showToast('error', 'Failed to update asset');
    }
  };

  const handleDeleteAsset = async (id: number, assetName: string) => {
    try {
      await assetService.deleteAsset(id);
      await loadAssets();
      showToast('success', `${assetName} deleted successfully!`);
    } catch (error) {
      console.error('Error deleting asset:', error);
      showToast('error', `Failed to delete ${assetName}`);
    }
  };

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
    const user = authService.getCurrentUser();
    setCurrentUser(user);
    showToast('success', 'Login successful! Welcome back.');
    loadAssets();
  };

  const handleRegisterSuccess = () => {
    showToast('success', 'Account created successfully! Please log in.');
    setCurrentAuthPage('login');
  };

  const handleLogout = () => {
    authService.clearAuth();
    setIsAuthenticated(false);
    setCurrentUser(null);
    setAssets([]);
    setAssetsWithPrices([]);
    setCurrentPage('portfolio');
    showToast('success', 'Logged out successfully');
  };

  const showToast = (type: 'success' | 'error' | 'info', message: string) => {
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}-${toastCounter}`;
    console.log(`Creating toast with ID: ${id}`, { type, message });
    setToasts(prev => [...prev, { id, type, message }]);
    setToastCounter(prev => prev + 1);
    setTimeout(() => {
      setToasts(prev => prev.filter(toast => toast.id !== id));
    }, 5000);
  };

  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };

  // Show authentication pages if not authenticated
  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-900">
        {currentAuthPage === 'login' ? (
          <LoginPage
            onLoginSuccess={handleLoginSuccess}
            onSwitchToRegister={() => setCurrentAuthPage('register')}
          />
        ) : (
          <RegisterPage
            onRegisterSuccess={handleRegisterSuccess}
            onSwitchToLogin={() => setCurrentAuthPage('login')}
          />
        )}
        
        {/* Toast Notifications */}
        <div className="fixed top-4 right-4 z-50 space-y-2">
          {toasts.map((toast) => (
            <AppToast
              key={toast.id}
              type={toast.type}
              message={toast.message}
              onClose={() => removeToast(toast.id)}
            />
          ))}
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-800"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      {/* Navigation */}
      <nav className="bg-gradient-to-r from-gray-900 via-gray-800 to-gray-900 border-b border-gray-700/50 shadow-xl shadow-black/20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <div className="flex items-center gap-4">
                <h1 className="text-3xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-teal-400 to-cyan-400 tracking-tight">
                  Portfolio Tracker
                </h1>
              </div>
              {currentUser && (
                <div className="ml-6 flex items-center gap-3">
                  <div className="w-2.5 h-2.5 bg-gradient-to-r from-emerald-400 to-teal-400 rounded-full animate-pulse shadow-lg shadow-emerald-400/50"></div>
                  <span className="text-sm text-gray-300">
                    Welcome, <span className="font-semibold text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-teal-400">{currentUser.username}</span>
                  </span>
                </div>
              )}
            </div>
            <div className="flex items-center space-x-1">
              <button
                onClick={() => setCurrentPage('portfolio')}
                className={`px-6 py-3 rounded-2xl transition-all duration-300 font-semibold text-sm tracking-wide ${
                  currentPage === 'portfolio' 
                    ? 'bg-gradient-to-r from-emerald-500 to-teal-500 text-white shadow-xl shadow-emerald-500/25 transform scale-105 border-0' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-800/60 hover:shadow-lg hover:shadow-gray-800/30 border border-gray-700/50 hover:border-gray-600/50'
                }`}
              >
                Portfolio
              </button>
              <button
                onClick={() => setCurrentPage('coins')}
                className={`px-6 py-3 rounded-2xl transition-all duration-300 font-semibold text-sm tracking-wide ${
                  currentPage === 'coins'
                    ? 'bg-gradient-to-r from-blue-500 to-indigo-500 text-white shadow-xl shadow-blue-500/25 transform scale-105 border-0' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-800/60 hover:shadow-lg hover:shadow-gray-800/30 border border-gray-700/50 hover:border-gray-600/50'
                }`}
              >
                Top Coins
              </button>
              <button
                onClick={() => setCurrentPage('analytics')}
                className={`px-6 py-3 rounded-2xl transition-all duration-300 font-semibold text-sm tracking-wide ${
                  currentPage === 'analytics'
                    ? 'bg-gradient-to-r from-purple-500 to-pink-500 text-white shadow-xl shadow-purple-500/25 transform scale-105 border-0' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-800/60 hover:shadow-lg hover:shadow-gray-800/30 border border-gray-700/50 hover:border-gray-600/50'
                }`}
              >
                Analytics
              </button>
              
              <button
                onClick={() => setCurrentPage('rebalancing')}
                className={`px-6 py-3 rounded-2xl transition-all duration-300 font-semibold text-sm tracking-wide ${
                  currentPage === 'rebalancing'
                    ? 'bg-gradient-to-r from-orange-500 to-red-500 text-white shadow-xl shadow-orange-500/25 transform scale-105 border-0' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-800/60 hover:shadow-lg hover:shadow-gray-800/30 border border-gray-700/50 hover:border-gray-600/50'
                }`}
              >
                Rebalancing
              </button>

              <div className="h-8 w-px bg-gradient-to-b from-gray-600/50 to-transparent mx-3"></div>
              <button
                onClick={handleLogout}
                className="px-6 py-3 rounded-2xl text-sm font-semibold text-red-400 hover:text-red-300 hover:bg-red-900/20 transition-all duration-300 hover:shadow-lg hover:shadow-red-900/30 border border-red-800/30 hover:border-red-700/50"
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="min-h-[600px]">
          {currentPage === 'portfolio' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-green-700 to-emerald-800 bg-clip-text text-transparent mb-2">
                  Portfolio Overview
                </h2>
                <p className="text-gray-400">Track your cryptocurrency investments and performance</p>
              </div>
              <PortfolioSummary assets={assetsWithPrices} />
              
              {/* Real-Time Market Data Widget */}
              {assets.length > 0 && (
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-xl font-bold text-white flex items-center gap-3">
                      <div className="w-8 h-8 bg-gradient-to-br from-cyan-500 to-blue-500 rounded-xl flex items-center justify-center">
                        <div className="w-3 h-3 bg-green-700 rounded-full animate-pulse"></div>
                      </div>
                      Live Market Data
                    </h3>
                    <div className="text-sm text-gray-400">
                      Auto-updating portfolio values
                    </div>
                  </div>
                  <div className="text-sm text-gray-300">
                    Your portfolio values are automatically updated with real-time market prices via manual refresh and polling.
                  </div>
                </div>
              )}
              
              {assets.length === 0 ? (
                <EmptyState onAddAsset={() => setCurrentPage('coins')} />
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-8">
                  {assetsWithPrices.map((asset) => (
                    <AssetCard
                      key={asset.id}
                      asset={asset}
                      livePrice={asset.currentPrice}
                      onUpdate={(updatedAsset) => handleUpdateAsset(asset.id, {
                        name: updatedAsset.name,
                        quantity: updatedAsset.quantity,
                        pricePerUnit: updatedAsset.pricePerUnit,
                        purchasePricePerUnit: updatedAsset.purchasePricePerUnit,
                        initialInvestment: updatedAsset.initialInvestment
                      })}
                      onDelete={handleDeleteAsset}
                      onShowToast={showToast}
                    />
                  ))}
                </div>
              )}
            </div>
          )}
          
          {currentPage === 'coins' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent mb-2">
                  Top Cryptocurrencies
                </h2>
                <p className="text-gray-400">Explore the latest market data and add coins to your portfolio</p>
              </div>
              <CoinsPage 
                onBack={() => setCurrentPage('portfolio')} 
                onAssetAdded={(asset) => handleAddAsset({
                  name: asset.name,
                  quantity: asset.quantity,
                  pricePerUnit: asset.pricePerUnit,
                  purchasePricePerUnit: asset.purchasePricePerUnit,
                  initialInvestment: asset.initialInvestment
                })} 
              />
            </div>
          )}
          
          {currentPage === 'analytics' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-purple-400 to-pink-400 bg-clip-text text-transparent mb-2">
                  Analytics & Insights
                </h2>
                <p className="text-gray-400">Analyze your portfolio performance and market trends</p>
              </div>
              <AnalyticsPage />
            </div>
          )}
          
          

          {currentPage === 'rebalancing' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold text-white mb-2">
                  Portfolio Rebalancing
                </h2>
                <p className="text-gray-400">Optimize your portfolio with Mean-Variance Optimization and Black-Litterman models</p>
              </div>
              <PortfolioRebalancingDashboard />
            </div>
          )}


        </div>
      </main>

      {/* Toast Notifications */}
      <div className="fixed top-4 right-4 z-50 space-y-2">
        {toasts.map((toast) => (
          <AppToast
            key={toast.id}
            type={toast.type}
            message={toast.message}
            onClose={() => removeToast(toast.id)}
          />
        ))}
      </div>
    </div>
  );
}

export default App; 