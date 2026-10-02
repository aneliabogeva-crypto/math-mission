import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError, getToken, setToken, type Me } from './api';
import { clearOutbox } from './offline';

interface Session {
  me: Me | null;
  loading: boolean;
  signIn: (token: string) => Promise<Me | null>;
  signOut: () => Promise<void>;
  refresh: () => Promise<Me | null>;
}

const Ctx = createContext<Session>(null as unknown as Session);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!getToken()) { setMe(null); setLoading(false); return null; }
    try {
      const m = await api<Me>('/api/me');
      setMe(m);
      try { localStorage.setItem('mm.me', JSON.stringify(m)); } catch { /* ignore */ }
      return m;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) { setToken(null); setMe(null); return null; }
      // Offline: keep the last known identity so the cached lesson still opens.
      try {
        const cached = JSON.parse(localStorage.getItem('mm.me') ?? 'null') as Me | null;
        setMe(cached);
        return cached;
      } catch { return null; }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const signIn = useCallback(async (token: string) => { setToken(token); return refresh(); }, [refresh]);
  const signOut = useCallback(async () => {
    try { await api('/api/me/logout', { method: 'POST' }); } catch { /* offline logout still clears the device */ }
    setToken(null);
    clearOutbox();
    try { localStorage.removeItem('mm.me'); } catch { /* ignore */ }
    navigator.serviceWorker?.controller?.postMessage('logout');
    setMe(null);
  }, []);

  return <Ctx.Provider value={{ me, loading, signIn, signOut, refresh }}>{children}</Ctx.Provider>;
}

export const useSession = () => useContext(Ctx);

/** Small data hook with loading/error state. */
export function useApi<T>(path: string | null, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(Boolean(path));
  const load = useCallback(async () => {
    if (!path) return;
    setLoading(true);
    try { setData(await api<T>(path)); setError(null); } catch (e) { setError(e as ApiError); } finally { setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, ...deps]);
  useEffect(() => { void load(); }, [load]);
  return { data, error, loading, reload: load, setData };
}
