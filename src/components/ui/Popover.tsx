import React, { useLayoutEffect, useState } from 'react';
import { createPortal } from 'react-dom';

interface Props {
  /** The element the popover points at. */
  anchor: HTMLElement | null;
  id: string;
  children: React.ReactNode;
  width?: number;
}

const GAP = 8;
const MARGIN = 8;

/**
 * A tooltip rendered at the page level, so frosted panels (each its own stacking layer) can't clip or cover it.
 * Placed under the anchor, flipped above when there's no room, and kept inside the viewport.
 */
const Popover: React.FC<Props> = ({ anchor, id, children, width = 288 }) => {
  const [pos, setPos] = useState<{ top: number; left: number; above: boolean } | null>(null);

  useLayoutEffect(() => {
    if (!anchor) return;
    const place = () => {
      const r = anchor.getBoundingClientRect();
      const left = Math.min(Math.max(MARGIN, r.left), window.innerWidth - width - MARGIN);
      const above = r.bottom + 200 > window.innerHeight && r.top > 200;
      setPos({ top: above ? r.top - GAP : r.bottom + GAP, left, above });
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [anchor, width]);

  return createPortal(
    <span role="tooltip" id={id}
          style={{ position: 'fixed', top: pos?.top ?? -9999, left: pos?.left ?? -9999, width,
                   transform: pos?.above ? 'translateY(-100%)' : undefined }}
          className="pointer-events-none z-[100] block rounded-lg border border-term-line2 bg-term-raised/95 p-3 text-left text-xs font-normal normal-case leading-relaxed tracking-normal text-term-text shadow-[0_16px_40px_-12px_rgba(0,0,0,0.8)] backdrop-blur-xl">
      {children}
    </span>,
    document.body,
  );
};

export default Popover;
