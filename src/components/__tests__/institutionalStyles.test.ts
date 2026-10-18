import { describe, it, expect } from 'vitest';
import { tierColor } from '../institutional/styles';
import { todayLocal } from '../../utils/dates';

describe('tier colours', () => {
  it('runs from gold at the top to red at the bottom', () => {
    expect(tierColor(1).hex).toBe('#f59e0b');
    expect(tierColor(2).hex).toBe('#f59e0b');
    expect(tierColor(4).hex).toBe('#10b981');
    expect(tierColor(6).hex).toBe('#06b6d4');
    expect(tierColor(8).hex).toBe('#f97316');
    expect(tierColor(10).hex).toBe('#ef4444');
  });
});

describe('todayLocal', () => {
  it('formats the local calendar date as YYYY-MM-DD', () => {
    expect(todayLocal(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
