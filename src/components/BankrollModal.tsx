import React, { useState } from 'react';
import { Wallet } from 'lucide-react';
import { Account } from '../types';
import { accountService } from '../services/api';
import { btn, focusRing, input } from './ui/kit';

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
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-[#04060B]/80 p-4 backdrop-blur-md" role="dialog" aria-modal="true"
         aria-labelledby="bankroll-title">
      <form onSubmit={submit} noValidate className="w-full max-w-lg space-y-5 rounded-xl border border-term-line2 bg-term-panel/95 p-6 shadow-[0_24px_64px_-16px_rgba(0,0,0,0.8),inset_0_1px_0_rgba(255,255,255,0.05)] backdrop-blur-xl">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-term-accent/30 bg-term-accent/10">
            <Wallet size={18} className="text-term-accent" />
          </div>
          <h2 id="bankroll-title" className="text-base font-semibold text-term-text">Set your initial paper trading bankroll (S₀):</h2>
        </div>
        <p className="text-sm text-term-muted">
          This is the cash you start with. Every buy is paid from it and every sell returns to it, and your returns are
          measured against it. It's saved to your account and can't be changed later.
        </p>

        <div className="grid grid-cols-3 gap-3" role="radiogroup" aria-label="Starting bankroll">
          {BANKROLL_PRESETS.map(p => {
            const disabled = !reset && p < minimum;
            return (
              <button key={p} type="button" role="radio" aria-checked={choice === p} disabled={disabled}
                      onClick={() => setChoice(p)}
                      className={`num h-10 rounded-md border text-sm font-medium transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-30 ${focusRing} ${
                        choice === p ? 'border-term-accent/70 bg-term-accent/15 text-term-text' : 'border-term-line2 bg-white/[0.03] text-term-muted hover:border-white/20 hover:text-term-text'}`}>
                {usd(p).replace('.00', '')}
              </button>
            );
          })}
        </div>
        <label className="block text-sm text-term-muted">
          <span className="flex items-center gap-2">
            <input type="radio" name="custom" checked={choice === 'custom'} onChange={() => setChoice('custom')} />
            Custom amount (USD)
          </span>
          <input type="number" inputMode="decimal" min={effectiveMin} max={MAX} step="any" value={custom}
                 aria-label="Custom bankroll"
                 onFocus={() => setChoice('custom')} onChange={e => { setCustom(e.target.value); setChoice('custom'); }}
                 placeholder={`${effectiveMin.toLocaleString('en-US')} – ${MAX.toLocaleString('en-US')}`}
                 className={`num mt-1 ${input}`} />
        </label>

        {legacy && (
          <div className="space-y-2 rounded-lg border border-term-warn/30 bg-term-warn/[0.06] p-4 text-sm">
            <p className="text-term-warn">
              Your portfolio already has trades worth {usd(account.minimumBankroll)}, recorded before bankrolls existed.
              Your bankroll must cover them, or you can start over.
            </p>
            <label className="flex items-start gap-2 text-term-text">
              <input type="checkbox" checked={reset} onChange={e => setReset(e.target.checked)} className="mt-1" />
              <span>Start over: permanently remove my current holdings and trade history.</span>
            </label>
          </div>
        )}

        {error && <p className="text-sm text-term-down" role="alert">{error}</p>}

        <button type="submit" disabled={saving}
                className={`${btn.primary} h-10 w-full text-sm`}>
          {saving ? 'Saving…' : Number.isFinite(amount) && amount > 0 ? `Start with ${usd(amount)}` : 'Start trading'}
        </button>
        <button type="button" onClick={onLogout} className={`${btn.ghost} w-full`}>Log out</button>
      </form>
    </div>
  );
};

export default BankrollModal;
