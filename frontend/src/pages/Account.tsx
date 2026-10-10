import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type Me } from '../api';
import {
  CloudError, URL_AUTH, currentSession, getProfile, linkLocal, linkedLocalId, pendingLink, saveProfile, sendPasswordReset,
  setPendingLink, signInWithPassword, signUp, updatePassword, type CloudSession, type Profile,
} from '../cloud';
import { Helper } from '../components/Helper';
import { Loading } from '../components/Layout';
import { useSession } from '../session';
import { Welcome } from './Welcome';

type Mode = 'loading' | 'login' | 'register' | 'forgot' | 'reset' | 'checkEmail' | 'onboard';
const MIN_PASSWORD = 8;
const passwordProblem = (p: string, again: string) =>
  p.length < MIN_PASSWORD ? `Паролата трябва да е поне ${MIN_PASSWORD} знака.`
    : !/[A-Za-zА-Яа-я]/.test(p) || !/\d/.test(p) ? 'Паролата трябва да съдържа поне една буква и една цифра.'
      : p !== again ? 'Двете пароли не съвпадат.' : null;

/** Opens the learner's profile on this device for an account: the linked one, or a new one from the saved profile. */
async function openOnDevice(s: CloudSession, signIn: (t: string) => Promise<Me | null>, me: Me | null): Promise<'done' | 'onboard'> {
  const linked = linkedLocalId(s.user.id);
  if (linked && (await signIn(linked))) return 'done';
  const profile: Profile | null = await getProfile(s);
  if (!profile) {
    // A device profile that was waiting for this account (registered from "Запази профила си").
    const waiting = pendingLink() ?? (me?.role === 'STUDENT' ? me.id : null);
    if (waiting && (await signIn(waiting))) {
      const m = await api<Me>('/api/me');
      await saveProfile(s, { nickname: m.displayName, avatar: m.avatar ?? 'fox' });
      linkLocal(s.user.id, waiting); setPendingLink(null);
      return 'done';
    }
    return 'onboard';
  }
  const r = await api<{ token: string }>('/api/auth/student', { method: 'POST',
    body: { nickname: profile.nickname, avatar: profile.avatar, goal: profile.goal, confidence: profile.confidence, ageBand: profile.age_band } });
  linkLocal(s.user.id, r.token);
  await signIn(r.token);
  return 'done';
}

function PasswordFields({ p, setP, again, setAgain, label = 'Парола' }: { p: string; setP: (v: string) => void; again: string; setAgain: (v: string) => void; label?: string }) {
  const [show, setShow] = useState(false);
  return (
    <>
      <label className="field"><span>{label}</span>
        <input type={show ? 'text' : 'password'} value={p} onChange={(e) => setP(e.target.value)} autoComplete="new-password" required />
      </label>
      <label className="field"><span>Повтори паролата</span>
        <input type={show ? 'text' : 'password'} value={again} onChange={(e) => setAgain(e.target.value)} autoComplete="new-password" required />
      </label>
      <label className="row small" style={{ marginBottom: '.75rem' }}>
        <input type="checkbox" checked={show} onChange={(e) => setShow(e.target.checked)} style={{ width: 22, height: 22 }} /> Покажи паролата
      </label>
      <p className="small muted">Поне {MIN_PASSWORD} знака, с поне една буква и една цифра.</p>
    </>
  );
}

