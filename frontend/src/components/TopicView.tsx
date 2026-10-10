import { useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import type { TestSummary } from '../api';
import { testLabel, type Placed, type TopicDef } from '../topics';

export interface LessonStop { key: string; title: string; status: string; statusLabel: string; progressPercent: number; available: boolean; recommendation?: string }

const ICON: Record<string, string> = { COMPLETED: '✓', IN_PROGRESS: '◐', NOT_STARTED: '○', COMING_SOON: '…' };
const pct = (p: number) => String(p).replace('.', ',');

export function TestRow({ t, topic }: { t: TestSummary; topic?: TopicDef }) {
  const solved = t.completedAttempts > 0;
  return (
    <li className={`test-row${solved ? ' solved' : ''}`}>
      <div className="body">
        <div className="title">{testLabel(t, topic)}</div>
        <div className="small">
          {solved ? <span className="chip ok">✓ Решен{t.bestPercent != null ? ` · най-добър ${pct(t.bestPercent)}%` : ''}</span>
            : t.inProgressAttemptId ? <span className="chip warn">◐ Започнат</span> : <span className="chip">○ Нерешен</span>}
          <span className="muted"> {t.questionCount} въпроса · {t.timeLimitMin} мин.{t.hintPolicy === 'ALLOWED' ? ' · с подсказки' : ' · без подсказки'}</span>
        </div>
      </div>
      <Link className={`btn ${solved ? 'secondary' : ''}`} to={`/tests/${t.id}`} aria-label={`${solved ? 'Реши отново' : 'Започни'}: ${t.title}`}>
        {t.inProgressAttemptId ? 'Продължи' : solved ? 'Реши отново' : 'Започни'}
      </Link>
    </li>
  );
}

/** One topic: coloured header, its lessons in order (each with its own tests) and the topic summary at the end. */
export function TopicView({ topic, index, placed, lessons, titles, testsOnly = false }: {
  topic: TopicDef; index: number; placed: Placed; lessons?: Map<string, LessonStop>; titles: Map<string, string>; testsOnly?: boolean;
}) {
  const summary = placed.summary.get(topic.id) ?? [];
  const exams = placed.exams.get(topic.id) ?? [];
  const lessonTests = topic.lessons.flatMap((k) => placed.byLesson.get(k) ?? []);
  const allTests = [...lessonTests, ...summary, ...exams];
  const solvedTests = allTests.filter((t) => t.completedAttempts > 0).length;
  const doneLessons = topic.lessons.filter((k) => lessons?.get(k)?.status === 'COMPLETED').length;
  const finished = (!lessons || doneLessons === topic.lessons.length) && solvedTests === allTests.length;
  const [open, setOpen] = useState(!finished);
  const shown = testsOnly ? topic.lessons.filter((k) => (placed.byLesson.get(k) ?? []).length > 0) : topic.lessons;
  const style = { '--zone': `var(${topic.colour})` } as CSSProperties;
  if (testsOnly && allTests.length === 0) return null;

  return (
    <section className="zone topic" style={style} aria-labelledby={`topic-${topic.id}`}>
      <div className="topic-head" onClick={() => setOpen(!open)}>
        <span className="topic-num" aria-hidden="true">{index}</span>
        <span className="topic-name">
          <h2 id={`topic-${topic.id}`}>Тема {index}. {topic.name}</h2>
          <span className="small muted">
            {lessons ? `Уроци: ${doneLessons} от ${topic.lessons.length}` : `${topic.lessons.length} урока`}
            {allTests.length > 0 && ` · Решени тестове: ${solvedTests} от ${allTests.length}`}
          </span>
        </span>
        <button type="button" className="btn ghost topic-toggle" aria-expanded={open} aria-controls={`topic-body-${topic.id}`}
          aria-label={`${open ? 'Скрий' : 'Покажи'} тема ${index}`} onClick={(e) => { e.stopPropagation(); setOpen(!open); }}>{open ? '▾' : '▸'}</button>
      </div>
      {open && (
        <div id={`topic-body-${topic.id}`}>
          <ol className="stops">
            {shown.map((k, i) => {
              const s = lessons?.get(k);
              const tests = placed.byLesson.get(k) ?? [];
              return (
                <li key={k} className="lesson-item">
                  <div className="stop">
                    <span className="num" aria-hidden="true">{topic.lessons.indexOf(k) + 1}</span>
                    <div className="body">
                      <div className="title">{s?.title ?? titles.get(k) ?? k}</div>
                      {s && <div className="small"><span aria-hidden="true">{ICON[s.status]}</span> {s.statusLabel}{s.progressPercent > 0 && s.progressPercent < 100 ? ` · ${s.progressPercent}%` : ''}</div>}
                      {s?.recommendation && s.available && s.status !== 'COMPLETED' && i === 0 && <div className="small muted">{s.recommendation}</div>}
                    </div>
                    {(!s || s.available)
                      ? <Link className="btn secondary" to={`/lesson/${k}`}>{s?.status === 'COMPLETED' ? 'Преговор' : 'Урок'}</Link>
                      : <span className="chip">Скоро</span>}
                  </div>
                  {tests.length > 0 && (
                    <div className="lesson-tests">
                      <div className="small muted">Тестове към урока</div>
                      <ul className="test-list">{tests.map((t) => <TestRow key={t.id} t={t} topic={topic} />)}</ul>
                    </div>
                  )}
                </li>
              );
            })}
          </ol>

          <div className="topic-summary">
            <h3>{testsOnly ? 'Обобщаващи тестове на темата' : 'Обобщение на темата'}</h3>
            {!testsOnly && <ul className="key-points">{topic.keyPoints.map((p) => <li key={p}>{p}</li>)}</ul>}
            {!testsOnly && topic.formulas && <div className="formula-row">{topic.formulas.map((f) => <span key={f} className="formula">{f}</span>)}</div>}
            {summary.length > 0 && (<>{!testsOnly && <h4>Обобщаващи тестове</h4>}<ul className="test-list">{summary.map((t) => <TestRow key={t.id} t={t} topic={topic} />)}</ul></>)}
            {exams.length > 0 && (<><h4>Подготовка за контролно</h4><p className="small muted">Като истинско контролно: 20 задачи, 40 минути, без подсказки.</p>
              <ul className="test-list">{exams.map((t) => <TestRow key={t.id} t={t} topic={topic} />)}</ul></>)}
            {summary.length + exams.length === 0 && <p className="small muted">Обобщаващите тестове за тази тема се подготвят.</p>}
          </div>
        </div>
      )}
    </section>
  );
}
