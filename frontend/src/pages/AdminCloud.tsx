import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Avatar } from '../components/Avatar';
import { ErrorNote, Loading } from '../components/Layout';
import { MathText, RichText } from '../components/MathText';
import {
  adminAttempts, adminAttemptsWithItems, adminProfiles, currentSession, type AdminProfile, type AttemptItem, type AttemptRecord, type CloudSession,
} from '../cloud';

// The administrator's view of the registered learners: who they are, which tests they solved, their grades,
// and — inside every test — where they made mistakes. Data comes from the accounts database (read-only).

function useCloud<T>(load: (s: CloudSession) => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<{ message: string } | null>(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setError(null);
    currentSession()
      .then((s) => { if (!s) throw new Error('Влез отново.'); return load(s); })
      .then((d) => { if (live) setData(d); })
      .catch((e: Error) => { if (live) setError({ message: e.message }); });
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick, ...deps]);
  return { data, error, reload: () => setTick((t) => t + 1) };
}

const fmtDate = (iso?: string) => (iso ? new Date(iso).toLocaleString('bg-BG', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');
const pct = (p?: number) => (p == null ? '—' : `${String(Math.round(p * 10) / 10).replace('.', ',')}%`);
const avg = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : undefined);
const GRADE_CLASS = (g?: number) => (g == null ? '' : g >= 5 ? 'ok' : g >= 4 ? 'info' : g >= 3 ? 'warn' : 'bad');
const STATUS: Record<string, [string, string]> = {
  CORRECT: ['✓', 'Вярно'], PARTIAL: ['◐', 'Частично'], INCORRECT: ['✗', 'Грешно'], UNANSWERED: ['○', 'Без отговор'],
  INVALID_INPUT: ['ⓘ', 'Неразчетен запис'], NEEDS_REVIEW: ['…', 'За преглед'],
};
const wrong = (it: AttemptItem) => it.status !== 'CORRECT';

function Grade({ a }: { a: { grade?: number; grade_label?: string } }) {
  if (a.grade == null) return <span className="chip">—</span>;
  return <span className={`chip grade-${GRADE_CLASS(a.grade)}`}>{a.grade_label ? `${a.grade_label} (${a.grade})` : a.grade}</span>;
}

/** Most frequent mistake types over a set of attempts. */
function MistakeSummary({ attempts }: { attempts: AttemptRecord[] }) {
  const counts = new Map<string, number>();
  attempts.forEach((a) => (a.misconceptions ?? []).forEach((m) => counts.set(m.label, (counts.get(m.label) ?? 0) + m.count)));
  const top = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);
  if (!top.length) return <p className="small muted">Още няма грешки за отчитане.</p>;
  const max = top[0][1];
  return (
    <ul className="bars">
      {top.map(([label, n]) => (
        <li key={label}><span className="bar-label">{label}</span><span className="bar"><span style={{ width: `${(n / max) * 100}%` }} /></span><strong>{n}</strong></li>
      ))}
    </ul>
  );
}

export function AdminNav() {
  return (
    <nav className="row" aria-label="Администрация" style={{ marginBottom: '1rem' }}>
      <Link className="btn secondary" to="/admin/children">Деца</Link>
      <Link className="btn secondary" to="/admin/tests">Тестове</Link>
    </nav>
  );
}

