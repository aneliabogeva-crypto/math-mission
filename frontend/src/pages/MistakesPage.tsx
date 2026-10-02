import { Link } from 'react-router-dom';
import { Helper } from '../components/Helper';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

interface Group { code: string; label: string; open: number; corrected: number; lessonKey: string; lessonTitle: string; practiceKeys: string[] }

/** US-STU-09: mistakes grouped by misconception; no conclusions about ability. */
export function MistakesPage() {
  const { data, error, loading, reload } = useApi<Group[]>('/api/student/mistakes');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  return (
    <div>
      <h1>Моите грешки</h1>
      <Helper>Грешките показват къде да упражниш, а не какъв си. Всяка поправена грешка носи награда!</Helper>
      {data.length === 0 && <p className="muted" style={{ marginTop: '1rem' }}>Засега няма записани грешки.</p>}
      {data.map((g) => (
        <div key={g.code} className="card" style={{ marginTop: '1rem' }}>
          <h2>{g.label}</h2>
          <p className="small">За поправяне: <strong>{g.open}</strong> · Поправени: <strong>{g.corrected}</strong> {g.corrected > 0 && '★'}</p>
          <div className="row">
            <Link className="btn secondary" to={`/lesson/${g.lessonKey}`}>Теория: {g.lessonTitle}</Link>
            {g.practiceKeys[0] && <Link className="btn" to={`/practice/${g.practiceKeys[0]}`}>Упражни</Link>}
          </div>
        </div>
      ))}
    </div>
  );
}
