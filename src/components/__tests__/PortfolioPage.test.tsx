import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PortfolioPage from '../PortfolioPage';
import { resetMarketDataCache } from '../../hooks/useMarketData';
import { Account, AssetWithPrice, Coin } from '../../types';

const api = vi.hoisted(() => ({ getTopCoins: vi.fn(), recordTrade: vi.fn(), updateAsset: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, assetService: api };
});

const coin = (id: string, name: string, symbol: string, price: number, change: number): Coin => ({
  id, name, symbol, image: '', current_price: price, market_cap: 1e9, price_change_percentage_24h: change,
  sparkline_in_7d: { price: [price * 0.9, price] },
});

const assets: AssetWithPrice[] = [
  { id: 1, name: 'Bitcoin', quantity: 0.1, pricePerUnit: 80000, currentPrice: 80000, purchasePricePerUnit: 70000, initialInvestment: 7000 },
  { id: 2, name: 'Ethereum', quantity: 1, pricePerUnit: 2000, currentPrice: 2000, purchasePricePerUnit: 2500, initialInvestment: 2500 },
];
const account: Account = {
  username: 't', email: 't@x.io', initialBankroll: 10000, bankrollSetAt: null, bankrollRequired: false,
  cash: 500, netInvested: 9500, minimumBankroll: 9500, hasPortfolio: true,
};

const renderPage = (overrides: Partial<React.ComponentProps<typeof PortfolioPage>> = {}) =>
  render(<PortfolioPage assets={assets} account={account} loading={false} loadError={null} onRetry={() => {}}
                        onAddAsset={() => {}} onChanged={() => {}} onDelete={() => {}} onShowToast={() => {}} {...overrides} />);

describe('PortfolioPage', () => {
  beforeEach(() => {
    resetMarketDataCache();
    api.getTopCoins.mockReset();
    api.recordTrade.mockReset();
    api.getTopCoins.mockResolvedValue([coin('bitcoin', 'Bitcoin', 'btc', 80000, 2.5), coin('ethereum', 'Ethereum', 'eth', 2000, -1.2)]);
  });

  it('shows equity, cash and return on S₀ from the bankroll', async () => {
    renderPage();
    const strip = screen.getByTestId('account-strip');
    expect(strip).toHaveTextContent('$10,500.00');      // 500 cash + 8,000 BTC + 2,000 ETH
    expect(strip).toHaveTextContent('+5.00%');          // vs S₀ $10,000
    expect(strip).toHaveTextContent('$500.00');
    expect(strip).toHaveTextContent('$500.00');         // unrealized: +1,000 BTC − 500 ETH
  });

  it('lists positions by value with allocation, P&L and the 24h change from market data', async () => {
    renderPage();
    const rows = within(screen.getByRole('table', { name: 'Positions' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Bitcoin');
    expect(rows[1]).toHaveTextContent('80.0%');
    expect(rows[1]).toHaveTextContent('+14.29%');
    expect(rows[2]).toHaveTextContent('Ethereum');
    expect(rows[2]).toHaveTextContent('-20.00%');
    expect(await within(rows[1]).findByText('+2.50%')).toBeInTheDocument();
    expect(within(rows[1]).getByRole('img', { name: 'Bitcoin 7-day trend' })).toBeInTheDocument();
  });

  it('sorts by a column header', async () => {
    renderPage();
    await userEvent.click(within(screen.getByRole('table', { name: 'Positions' })).getByRole('button', { name: /Unrealized P&L/ }));
    const rows = within(screen.getByRole('table', { name: 'Positions' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Bitcoin');
    await userEvent.click(within(screen.getByRole('table', { name: 'Positions' })).getByRole('button', { name: /Unrealized P&L/ }));
    expect(within(screen.getByRole('table', { name: 'Positions' })).getAllByRole('row')[1]).toHaveTextContent('Ethereum');
  });

  it('opens the order ticket on the chosen side from the row', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: 'Sell Ethereum' }));
    const dialog = screen.getByRole('dialog', { name: 'Trade Ethereum' });
    expect(within(dialog).getByRole('button', { name: 'Sell' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(dialog).getByRole('button', { name: 'Record sell' })).toBeInTheDocument();
  });

  it('shows skeletons while loading and the empty state without positions', () => {
    const { rerender } = renderPage({ assets: [], loading: true });
    expect(screen.getAllByRole('status', { name: 'Loading' }).length).toBeGreaterThan(0);
    rerender(<PortfolioPage assets={[]} account={account} loading={false} loadError={null} onRetry={() => {}}
                            onAddAsset={() => {}} onChanged={() => {}} onDelete={() => {}} onShowToast={() => {}} />);
    expect(screen.getByText('Your portfolio is currently empty')).toBeInTheDocument();
  });
});
