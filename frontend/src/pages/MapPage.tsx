import { Link } from 'react-router-dom';
import type { TestSummary } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';
import { shortTitle, topicsFrom, type Topic } from '../topics';

interface Stop { key: string; order: number; title: string; status: string; statusLabel: string; progressPercent: number; available: boolean; recommendation?: string }
interface Zone { path: string; title: string; colour: string; stops: Stop[] }

const ICON: Record<string, string> = { COMPLETED: '✓', IN_PROGRESS: '◐', NOT_STARTED: '○', COMING_SOON: '…' };
const pct = (p: number) => String(p).replace('.', ',');

function TestStop({ t, n }: { t: TestSummary; n: number }) {
  const solved = t.completedAttempts > 0;
  const status = solved ? `✓ Решен${t.bestPercent != null ? ` · най-добър резултат ${pct(t.bestPercent)}%` : ''}` : t.inProgressAttemptId ? '◐ Започнат' : '○ Нерешен';
  return (
    <li className="stop">
      <span className="num" aria-hidden="true">{n}</span>
      <div className="body">
        <div className="title">{shortTitle(t).replace(/^.*— (вариант \d+)$/, 'Контролно — $1')}</div>
        <div className="small">{status}</div>
        <div className="small muted">{t.questionCount} въпроса · {t.timeLimitMin} мин.{t.hintPolicy === 'ALLOWED' ? ' · с подсказки' : ' · без подсказки'}</div>
      </div>
      <Link className="btn secondary" to={`/tests/${t.id}`} aria-label={`${solved ? 'Реши отново' : 'Започни'} ${t.title}`}>
        {t.inProgressAttemptId ? 'Продължи' : solved ? 'Реши отново' : 'Започни'}
      </Link>
    </li>
  );
}

/** A topic section ("тема"): lessons to revise, practice tests and class-test preparation, in that order. */
function TopicZone({ topic, index, lessons }: { topic: Topic; index: number; lessons: Map<string, Stop> }) {
  const all = [...topic.practice, ...topic.exams];
  const solved = all.filter((t) => t.completedAttempts > 0).length;
  let n = 0;
  return (
    <section className="zone zone-T" aria-labelledby={`topic-${topic.prefix}`}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2 id={`topic-${topic.prefix}`} style={{ margin: 0 }}>Тема {index}. {topic.name}</h2>
        <span className={`chip ${solved === all.length ? 'ok' : ''}`}>Решени тестове: {solved} от {all.length}</span>
      </div>
      <div className="progress" aria-hidden="true" style={{ margin: '.6rem 0' }}><span style={{ width: `${all.length ? (solved / all.length) * 100 : 0}%` }} /></div>
      {topic.note && <p className="small muted">{topic.note}</p>}
      {topic.lessons.length > 0 && (
        <>
          <h3>Уроци</h3>
          <ol className="stops">
            {topic.lessons.map((l) => {
              const s = lessons.get(l.key);
              n += 1;
              return (
                <li key={l.key} className="stop">
                  <span className="num" aria-hidden="true">{n}</span>
                  <div className="body">
                    <div className="title">{s?.title ?? l.title}</div>
                    {s && <div className="small"><span aria-hidden="true">{ICON[s.status]}</span> {s.statusLabel}{s.progressPercent > 0 && s.progressPercent < 100 ? ` · ${s.progressPercent}%` : ''}</div>}
                  </div>
                  <Link className="btn secondary" to={`/lesson/${l.key}`}>{s?.status === 'COMPLETED' ? 'Преговор' : 'Отвори'}</Link>
                </li>
              );
            })}
          </ol>
        </>
      )}
      {topic.practice.length > 0 && (<><h3 style={{ marginTop: '1rem' }}>Тренировки</h3><ol className="stops">{topic.practice.map((t) => <TestStop key={t.id} t={t} n={++n} />)}</ol></>)}
      {topic.exams.length > 0 && (<><h3 style={{ marginTop: '1rem' }}>Подготовка за контролно</h3><ol className="stops">{topic.exams.map((t) => <TestStop key={t.id} t={t} n={++n} />)}</ol></>)}
    </section>
  );
}

/** US-STU-03: the curriculum zones (lessons) and the topic sections (lessons + tests); status is shown with icon and text. */
export function MapPage() {
  const { data, error, loading, reload } = useApi<Zone[]>('/api/student/map');
  const tests = useApi<TestSummary[]>('/api/student/tests');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const lessons = new Map(data.flatMap((z) => z.stops).map((s) => [s.key, s]));
  const topics = topicsFrom(tests.data ?? []);
  return (
    <div>
      <h1>Карта на мисиите</h1>
      <p className="muted">Всеки урок е спирка. Можеш да разгледаш и следващите — картата ще ти подскаже какво е добре да минеш първо.</p>
      {topics.length > 0 && (
        <nav className="row" aria-label="Към тема" style={{ marginBottom: '1rem' }}>
          <a className="btn secondary" href="#zone-A">Уроци по програмата</a>
          {topics.map((t, i) => <a key={t.prefix} className="btn secondary" href={`#topic-${t.prefix}`}>Тема {i + 1}: {t.name}</a>)}
        </nav>
      )}
      {data.map((z) => (
        <section key={z.path} className={`zone zone-${z.path}`} aria-labelledby={`zone-${z.path}`}>
          <h2 id={`zone-${z.path}`}>{z.path}. {z.title}</h2>
          <ol className="stops">
            {z.stops.map((s) => (
              <li key={s.key} className="stop">
                <span className="num" aria-hidden="true">{s.order}</span>
                <div className="body">
                  <div className="title">{s.title}</div>
                  <div className="small"><span aria-hidden="true">{ICON[s.status]}</span> {s.statusLabel}{s.progressPercent > 0 && s.progressPercent < 100 ? ` · ${s.progressPercent}%` : ''}</div>
                  {s.recommendation && s.available && s.status !== 'COMPLETED' && <div className="small muted">{s.recommendation}</div>}
                </div>
                {s.available
                  ? <Link className="btn secondary" to={`/lesson/${s.key}`} aria-label={`Отвори урок ${s.title}`}>{s.status === 'COMPLETED' ? 'Преговор' : 'Отвори'}</Link>
                  : <span className="chip">Скоро</span>}
              </li>
            ))}
          </ol>
        </section>
      ))}
      {topics.length > 0 && <h2 style={{ marginTop: '1.5rem' }}>Теми за упражнение и контролно</h2>}
      {topics.map((t, i) => <TopicZone key={t.prefix} topic={t} index={i + 1} lessons={lessons} />)}
    </div>
  );
}
