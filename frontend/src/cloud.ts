// Accounts with e-mail and password (Supabase Auth). Passwords are stored and checked only by Supabase
// (hashed); the app keeps a short-lived session token. Profiles live in the "profiles" table, protected by
// row-level security so every account can read and change only its own row.
// Learning progress stays on the device (local mode); the account links the device profile to the e-mail.
import { STANDALONE } from './api';

export const SUPABASE_URL: string = import.meta.env.VITE_SUPABASE_URL || 'https://acdzoowaixgccjaffzza.supabase.co';
// Publishable key of the "math-mission" Supabase project: public by design (all data is protected by row-level security).
export const SUPABASE_KEY: string = import.meta.env.VITE_SUPABASE_KEY || 'sb_publishable_x-ASFz6shEQZIIG6gz9Bcg_X7wFKxau';
/** Accounts are used in the phone/web version once a project key is configured. */
export const CLOUD = STANDALONE && Boolean(SUPABASE_KEY);

const PAGES_URL = 'https://aneliabogeva-crypto.github.io/math-mission/';
const isNative = Boolean((window as Window & { Capacitor?: unknown }).Capacitor);
/** Where links from e-mails (confirmation, password reset) lead: the web version of the app. */
export const REDIRECT = isNative ? PAGES_URL : new URL(import.meta.env.BASE_URL, window.location.origin).href;

export interface CloudSession { access_token: string; refresh_token: string; expires_at: number; user: { id: string; email?: string } }
export interface Profile { id: string; nickname: string; avatar: string; age_band?: string; goal?: string; confidence?: number }

export class CloudError extends Error {}

const SESSION_KEY = 'mm.cloud.session';
const LINKS_KEY = 'mm.cloud.links';

const read = <T,>(k: string): T | null => { try { return JSON.parse(localStorage.getItem(k) ?? 'null') as T | null; } catch { return null; } };
const write = (k: string, v: unknown) => { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ } };

export const getCloudSession = () => read<CloudSession>(SESSION_KEY);
const setCloudSession = (s: CloudSession | null) => write(SESSION_KEY, s);

/** Which on-device profile belongs to which account (so the same e-mail reopens the same progress). */
export const linkedLocalId = (uid: string) => read<Record<string, string>>(LINKS_KEY)?.[uid];
export const linkLocal = (uid: string, localId: string) => write(LINKS_KEY, { ...(read<Record<string, string>>(LINKS_KEY) ?? {}), [uid]: localId });

const MESSAGES: [RegExp, string][] = [
  [/invalid login credentials|invalid_grant/i, 'Грешен имейл или парола.'],
  [/email not confirmed/i, 'Имейлът още не е потвърден. Отвори писмото, което ти изпратихме, и натисни връзката в него.'],
  [/user already registered|already been registered/i, 'Вече има профил с този имейл. Влез или използвай „Забравена парола“.'],
  [/password should be at least|weak.?password/i, 'Паролата е твърде слаба: поне 8 знака, с букви и цифри.'],
  [/unable to validate email|invalid.*email|email.*invalid/i, 'Провери имейла — изглежда не е въведен правилно.'],
  [/rate limit|too many|over_email_send_rate_limit|security purposes/i, 'Твърде много опити. Изчакай малко и опитай пак.'],
  [/same.?password|different from the old/i, 'Новата парола трябва да е различна от старата.'],
  [/expired|invalid.*token|jwt/i, 'Връзката е изтекла. Поискай нова от „Забравена парола“.'],
  [/not authorized|sending.*(confirmation|recovery)?.*email|error sending/i, 'В момента не можем да изпратим писмо до този имейл. Опитай по-късно или помоли родител да се свърже с учителя.'],
];

async function call<T>(path: string, init: { method?: string; body?: unknown; token?: string; prefer?: string } = {}): Promise<T> {
  if (!navigator.onLine) throw new CloudError('Няма връзка с интернет. Входът с имейл изисква интернет.');
  let res: Response;
  try {
    res = await fetch(SUPABASE_URL + path, {
      method: init.method ?? (init.body ? 'POST' : 'GET'),
      headers: {
        apikey: SUPABASE_KEY, 'Content-Type': 'application/json',
        // Publishable keys go only in "apikey"; Authorization carries a signed-in user's token.
        ...(init.token ? { Authorization: `Bearer ${init.token}` } : {}),
        ...(init.prefer ? { Prefer: init.prefer } : {}),
      },
      body: init.body ? JSON.stringify(init.body) : undefined,
    });
  } catch {
    throw new CloudError('Няма връзка със сървъра за профили. Опитай пак след малко.');
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const raw = String(data?.msg ?? data?.error_description ?? data?.message ?? data?.error ?? res.statusText);
    throw new CloudError(MESSAGES.find(([r]) => r.test(raw) || r.test(String(data?.error_code ?? '')))?.[1] ?? `Нещо се обърка (${raw}).`);
  }
  return data as T;
}

