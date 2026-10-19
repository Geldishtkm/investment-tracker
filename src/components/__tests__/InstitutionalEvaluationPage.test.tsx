import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InstitutionalEvaluationPage, { STALE_RETRY_MS } from '../InstitutionalEvaluationPage';
import { scoreFixture } from './fixtures';
import { resetScoreCache } from '../../hooks/useScore';

const api = vi.hoisted(() => ({ getInstitutionalScore: vi.fn(), setRiskTarget: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, assetService: api };
});
const { ApiError } = await import('../../services/api');

describe('InstitutionalEvaluationPage', () => {
  beforeEach(() => {
    resetScoreCache();
    api.getInstitutionalScore.mockReset();
    api.setRiskTarget.mockReset();
  });

  it('shows the 0–1000 gauge, the rank tier badge and the provisional label', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    const gauge = await screen.findByRole('meter', { name: 'Trader Index Score' });
    expect(gauge).toHaveAttribute('aria-valuenow', '499');
    expect(gauge).toHaveAttribute('aria-valuemax', '1000');
    const badge = screen.getByTestId('tier-badge');
    expect(badge).toHaveTextContent('Tier 7');
    expect(badge).toHaveTextContent('Tactical Retail Trader');
    expect(screen.getByText('Provisional · based on 3 of 4 pillars')).toBeInTheDocument();
  });

  it('breaks the score down into the four pillars, marking unscored ones', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    expect(await screen.findByTestId('pillar-risk')).toHaveTextContent('97 / 250');
    expect(screen.getByTestId('pillar-consistency')).toHaveTextContent('78 / 250');
    expect(screen.getByTestId('pillar-execution')).toHaveTextContent('250 / 250');
    const behavior = screen.getByTestId('pillar-behavior');
    expect(behavior).toHaveTextContent('Not scored');
    expect(behavior).toHaveTextContent('Needs dated sells.');
    expect(screen.getByLabelText('Pillar scores radar chart')).toBeInTheDocument();
  });

  it('renders flagged mistakes as diagnostic table rows with coaching, and data gaps separately', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    const card = await screen.findByRole('row', { name: 'Disposition Effect Flagged' });
    expect(card).toHaveTextContent('Losers held 3.2x longer than winners');
    expect(card).toHaveTextContent('High');
    expect(card).toHaveTextContent('Coaching: Decide the exit before you enter.');
    // INFO flags are "how to unlock more of the score", not mistakes
    expect(screen.queryByRole('row', { name: 'Behavioral Psychology Not Scored Yet' })).not.toBeInTheDocument();
    expect(screen.getByText('Record your buys and sells with their dates.')).toBeInTheDocument();
    expect(screen.getByText(/Liquidity impact: largest trade/)).toBeInTheDocument();
  });

  it('shows the Risk & Diversification card under the pillar breakdown, and hides it without holdings', async () => {
    api.getInstitutionalScore.mockResolvedValueOnce(scoreFixture());
    const { unmount } = render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const card = await screen.findByTestId('diversification-card');
    expect(within(card).getByRole('heading', { name: 'Risk & Diversification' })).toBeInTheDocument();
    unmount();
    resetScoreCache(); // a new evaluation, not the one just cached

    api.getInstitutionalScore.mockResolvedValueOnce(scoreFixture({ diversification: null }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    await screen.findByRole('meter', { name: 'Trader Index Score' });
    expect(screen.queryByTestId('diversification-card')).not.toBeInTheDocument();
  });

  it('shows position-sizing findings with the capital share at risk', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({ flags: [{
      id: 'position-sizing', severity: 'HIGH', pillar: 'risk', title: 'High-Risk Concentration & Impulse',
      detail: 'High concentration risk: a single trade allocated 25.0% of your total portfolio ($25 of $100) to Pepe, a micro-cap asset ($40.0M market cap).',
      recommendation: 'Size speculative positions at 1–2% of capital and never more than 5%.',
    }] }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const card = await screen.findByRole('row', { name: 'High-Risk Concentration & Impulse' });
    expect(card).toHaveTextContent('High concentration risk: a single trade');
    expect(within(card).getByText('Position sizing')).toBeInTheDocument();
    expect(card).toHaveTextContent('allocated 25.0% of your total portfolio');
    expect(card).toHaveTextContent('micro-cap asset');
    expect(card).toHaveTextContent('1–2% of capital');
    expect(within(card).getByText('High')).toBeInTheDocument();
  });

  it('says so when no issues are flagged', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({ flags: [], provisional: false, pillarsScored: 4, caps: [] }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    expect(await screen.findByText(/No behavioral or risk issues detected/)).toBeInTheDocument();
    expect(screen.queryByText(/Provisional/)).not.toBeInTheDocument();
  });

  it('highlights the current tier in the ladder', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    await screen.findByRole('meter');
    const current = document.querySelector('[aria-current="true"]');
    expect(current).toHaveTextContent('7. Tactical Retail Trader');
  });

  it('explains a capped score and what each locked tier still requires', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    const caps = await screen.findByRole('region', { name: /Score capped at 499 \(pillar total 566\)/ });
    expect(caps).toHaveTextContent('No sells recorded');
    expect(caps).toHaveTextContent('Provisional score');

    const gate = screen.getByTestId('gate-600');
    expect(gate).toHaveTextContent('Tier 5 and above');
    expect(within(gate).getAllByLabelText('met')).toHaveLength(1);
    expect(within(gate).getAllByLabelText('not met')).toHaveLength(1);
    expect(within(gate).getByTestId('req-Trade Count')).toHaveTextContent('Trade Count: 4 trades (Target: ≥ 10 trades)');
    expect(gate).not.toHaveTextContent(/day track record|Profit Factor|Sharpe/);
  });

  it('presents limits as a compliance check: cooling-period caps and locked tiers as checklists', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({
      score: 299, rawScore: 541,
      caps: [
        { id: 'impulsive-deployment', maxScore: 299, title: 'Rapid Capital Deployment Warning',
          detail: 'Deploying over 75% of capital in a single day triggers a risk cooling period to encourage disciplined scaling. It lifts after 5 further trades each under 10% of capital (2 of 5 so far), or after 90 days. On 2026-10-19, 99.0% went into 3 buys.' },
        { id: 'gate-600', maxScore: 599, title: 'Tier 5 Advancement Locked', detail: 'Still needed: …' },
      ],
    }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    await screen.findByRole('region', { name: /Score capped at 299 \(pillar total 541\)/ });

    const cooling = screen.getByTestId('cap-impulsive-deployment');
    expect(cooling).toHaveTextContent('Rapid Capital Deployment Warning');
    expect(cooling).toHaveTextContent('Score Temporarily Capped at 299');
    expect(cooling).toHaveTextContent('Deploying over 75% of capital in a single day triggers a risk cooling period to encourage disciplined scaling.');
    expect(cooling).toHaveTextContent('It lifts after 5 further trades each under 10% of capital (2 of 5 so far), or after 90 days.');
    expect(cooling).toHaveTextContent('On 2026-10-19, 99.0% went into 3 buys.');

    const lock = screen.getByTestId('cap-gate-600');
    expect(lock).toHaveTextContent('Tier 5 Advancement Locked');
    const checklist = within(lock).getByRole('list', { name: 'Tier 5 Advancement Locked checklist' });
    expect(within(checklist).getByTestId('req-Return on Capital')).toHaveTextContent('Return on Capital: +17.0% (Target: ≥ +5.0%)');
    expect(within(checklist).getByRole('progressbar', { name: 'Trade Count progress' })).toHaveAttribute('aria-valuenow', '40');
    expect(lock).not.toHaveTextContent('Still needed');
  });

  it('shows no cap banner when nothing limits the score', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({ caps: [], score: 566, rawScore: 566 }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    await screen.findByRole('meter');
    expect(screen.queryByText(/Score capped/)).not.toBeInTheDocument();
  });

  it('warns when the evaluation had to use old prices or saved history', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({
      dataFreshness: { pricesAsOf: '2026-09-27T10:00:00Z', stalePrices: ['Bitcoin'], historyThrough: '2026-09-27', historyStale: true },
    }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const notice = await screen.findByRole('status', { name: 'Market data freshness' });
    expect(notice).toHaveTextContent('Live prices unavailable for Bitcoin');
    expect(notice).toHaveTextContent('Price history runs only to 2026-09-27');
  });

  it('reloads by itself while the history is stale, then stops once it is current', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const stale = scoreFixture({ dataFreshness: { pricesAsOf: null, stalePrices: [], historyThrough: '2026-09-27', historyStale: true } });
      api.getInstitutionalScore.mockResolvedValueOnce(stale).mockResolvedValue(scoreFixture());
      render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
      await screen.findByRole('status', { name: 'Market data freshness' });

      await vi.advanceTimersByTimeAsync(STALE_RETRY_MS);
      await waitFor(() => expect(screen.queryByRole('status', { name: 'Market data freshness' })).not.toBeInTheDocument());
      expect(api.getInstitutionalScore).toHaveBeenCalledTimes(2);

      await vi.advanceTimersByTimeAsync(STALE_RETRY_MS * 3);
      expect(api.getInstitutionalScore).toHaveBeenCalledTimes(2); // current now: no more reloads
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows no freshness warning when everything is current', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    await screen.findByRole('meter');
    expect(screen.queryByRole('status', { name: 'Market data freshness' })).not.toBeInTheDocument();
  });

  it('shows the add-assets empty state when there is nothing to evaluate', async () => {
    api.getInstitutionalScore.mockRejectedValue(new ApiError('Your portfolio has no assets.', 422, 'NO_ASSETS'));
    const onAddAsset = vi.fn();
    render(<InstitutionalEvaluationPage onAddAsset={onAddAsset} />);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
    expect(screen.queryByRole('meter')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button'));
    expect(onAddAsset).toHaveBeenCalled();
  });

  it('explains when not enough data exists to score anything', async () => {
    api.getInstitutionalScore.mockRejectedValue(new ApiError('None of the four pillars can be scored yet.', 422, 'INSUFFICIENT_HISTORY'));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Not enough data to score yet');
    expect(alert).toHaveTextContent('None of the four pillars can be scored yet.');
  });

  it('saves a risk target as a fraction and reloads the evaluation', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    api.setRiskTarget.mockResolvedValue(undefined);
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    const input = await screen.findByLabelText('Target annual volatility (%)');
    await userEvent.type(input, '40');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(api.setRiskTarget).toHaveBeenCalledWith(0.4);
    await waitFor(() => expect(api.getInstitutionalScore).toHaveBeenCalledTimes(2));
  });

  it('rejects an out-of-range risk target without calling the API', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    await userEvent.type(await screen.findByLabelText('Target annual volatility (%)'), '900');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    const form = screen.getByRole('form', { name: 'Risk Target' });
    expect(within(form).getByRole('alert')).toHaveTextContent('between 1% and 300%');
    expect(api.setRiskTarget).not.toHaveBeenCalled();
  });
});
