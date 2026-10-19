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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true"
         aria-labelledby="account-title">
      <div className="w-full max-w-lg bg-gray-900 border border-gray-700 rounded-2xl p-6 space-y-5">
        <div className="flex items-center justify-between">
          <h2 id="account-title" className="text-xl font-bold text-white">Account</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-white"><X size={20} /></button>
        </div>

        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div><dt className="text-gray-500">Username</dt><dd className="text-white">{account.username}</dd></div>
          <div><dt className="text-gray-500">Email</dt><dd className="text-white break-all">{account.email ?? '—'}</dd></div>
          <div><dt className="text-gray-500">Starting bankroll (S₀)</dt><dd className="text-white">{usd(account.initialBankroll)}</dd></div>
          <div><dt className="text-gray-500">Cash available</dt><dd className="text-emerald-300 font-semibold">{usd(account.cash)}</dd></div>
          <div><dt className="text-gray-500">Invested (buys − sells)</dt><dd className="text-white">{usd(account.netInvested)}</dd></div>
          <div><dt className="text-gray-500">Bankroll set</dt>
            <dd className="text-white">{account.bankrollSetAt ? new Date(account.bankrollSetAt).toLocaleDateString() : '—'}</dd></div>
        </dl>

        <section className="rounded-xl border border-red-700/50 bg-red-950/20 p-4 space-y-3" aria-labelledby="danger-heading">
          <h3 id="danger-heading" className="font-semibold text-red-300 flex items-center gap-2">
            <AlertTriangle size={18} /> Delete account
          </h3>
          <p className="text-sm text-gray-400">
            Permanently erases your account, holdings, trade history, bankroll and settings. This can't be undone.
          </p>

          {step === 'idle' && (
            <button type="button" onClick={() => { setStep('password'); setError(null); }}
                    className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-500 text-white font-semibold flex items-center gap-2">
              <Trash2 size={16} /> Delete Account
            </button>
          )}

          {step === 'password' && (
            <form onSubmit={requestCode} noValidate className="space-y-3">
              <label className="block text-sm text-gray-300">
                Confirm your password and we'll email you a confirmation code
                <input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)}
                       className="mt-1 w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white" />
              </label>
              <div className="flex gap-2">
                <button type="submit" disabled={busy}
                        className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-500 text-white font-semibold disabled:opacity-50">
                  {busy ? 'Sending…' : 'Email me a code'}
                </button>
                <button type="button" onClick={() => { setStep('idle'); setError(null); }} className="px-4 py-2 text-gray-400 hover:text-white">
                  Cancel
                </button>
              </div>
            </form>
          )}

          {step === 'code' && (
            <form onSubmit={confirm} noValidate className="space-y-3">
              {sentTo && <p className="text-sm text-gray-300">{sentTo}</p>}
              <label className="block text-sm text-gray-300">
                Confirmation code
                <input inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code}
                       onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
                       className="mt-1 w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white tracking-[0.4em] font-mono" />
              </label>
              <div className="flex gap-2">
                <button type="submit" disabled={busy}
                        className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-500 text-white font-semibold disabled:opacity-50">
                  {busy ? 'Deleting…' : 'Permanently delete my account'}
                </button>
                <button type="button" onClick={() => { setStep('password'); setCode(''); setError(null); }}
                        className="px-4 py-2 text-gray-400 hover:text-white">
                  Send a new code
                </button>
              </div>
            </form>
          )}

          {error && <p className="text-sm text-red-300" role="alert">{error}</p>}
        </section>
      </div>
    </div>
  );
};

export default AccountModal;
