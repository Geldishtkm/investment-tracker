import React, { useId, useState } from 'react';
import { Info } from 'lucide-react';

interface Props {
  /** The metric name, used for the button's label. */
  title: string;
  what: string;
  improve: string;
  /** Which side of the icon the popover opens towards. */
  align?: 'left' | 'right';
}

/** An info icon that explains a metric on hover or keyboard focus: what it means, and how to improve it. */
const InfoTip: React.FC<Props> = ({ title, what, improve, align = 'left' }) => {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <span className="relative inline-flex" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button type="button" aria-label={`About ${title}`} aria-describedby={open ? id : undefined} aria-expanded={open}
              onFocus={() => setOpen(true)} onBlur={() => setOpen(false)} onClick={() => setOpen(o => !o)}
              onKeyDown={e => { if (e.key === 'Escape') setOpen(false); }}
              className="inline-flex h-4 w-4 items-center justify-center rounded-full text-term-dim transition-colors hover:text-term-accent focus-visible:text-term-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-term-accent/60">
        <Info size={13} />
      </button>
      {open && (
        <span role="tooltip" id={id}
              className={`absolute top-full z-50 mt-2 w-72 rounded-lg border border-term-line2 bg-term-raised/95 p-3 text-left text-xs leading-relaxed shadow-[0_16px_40px_-12px_rgba(0,0,0,0.8)] backdrop-blur-xl ${
                align === 'left' ? 'left-0' : 'right-0'}`}>
          <span className="block text-[10px] font-semibold uppercase tracking-wider text-term-accent">What it means</span>
          <span className="mt-0.5 block text-term-text">{what}</span>
          <span className="mt-2 block text-[10px] font-semibold uppercase tracking-wider text-term-up">How to improve it</span>
          <span className="mt-0.5 block text-term-muted">{improve}</span>
        </span>
      )}
    </span>
  );
};

export default InfoTip;
