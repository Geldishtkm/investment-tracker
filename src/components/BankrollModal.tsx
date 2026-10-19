import React, { useState } from 'react';
import { Wallet } from 'lucide-react';
import { Account } from '../types';
import { accountService } from '../services/api';

export const BANKROLL_PRESETS = [1_000, 10_000, 100_000];
const MIN = 100;
const MAX = 10_000_000;

const usd = (v: number) => v.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 });

interface Props {
  account: Account;
  onSaved: (account: Account) => void;
  onLogout: () => void;
}

/**
 * Asks for the starting paper-trading bankroll (S₀) before anything else. It can't be dismissed: buys are
 * paid from this cash, so the app is unusable without it. Accounts with trades recorded before S₀ existed
 * must cover them or start over with an empty portfolio.
 */
const BankrollModal: React.FC<Props> = ({ account, onSaved, onLogout }) => {
  const minimum = Math.max(MIN, account.minimumBankroll);
  const firstPreset = BANKROLL_PRESETS.find(p => p >= minimum);
  const [choice, setChoice] = useState<number | 'custom'>(firstPreset ?? 'custom');
  const [custom, setCustom] = useState('');
  const [reset, setReset] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const effectiveMin = reset ? MIN : minimum;
  const amount = choice === 'custom' ? parseFloat(custom) : choice;
  const legacy = account.hasPortfolio && account.minimumBankroll > 0;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!(amount >= effectiveMin) || !(amount <= MAX)) {
      setError(`Choose an amount between ${usd(effectiveMin)} and ${usd(MAX)}.`);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await accountService.setBankroll(amount, reset));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to set your bankroll');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4" role="dialog" aria-modal="true"
         aria-labelledby="bankroll-title">
      <form onSubmit={submit} noValidate className="w-full max-w-lg bg-gray-900 border border-emerald-600/40 rounded-2xl p-6 space-y-5">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center">
            <Wallet size={22} className="text-white" />
          </div>
          <h2 id="bankroll-title" className="text-xl font-bold text-white">Set your initial paper trading bankroll (S₀):</h2>
        </div>
        <p className="text-sm text-gray-400">
          This is the cash you start with. Every buy is paid from it and every sell returns to it, and your returns are
          measured against it. It's saved to your account and can't be changed later.
        </p>

        <div className="grid grid-cols-3 gap-3" role="radiogroup" aria-label="Starting bankroll">
          {BANKROLL_PRESETS.map(p => {
            const disabled = !reset && p < minimum;
            return (
              <button key={p} type="button" role="radio" aria-checked={choice === p} disabled={disabled}
                      onClick={() => setChoice(p)}
                      className={`py-3 rounded-xl font-semibold border transition-colors disabled:opacity-30 disabled:cursor-not-allowed ${
                        choice === p ? 'bg-emerald-600 border-emerald-400 text-white' : 'bg-gray-800 border-gray-700 text-gray-200 hover:border-emerald-500/60'}`}>
                {usd(p).replace('.00', '')}
              </button>
            );
          })}
        </div>
        <label className="block text-sm text-gray-300">
          <span className="flex items-center gap-2">
            <input type="radio" name="custom" checked={choice === 'custom'} onChange={() => setChoice('custom')} />
            Custom amount (USD)
          </span>
          <input type="number" inputMode="decimal" min={effectiveMin} max={MAX} step="any" value={custom}
                 aria-label="Custom bankroll"
                 onFocus={() => setChoice('custom')} onChange={e => { setCustom(e.target.value); setChoice('custom'); }}
                 placeholder={`${effectiveMin.toLocaleString('en-US')} – ${MAX.toLocaleString('en-US')}`}
                 className="mt-1 w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white" />
        </label>

        {legacy && (
          <div className="rounded-xl border border-yellow-600/40 bg-yellow-950/20 p-4 text-sm space-y-2">
            <p className="text-yellow-200">
              Your portfolio already has trades worth {usd(account.minimumBankroll)}, recorded before bankrolls existed.
              Your bankroll must cover them, or you can start over.
            </p>
            <label className="flex items-start gap-2 text-gray-200">
              <input type="checkbox" checked={reset} onChange={e => setReset(e.target.checked)} className="mt-1" />
              <span>Start over: permanently remove my current holdings and trade history.</span>
            </label>
          </div>
        )}

        {error && <p className="text-sm text-red-300" role="alert">{error}</p>}

        <button type="submit" disabled={saving}
                className="w-full py-3 rounded-xl font-semibold text-white bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50">
          {saving ? 'Saving…' : Number.isFinite(amount) && amount > 0 ? `Start with ${usd(amount)}` : 'Start trading'}
        </button>
        <button type="button" onClick={onLogout} className="w-full text-sm text-gray-500 hover:text-gray-300">Log out</button>
      </form>
    </div>
  );
};

export default BankrollModal;
