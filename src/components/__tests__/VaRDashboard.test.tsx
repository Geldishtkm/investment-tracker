import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import VaRDashboard, { VaRCalculation } from '../VaRDashboard';

const auth = vi.hoisted(() => ({ authFetch: vi.fn() }));
vi.mock('../../services/authService', () => ({ authService: auth }));

const calc = (overrides: Partial<VaRCalculation> = {}): VaRCalculation => ({
  confidenceLevel: 0.95, timeHorizon: 1, portfolioValue: 10000, investedValue: 5089, cashValue: 4911, exposure: 0.5089,
  historicalVaR: 180, parametricVaR: 190, monteCarloVaR: 195, conditionalVaR: 260, volatility: 0.012, annualizedVolatility: 0.23,
  skewness: -0.2, kurtosis: 4.1, expectedReturn: 0.0002, assetWeights: { Bitcoin: 0.5089 }, assetReturns: { Bitcoin: 0.0004 },
  historicalReturns: Array.from({ length: 100 }, (_, i) => (i - 50) / 2000), observations: 100,
  ...overrides,
});
const ok = (body: unknown) => ({ ok: true, status: 200, json: async () => body });

describe('VaRDashboard', () => {
  beforeEach(() => { auth.authFetch.mockReset(); });

  it('measures the whole account: coins plus riskless cash', async () => {
    auth.authFetch.mockResolvedValue(ok(calc()));
    render(<VaRDashboard onAddAsset={() => {}} />);
    const account = await screen.findByTestId('var-account');
    expect(account).toHaveTextContent('$10,000.00');
    expect(account).toHaveTextContent('coins $5,089 · cash $4,911');
    expect(screen.getByTestId('var-headline')).toHaveTextContent('1.80% of value'); // $180 of $10,000

    const cash = screen.getByTestId('var-cash-row');
    expect(cash).toHaveTextContent('49.1%');
    expect(cash).toHaveTextContent('riskless');
    const table = screen.getByRole('table', { name: 'Exposure breakdown' });
    // A −50% shock hits only the coins: $2,544.50, 25.4% of the account
    expect(within(table).getAllByRole('row').at(-1)).toHaveTextContent('-$2,544.50');
  });

  it('recalculates when positions change', async () => {
    auth.authFetch.mockResolvedValue(ok(calc()));
    const { rerender } = render(<VaRDashboard onAddAsset={() => {}} refreshKey={0} />);
    await screen.findByTestId('var-account');
    auth.authFetch.mockResolvedValue(ok(calc({ portfolioValue: 10000, investedValue: 9000, cashValue: 1000 })));
    rerender(<VaRDashboard onAddAsset={() => {}} refreshKey={1} />);
    expect(await screen.findByText('coins $9,000 · cash $1,000')).toBeInTheDocument();
    expect(auth.authFetch).toHaveBeenCalledTimes(2);
  });
});
