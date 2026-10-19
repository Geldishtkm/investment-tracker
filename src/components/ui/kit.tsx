import React from 'react';
import Term from './Term';
import { Tone, TONE_TEXT, toneOf } from './format';

/** Shared building blocks for the terminal: panels, metrics, deltas, badges, skeletons and sparklines. */

export const btn = {
  primary: 'inline-flex items-center justify-center gap-1.5 rounded-md bg-term-accent px-3 py-1.5 text-sm font-semibold text-term-bg hover:bg-[#7AA3F3] disabled:cursor-not-allowed disabled:opacity-50',
  secondary: 'inline-flex items-center justify-center gap-1.5 rounded-md border border-term-line2 bg-term-raised px-3 py-1.5 text-sm font-medium text-term-text hover:border-term-dim hover:bg-term-hover disabled:cursor-not-allowed disabled:opacity-50',
  ghost: 'inline-flex items-center justify-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm font-medium text-term-muted hover:bg-term-hover hover:text-term-text disabled:opacity-50',
  buy: 'inline-flex items-center justify-center rounded px-2 py-1 text-xs font-semibold text-term-up border border-term-up/40 bg-term-up-soft hover:border-term-up hover:text-[#5CC4A3]',
  sell: 'inline-flex items-center justify-center rounded px-2 py-1 text-xs font-semibold text-term-down border border-term-down/40 bg-term-down-soft hover:border-term-down hover:text-[#E0626B]',
  danger: 'inline-flex items-center justify-center gap-1.5 rounded-md bg-term-down px-3 py-1.5 text-sm font-semibold text-white hover:bg-[#D85A62] disabled:opacity-50',
};

export const input = 'w-full rounded-md border border-term-line2 bg-term-bg px-3 py-2 text-sm text-term-text placeholder:text-term-dim focus:border-term-accent focus:outline-none';

interface PanelProps {
  title?: React.ReactNode;
  /** Small caption under or beside the title. */
  subtitle?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  /** No inner padding, for tables that run edge to edge. */
  flush?: boolean;
  id?: string;
  'data-testid'?: string;
}

export const Panel: React.FC<PanelProps> = ({ title, subtitle, actions, children, className = '', flush, id, ...rest }) => {
  const headingId = id ? `${id}-heading` : undefined;
  return (
    <section className={`rounded-lg border border-term-line bg-term-panel ${className}`} aria-labelledby={title ? headingId : undefined}
             data-testid={rest['data-testid']}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-term-line px-4 py-2.5">
          <div className="min-w-0">
            {title && <h3 id={headingId} className="text-[11px] font-semibold uppercase tracking-[0.12em] text-term-muted">{title}</h3>}
            {subtitle && <p className="mt-0.5 text-xs text-term-dim">{subtitle}</p>}
          </div>
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={flush ? '' : 'p-4'}>{children}</div>
    </section>
  );
};

interface MetricProps {
  label: string;
  /** Glossary term to explain the label; defaults to the label itself. */
  term?: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
  tone?: Tone;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}

/** A labelled figure: the label (with its definition on hover), a monospace value, and an optional note. */
export const Metric: React.FC<MetricProps> = ({ label, term, value, sub, tone, className = '', size = 'md' }) => {
  const sizes = { sm: 'text-base', md: 'text-lg sm:text-xl', lg: 'text-lg sm:text-xl 2xl:text-2xl' };
  return (
    <div className={`min-w-0 ${className}`}>
      <div className="text-[11px] font-medium uppercase tracking-[0.1em] text-term-dim">
        <Term term={term ?? label}>{label}</Term>
      </div>
      <div className={`num mt-1 truncate font-medium ${sizes[size]} ${tone ? TONE_TEXT[tone] : 'text-term-text'}`}>{value}</div>
      {sub && <div className="mt-0.5 truncate text-xs text-term-muted">{sub}</div>}
    </div>
  );
};

/** A signed change with an arrow, coloured by direction. value is in display units already formatted by `format`. */
export const Delta: React.FC<{ value: number | null | undefined; format: (v: number) => string; className?: string }> = ({ value, format, className = '' }) => {
  if (value == null || !Number.isFinite(value)) return <span className={`num text-term-dim ${className}`}>—</span>;
  const tone = toneOf(value, 1e-12);
  const arrow = tone === 'up' ? '▲' : tone === 'down' ? '▼' : '';
  return (
    <span className={`num inline-flex items-center gap-1 ${TONE_TEXT[tone]} ${className}`}>
      {arrow && <span aria-hidden="true" className="text-[9px]">{arrow}</span>}
      {format(value)}
    </span>
  );
};

type BadgeTone = 'neutral' | 'up' | 'down' | 'warn' | 'accent' | 'gold';
const BADGE: Record<BadgeTone, string> = {
  neutral: 'border-term-line2 bg-term-raised text-term-muted',
  up: 'border-term-up/40 bg-term-up-soft text-term-up',
  down: 'border-term-down/40 bg-term-down-soft text-term-down',
  warn: 'border-term-warn/40 bg-term-warn-soft text-term-warn',
  accent: 'border-term-accent/40 bg-term-accent-soft text-term-accent',
  gold: 'border-term-gold/50 bg-[#2A2213] text-term-gold',
};

