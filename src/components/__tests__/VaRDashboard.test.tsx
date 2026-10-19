import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import VaRDashboard, { EXPECTED_RETURN_INFO, VOLATILITY_INFO, VaRCalculation, resetVaRCache } from '../VaRDashboard';

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
  beforeEach(() => { auth.authFetch.mockReset(); resetVaRCache(); });

  it('measures the whole account: coins plus riskless cash', async () => {
    auth.authFetch.mockResolvedValue(ok(calc()));
    render(<VaRDashboard onAddAsset={() => {}} />);
    const account = await screen.findByTestId('var-account');
    expect(account).toHaveTextContent('$10,000.00');
    expect(account).toHaveTextContent('coins $5,089 · cash $4,911');
    expect(screen.getByTestId('var-headline')).toHaveTextContent('1.80% of value'); // $180 of $10,000

    // The exposure breakdown and its price-shock table are gone
    expect(screen.queryByRole('table', { name: 'Exposure breakdown' })).not.toBeInTheDocument();
    expect(screen.queryByText(/shock/i)).not.toBeInTheDocument();
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

  it('reuses a fresh result when the same inputs come back, and Recalculate always reruns', async () => {
    auth.authFetch.mockResolvedValue(ok(calc()));
    render(<VaRDashboard onAddAsset={() => {}} />);
    await screen.findByTestId('var-account');
    fireEvent.click(screen.getByRole('button', { name: '99%' }));
    await screen.findByText(/1-day loss at 99%/);
    fireEvent.click(screen.getByRole('button', { name: '95%' }));
    await screen.findByText(/1-day loss at 95%/);
    expect(auth.authFetch).toHaveBeenCalledTimes(2); // 95% again came from memory

    fireEvent.click(screen.getByRole('button', { name: 'Recalculate' }));
    await screen.findByTestId('var-account');
    await vi.waitFor(() => expect(auth.authFetch).toHaveBeenCalledTimes(3));
  });

  it('shows a clean return profile: volatility and expected return only, each with an explanation', async () => {
    auth.authFetch.mockResolvedValue(ok(calc()));
    render(<VaRDashboard onAddAsset={() => {}} />);
    const profile = await screen.findByTestId('return-profile');
    expect(within(profile).getAllByTestId(/^profile-/)).toHaveLength(2);
    expect(screen.getByTestId('profile-Volatility')).toHaveTextContent('23.0%');
    expect(screen.getByTestId('profile-Volatility')).toHaveTextContent('annualized · 1.20% daily');
    expect(screen.getByTestId('profile-Expected return')).toHaveTextContent('+0.020%');
    expect(screen.getByTestId('profile-Expected return')).toHaveTextContent('mean daily · +7.3% a year projected');
    expect(screen.queryByText(/Skewness|Kurtosis/)).not.toBeInTheDocument();

    fireEvent.focus(within(profile).getByRole('button', { name: 'About Volatility' }));
    expect(screen.getByRole('tooltip')).toHaveTextContent(VOLATILITY_INFO);
    expect(screen.getByRole('tooltip')).not.toHaveTextContent('How to improve it');
    fireEvent.blur(within(profile).getByRole('button', { name: 'About Volatility' }));
    fireEvent.focus(within(profile).getByRole('button', { name: 'About Expected return' }));
    expect(screen.getByRole('tooltip')).toHaveTextContent(EXPECTED_RETURN_INFO);
  });
});
