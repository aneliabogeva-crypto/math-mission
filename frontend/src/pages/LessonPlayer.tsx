import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { uuid, type QuestionView } from '../api';
import { Helper } from '../components/Helper';
import { ErrorNote, Loading } from '../components/Layout';
import { MathText } from '../components/MathText';
import { QuestionCard } from '../components/QuestionCard';
import { send } from '../offline';
import { useApi } from '../session';
import lessonKeys from '../local/data/lesson-keys.json';

interface Step { expression: string; why: string }
interface LessonContent {
  context: { title: string; text: string };
  objectives: string[];
  prerequisiteCheck: { intro: string; questionKeys: string[] };
  explanation: string[];
  definition: { title: string; text: string };
  visual: { kind: string; title: string; description: string; groups?: { label: string; items: string[]; colour: string }[] };
  workedExamples: { level: 'BASIC' | 'TYPICAL' | 'CHALLENGE'; problem: string; steps: Step[]; answer: string }[];
  watchOut: { wrong: string; right: string; why: string }[];
  guidedPractice: { questionKey: string; support: string; coaching?: string }[];
  check: { intro: string; questionKeys: string[] };
  summary: string[];
  nextStep: { lessonKey?: string; text: string };
}
interface LessonView {
  key: string; title: string; path: string; version: number; academicYear: string; content: LessonContent;
  questions: Record<string, QuestionView>; position: number; maxPosition: number; status: string;
}

const SECTIONS = ['Пъзел', 'Цел', 'Подгряване', 'Обяснение', 'Дефиниция', 'Визуализация', 'Решени примери',
  'Внимание!', 'Упражнение с насоки', 'Проверка без оценка', 'Обобщение'];
const LEVEL = { BASIC: 'Основен', TYPICAL: 'Типичен', CHALLENGE: 'Предизвикателен' };
const TILE: Record<string, string> = { purple: 'var(--monomial)', blue: 'var(--rational)', coral: 'var(--polynomial)' };

