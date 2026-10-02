import { Link } from 'react-router-dom';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

interface Day { date: string; tasks: { kind: string; title: string; lessonKey?: string; minutes: number; reason: string }[] }

/** US-STU-10: bounded seven-day plan; short sessions; prerequisite review after repeated errors. */
export function PlanPage() {
  const { data, error, loading, reload } = useApi<Day[]>('/api/student/plan');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  return (
    <div>
      <h1>План за 7 дни</h1>
      <p className="muted">Кратки сесии — около 10–15 минути на ден. Пропуснат ден не е проблем.</p>
      {data.map((d) => (
        <div key={d.date} className="card">
          <h2>{new Date(d.date).toLocaleDateString('bg-BG', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Europe/Sofia' })}</h2>
          <ul className="stops">
            {d.tasks.map((t, i) => (
              <li key={i} className="stop">
                <div className="body"><div className="title">{t.title}</div><div className="small muted">{t.minutes} мин. · {t.reason}</div></div>
                {t.lessonKey && <Link className="btn secondary" to={`/lesson/${t.lessonKey}`}>Отвори</Link>}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
