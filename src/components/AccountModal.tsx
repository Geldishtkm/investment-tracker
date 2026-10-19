import React, { useState } from 'react';
import { AlertTriangle, Trash2, X } from 'lucide-react';
import { Account } from '../types';
import { accountService } from '../services/api';

const usd = (v: number | null) =>
  v == null ? '—' : v.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 });

interface Props {
  account: Account;
  onClose: () => void;
  /** Called once the account no longer exists. */
  onDeleted: () => void;
}

type Step = 'idle' | 'password' | 'code';

/** Account settings: the bankroll and cash, and deleting the account after an emailed confirmation code. */
const AccountModal: React.FC<Props> = ({ account, onClose, onDeleted }) => {
  const [step, setStep] = useState<Step>('idle');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  };

  const requestCode = (event: React.FormEvent) => {
    event.preventDefault();
    if (!password) return setError('Enter your password.');
    void run(async () => {
      const result = await accountService.requestDeletion(password);
      setSentTo(result.message);
      setPassword('');
      setStep('code');
    });
  };

  const confirm = (event: React.FormEvent) => {
    event.preventDefault();
    if (!/^\d{6}$/.test(code.trim())) return setError('Enter the 6-digit code from the email.');
    void run(async () => {
      await accountService.confirmDeletion(code.trim());
      onDeleted();
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-term-raised p-4" role="dialog" aria-modal="true"
         aria-labelledby="account-title">
      <div className="w-full max-w-lg bg-term-raised border border-term-line2 rounded-lg p-6 space-y-5">
        <div className="flex items-center justify-between">
          <h2 id="account-title" className="text-xl font-bold text-term-text">Account</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="text-term-muted hover:text-term-text"><X size={20} /></button>
        </div>

        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div><dt className="text-term-dim">Username</dt><dd className="text-term-text">{account.username}</dd></div>
          <div><dt className="text-term-dim">Email</dt><dd className="text-term-text break-all">{account.email ?? '—'}</dd></div>
          <div><dt className="text-term-dim">Starting bankroll (S₀)</dt><dd className="text-term-text">{usd(account.initialBankroll)}</dd></div>
          <div><dt className="text-term-dim">Cash available</dt><dd className="text-term-accent font-semibold">{usd(account.cash)}</dd></div>
          <div><dt className="text-term-dim">Invested (buys − sells)</dt><dd className="text-term-text">{usd(account.netInvested)}</dd></div>
          <div><dt className="text-term-dim">Bankroll set</dt>
            <dd className="text-term-text">{account.bankrollSetAt ? new Date(account.bankrollSetAt).toLocaleDateString() : '—'}</dd></div>
        </dl>

        <section className="rounded-md border border-term-down/50 bg-term-down-soft p-4 space-y-3" aria-labelledby="danger-heading">
          <h3 id="danger-heading" className="font-semibold text-term-down flex items-center gap-2">
            <AlertTriangle size={18} /> Delete account
          </h3>
          <p className="text-sm text-term-muted">
            Permanently erases your account, holdings, trade history, bankroll and settings. This can't be undone.
          </p>

          {step === 'idle' && (
            <button type="button" onClick={() => { setStep('password'); setError(null); }}
                    className="px-4 py-2 rounded-lg bg-term-down-soft hover:bg-term-down-soft text-term-text font-semibold flex items-center gap-2">
              <Trash2 size={16} /> Delete Account
            </button>
          )}

          {step === 'password' && (
            <form onSubmit={requestCode} noValidate className="space-y-3">
              <label className="block text-sm text-term-muted">
                Confirm your password and we'll email you a confirmation code
                <input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)}
                       className="mt-1 w-full px-3 py-2 bg-term-raised border border-term-line2 rounded-lg text-term-text" />
              </label>
              <div className="flex gap-2">
                <button type="submit" disabled={busy}
                        className="px-4 py-2 rounded-lg bg-term-down-soft hover:bg-term-down-soft text-term-text font-semibold disabled:opacity-50">
                  {busy ? 'Sending…' : 'Email me a code'}
                </button>
                <button type="button" onClick={() => { setStep('idle'); setError(null); }} className="px-4 py-2 text-term-muted hover:text-term-text">
                  Cancel
                </button>
              </div>
            </form>
          )}

          {step === 'code' && (
            <form onSubmit={confirm} noValidate className="space-y-3">
              {sentTo && <p className="text-sm text-term-muted">{sentTo}</p>}
              <label className="block text-sm text-term-muted">
                Confirmation code
                <input inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code}
                       onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
                       className="mt-1 w-full px-3 py-2 bg-term-raised border border-term-line2 rounded-lg text-term-text tracking-[0.4em] font-mono" />
              </label>
              <div className="flex gap-2">
                <button type="submit" disabled={busy}
                        className="px-4 py-2 rounded-lg bg-term-down-soft hover:bg-term-down-soft text-term-text font-semibold disabled:opacity-50">
                  {busy ? 'Deleting…' : 'Permanently delete my account'}
                </button>
                <button type="button" onClick={() => { setStep('password'); setCode(''); setError(null); }}
                        className="px-4 py-2 text-term-muted hover:text-term-text">
                  Send a new code
                </button>
              </div>
            </form>
          )}

          {error && <p className="text-sm text-term-down" role="alert">{error}</p>}
        </section>
      </div>
    </div>
  );
};

export default AccountModal;
