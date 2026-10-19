import React, { useCallback, useEffect, useState } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { API_BASE_URL } from '../config';
import { Coin, PriceHistoryPoint } from '../types';
import { Delta, Metric, Modal, Skeleton, btn, segmented } from './ui/kit';
import { pct, usd } from './ui/format';

interface PriceHistoryChartProps {
  selectedCoin: Coin | null;
  onClose: () => void;
}

const RANGES = [7, 30, 90, 365];

/** Daily price history for one coin, served (and cached) by the backend from CoinGecko. */
const PriceHistoryChart: React.FC<PriceHistoryChartProps> = ({ selectedCoin, onClose }) => {
  const [priceHistory, setPriceHistory] = useState<PriceHistoryPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [timeRange, setTimeRange] = useState(30);

  const fetchPriceHistory = useCallback(async () => {
    if (!selectedCoin) return;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(`${API_BASE_URL}/api/price-history/${encodeURIComponent(selectedCoin.id)}?days=${timeRange}`,
        { signal: controller.signal });
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(body?.error || `Failed to load price history (HTTP ${response.status})`);
      const points: PriceHistoryPoint[] = (body?.priceHistory ?? []).map(
        ([timestamp, price]: [number, number]) => ({ timestamp, price }));
      if (points.length === 0) throw new Error(`No price history available for ${selectedCoin.name}`);
      setPriceHistory(points);
    } catch (err) {
      setPriceHistory([]);
      setError(err instanceof DOMException && err.name === 'AbortError' ? 'Loading timed out. Please try again.'
        : err instanceof Error ? err.message : 'Failed to load price history');
    } finally {
      clearTimeout(timeout);
      setLoading(false);
    }
  }, [selectedCoin, timeRange]);

  useEffect(() => { void fetchPriceHistory(); }, [fetchPriceHistory]);

  if (!selectedCoin) return null;
  const first = priceHistory[0]?.price, last = priceHistory[priceHistory.length - 1]?.price;
  const change = first && last ? last / first - 1 : null;
  const prices = priceHistory.map(p => p.price);
  const up = (change ?? 0) >= 0;
  const color = up ? '#4FD1A5' : '#E06C75';

  return (
    <Modal title={`${selectedCoin.name} · ${selectedCoin.symbol.toUpperCase()}`} labelledBy="chart-title" onClose={onClose} width="max-w-4xl">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
        <div className="grid grid-cols-2 gap-6 sm:grid-cols-4">
          <Metric label="Price" value={usd(selectedCoin.current_price)} />
          <Metric label={`${timeRange}d change`} value={<Delta value={change} format={v => pct(v, 2, true)} />} />
          <Metric label={`${timeRange}d high`} value={prices.length ? usd(Math.max(...prices)) : '—'} />
          <Metric label={`${timeRange}d low`} value={prices.length ? usd(Math.min(...prices)) : '—'} />
        </div>
        <div className={segmented.track} role="group" aria-label="Range">
          {RANGES.map(r => (
            <button key={r} type="button" aria-pressed={timeRange === r} onClick={() => setTimeRange(r)}
                    className={segmented.option(timeRange === r)}>
              {r === 365 ? '1Y' : `${r}D`}
            </button>
          ))}
        </div>
      </div>

      {loading ? <Skeleton className="h-80 w-full" /> : error ? (
        <div className="flex h-80 flex-col items-center justify-center gap-3 text-center" role="alert">
          <p className="text-sm text-term-down">{error}</p>
          <button type="button" className={btn.secondary} onClick={() => void fetchPriceHistory()}>Try again</button>
        </div>
      ) : (
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={priceHistory} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="phFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={color} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={color} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="#1C2433" vertical={false} />
              <XAxis dataKey="timestamp" tickFormatter={t => new Date(t).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                     stroke="#5F6B82" tick={{ fontSize: 11, fontFamily: 'JetBrains Mono' }} tickLine={false} axisLine={false} minTickGap={40} />
              <YAxis domain={['auto', 'auto']} tickFormatter={v => usd(v)} width={84} orientation="right"
                     stroke="#5F6B82" tick={{ fontSize: 11, fontFamily: 'JetBrains Mono' }} tickLine={false} axisLine={false} />
              <Tooltip contentStyle={{ background: '#141B2A', border: '1px solid #2A3447', borderRadius: 6, fontSize: 12 }}
                       labelFormatter={t => new Date(t as number).toLocaleDateString()} formatter={(v: number) => [usd(v), 'Price']} />
              <Area type="monotone" dataKey="price" stroke={color} strokeWidth={1.5} fill="url(#phFill)" dot={false} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </Modal>
  );
};

export default PriceHistoryChart;
