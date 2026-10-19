import React, { useState, useEffect } from 'react';
import { accountService, assetService, NewAsset } from './services/api';
import { authService, SESSION_EXPIRED_EVENT, SessionExpiredError } from './services/authService';
import { Account, Asset, AssetWithPrice, User } from './types';
import PortfolioPage from './components/PortfolioPage';
import PortfolioEmptyState, { AssetsLoadError } from './components/PortfolioEmptyState';
import CoinsPage from './components/CoinsPage';
import AnalyticsPage from './components/AnalyticsPage';
import VaRDashboard from './components/VaRDashboard';
import PortfolioRebalancingDashboard from './components/PortfolioRebalancingDashboard';
import InstitutionalEvaluationPage from './components/InstitutionalEvaluationPage';
import LoginPage from './components/LoginPage';
import RegisterPage from './components/RegisterPage';
import BankrollModal from './components/BankrollModal';
import AccountModal from './components/AccountModal';


// Notification for the app shell
const AppToast: React.FC<{
  type: 'success' | 'error' | 'info';
  message: string;
  onClose: () => void;
}> = ({ type, message, onClose }) => {
  const accent = type === 'success' ? 'border-l-term-up' : type === 'error' ? 'border-l-term-down' : 'border-l-term-accent';
  return (
    <div role={type === 'error' ? 'alert' : 'status'}
         className={`flex min-w-[300px] max-w-[420px] items-start gap-3 rounded-md border border-term-line2 border-l-2 ${accent} bg-term-raised px-4 py-3 shadow-2xl shadow-black/60`}>
      <p className="flex-1 text-sm leading-relaxed text-term-text">{message}</p>
      <button onClick={onClose} aria-label="Dismiss" className="rounded p-0.5 text-term-dim hover:text-term-text">✕</button>
    </div>
  );
};

/** Sections of the terminal, in tab order. */
const PAGES: { key: Page; label: string }[] = [
  { key: 'portfolio', label: 'Portfolio' },
  { key: 'coins', label: 'Top Coins' },
  { key: 'analytics', label: 'Analytics' },
  { key: 'var', label: 'VaR' },
  { key: 'rebalancing', label: 'Rebalancing' },
  { key: 'institutional', label: 'Evaluation' },
];

const PAGE_TITLES: Record<Page, { title: string; subtitle: string }> = {
  portfolio: { title: 'Portfolio', subtitle: 'Positions, cash and performance against your starting bankroll' },
  coins: { title: 'Markets', subtitle: 'Top 500 coins by market capitalisation · quotes refresh every 10 minutes' },
  analytics: { title: 'Analytics', subtitle: 'Risk-adjusted performance, diversification and per-asset attribution' },
  var: { title: 'Value at Risk', subtitle: 'Loss distribution, tail risk and exposure by asset' },
  rebalancing: { title: 'Rebalancing', subtitle: 'Target weights from MVO or Black-Litterman, and the trades to reach them' },
  institutional: { title: 'Institutional Evaluation', subtitle: 'Trader Index Score (0–1000) across risk, behavior, consistency and execution' },
};

