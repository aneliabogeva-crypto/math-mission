import { useState } from 'react';
import { api, ApiError } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi, useSession } from '../session';

interface QAdmin { id: string; key: string; version: number; status: string; draft: { skill: string; prompt: { text: string }; key: { answer?: string; correctOptionId?: string; solution: string } };
  reviewComment?: string; validationProblems: string[] }
interface LAdmin { id: string; key: string; version: number; status: string; title: string; patternProblems: string[]; reviewComment?: string }

/** Review workflow for authors and mathematics reviewers (US-CNT-01..03). Authoring of new items is done through the API/editor. */
export function ContentPage() {
  const { me } = useSession();
  const [status, setStatus] = useState(me?.role === 'REVIEWER' ? 'IN_REVIEW' : 'DRAFT');
  const qs = useApi<QAdmin[]>(`/api/content/questions?status=${status}`, [status]);
  const ls = useApi<LAdmin[]>(`/api/content/lessons?status=${status}`, [status]);
  const [comment, setComment] = useState<Record<string, string>>({});
  const [msg, setMsg] = useState<string | null>(null);

  async function act(path: string, body?: unknown) {
    setMsg(null);
    try { await api(path, { method: 'POST', body }); void qs.reload(); void ls.reload(); setMsg('Готово.'); }
    catch (e) { setMsg((e as ApiError).message); }
  }

  return (
    <div className="stack">
      <h1>Съдържание</h1>
      <div className="row" role="tablist">
        {['DRAFT', 'IN_REVIEW', 'PUBLISHED', 'WITHDRAWN', 'SUPERSEDED'].map((s) => (
          <button key={s} role="tab" aria-selected={s === status} className={`btn ${s === status ? '' : 'secondary'}`} onClick={() => setStatus(s)}>{s}</button>
        ))}
      </div>
      {msg && <div className="alert info" role="status">{msg}</div>}
      {(qs.loading || ls.loading) && <Loading />}
      <ErrorNote error={qs.error ?? ls.error} />
      {ls.data?.map((l) => (
        <div key={l.id} className="card">
          <span className="chip">Урок {l.key} v{l.version}</span> <strong>{l.title}</strong>
          {l.patternProblems.length > 0 && <ul className="small">{l.patternProblems.map((p) => <li key={p}>{p}</li>)}</ul>}
          {l.reviewComment && <p className="small muted">Коментар: {l.reviewComment}</p>}
          {me?.role === 'AUTHOR' && l.status === 'DRAFT' && <button className="btn" onClick={() => void act(`/api/content/lessons/${l.id}/submit`)}>Изпрати за преглед</button>}
          {me?.role === 'REVIEWER' && l.status === 'IN_REVIEW' && (
            <ReviewButtons id={l.id} kind="lessons" comment={comment[l.id] ?? ''} setComment={(v) => setComment({ ...comment, [l.id]: v })} act={act} />
          )}
        </div>
      ))}
      {qs.data?.map((q) => (
        <div key={q.id} className="card">
          <span className="chip">{q.key} v{q.version}</span> <span className="small muted">{q.draft.skill}</span>
          <p>{q.draft.prompt.text}</p>
          <p className="small">Ключ: <strong>{q.draft.key.answer ?? q.draft.key.correctOptionId}</strong> · {q.draft.key.solution}</p>
          {q.reviewComment && <p className="small muted">Коментар: {q.reviewComment}</p>}
          {me?.role === 'AUTHOR' && q.status === 'DRAFT' && <button className="btn" onClick={() => void act(`/api/content/questions/${q.id}/submit`)}>Изпрати за преглед</button>}
          {me?.role === 'REVIEWER' && q.status === 'IN_REVIEW' && (
            <ReviewButtons id={q.id} kind="questions" comment={comment[q.id] ?? ''} setComment={(v) => setComment({ ...comment, [q.id]: v })} act={act} />
          )}
          {(me?.role === 'REVIEWER' || me?.role === 'ADMIN') && q.status === 'PUBLISHED' && (
            <button className="btn ghost" onClick={() => {
              const reason = prompt('Причина за оттегляне:') ?? '';
              if (reason) void act(`/api/content/questions/by-key/${q.key}/withdraw`, { reason });
            }}>Оттегли (дефект)</button>
          )}
        </div>
      ))}
    </div>
  );
}

function ReviewButtons({ id, kind, comment, setComment, act }: { id: string; kind: string; comment: string; setComment: (v: string) => void;
  act: (path: string, body?: unknown) => Promise<void> }) {
  return (
    <div>
      <label className="field"><span>Коментар към автора</span><input value={comment} onChange={(e) => setComment(e.target.value)} /></label>
      <div className="row">
        <button className="btn" onClick={() => void act(`/api/content/${kind}/${id}/review`, { approve: true, comment })}>Одобри (математически проверено)</button>
        <button className="btn secondary" disabled={!comment} onClick={() => void act(`/api/content/${kind}/${id}/review`, { approve: false, comment })}>Върни с коментар</button>
      </div>
    </div>
  );
}
