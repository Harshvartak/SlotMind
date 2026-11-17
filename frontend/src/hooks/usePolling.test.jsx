import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { usePolling } from './usePolling.js';

const INTERVAL = 1000;

async function tick(ms) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe('usePolling', () => {
  it('loads immediately and then on every interval', async () => {
    const fetcher = vi.fn().mockResolvedValue(['a']);
    const { result } = renderHook(() => usePolling(fetcher, INTERVAL));

    await tick(0);
    expect(result.current.data).toEqual(['a']);
    expect(result.current.loading).toBe(false);
    expect(fetcher).toHaveBeenCalledTimes(1);

    await tick(INTERVAL);
    await tick(INTERVAL);
    expect(fetcher).toHaveBeenCalledTimes(3);
  });

  it('keeps the last good data and reports the error when a poll fails', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(['a']).mockRejectedValueOnce(new Error('down'));
    const { result } = renderHook(() => usePolling(fetcher, INTERVAL));

    await tick(0);
    await tick(INTERVAL);

    expect(result.current.data).toEqual(['a']);
    expect(result.current.error).toBe('down');
  });

  it('clears the error once the server answers again', async () => {
    const fetcher = vi
      .fn()
      .mockRejectedValueOnce(new Error('down'))
      .mockResolvedValueOnce(['b']);
    const { result } = renderHook(() => usePolling(fetcher, INTERVAL));

    await tick(0);
    expect(result.current.error).toBe('down');
    await tick(INTERVAL);

    expect(result.current.error).toBeNull();
    expect(result.current.data).toEqual(['b']);
  });

  it('refresh reloads right away', async () => {
    const fetcher = vi.fn().mockResolvedValue([]);
    const { result } = renderHook(() => usePolling(fetcher, INTERVAL));
    await tick(0);

    await act(async () => {
      await result.current.refresh();
    });

    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('stops polling when unmounted', async () => {
    const fetcher = vi.fn().mockResolvedValue([]);
    const { unmount } = renderHook(() => usePolling(fetcher, INTERVAL));
    await tick(0);

    unmount();
    await tick(INTERVAL * 3);

    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});
