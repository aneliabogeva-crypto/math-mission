import { useEffect, useState } from 'react';

const VERSION: string | undefined = import.meta.env.VITE_APP_VERSION;
const PAGES = 'https://aneliabogeva-crypto.github.io/math-mission/';
const APK = 'https://github.com/aneliabogeva-crypto/math-mission/releases/download/v1.0.0/MathMission-android.apk';
const isNative = Boolean((window as Window & { Capacitor?: unknown }).Capacitor);

/** Tells the learner when a newer version is published and updates it with one tap. */
export function UpdateBanner() {
  const [newer, setNewer] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!VERSION) return;
    const url = (isNative ? PAGES : import.meta.env.BASE_URL) + `version.json?t=${Date.now()}`;
    const check = () => {
      if (!navigator.onLine) return;
      fetch(url, { cache: 'no-store' })
        .then((r) => (r.ok ? r.json() : null))
        .then((v: { version?: string } | null) => { if (v?.version && v.version !== VERSION) setNewer(true); })
        .catch(() => { /* offline or blocked: try again later */ });
    };
    check();
    const onVisible = () => { if (document.visibilityState === 'visible') check(); };
    document.addEventListener('visibilitychange', onVisible);
    const timer = window.setInterval(check, 30 * 60 * 1000);
    return () => { document.removeEventListener('visibilitychange', onVisible); window.clearInterval(timer); };
  }, []);

  if (!newer) return null;

  async function update() {
    setBusy(true);
    try {
      const reg = await navigator.serviceWorker?.getRegistration();
      await reg?.update();
      const keys = await caches.keys();
      await Promise.all(keys.filter((k) => k.startsWith('mm-shell')).map((k) => caches.delete(k)));
    } catch { /* reload anyway */ }
    window.location.reload();
  }

  return (
    <div className="banner sync" role="status">
      <span aria-hidden="true">✨</span>
      <span style={{ flex: 1 }}>Има нова версия на приложението с нови уроци и тестове.</span>
      {isNative
        ? <a className="btn" href={APK}>Изтегли новата версия</a>
        : <button type="button" className="btn" disabled={busy} onClick={() => void update()}>Обнови сега</button>}
    </div>
  );
}
