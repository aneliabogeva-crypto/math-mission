import type { ReactNode } from 'react';

/** "Компи" — the original helper character: a friendly compass. Purely decorative. */
export function Helper({ children, mood = 'happy' }: { children: ReactNode; mood?: 'happy' | 'thinking' }) {
  return (
    <div className="helper">
      <svg viewBox="0 0 64 64" aria-hidden="true" focusable="false">
        <circle cx="32" cy="34" r="26" fill="var(--brand)" />
        <circle cx="32" cy="34" r="20" fill="var(--surface)" />
        <path d="M32 14 L36 34 L32 54 L28 34 Z" fill="var(--polynomial)" opacity=".85" />
        <circle cx="24" cy="30" r="3.5" fill="var(--text)" />
        <circle cx="40" cy="30" r="3.5" fill="var(--text)" />
        {mood === 'happy'
          ? <path d="M24 41 Q32 47 40 41" stroke="var(--text)" strokeWidth="3" fill="none" strokeLinecap="round" />
          : <path d="M25 43 L39 41" stroke="var(--text)" strokeWidth="3" fill="none" strokeLinecap="round" />}
        <circle cx="32" cy="7" r="4" fill="var(--revision)" />
      </svg>
      <div className="bubble">{children}</div>
    </div>
  );
}
