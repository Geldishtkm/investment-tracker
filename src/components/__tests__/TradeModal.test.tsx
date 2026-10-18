import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TradeModal from '../TradeModal';
import { todayLocal } from '../../utils/dates';

const api = vi.hoisted(() => ({ recordTrade: vi.fn() }));
vi.mock('../../services/api', () => ({ assetService: api }));

const asset = { id: 5, name: 'Solana', quantity: 10, pricePerUnit: 150, purchasePricePerUnit: 120, initialInvestment: 1200 };

describe('TradeModal', () => {
  // Braces matter: a function returned from beforeEach runs as cleanup, which would call the mock again
  beforeEach(() => { api.recordTrade.mockReset(); });

  it('records a buy with quantity, price and date', async () => {
    api.recordTrade.mockResolvedValue({ asset, positionClosed: false });
    const onSaved = vi.fn();
    render(<TradeModal asset={asset} currentPrice={150} onClose={() => {}} onSaved={onSaved} />);

    await userEvent.type(screen.getByLabelText(/Quantity/), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Record buy' }));

    expect(api.recordTrade).toHaveBeenCalledWith(5, { side: 'BUY', quantity: 2, price: 150, date: todayLocal() });
    expect(onSaved).toHaveBeenCalledWith('Bought 2 Solana.');
  });

  it('blocks selling more than is held, without calling the API', async () => {
    render(<TradeModal asset={asset} currentPrice={150} onClose={() => {}} onSaved={() => {}} />);

    await userEvent.click(screen.getByRole('button', { name: 'Sell' }));
    expect(screen.getByText('(you hold 10)')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Quantity/), '11');
    await userEvent.click(screen.getByRole('button', { name: 'Record sell' }));

    expect(screen.getByRole('alert')).toHaveTextContent("you can't sell more than that");
    expect(api.recordTrade).not.toHaveBeenCalled();
  });

  it('reports a closed position after selling everything', async () => {
    api.recordTrade.mockResolvedValue({ asset: null, positionClosed: true });
    const onSaved = vi.fn();
    render(<TradeModal asset={asset} currentPrice={150} onClose={() => {}} onSaved={onSaved} />);

    await userEvent.click(screen.getByRole('button', { name: 'Sell' }));
    await userEvent.type(screen.getByLabelText(/Quantity/), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Record sell' }));

    expect(onSaved).toHaveBeenCalledWith(expect.stringContaining('the position is closed'));
  });

  it('shows the backend error message', async () => {
    api.recordTrade.mockRejectedValue(new Error('The trade date can\'t be in the future.'));
    render(<TradeModal asset={asset} currentPrice={150} onClose={() => {}} onSaved={() => {}} />);
    await userEvent.type(screen.getByLabelText(/Quantity/), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Record buy' }));
    expect(await screen.findByRole('alert')).toHaveTextContent("can't be in the future");
  });
});
