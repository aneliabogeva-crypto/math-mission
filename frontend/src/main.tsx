import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { startSync } from './offline';
import { SessionProvider } from './session';
import './styles.css';

try {
  const theme = localStorage.getItem('mm.theme');
  if (theme && theme !== 'auto') document.documentElement.setAttribute('data-theme', theme);
} catch { /* ignore */ }

startSync();

const isNativeApp = Boolean((window as Window & { Capacitor?: unknown }).Capacitor);
if ('serviceWorker' in navigator && import.meta.env.PROD && !isNativeApp) {
  // A new service worker takes over immediately (skipWaiting); reload once so the new version is shown.
  const hadController = Boolean(navigator.serviceWorker.controller);
  let reloaded = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (hadController && !reloaded) { reloaded = true; window.location.reload(); }
  });
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`, { updateViaCache: 'none' })
      .then((reg) => { document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void reg.update(); }); });
  });
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '')}>
      <SessionProvider>
        <App />
      </SessionProvider>
    </BrowserRouter>
  </StrictMode>,
);
