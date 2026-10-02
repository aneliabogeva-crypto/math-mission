import { useNavigate, useParams } from 'react-router-dom';
import type { QuestionView } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { QuestionCard } from '../components/QuestionCard';
import { useApi } from '../session';

/** A single "similar question" follow-up. */
export function PracticePage() {
  const { key } = useParams();
  const nav = useNavigate();
  const { data, error, loading, reload } = useApi<QuestionView>(`/api/student/practice/${key}`, [key]);
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  return (
    <div>
      <h1>Подобна задача</h1>
      <QuestionCard key={data.key} q={data} onSimilar={(k) => nav(`/practice/${k}`)} />
    </div>
  );
}
