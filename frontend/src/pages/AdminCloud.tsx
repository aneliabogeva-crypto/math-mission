import { useEffect, useState, type CSSProperties } from 'react';
import { Link, NavLink, useParams } from 'react-router-dom';
import contentJson from '../local/data/content.json';
import curriculumJson from '../local/data/curriculum.json';
import lessonKeys from '../local/data/lesson-keys.json';
import { TOPICS } from '../topics';
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
    <nav className="row admin-nav" aria-label="Администрация" style={{ marginBottom: '1rem' }}>
      <NavLink className="btn secondary" to="/admin/children">Деца</NavLink>
      <NavLink className="btn secondary" to="/admin/tests">Резултати по тестове</NavLink>
      <NavLink className="btn secondary" to="/admin/content">Съдържание</NavLink>
      <Link className="btn ghost" to="/">← Към приложението</Link>
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


// ------------------------------------------------------------------ published content (lessons and tests with answer keys)

interface CQ { key: string; draft: { skill: string; responseType: string; maxPoints: number; difficulty: string; hints: string[];
  prompt: { text: string; options?: { id: string; text: string }[]; parts?: { id: string; label: string }[]; startExpression?: string };
  key: { correctOptionId?: string; answer?: string; parts?: { id: string; answer: string }[]; solution: string } } }
