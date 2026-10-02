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

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => { void navigator.serviceWorker.register('/sw.js'); });
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <SessionProvider>
        <App />
      </SessionProvider>
    </BrowserRouter>
  </StrictMode>,
);
