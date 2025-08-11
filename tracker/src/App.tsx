import React, { useState, useEffect } from 'react';
import { assetService } from './services/api';
import { authService } from './services/authService';
import { Asset, AssetWithPrice, User } from './types';
import AssetCard from './components/AssetCard';
import PortfolioSummary from './components/PortfolioSummary';
import EmptyState from './components/EmptyState';
import CoinsPage from './components/CoinsPage';
import AnalyticsPage from './components/AnalyticsPage';
import VaRDashboard from './components/VaRDashboard';
import PortfolioRebalancingDashboard from './components/PortfolioRebalancingDashboard';
import SecurityDashboard from './components/SecurityDashboard';
import LoginPage from './components/LoginPage';
import RegisterPage from './components/RegisterPage';

// Inline Toast component for App.tsx
const AppToast: React.FC<{
  type: 'success' | 'error' | 'info';
  message: string;
  onClose: () => void;
}> = ({ type, message, onClose }) => {
  const bgColor = type === 'success' 
    ? 'bg-gradient-to-r from-green-500/90 to-emerald-600/90' 
    : type === 'error'
    ? 'bg-gradient-to-r from-red-500/90 to-red-600/90'
    : 'bg-gradient-to-r from-blue-500/90 to-blue-600/90';
  
  const borderColor = type === 'success' 
    ? 'border-green-400/30' 
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

type Page = 'portfolio' | 'coins' | 'analytics' | 'var' | 'rebalancing' | 'security' | 'debug';
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
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-500"></div>
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
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-gradient-to-br from-green-400 to-emerald-500 rounded-xl flex items-center justify-center shadow-lg shadow-green-500/30">
                  <span className="text-2xl">💰</span>
                </div>
                <h1 className="text-xl font-bold bg-gradient-to-r from-green-400 to-emerald-400 bg-clip-text text-transparent">
                  Portfolio Tracker
                </h1>
              </div>
              {currentUser && (
                <div className="ml-6 flex items-center gap-2">
                  <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse"></div>
                  <span className="text-sm text-gray-300">
                    Welcome, <span className="font-medium text-green-400">{currentUser.username}</span>!
                  </span>
                </div>
              )}
            </div>
            <div className="flex items-center space-x-2">
              <button
                onClick={() => setCurrentPage('portfolio')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'portfolio' 
                    ? 'bg-gradient-to-r from-green-500 to-emerald-500 text-white shadow-lg shadow-green-500/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                📊 Portfolio
              </button>
              <button
                onClick={() => setCurrentPage('coins')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'coins'
                    ? 'bg-gradient-to-r from-blue-500 to-indigo-500 text-white shadow-lg shadow-blue-500/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                🪙 Top Coins
              </button>
              <button
                onClick={() => setCurrentPage('analytics')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'analytics'
                    ? 'bg-gradient-to-r from-purple-500 to-pink-500 text-white shadow-lg shadow-purple-500/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                📈 Analytics
              </button>
              <button
                onClick={() => setCurrentPage('var')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'var'
                    ? 'bg-gradient-to-r from-teal-500 to-cyan-500 text-white shadow-lg shadow-teal-500/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                🧮 VaR
              </button>
              <button
                onClick={() => setCurrentPage('rebalancing')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'rebalancing'
                    ? 'bg-gradient-to-r from-orange-500 to-red-500 text-white shadow-lg shadow-orange-500/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                ⚖️ Rebalancing
              </button>
              <button
                onClick={() => setCurrentPage('security')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'security'
                    ? 'bg-gradient-to-r from-purple-600 to-blue-600 text-white shadow-lg shadow-purple-600/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                🔐 Security
              </button>
              <button
                onClick={() => setCurrentPage('debug')}
                className={`px-4 py-2 rounded-xl transition-all duration-300 font-medium ${
                  currentPage === 'debug'
                    ? 'bg-gradient-to-r from-yellow-500 to-orange-500 text-white shadow-lg shadow-yellow-500/30 transform scale-105' 
                    : 'text-gray-300 hover:text-white hover:bg-gray-700/50 hover:shadow-lg hover:shadow-gray-700/30'
                }`}
              >
                🛠️ Debug
              </button>
              <div className="h-6 w-px bg-gray-600 mx-2"></div>
              <button
                onClick={handleLogout}
                className="px-4 py-2 rounded-xl text-sm font-medium text-red-400 hover:text-red-300 hover:bg-red-900/20 transition-all duration-300 hover:shadow-lg hover:shadow-red-900/30"
              >
                🚪 Logout
              </button>
              <button
                onClick={async () => {
                  try {
                    const isAuthValid = await authService.testAuth();
                    showToast('success', `Auth Status: ${isAuthValid ? '✅ Valid' : '❌ Invalid'}`);
                  } catch (error) {
                    showToast('error', `Auth Test Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                  }
                }}
                className="px-4 py-2 rounded-xl text-sm font-medium text-blue-400 hover:text-blue-300 hover:bg-blue-900/20 transition-all duration-300 hover:shadow-lg hover:shadow-blue-900/30"
                title="Test authentication with backend"
              >
                🔍 Test Auth
              </button>
            </div>
          </div>
        </div>
      </nav>

      {/* Debug Section */}
      <div className="bg-gradient-to-r from-gray-800/90 to-gray-900/90 border-b border-gray-700/50 backdrop-blur-sm p-4">
        <div className="max-w-7xl mx-auto">
          <div className="flex items-center justify-between text-sm">
            <div className="flex items-center space-x-6 text-gray-300">
              <span className="flex items-center gap-2">
                <div className={`w-2 h-2 rounded-full ${isAuthenticated ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`}></div>
                Auth: {isAuthenticated ? '✅ Authenticated' : '❌ Not Authenticated'}
              </span>
              <span className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-blue-400"></div>
                User: {currentUser?.username || 'None'}
              </span>
              <span className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-purple-400"></div>
                Token: {authService.getToken() ? '✅ Present' : '❌ Missing'}
              </span>
              <span className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-yellow-400"></div>
                Assets: {assets.length}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="min-h-[600px]">
          {currentPage === 'portfolio' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-green-400 to-emerald-400 bg-clip-text text-transparent mb-2">
                  📊 Portfolio Overview
                </h2>
                <p className="text-gray-400">Track your cryptocurrency investments and performance</p>
              </div>
              <PortfolioSummary assets={assetsWithPrices} />
              {assets.length === 0 ? (
                <EmptyState onAddAsset={() => setCurrentPage('coins')} />
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-8">
                  {assetsWithPrices.map((asset) => (
                    <AssetCard
                      key={asset.id}
                      asset={asset}
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
                  🪙 Top Cryptocurrencies
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
                  📈 Analytics & Insights
                </h2>
                <p className="text-gray-400">Analyze your portfolio performance and market trends</p>
              </div>
              <AnalyticsPage />
            </div>
          )}
          {currentPage === 'var' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-teal-400 to-cyan-400 bg-clip-text text-transparent mb-2">
                  🧮 VaR Dashboard
                </h2>
                <p className="text-gray-400">Calculate and visualize Value at Risk for your portfolio</p>
              </div>
              <VaRDashboard />
            </div>
          )}

          {currentPage === 'rebalancing' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-orange-400 to-red-400 bg-clip-text text-transparent mb-2">
                  ⚖️ Portfolio Rebalancing
                </h2>
                <p className="text-gray-400">Optimize your portfolio with Mean-Variance Optimization and Black-Litterman models</p>
              </div>
              <PortfolioRebalancingDashboard />
            </div>
          )}

          {currentPage === 'security' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-purple-400 to-blue-400 bg-clip-text text-transparent mb-2">
                  🔐 Security & Compliance
                </h2>
                <p className="text-gray-400">Enterprise-grade security with MFA, audit logging, and compliance monitoring</p>
              </div>
              <SecurityDashboard />
            </div>
          )}

          {currentPage === 'debug' && (
            <div className="space-y-6">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold bg-gradient-to-r from-yellow-400 to-orange-400 bg-clip-text text-transparent mb-2">
                  🛠️ Debug Dashboard
                </h2>
                <p className="text-gray-400">Test backend endpoints and authentication status</p>
              </div>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-xl flex items-center justify-center">
                      <span className="text-xl">🔌</span>
                    </div>
                    <h3 className="text-xl font-semibold text-blue-300">Backend Endpoints</h3>
                  </div>
                  <div className="space-y-3">
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/auth/test');
                          const data = await response.json();
                          showToast('success', `Auth Controller Response: ${JSON.stringify(data)}`);
                        } catch (error) {
                          showToast('error', `Auth Controller Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-blue-600/20 to-indigo-600/20 text-blue-300 hover:from-blue-600/30 hover:to-indigo-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-blue-600/20 border border-blue-600/30"
                      title="Test auth controller"
                    >
                      🔐 Test Auth Controller
                    </button>
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/api/assets/test');
                          const data = await response.json();
                          showToast('success', `Assets Controller Response: ${JSON.stringify(data)}`);
                        } catch (error) {
                          showToast('error', `Assets Controller Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-green-600/20 to-emerald-600/20 text-green-300 hover:from-green-600/30 hover:to-emerald-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-green-600/20 border border-green-600/30"
                      title="Test assets controller (public)"
                    >
                      📊 Test Assets Controller
                    </button>
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/api/assets/debug');
                          const data = await response.json();
                          showToast('success', `Debug Endpoint Response: ${JSON.stringify(data)}`);
                        } catch (error) {
                          showToast('error', `Debug Endpoint Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-purple-600/20 to-pink-600/20 text-purple-300 hover:from-purple-600/30 hover:to-pink-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-purple-600/20 border border-purple-600/30"
                      title="Test debug endpoint (public)"
                    >
                      🔍 Test Debug Endpoint
                    </button>
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/api/assets', {
                            headers: authService.getAuthHeader()
                          });
                          if (response.ok) {
                            const data = await response.json();
                            showToast('success', `Assets Response: ${JSON.stringify(data)}`);
                          } else {
                            showToast('error', `Assets Error: ${response.status} ${response.statusText}`);
                          }
                        } catch (error) {
                          showToast('error', `Assets Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-yellow-600/20 to-orange-600/20 text-yellow-300 hover:from-yellow-600/30 hover:to-orange-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-yellow-600/20 border border-yellow-600/30"
                      title="Get all assets (requires auth)"
                    >
                      📊 Get Assets (Auth Required)
                    </button>
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/api/assets/with-prices', {
                            headers: authService.getAuthHeader()
                          });
                          if (response.ok) {
                            const data = await response.json();
                            showToast('success', `Assets with Prices Response: ${JSON.stringify(data)}`);
                          } else {
                            showToast('error', `Assets with Prices Error: ${response.status} ${response.statusText}`);
                          }
                        } catch (error) {
                          showToast('error', `Assets with Prices Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-teal-600/20 to-cyan-600/20 text-teal-300 hover:from-teal-600/30 hover:to-cyan-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-teal-600/20 border border-teal-600/30"
                      title="Get all assets with current prices (requires auth)"
                    >
                      📈 Get Assets with Prices (Auth Required)
                    </button>
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/api/portfolio-rebalancing/health', {
                            headers: authService.getAuthHeader()
                          });
                          if (response.ok) {
                            const data = await response.json();
                            showToast('success', `Portfolio Rebalancing Health: ${JSON.stringify(data)}`);
                          } else {
                            showToast('error', `Portfolio Rebalancing Health Error: ${response.status} ${response.statusText}`);
                          }
                        } catch (error) {
                          showToast('error', `Portfolio Rebalancing Health Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-orange-600/20 to-red-600/20 text-orange-300 hover:from-orange-600/30 hover:to-red-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-orange-600/20 border border-orange-600/30"
                      title="Test portfolio rebalancing health endpoint"
                    >
                      ⚖️ Test Portfolio Rebalancing Health
                    </button>
                    <button
                      onClick={async () => {
                        try {
                          const response = await fetch('http://localhost:8080/api/security/health', {
                            headers: authService.getAuthHeader()
                          });
                          if (response.ok) {
                            const data = await response.json();
                            showToast('success', `Security Service Health: ${JSON.stringify(data)}`);
                          } else {
                            showToast('error', `Security Service Health Error: ${response.status} ${response.statusText}`);
                          }
                        } catch (error) {
                          showToast('error', `Security Service Health Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-purple-600/20 to-blue-600/20 text-purple-300 hover:from-purple-600/30 hover:to-blue-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-purple-600/20 border border-purple-600/30"
                      title="Test security service health endpoint"
                    >
                      🔐 Test Security Service Health
                    </button>
                  </div>
                </div>
                
                <div className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 shadow-xl shadow-black/20">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-gradient-to-br from-green-500 to-emerald-600 rounded-xl flex items-center justify-center">
                      <span className="text-xl">🔐</span>
                    </div>
                    <h3 className="text-xl font-semibold text-green-300">Authentication</h3>
                  </div>
                  <div className="space-y-3">
                    <button
                      onClick={async () => {
                        try {
                          const isAuthValid = await authService.testAuth();
                          showToast('success', `Auth Status: ${isAuthValid ? '✅ Valid' : '❌ Invalid'}`);
                        } catch (error) {
                          showToast('error', `Auth Test Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
                        }
                      }}
                      className="w-full px-4 py-3 rounded-xl text-sm font-medium bg-gradient-to-r from-green-600/20 to-emerald-600/20 text-green-300 hover:from-green-600/30 hover:to-emerald-600/30 transition-all duration-300 hover:shadow-lg hover:shadow-green-600/20 border border-green-600/30"
                      title="Test authentication with backend"
                    >
                      🔍 Test Auth (Frontend)
                    </button>

                  </div>
                </div>
              </div>
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