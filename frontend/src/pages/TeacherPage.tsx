import { useState } from 'react';
import { api, ApiError } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';
import curriculum from '../local/data/curriculum.json';
import lessonKeys from '../local/data/lesson-keys.json';

const LESSON_OPTIONS = (curriculum as { lessons: { key: string; title: string }[] }).lessons.filter((l) => (lessonKeys as string[]).includes(l.key));

interface ClassView { id: string; name: string; code: string; codeRevoked: boolean; activeStudents: number }
interface Member { studentId: string; nickname: string; avatar: string; joinedAt: string }
interface TestRow { id: string; key: string; title: string; kind: string; status: string; questionCount: number; timeLimitMin: number }
interface Assignment { id: string; title: string; targetType: string; deadline?: string; allowedAttempts: number; hintPolicy: string; wholeClass: boolean; testId?: string }
interface Report {
  title: string; enrolled: number; participated: number; completed: number; averagePercent?: number;
  distribution: { label: string; count: number }[];
  items: { questionNumber: number; questionKey: string; answered: number; averagePercent: number; topMisconception?: string }[];
  skills: { skill: string; title: string; averagePercent: number; students: number }[];
  misconceptions: { code: string; label: string; occurrences: number; students: number }[];
  students: { studentId: string; nickname: string; attempts: number; firstPercent?: number; latestPercent?: number; change?: number; scoringSource: string }[];
  note: string;
}

/** Teacher workspace: classes (US-TCH-01), assignments (02), skill results (03), generator (04), review queue (05). */
export function TeacherPage() {
  const classes = useApi<ClassView[]>('/api/teacher/classes');
  const tests = useApi<TestRow[]>('/api/teacher/tests');
  const [selected, setSelected] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  async function create() {
    try { await api('/api/teacher/classes', { method: 'POST', body: { name } }); setName(''); void classes.reload(); }
    catch (e) { setMsg((e as ApiError).message); }
  }

  if (classes.loading && !classes.data) return <Loading />;
  const cls = classes.data?.find((c) => c.id === selected);
  return (
    <div className="stack">
      <h1>Класове</h1>
      <ErrorNote error={classes.error} onRetry={classes.reload} />
      {msg && <div className="alert warn" role="alert">{msg}</div>}
      <div className="card">
        <div className="row">
          <label className="field" style={{ flex: 1 }}><span>Нов клас</span><input value={name} onChange={(e) => setName(e.target.value)} placeholder="напр. 7б" /></label>
          <button className="btn" disabled={!name.trim()} onClick={() => void create()}>Създай</button>
        </div>
      </div>
      <div className="row">
        {classes.data?.map((c) => (
          <button key={c.id} className={`btn ${c.id === selected ? '' : 'secondary'}`} onClick={() => setSelected(c.id)}>{c.name} ({c.activeStudents})</button>
        ))}
      </div>
      {cls && <ClassDetail key={cls.id} cls={cls} tests={tests.data ?? []} onChanged={classes.reload} />}
      <Generator onCreated={tests.reload} />
      <ReviewQueue />
    </div>
  );
}

