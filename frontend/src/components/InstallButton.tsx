import { useEffect, useState } from 'react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/** "Install app" button for Chrome/Edge/Android; a short hint on iPhone/iPad (Share → Add to Home Screen). */
export function InstallButton() {
  const [evt, setEvt] = useState<BeforeInstallPromptEvent | null>(null);
  const [iosHint, setIosHint] = useState(false);
  const standalone = window.matchMedia?.('(display-mode: standalone)').matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true;

  useEffect(() => {
    const onPrompt = (e: Event) => { e.preventDefault(); setEvt(e as BeforeInstallPromptEvent); };
    const onInstalled = () => setEvt(null);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  if (standalone) return null;
  const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);

  if (evt) {
    return (
      <button type="button" className="btn ghost" onClick={async () => { await evt.prompt(); await evt.userChoice; setEvt(null); }}>
        ⤓ Инсталирай
      </button>
    );
  }
  if (isIos) {
    return (
      <>
        <button type="button" className="btn ghost" aria-expanded={iosHint} onClick={() => setIosHint((v) => !v)}>⤓ Инсталирай</button>
        {iosHint && (
          <div className="alert info small" role="status" style={{ position: 'absolute', top: 56, right: 16, left: 16, zIndex: 30 }}>
            Натисни бутона „Сподели“ <span aria-hidden="true">⬆︎</span>, после „Добави към началния екран“.
          </div>
        )}
      </>
    );
  }
  return null;
}