interface CT { testKey: string; title: string; kind: string; timeLimitMin: number; hintPolicy: string; questionKeys: string[] }
const CONTENT = contentJson as unknown as { questions: CQ[]; tests: CT[] };
const QMAP = new Map(CONTENT.questions.map((q) => [q.key, q]));
const TMAP = new Map(CONTENT.tests.map((t) => [t.testKey, t]));
const LTITLE = new Map((curriculumJson as { lessons: { key: string; title: string }[] }).lessons.map((l) => [l.key, l.title]));
const PUBLISHED = new Set(lessonKeys as string[]);
const RT: Record<string, string> = { SINGLE_CHOICE: 'избор', NUMERIC: 'число', EXPRESSION: 'израз', STEPS: 'стъпки', STRUCTURED: 'с подточки', FREE_TEXT: 'свободен текст' };
const pretty = (s: string) => s.replace(/\^(\d)/g, (_, d: string) => '⁰¹²³⁴⁵⁶⁷⁸⁹'[Number(d)]).replace(/\*/g, '·').replace(/-/g, ' − ').replace(/\+/g, ' + ').replace(/\( − /g, '(−');

function TestLine({ t }: { t: CT }) {
  return (
    <li className="test-row">
      <div className="body">
        <div className="title">{t.title.replace(/^[^·]+ · /, '')}</div>
        <div className="small muted">{t.questionKeys.length} въпроса · {t.timeLimitMin} мин. · {t.hintPolicy === 'ALLOWED' ? 'с подсказки' : 'без подсказки'}</div>
      </div>
      <Link className="btn secondary" to={`/admin/content/${t.testKey}`}>Въпроси и отговори</Link>
    </li>
  );
}

/** Everything that is published: topics, lessons and tests, in the order the learners see them. */
export function AdminContent() {
  const shown = new Set<string>();
  const total = CONTENT.tests.length;
  return (
    <div>
      <h1>Съдържание</h1>
      <AdminNav />
      <p className="muted">Публикувани: {PUBLISHED.size} урока · {total} теста · {CONTENT.questions.length} задачи. Уроците се отварят както ги виждат децата; при тестовете виждаш всички въпроси с верните отговори и решенията.</p>
      {TOPICS.map((topic, i) => {
        const lessonTests = (k: string) => (topic.lessonTests[k] ?? []).map((key) => TMAP.get(key)).filter((t): t is CT => Boolean(t));
        const summary = [...topic.summaryTests, ...topic.examTests].map((key) => TMAP.get(key)).filter((t): t is CT => Boolean(t));
        [...topic.lessons.flatMap((k) => topic.lessonTests[k] ?? []), ...topic.summaryTests, ...topic.examTests].forEach((k) => shown.add(k));
        return (
          <section key={topic.id} className="zone topic" style={{ '--zone': `var(${topic.colour})` } as CSSProperties}>
            <div className="topic-head"><span className="topic-num">{i + 1}</span><span className="topic-name"><h2>Тема {i + 1}. {topic.name}</h2>
              <span className="small muted">{topic.lessons.length} урока · {topic.lessons.reduce((n, k) => n + lessonTests(k).length, 0) + summary.length} теста</span></span></div>
            <ol className="stops">
              {topic.lessons.map((k, j) => (
                <li key={k} className="lesson-item">
                  <div className="stop">
                    <span className="num" aria-hidden="true">{j + 1}</span>
                    <div className="body"><div className="title">{LTITLE.get(k) ?? k}</div><div className="small muted">Урок {k}{PUBLISHED.has(k) ? '' : ' · не е публикуван'}</div></div>
                    {PUBLISHED.has(k) && <Link className="btn secondary" to={`/lesson/${k}`}>Отвори урока</Link>}
                  </div>
                  {lessonTests(k).length > 0 && <div className="lesson-tests"><ul className="test-list">{lessonTests(k).map((t) => <TestLine key={t.testKey} t={t} />)}</ul></div>}
                </li>
              ))}
            </ol>
            {summary.length > 0 && <div className="topic-summary"><h3>Обобщаващи тестове и контролно</h3><ul className="test-list">{summary.map((t) => <TestLine key={t.testKey} t={t} />)}</ul></div>}
          </section>
        );
      })}
      {CONTENT.tests.some((t) => !shown.has(t.testKey)) && (
        <section className="zone"><h2>Други тестове</h2><ul className="test-list">{CONTENT.tests.filter((t) => !shown.has(t.testKey)).map((t) => <TestLine key={t.testKey} t={t} />)}</ul></section>
      )}
    </div>
  );
}

/** One published test: every question with its answer key, hints and full solution. */
export function AdminContentTest() {
  const { key } = useParams();
  const t = TMAP.get(key ?? '');
  if (!t) return <ErrorNote error={{ message: 'Тестът не е намерен.' }} />;
  return (
    <div className="stack">
      <AdminNav />
      <Link to="/admin/content">← Съдържание</Link>
      <h1>{t.title}</h1>
      <p className="muted">{t.questionKeys.length} въпроса · {t.timeLimitMin} минути · {t.hintPolicy === 'ALLOWED' ? 'с подсказки' : 'без подсказки'} · <Link to={`/tests/${t.testKey}`}>Реши го като ученик</Link></p>
      <ol className="admin-items">
        {t.questionKeys.map((k, i) => {
          const q = QMAP.get(k);
          if (!q) return null;
          const d = q.draft;
          return (
            <li key={k} className="card admin-item is-ok">
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <strong>Въпрос {i + 1}</strong>
                <span className="small muted">{RT[d.responseType] ?? d.responseType} · {d.maxPoints} т.</span>
              </div>
              <p><RichText text={d.prompt.text} /></p>
              {d.prompt.startExpression && <p className="small">Начален израз: <span className="math">{pretty(d.prompt.startExpression)}</span></p>}
              {d.prompt.options && (
                <ul className="options-key">
                  {d.prompt.options.map((o) => (
                    <li key={o.id} className={o.id === d.key.correctOptionId ? 'correct' : ''}>
                      {o.id}) <MathText>{o.text}</MathText>{o.id === d.key.correctOptionId && <strong> ✓ верен</strong>}
                    </li>
                  ))}
                </ul>
              )}
              {!d.prompt.options && d.key.answer && <p>Верен отговор: <strong className="math">{pretty(d.key.answer)}</strong></p>}
              {d.key.parts && d.prompt.parts && (
                <ul className="small">{d.prompt.parts.map((p) => <li key={p.id}>{p.label}: <strong className="math">{pretty(d.key.parts!.find((x) => x.id === p.id)?.answer ?? '')}</strong></li>)}</ul>
              )}
              <div className="alert ok small"><strong>Решение:</strong> <RichText text={d.key.solution} /></div>
              <details className="small"><summary>Подсказки</summary><ol>{d.hints.map((h) => <li key={h}><RichText text={h} /></li>)}</ol></details>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
