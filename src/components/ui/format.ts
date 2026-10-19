/** Number formatting for the terminal: every figure goes through these so columns stay consistent. */

const isNum = (v: number | null | undefined): v is number => typeof v === 'number' && Number.isFinite(v);

/** $1,234.56; small prices keep significant digits ($0.000123). */
export function usd(v: number | null | undefined, digits?: number): string {
  if (!isNum(v)) return '—';
  const abs = Math.abs(v);
  const d = digits ?? (abs === 0 ? 2 : abs < 0.01 ? 6 : abs < 1 ? 4 : 2);
  return v.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: Math.min(d, 2), maximumFractionDigits: d });
}

/** $1.23T, $45.6B, $789.0M, $12.3K. */
export function compactUsd(v: number | null | undefined): string {
  if (!isNum(v)) return '—';
  const abs = Math.abs(v);
  const sign = v < 0 ? '-' : '';
  if (abs >= 1e12) return `${sign}$${(abs / 1e12).toFixed(2)}T`;
  if (abs >= 1e9) return `${sign}$${(abs / 1e9).toFixed(2)}B`;
  if (abs >= 1e6) return `${sign}$${(abs / 1e6).toFixed(2)}M`;
  if (abs >= 1e3) return `${sign}$${(abs / 1e3).toFixed(1)}K`;
  return usd(v);
}

/** A fraction as a percentage: 0.1234 → 12.34%. signed adds + for gains. */
export function pct(v: number | null | undefined, digits = 2, signed = false): string {
  if (!isNum(v)) return '—';
  const s = (v * 100).toFixed(digits);
  return `${signed && v > 0 ? '+' : ''}${s}%`;
}

/** A value already in percent units (CoinGecko's 24h change): 3.2 → +3.20%. */
export function pctPoints(v: number | null | undefined, digits = 2): string {
  if (!isNum(v)) return '—';
  return `${v > 0 ? '+' : ''}${v.toFixed(digits)}%`;
}

/** Plain number with up to `digits` decimals and thousands separators. */
export function num(v: number | null | undefined, digits = 2): string {
  if (!isNum(v)) return '—';
  return v.toLocaleString('en-US', { maximumFractionDigits: digits });
}

/** Coin quantities: more decimals for small amounts. */
export function qty(v: number | null | undefined): string {
  if (!isNum(v)) return '—';
  const abs = Math.abs(v);
  return v.toLocaleString('en-US', { maximumFractionDigits: abs >= 1000 ? 2 : abs >= 1 ? 4 : 8 });
}

export type Tone = 'up' | 'down' | 'flat';

export function toneOf(v: number | null | undefined, epsilon = 0): Tone {
  if (!isNum(v) || Math.abs(v) <= epsilon) return 'flat';
  return v > 0 ? 'up' : 'down';
}

export const TONE_TEXT: Record<Tone, string> = {
  up: 'text-term-up',
  down: 'text-term-down',
  flat: 'text-term-muted',
};
