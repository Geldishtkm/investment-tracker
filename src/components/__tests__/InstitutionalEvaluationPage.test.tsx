import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InstitutionalEvaluationPage, { STALE_RETRY_MS } from '../InstitutionalEvaluationPage';
import { scoreFixture } from './fixtures';
import { resetScoreCache } from '../../hooks/useScore';

const api = vi.hoisted(() => ({ getInstitutionalScore: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, assetService: api };
});
const { ApiError } = await import('../../services/api');

describe('InstitutionalEvaluationPage', () => {
  beforeEach(() => {
    resetScoreCache();
    api.getInstitutionalScore.mockReset();
  });

  it('shows the 0–1000 gauge, the rank tier badge and the provisional label', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);

    const gauge = await screen.findByRole('meter', { name: 'Trader Index Score' });
    expect(gauge).toHaveAttribute('aria-valuenow', '566');
    expect(gauge).toHaveAttribute('aria-valuemax', '1000');
    const badge = screen.getByTestId('tier-badge');
    expect(badge).toHaveTextContent('Tier 6');
    expect(badge).toHaveTextContent('Systematic Swing Trader');
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
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({ flags: [], provisional: false, pillarsScored: 4 }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    expect(await screen.findByText(/No behavioral or risk issues detected/)).toBeInTheDocument();
    expect(screen.queryByText(/Provisional/)).not.toBeInTheDocument();
  });

  it('highlights the current tier in the ladder', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    await screen.findByRole('meter');
    const current = document.querySelector('[aria-current="true"]');
    expect(current).toHaveTextContent('6. Systematic Swing Trader');
    expect(current).toHaveTextContent('525–599');
  });

  it('shows score optimization tips and milestone progress instead of caps or locks', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture());
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const panel = await screen.findByRole('region', { name: 'Score optimization tips' });

    expect(within(panel).getByTestId('milestone')).toHaveTextContent('34 pts to Tier 5 · Disciplined Quantitative Analyst');
    expect(within(panel).getByRole('progressbar', { name: 'Progress to next tier' })).toHaveAttribute('aria-valuenow', '55');
    const sizing = within(panel).getByTestId('tip-position-sizing');
    expect(sizing).toHaveTextContent('+19 pts');
    expect(sizing).toHaveTextContent('Reduce your largest position (38% of capital) to 15% or less.');
    expect(sizing).toHaveTextContent('Risk · Position sizing: largest 38.0% of capital in Bitcoin');
    expect(within(panel).getByTestId('tip-profit-concentration')).toHaveTextContent('+15 pts');

    // No caps, locks or tier requirement checklists anywhere
    expect(screen.queryByText(/capped|Locked|Tier requirements|Advancement/i)).not.toBeInTheDocument();
  });

  it('says when the top tier is reached and nothing is left to gain', async () => {
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({ score: 950, milestone: null, tips: [] }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const panel = await screen.findByRole('region', { name: 'Score optimization tips' });
    expect(panel).toHaveTextContent('Top tier reached.');
    expect(panel).toHaveTextContent('Every scored metric is at or near full marks.');
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

  it('puts an info tooltip on every pillar metric and explains coverage scaling', async () => {
    const component = (key: string, name: string, points: number, weight: number, available = true) => ({
      key, name, available, value: available ? 1 : null, displayValue: available ? 'shown' : 'Not enough data', weight,
      score: available ? points / weight : 0, points: available ? points : 0, detail: available ? 'how' : 'Trading volume unavailable.',
    });
    const base = scoreFixture();
    api.getInstitutionalScore.mockResolvedValue(scoreFixture({
      pillars: base.pillars.map(p => p.key !== 'execution' ? p : {
        ...p, points: 250, coverage: 0.572, components: [
          component('velocity', 'Trading velocity', 143, 143),
          component('liquidity', 'Liquidity impact', 0, 107, false),
        ],
      }),
    }));
    render(<InstitutionalEvaluationPage onAddAsset={() => {}} />);
    const exec = await screen.findByTestId('pillar-execution');
    for (const name of ['Trading velocity', 'Liquidity impact']) {
      expect(within(exec).getByRole('button', { name: `About ${name}` })).toBeInTheDocument();
    }
    expect(screen.getByTestId('coverage-execution'))
      .toHaveTextContent('57% of this pillar\'s metrics have data · scaled to 250 from the 143 of 143 points with data, so missing data costs nothing');
  });
});
