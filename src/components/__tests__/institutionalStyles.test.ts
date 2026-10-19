import { describe, it, expect } from 'vitest';
import { tierColor } from '../institutional/styles';
import { todayLocal } from '../../utils/dates';

describe('tier colours', () => {
  it('runs from muted gold at the top to crimson at the bottom', () => {
    expect(tierColor(1).hex).toBe('#C6A15B');
    expect(tierColor(2).hex).toBe('#C6A15B');
    expect(tierColor(4).hex).toBe('#3FA586');
    expect(tierColor(6).hex).toBe('#5B8DEF');
    expect(tierColor(8).hex).toBe('#C99A3E');
    expect(tierColor(10).hex).toBe('#C8464F');
  });
});

describe('todayLocal', () => {
  it('formats the local calendar date as YYYY-MM-DD', () => {
    expect(todayLocal(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
