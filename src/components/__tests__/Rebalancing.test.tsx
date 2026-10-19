import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PortfolioRebalancingDashboard, { suggestTrades } from '../PortfolioRebalancingDashboard';
import { AssetWithPrice } from '../../types';
import { todayLocal } from '../../utils/dates';

const api = vi.hoisted(() => ({ getAllAssetsWithPrices: vi.fn(), recordTrade: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, assetService: api };
});

const assets: AssetWithPrice[] = [
  { id: 1, name: 'Bitcoin', quantity: 0.1, pricePerUnit: 80000, currentPrice: 80000, purchasePricePerUnit: 70000, initialInvestment: 7000 },
  { id: 2, name: 'Ethereum', quantity: 1, pricePerUnit: 2000, currentPrice: 2000, purchasePricePerUnit: 2000, initialInvestment: 2000 },
];

describe('suggestTrades', () => {
  it('moves each holding to its target weight, sells first, within the band', () => {
    const trades = suggestTrades(assets, { Bitcoin: 0.5, Ethereum: 0.5 }, 0.01);
    expect(trades.map(t => [t.side, t.asset.name])).toEqual([['SELL', 'Bitcoin'], ['BUY', 'Ethereum']]);
    expect(trades[0].quantity).toBeCloseTo(0.0375, 8);   // 8,000 → 5,000
    expect(trades[1].quantity).toBeCloseTo(1.5, 8);      // 2,000 → 5,000
    expect(suggestTrades(assets, { Bitcoin: 0.795, Ethereum: 0.205 }, 0.01)).toEqual([]);
  });

  it('sells the whole position for a zero target', () => {
    const [sell] = suggestTrades(assets, { Bitcoin: 1, Ethereum: 0 }, 0.01).filter(t => t.side === 'SELL');
    expect(sell.asset.name).toBe('Ethereum');
    expect(sell.quantity).toBe(1);
  });
});

describe('PortfolioRebalancingDashboard', () => {
  beforeEach(() => {
    api.getAllAssetsWithPrices.mockReset();
    api.recordTrade.mockReset();
    api.getAllAssetsWithPrices.mockResolvedValue(assets);
    api.recordTrade.mockResolvedValue({ asset: null, positionClosed: false });
  });

  it('compares target with actual weights and requires targets to add up to 100%', async () => {
    render(<PortfolioRebalancingDashboard onAddAsset={() => {}} />);
    const btc = await screen.findByTestId('target-row-Bitcoin');
    expect(btc).toHaveTextContent('80.0%');
    const input = within(btc).getByRole('spinbutton', { name: 'Bitcoin target percent' });
    await userEvent.clear(input);
    await userEvent.type(input, '50');
    expect(screen.getByTestId('target-sum')).toHaveTextContent('70.0%');
    expect(screen.getByRole('button', { name: /Execute 0 trades/ })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Normalize' }));
    expect(screen.getByTestId('target-sum')).toHaveTextContent('100.0%');
  });

  it('executes the suggested trades in one click after confirmation, sells first', async () => {
    const onTraded = vi.fn();
    render(<PortfolioRebalancingDashboard onAddAsset={() => {}} onTraded={onTraded} />);
    const btc = await screen.findByTestId('target-row-Bitcoin');
    const eth = screen.getByTestId('target-row-Ethereum');
    await userEvent.clear(within(btc).getByRole('spinbutton', { name: 'Bitcoin target percent' }));
    await userEvent.type(within(btc).getByRole('spinbutton', { name: 'Bitcoin target percent' }), '50');
    await userEvent.clear(within(eth).getByRole('spinbutton', { name: 'Ethereum target percent' }));
    await userEvent.type(within(eth).getByRole('spinbutton', { name: 'Ethereum target percent' }), '50');
    expect(within(btc).getByText('SELL')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Execute 2 trades' }));
    const dialog = screen.getByRole('dialog', { name: 'Confirm rebalancing trades' });
    expect(api.recordTrade).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Execute trades' }));

    expect(await screen.findByRole('list', { name: 'Execution results' })).toHaveTextContent('✓ Sell 0.0375 Bitcoin');
    expect(api.recordTrade.mock.calls[0]).toEqual([1, { side: 'SELL', quantity: 0.0375, price: 80000, date: todayLocal() }]);
    expect(api.recordTrade.mock.calls[1][1]).toMatchObject({ side: 'BUY', price: 2000 });
    expect(onTraded).toHaveBeenCalled();
  });
});
