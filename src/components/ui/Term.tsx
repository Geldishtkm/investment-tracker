import React, { useId, useRef, useState } from 'react';
import Popover from './Popover';

/** Plain-language definitions for the quantitative terms used across the terminal. */
export const GLOSSARY: Record<string, string> = {
  'S₀': 'Your starting bankroll: the paper-trading capital you began with. Returns are measured against it.',
  Equity: 'Cash plus the market value of your holdings.',
  'Unrealized P&L': 'Gain or loss on positions you still hold, at current prices versus your average cost.',
  'Avg cost': 'Average price paid per unit, across all buys still held.',
  Allocation: "The holding's share of the portfolio's market value.",
  Sharpe: 'Sharpe ratio: annualized return above the risk-free rate, divided by annualized volatility. Above 1 is good; below 0 means the risk was not paid for.',
  Sortino: 'Sortino ratio: like Sharpe, but only downside volatility (returns below the risk-free rate) counts as risk.',
  Volatility: 'Annualized standard deviation of daily returns: how much the value typically swings in a year.',
  Beta: 'Sensitivity to the market benchmark (Bitcoin): 1.0 moves with it, above 1 amplifies its moves.',
  HHI: 'Herfindahl–Hirschman index: the sum of squared portfolio weights. 1.00 is a single holding; 0.20 is five equal holdings.',
  'N_eff': 'Effective number of holdings, 1 / HHI: how many equal-sized positions the portfolio behaves like.',
  'ρ̄': 'Average pairwise correlation of daily returns, weighted by position size. Near 1 means holdings move in lockstep.',
  'Independent bets': 'Diversification ratio squared: how many uncorrelated positions the portfolio’s risk is equivalent to.',
  Diversification: 'Score from 0 to 100 combining concentration (HHI) and correlation between holdings.',
  VaR: 'Value at Risk: the loss the portfolio should not exceed over the horizon at the chosen confidence level.',
  'Historical VaR': 'VaR read directly from the distribution of past returns, with no assumption about their shape.',
  'Parametric VaR': 'VaR assuming returns are normally distributed, from the portfolio’s mean and volatility.',
  'Monte Carlo VaR': 'VaR from thousands of simulated portfolio paths drawn from the assets’ joint return distribution.',
  CVaR: 'Conditional VaR (expected shortfall): the average loss in the worst cases beyond VaR.',
  'Expected return': 'Mean daily return of the portfolio over the history, annualized where stated.',
  'Market cap': 'Circulating supply × price.',
  'Volume 24h': 'Value traded across exchanges in the last 24 hours. Low volume relative to market cap means thinner liquidity.',
  Drift: 'Difference between the current weight and the target weight.',
  MVO: 'Mean-variance optimization (Markowitz): weights that maximize return for the chosen level of risk.',
  'Black-Litterman': 'Starts from market-cap weights and tilts them toward your own views on each asset.',
  'Risk tolerance': '0 favours the minimum-variance portfolio; 1 favours maximum expected return.',
  TIS: 'Trader Index Score: 0–1000 across four pillars of 250 points: risk control, behavior, consistency and execution.',
  'Profit factor': 'Gross realized profit divided by gross realized loss on closed trades.',
};

interface TermProps {
  /** Glossary key; the visible label defaults to it. */
  term: keyof typeof GLOSSARY | string;
  children?: React.ReactNode;
  className?: string;
}

/**
 * A quantitative term with a definition on hover or keyboard focus. Unknown terms render as plain text.
 */
const Term: React.FC<TermProps> = ({ term, children, className = '' }) => {
  const [open, setOpen] = useState(false);
  const id = useId();
  const button = useRef<HTMLButtonElement>(null);
  const definition = GLOSSARY[term];
  const label = children ?? term;
  if (!definition) return <span className={className}>{label}</span>;
  return (
    <span className={`relative inline-flex items-center ${className}`}
          onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button ref={button} type="button" aria-describedby={open ? id : undefined}
              onFocus={() => setOpen(true)} onBlur={() => setOpen(false)}
              onKeyDown={e => { if (e.key === 'Escape') setOpen(false); }}
              className="cursor-help border-b border-dotted border-term-dim/70 leading-tight text-inherit [text-transform:inherit] [letter-spacing:inherit] hover:border-term-muted focus:outline-none focus-visible:border-term-accent">
        {label}
      </button>
      {open && (
        <Popover anchor={button.current} id={id} width={256}>
          {definition}
        </Popover>
      )}
    </span>
  );
};

export default Term;
