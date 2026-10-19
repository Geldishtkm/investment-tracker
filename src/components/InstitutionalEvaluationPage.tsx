import React, { useCallback, useEffect, useState } from 'react';
import { Award, CheckCircle2, Lightbulb, Lock, RefreshCw, ShieldAlert, Target, Unlock } from 'lucide-react';
import { ApiError, assetService } from '../services/api';
import { DiagnosticFlag, InstitutionalScore, ScoreCap, ScorePillar, TierGate } from '../types';
import PortfolioEmptyState from './PortfolioEmptyState';
import ScoreGauge from './institutional/ScoreGauge';
import PillarRadar from './institutional/PillarRadar';
import DiversificationCard from './institutional/DiversificationCard';
import { PILLAR_SHORT_NAME, SEVERITY_STYLE, tierColor } from './institutional/styles';

interface InstitutionalEvaluationPageProps {
  /** Opens the add-asset flow; shown when there is nothing to evaluate. */
  onAddAsset: () => void;
}

export const STALE_RETRY_MS = 75_000;
const MAX_STALE_RETRIES = 3;

const card = 'bg-gradient-to-br from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6';

const InstitutionalEvaluationPage: React.FC<InstitutionalEvaluationPageProps> = ({ onAddAsset }) => {
  const [data, setData] = useState<InstitutionalScore | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await assetService.getInstitutionalScore());
    } catch (e) {
      setError(e instanceof Error ? e : new Error('Failed to load the evaluation'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Saved history was used because CoinGecko was busy; the backend refetches it in the background,
  // so check again shortly (a few times at most) instead of leaving the user on old data
  const [staleRetries, setStaleRetries] = useState(0);
  const historyStale = data?.dataFreshness?.historyStale ?? false;
  useEffect(() => {
    if (!historyStale || staleRetries >= MAX_STALE_RETRIES) return;
    const timer = setTimeout(() => {
      setStaleRetries(n => n + 1);
      load();
    }, STALE_RETRY_MS);
    return () => clearTimeout(timer);
  }, [historyStale, staleRetries, load]);

  if (loading && !data) {
    return (
      <div className={`${card} text-center text-gray-400`} role="status">
        <RefreshCw className="mx-auto mb-3 animate-spin" size={28} />
        Evaluating your trading…
      </div>
    );
  }

  if (error instanceof ApiError && error.code === 'NO_ASSETS') {
    return <PortfolioEmptyState onAddAsset={onAddAsset} />;
  }

  if (error || !data) {
    const notEnough = error instanceof ApiError && error.code === 'INSUFFICIENT_HISTORY';
    return (
      <div className={`${card} text-center`} role="alert">
        <ShieldAlert className={`mx-auto mb-3 ${notEnough ? 'text-blue-400' : 'text-red-400'}`} size={32} />
        <h3 className="text-xl font-bold text-white mb-2">{notEnough ? 'Not enough data to score yet' : 'Evaluation unavailable'}</h3>
        <p className="text-gray-400 mb-6">{error?.message}</p>
        <button onClick={load} className="px-5 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-medium">Try again</button>
      </div>
    );
  }

  const issues = data.flags.filter(f => f.severity !== 'INFO');
  const notes = data.flags.filter(f => f.severity === 'INFO');

  return (
    <div className="space-y-6">
      {/* Score and pillars at a glance */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className={card} aria-labelledby="tis-heading">
          <h3 id="tis-heading" className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <Award size={20} className="text-amber-300" /> Trader Index Score
          </h3>
          <ScoreGauge score={data.score} maxScore={data.maxScore} tier={data.tier}
                      provisional={data.provisional} pillarsScored={data.pillarsScored} />
        </section>
        <section className={card} aria-labelledby="radar-heading">
          <h3 id="radar-heading" className="text-lg font-semibold text-white mb-2">Four-Pillar Profile</h3>
          <PillarRadar pillars={data.pillars} />
        </section>
      </div>

      <FreshnessNotice freshness={data.dataFreshness} />

      <ScoreCaps score={data.score} rawScore={data.rawScore} caps={data.caps} />

      {/* Pillar breakdown */}
      <section aria-labelledby="breakdown-heading">
        <h3 id="breakdown-heading" className="text-xl font-bold text-white mb-4">Pillar Breakdown</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {data.pillars.map(p => <PillarCard key={p.key} pillar={p} />)}
        </div>
      </section>

      {data.diversification && (
        <DiversificationCard diversification={data.diversification}
                             riskComponents={data.pillars.find(p => p.key === 'risk')?.components ?? []} />
      )}

      {/* Diagnostics */}
      <section aria-labelledby="diagnostics-heading">
        <h3 id="diagnostics-heading" className="text-xl font-bold text-white mb-4 flex items-center gap-2">
          <ShieldAlert size={20} className="text-red-300" /> Diagnostic Assessment
        </h3>
        {issues.length === 0 ? (
          <div className={`${card} text-emerald-300`}>No behavioral or risk issues detected in the data available.</div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {issues.map(f => <DiagnosticCard key={f.id} flag={f} />)}
          </div>
        )}
        {data.strengths.length > 0 && (
          <div className={`${card} mt-4`}>
            <div className="text-sm font-semibold text-emerald-300 mb-2">Strengths</div>
            <ul className="text-sm text-gray-300 space-y-1">
              {data.strengths.map(s => <li key={s}>✓ {s}</li>)}
            </ul>
          </div>
        )}
      </section>

      {/* Unlocking more of the score */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <RiskTargetForm current={data.riskTargetVolatility} onSaved={load} />
        {notes.length > 0 && (
          <section className={card} aria-labelledby="notes-heading">
            <h3 id="notes-heading" className="text-lg font-semibold text-white mb-3 flex items-center gap-2">
              <Lightbulb size={18} className="text-blue-300" /> Improve Score Coverage
            </h3>
            <ul className="space-y-3">
              {notes.map(f => (
                <li key={f.id} className="text-sm">
                  <div className="font-medium text-blue-200">{f.title}</div>
                  <div className="text-gray-400">{f.recommendation}</div>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>

      <TierGates gates={data.gates} />

      <TierLadder tiers={data.tiers} currentRank={data.tier.rank} />

      <p className="text-xs text-gray-500">
        Based on {data.coverage.observations} daily returns, {data.coverage.trades} recorded trades
        ({data.coverage.datedTrades} with known dates) and {data.coverage.closedLots} closed lots.
        Metrics without enough data are left out rather than estimated.
      </p>
    </div>
  );
};

const PillarCard: React.FC<{ pillar: ScorePillar }> = ({ pillar }) => {
  const pct = pillar.available ? pillar.points / pillar.maxPoints : 0;
  return (
    <div className={card} data-testid={`pillar-${pillar.key}`}>
      <div className="flex items-baseline justify-between mb-2">
        <h4 className="font-semibold text-white">{pillar.name}</h4>
        <span className="text-lg font-bold text-cyan-300">
          {pillar.available ? `${Math.round(pillar.points)} / ${pillar.maxPoints}` : 'Not scored'}
        </span>
      </div>
      <div className="h-2 rounded-full bg-gray-700 overflow-hidden mb-1">
        <div className="h-full bg-gradient-to-r from-cyan-500 to-emerald-400" style={{ width: `${pct * 100}%` }} />
      </div>
      <div className="text-xs text-gray-500 mb-4">{Math.round(pillar.coverage * 100)}% of this pillar's metrics have data</div>
      <ul className="space-y-2">
        {pillar.components.map(c => (
          <li key={c.key} className="flex items-start justify-between gap-3 text-sm" title={c.detail}>
            <div>
              <div className={c.available ? 'text-gray-200' : 'text-gray-500'}>{c.name}</div>
              <div className="text-xs text-gray-500">{c.available ? c.displayValue : c.detail}</div>
            </div>
            <div className={`shrink-0 font-mono ${c.available ? 'text-gray-300' : 'text-gray-600'}`}>
              {c.available ? `${c.points.toFixed(0)}/${c.weight}` : '—'}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
};

/** Flags about how much capital a trade or holding put at risk. */
const SIZING_FLAGS = new Set(['position-sizing', 'speculative-accumulation']);

export const DiagnosticCard: React.FC<{ flag: DiagnosticFlag }> = ({ flag }) => {
  const style = SEVERITY_STYLE[flag.severity];
  return (
    <article className={`rounded-2xl border p-5 ${style.card}`} aria-label={flag.title}>
      <div className="flex items-center gap-2 mb-2">
        <span className={`text-xs px-2 py-0.5 rounded-full border ${style.chip}`}>{style.label}</span>
        <span className="text-xs text-gray-400">{PILLAR_SHORT_NAME[flag.pillar]}</span>
        {SIZING_FLAGS.has(flag.id) && (
          <span className="text-xs px-2 py-0.5 rounded-full border border-fuchsia-500/40 bg-fuchsia-500/15 text-fuchsia-200">Position sizing</span>
        )}
      </div>
      <h4 className="font-semibold text-white mb-1">{flag.title}</h4>
      <p className="text-sm text-gray-300 mb-3">{flag.detail}</p>
      <p className="text-sm text-gray-400"><span className="text-gray-200 font-medium">Coaching: </span>{flag.recommendation}</p>
    </article>
  );
};

const RiskTargetForm: React.FC<{ current: number | null; onSaved: () => void }> = ({ current, onSaved }) => {
  const [value, setValue] = useState(current != null ? String(Math.round(current * 1000) / 10) : '');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const save = async (target: number | null) => {
    setSaving(true);
    setMessage(null);
    try {
      await assetService.setRiskTarget(target);
      onSaved();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const pct = parseFloat(value);
    if (!(pct >= 1 && pct <= 300)) {
      setMessage('Enter a target between 1% and 300% a year.');
      return;
    }
    save(pct / 100);
  };

  return (
    <form className={card} onSubmit={submit} noValidate aria-labelledby="target-heading">
      <h3 id="target-heading" className="text-lg font-semibold text-white mb-1 flex items-center gap-2">
        <Target size={18} className="text-emerald-300" /> Risk Target
      </h3>
      <p className="text-sm text-gray-400 mb-4">The annual volatility you aim to run at. Execution Discipline scores how closely you stick to it.</p>
      <div className="flex gap-2">
        <label className="sr-only" htmlFor="risk-target">Target annual volatility (%)</label>
        <input id="risk-target" type="number" min="1" max="300" step="0.5" value={value} placeholder="e.g. 40"
               onChange={e => setValue(e.target.value)}
               className="flex-1 px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        <span className="self-center text-gray-400">%</span>
        <button type="submit" disabled={saving} className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white disabled:opacity-50">Save</button>
        {current != null && (
          <button type="button" disabled={saving} onClick={() => save(null)}
                  className="px-4 py-2 rounded-lg border border-gray-600 text-gray-300 hover:bg-gray-800">Clear</button>
        )}
      </div>
      {message && <p className="text-sm text-red-300 mt-2" role="alert">{message}</p>}
    </form>
  );
};

/** Shown only when the evaluation had to use old prices or saved history (CoinGecko unreachable). */
export const FreshnessNotice: React.FC<{ freshness?: InstitutionalScore['dataFreshness'] }> = ({ freshness }) => {
  if (!freshness || (freshness.stalePrices.length === 0 && !freshness.historyStale)) return null;
  return (
    <div className="rounded-2xl border border-blue-500/40 bg-blue-950/30 p-4 text-sm text-blue-100" role="status"
         aria-label="Market data freshness">
      <div className="font-semibold mb-1">Market data isn't fully up to date</div>
      {freshness.stalePrices.length > 0 && (
        <div>Live prices unavailable for {freshness.stalePrices.join(', ')}
          {freshness.pricesAsOf ? `; valued at prices from ${new Date(freshness.pricesAsOf).toLocaleString()}` : ''}.</div>
      )}
      {freshness.historyStale && freshness.historyThrough && (
        <div>Price history runs only to {freshness.historyThrough}; returns after that date aren't included yet.</div>
      )}
      <div className="text-blue-300/80 mt-1">Fresh data is being fetched in the background; this page updates automatically.</div>
    </div>
  );
};

/** Why the score is lower than the pillar total: the binding cap first, then any others that apply. */
export const ScoreCaps: React.FC<{ score: number; rawScore: number; caps: ScoreCap[] }> = ({ score, rawScore, caps }) => {
  if (caps.length === 0) return null;
  const capped = score < rawScore;
  return (
    <section className="rounded-2xl border border-yellow-500/40 bg-yellow-950/20 p-5" aria-labelledby="caps-heading">
      <h3 id="caps-heading" className="font-semibold text-yellow-200 mb-1 flex items-center gap-2">
        <Lock size={18} /> {capped ? `Score capped at ${score} (pillar total ${rawScore})` : 'Score limits that apply'}
      </h3>
      <p className="text-sm text-gray-400 mb-3">High ranks require proven results: closed profitable trades, real growth and enough history.</p>
      <ul className="space-y-2">
        {caps.map(c => (
          <li key={c.id} className="text-sm">
            <span className="font-medium text-gray-100">{c.title}</span>
            <span className="text-gray-500"> · max {c.maxScore}</span>
            <div className="text-gray-400">{c.detail}</div>
          </li>
        ))}
      </ul>
    </section>
  );
};

const TierGates: React.FC<{ gates: TierGate[] }> = ({ gates }) => (
  <section className={card} aria-labelledby="gates-heading">
    <h3 id="gates-heading" className="text-lg font-semibold text-white mb-3">Tier Requirements</h3>
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      {gates.map(g => (
        <div key={g.minScore} className={`rounded-xl border p-4 ${g.unlocked ? 'border-emerald-500/40' : 'border-gray-700'}`}
             data-testid={`gate-${g.minScore}`}>
          <div className="flex items-center gap-2 font-medium mb-2">
            {g.unlocked ? <Unlock size={16} className="text-emerald-300" /> : <Lock size={16} className="text-gray-400" />}
            <span className={g.unlocked ? 'text-emerald-200' : 'text-gray-200'}>{g.name}</span>
            <span className="text-xs text-gray-500">({g.minScore}+)</span>
          </div>
          <ul className="space-y-1.5">
            {g.requirements.map(r => (
              <li key={r.label} className="text-xs flex gap-2">
                <CheckCircle2 size={14} className={`shrink-0 mt-0.5 ${r.met ? 'text-emerald-400' : 'text-gray-600'}`}
                              aria-label={r.met ? 'met' : 'not met'} />
                <span className={r.met ? 'text-gray-300' : 'text-gray-400'}>
                  {r.label} <span className="text-gray-500">— {r.actual}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  </section>
);

const TierLadder: React.FC<{ tiers: InstitutionalScore['tiers']; currentRank: number }> = ({ tiers, currentRank }) => (
  <section className={card} aria-labelledby="ladder-heading">
    <h3 id="ladder-heading" className="text-lg font-semibold text-white mb-3">Rank Tiers</h3>
    <ol className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1 text-sm">
      {tiers.map(t => (
        <li key={t.rank} aria-current={t.rank === currentRank ? 'true' : undefined}
            className={`flex justify-between px-3 py-1.5 rounded-lg ${t.rank === currentRank ? 'bg-gray-700/70 font-semibold' : ''}`}>
          <span className={t.rank === currentRank ? tierColor(t.rank).text : 'text-gray-400'}>{t.rank}. {t.name}</span>
          <span className="text-gray-500 font-mono">{t.minScore}–{t.maxScore}</span>
        </li>
      ))}
    </ol>
  </section>
);

export default InstitutionalEvaluationPage;
