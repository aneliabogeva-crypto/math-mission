import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, ApiError, uuid, type AnswerPayload, type AttemptView, type ResultView } from '../api';
import { AnswerInput, isAnswered } from '../components/AnswerInput';
import { ErrorNote, Loading } from '../components/Layout';
import { RichText } from '../components/MathText';
import { flush, pendingFor, send } from '../offline';
import { useApi } from '../session';

/** FR-05 question player: autosave after each answer, mark for review, scratch area, resume and offline recovery. */
export function AttemptPlayer() {
  const { id } = useParams();
  const nav = useNavigate();
  const { data, error, loading, reload } = useApi<AttemptView>(`/api/student/attempts/${id}`, [id]);
  const [answers, setAnswers] = useState<Record<number, AnswerPayload>>({});
  const [marked, setMarked] = useState<Record<number, boolean>>({});
  const [hints, setHints] = useState<Record<number, string[]>>({});
  const [pos, setPos] = useState(0);
  const [saveState, setSaveState] = useState<Record<number, 'saved' | 'queued' | 'saving'>>({});
  const [review, setReview] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const offset = useRef(0);
  const timers = useRef<Record<number, number>>({});
  const scratchKey = `mm.scratch.${id}`;
  const [scratch, setScratch] = useState(() => { try { return localStorage.getItem(scratchKey) ?? ''; } catch { return ''; } });

  useEffect(() => {
    if (!data) return;
    if (data.status === 'SUBMITTED') { nav(`/result/${data.attemptId}`, { replace: true }); return; }
    offset.current = new Date(data.serverNow).getTime() - Date.now();
    const a: Record<number, AnswerPayload> = {};
    const m: Record<number, boolean> = {};
    const h: Record<number, string[]> = {};
    const s: Record<number, 'saved' | 'queued'> = {};
    data.items.forEach((it) => {
      if (it.answer) { a[it.position] = it.answer; s[it.position] = 'saved'; }
      m[it.position] = it.markedForReview;
      h[it.position] = it.revealedHints;
    });
    // Answers confirmed on this device but not yet synchronised take precedence over the server copy.
    pendingFor(`/api/student/attempts/${id}/items/`).forEach((e) => {
      const p = Number(e.path.split('/').pop());
      const body = e.body as { answer: AnswerPayload; markedForReview: boolean };
      a[p] = body.answer; m[p] = body.markedForReview; s[p] = 'queued';
    });
    setAnswers(a); setMarked(m); setHints(h); setSaveState(s);
    setPos(Math.min(data.lastPosition, data.items.length - 1));
  }, [data, id, nav]);

  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, []);
  useEffect(() => { try { localStorage.setItem(scratchKey, scratch); } catch { /* ignore */ } }, [scratch, scratchKey]);

  const save = useCallback(async (position: number, answer: AnswerPayload, isMarked: boolean) => {
    const requestId = uuid();
    setSaveState((s) => ({ ...s, [position]: 'saving' }));
    try {
      const r = await send({ id: requestId, method: 'PUT', path: `/api/student/attempts/${id}/items/${position}`,
        body: { requestId, answer, markedForReview: isMarked, lastPosition: position } });
      setSaveState((s) => ({ ...s, [position]: r.queued ? 'queued' : 'saved' }));
    } catch (e) {
      const err = e as ApiError;
      setSubmitError(err.message);
      if (err.code === 'ATTEMPT_SUBMITTED') nav(`/result/${id}`);
    }
  }, [id, nav]);

  function change(position: number, answer: AnswerPayload) {
    setAnswers((a) => ({ ...a, [position]: answer }));
    window.clearTimeout(timers.current[position]);
    timers.current[position] = window.setTimeout(() => void save(position, answer, marked[position] ?? false), 600);
  }

  function toggleMark() {
    const next = !marked[pos];
    setMarked((m) => ({ ...m, [pos]: next }));
    void save(pos, answers[pos] ?? {}, next);
  }

  async function hint() {
    const level = (hints[pos]?.length ?? 0) + 1;
    try {
      const r = await api<{ text: string }>(`/api/student/attempts/${id}/items/${pos}/hints/${level}`, { method: 'POST' });
      setHints((h) => ({ ...h, [pos]: [...(h[pos] ?? []), r.text] }));
    } catch (e) { setSubmitError((e as ApiError).message); }
  }

  async function submit() {
    setSubmitError(null);
    Object.values(timers.current).forEach((t) => window.clearTimeout(t));
    await Promise.all(Object.entries(answers).map(([p, a]) => saveState[Number(p)] === 'saved' ? null : save(Number(p), a, marked[Number(p)] ?? false)));
    await flush();
    if (pendingFor(`/api/student/attempts/${id}/`).length > 0) {
      setSubmitError('Има отговори, които още не са изпратени. Провери връзката и опитай отново — нищо няма да се загуби.');
      return;
    }
    try {
      const r = await api<ResultView>(`/api/student/attempts/${id}/submit`, { method: 'POST' });
      try { localStorage.removeItem(scratchKey); } catch { /* ignore */ }
      nav(`/result/${r.attemptId}`);
    } catch (e) { setSubmitError((e as ApiError).message); }
  }

  const remaining = useMemo(() => {
    if (!data?.deadlineAt) return null;
    return Math.max(0, Math.floor((new Date(data.deadlineAt).getTime() - (now + offset.current)) / 1000));
  }, [data, now]);

  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const item = data.items[pos];
  const total = data.items.length;
  const answeredCount = data.items.filter((it) => isAnswered(answers[it.position])).length;
  const timeText = remaining == null ? '' : `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}`;

  if (review) {
    const unanswered = data.items.filter((it) => !isAnswered(answers[it.position]));
    const flagged = data.items.filter((it) => marked[it.position]);
    return (
      <div className="stack">
        <h1>Преди предаване</h1>
        <p>Отговорени: {answeredCount} от {total}.</p>
        {unanswered.length > 0 && <div className="alert warn">Без отговор: {unanswered.map((u) => u.position + 1).join(', ')}</div>}
        {flagged.length > 0 && <div className="alert info">Маркирани за преглед: {flagged.map((u) => u.position + 1).join(', ')}</div>}
        {submitError && <div className="alert bad" role="alert">{submitError}</div>}
        <div className="row">
          <button className="btn secondary" onClick={() => setReview(false)}>Върни се към въпросите</button>
          <button className="btn" onClick={() => void submit()}>Предай теста</button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1 style={{ fontSize: '1.15rem', margin: 0 }}>{data.title}</h1>
        {remaining != null && (
          <span className={`chip ${remaining < 300 ? 'warn' : ''}`} role="timer" aria-live={remaining < 60 ? 'polite' : 'off'}>
            ⏱ <span className="sr-only">Оставащо време</span>{timeText}
          </span>
        )}
      </div>
      <nav className="qnav" aria-label="Въпроси" style={{ margin: '.75rem 0' }}>
        {data.items.map((it) => (
          <button key={it.position} type="button" onClick={() => setPos(it.position)}
            className={`${isAnswered(answers[it.position]) ? 'answered' : ''} ${it.position === pos ? 'current' : ''}`}
            aria-current={it.position === pos ? 'step' : undefined}
            aria-label={`Въпрос ${it.position + 1}${isAnswered(answers[it.position]) ? ', отговорен' : ''}${marked[it.position] ? ', маркиран' : ''}`}>
            {it.position + 1}{marked[it.position] && <span className="flag" aria-hidden="true">⚑</span>}
          </button>
        ))}
      </nav>

      <section className="card" aria-labelledby="qtitle">
        <p className="small muted">Въпрос {pos + 1} от {total} · {item.question.maxPoints} т. · {item.question.skillTitle}</p>
        <h2 id="qtitle"><RichText text={item.question.prompt.text} /></h2>
        <AnswerInput key={item.position} question={item.question} value={answers[pos] ?? {}} onChange={(v) => change(pos, v)} />
        {(hints[pos] ?? []).map((h, i) => <div key={i} className="alert info small"><strong>Подсказка {i + 1}:</strong> {h}</div>)}
        <div className="row" style={{ marginTop: '.5rem' }}>
          <button type="button" className="btn secondary" aria-pressed={marked[pos] ?? false} onClick={toggleMark}>
            ⚑ {marked[pos] ? 'Маркиран' : 'Маркирай за преглед'}
          </button>
          {data.hintPolicy === 'ALLOWED' && (hints[pos]?.length ?? 0) < item.question.hintLevels && (
            <button type="button" className="btn ghost" onClick={() => void hint()}>Подсказка</button>
          )}
          <span className="small muted" role="status">
            {saveState[pos] === 'saved' ? '✓ Запазено' : saveState[pos] === 'queued' ? '⏳ Запазено на устройството' : saveState[pos] === 'saving' ? 'Запазване…' : ''}
          </span>
        </div>
      </section>

      <details className="card">
        <summary>Чернова (вижда се само на това устройство)</summary>
        <textarea aria-label="Чернова" value={scratch} onChange={(e) => setScratch(e.target.value)} style={{ marginTop: '.5rem', fontFamily: 'var(--math)' }} />
      </details>

      {submitError && <div className="alert warn" role="alert">{submitError}</div>}
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <button type="button" className="btn secondary" disabled={pos === 0} onClick={() => setPos(pos - 1)}>Назад</button>
        {pos < total - 1
          ? <button type="button" className="btn" onClick={() => setPos(pos + 1)}>Напред</button>
          : <button type="button" className="btn" onClick={() => setReview(true)}>Преглед и предаване</button>}
      </div>
      {pos < total - 1 && <button type="button" className="btn ghost block" onClick={() => setReview(true)}>Към предаване ({answeredCount}/{total})</button>}
    </div>
  );
}
