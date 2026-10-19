import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AccountModal from '../AccountModal';
import { Account } from '../../types';

const api = vi.hoisted(() => ({ requestDeletion: vi.fn(), confirmDeletion: vi.fn() }));
vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>();
  return { ...actual, accountService: api };
});
const { ApiError } = await import('../../services/api');

const account: Account = {
  username: 'trader', email: 'trader@example.com', initialBankroll: 10000, bankrollSetAt: '2026-10-19T10:00:00Z',
  bankrollRequired: false, cash: 3500, netInvested: 6500, minimumBankroll: 6500, hasPortfolio: true,
};

describe('AccountModal', () => {
  beforeEach(() => {
    api.requestDeletion.mockReset();
    api.confirmDeletion.mockReset();
  });

  it('shows the bankroll and the cash left', () => {
    render(<AccountModal account={account} onClose={() => {}} onDeleted={() => {}} />);
    expect(screen.getByText('$10,000.00')).toBeInTheDocument();
    expect(screen.getByText('$3,500.00')).toBeInTheDocument();
  });

  it('deletes the account only after the password and the emailed code', async () => {
    api.requestDeletion.mockResolvedValue({ message: 'We emailed a confirmation code to t***@example.com.', expiresInSeconds: 900 });
    api.confirmDeletion.mockResolvedValue(undefined);
    const onDeleted = vi.fn();
    render(<AccountModal account={account} onClose={() => {}} onDeleted={onDeleted} />);

    await userEvent.click(screen.getByRole('button', { name: 'Delete Account' }));
    await userEvent.type(screen.getByLabelText(/Confirm your password/), 'Correct-Horse-1');
    await userEvent.click(screen.getByRole('button', { name: 'Email me a code' }));
    expect(api.requestDeletion).toHaveBeenCalledWith('Correct-Horse-1');
    expect(await screen.findByText('We emailed a confirmation code to t***@example.com.')).toBeInTheDocument();
    expect(onDeleted).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText('Confirmation code'), '12a34-56');
    expect(screen.getByLabelText('Confirmation code')).toHaveValue('123456');
    await userEvent.click(screen.getByRole('button', { name: 'Permanently delete my account' }));
    expect(api.confirmDeletion).toHaveBeenCalledWith('123456');
    expect(onDeleted).toHaveBeenCalled();
  });

  it('keeps the account when the password or code is wrong', async () => {
    api.requestDeletion.mockRejectedValueOnce(new ApiError('Incorrect password.', 401, 'INVALID_PASSWORD'));
    const onDeleted = vi.fn();
    render(<AccountModal account={account} onClose={() => {}} onDeleted={onDeleted} />);
    await userEvent.click(screen.getByRole('button', { name: 'Delete Account' }));
    await userEvent.type(screen.getByLabelText(/Confirm your password/), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Email me a code' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect password.');

    api.requestDeletion.mockResolvedValue({ message: 'Code sent.', expiresInSeconds: 900 });
    api.confirmDeletion.mockRejectedValue(new ApiError('Incorrect code. 4 attempts left.', 400, 'INVALID_CODE'));
    await userEvent.type(screen.getByLabelText(/Confirm your password/), 'right');
    await userEvent.click(screen.getByRole('button', { name: 'Email me a code' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Permanently delete my account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the 6-digit code');
    await userEvent.type(screen.getByLabelText('Confirmation code'), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Permanently delete my account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect code. 4 attempts left.');
    expect(onDeleted).not.toHaveBeenCalled();
  });
});
