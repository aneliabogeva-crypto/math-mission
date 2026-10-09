import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { subscribe } from '../offline';
import { InstallButton } from './InstallButton';
import { useSession } from '../session';

const STUDENT_NAV = [
  { to: '/', label: 'Начало', icon: '⌂' },
  { to: '/map', label: 'Карта', icon: '🗺' },
  { to: '/tests', label: 'Тестове', icon: '✎' },
  { to: '/mistakes', label: 'Грешки', icon: '🔍' },
  { to: '/reference', label: 'Формули', icon: 'ƒ' },
];

function ThemeToggle() {
  const [theme, setTheme] = useState<string>(() => {
    try { return localStorage.getItem('mm.theme') ?? 'auto'; } catch { return 'auto'; }
  });
  useEffect(() => {
    if (theme === 'auto') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('mm.theme', theme); } catch { /* ignore */ }
  }, [theme]);
  const next = theme === 'auto' ? 'dark' : theme === 'dark' ? 'light' : 'auto';
  const label = theme === 'auto' ? 'Тема: автоматична' : theme === 'dark' ? 'Тема: тъмна' : 'Тема: светла';
  return <button type="button" className="btn ghost theme-toggle" onClick={() => setTheme(next)} aria-label={`${label}. Смени темата.`} title={label}>
    <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2" /><path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor" /></svg>
  </button>;
}

export function Layout({ children }: { children: ReactNode }) {
  const { me, signOut } = useSession();
  const [pending, setPending] = useState(0);
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => subscribe((n, o) => { setPending(n); setOnline(o); }), []);

  const staffLinks: Record<string, { to: string; label: string }[]> = {
    TEACHER: [{ to: '/teacher', label: 'Класове' }],
    GUARDIAN: [{ to: '/guardian', label: 'Родителски портал' }],
    AUTHOR: [{ to: '/content', label: 'Съдържание' }],
    REVIEWER: [{ to: '/content', label: 'Преглед' }],
    ADMIN: [{ to: '/admin', label: 'Администрация' }],
  };

  return (
    <div className={`app${me?.role === 'STUDENT' && me.status === 'ACTIVE' ? ' has-nav' : ''}`}>
      <a className="skip-link" href="#main">Към съдържанието</a>
      <header className="topbar">
        <Link className="brand" to="/"><img src={`${import.meta.env.BASE_URL}icon.svg`} alt="" width={28} height={28} /> <span className="brand-name">Math Mission</span></Link>
        {me?.role === 'STUDENT' && me.status === 'ACTIVE' && (
          <nav className="topnav" aria-label="Основна навигация">
            {STUDENT_NAV.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.to === '/'}><span className="icon" aria-hidden="true">{n.icon}</span>{n.label}</NavLink>
            ))}
          </nav>
        )}
        <span className="spacer" />
        {me && me.role !== 'STUDENT' && staffLinks[me.role]?.map((l) => <NavLink key={l.to} className="btn ghost" to={l.to}>{l.label}</NavLink>)}
        <InstallButton />
        <ThemeToggle />
        {me && <button type="button" className="btn ghost" onClick={() => void signOut()}>Изход</button>}
      </header>
      {!online && (
        <div className="banner offline" role="status">
          <span aria-hidden="true">⚡</span> Няма връзка. Можеш да продължиш — отговорите се пазят на устройството и ще се изпратят автоматично.
        </div>
      )}
      {online && pending > 0 && (
        <div className="banner sync" role="status">Изпращане на {pending} запазени отговора…</div>
      )}
      <main id="main" tabIndex={-1}>{children}</main>
      {me?.role === 'STUDENT' && me.status === 'ACTIVE' && (
        <nav className="bottomnav" aria-label="Основна навигация (долу)">
          {STUDENT_NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.to === '/'}>
              <span className="icon" aria-hidden="true">{n.icon}</span>{n.label}
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  );
}

export function Loading() {
  return <p className="muted" role="status">Зареждане…</p>;
}

export function ErrorNote({ error, onRetry }: { error: { message: string } | null; onRetry?: () => void }) {
  if (!error) return null;
  return (
    <div className="alert warn" role="alert">
      {error.message} {onRetry && <button type="button" className="btn secondary" onClick={onRetry}>Опитай пак</button>}
    </div>
  );
}