function ClassDetail({ cls, tests, onChanged }: { cls: ClassView; tests: TestRow[]; onChanged: () => void }) {
  const members = useApi<Member[]>(`/api/teacher/classes/${cls.id}/members`, [cls.id]);
  const assignments = useApi<Assignment[]>(`/api/teacher/classes/${cls.id}/assignments`, [cls.id]);
  const [target, setTarget] = useState('');
  const [deadline, setDeadline] = useState('');
  const [attempts, setAttempts] = useState(1);
  const [hintPolicy, setHintPolicy] = useState('NONE');
  const [preview, setPreview] = useState<unknown>(null);
  const [report, setReport] = useState<Report | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function act(fn: () => Promise<unknown>) {
    setErr(null);
    try { await fn(); } catch (e) { setErr((e as ApiError).message); }
  }
  const [type, key] = target.split(':');

  return (
    <div className="card stack">
      <h2>{cls.name}</h2>
      {err && <div className="alert warn" role="alert">{err}</div>}
      <p>Код за присъединяване: <strong className="math" style={{ letterSpacing: '.1em' }}>{cls.codeRevoked ? '(отменен)' : cls.code}</strong></p>
      <div className="row">
        <button className="btn secondary" onClick={() => void act(async () => { await api(`/api/teacher/classes/${cls.id}/code/revoke`, { method: 'POST' }); onChanged(); })}>Отмени кода</button>
        <button className="btn secondary" onClick={() => void act(async () => { await api(`/api/teacher/classes/${cls.id}/code/regenerate`, { method: 'POST' }); onChanged(); })}>Нов код</button>
      </div>

      <h3>Ученици</h3>
      {members.data?.length === 0 && <p className="muted small">Още няма ученици. Дайте им кода — те се присъединяват с прякор.</p>}
      <ul className="stops">
        {members.data?.map((m) => (
          <li key={m.studentId} className="stop"><div className="body">{m.nickname}</div>
            <button className="btn ghost" onClick={() => void act(async () => {
              await api(`/api/teacher/classes/${cls.id}/members/${m.studentId}`, { method: 'DELETE' }); void members.reload(); onChanged();
            })}>Премахни от класа</button></li>
        ))}
      </ul>

      <h3>Ново задание</h3>
      <label className="field"><span>Урок или тест</span>
        <select value={target} onChange={(e) => setTarget(e.target.value)}>
          <option value="">Избери…</option>
          {LESSON_OPTIONS.map((l) => <option key={l.key} value={`LESSON:${l.key}`}>Урок {l.key} — {l.title}</option>)}
          {tests.filter((t) => t.status === 'PUBLISHED').map((t) => <option key={t.id} value={`TEST:${t.key}`}>Тест: {t.title} ({t.questionCount} в.)</option>)}
        </select>
      </label>
      <div className="grid three">
        <label className="field"><span>Краен срок</span><input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} /></label>
        <label className="field"><span>Разрешени опити</span><input type="number" min={1} max={10} value={attempts} onChange={(e) => setAttempts(Number(e.target.value))} /></label>
        <label className="field"><span>Подсказки</span>
          <select value={hintPolicy} onChange={(e) => setHintPolicy(e.target.value)}><option value="NONE">Без подсказки</option><option value="ALLOWED">Разрешени</option></select>
        </label>
      </div>
      <div className="row">
        <button className="btn secondary" disabled={!target} onClick={() => void act(async () =>
          setPreview(await api(type === 'TEST' ? `/api/teacher/preview/test/${key}` : `/api/teacher/preview/lesson/${key}`)))}>Преглед като ученик</button>
        <button className="btn" disabled={!target} onClick={() => void act(async () => {
          await api(`/api/teacher/classes/${cls.id}/assignments`, { method: 'POST', body: {
            targetType: type, targetKey: key, allowedAttempts: attempts, hintPolicy,
            deadline: deadline ? new Date(deadline).toISOString() : null } });
          void assignments.reload();
        })}>Възложи на целия клас</button>
      </div>
      {preview != null && (
        <details open className="card"><summary>Какво ще видят учениците</summary>
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: '.8rem', maxHeight: 300, overflow: 'auto' }}>{JSON.stringify(preview, null, 1)}</pre>
        </details>
      )}

      <h3>Задания</h3>
      <ul className="stops">
        {assignments.data?.map((a) => (
          <li key={a.id} className="stop"><div className="body"><div className="title">{a.title}</div>
            <div className="small muted">{a.deadline ? `до ${new Date(a.deadline).toLocaleString('bg-BG', { timeZone: 'Europe/Sofia' })}` : 'без срок'} · опити: {a.allowedAttempts}</div></div>
            {a.testId && <button className="btn secondary" onClick={() => void act(async () => setReport(await api<Report>(`/api/teacher/classes/${cls.id}/tests/${a.testId}/report`)))}>Резултати</button>}
          </li>
        ))}
      </ul>
      {report && <ReportView r={report} />}
    </div>
  );
}

function ReportView({ r }: { r: Report }) {
  return (
    <div className="card">
      <h3>{r.title}</h3>
      <p>В класа: {r.enrolled} · Започнали: {r.participated} · Предали: {r.completed} · Среден резултат: {r.averagePercent ?? '—'}%</p>
      <p className="small muted">{r.note}</p>
      <h4>Разпределение</h4>
      <div className="row">{r.distribution.map((d) => <span key={d.label} className="chip">{d.label}: {d.count}</span>)}</div>
      <h4>Умения (от най-слабото)</h4>
      <div className="table-wrap"><table className="data"><thead><tr><th>Умение</th><th>Среден %</th><th>Ученици</th></tr></thead>
        <tbody>{r.skills.map((s) => <tr key={s.skill}><td>{s.title}</td><td>{s.averagePercent}</td><td>{s.students}</td></tr>)}</tbody></table></div>
      <h4>Типични грешки</h4>
      <div className="table-wrap"><table className="data"><thead><tr><th>Вид</th><th>Случаи</th><th>Ученици</th></tr></thead>
        <tbody>{r.misconceptions.map((m) => <tr key={m.code}><td>{m.label}</td><td>{m.occurrences}</td><td>{m.students}</td></tr>)}</tbody></table></div>
      <h4>Анализ по въпроси</h4>
      <div className="table-wrap"><table className="data"><thead><tr><th>№</th><th>Отговорили</th><th>Среден %</th><th>Честа грешка</th></tr></thead>
        <tbody>{r.items.map((i) => <tr key={i.questionKey}><td>{i.questionNumber}</td><td>{i.answered}</td><td>{i.averagePercent}</td><td>{i.topMisconception ?? '—'}</td></tr>)}</tbody></table></div>
      <h4>Първи и последен опит</h4>
      <div className="table-wrap"><table className="data"><thead><tr><th>Ученик</th><th>Опити</th><th>Първи %</th><th>Последен %</th><th>Промяна</th></tr></thead>
        <tbody>{r.students.map((s) => <tr key={s.studentId}><td>{s.nickname}</td><td>{s.attempts}</td><td>{s.firstPercent ?? '—'}</td><td>{s.latestPercent ?? '—'}</td><td>{s.change ?? '—'}</td></tr>)}</tbody></table></div>
    </div>
  );
}

