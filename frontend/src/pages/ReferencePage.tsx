import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorNote, Loading } from '../components/Layout';

import { useApi } from '../session';

interface Entry { id: string; path: string; kind: string; title: string; formula: string; spoken: string; meaning: string;
  conditions: string; example: string; commonMistake: string; lessonKey: string; academicYear: string }

/** US-STU-11: searchable reference; formulas carry a spoken reading for screen readers. */
export function ReferencePage() {
  const [q, setQ] = useState('');
  const [path, setPath] = useState('');
  const { data, error, loading, reload } = useApi<Entry[]>(`/api/student/reference?q=${encodeURIComponent(q)}&path=${path}`, [q, path]);
  return (
    <div>
      <h1>Формули и понятия</h1>
      <div className="row">
        <label className="field" style={{ flex: 2 }}><span>Търсене</span><input type="search" value={q} onChange={(e) => setQ(e.target.value)} /></label>
        <label className="field" style={{ flex: 1 }}><span>Тема</span>
          <select value={path} onChange={(e) => setPath(e.target.value)}>
            <option value="">Всички</option><option value="A">Рационални изрази</option><option value="B">Едночлени</option><option value="C">Многочлени</option>
          </select>
        </label>
      </div>
      {loading && !data && <Loading />}
      <ErrorNote error={error} onRetry={reload} />
      {data?.map((e) => (
        <article key={e.id} className="card">
          <h2>{e.title}</h2>
          {e.formula && <div className="math-block" role="math" aria-label={e.spoken}><span aria-hidden="true">{e.formula}</span></div>}
          <p>{e.meaning}</p>
          <p className="small"><strong>Условия:</strong> {e.conditions}</p>
          <p className="small"><strong>Пример:</strong> <span className="math">{e.example}</span></p>
          <p className="small"><strong>Внимание:</strong> {e.commonMistake}</p>
          <p className="small muted">Учебна година {e.academicYear} · <Link to={`/lesson/${e.lessonKey}`}>Урок {e.lessonKey}</Link></p>
        </article>
      ))}
    </div>
  );
}
