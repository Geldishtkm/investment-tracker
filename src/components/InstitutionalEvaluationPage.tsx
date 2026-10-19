import React, { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Circle, Lock, RefreshCw, Unlock } from 'lucide-react';
import { ApiError, assetService } from '../services/api';
import { DiagnosticFlag, InstitutionalScore, ScoreCap, ScorePillar, TierGate } from '../types';
import PortfolioEmptyState from './PortfolioEmptyState';
import ScoreGauge from './institutional/ScoreGauge';
import PillarRadar from './institutional/PillarRadar';
import DiversificationCard from './institutional/DiversificationCard';
import { PILLAR_SHORT_NAME, SEVERITY_STYLE, tierColor } from './institutional/styles';
import Term from './ui/Term';
import { Badge, Metric, Panel, ShareBar, Skeleton, SkeletonRows, btn, input } from './ui/kit';
import { pct } from './ui/format';

interface InstitutionalEvaluationPageProps {
  /** Opens the add-asset flow; shown when there is nothing to evaluate. */
  onAddAsset: () => void;
}

export const STALE_RETRY_MS = 75_000;
const MAX_STALE_RETRIES = 3;

/** Flags about how much capital a trade or holding put at risk. */
const SIZING_FLAGS = new Set(['position-sizing', 'speculative-accumulation', 'impulsive-deployment']);

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

  useEffect(() => { void load(); }, [load]);

  // Saved history was used because CoinGecko was busy; the backend refetches it in the background,
  // so check again shortly (a few times at most) instead of leaving the user on old data
  const [staleRetries, setStaleRetries] = useState(0);
  const historyStale = data?.dataFreshness?.historyStale ?? false;
  useEffect(() => {
    if (!historyStale || staleRetries >= MAX_STALE_RETRIES) return;
    const timer = setTimeout(() => {
      setStaleRetries(n => n + 1);
      void load();
    }, STALE_RETRY_MS);
    return () => clearTimeout(timer);
  }, [historyStale, staleRetries, load]);

  if (loading && !data) {
    return (
      <div className="space-y-4" role="status" aria-label="Evaluating your trading">
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
          <Skeleton className="h-80" /><Skeleton className="h-80" /><Skeleton className="h-80" />
        </div>
        <Panel title="Pillar breakdown" flush><SkeletonRows rows={6} cols={4} /></Panel>
      </div>
    );
  }

  if (error instanceof ApiError && error.code === 'NO_ASSETS') return <PortfolioEmptyState onAddAsset={onAddAsset} />;

  if (error || !data) {
    const notEnough = error instanceof ApiError && error.code === 'INSUFFICIENT_HISTORY';
    return (
      <div className={`rounded-lg border bg-term-panel p-10 text-center ${notEnough ? 'border-term-line' : 'border-term-down/40'}`} role="alert">
        <h3 className="text-base font-semibold text-term-text">{notEnough ? 'Not enough data to score yet' : 'Evaluation unavailable'}</h3>
        <p className="mt-2 text-sm text-term-muted">{error?.message}</p>
        <button onClick={() => void load()} className={`${btn.secondary} mt-5`}>Try again</button>
      </div>
    );
  }

  const issues = [...data.flags.filter(f => f.severity !== 'INFO')]
    .sort((a, b) => SEVERITY_STYLE[a.severity].rank - SEVERITY_STYLE[b.severity].rank);
  const notes = data.flags.filter(f => f.severity === 'INFO');
  const color = tierColor(data.tier.rank);

  return (
    <div className="space-y-4">
      <FreshnessNotice freshness={data.dataFreshness} />

      {/* Scorecard */}
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Panel title={<Term term="TIS">Trader Index Score</Term>} id="tis"
               actions={<button type="button" className={btn.ghost} onClick={() => void load()} aria-label="Re-evaluate">
                 <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
               </button>}>
          <ScoreGauge score={data.score} maxScore={data.maxScore} tier={data.tier}
                      provisional={data.provisional} pillarsScored={data.pillarsScored} />
          <div className="mt-4 grid grid-cols-3 gap-3 border-t border-term-line pt-3">
            <Metric size="sm" label="Raw total" term="TIS" value={data.rawScore} sub="before caps" />
            <Metric size="sm" label="Pillars" term="TIS" value={`${data.pillarsScored}/4`} sub="scored" />
            <Metric size="sm" label="Trades" term="TIS" value={data.coverage.trades} sub={`${data.coverage.datedTrades} dated`} />
          </div>
        </Panel>

        <Panel title="Four-pillar profile" id="radar">
          <PillarRadar pillars={data.pillars} />
          <ul className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2">
            {data.pillars.map(p => (
              <li key={p.key}>
                <div className="flex justify-between text-xs">
                  <span className="text-term-muted">{PILLAR_SHORT_NAME[p.key]}</span>
                  <span className="num text-term-text">{p.available ? `${Math.round(p.points)}/${p.maxPoints}` : 'n/a'}</span>
                </div>
                <ShareBar value={p.available ? p.points / p.maxPoints : 0} className="mt-1" />
              </li>
            ))}
          </ul>
        </Panel>

        <div className="space-y-4">
          <ScoreCaps score={data.score} rawScore={data.rawScore} caps={data.caps} />
          {data.caps.length === 0 && (
            <Panel title="Score limits"><p className="text-sm text-term-up">No caps apply: the score is the pillar total.</p></Panel>
          )}
          <TierLadder tiers={data.tiers} currentRank={data.tier.rank} accent={color.text} />
        </div>
      </div>

      {/* Pillar breakdown */}
      <section aria-labelledby="breakdown-heading">
        <h3 id="breakdown-heading" className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-term-muted">Pillar breakdown</h3>
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {data.pillars.map(p => <PillarCard key={p.key} pillar={p} />)}
        </div>
      </section>

      {data.diversification && (
        <DiversificationCard diversification={data.diversification}
                             riskComponents={data.pillars.find(p => p.key === 'risk')?.components ?? []} />
      )}

      {/* Diagnostics */}
      <Panel flush title="Diagnostic assessment" id="diagnostics"
             subtitle={issues.length ? `${issues.length} finding${issues.length === 1 ? '' : 's'}, most severe first` : undefined}>
        {issues.length === 0 ? (
          <p className="px-4 py-4 text-sm text-term-up">No behavioral or risk issues detected in the data available.</p>
        ) : (
          <div className="overflow-auto">
            <table className="w-full min-w-[900px] text-sm" aria-label="Diagnostic findings">
              <thead className="text-[11px] uppercase tracking-wider text-term-dim">
                <tr className="border-b border-term-line">
                  <th scope="col" className="w-24 px-4 py-2 text-left font-medium">Severity</th>
                  <th scope="col" className="w-28 px-3 py-2 text-left font-medium">Pillar</th>
                  <th scope="col" className="px-3 py-2 text-left font-medium">Finding</th>
                  <th scope="col" className="w-[34%] px-3 py-2 text-left font-medium">Coaching</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-term-line">
                {issues.map(f => <DiagnosticRow key={f.id} flag={f} />)}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {data.strengths.length > 0 && (
          <Panel title="Strengths">
            <ul className="space-y-1.5 text-sm">
              {data.strengths.map(s => <li key={s} className="flex gap-2 text-term-text"><span className="text-term-up">✓</span>{s}</li>)}
            </ul>
          </Panel>
        )}
        <RiskTargetForm current={data.riskTargetVolatility} onSaved={load} />
        {notes.length > 0 && (
          <Panel title="Improve score coverage" id="notes">
            <ul className="space-y-3">
              {notes.map(f => (
                <li key={f.id} className="text-sm">
                  <div className="font-medium text-term-accent">{f.title}</div>
                  <div className="text-term-muted">{f.recommendation}</div>
                </li>
              ))}
            </ul>
          </Panel>
        )}
      </div>

      <TierGates gates={data.gates} />

      <p className="text-xs text-term-dim">
        Based on {data.coverage.observations} daily returns, {data.coverage.trades} recorded trades
        ({data.coverage.datedTrades} with known dates) and {data.coverage.closedLots} closed lots.
        Metrics without enough data are left out rather than estimated.
      </p>
    </div>
  );
};

const PillarCard: React.FC<{ pillar: ScorePillar }> = ({ pillar }) => {
  const pctScore = pillar.available ? pillar.points / pillar.maxPoints : 0;
  return (
    <div className="rounded-lg border border-term-line bg-term-panel" data-testid={`pillar-${pillar.key}`}>
      <div className="flex items-baseline justify-between border-b border-term-line px-4 py-2.5">
        <h4 className="text-sm font-semibold text-term-text">{pillar.name}</h4>
        <span className={`num text-sm ${pillar.available ? 'text-term-text' : 'text-term-dim'}`}>
          {pillar.available ? `${Math.round(pillar.points)} / ${pillar.maxPoints}` : 'Not scored'}
        </span>
      </div>
      <div className="px-4 pt-2">
        <ShareBar value={pctScore} tone={pctScore >= 0.7 ? 'up' : pctScore < 0.4 ? 'down' : 'accent'} />
        <div className="mt-1 text-[11px] text-term-dim">{Math.round(pillar.coverage * 100)}% of this pillar's metrics have data</div>
      </div>
      <table className="mt-1 w-full text-sm">
        <tbody className="divide-y divide-term-line">
          {pillar.components.map(c => (
            <tr key={c.key} title={c.detail}>
              <td className="px-4 py-2">
                <div className={c.available ? 'text-term-text' : 'text-term-dim'}>{c.name}</div>
                <div className="text-xs text-term-dim">{c.available ? c.displayValue : c.detail}</div>
              </td>
              <td className="w-28 px-4 py-2 align-middle">
                {c.available && <ShareBar value={c.score} tone={c.score >= 0.7 ? 'up' : c.score < 0.4 ? 'down' : 'accent'} />}
              </td>
              <td className={`num w-20 px-4 py-2 text-right ${c.available ? 'text-term-text' : 'text-term-dim'}`}>
                {c.available ? `${c.points.toFixed(0)}/${c.weight}` : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/** One finding: severity, pillar, what was found and what to do about it. */
export const DiagnosticRow: React.FC<{ flag: DiagnosticFlag }> = ({ flag }) => {
  const style = SEVERITY_STYLE[flag.severity];
  return (
    <tr aria-label={flag.title} className="align-top hover:bg-term-hover">
      <td className="px-4 py-3"><Badge tone={style.tone}>{style.label}</Badge></td>
      <td className="px-3 py-3 text-term-muted">
        {PILLAR_SHORT_NAME[flag.pillar]}
        {SIZING_FLAGS.has(flag.id) && <div className="mt-1"><Badge tone="gold">Position sizing</Badge></div>}
      </td>
      <td className="px-3 py-3">
        <div className="font-medium text-term-text">{flag.title}</div>
        <div className="mt-0.5 text-term-muted">{flag.detail}</div>
      </td>
      <td className="px-3 py-3 text-term-muted"><span className="sr-only">Coaching: </span>{flag.recommendation}</td>
    </tr>
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
    const p = parseFloat(value);
    if (!(p >= 1 && p <= 300)) {
      setMessage('Enter a target between 1% and 300% a year.');
      return;
    }
    void save(p / 100);
  };

  return (
    <Panel title="Risk target" id="target" subtitle="Annual volatility you aim to run at; Execution Discipline scores adherence">
      <form onSubmit={submit} noValidate aria-label="Risk Target">
        <div className="flex items-center gap-2">
          <label className="sr-only" htmlFor="risk-target">Target annual volatility (%)</label>
          <input id="risk-target" type="number" min="1" max="300" step="0.5" value={value} placeholder="e.g. 40"
                 onChange={e => setValue(e.target.value)} className={`num flex-1 ${input}`} />
          <span className="text-sm text-term-muted">%</span>
          <button type="submit" disabled={saving} className={btn.primary}>Save</button>
          {current != null && <button type="button" disabled={saving} onClick={() => void save(null)} className={btn.ghost}>Clear</button>}
        </div>
        {current != null && <p className="mt-2 text-xs text-term-dim">Current target {pct(current, 1)}</p>}
        {message && <p className="mt-2 text-sm text-term-down" role="alert">{message}</p>}
      </form>
    </Panel>
  );
};

/** Shown only when the evaluation had to use old prices or saved history (CoinGecko unreachable). */
export const FreshnessNotice: React.FC<{ freshness?: InstitutionalScore['dataFreshness'] }> = ({ freshness }) => {
  if (!freshness || (freshness.stalePrices.length === 0 && !freshness.historyStale)) return null;
  return (
    <div className="rounded-lg border border-term-accent/40 bg-term-accent-soft px-4 py-3 text-sm text-term-text" role="status"
         aria-label="Market data freshness">
      <div className="font-semibold">Market data isn't fully up to date</div>
      {freshness.stalePrices.length > 0 && (
        <div className="text-term-muted">Live prices unavailable for {freshness.stalePrices.join(', ')}
          {freshness.pricesAsOf ? `; valued at prices from ${new Date(freshness.pricesAsOf).toLocaleString()}` : ''}.</div>
      )}
      {freshness.historyStale && freshness.historyThrough && (
        <div className="text-term-muted">Price history runs only to {freshness.historyThrough}; returns after that date aren't included yet.</div>
      )}
      <div className="mt-1 text-xs text-term-accent">Fresh data is being fetched in the background; this page updates automatically.</div>
    </div>
  );
};

/** Why the score is lower than the pillar total: the binding cap first, then any others that apply. */
export const ScoreCaps: React.FC<{ score: number; rawScore: number; caps: ScoreCap[] }> = ({ score, rawScore, caps }) => {
  if (caps.length === 0) return null;
  const capped = score < rawScore;
  return (
    <section className="rounded-lg border border-term-warn/40 bg-term-panel" aria-labelledby="caps-heading">
      <h3 id="caps-heading" className="flex items-center gap-2 border-b border-term-line px-4 py-2.5 text-sm font-semibold text-term-warn">
        <Lock size={14} /> {capped ? `Score capped at ${score} (pillar total ${rawScore})` : 'Score limits that apply'}
      </h3>
      <ul className="max-h-56 divide-y divide-term-line overflow-auto">
        {caps.map((c, i) => (
          <li key={c.id} className="px-4 py-2 text-sm">
            <div className="flex items-baseline justify-between gap-2">
              <span className={`font-medium ${i === 0 && capped ? 'text-term-text' : 'text-term-muted'}`}>{c.title}</span>
              <span className="num shrink-0 text-xs text-term-dim">≤ {c.maxScore}</span>
            </div>
            <div className="text-xs text-term-muted">{c.detail}</div>
          </li>
        ))}
      </ul>
    </section>
  );
};

const TierGates: React.FC<{ gates: TierGate[] }> = ({ gates }) => (
  <Panel title="Tier requirements" id="gates">
    <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
      {gates.map(g => (
        <div key={g.minScore} className={`rounded-md border p-3 ${g.unlocked ? 'border-term-up/40' : 'border-term-line'}`} data-testid={`gate-${g.minScore}`}>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium">
            {g.unlocked ? <Unlock size={14} className="text-term-up" /> : <Lock size={14} className="text-term-dim" />}
            <span className={g.unlocked ? 'text-term-up' : 'text-term-text'}>{g.name}</span>
            <span className="num text-xs text-term-dim">{g.minScore}+</span>
          </div>
          <ul className="space-y-1.5">
            {g.requirements.map(r => (
              <li key={r.label} className="flex gap-2 text-xs">
                {r.met ? <CheckCircle2 size={14} className="mt-0.5 shrink-0 text-term-up" aria-label="met" />
                  : <Circle size={14} className="mt-0.5 shrink-0 text-term-dim" aria-label="not met" />}
                <span className={r.met ? 'text-term-text' : 'text-term-muted'}>
                  {r.label} <span className="num text-term-dim">— {r.actual}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  </Panel>
);

const TierLadder: React.FC<{ tiers: InstitutionalScore['tiers']; currentRank: number; accent: string }> = ({ tiers, currentRank, accent }) => (
  <Panel title="Rank tiers" id="ladder" flush>
    <ol className="divide-y divide-term-line text-sm">
      {tiers.map(t => (
        <li key={t.rank} aria-current={t.rank === currentRank ? 'true' : undefined}
            className={`flex justify-between px-4 py-1.5 ${t.rank === currentRank ? 'bg-term-raised font-semibold' : ''}`}>
          <span className={t.rank === currentRank ? accent : 'text-term-muted'}>{t.rank}. {t.name}</span>
          <span className="num text-xs text-term-dim">{t.minScore}–{t.maxScore}</span>
        </li>
      ))}
    </ol>
  </Panel>
);

export default InstitutionalEvaluationPage;
