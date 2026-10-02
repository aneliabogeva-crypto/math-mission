import { useState } from 'react';
import { api, ApiError, uuid, type AnswerPayload, type Feedback, type QuestionView } from '../api';
import { send } from '../offline';
import { AnswerInput, isAnswered } from './AnswerInput';
import { FeedbackPanel } from './FeedbackPanel';
import { RichText } from './MathText';

/**
 * Ungraded practice item with graduated hints (US-STU-06) and explanatory feedback (US-STU-07).
 * Offline answers are queued with a fixed requestId and checked once the connection returns.
 */
export function QuestionCard({ q, lessonKey, coaching, onSimilar, onDone }: {
  q: QuestionView; lessonKey?: string; coaching?: string; onSimilar?: (key: string) => void; onDone?: (correct: boolean) => void;
}) {
  const [answer, setAnswer] = useState<AnswerPayload>({});
  const [hints, setHints] = useState<string[]>([]);
  const [fb, setFb] = useState<Feedback | null>(null);
  const [queued, setQueued] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function hint() {
    const level = hints.length + 1;
    try {
      const r = await api<{ text: string }>(`/api/student/practice/${q.key}/hints/${level}`);
      setHints([...hints, r.text]);
    } catch (e) { setError((e as ApiError).message); }
  }

  async function check() {
    setBusy(true);
    setError(null);
    const requestId = uuid();
    try {
      const r = await send<Feedback>({
        id: requestId, method: 'POST', path: `/api/student/practice/${q.key}/check`,
        body: { requestId, answer, hintsUsed: hints.length, lessonKey },
      });
      if (r.queued) setQueued(true);
      else if (r.result) { setFb(r.result); setQueued(false); onDone?.(r.result.correct); }
    } catch (e) { setError((e as ApiError).message); } finally { setBusy(false); }
  }

  function retry() {
    setFb(null);
    setAnswer({});
    setHints([]);
  }

  const hintLabels = ['Подсказка 1: спомни си', 'Подсказка 2: метод', 'Подсказка 3: ключова стъпка'];

  return (
    <div className="card" aria-labelledby={`q-${q.key}`}>
      <p className="small muted">{q.skillTitle}</p>
      <h3 id={`q-${q.key}`}><RichText text={q.prompt.text} /></h3>
      {coaching && <p className="small" style={{ color: 'var(--monomial)' }}>💡 {coaching}</p>}
      <AnswerInput question={q} value={answer} onChange={setAnswer} disabled={Boolean(fb) || queued} />
      {hints.map((h, i) => <div key={i} className="alert info small"><strong>{hintLabels[i]}:</strong> {h}</div>)}
      {error && <div className="alert warn" role="alert">{error}</div>}
      {queued && <div className="alert warn" role="status">Отговорът е запазен на устройството и ще бъде проверен, когато се появи връзка.</div>}
      {!fb && !queued && (
        <div className="row">
          <button type="button" className="btn" disabled={!isAnswered(answer) || busy} onClick={() => void check()}>Провери</button>
          {hints.length < q.hintLevels && (
            <button type="button" className="btn secondary" onClick={() => void hint()}>Подсказка ({hints.length + 1}/{q.hintLevels})</button>
          )}
        </div>
      )}
      {fb && (
        <>
          <FeedbackPanel fb={fb} onSimilar={onSimilar} />
          {!fb.correct && <button type="button" className="btn ghost" onClick={retry}>Опитай отново</button>}
        </>
      )}
    </div>
  );
}
