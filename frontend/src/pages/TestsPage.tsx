import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useState } from 'react';
import { api, ApiError, uuid, type AttemptView, type TestSummary } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';
import { TOPICS, placeTests } from '../topics';
import { TestRow, TopicView } from '../components/TopicView';
import curriculum from '../local/data/curriculum.json';

const LESSON_TITLES = new Map((curriculum as { lessons: { key: string; title: string }[] }).lessons.map((l) => [l.key, l.title]));

/** Tests by topic: under each lesson its own tests, at the end of the topic the summary tests and class-test variants. */
export function TestsPage() {
  const { data, error, loading, reload } = useApi<TestSummary[]>('/api/student/tests');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const placed = placeTests(data);
  const done = data.filter((t) => t.completedAttempts > 0).length;
  return (
    <div>
      <h1>Тестове</h1>
      <p className="muted">Подредени по теми: тестовете към всеки урок, а накрая на темата — обобщаващите тестове. Решени: {done} от {data.length}.</p>
      <div className="progress" aria-hidden="true" style={{ marginBottom: '1rem' }}><span style={{ width: `${data.length ? (done / data.length) * 100 : 0}%` }} /></div>
      {TOPICS.map((t, i) => <TopicView key={t.id} topic={t} index={i + 1} placed={placed} titles={LESSON_TITLES} testsOnly />)}
      {placed.other.length > 0 && (
        <section className="zone"><h2>Още тестове</h2><ul className="test-list">{placed.other.map((t) => <TestRow key={t.id} t={t} />)}</ul></section>
      )}
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
