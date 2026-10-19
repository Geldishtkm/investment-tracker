import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import BankrollModal from '../BankrollModal';
import { Account } from '../../types';

const api = vi.hoisted(() => ({ setBankroll: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, accountService: api };
});

const fresh: Account = {
  username: 'trader', email: 't@example.com', initialBankroll: null, bankrollSetAt: null, bankrollRequired: true,
  cash: null, netInvested: 0, minimumBankroll: 0, hasPortfolio: false,
};

describe('BankrollModal', () => {
  beforeEach(() => { api.setBankroll.mockReset(); });

  it('asks for S₀ with $1,000 / $10,000 / $100,000 presets and saves the choice', async () => {
    const saved = { ...fresh, initialBankroll: 10000, bankrollRequired: false, cash: 10000 };
    api.setBankroll.mockResolvedValue(saved);
    const onSaved = vi.fn();
    render(<BankrollModal account={fresh} onSaved={onSaved} onLogout={() => {}} />);

    expect(screen.getByRole('dialog', { name: 'Set your initial paper trading bankroll (S₀):' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '$1,000' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '$100,000' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: '$10,000' }));
    await userEvent.click(screen.getByRole('button', { name: 'Start with $10,000.00' }));

    expect(api.setBankroll).toHaveBeenCalledWith(10000, false);
    expect(onSaved).toHaveBeenCalledWith(saved);
  });

  it('can only be left by logging out', () => {
    const onLogout = vi.fn();
    render(<BankrollModal account={fresh} onSaved={() => {}} onLogout={onLogout} />);
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
    screen.getByRole('button', { name: 'Log out' }).click();
    expect(onLogout).toHaveBeenCalled();
  });

  it('accepts a custom amount and rejects one outside $100 – $10,000,000 without calling the API', async () => {
    api.setBankroll.mockResolvedValue({ ...fresh, bankrollRequired: false });
    render(<BankrollModal account={fresh} onSaved={() => {}} onLogout={() => {}} />);
    const custom = screen.getByRole('spinbutton', { name: 'Custom bankroll' });

    await userEvent.type(custom, '50');
    await userEvent.click(screen.getByRole('button', { name: /Start with/ }));
    expect(screen.getByRole('alert')).toHaveTextContent('between $100.00 and $10,000,000.00');
    expect(api.setBankroll).not.toHaveBeenCalled();

    await userEvent.clear(custom);
    await userEvent.type(custom, '2500');
    await userEvent.click(screen.getByRole('button', { name: 'Start with $2,500.00' }));
    expect(api.setBankroll).toHaveBeenCalledWith(2500, false);
  });

  it('makes accounts with older trades cover them or start over', async () => {
    const legacy = { ...fresh, hasPortfolio: true, netInvested: 28_887_343, minimumBankroll: 28_887_343 };
    api.setBankroll.mockResolvedValue({ ...legacy, bankrollRequired: false });
    render(<BankrollModal account={legacy} onSaved={() => {}} onLogout={() => {}} />);

    expect(screen.getByText(/already has trades worth \$28,887,343\.00/)).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '$10,000' })).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: /Start over/ }));
    expect(screen.getByRole('radio', { name: '$10,000' })).toBeEnabled();
    await userEvent.click(screen.getByRole('radio', { name: '$10,000' }));
    await userEvent.click(screen.getByRole('button', { name: 'Start with $10,000.00' }));
    expect(api.setBankroll).toHaveBeenCalledWith(10000, true);
  });

  it('shows the server reason when saving fails', async () => {
    const { ApiError } = await import('../../services/api');
    api.setBankroll.mockRejectedValue(new ApiError('Your starting bankroll is already set.', 409, 'BANKROLL_ALREADY_SET'));
    render(<BankrollModal account={fresh} onSaved={() => {}} onLogout={() => {}} />);
    await userEvent.click(screen.getByRole('button', { name: 'Start with $1,000.00' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('already set');
  });
});
