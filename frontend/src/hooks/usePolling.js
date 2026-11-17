import { useCallback, useEffect, useRef, useState } from 'react';

export const POLL_MS = 5000;

/** Calls `fetcher` now and every `intervalMs`; `refresh` forces an immediate reload. */
export function usePolling(fetcher, intervalMs = POLL_MS) {
  const [data, setData] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const refresh = useCallback(async () => {
    try {
      setData(await fetcherRef.current());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, intervalMs);
    return () => clearInterval(timer);
  }, [refresh, intervalMs]);

  return { data, error, loading, refresh };
}
