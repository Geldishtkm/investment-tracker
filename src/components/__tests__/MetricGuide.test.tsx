import { describe, it, expect } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { METRIC_GUIDE } from '../institutional/metricGuide';
import InfoTip from '../ui/InfoTip';

/** Every component key the scoring engine can send (from TraderScoring.java). */
const ENGINE_KEYS = 'bankroll-return benchmark-beta concentration correlation deployment-pacing disposition fomo independent-bets liquidity panic position-sizing profit-factor sharpe smoothness sortino speculative-accumulation velocity volatility'.split(' ');

describe('metric guide', () => {
  it('explains every metric the engine scores, with what it means and how to improve it', () => {
    expect(ENGINE_KEYS.length).toBe(18);
    for (const key of ENGINE_KEYS) {
      const g = METRIC_GUIDE[key];
      expect(g, key).toBeDefined();
      expect(g.what.length, key).toBeGreaterThan(30);
      expect(g.improve.length, key).toBeGreaterThan(30);
    }
  });

  it('opens on hover or focus with both sections, and closes on Escape', () => {
    render(<InfoTip title="Sharpe ratio" {...METRIC_GUIDE.sharpe} />);
    const button = screen.getByRole('button', { name: 'About Sharpe ratio' });
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    fireEvent.focus(button);
    const tip = screen.getByRole('tooltip');
    expect(within(tip).getByText('What it means')).toBeInTheDocument();
    expect(tip).toHaveTextContent('return you earn for every unit of risk');
    expect(within(tip).getByText('How to improve it')).toBeInTheDocument();
    expect(button).toHaveAttribute('aria-describedby', tip.id);
    fireEvent.keyDown(button, { key: 'Escape' });
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });
});
