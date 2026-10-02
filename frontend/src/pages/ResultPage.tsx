import { Link, useParams } from 'react-router-dom';
import type { AnswerPayload, ResultView } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { MathText, RichText } from '../components/MathText';
import { useApi } from '../session';

function answerText(a?: AnswerPayload): string {
  if (!a) return '—';
  if (a.optionId) return a.optionId;
  if (a.value) return a.value;
  if (a.steps) return a.steps.filter(Boolean).join('  ⟶  ');
  if (a.parts) return Object.entries(a.parts).map(([k, v]) => `${k}: ${v}`).join('; ');
  return a.text ?? '—';
}

const STATUS: Record<string, string> = { CORRECT: '✓ Вярно', PARTIAL: '◐ Частично', INCORRECT: '✗ Невярно', UNANSWERED: '○ Без отговор',
  INVALID_INPUT: 'ⓘ Неразчетен запис', NEEDS_REVIEW: '… Чака учител' };

/** Transparent result: points, percentage, educational grade, skill analysis and item review. */
export function ResultPage() {
  const { id } = useParams();
  const { data, error, loading, reload } = useApi<ResultView>(`/api/student/attempts/${id}/result`, [id]);
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  return (
    <div className="stack">
      <span className="chip info">{data.kindLabel}</span>
      <h1>{data.title}</h1>
      {data.corrections.map((c, i) => <div key={i} className="alert info" role="status"><strong>Корекция:</strong> {c}</div>)}
      <div className="card accent">
        <div className="grid three">
          <div><div className="small muted">Точки</div><div style={{ fontSize: '1.6rem', fontWeight: 800 }}>{fmt(data.points)} / {fmt(data.maxPoints)}</div></div>
          <div><div className="small muted">Процент</div><div style={{ fontSize: '1.6rem', fontWeight: 800 }}>{fmt(data.percent)}%</div></div>
          {data.grade != null && <div><div className="small muted">Учебна оценка</div><div style={{ fontSize: '1.6rem', fontWeight: 800 }}>{data.gradeLabel} ({data.grade})</div></div>}
        </div>
        {data.scoringSource === 'PENDING_TEACHER_REVIEW' && <p className="alert warn small">Някои отговори чакат преглед от учител. Оценката ще се покаже след прегледа.</p>}
        {data.scoringSource === 'TEACHER_REVIEWED' && <p className="small">Част от отговорите са оценени от учител.</p>}
        <p className="small muted">{data.disclaimer}</p>
      </div>

      <div className="card">
        <h2>Анализ по умения</h2>
        {data.skills.map((s) => (
          <div key={s.skill} style={{ marginBottom: '.6rem' }}>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <span>{s.title}</span><span className="small">{fmt(s.points)} / {fmt(s.maxPoints)} т.</span>
            </div>
            <div className="progress" role="progressbar" aria-label={s.title} aria-valuenow={s.percent} aria-valuemin={0} aria-valuemax={100}>
              <span style={{ width: `${s.percent}%`, background: s.percent >= 70 ? 'var(--mastered)' : 'var(--brand)' }} />
            </div>
            {s.percent < 70 && s.lessonKey && <Link className="small" to={`/lesson/${s.lessonKey}`}>Преговори урока →</Link>}
          </div>
        ))}
      </div>

      {data.misconceptions.length > 0 && (
        <div className="card">
          <h2>Какво да упражниш</h2>
          <ul>{data.misconceptions.map((m) => <li key={m.code}>{m.label} ({m.count}) — <Link to={`/lesson/${m.lessonKey}`}>свързан урок</Link></li>)}</ul>
          <Link className="btn secondary" to="/mistakes">Към моите грешки</Link>
        </div>
      )}

      <h2>Преглед на въпросите</h2>
      {!data.answerKeyVisible && <div className="alert info">Верните отговори ще се покажат след крайния срок на заданието.</div>}
      {data.items.map((it) => (
        <details key={it.position} className="card">
          <summary>
            <strong>{it.position + 1}.</strong> {STATUS[it.status ?? 'UNANSWERED']} · {fmt(it.points ?? 0)} / {fmt(it.maxPoints)} т.
          </summary>
          <p><RichText text={it.question.prompt.text} /></p>
          <p>Твоят отговор: <MathText>{answerText(it.answer)}</MathText></p>
          {it.feedback && <p>{it.feedback}</p>}
          {it.misconception && <p className="small">Вид грешка: <span className="chip warn">{it.misconception}</span></p>}
          {it.correctAnswer && <p>Верен отговор: <MathText>{it.correctAnswer}</MathText></p>}
          {it.solution && <p className="small">Решение: {it.solution}</p>}
          {it.teacherComment && <p className="small">Коментар от учителя: {it.teacherComment}</p>}
          <p className="small muted">{it.scoredBy}</p>
          {it.theoryLessonKey && <Link className="small" to={`/lesson/${it.theoryLessonKey}`}>Теория →</Link>}
        </details>
      ))}
    </div>
  );
}

function fmt(n: number | null | undefined): string {
  return n == null ? '—' : String(Math.round(n * 100) / 100).replace('.', ',');
}
