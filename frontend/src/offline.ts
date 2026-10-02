// Idempotent outbox for answers confirmed while offline (US-STU-02, US-STU-12).
// Every entry carries a requestId generated once; the server acknowledges duplicates without
// changing anything, so resending after a reconnect never creates duplicate points or attempts.

import { api, ApiError } from './api';

export interface OutboxEntry {
  id: string;            // == requestId
  path: string;
  method: 'PUT' | 'POST';
  body: unknown;
  createdAt: number;
}

const KEY = 'mm.outbox';
type Listener = (pending: number, online: boolean) => void;
const listeners = new Set<Listener>();

function read(): OutboxEntry[] {
  try { return JSON.parse(localStorage.getItem(KEY) ?? '[]'); } catch { return memory; }
}
let memory: OutboxEntry[] = [];
function write(entries: OutboxEntry[]) {
  memory = entries;
  try { localStorage.setItem(KEY, JSON.stringify(entries)); } catch { /* memory only */ }
  notify();
}

function notify() {
  const n = read().length;
  listeners.forEach((l) => l(n, navigator.onLine));
}

export function subscribe(l: Listener): () => void {
  listeners.add(l);
  l(read().length, navigator.onLine);
  return () => listeners.delete(l);
}

export function pendingFor(prefix: string): OutboxEntry[] {
  return read().filter((e) => e.path.startsWith(prefix));
}

/** Sends now if possible; otherwise keeps the entry locally until the next flush. */
export async function send<T>(entry: Omit<OutboxEntry, 'createdAt'>): Promise<{ queued: boolean; result?: T }> {
  // Replace an older pending save for the same item; the newest answer wins.
  write([...read().filter((e) => e.path !== entry.path || e.method !== entry.method), { ...entry, createdAt: Date.now() }]);
  try {
    const result = await api<T>(entry.path, { method: entry.method, body: entry.body });
    write(read().filter((e) => e.id !== entry.id));
    return { queued: false, result };
  } catch (err) {
    if (err instanceof ApiError && err.status !== 0 && err.status < 500) {
      write(read().filter((e) => e.id !== entry.id)); // a real refusal (e.g. test submitted): do not retry forever
      throw err;
    }
    return { queued: true };
  }
}

let flushing = false;
export async function flush(): Promise<void> {
  if (flushing || !navigator.onLine) return;
  flushing = true;
  try {
    for (const e of [...read()].sort((a, b) => a.createdAt - b.createdAt)) {
      try {
        await api(e.path, { method: e.method, body: e.body });
        write(read().filter((x) => x.id !== e.id));
      } catch (err) {
        if (err instanceof ApiError && err.status !== 0 && err.status < 500) {
          write(read().filter((x) => x.id !== e.id));
        } else {
          break; // still offline or server unavailable; try again later
        }
      }
    }
  } finally {
    flushing = false;
    notify();
  }
}

export function startSync() {
  window.addEventListener('online', () => { notify(); void flush(); });
  window.addEventListener('offline', notify);
  setInterval(() => void flush(), 15000);
  void flush();
}

export function clearOutbox() {
  write([]);
}
