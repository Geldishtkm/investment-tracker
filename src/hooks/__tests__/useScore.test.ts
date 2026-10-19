import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { FRESH_MS, fetchScore, peekScore, resetScoreCache } from '../useScore';

const api = vi.hoisted(() => ({ getInstitutionalScore: vi.fn() }));
vi.mock('../../services/api', () => ({ assetService: api }));

const score = (n: number) => ({ score: n, tier: { rank: 6, name: 'Systematic Swing Trader' } }) as never;

describe('shared score', () => {
  beforeEach(() => {
    resetScoreCache();
    api.getInstitutionalScore.mockReset();
  });
  afterEach(() => { vi.restoreAllMocks(); });

  it('makes one request per data version, however many readers ask', async () => {
    api.getInstitutionalScore.mockResolvedValue(score(599));
    const [a, b] = await Promise.all([fetchScore(0), fetchScore(0)]);
    expect(a).toBe(b);
    await fetchScore(0); // fresh: served from memory
    expect(api.getInstitutionalScore).toHaveBeenCalledTimes(1);
  });

  it('refetches after a trade (new version), keeping the last score visible meanwhile', async () => {
    api.getInstitutionalScore.mockResolvedValueOnce(score(599));
    await fetchScore(0);
    let resolve: (v: unknown) => void = () => {};
    api.getInstitutionalScore.mockReturnValueOnce(new Promise(r => { resolve = r; }));
    const next = fetchScore(1);
    expect((peekScore() as unknown as { score: number }).score).toBe(599); // still shown while updating
    resolve(score(612));
    await next;
    expect((peekScore() as unknown as { score: number }).score).toBe(612);
    expect(api.getInstitutionalScore).toHaveBeenCalledTimes(2);
  });

  it('refetches the same version once it is no longer fresh, or when forced', async () => {
    let now = 1_000_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    api.getInstitutionalScore.mockResolvedValue(score(599));
    await fetchScore(0);
    await fetchScore(0, { force: true });
    expect(api.getInstitutionalScore).toHaveBeenCalledTimes(2);
    now += FRESH_MS + 1;
    await fetchScore(0);
    expect(api.getInstitutionalScore).toHaveBeenCalledTimes(3);
  });

  it('never lets an older request overwrite a newer score', async () => {
    let resolveOld: (v: unknown) => void = () => {};
    api.getInstitutionalScore.mockReturnValueOnce(new Promise(r => { resolveOld = r; }))
      .mockResolvedValueOnce(score(640));
    const older = fetchScore(1);
    await fetchScore(2);
    resolveOld(score(599));
    await older;
    expect((peekScore() as unknown as { score: number }).score).toBe(640);
  });
});
