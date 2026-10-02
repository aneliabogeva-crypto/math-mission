import { Link } from 'react-router-dom';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

interface Stop { key: string; order: number; title: string; status: string; statusLabel: string; progressPercent: number; available: boolean; recommendation?: string }
interface Zone { path: string; title: string; colour: string; stops: Stop[] }

const ICON: Record<string, string> = { COMPLETED: '✓', IN_PROGRESS: '◐', NOT_STARTED: '○', COMING_SOON: '…' };

/** US-STU-03: three visual zones (plus revision); status is shown with icon and text, not colour alone. */
export function MapPage() {
  const { data, error, loading, reload } = useApi<Zone[]>('/api/student/map');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  return (
    <div>
      <h1>Карта на мисиите</h1>
      <p className="muted">Всеки урок е спирка. Можеш да разгледаш и следващите — картата ще ти подскаже какво е добре да минеш първо.</p>
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
    </div>
  );
}
