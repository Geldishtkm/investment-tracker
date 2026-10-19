import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CoinsPage from '../CoinsPage';
import { resetMarketDataCache } from '../../hooks/useMarketData';
import { Coin } from '../../types';
import { todayLocal } from '../../utils/dates';

const api = vi.hoisted(() => ({ getTopCoins: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, assetService: api };
});
const { ApiError } = await import('../../services/api');

const coins: Coin[] = [
  { id: 'bitcoin', name: 'Bitcoin', symbol: 'btc', image: '', current_price: 80000, market_cap: 1.6e12, market_cap_rank: 1,
    total_volume: 40e9, price_change_percentage_24h: 1.5, price_change_percentage_7d_in_currency: -3, sparkline_in_7d: { price: [1, 2] } },
  { id: 'solana', name: 'Solana', symbol: 'sol', image: '', current_price: 100, market_cap: 50e9, market_cap_rank: 5,
    total_volume: 4e9, price_change_percentage_24h: -4.2, price_change_percentage_7d_in_currency: 8 },
];

describe('CoinsPage', () => {
  beforeEach(() => {
    resetMarketDataCache();
    api.getTopCoins.mockReset();
    api.getTopCoins.mockResolvedValue(coins);
  });

  it('shows the market tape and a dense grid with rank, changes, market cap and volume', async () => {
    render(<CoinsPage cash={1000} onBuy={vi.fn()} heldNames={['Bitcoin']} />);
    const btc = await screen.findByTestId('coin-bitcoin');
    expect(btc).toHaveTextContent('$80,000.00');
    expect(btc).toHaveTextContent('+1.50%');
    expect(btc).toHaveTextContent('-3.00%');
    expect(btc).toHaveTextContent('$1.60T');
    expect(btc).toHaveTextContent('$40.00B');
    expect(within(btc).getByText('Held')).toBeInTheDocument();
    expect(screen.getByTestId('market-tape')).toHaveTextContent('1 / 1');
  });

  it('filters by search and sorts by a column', async () => {
    render(<CoinsPage cash={1000} onBuy={vi.fn()} />);
    await screen.findByTestId('coin-bitcoin');
    await userEvent.click(within(screen.getByRole('table', { name: 'Markets' })).getByRole('button', { name: /^24h/ }));
    let rows = within(screen.getByRole('table', { name: 'Markets' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Bitcoin');
    await userEvent.click(within(screen.getByRole('table', { name: 'Markets' })).getByRole('button', { name: /^24h/ }));
    rows = within(screen.getByRole('table', { name: 'Markets' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Solana');
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search coins' }), 'sol');
    expect(screen.queryByTestId('coin-bitcoin')).not.toBeInTheDocument();
  });

  it('quick-buys by USD amount at the market price, and blocks orders above the cash', async () => {
    const onBuy = vi.fn().mockResolvedValue(undefined);
    render(<CoinsPage cash={1000} onBuy={onBuy} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Buy Solana' }));
    const dialog = screen.getByRole('dialog', { name: 'Buy Solana' });

    await userEvent.type(within(dialog).getByLabelText('Amount (USD)', { selector: 'input' }), '2000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Buy SOL' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Insufficient cash available. Current cash balance: $1,000.00');
    expect(onBuy).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('button', { name: '10% of cash' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Buy SOL' }));
    expect(onBuy).toHaveBeenCalledWith({ name: 'Solana', quantity: 1, pricePerUnit: 100, purchasePricePerUnit: 100,
      initialInvestment: 100, purchaseDate: todayLocal() });
  });

  it('keeps the ticket open with the server reason when the buy is refused', async () => {
    const onBuy = vi.fn().mockRejectedValue(new Error('$1.00 is 99% below Solana’s market price'));
    render(<CoinsPage cash={1000} onBuy={onBuy} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Buy Solana' }));
    const dialog = screen.getByRole('dialog', { name: 'Buy Solana' });
    await userEvent.type(within(dialog).getByLabelText('Amount (USD)', { selector: 'input' }), '50');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Buy SOL' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('below Solana’s market price');
  });

  it('renders only the visible window of a 500-coin catalog and follows the scroll', async () => {
    const many: Coin[] = Array.from({ length: 500 }, (_, i) => ({
      id: `coin-${i + 1}`, name: `Coin ${i + 1}`, symbol: `c${i + 1}`, image: '', current_price: 500 - i,
      market_cap: (500 - i) * 1e6, market_cap_rank: i + 1, total_volume: 1e6, price_change_percentage_24h: 0,
    }));
    api.getTopCoins.mockResolvedValue(many);
    render(<CoinsPage cash={1000} onBuy={vi.fn()} />);
    await screen.findByTestId('coin-coin-1');

    const table = screen.getByRole('table', { name: 'Markets' });
    expect(table).toHaveAttribute('aria-rowcount', '501');
    const rendered = within(table).getAllByRole('row').length - 1; // minus the header
    expect(rendered).toBeLessThan(40);
    expect(screen.queryByTestId('coin-coin-300')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Top 500 by market cap' })).toBeInTheDocument();

    const scroller = screen.getByTestId('markets-scroller');
    scroller.scrollTop = 299 * 44;
    fireEvent.scroll(scroller);
    expect(await screen.findByTestId('coin-coin-300')).toBeInTheDocument();
    expect(screen.queryByTestId('coin-coin-1')).not.toBeInTheDocument();

    // Search reaches any coin in the catalog, not only the rendered ones
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search coins' }), 'Coin 487');
    expect(await screen.findByTestId('coin-coin-487')).toBeInTheDocument();
    expect(screen.getByText(/1 match ·/)).toBeInTheDocument();
  });

  describe('click to buy', () => {
    it('opens the ticket from a row click, pre-filled with the asset id, market price, name and today', async () => {
      render(<CoinsPage cash={1000} onBuy={vi.fn()} />);
      await userEvent.click(await screen.findByText('Solana'));
      const dialog = screen.getByRole('dialog', { name: 'Buy Solana' });
      expect(within(dialog).getByTestId('ticket-asset-id')).toHaveTextContent('solana');
      expect(within(dialog).getByTestId('ticket-price')).toHaveTextContent('$100.00');
      expect(within(dialog).getByLabelText('Trade date')).toHaveValue(todayLocal());
    });

    it('opens from the keyboard, and the row buttons open only their own action', async () => {
      render(<CoinsPage cash={1000} onBuy={vi.fn()} />);
      const row = await screen.findByTestId('coin-bitcoin');
      row.focus();
      await userEvent.keyboard('{Enter}');
      expect(screen.getByRole('dialog', { name: 'Buy Bitcoin' })).toBeInTheDocument();
      await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));

      await userEvent.click(within(row).getByRole('button', { name: 'Buy Bitcoin' }));
      expect(screen.getAllByRole('dialog')).toHaveLength(1);
    });

    it('opens the bankroll setup instead when S₀ isn’t set, then the ticket once it is', async () => {
      const onRequireBankroll = vi.fn();
      const { rerender } = render(<CoinsPage cash={null} onBuy={vi.fn()} bankrollRequired onRequireBankroll={onRequireBankroll} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Buy Solana' }));
      expect(onRequireBankroll).toHaveBeenCalledTimes(1);
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      rerender(<CoinsPage cash={10000} onBuy={vi.fn()} bankrollRequired={false} onRequireBankroll={onRequireBankroll} />);
      expect(await screen.findByRole('dialog', { name: 'Buy Solana' })).toBeInTheDocument();
    });

    it('hands off to the bankroll setup when the server says S₀ is missing', async () => {
      const onRequireBankroll = vi.fn();
      const onBuy = vi.fn().mockRejectedValue(new ApiError('Set your starting bankroll before buying.', 409, 'BANKROLL_REQUIRED'));
      render(<CoinsPage cash={null} onBuy={onBuy} onRequireBankroll={onRequireBankroll} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Buy Solana' }));
      await userEvent.type(screen.getByLabelText('Amount (USD)', { selector: 'input' }), '50');
      await userEvent.click(screen.getByRole('button', { name: 'Buy SOL' }));
      expect(onRequireBankroll).toHaveBeenCalled();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('shows the server’s cash balance when it refuses for insufficient funds', async () => {
      const onBuy = vi.fn().mockRejectedValue(new ApiError('Not enough cash', 400, 'INSUFFICIENT_FUNDS', { cash: 42.5, cost: 50 }));
      render(<CoinsPage cash={1000} onBuy={onBuy} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Buy Solana' }));
      const dialog = screen.getByRole('dialog', { name: 'Buy Solana' });
      await userEvent.type(within(dialog).getByLabelText('Amount (USD)', { selector: 'input' }), '50');
      await userEvent.click(within(dialog).getByRole('button', { name: 'Buy SOL' }));
      expect(await within(dialog).findByRole('alert')).toHaveTextContent('Insufficient cash available. Current cash balance: $42.50');
    });
  });
});
