import { useCallback, useEffect, useState } from 'react';
import { InstitutionalScore } from '../types';
import { assetService } from '../services/api';

/**
 * One shared Trader Index Score for the header and the Evaluation page.
 *
 * The score is computed on request by the backend, never inside a trade. Each trade bumps a data
 * version; the first reader of a new version fetches it once and everyone else shares that request.
 * Within FRESH_MS a version is served from memory (e.g. when switching tabs); after that, opening the
 * page refetches. While a fetch runs, the last known score stays on screen, marked as updating.
 */
export const FRESH_MS = 30_000;

type Entry = { version: number; at: number; data: InstitutionalScore };
let cache: Entry | null = null;
let inflight: { version: number; promise: Promise<InstitutionalScore> } | null = null;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach(l => l());

export function fetchScore(version: number, { force = false } = {}): Promise<InstitutionalScore> {
  if (!force && cache && cache.version === version && Date.now() - cache.at < FRESH_MS) return Promise.resolve(cache.data);
  if (!force && inflight && inflight.version === version) return inflight.promise;
  const promise: Promise<InstitutionalScore> = assetService.getInstitutionalScore()
    .then(data => {
      // A slower, older request must not overwrite a newer version
      if (!cache || version >= cache.version) cache = { version, at: Date.now(), data };
      return data;
    })
    .finally(() => {
      // Cleared before callers resume, so nothing sees a finished request as still running
      if (inflight?.promise === promise) inflight = null;
      notify();
    });
  inflight = { version, promise };
  notify();
  return promise;
}

/** The last score fetched, whatever its version (shown while a newer one loads). */
export function peekScore(): InstitutionalScore | null {
  return cache?.data ?? null;
}

/** Forget the score, e.g. on logout so the next user never sees it. */
export function resetScoreCache() {
  cache = null;
  inflight = null;
  notify();
}

/** Subscribe to the shared score; fetches the given version when it isn't in memory yet. */
export function useScore(version: number, enabled = true) {
  const [, rerender] = useState(0);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const listener = () => rerender(n => n + 1);
    listeners.add(listener);
    return () => { listeners.delete(listener); };
  }, []);

  const refresh = useCallback((force = false) => {
    setError(null);
    return fetchScore(version, { force }).catch(e => {
      setError(e instanceof Error ? e : new Error('Failed to load the evaluation'));
      return null;
    });
  }, [version]);

  useEffect(() => {
    if (enabled) void refresh();
  }, [enabled, refresh]);

  return {
    data: peekScore(),
    /** True while a request is running (the shown score may be from before the last trade). */
    updating: inflight !== null,
    /** True when the shown score already reflects the current version. */
    current: cache?.version === version,
    error,
    refresh,
  };
}
