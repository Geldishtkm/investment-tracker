import { useEffect, useState } from 'react';
import { Coin } from '../types';
import { assetService } from '../services/api';

/** The market list changes every 10 minutes on the backend; refetching sooner gains nothing. */
const TTL_MS = 60_000;
let cache: { coins: Coin[]; at: number } | null = null;
let inflight: Promise<Coin[]> | null = null;

/** Top coins by market cap, shared across pages so switching tabs doesn't refetch. */
export function loadMarketData(force = false): Promise<Coin[]> {
  if (!force && cache && Date.now() - cache.at < TTL_MS) return Promise.resolve(cache.coins);
  if (!inflight) {
    inflight = assetService.getTopCoins()
      .then(coins => {
        cache = { coins: Array.isArray(coins) ? coins : [], at: Date.now() };
        return cache.coins;
      })
      .finally(() => { inflight = null; });
  }
  return inflight;
}

/** For tests. */
export function resetMarketDataCache() {
  cache = null;
  inflight = null;
}

export function useMarketData() {
  const [coins, setCoins] = useState<Coin[]>(cache?.coins ?? []);
  const [loading, setLoading] = useState(!cache);
  const [error, setError] = useState<string | null>(null);
  const [asOf, setAsOf] = useState<number | null>(cache?.at ?? null);

  const refresh = (force = false) => {
    setLoading(true);
    setError(null);
    return loadMarketData(force)
      .then(c => { setCoins(c); setAsOf(cache?.at ?? Date.now()); })
      .catch(e => setError(e instanceof Error ? e.message : 'Market data is unavailable'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load once per mount; refresh() is stable in effect
  }, []);

  return { coins, loading, error, asOf, refresh };
}

/** The market coin a holding refers to, matched by CoinGecko ID, name or symbol as the backend does. */
export function findCoin(coins: Coin[], assetName: string): Coin | undefined {
  const key = assetName.trim().toLowerCase();
  return coins.find(c => c.id.toLowerCase() === key || c.name.toLowerCase() === key || c.symbol.toLowerCase() === key);
}
