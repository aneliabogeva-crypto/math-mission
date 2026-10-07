import { Link } from 'react-router-dom';
import type { Feedback } from '../api';
import { MathText } from './MathText';

/** Specific, respectful, actionable feedback: why the answer is wrong, the right answer and how to get it. */
export function FeedbackPanel({ fb, onSimilar }: { fb: Feedback; onSimilar?: (key: string) => void }) {
  const tone = fb.correct ? 'ok' : fb.status === 'PARTIAL' ? 'warn' : fb.status === 'INVALID_INPUT' ? 'info' : 'bad';
  const title = fb.correct ? '✓ Вярно' : fb.status === 'PARTIAL' ? '◐ Частично вярно' : fb.status === 'INVALID_INPUT' ? 'ⓘ Провери записа' : '✗ Не е вярно';
  return (
    <div className={`alert ${tone} pop`} role="status" aria-live="polite">
      <strong>{title}</strong>
      {fb.correct ? <p>{fb.message}</p> : (
        <div className="stack" style={{ marginTop: '.5rem' }}>
          <div>
            <div className="small" style={{ fontWeight: 700 }}>{fb.status === 'INVALID_INPUT' ? 'Какво да поправиш' : 'Защо не е вярно'}</div>
            <p style={{ margin: 0 }}>{fb.message}</p>
            {fb.misconceptionLabel && <p className="small" style={{ margin: '.25rem 0 0' }}>Вид грешка: <span className="chip warn">{fb.misconceptionLabel}</span></p>}
            {fb.errorStep && <p className="small" style={{ margin: '.25rem 0 0' }}>Грешката е на ред {fb.errorStep}.</p>}
          </div>
          {fb.parts?.length ? (
            <ul className="small" style={{ margin: 0 }}>{fb.parts.map((p) => <li key={p.id}>{p.id}) {p.status === 'CORRECT' ? '✓' : '✗'} {p.message}</li>)}</ul>
          ) : null}
          {fb.correctAnswer && (
            <div>
              <div className="small" style={{ fontWeight: 700 }}>Верен отговор</div>
              <p style={{ margin: 0 }}><MathText>{fb.correctAnswer}</MathText></p>
            </div>
          )}
          {fb.solution && (
            <div>
              <div className="small" style={{ fontWeight: 700 }}>Как се решава</div>
              <p style={{ margin: 0 }}>{fb.solution}</p>
            </div>
          )}
        </div>
      )}
      {fb.correct && fb.solution && (
        <details><summary>Виж решението</summary><p>{fb.solution}</p></details>
      )}
      <div className="row" style={{ marginTop: '.5rem' }}>
        {fb.theoryLessonKey && <Link className="btn secondary" to={`/lesson/${fb.theoryLessonKey}`}>Теория: {fb.theoryLessonTitle}</Link>}
        {fb.similarQuestionKey && onSimilar && (
          <button type="button" className="btn" onClick={() => onSimilar(fb.similarQuestionKey!)}>Опитай подобна задача</button>
        )}
      </div>
    </div>
  );
}
