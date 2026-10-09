import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useState } from 'react';
import { api, ApiError, uuid, type AttemptView, type TestSummary } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

/** Topic groups in study order; test titles are "Тема · Име". */
const GROUP_ORDER = ['Рационални изрази', 'Едночлени', 'Многочлени', 'Действия с многочлени', 'Обобщителни тестове', 'Подготовка за контролно'];
const GROUP_LESSONS: Record<string, { key: string; title: string }[]> = {
  'Рационални изрази': [{ key: 'A2', title: 'Цели и дробни изрази' }, { key: 'A3', title: 'Числена стойност' }],
  'Едночлени': [{ key: 'B3', title: 'Подобни едночлени' }, { key: 'B4', title: 'Умножение' }, { key: 'B5', title: 'Степенуване' }],
  'Многочлени': [{ key: 'C6', title: 'Квадрат на двучлен' }, { key: 'C7', title: 'Разлика на квадрати' }, { key: 'C11', title: 'Общ множител' }],
  'Действия с многочлени': [{ key: 'C3', title: 'Събиране и изваждане' }, { key: 'C4', title: 'Многочлен по едночлен' }, { key: 'C5', title: 'Многочлен по многочлен' }],
  'Обобщителни тестове': [{ key: 'D1', title: 'Карта на формулите' }, { key: 'D2', title: 'Типични грешки' }],
};
const GROUP_NOTE: Record<string, string> = {
  'Действия с многочлени': 'Тренировки по всяко действие поотделно — с подсказки.',
  'Обобщителни тестове': 'Смесени задачи от трите теми, без подсказки.',
  'Подготовка за контролно': 'Като истинско контролно: 20 задачи, 40 минути, без подсказки. Реши поне два варианта.',
};
const fmtPct = (p: number) => String(p).replace('.', ',');

/** Tests grouped by topic; each group and each test shows what has already been solved. */
export function TestsPage() {
  const { data, error, loading, reload } = useApi<TestSummary[]>('/api/student/tests');
  const [open, setOpen] = useState<Record<string, boolean>>({});
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const groups = new Map<string, TestSummary[]>();
  for (const t of data) {
    const g = /^([^·]+) · /.exec(t.title)?.[1].trim() ?? 'Още тестове';
    groups.set(g, [...(groups.get(g) ?? []), t]);
  }
  const order = [...groups.keys()].sort((a, b) => (GROUP_ORDER.indexOf(a) + 1 || 99) - (GROUP_ORDER.indexOf(b) + 1 || 99));
  const done = data.filter((t) => t.completedAttempts > 0).length;
  const card = (t: TestSummary) => {
    const solved = t.completedAttempts > 0;
    return (
      <div key={t.id} className="card" style={{ marginBottom: 8, borderLeft: `6px solid ${solved ? 'var(--mastered)' : 'var(--border)'}` }}>
        <div className="row" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 6 }}>
          <span className="chip info">{t.kindLabel}</span>
          {solved
            ? <span className="chip ok">✓ Решен{t.completedAttempts > 1 ? ` ${t.completedAttempts} пъти` : ''}{t.bestPercent != null ? ` · най-добър резултат ${fmtPct(t.bestPercent)}%` : ''}</span>
            : t.inProgressAttemptId ? <span className="chip warn">Започнат</span> : <span className="chip">Нерешен</span>}
        </div>
        <h3 style={{ marginTop: '.5rem' }}>{t.title.replace(/^[^·]+ · /, '')}</h3>
        <p className="small muted">{t.questionCount} въпроса · {t.timeLimitMin} минути{t.hintPolicy === 'ALLOWED' ? ' · с подсказки' : ''}</p>
        <Link className={`btn ${solved ? 'secondary' : ''}`} to={`/tests/${t.id}`}>
          {t.inProgressAttemptId ? 'Продължи' : solved ? 'Реши отново' : 'Започни'}
        </Link>
      </div>
    );
  };
  return (
    <div>
      <h1>Тестове</h1>
      <p className="muted">Тестовете са подредени по теми. Решени: {done} от {data.length}.</p>
      <div className="progress" aria-hidden="true" style={{ marginBottom: '1rem' }}><span style={{ width: `${data.length ? (done / data.length) * 100 : 0}%` }} /></div>
      {order.map((name) => {
        const list = groups.get(name)!;
        const solved = list.filter((t) => t.completedAttempts > 0).length;
        const allDone = solved === list.length;
        const isOpen = open[name] ?? !allDone;
        return (
          <section key={name} style={{ marginBottom: '1rem' }}>
            <button type="button" className="card" aria-expanded={isOpen} onClick={() => setOpen({ ...open, [name]: !isOpen })}
              style={{ width: '100%', textAlign: 'left', cursor: 'pointer', marginBottom: 8, color: 'var(--text)', font: 'inherit' }}>
              <div className="row" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 6 }}>
                <h2 style={{ margin: 0 }}>{isOpen ? '▾' : '▸'} {name}</h2>
                <span className={`chip ${allDone ? 'ok' : ''}`}>{allDone ? '✓ ' : ''}Решени {solved} от {list.length}</span>
              </div>
              <div className="progress" aria-hidden="true" style={{ marginTop: 8 }}><span style={{ width: `${(solved / list.length) * 100}%` }} /></div>
            </button>
            {isOpen && (
              <>
                {GROUP_NOTE[name] && <p className="small muted">{GROUP_NOTE[name]}</p>}
                {GROUP_LESSONS[name] && (
                  <div className="row" style={{ flexWrap: 'wrap', gap: 6, marginBottom: 8 }}>
                    <span className="small muted">Преговори уроците:</span>
                    {GROUP_LESSONS[name].map((l) => <Link key={l.key} className="btn secondary" to={`/lesson/${l.key}`}>{l.key} · {l.title}</Link>)}
                  </div>
                )}
                <div className="cards">{list.map(card)}</div>
              </>
            )}
          </section>
        );
      })}
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