export const Badge: React.FC<{ tone?: BadgeTone; children: React.ReactNode; className?: string }> = ({ tone = 'neutral', children, className = '' }) => (
  <span className={`inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${BADGE[tone]} ${className}`}>
    {children}
  </span>
);

export const Skeleton: React.FC<{ className?: string }> = ({ className = 'h-4 w-full' }) => (
  <div className={`skeleton ${className}`} aria-hidden="true" />
);

/** Placeholder for a table while it loads. */
export const SkeletonRows: React.FC<{ rows?: number; cols?: number }> = ({ rows = 6, cols = 6 }) => (
  <div className="divide-y divide-term-line" role="status" aria-label="Loading">
    {Array.from({ length: rows }, (_, r) => (
      <div key={r} className="flex items-center gap-4 px-4 py-3">
        {Array.from({ length: cols }, (_, c) => (
          <Skeleton key={c} className={`h-3.5 ${c === 0 ? 'w-40' : 'flex-1'}`} />
        ))}
      </div>
    ))}
  </div>
);

/** Placeholder for a row of metric tiles. */
export const SkeletonMetrics: React.FC<{ count?: number }> = ({ count = 4 }) => (
  <div className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-term-line bg-term-line md:grid-cols-4" role="status" aria-label="Loading">
    {Array.from({ length: count }, (_, i) => (
      <div key={i} className="space-y-2 bg-term-panel p-4">
        <Skeleton className="h-2.5 w-20" />
        <Skeleton className="h-6 w-28" />
      </div>
    ))}
  </div>
);

/** Minimal SVG sparkline; colour follows the first-to-last direction. */
export const Sparkline: React.FC<{ data?: number[] | null; width?: number; height?: number; className?: string; label?: string }> = ({
  data, width = 96, height = 28, className = '', label,
}) => {
  const points = (data ?? []).filter(Number.isFinite);
  if (points.length < 2) return <span className="num text-xs text-term-dim">—</span>;
  const min = Math.min(...points), max = Math.max(...points);
  const span = max - min || 1;
  const step = width / (points.length - 1);
  const path = points.map((v, i) => `${(i * step).toFixed(1)},${(height - 2 - ((v - min) / span) * (height - 4)).toFixed(1)}`).join(' ');
  const up = points[points.length - 1] >= points[0];
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} className={className}
         role="img" aria-label={label ?? `Trend ${up ? 'up' : 'down'}`}>
      <polyline points={path} fill="none" strokeWidth={1.25} strokeLinejoin="round" strokeLinecap="round"
                stroke={up ? '#3FA586' : '#C8464F'} />
    </svg>
  );
};

/** A thin horizontal bar for shares (allocation, volume), 0–1. */
export const ShareBar: React.FC<{ value: number; tone?: 'accent' | 'muted' | 'up' | 'down'; className?: string }> = ({ value, tone = 'accent', className = '' }) => {
  const fill = { accent: 'bg-term-accent/80', muted: 'bg-term-dim', up: 'bg-term-up/80', down: 'bg-term-down/80' }[tone];
  return (
    <div className={`h-1 w-full overflow-hidden rounded-full bg-term-line ${className}`} aria-hidden="true">
      <div className={`h-full ${fill}`} style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} />
    </div>
  );
};

/** Page title row used by every section. */
export const PageHeader: React.FC<{ title: string; subtitle?: React.ReactNode; actions?: React.ReactNode }> = ({ title, subtitle, actions }) => (
  <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
    <div>
      <h2 className="text-lg font-semibold tracking-tight text-term-text">{title}</h2>
      {subtitle && <p className="mt-0.5 text-sm text-term-muted">{subtitle}</p>}
    </div>
    {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
  </div>
);

/** A modal shell matching the terminal style. */
export const Modal: React.FC<{ title: React.ReactNode; onClose?: () => void; children: React.ReactNode; labelledBy?: string; width?: string }> = ({
  title, onClose, children, labelledBy = 'modal-title', width = 'max-w-md',
}) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-[2px]" role="dialog" aria-modal="true"
       aria-labelledby={labelledBy}
       onKeyDown={e => { if (e.key === 'Escape' && onClose) onClose(); }}>
    <div className={`w-full ${width} rounded-lg border border-term-line2 bg-term-panel shadow-2xl shadow-black/60`}>
      <div className="flex items-center justify-between border-b border-term-line px-4 py-3">
        <h3 id={labelledBy} className="text-sm font-semibold text-term-text">{title}</h3>
        {onClose && (
          <button type="button" onClick={onClose} aria-label="Close" className="rounded p-1 text-term-muted hover:bg-term-hover hover:text-term-text">✕</button>
        )}
      </div>
      <div className="p-4">{children}</div>
    </div>
  </div>
);
