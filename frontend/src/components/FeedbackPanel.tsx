import { Link } from 'react-router-dom';
import type { Feedback } from '../api';
import { MathText } from './MathText';

/** Specific, respectful, actionable feedback: never just "wrong". */
export function FeedbackPanel({ fb, onSimilar }: { fb: Feedback; onSimilar?: (key: string) => void }) {
  const tone = fb.correct ? 'ok' : fb.status === 'PARTIAL' ? 'warn' : fb.status === 'INVALID_INPUT' ? 'info' : 'bad';
  const title = fb.correct ? '✓ Вярно' : fb.status === 'PARTIAL' ? '◐ Частично вярно' : fb.status === 'INVALID_INPUT' ? 'ⓘ Провери записа' : '✗ Още не';
  return (
    <div className={`alert ${tone} pop`} role="status" aria-live="polite">
      <strong>{title}</strong>
      <p>{fb.message}</p>
      {fb.misconceptionLabel && !fb.correct && <p className="small">Вид грешка: <span className="chip warn">{fb.misconceptionLabel}</span></p>}
      {fb.errorStep && <p className="small">Провери ред {fb.errorStep}.</p>}
      {fb.parts?.length ? (
        <ul className="small">{fb.parts.map((p) => <li key={p.id}>{p.id}) {p.status === 'CORRECT' ? '✓' : '✗'} {p.message}</li>)}</ul>
      ) : null}
      {fb.solution && (
        <details open={!fb.correct}>
          <summary>{fb.correct ? 'Виж решението' : 'Верен начин'}</summary>
          {fb.correctAnswer && <p>Отговор: <MathText>{fb.correctAnswer}</MathText></p>}
          <p>{fb.solution}</p>
        </details>
      )}
      <div className="row">
        {fb.theoryLessonKey && <Link className="btn secondary" to={`/lesson/${fb.theoryLessonKey}`}>Теория: {fb.theoryLessonTitle}</Link>}
        {fb.similarQuestionKey && onSimilar && (
          <button type="button" className="btn" onClick={() => onSimilar(fb.similarQuestionKey!)}>Опитай подобна задача</button>
        )}
      </div>
    </div>
  );
}
