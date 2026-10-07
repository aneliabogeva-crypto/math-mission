import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useState } from 'react';
import { api, ApiError, uuid, type AttemptView, type TestSummary } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

/** Lessons to revise before a topic group of tests. */
const GROUP_LESSONS: Record<string, { key: string; title: string }[]> = {
  'Действия с многочлени': [{ key: 'C3', title: 'Събиране и изваждане' }, { key: 'C4', title: 'Многочлен по едночлен' }, { key: 'C5', title: 'Многочлен по многочлен' }],
};
const GROUP_NOTE: Record<string, string> = {
  'Действия с многочлени': 'Тренировки по всяко действие поотделно — с подсказки.',
  'Подготовка за контролно': 'Като истинско контролно: 20 задачи от трите действия, 40 минути, без подсказки. Реши поне два варианта.',
};

/** Tests grouped into the 7-day programme ("Ден N · …" titles) and topic groups ("Тема · …"). */
export function TestsPage() {
  const { data, error, loading, reload } = useApi<TestSummary[]>('/api/student/tests');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const days = new Map<number, TestSummary[]>();
  const groups = new Map<string, TestSummary[]>();
  const other: TestSummary[] = [];
  for (const t of data) {
    const m = /^Ден (\d+) · /.exec(t.title);
    const g = /^([^·]+) · /.exec(t.title);
    if (m) days.set(Number(m[1]), [...(days.get(Number(m[1])) ?? []), t]);
    else if (g) groups.set(g[1].trim(), [...(groups.get(g[1].trim()) ?? []), t]);
    else other.push(t);
  }
  const done = data.filter((t) => t.completedAttempts > 0).length;
  const card = (t: TestSummary) => (
    <div key={t.id} className="card" style={{ marginBottom: 8 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="chip info">{t.kindLabel}</span>
        {t.completedAttempts > 0 && <span className="chip ok">✓ Решен{t.bestPercent != null ? ` · ${String(t.bestPercent).replace('.', ',')}%` : ''}</span>}
      </div>
      <h3 style={{ marginTop: '.5rem' }}>{t.title.replace(/^[^·]+ · /, '')}</h3>
      <p className="small muted">{t.questionCount} въпроса · {t.timeLimitMin} минути{t.hintPolicy === 'ALLOWED' ? ' · с подсказки' : ''}</p>
      <Link className={`btn ${t.completedAttempts > 0 ? 'secondary' : ''}`} to={`/tests/${t.id}`}>
        {t.inProgressAttemptId ? 'Продължи' : t.completedAttempts > 0 ? 'Реши отново' : 'Започни'}
      </Link>
    </div>
  );
  return (
    <div>
      <h1>Тестове</h1>
      <p className="muted">Програма за 7 дни, тренировки по теми и подготовка за контролно. Решени: {done} от {data.length}.</p>
      <div className="progress" aria-hidden="true" style={{ marginBottom: '1rem' }}><span style={{ width: `${data.length ? (done / data.length) * 100 : 0}%` }} /></div>
      {[...days.entries()].sort((a, b) => a[0] - b[0]).map(([day, list]) => (
        <section key={day} aria-labelledby={`day-${day}`} style={{ marginBottom: '1rem' }}>
          <h2 id={`day-${day}`}>Ден {day} {list.every((t) => t.completedAttempts > 0) && <span className="chip ok">✓ изпълнен</span>}</h2>
          {list.map(card)}
        </section>
      ))}
      {[...groups.entries()].map(([name, list]) => (
        <section key={name} style={{ marginBottom: '1rem' }}>
          <h2>{name} {list.every((t) => t.completedAttempts > 0) && <span className="chip ok">✓ изпълнено</span>}</h2>
          {GROUP_NOTE[name] && <p className="small muted">{GROUP_NOTE[name]}</p>}
          {GROUP_LESSONS[name] && (
            <div className="row" style={{ flexWrap: 'wrap', gap: 6, marginBottom: 8 }}>
              <span className="small muted">Преговори уроците:</span>
              {GROUP_LESSONS[name].map((l) => <Link key={l.key} className="btn secondary" to={`/lesson/${l.key}`}>{l.key} · {l.title}</Link>)}
            </div>
          )}
          {list.map(card)}
        </section>
      ))}
      {other.length > 0 && <section><h2>Още тестове</h2>{other.map(card)}</section>}
      {data.length === 0 && <p>Все още няма публикувани тестове.</p>}
    </div>
  );
}

/** US-STU-08: scale, time and scope are visible before starting. */
export function TestIntro() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const assignmentId = params.get('assignment');
  const nav = useNavigate();
  const { data, error, loading, reload } = useApi<TestSummary[]>('/api/student/tests');
  const [startError, setStartError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  if (loading && !data) return <Loading />;
  const t = data?.find((x) => x.id === id);
  if (!t) return <ErrorNote error={error ?? { message: 'Тестът не е намерен.' }} onRetry={reload} />;

  async function start() {
    setBusy(true);
    // The attempt id is generated once and remembered, so a retried "start" never creates a second attempt.
    const storeKey = `mm.start.${id}.${assignmentId ?? ''}`;
    let attemptId: string;
    try { attemptId = sessionStorage.getItem(storeKey) ?? uuid(); sessionStorage.setItem(storeKey, attemptId); } catch { attemptId = uuid(); }
    try {
      const a = await api<AttemptView>(`/api/student/tests/${id}/attempts`, { method: 'POST', body: { attemptId, assignmentId } });
      try { sessionStorage.removeItem(storeKey); } catch { /* ignore */ }
      nav(`/attempt/${a.attemptId}`);
    } catch (e) {
      const err = e as ApiError;
      if (err.code.startsWith('ATTEMPT_IN_PROGRESS:')) nav(`/attempt/${err.code.split(':')[1]}`);
      else setStartError(err.message);
    } finally { setBusy(false); }
  }

  return (
    <div className="stack">
      <span className="chip info">{t.kindLabel}</span>
      <h1>{t.title}</h1>
      <div className="card">
        <h2>Преди да започнеш</h2>
        <ul>
          <li><strong>{t.questionCount}</strong> въпроса, време: <strong>{t.timeLimitMin} минути</strong>.</li>
          <li>Отговорите се запазват след всеки въпрос. Ако връзката прекъсне, продължаваш оттам, докъдето си стигнал(а).</li>
          <li>Можеш да маркираш въпрос с ⚑ и да се върнеш към него преди предаване.</li>
          <li>{t.hintPolicy === 'ALLOWED' ? 'Подсказките са разрешени, но се отчитат при оценката на уменията.' : 'В този тест няма подсказки.'}</li>
        </ul>
      </div>
      {t.gradingBands && (
        <div className="card">
          <h2>Скала за оценяване</h2>
          <table className="data">
            <thead><tr><th>Процент</th><th>Учебна оценка</th></tr></thead>
            <tbody>
              {t.gradingBands.map((b, i) => {
                const next = t.gradingBands![i + 1];
                return <tr key={b.grade}><td>{b.minPercent}–{next ? next.minPercent - 1 : 100}%</td><td>{b.labelBg} ({b.grade})</td></tr>;
              })}
            </tbody>
          </table>
        </div>
      )}
      <div className="alert info small">{t.scoringNote}<br />{t.scopeNote}</div>
      {startError && <div className="alert bad" role="alert">{startError}</div>}
      <button className="btn block" disabled={busy} onClick={() => (t.inProgressAttemptId ? nav(`/attempt/${t.inProgressAttemptId}`) : void start())}>
        {t.inProgressAttemptId ? 'Продължи започнатия опит' : 'Започни теста'}
      </button>
    </div>
  );
}