function toSession(d: { access_token: string; refresh_token: string; expires_in?: number; expires_at?: number; user: CloudSession['user'] }): CloudSession {
  return { access_token: d.access_token, refresh_token: d.refresh_token, user: { id: d.user.id, email: d.user.email },
    expires_at: d.expires_at ?? Math.floor(Date.now() / 1000) + (d.expires_in ?? 3600) };
}

/** Registers; returns a session, or null when the address must first be confirmed by e-mail. */
export async function signUp(email: string, password: string): Promise<CloudSession | null> {
  const d = await call<{ access_token?: string; refresh_token: string; expires_in?: number; user?: CloudSession['user']; id?: string }>(
    `/auth/v1/signup?redirect_to=${encodeURIComponent(REDIRECT)}`, { body: { email, password } });
  if (d.access_token && d.user) { const s = toSession(d as Parameters<typeof toSession>[0]); setCloudSession(s); return s; }
  return null;
}

export async function signInWithPassword(email: string, password: string): Promise<CloudSession> {
  const d = await call<Parameters<typeof toSession>[0]>('/auth/v1/token?grant_type=password', { body: { email, password } });
  const s = toSession(d); setCloudSession(s); return s;
}

export async function sendPasswordReset(email: string) {
  await call(`/auth/v1/recover?redirect_to=${encodeURIComponent(REDIRECT)}`, { body: { email } });
}

export async function updatePassword(s: CloudSession, password: string) {
  await call('/auth/v1/user', { method: 'PUT', body: { password }, token: s.access_token });
}

/** A valid session (refreshed when it has expired), or null. */
export async function currentSession(): Promise<CloudSession | null> {
  const s = getCloudSession();
  if (!s) return null;
  if (s.expires_at - 60 > Date.now() / 1000) return s;
  try {
    const d = await call<Parameters<typeof toSession>[0]>('/auth/v1/token?grant_type=refresh_token', { body: { refresh_token: s.refresh_token } });
    const n = toSession(d); setCloudSession(n); return n;
  } catch (e) {
    if (!navigator.onLine) return s; // offline: keep using the device profile
    setCloudSession(null);
    throw e;
  }
}

export async function cloudSignOut() {
  const s = getCloudSession();
  setCloudSession(null);
  if (s && navigator.onLine) { try { await call('/auth/v1/logout', { method: 'POST', token: s.access_token }); } catch { /* already signed out */ } }
}

export async function getProfile(s: CloudSession): Promise<Profile | null> {
  const rows = await call<Profile[]>(`/rest/v1/profiles?id=eq.${s.user.id}&select=id,nickname,avatar,age_band,goal,confidence`, { token: s.access_token });
  return rows[0] ?? null;
}

export async function saveProfile(s: CloudSession, p: Omit<Profile, 'id'>) {
  await call('/rest/v1/profiles?on_conflict=id', { body: { id: s.user.id, ...p, updated_at: new Date().toISOString() }, token: s.access_token,
    prefer: 'resolution=merge-duplicates,return=minimal' });
}

/** Links from e-mails come back as #access_token=…&type=recovery|signup. Returns the kind and stores the session. */
export function takeSessionFromUrl(): { type: string; session: CloudSession } | null {
  const h = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const access = h.get('access_token');
  if (!access) {
    const err = h.get('error_description');
    if (err) { history.replaceState(null, '', window.location.pathname); throw new CloudError(MESSAGES.find(([r]) => r.test(err))?.[1] ?? err); }
    return null;
  }
  let uid = ''; let email: string | undefined;
  try { const p = JSON.parse(atob(access.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))); uid = p.sub; email = p.email; } catch { return null; }
  const session: CloudSession = { access_token: access, refresh_token: h.get('refresh_token') ?? '', user: { id: uid, email },
    expires_at: Number(h.get('expires_at')) || Math.floor(Date.now() / 1000) + Number(h.get('expires_in') ?? 3600) };
  setCloudSession(session);
  history.replaceState(null, '', window.location.pathname);
  return { type: h.get('type') ?? 'signup', session };
}

/** Read once at start-up, before the router changes the URL. */
export const URL_AUTH: { type?: string; error?: string } = (() => {
  if (!CLOUD) return {};
  try { const r = takeSessionFromUrl(); return r ? { type: r.type } : {}; } catch (e) { return { error: (e as Error).message }; }
})();

/** A device profile waiting to be linked to an account whose e-mail is not confirmed yet. */
export const PENDING_LINK_KEY = 'mm.cloud.pendingLink';
export const pendingLink = () => read<string>(PENDING_LINK_KEY);
export const setPendingLink = (localId: string | null) => write(PENDING_LINK_KEY, localId);