/** All registered learners with their activity. */
export function AdminChildren() {
  const { data, error, reload } = useCloud(async (s) => {
    const [profiles, attempts] = await Promise.all([adminProfiles(s), adminAttempts(s)]);
    return { profiles, attempts, me: s.user.id };
  });
  if (!data) return error ? <ErrorNote error={error} onRetry={reload} /> : <Loading />;
  const by = new Map<string, AttemptRecord[]>();
  data.attempts.forEach((a) => by.set(a.user_id, [...(by.get(a.user_id) ?? []), a]));
  const kids = data.profiles.filter((p) => p.id !== data.me); // the administrator's own profile is not a learner
  return (
    <div>
      <h1>Деца</h1>
      <AdminNav />
      <p className="muted">Регистрирани: {kids.length} · Решени тестове общо: {data.attempts.length}</p>
      {kids.length === 0 && <p>Още няма регистрирани деца.</p>}
      <ul className="admin-list">
        {kids.map((p: AdminProfile) => {
          const list = by.get(p.id) ?? [];
          const av = avg(list.map((a) => a.percent ?? 0));
          return (
            <li key={p.id} className="card admin-row">
              <Avatar id={p.avatar} />
              <div className="body">
                <div className="title">{p.nickname}</div>
                <div className="small muted">{p.email ?? '—'} · {p.age_band === 'UNDER_14' ? 'под 14 г.' : '14+ г.'} · регистриран {fmtDate(p.created_at)}</div>
                <div className="small">Тестове: <strong>{list.length}</strong> · Среден резултат: <strong>{pct(av)}</strong> · Последен: {fmtDate(list[0]?.submitted_at)}</div>
              </div>
              <Link className="btn" to={`/admin/child/${p.id}`}>Виж</Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** One learner: every submitted test with grade, and where they go wrong most often. */
export function AdminChild() {
  const { id } = useParams();
  const { data, error, reload } = useCloud(async (s) => {
    const [profiles, attempts] = await Promise.all([adminProfiles(s), adminAttempts(s, `user_id=eq.${id}`)]);
    return { profile: profiles.find((p) => p.id === id), attempts };
  }, [id]);
  if (!data) return error ? <ErrorNote error={error} onRetry={reload} /> : <Loading />;
  const { profile, attempts } = data;
  return (
    <div className="stack">
      <AdminNav />
      <div className="row"><Avatar id={profile?.avatar} /><div><h1 style={{ margin: 0 }}>{profile?.nickname ?? 'Ученик'}</h1><div className="small muted">{profile?.email}</div></div></div>
      <div className="grid three">
        <div className="card"><div className="small muted">Решени тестове</div><div className="big">{attempts.length}</div></div>
        <div className="card"><div className="small muted">Среден резултат</div><div className="big">{pct(avg(attempts.map((a) => a.percent ?? 0)))}</div></div>
        <div className="card"><div className="small muted">Средна оценка</div><div className="big">{attempts.length ? String(Math.round((avg(attempts.map((a) => a.grade ?? 2)) ?? 0) * 100) / 100).replace('.', ',') : '—'}</div></div>
      </div>
      <div className="card"><h2>Къде бърка най-често</h2><MistakeSummary attempts={attempts} /></div>
      <h2>Тестове</h2>
      {attempts.length === 0 && <p className="muted">Още няма предадени тестове.</p>}
      <ul className="test-list">
        {attempts.map((a) => (
          <li key={a.id} className="test-row">
            <div className="body">
              <div className="title">{a.test_title}</div>
              <div className="small">{fmtDate(a.submitted_at)} · {a.points ?? 0} от {a.max_points ?? 0} т. · <strong>{pct(a.percent)}</strong> <Grade a={a} /></div>
            </div>
            <Link className="btn" to={`/admin/attempt/${a.user_id}/${a.id}`}>Отвори</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ItemCard({ it }: { it: AttemptItem }) {
  const [icon, label] = STATUS[it.status] ?? ['?', it.status];
  return (
    <li className={`card admin-item ${it.status === 'CORRECT' ? 'is-ok' : it.status === 'PARTIAL' ? 'is-partial' : 'is-bad'}`}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>Въпрос {it.n}</strong>
        <span className="small"><span aria-hidden="true">{icon}</span> {label} · {it.points ?? 0} от {it.max} т.</span>
      </div>
      <p className="small muted">{it.skill}</p>
      <p><RichText text={it.prompt} /></p>
      {it.options && <ul className="small">{it.options.map((o) => <li key={o}><MathText>{o}</MathText></li>)}</ul>}
      <div className="answer-grid">
        <div><div className="small muted">Отговор на детето</div><div className="math">{it.answer || '—'}</div></div>
        {it.correct && <div><div className="small muted">Верен отговор</div><div className="math">{it.correct}</div></div>}
      </div>
      {wrong(it) && (it.misconception || it.feedback) && (
        <div className="alert bad small">{it.misconception && <><strong>Вид грешка:</strong> {it.misconception}. </>}{it.feedback}</div>
      )}
    </li>
  );
}

/** One submitted test, question by question. */
export function AdminAttempt() {
  const { uid, id } = useParams();
  const [onlyWrong, setOnlyWrong] = useState(true);
  const { data, error, reload } = useCloud(async (s) => {
    const [a] = await adminAttemptsWithItems(s, `user_id=eq.${uid}&id=eq.${encodeURIComponent(id ?? '')}`);
    const profile = (await adminProfiles(s)).find((p) => p.id === uid);
    return { a, profile };
  }, [uid, id]);
  if (!data) return error ? <ErrorNote error={error} onRetry={reload} /> : <Loading />;
  const { a, profile } = data;
  if (!a) return <ErrorNote error={{ message: 'Тестът не е намерен.' }} />;
  const items = a.items ?? [];
  const bad = items.filter(wrong);
  return (
    <div className="stack">
      <AdminNav />
      <Link to={`/admin/child/${uid}`}>← {profile?.nickname ?? 'Ученик'}</Link>
      <h1>{a.test_title}</h1>
      <div className="card accent">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <div><div className="big">{pct(a.percent)}</div><div className="small muted">{a.points ?? 0} от {a.max_points ?? 0} точки · {fmtDate(a.submitted_at)}</div></div>
          <Grade a={a} />
        </div>
        <p className="small">Верни: {items.length - bad.length} от {items.length} · Грешни или непълни: {bad.length}</p>
      </div>
      <div className="card"><h2>Видове грешки в този тест</h2><MistakeSummary attempts={[a]} /></div>
      <label className="row small"><input type="checkbox" checked={onlyWrong} onChange={(e) => setOnlyWrong(e.target.checked)} style={{ width: 22, height: 22 }} /> Покажи само грешните въпроси</label>
      <ol className="admin-items">{(onlyWrong ? bad : items).map((it) => <ItemCard key={it.n} it={it} />)}</ol>
      {onlyWrong && bad.length === 0 && <p className="alert ok">Всички отговори са верни.</p>}
    </div>
  );
}

/** All tests that have been solved, with averages. */
export function AdminTests() {
  const { data, error, reload } = useCloud((s) => adminAttempts(s));
  if (!data) return error ? <ErrorNote error={error} onRetry={reload} /> : <Loading />;
  const by = new Map<string, AttemptRecord[]>();
  data.forEach((a) => by.set(a.test_key, [...(by.get(a.test_key) ?? []), a]));
  return (
    <div>
      <h1>Тестове</h1>
      <AdminNav />
      {by.size === 0 && <p>Още няма решени тестове.</p>}
      <ul className="test-list">
        {[...by.entries()].map(([key, list]) => (
          <li key={key} className="test-row">
            <div className="body">
              <div className="title">{list[0].test_title}</div>
              <div className="small">Решавания: {list.length} · Деца: {new Set(list.map((a) => a.user_id)).size} · Среден резултат: <strong>{pct(avg(list.map((a) => a.percent ?? 0)))}</strong></div>
            </div>
            <Link className="btn" to={`/admin/test/${encodeURIComponent(key)}`}>Къде грешат</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** One test across all learners: which questions are missed most and with which wrong answers. */
export function AdminTest() {
  const { key } = useParams();
  const { data, error, reload } = useCloud(async (s) => {
    const [attempts, profiles] = await Promise.all([adminAttemptsWithItems(s, `test_key=eq.${encodeURIComponent(key ?? '')}`), adminProfiles(s)]);
    return { attempts, names: new Map(profiles.map((p) => [p.id, p.nickname])) };
  }, [key]);
  if (!data) return error ? <ErrorNote error={error} onRetry={reload} /> : <Loading />;
  const { attempts, names } = data;
  if (!attempts.length) return <ErrorNote error={{ message: 'Този тест още не е решаван.' }} />;
  const qs = new Map<string, { it: AttemptItem; total: number; ok: number; answers: Map<string, number>; who: string[] }>();
  for (const a of attempts) {
    for (const it of a.items ?? []) {
      const e = qs.get(it.key) ?? { it, total: 0, ok: 0, answers: new Map<string, number>(), who: [] as string[] };
      e.total++;
      if (it.status === 'CORRECT') e.ok++;
      else { e.answers.set(it.answer || '—', (e.answers.get(it.answer || '—') ?? 0) + 1); e.who.push(names.get(a.user_id) ?? '?'); }
      qs.set(it.key, e);
    }
  }
  const rows = [...qs.values()].sort((x, y) => x.ok / x.total - y.ok / y.total);
  return (
    <div className="stack">
      <AdminNav />
      <h1>{attempts[0].test_title}</h1>
      <p className="muted">Решавания: {attempts.length} · Среден резултат: {pct(avg(attempts.map((a) => a.percent ?? 0)))}. Въпросите са подредени от най-трудния.</p>
      <div className="card">
        <h2>Резултати по деца</h2>
        <ul className="test-list">
          {attempts.map((a) => (
            <li key={a.user_id + a.id} className="test-row">
              <div className="body"><div className="title">{names.get(a.user_id) ?? '?'}</div><div className="small">{fmtDate(a.submitted_at)} · <strong>{pct(a.percent)}</strong> <Grade a={a} /></div></div>
              <Link className="btn secondary" to={`/admin/attempt/${a.user_id}/${a.id}`}>Отвори</Link>
            </li>
          ))}
        </ul>
      </div>
      <ol className="admin-items">
        {rows.map(({ it, total, ok, answers, who }) => (
          <li key={it.key} className={`card admin-item ${ok === total ? 'is-ok' : 'is-bad'}`}>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <strong>Въпрос {it.n}</strong>
              <span className="small">Верно: {ok} от {total} ({pct((ok / total) * 100)})</span>
            </div>
            <div className="bar" aria-hidden="true"><span style={{ width: `${(ok / total) * 100}%` }} /></div>
            <p><RichText text={it.prompt} /></p>
            {it.correct && <p className="small">Верен отговор: <span className="math">{it.correct}</span></p>}
            {answers.size > 0 && (
              <div className="small">
                <div className="muted">Грешни отговори:</div>
                <ul>{[...answers.entries()].sort((a, b) => b[1] - a[1]).map(([ans, n]) => <li key={ans}><span className="math">{ans}</span> — {n} {n === 1 ? 'път' : 'пъти'}</li>)}</ul>
                <div className="muted">Сгрешили: {who.join(', ')}</div>
              </div>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}