/** Sign in, registration, forgotten password and new password — accounts with e-mail and password. */
export function CloudWelcome({ initial = 'login', onDone }: { initial?: Mode; onDone?: () => void } = {}) {
  const { me, signIn } = useSession();
  const nav = useNavigate();
  const [mode, setMode] = useState<Mode>('loading');
  const [wantReset] = useState(() => URL_AUTH.type === 'recovery'); // read once (effects may run twice)
  const [session, setSession] = useState<CloudSession | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [again, setAgain] = useState('');
  const [error, setError] = useState<string | null>(URL_AUTH.error ?? null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function proceed(s: CloudSession) {
    setSession(s);
    const r = await openOnDevice(s, signIn, me);
    if (r === 'done') { onDone?.(); nav('/', { replace: true }); } else setMode('onboard');
  }

  useEffect(() => {
    void (async () => {
      try {
        const s = await currentSession();
        if (s && wantReset) { setSession(s); setMode('reset'); return; }
        if (s) { await proceed(s); return; }
      } catch (e) { setError((e as Error).message); }
      setMode(initial);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function run(fn: () => Promise<void>) {
    setBusy(true); setError(null); setInfo(null);
    try { await fn(); } catch (e) { setError(e instanceof CloudError ? e.message : (e as Error).message || 'Нещо се обърка.'); } finally { setBusy(false); }
  }
  const submit = (fn: () => Promise<void>) => (e: FormEvent) => { e.preventDefault(); void run(fn); };
  const go = (m: Mode) => { setMode(m); setError(null); setInfo(null); setPassword(''); setAgain(''); };
  const emailField = (
    <label className="field"><span>Имейл</span>
      <input type="email" inputMode="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value.trim())} required />
    </label>
  );
  const notes = <>{error && <div className="alert bad" role="alert">{error}</div>}{info && <div className="alert ok" role="status">{info}</div>}</>;

  if (mode === 'loading') return <Loading />;
  if (mode === 'onboard' && session) return <Welcome cloud={session} />;

  return (
    <div className="stack" style={{ maxWidth: 480, margin: '0 auto' }}>
      <Helper>{mode === 'register' ? 'Създай профил с имейл и парола — така напредъкът ти е защитен.' : 'Здравей! Аз съм Компи. Влез, за да продължим.'}</Helper>

      {mode === 'login' && (
        <form className="card" onSubmit={submit(async () => proceed(await signInWithPassword(email, password)))}>
          <h1>Вход</h1>
          {emailField}
          <label className="field"><span>Парола</span>
            <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          {notes}
          <button className="btn block" disabled={busy}>Влез</button>
          <div className="row" style={{ justifyContent: 'space-between', marginTop: '.75rem' }}>
            <button type="button" className="btn ghost" onClick={() => go('forgot')}>Забравена парола?</button>
            <button type="button" className="btn secondary" onClick={() => go('register')}>Нов профил</button>
          </div>
        </form>
      )}

      {mode === 'register' && (
        <form className="card" onSubmit={submit(async () => {
          const problem = passwordProblem(password, again);
          if (problem) throw new CloudError(problem);
          if (me?.role === 'STUDENT') setPendingLink(me.id);
          const s = await signUp(email, password);
          if (s) await proceed(s); else setMode('checkEmail');
        })}>
          <h1>Нов профил</h1>
          {emailField}
          <p className="small muted">Ако си под 14 години, помоли родител да въведе своя имейл.</p>
          <PasswordFields p={password} setP={setPassword} again={again} setAgain={setAgain} />
          {notes}
          <button className="btn block" disabled={busy}>Създай профил</button>
          <button type="button" className="btn ghost block" onClick={() => go('login')}>Имам профил — вход</button>
        </form>
      )}

      {mode === 'checkEmail' && (
        <div className="card">
          <h1>Провери пощата си</h1>
          <p>Изпратихме писмо до <strong>{email}</strong>. Отвори го и натисни връзката, за да потвърдиш имейла. След това влез с паролата си.</p>
          <p className="small muted">Не виждаш писмото? Провери папка „Спам“ или „Промоции“.</p>
          <button className="btn block" onClick={() => go('login')}>Към входа</button>
        </div>
      )}

      {mode === 'forgot' && (
        <form className="card" onSubmit={submit(async () => {
          await sendPasswordReset(email);
          setInfo('Ако има профил с този имейл, изпратихме писмо с връзка за нова парола. Отвори го от този телефон или компютър.');
        })}>
          <h1>Забравена парола</h1>
          <p className="muted">Въведи имейла на профила. Ще ти изпратим връзка, с която да зададеш нова парола.</p>
          {emailField}
          {notes}
          <button className="btn block" disabled={busy}>Изпрати връзка</button>
          <button type="button" className="btn ghost block" onClick={() => go('login')}>Назад към входа</button>
        </form>
      )}

      {mode === 'reset' && session && (
        <form className="card" onSubmit={submit(async () => {
          const problem = passwordProblem(password, again);
          if (problem) throw new CloudError(problem);
          await updatePassword(session, password);
          URL_AUTH.type = undefined;
          setInfo('Паролата е сменена.');
          await proceed(session);
        })}>
          <h1>Нова парола</h1>
          <p className="muted">За профила {session.user.email}</p>
          <PasswordFields p={password} setP={setPassword} again={again} setAgain={setAgain} label="Нова парола" />
          {notes}
          <button className="btn block" disabled={busy}>Запази новата парола</button>
        </form>
      )}
    </div>
  );
}

/** Account settings for a signed-in learner: e-mail, change password. */
export function AccountPage() {
  const nav = useNavigate();
  const { signOut } = useSession();
  const [session, setSession] = useState<CloudSession | null | undefined>(undefined);
  const [password, setPassword] = useState('');
  const [again, setAgain] = useState('');
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { currentSession().then(setSession).catch(() => setSession(null)); }, []);
  if (session === undefined) return <Loading />;
  if (!session) return <CloudWelcome initial="register" />;
  return (
    <form className="stack" style={{ maxWidth: 520 }} onSubmit={(e) => {
      e.preventDefault();
      const problem = passwordProblem(password, again);
      if (problem) { setMsg({ ok: false, text: problem }); return; }
      setBusy(true);
      updatePassword(session, password).then(() => { setMsg({ ok: true, text: 'Паролата е сменена.' }); setPassword(''); setAgain(''); })
        .catch((err: Error) => setMsg({ ok: false, text: err.message })).finally(() => setBusy(false));
    }}>
      <h1>Профил</h1>
      <div className="card">
        <p className="small muted">Влизаш с имейла</p><p><strong>{session.user.email}</strong></p>
        <button type="button" className="btn secondary" onClick={() => void signOut().then(() => nav('/welcome'))}>Изход</button>
      </div>
      <div className="card">
        <h2>Смяна на паролата</h2>
        <PasswordFields p={password} setP={setPassword} again={again} setAgain={setAgain} label="Нова парола" />
        {msg && <div className={`alert ${msg.ok ? 'ok' : 'bad'}`} role="status">{msg.text}</div>}
        <button className="btn" disabled={busy}>Смени паролата</button>
      </div>
    </form>
  );
}