function Generator({ onCreated }: { onCreated: () => void }) {
  const [title, setTitle] = useState('Едночлени — тест от учителя');
  const [count, setCount] = useState(20);
  const [time, setTime] = useState(40);
  const [preview, setPreview] = useState<{ testId: string; status: string; questionCount: number; maxPoints: number; warnings: string[];
    typeComposition: Record<string, number>; difficultyComposition: Record<string, number>;
    items: { position: number; question: { prompt: { text: string } }; correctAnswer: string }[] } | null>(null);
  const [err, setErr] = useState<string | null>(null);
  async function generate() {
    setErr(null);
    try { setPreview(await api('/api/teacher/tests/generate', { method: 'POST', body: { title, paths: ['B'], questionCount: count, timeLimitMin: time } })); }
    catch (e) { setErr((e as ApiError).message); }
  }
  async function publish() {
    try { setPreview(await api(`/api/teacher/tests/${preview!.testId}/publish`, { method: 'POST' })); onCreated(); }
    catch (e) { setErr((e as ApiError).message); }
  }
  return (
    <div className="card stack">
      <h2>Генератор на тест</h2>
      <p className="small muted">Само прегледани въпроси, без повторения. Оценяващ тест има поне 20 въпроса.</p>
      <div className="grid three">
        <label className="field"><span>Заглавие</span><input value={title} onChange={(e) => setTitle(e.target.value)} /></label>
        <label className="field"><span>Брой въпроси</span><input type="number" min={20} max={60} value={count} onChange={(e) => setCount(Number(e.target.value))} /></label>
        <label className="field"><span>Време (мин.)</span><input type="number" min={10} value={time} onChange={(e) => setTime(Number(e.target.value))} /></label>
      </div>
      {err && <div className="alert warn" role="alert">{err}</div>}
      <button className="btn" onClick={() => void generate()}>Генерирай чернова</button>
      {preview && (
        <div>
          <p>Статус: <strong>{preview.status}</strong> · {preview.questionCount} въпроса · {preview.maxPoints} т.</p>
          <p className="small">Типове: {JSON.stringify(preview.typeComposition)} · Трудност: {JSON.stringify(preview.difficultyComposition)}</p>
          {preview.warnings.map((w) => <div key={w} className="alert warn small">{w}</div>)}
          <details><summary>Пълен преглед с отговори (само за учителя)</summary>
            <ol>{preview.items.map((i) => <li key={i.position}>{i.question.prompt.text} — <strong>{i.correctAnswer}</strong></li>)}</ol></details>
          {preview.status === 'DRAFT' && <button className="btn" onClick={() => void publish()}>Публикувай</button>}
        </div>
      )}
    </div>
  );
}

function ReviewQueue() {
  const q = useApi<{ itemId: string; testTitle: string; position: number; question: { prompt: { text: string } }; answer?: { text?: string }; maxPoints: number; rubric?: string }[]>('/api/teacher/review-queue');
  const [points, setPoints] = useState<Record<string, number>>({});
  const [comment, setComment] = useState<Record<string, string>>({});
  if (!q.data?.length) return null;
  return (
    <div className="card">
      <h2>За преглед ({q.data.length})</h2>
      {q.data.map((i) => (
        <div key={i.itemId} className="card">
          <p className="small muted">{i.testTitle} · въпрос {i.position + 1}</p>
          <p>{i.question.prompt.text}</p>
          <p><strong>Отговор:</strong> {i.answer?.text}</p>
          {i.rubric && <p className="small">Рубрика: {i.rubric}</p>}
          <div className="row">
            <label className="field"><span>Точки (0–{i.maxPoints})</span><input type="number" min={0} max={i.maxPoints} step={0.5} value={points[i.itemId] ?? 0} onChange={(e) => setPoints({ ...points, [i.itemId]: Number(e.target.value) })} /></label>
            <label className="field" style={{ flex: 1 }}><span>Коментар</span><input value={comment[i.itemId] ?? ''} onChange={(e) => setComment({ ...comment, [i.itemId]: e.target.value })} /></label>
          </div>
          <button className="btn" onClick={async () => { await api(`/api/teacher/review-queue/${i.itemId}`, { method: 'POST', body: { points: points[i.itemId] ?? 0, comment: comment[i.itemId] } }); void q.reload(); }}>Запиши оценката</button>
        </div>
      ))}
    </div>
  );
}
