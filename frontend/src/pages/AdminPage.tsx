import { useState } from 'react';
import { api, ApiError } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

interface Staff { id: string; username: string; displayName: string; role: string; status: string; mfaEnabled: boolean; lastActiveAt?: string; flags: string[] }
interface Blueprint { id: string; name: string; academicYear: string; version: number; status: string; composition: Record<string, number>; questionCount: number; timeLimitMin: number }
interface Audit { id: string; action: string; entityType: string; entityId: string; details?: string; createdAt: string }
interface Privacy { id: string; kind: string; status: string; createdAt: string }

export function AdminPage() {
  const staff = useApi<Staff[]>('/api/admin/access-review');
  const bps = useApi<Blueprint[]>('/api/admin/assessment-models');
  const audit = useApi<Audit[]>('/api/admin/audit');
  const privacy = useApi<Privacy[]>('/api/admin/privacy-requests');
  const [form, setForm] = useState({ role: 'TEACHER', username: '', displayName: '', password: '' });
  const [msg, setMsg] = useState<string | null>(null);

  async function act(fn: () => Promise<unknown>, ok: string) {
    try { const r = await fn(); setMsg(typeof r === 'string' ? r : ok); void staff.reload(); void bps.reload(); void privacy.reload(); void audit.reload(); }
    catch (e) { setMsg((e as ApiError).message); }
  }

  if (staff.loading && !staff.data) return <Loading />;
  return (
    <div className="stack">
      <h1>Администрация</h1>
      {msg && <div className="alert info" role="status">{msg}</div>}
      <ErrorNote error={staff.error} onRetry={staff.reload} />

      <div className="card">
        <h2>Преглед на достъпа</h2>
        <div className="table-wrap"><table className="data">
          <thead><tr><th>Потребител</th><th>Роля</th><th>MFA</th><th>Последна активност</th><th>Бележки</th><th /></tr></thead>
          <tbody>{staff.data?.map((s) => (
            <tr key={s.id}><td>{s.username ?? s.displayName}</td><td>{s.role}</td><td>{s.mfaEnabled ? '✓' : '—'}</td>
              <td>{s.lastActiveAt ? new Date(s.lastActiveAt).toLocaleDateString('bg-BG') : 'никога'}</td><td>{s.flags.join('; ')}</td>
              <td>{s.status === 'ACTIVE' && <button className="btn ghost" onClick={() => void act(() => api(`/api/admin/users/${s.id}/disable`, { method: 'POST' }), 'Профилът е деактивиран.')}>Деактивирай</button>}</td></tr>
          ))}</tbody>
        </table></div>
      </div>

      <div className="card">
        <h2>Нов служебен профил</h2>
        <div className="grid two">
          <label className="field"><span>Роля</span><select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            {['TEACHER', 'AUTHOR', 'REVIEWER', 'ADMIN'].map((r) => <option key={r}>{r}</option>)}</select></label>
          <label className="field"><span>Потребителско име</span><input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
          <label className="field"><span>Име за показване</span><input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></label>
          <label className="field"><span>Временна парола (12+)</span><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
        </div>
        <button className="btn" onClick={() => void act(async () => {
          const r = await api<{ totpSecret?: string }>('/api/admin/staff', { method: 'POST', body: form });
          return r.totpSecret ? `Създаден. TOTP тайна (покажете я веднъж на администратора): ${r.totpSecret}` : 'Създаден.';
        }, 'Създаден.')}>Създай</button>
      </div>

      <div className="card">
        <h2>Модели на оценяване</h2>
        <ul className="stops">{bps.data?.map((b) => (
          <li key={b.id} className="stop"><div className="body"><div className="title">{b.name} v{b.version} · {b.academicYear}</div>
            <div className="small muted">{b.status} · {b.questionCount} въпроса · {b.timeLimitMin} мин. · {JSON.stringify(b.composition)}</div></div>
            {b.status === 'DRAFT' && <button className="btn" onClick={() => void act(() => api(`/api/admin/assessment-models/${b.id}/approve`, { method: 'POST' }), 'Одобрен.')}>Одобри</button>}</li>
        ))}</ul>
      </div>

      <div className="card">
        <h2>Заявки за лични данни</h2>
        {privacy.data?.length === 0 && <p className="muted small">Няма отворени заявки.</p>}
        <ul className="stops">{privacy.data?.map((p) => (
          <li key={p.id} className="stop"><div className="body">{p.kind} · {new Date(p.createdAt).toLocaleString('bg-BG', { timeZone: 'Europe/Sofia' })}</div>
            <button className="btn" onClick={() => void act(() => api(`/api/admin/privacy-requests/${p.id}/complete`, { method: 'POST' }), 'Изпълнена.')}>Изпълни</button></li>
        ))}</ul>
      </div>

      <div className="card">
        <h2>Одитен дневник (последни 200)</h2>
        <div className="table-wrap" style={{ maxHeight: 360, overflow: 'auto' }}><table className="data">
          <thead><tr><th>Време</th><th>Действие</th><th>Обект</th><th>Детайли</th></tr></thead>
          <tbody>{audit.data?.map((a) => (
            <tr key={a.id}><td>{new Date(a.createdAt).toLocaleString('bg-BG', { timeZone: 'Europe/Sofia' })}</td><td>{a.action}</td><td>{a.entityType} {a.entityId.slice(0, 12)}</td><td>{a.details}</td></tr>
          ))}</tbody>
        </table></div>
      </div>
    </div>
  );
}
