import { useCallback, useEffect, useState } from 'react';

/** Height used before the container has been measured (and in test environments without layout). */
const FALLBACK_VIEWPORT = 720;

/**
 * Windowing for long tables with fixed-height rows: only the rows in view (plus `overscan` on each side)
 * are rendered, with spacer heights above and below keeping the scrollbar true to the full list.
 *
 * Attach `ref` to the scrolling element. It is a callback ref, so a container that mounts later (after
 * a loading state) is still picked up.
 */
export function useVirtualRows(count: number, rowHeight: number, overscan = 8) {
  const [el, setEl] = useState<HTMLElement | null>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewport, setViewport] = useState(FALLBACK_VIEWPORT);

  useEffect(() => {
    if (!el) return;
    const onScroll = () => setScrollTop(el.scrollTop);
    el.addEventListener('scroll', onScroll, { passive: true });
    const measure = () => setViewport(el.clientHeight || FALLBACK_VIEWPORT);
    measure();
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    observer?.observe(el);
    return () => {
      el.removeEventListener('scroll', onScroll);
      observer?.disconnect();
    };
  }, [el]);

  const start = Math.max(0, Math.floor(scrollTop / rowHeight) - overscan);
  const end = Math.min(count, Math.ceil((scrollTop + viewport) / rowHeight) + overscan);

  /** Jump back to the top, e.g. after the list is filtered or re-sorted. */
  const reset = useCallback(() => {
    if (el) el.scrollTop = 0;
    setScrollTop(0);
  }, [el]);

  return { ref: setEl, start, end, padTop: start * rowHeight, padBottom: Math.max(0, (count - end) * rowHeight), reset };
}