type Page = 'portfolio' | 'coins' | 'analytics' | 'var' | 'rebalancing' | 'institutional';
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
  // Set when assets could not be loaded, so an outage isn't mistaken for an empty portfolio
  const [assetsLoadError, setAssetsLoadError] = useState<string | null>(null);
  const [assetsWithPrices, setAssetsWithPrices] = useState<AssetWithPrice[]>([]);
  // booting: checking the saved session at startup; loading: (re)loading positions
  const [booting, setBooting] = useState(true);
  const [loading, setLoading] = useState(false);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [toastCounter, setToastCounter] = useState(0);
  // The paper-trading account: S₀ and cash. Until S₀ is set the bankroll modal blocks the app.
  const [account, setAccount] = useState<Account | null>(null);
  const [showAccount, setShowAccount] = useState(false);

  const loadAccount = async () => {
    try {
      setAccount(await accountService.getAccount());
    } catch (error) {
      console.warn('Account details unavailable:', error);
    }
  };

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
              setIsAuthenticated(true);
              void loadAssets();
            } else {
              // An ended session was already cleared by authService; after a network error the
              // saved tokens are kept so the next visit can still renew them
              setIsAuthenticated(false);
              setCurrentUser(null);
              setAssets([]);
              setAssetsWithPrices([]);
            }
          } catch (error) {
            setIsAuthenticated(false);
            setCurrentUser(null);
            setAssets([]);
            setAssetsWithPrices([]);
          }
        } else {
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
        setBooting(false);
      }
    };

    checkAuth();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- check the saved session once on startup
  }, []);

  // An expired or rejected token anywhere in the app ends the session and shows the login page
  useEffect(() => {
    const onSessionExpired = (event: Event) => {
      setIsAuthenticated(false);
      setCurrentUser(null);
      setAccount(null);
      setShowAccount(false);
      setAssets([]);
      setAssetsWithPrices([]);
      setCurrentPage('portfolio');
      setCurrentAuthPage('login');
      showToast('info', (event as CustomEvent<string>).detail || 'Your session has expired. Please log in again.');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- register the listener once
  }, []);

  const loadAssets = async () => {
    void loadAccount();
    try {
      setLoading(true);
      
      // Bring stored prices up to date first (one batched CoinGecko request on the backend).
      // If market data is unavailable the portfolio still loads with the last known prices.
      try {
        const refresh = await assetService.refreshPrices();
        if (refresh.stale?.length) {
          // Coming back after a break while CoinGecko is unreachable: say the values aren't live
          const since = refresh.oldestPriceAt ? ` (last updated ${new Date(refresh.oldestPriceAt).toLocaleString()})` : '';
          showToast('info', `Live prices unavailable for ${refresh.stale.join(', ')}; showing last known prices${since}.`);
        }
      } catch (refreshError) {
        if (refreshError instanceof SessionExpiredError) throw refreshError;
        console.warn('Price refresh skipped:', refreshError);
      }

      const assetsData = await assetService.getAllAssets();
      setAssets(assetsData);
      setAssetsLoadError(null);
      
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
      if (error instanceof SessionExpiredError) return; // the login page and a notice are shown instead
      setAssetsLoadError(error instanceof Error ? error.message : 'Unknown error');
      showToast('error', 'Failed to load assets: ' + (error instanceof Error ? error.message : 'Unknown error'));
    } finally {
      setLoading(false);
    }
  };

  /** Buys a new position from the market grid; errors are thrown to the order ticket that placed it. */
  const handleBuy = async (asset: NewAsset) => {
    await assetService.addAsset(asset);
    showToast('success', `Bought ${asset.quantity} ${asset.name}.`);
    void loadAssets();
  };

  const handleDeleteAsset = async (id: number, assetName: string) => {
    try {
      await assetService.deleteAsset(id);
      await loadAssets();
      showToast('success', `${assetName} deleted successfully!`);
    } catch (error) {
      console.error('Error deleting asset:', error);
      if (error instanceof SessionExpiredError) return;
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
    showToast('success', 'Email verified! Please log in.');
    setCurrentAuthPage('login');
  };

  const handleLogout = () => {
    void authService.logout();
    setIsAuthenticated(false);
    setCurrentUser(null);
    setAccount(null);
    setShowAccount(false);
    setAssets([]);
    setAssetsWithPrices([]);
    setCurrentPage('portfolio');
    showToast('success', 'Logged out successfully');
  };

  const handleAccountDeleted = () => {
    // The server already ended every session; just clear this browser's copy
    authService.clearAuth();
    setIsAuthenticated(false);
    setCurrentUser(null);
    setAccount(null);
    setShowAccount(false);
    setAssets([]);
    setAssetsWithPrices([]);
    setCurrentPage('portfolio');
    showToast('success', 'Your account and all its data have been permanently deleted.');
  };

  const showToast = (type: 'success' | 'error' | 'info', message: string) => {
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}-${toastCounter}`;
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
  if (booting) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-term-bg" role="status" aria-label="Loading">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-term-line2 border-t-term-accent" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-term-bg">
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
        
        {/* Notifications */}
        <div className="fixed top-4 right-4 z-50 space-y-2" aria-live="polite">
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

  const header = PAGE_TITLES[currentPage];
  const goToMarkets = () => setCurrentPage('coins');
  const equity = account?.cash != null
    ? account.cash + assetsWithPrices.reduce((s, a) => s + a.quantity * (a.currentPrice || a.pricePerUnit), 0)
    : null;

  return (
    <div className="min-h-screen text-term-text">
      {/* Terminal bar */}
      <header className="sticky top-0 z-40 border-b border-term-line bg-term-bg/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-[1800px] items-center gap-6 px-4 sm:px-6">
          <div className="flex shrink-0 items-center gap-2">
            <span className="flex h-6 w-6 items-center justify-center rounded bg-term-accent text-[11px] font-bold text-term-bg">PT</span>
            <span className="hidden text-sm font-semibold tracking-tight text-term-text sm:inline">Portfolio Tracker</span>
            <span className="hidden rounded border border-term-line2 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-term-dim lg:inline">Paper</span>
          </div>

          <nav className="flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto" aria-label="Sections">
            {PAGES.map(p => (
              <button key={p.key} onClick={() => setCurrentPage(p.key)} aria-current={currentPage === p.key ? 'page' : undefined}
                      className={`relative whitespace-nowrap px-3 py-4 text-sm font-medium transition-colors ${
                        currentPage === p.key ? 'text-term-text' : 'text-term-muted hover:text-term-text'}`}>
                {p.label}
                {currentPage === p.key && <span className="absolute inset-x-3 bottom-0 h-0.5 rounded-full bg-term-accent" />}
              </button>
            ))}
          </nav>

          <div className="flex shrink-0 items-center gap-4">
            {account?.cash != null && (
              <div className="hidden items-center gap-4 border-r border-term-line pr-4 md:flex" data-testid="account-widget">
                <div className="text-right">
                  <div className="text-[10px] font-medium uppercase tracking-wider text-term-dim">Equity</div>
                  <div className="num text-sm text-term-text">{usdShort(equity)}</div>
                </div>
                <div className="text-right">
                  <div className="text-[10px] font-medium uppercase tracking-wider text-term-dim">Cash</div>
                  <div className="num text-sm text-term-up">{usdShort(account.cash)}</div>
                </div>
              </div>
            )}
            <button onClick={() => { void loadAccount(); setShowAccount(true); }}
                    className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-term-muted hover:bg-term-hover hover:text-term-text">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-term-raised text-[11px] font-semibold uppercase text-term-text">
                {currentUser?.username?.[0] ?? '?'}
              </span>
              <span className="hidden lg:inline">{currentUser?.username}</span>
              <span className="sr-only">Account</span>
            </button>
            <button onClick={handleLogout} className="rounded-md px-2 py-1.5 text-sm text-term-muted hover:bg-term-hover hover:text-term-down">
              Logout
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[1800px] px-4 py-6 sm:px-6">
        <div className="mb-5">
          <h1 className="text-xl font-semibold tracking-tight text-term-text">{header.title}</h1>
          <p className="mt-0.5 text-sm text-term-muted">{header.subtitle}</p>
        </div>

        {currentPage === 'portfolio' && (
          <PortfolioPage assets={assetsWithPrices} account={account} loading={loading} loadError={assetsLoadError}
                         onRetry={loadAssets} onAddAsset={goToMarkets} onChanged={loadAssets}
                         onDelete={handleDeleteAsset} onShowToast={showToast} />
        )}

        {currentPage === 'coins' && (
          <CoinsPage cash={account?.cash ?? null} onBuy={handleBuy}
                     heldNames={assets.map(a => a.name)} />
        )}

        {currentPage !== 'portfolio' && currentPage !== 'coins' && currentPage !== 'institutional' && assetsLoadError && (
          <AssetsLoadError message={assetsLoadError} onRetry={loadAssets} />
        )}
        {currentPage !== 'portfolio' && currentPage !== 'coins' && currentPage !== 'institutional' && !assetsLoadError
          && !loading && assets.length === 0 && <PortfolioEmptyState onAddAsset={goToMarkets} />}

        {currentPage === 'analytics' && !assetsLoadError && assets.length > 0 && (
          <AnalyticsPage onAddAsset={goToMarkets} />
        )}
        {currentPage === 'var' && !assetsLoadError && assets.length > 0 && (
          <VaRDashboard onAddAsset={goToMarkets} />
        )}
        {currentPage === 'rebalancing' && !assetsLoadError && assets.length > 0 && (
          <PortfolioRebalancingDashboard onAddAsset={goToMarkets} onTraded={loadAssets} onShowToast={showToast} />
        )}
        {currentPage === 'institutional' && (
          assetsLoadError
            ? <AssetsLoadError message={assetsLoadError} onRetry={loadAssets} />
            // Not gated on current holdings: closed positions still have a trade history to evaluate
            : <InstitutionalEvaluationPage onAddAsset={goToMarkets} />
        )}
      </main>

      {account?.bankrollRequired && (
        <BankrollModal account={account} onLogout={handleLogout}
                       onSaved={(saved) => {
                         setAccount(saved);
                         showToast('success', `Your starting bankroll is set. You have ${(saved.cash ?? 0).toLocaleString('en-US', { style: 'currency', currency: 'USD' })} to trade with.`);
                         loadAssets();
                       }} />
      )}
      {showAccount && account && (
        <AccountModal account={account} onClose={() => setShowAccount(false)} onDeleted={handleAccountDeleted} />
      )}

      {/* Notifications */}
      <div className="fixed right-4 top-16 z-[70] space-y-2" aria-live="polite">
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

/** $12.3K / $1.25M for the header widget. */
function usdShort(v: number | null): string {
  if (v == null || !Number.isFinite(v)) return '—';
  return v >= 1e6 ? `$${(v / 1e6).toFixed(2)}M` : v.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
}

export default App;
