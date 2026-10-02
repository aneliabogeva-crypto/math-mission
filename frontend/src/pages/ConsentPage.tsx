import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi, useSession } from '../session';

interface Text { version: string; audience: string; title: string; paragraphs: string[] }
interface Lookup { studentNickname: string; status: string; textVersion: string; scope: string }

/** US-GUA-01: adult and child-friendly texts; consent records version, date, scope and verification. */
export function ConsentPage() {
  const { code } = useParams();
  const nav = useNavigate();
  const { me, signIn } = useSession();
  const lookup = useApi<Lookup>(`/api/consent/code/${code}`, [code]);
  const adult = useApi<Text>('/api/consent/text/adult');
  const child = useApi<Text>('/api/consent/text/child');
  const [read, setRead] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [doneForChild, setDoneForChild] = useState(false);

  if (lookup.loading || adult.loading) return <Loading />;
  if (!lookup.data || !adult.data) return <ErrorNote error={lookup.error ?? adult.error} />;
  const isGuardian = me?.role === 'GUARDIAN';
  if (doneForChild) {
    return (
      <div className="card stack">
        <h1>Благодарим! Съгласието е дадено.</h1>
        <p>Профилът на детето вече е активен. Родителският портал се отваря от „Вход“ с потребителското име и паролата, които избрахте.</p>
        <button className="btn" onClick={() => nav('/')}>Към профила на детето</button>
      </div>
    );
  }

  async function grant() {
    setError(null);
    try {
      const r = await api<{ token?: string }>(`/api/consent/code/${code}/grant`, { method: 'POST',
        body: { acceptedVersion: adult.data!.version, username: isGuardian ? undefined : username, password: isGuardian ? undefined : password } });
      if (me?.role === 'STUDENT') {
        // Shared family computer: keep the child signed in; the parent can open the portal later via "Вход".
        setDoneForChild(true);
        return;
      }
      if (r.token) await signIn(r.token);
      nav('/guardian');
    } catch (e) { setError((e as ApiError).message); }
  }

  return (
    <div className="stack">
      <h1>{adult.data.title}</h1>
      <p>Профил: <strong>{lookup.data.studentNickname}</strong> · Статус: {lookup.data.status === 'GRANTED' ? 'дадено' : lookup.data.status === 'PENDING' ? 'очаква съгласие' : 'оттеглено'}</p>
      <div className="card">
        {adult.data.paragraphs.map((p, i) => <p key={i}>{p}</p>)}
        <p className="small muted">Обхват: {lookup.data.scope} · Версия на текста: {adult.data.version}</p>
      </div>
      {child.data && (
        <details className="card">
          <summary>Версия за детето: „{child.data.title}“</summary>
          <ul>{child.data.paragraphs.map((p, i) => <li key={i}>{p}</li>)}</ul>
        </details>
      )}
      {lookup.data.status === 'PENDING' && (
        <div className="card">
          <label className="row"><input type="checkbox" checked={read} onChange={(e) => setRead(e.target.checked)} style={{ width: 24, height: 24 }} />
            Прочетох информацията и давам съгласие като родител/настойник.</label>
          {!isGuardian && (
            <>
              <p className="small muted">Създайте родителски профил, за да виждате обобщения и да управлявате съгласието.</p>
              <label className="field"><span>Потребителско име</span><input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" /></label>
              <label className="field"><span>Парола (поне 10 знака)</span><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" /></label>
            </>
          )}
          {error && <div className="alert bad" role="alert">{error}</div>}
          <button className="btn" disabled={!read || (!isGuardian && (!username || password.length < 10))} onClick={() => void grant()}>Давам съгласие</button>
        </div>
      )}
    </div>
  );
}
