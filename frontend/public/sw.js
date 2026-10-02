/* Math Mission service worker.
 * - App shell: cache-first, refreshed in the background.
 * - Lesson and practice-question GETs: network-first with cache fallback, so the currently opened
 *   lesson and its practice items keep working when the connection drops (US-STU-12).
 * - Writes (answers) are never handled here: the app keeps an idempotent outbox and resends them.
 */
const SHELL = 'mm-shell-v2';
const DATA = 'mm-data-v1';
const SHELL_URLS = ['/', '/index.html', '/manifest.webmanifest', '/icon.svg', '/icon-192.png', '/icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(SHELL_URLS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => ![SHELL, DATA].includes(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

const CACHEABLE_API = [/^\/api\/student\/lessons\/[^/]+$/, /^\/api\/student\/practice\/[^/]+$/, /^\/api\/student\/reference/,
  /^\/api\/student\/map$/, /^\/api\/student\/attempts\/[^/]+$/];

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith('/api/')) {
    if (!CACHEABLE_API.some((r) => r.test(url.pathname))) return;
    e.respondWith(
      fetch(req).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(DATA).then((c) => c.put(req, copy));
        }
        return res;
      }).catch(() => caches.match(req).then((m) => m || new Response(JSON.stringify({ code: 'OFFLINE', message: 'Няма връзка.' }),
        { status: 503, headers: { 'Content-Type': 'application/json' } }))),
    );
    return;
  }

  // Navigation and static assets: cache-first for the shell, falling back to index.html for SPA routes.
  e.respondWith(
    caches.match(req).then((hit) => {
      const network = fetch(req).then((res) => {
        if (res.ok && (req.mode === 'navigate' || url.pathname.startsWith('/assets/'))) {
          const copy = res.clone();
          caches.open(SHELL).then((c) => c.put(req.mode === 'navigate' ? '/index.html' : req, copy));
        }
        return res;
      }).catch(() => (req.mode === 'navigate' ? caches.match('/index.html') : undefined));
      return hit || network;
    }),
  );
});

self.addEventListener('message', (e) => {
  if (e.data === 'logout') caches.delete(DATA); // never keep one learner's data for the next user of a device
});
