import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AnalyticsPage from '../AnalyticsPage';

const api = vi.hoisted(() => ({ getAllAnalytics: vi.fn(), recordTrade: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, assetService: api };
});

const analytics = {
  totalValue: 10000, totalInitialInvestment: 9000, totalAssets: 1, roi: 1000, riskMetricsAvailable: false,
  assetBreakdown: [{ id: 7, name: 'Bitcoin', quantity: 0.125, currentPrice: 80000, purchasePrice: 72000,
                     currentValue: 10000, initialInvestment: 9000, individualRoi: 11.1 }],
};

describe('AnalyticsPage trading', () => {
  beforeEach(() => {
    api.getAllAnalytics.mockReset();
    api.recordTrade.mockReset();
    api.getAllAnalytics.mockResolvedValue(analytics);
  });

  it('sells a position from its attribution row and refreshes everything after', async () => {
    api.recordTrade.mockResolvedValue({ asset: null, positionClosed: true });
    const onTraded = vi.fn(), onShowToast = vi.fn();
    render(<AnalyticsPage onAddAsset={() => {}} cash={250} onTraded={onTraded} onShowToast={onShowToast} />);
    await userEvent.click(await screen.findByTestId('attribution-Bitcoin'));
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Bitcoin' })).getByRole('button', { name: 'Sell / Liquidate' }));
    const ticket = screen.getByRole('dialog', { name: 'Trade Bitcoin' });
    expect(within(ticket).getByLabelText(/Quantity/)).toHaveValue(0.125);
    expect(within(ticket).getByTestId('ticket-realized')).toHaveTextContent('+$1,000.00');
    await userEvent.click(within(ticket).getByRole('button', { name: 'Record sell' }));

    expect(api.recordTrade).toHaveBeenCalledWith(7, expect.objectContaining({ side: 'SELL', quantity: 0.125, price: 80000 }));
    expect(onShowToast).toHaveBeenCalledWith('success', expect.stringContaining('$10,000.00 credited to cash (+$1,000.00 realized)'));
    expect(onTraded).toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
