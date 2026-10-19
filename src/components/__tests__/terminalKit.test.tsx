import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { compactUsd, pct, pctPoints, qty, toneOf, usd } from '../ui/format';
import Term from '../ui/Term';
import { Delta, Sparkline } from '../ui/kit';
import { histogram, quantile, riskLevel } from '../VaRDashboard';

describe('number formatting', () => {
  it('formats money, compact money, percentages and quantities consistently', () => {
    expect(usd(1234.5)).toBe('$1,234.50');
    expect(usd(0.000123)).toBe('$0.000123');
    expect(usd(null)).toBe('—');
    expect(compactUsd(1.5e12)).toBe('$1.50T');
    expect(compactUsd(45.6e9)).toBe('$45.60B');
    expect(compactUsd(-2_500_000)).toBe('-$2.50M');
    expect(pct(0.1234)).toBe('12.34%');
    expect(pct(0.05, 1, true)).toBe('+5.0%');
    expect(pct(-0.05, 1, true)).toBe('-5.0%');
    expect(pctPoints(3.2)).toBe('+3.20%');
    expect(qty(0.00012345)).toBe('0.00012345');
    expect(toneOf(-1)).toBe('down');
    expect(toneOf(0.001, 0.01)).toBe('flat');
  });
});

describe('Term', () => {
  it('explains a quantitative term on focus and hides it again on blur', () => {
    render(<Term term="HHI" />);
    const trigger = screen.getByRole('button', { name: 'HHI' });
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    fireEvent.focus(trigger);
    expect(screen.getByRole('tooltip')).toHaveTextContent('sum of squared portfolio weights');
    expect(trigger).toHaveAttribute('aria-describedby', screen.getByRole('tooltip').id);
    fireEvent.blur(trigger);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('renders unknown terms as plain text', () => {
    render(<Term term="Something else" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByText('Something else')).toBeInTheDocument();
  });
});

describe('Delta and Sparkline', () => {
  it('colours and marks gains and losses', () => {
    const { container, rerender } = render(<Delta value={0.034} format={v => pct(v, 1, true)} />);
    expect(container.firstChild).toHaveTextContent('▲+3.4%');
    expect(container.firstChild).toHaveClass('text-term-up');
    rerender(<Delta value={-0.02} format={v => pct(v, 1, true)} />);
    expect(container.firstChild).toHaveTextContent('▼-2.0%');
    expect(container.firstChild).toHaveClass('text-term-down');
  });

  it('draws a trend line in the direction of the move', () => {
    render(<Sparkline data={[1, 2, 3]} label="BTC trend" />);
    expect(screen.getByRole('img', { name: 'BTC trend' }).querySelector('polyline')).toHaveAttribute('stroke', '#4FD1A5');
  });
});

describe('VaR helpers', () => {
  it('uses the backend risk-level thresholds', () => {
    expect(riskLevel(0.04)).toBe('LOW');
    expect(riskLevel(0.06)).toBe('MEDIUM');
    expect(riskLevel(0.12)).toBe('HIGH');
  });

  it('interpolates quantiles the way the backend does', () => {
    expect(quantile([-0.1, 0, 0.1, 0.2, 0.3], 0.1)).toBeCloseTo(-0.06, 10);
    expect(quantile([1, 2, 3], 0.5)).toBe(2);
  });

  it('bins returns and marks the tail beyond the VaR cutoff', () => {
    const returns = [-0.1, -0.05, 0, 0.02, 0.05, 0.1];
    const bins = histogram(returns, 4, -0.05);
    expect(bins.reduce((s, b) => s + b.count, 0)).toBe(6);
    expect(bins[0].tail).toBe(true);
    expect(bins[3].tail).toBe(false);
  });
});
