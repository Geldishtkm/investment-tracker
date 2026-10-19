import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import DiversificationCard, { correlationColor } from '../institutional/DiversificationCard';
import { ScoreComponent } from '../../types';
import { diversificationFixture } from './fixtures';

const component = (key: string, name: string, points: number, available = true): ScoreComponent => ({
  key, name, available, value: available ? 1 : null, displayValue: available ? `${name} value` : 'Not enough data',
  weight: 20, score: points / 20, points, detail: available ? 'formula' : 'Needs price history.',
});

const riskComponents = [
  component('sharpe', 'Sharpe ratio', 30),
  component('concentration', 'Concentration (HHI)', 16),
  component('correlation', 'Average pairwise correlation', 1),
  component('independent-bets', 'Independent bets (correlation-adjusted)', 1),
];

describe('DiversificationCard', () => {
  it('shows holdings, effective holdings and correlation-adjusted independent bets', () => {
    render(<DiversificationCard diversification={diversificationFixture()} riskComponents={riskComponents} />);
    const card = screen.getByTestId('diversification-card');
    expect(within(card).getByText('Holdings').nextSibling).toHaveTextContent('3');
    expect(within(card).getByText('Effective holdings').nextSibling).toHaveTextContent('2.6');
    expect(within(card).getByText('N = 1 / HHI · HHI 0.38')).toBeInTheDocument();
    expect(within(card).getByText('Independent bets').nextSibling).toHaveTextContent('1.2');
    expect(within(card).getByText('31')).toBeInTheDocument();
  });

  it('counts only the diversification components toward the Risk pillar points', () => {
    render(<DiversificationCard diversification={diversificationFixture()} riskComponents={riskComponents} />);
    expect(screen.getByText(/Earns 18 of 60 points in the Risk Control pillar/)).toBeInTheDocument();
    expect(screen.queryByText('Sharpe ratio')).not.toBeInTheDocument();
    expect(screen.getByText('Average pairwise correlation')).toBeInTheDocument();
  });

  it('renders the correlation matrix and lists pairs moving in lockstep', () => {
    render(<DiversificationCard diversification={diversificationFixture()} riskComponents={riskComponents} />);
    const matrix = screen.getByRole('table', { name: 'Correlation matrix' });
    expect(within(matrix).getAllByRole('row')).toHaveLength(4);
    expect(within(matrix).getAllByText('0.86')).toHaveLength(2);
    expect(screen.getByText('Bitcoin / Ethereum: 0.86')).toBeInTheDocument();
    expect(screen.getByText('Ethereum / Solana: 0.84')).toBeInTheDocument();
    expect(screen.getByText('Bitcoin / Solana: 0.79')).toBeInTheDocument(); // best diversifying pair
    expect(screen.getByRole('img', { name: /Average correlation 0.82/ })).toBeInTheDocument();
  });

  it('explains when correlations could not be measured', () => {
    const d = diversificationFixture({
      averageCorrelation: null, effectiveBets: null, correlationScore: null, betsScore: null, correlations: null,
      pairs: [], correlationReason: 'Price history for your holdings is unavailable.',
    });
    render(<DiversificationCard diversification={d} riskComponents={[
      component('concentration', 'Concentration (HHI)', 16),
      component('correlation', 'Average pairwise correlation', 0, false),
    ]} />);
    expect(screen.getByText(/Correlation not measured: Price history for your holdings is unavailable/)).toBeInTheDocument();
    expect(screen.getByText('Independent bets').nextSibling).toHaveTextContent('—');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText(/metrics without history are left out/)).toBeInTheDocument();
  });

  it('colours positive correlation slate-red and negative correlation mint, stronger with magnitude', () => {
    expect(correlationColor(0.9)).toMatch(/^rgba\(224, 108, 117/);
    expect(correlationColor(-0.5)).toMatch(/^rgba\(79, 209, 165/);
    const alpha = (c: string) => parseFloat(c.slice(c.lastIndexOf(',') + 1));
    expect(alpha(correlationColor(0.9))).toBeGreaterThan(alpha(correlationColor(0.2)));
  });
});
