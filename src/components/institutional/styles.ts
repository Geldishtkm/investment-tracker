import { FlagSeverity, PillarKey } from '../../types';

/** Colour family for a rank tier: muted gold at the top, crimson at the bottom. */
export function tierColor(rank: number): { hex: string; text: string; badge: string } {
  if (rank <= 2) return { hex: '#D4B062', text: 'text-term-gold', badge: 'border-term-gold/50 bg-[#2A2414]' };
  if (rank <= 4) return { hex: '#4FD1A5', text: 'text-term-up', badge: 'border-term-up/40 bg-term-up-soft' };
  if (rank <= 6) return { hex: '#7C83FF', text: 'text-term-accent', badge: 'border-term-accent/40 bg-term-accent-soft' };
  if (rank <= 8) return { hex: '#E3B062', text: 'text-term-warn', badge: 'border-term-warn/40 bg-term-warn-soft' };
  return { hex: '#E06C75', text: 'text-term-down', badge: 'border-term-down/40 bg-term-down-soft' };
}

export const SEVERITY_STYLE: Record<FlagSeverity, { label: string; tone: 'down' | 'warn' | 'neutral' | 'accent'; rank: number }> = {
  HIGH: { label: 'High', tone: 'down', rank: 0 },
  MEDIUM: { label: 'Medium', tone: 'warn', rank: 1 },
  LOW: { label: 'Low', tone: 'neutral', rank: 2 },
  INFO: { label: 'Info', tone: 'accent', rank: 3 },
};

export const PILLAR_SHORT_NAME: Record<PillarKey, string> = {
  risk: 'Risk',
  behavior: 'Behavior',
  consistency: 'Consistency',
  execution: 'Execution',
};