/** US-STU-04 lesson player; position is autosaved so the student resumes exactly where they stopped. */
export function LessonPlayer() {
  const { key } = useParams();
  const nav = useNavigate();
  const { data, error, loading, reload } = useApi<LessonView>(`/api/student/lessons/${key}`, [key]);
  const [pos, setPos] = useState<number | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => { if (data && pos === null) setPos(Math.min(data.position, SECTIONS.length - 1)); }, [data, pos]);

  function go(next: number, completed = false) {
    setPos(next);
    const id = uuid();
    void send({ id, method: 'PUT', path: `/api/student/lessons/${key}/position`, body: { position: next, completed } });
    requestAnimationFrame(() => headingRef.current?.focus());
    window.scrollTo({ top: 0 });
  }

  if (loading && !data) return <Loading />;
  if (!data || pos === null) return <ErrorNote error={error} onRetry={reload} />;
  const c = data.content;
  const qs = (keys: string[]) => keys.map((k) => data.questions[k]).filter(Boolean);
  const last = SECTIONS.length - 1;

  let body: JSX.Element;
  switch (pos) {
    case 0:
      body = <><h3>{c.context.title}</h3><p>{c.context.text}</p></>;
      break;
    case 1:
      body = <ul>{c.objectives.map((o) => <li key={o}>{o}</li>)}</ul>;
      break;
    case 2:
      body = <><p>{c.prerequisiteCheck.intro}</p>{qs(c.prerequisiteCheck.questionKeys).map((q) => <QuestionCard key={q.key} q={q} lessonKey={data.key} />)}</>;
      break;
    case 3:
      body = <>{c.explanation.map((p, i) => <p key={i}>{p}</p>)}</>;
      break;
    case 4:
      body = <div className="definition"><h3>{c.definition.title}</h3><p>{c.definition.text}</p></div>;
      break;
    case 5:
      body = (
        <figure style={{ margin: 0 }}>
          <figcaption><strong>{c.visual.title}</strong></figcaption>
          <div className="tiles" aria-hidden="true" style={{ marginTop: '.5rem' }}>
            {c.visual.groups?.map((g) => (
              <div key={g.label} className="tile-group pop" style={{ ['--tile' as string]: TILE[g.colour] ?? 'var(--border)' }}>
                <div className="small"><strong>Вид: <span className="math">{g.label}</span></strong></div>
                {g.items.map((t) => <span key={t} className="tile">{t}</span>)}
              </div>
            ))}
          </div>
          <p className="small" style={{ marginTop: '.75rem' }}>{c.visual.description}</p>
        </figure>
      );
      break;
    case 6:
      body = (
        <>
          {c.workedExamples.map((ex, i) => (
            <div key={i} className="card">
              <span className="chip info">{LEVEL[ex.level]}</span>
              <p><strong>Пример {i + 1}:</strong> <MathText>{ex.problem}</MathText></p>
              <div className="table-wrap">
                <table className="steps-table">
                  <thead className="sr-only"><tr><th>Преобразувание</th><th>Защо е вярно</th></tr></thead>
                  <tbody>
                    {ex.steps.map((s, j) => (
                      <tr key={j}><td className="expr"><MathText>{s.expression}</MathText></td><td className="small">{s.why}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p>Отговор: <MathText>{ex.answer}</MathText></p>
            </div>
          ))}
        </>
      );
      break;
    case 7:
      body = (
        <>
          {c.watchOut.map((w, i) => (
            <div key={i} className="card">
              <p><span aria-hidden="true">✗</span> <span className="sr-only">Грешно:</span> <s><MathText>{w.wrong}</MathText></s></p>
              <p><span aria-hidden="true">✓</span> <span className="sr-only">Вярно:</span> <MathText>{w.right}</MathText></p>
              <p className="small muted">{w.why}</p>
            </div>
          ))}
        </>
      );
      break;
    case 8:
      body = (
        <>
          <p className="muted">Подкрепата постепенно намалява — последната задача е изцяло твоя.</p>
          {c.guidedPractice.map((g) => data.questions[g.questionKey] && (
            <QuestionCard key={g.questionKey} q={data.questions[g.questionKey]} lessonKey={data.key}
              coaching={g.support === 'NONE' ? undefined : g.coaching} onSimilar={(k) => nav(`/practice/${k}`)} />
          ))}
        </>
      );
      break;
    case 9:
      body = (
        <>
          <Helper>{c.check.intro} Това не е тест и не носи оценка.</Helper>
          {qs(c.check.questionKeys).map((q) => <QuestionCard key={q.key} q={q} lessonKey={data.key} onSimilar={(k) => nav(`/practice/${k}`)} />)}
        </>
      );
      break;
    default:
      body = (
        <>
          <ul>{c.summary.map((s) => <li key={s}>{s}</li>)}</ul>
          <div className="alert ok"><strong>Следваща стъпка:</strong> {c.nextStep.text}</div>
          <div className="row">
            {c.nextStep.lessonKey && (lessonKeys as string[]).includes(c.nextStep.lessonKey)
              && <Link className="btn" reloadDocument to={`/lesson/${c.nextStep.lessonKey}`}>Към следващия урок</Link>}
            <Link className="btn secondary" to="/map">Към картата</Link>
            <Link className="btn secondary" to="/mistakes">Прегледай грешките си</Link>
          </div>
        </>
      );
  }

  return (
    <article>
      <p className="small muted"><Link to="/map">Карта</Link> / {data.key} · версия {data.version} · {data.academicYear}</p>
      <h1>{data.title}</h1>
      <nav aria-label="Части на урока" className="qnav" style={{ marginBottom: '.75rem' }}>
        {SECTIONS.map((s, i) => (
          <button key={s} type="button" className={`${i <= data.maxPosition || i <= pos ? 'answered' : ''} ${i === pos ? 'current' : ''}`}
            aria-current={i === pos ? 'step' : undefined} aria-label={`${i + 1}. ${s}${i <= Math.max(data.maxPosition, pos) ? ', преминато' : ''}`}
            onClick={() => go(i)}>{i + 1}</button>
        ))}
      </nav>
      <section className="card">
        <h2 ref={headingRef} tabIndex={-1}>{pos + 1}. {SECTIONS[pos]}</h2>
        {body}
      </section>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <button type="button" className="btn secondary" disabled={pos === 0} onClick={() => go(pos - 1)}>Назад</button>
        {pos < last
          ? <button type="button" className="btn" onClick={() => go(pos + 1, pos + 1 === last)}>Напред</button>
          : <Link className="btn" to="/">Готово</Link>}
      </div>
    </article>
  );
}
