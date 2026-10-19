import React, { useId, useRef, useState } from 'react';
import { Info } from 'lucide-react';
import Popover from './Popover';

interface Props {
  /** The metric name, used for the button's label. */
  title: string;
  what: string;
  /** Optional advice; the "How to improve it" section is shown only when given. */
  improve?: string;
}

/** An info icon that explains a metric on hover or keyboard focus: what it means, and how to improve it. */
const InfoTip: React.FC<Props> = ({ title, what, improve }) => {
  const [open, setOpen] = useState(false);
  const id = useId();
  const button = useRef<HTMLButtonElement>(null);
  return (
    <span className="relative inline-flex" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button ref={button} type="button" aria-label={`About ${title}`} aria-describedby={open ? id : undefined} aria-expanded={open}
              onFocus={() => setOpen(true)} onBlur={() => setOpen(false)} onClick={() => setOpen(o => !o)}
              onKeyDown={e => { if (e.key === 'Escape') setOpen(false); }}
              className="inline-flex h-4 w-4 items-center justify-center rounded-full text-term-dim transition-colors hover:text-term-accent focus-visible:text-term-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-term-accent/60">
        <Info size={13} />
      </button>
      {open && (
        <Popover anchor={button.current} id={id}>
          <span className="block text-[10px] font-semibold uppercase tracking-wider text-term-accent">What it means</span>
          <span className="mt-0.5 block text-term-text">{what}</span>
          {improve && (
            <>
              <span className="mt-2 block text-[10px] font-semibold uppercase tracking-wider text-term-up">How to improve it</span>
              <span className="mt-0.5 block text-term-muted">{improve}</span>
            </>
          )}
        </Popover>
      )}
    </span>
  );
};

export default InfoTip;
