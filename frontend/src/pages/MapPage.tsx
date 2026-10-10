import type { TestSummary } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { TestRow, TopicView, type LessonStop } from '../components/TopicView';
import { useApi } from '../session';
import { TOPICS, placeTests } from '../topics';

interface Zone { path: string; title: string; colour: string; stops: (LessonStop & { order: number })[] }

/** US-STU-03: the course by topics — each topic in its own colour, lessons with their tests, and a summary at the end. */
export function MapPage() {
  const { data, error, loading, reload } = useApi<Zone[]>('/api/student/map');
  const tests = useApi<TestSummary[]>('/api/student/tests');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const lessons = new Map(data.flatMap((z) => z.stops).map((s) => [s.key, s]));
  const titles = new Map([...lessons.values()].map((s) => [s.key, s.title]));
  const placed = placeTests(tests.data ?? []);
  return (
    <div>
      <h1>Карта на мисиите</h1>
      <p className="muted">Темите са подредени по реда на учене. Във всяка тема минаваш уроците един след друг, решаваш тестовете към тях, а накрая — обобщението и обобщаващите тестове.</p>
      <nav className="topic-jump" aria-label="Към тема">
        {TOPICS.map((t, i) => <a key={t.id} href={`#topic-${t.id}`} style={{ borderColor: `var(${t.colour})` }}>{i + 1}. {t.name}</a>)}
      </nav>
      {TOPICS.map((t, i) => <TopicView key={t.id} topic={t} index={i + 1} placed={placed} lessons={lessons} titles={titles} />)}
      {placed.other.length > 0 && (
        <section className="zone"><h2>Още тестове</h2><ul className="test-list">{placed.other.map((t) => <TestRow key={t.id} t={t} />)}</ul></section>
      )}
    </div>
  );
}
