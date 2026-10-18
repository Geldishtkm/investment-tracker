import { FlagSeverity, PillarKey } from '../../types';

/** Colour family for a rank tier: gold at the top, red at the bottom. */
export function tierColor(rank: number): { hex: string; text: string; badge: string } {
  if (rank <= 2) return { hex: '#f59e0b', text: 'text-amber-300', badge: 'from-amber-500/30 to-yellow-500/20 border-amber-400/50' };
  if (rank <= 4) return { hex: '#10b981', text: 'text-emerald-300', badge: 'from-emerald-500/30 to-teal-500/20 border-emerald-400/50' };
  if (rank <= 6) return { hex: '#06b6d4', text: 'text-cyan-300', badge: 'from-cyan-500/30 to-blue-500/20 border-cyan-400/50' };
  if (rank <= 8) return { hex: '#f97316', text: 'text-orange-300', badge: 'from-orange-500/30 to-amber-600/20 border-orange-400/50' };
  return { hex: '#ef4444', text: 'text-red-300', badge: 'from-red-500/30 to-rose-600/20 border-red-400/50' };
}

export const SEVERITY_STYLE: Record<FlagSeverity, { label: string; card: string; chip: string }> = {
  HIGH: { label: 'High', card: 'border-red-500/50 bg-red-950/30', chip: 'bg-red-500/20 text-red-300 border-red-500/40' },
  MEDIUM: { label: 'Medium', card: 'border-orange-500/40 bg-orange-950/20', chip: 'bg-orange-500/20 text-orange-300 border-orange-500/40' },
  LOW: { label: 'Low', card: 'border-yellow-500/30 bg-yellow-950/10', chip: 'bg-yellow-500/20 text-yellow-200 border-yellow-500/40' },
  INFO: { label: 'Info', card: 'border-blue-500/30 bg-blue-950/20', chip: 'bg-blue-500/20 text-blue-300 border-blue-500/40' },
};

export const PILLAR_SHORT_NAME: Record<PillarKey, string> = {
  risk: 'Risk',
  behavior: 'Behavior',
  consistency: 'Consistency',
  execution: 'Execution',
};
