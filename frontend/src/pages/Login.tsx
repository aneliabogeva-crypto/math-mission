import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, STANDALONE } from '../api';
import { useSession } from '../session';

export function Login() {
  const { signIn } = useSession();
  const nav = useNavigate();
  const [code, setCode] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [totp, setTotp] = useState('');
  const [needTotp, setNeedTotp] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function studentLogin(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const r = await api<{ token: string }>('/api/auth/student/recover', { method: 'POST', body: { recoveryCode: code } });
      await signIn(r.token);
      nav('/');
    } catch (err) { setError((err as ApiError).message); }
  }

  async function staffLogin(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const r = await api<{ token: string; role: string }>('/api/auth/login', { method: 'POST', body: { username, password, totp: totp || undefined } });
      await signIn(r.token);
      nav({ TEACHER: '/teacher', GUARDIAN: '/guardian', AUTHOR: '/content', REVIEWER: '/content', ADMIN: '/admin' }[r.role] ?? '/');
    } catch (err) {
      const ae = err as ApiError;
      if (ae.code === 'MFA_REQUIRED') setNeedTotp(true);
      setError(ae.message);
    }
  }

  return (
    <div className="grid two">
      <form className="card" onSubmit={studentLogin}>
        <h2>Ученик</h2>
        <label className="field"><span>Код за вход</span>
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="ABC123-XXXXXXXXXX" autoComplete="off" />
        </label>
        <button className="btn block" disabled={!code}>Влез</button>
      </form>
      <form className="card" onSubmit={staffLogin}>
        <h2>{STANDALONE ? 'Родител' : 'Учител, родител или екип'}</h2>
        <label className="field"><span>Потребителско име</span><input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" /></label>
        <label className="field"><span>Парола</span><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
        {needTotp && (
          <label className="field"><span>Код от приложението за удостоверяване</span>
            <input inputMode="numeric" maxLength={6} value={totp} onChange={(e) => setTotp(e.target.value)} autoComplete="one-time-code" />
          </label>
        )}
        <button className="btn block" disabled={!username || !password}>Вход</button>
      </form>
      {error && <div className="alert bad" role="alert">{error}</div>}
    </div>
  );
}
