import { useState } from 'react';
import { api, ApiError } from '../api';

/** Students join a teacher's class with the class code (US-TCH-01). */
export function JoinClass({ onJoined }: { onJoined: () => void }) {
  const [code, setCode] = useState('');
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  async function join() {
    try {
      const r = await api<{ name: string }>('/api/student/classes/join', { method: 'POST', body: { code } });
      setMsg({ ok: true, text: `Присъедини се към клас „${r.name}“.` });
      setCode('');
      onJoined();
    } catch (e) { setMsg({ ok: false, text: (e as ApiError).message }); }
  }
  return (
    <div className="card">
      <h2>Влез в клас</h2>
      <div className="row">
        <label className="field" style={{ flex: 1, marginBottom: 0 }}>
          <span className="sr-only">Код на класа</span>
          <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="Код от учителя" maxLength={12} autoComplete="off" />
        </label>
        <button className="btn" disabled={code.trim().length < 4} onClick={() => void join()}>Влез</button>
      </div>
      {msg && <div className={`alert ${msg.ok ? 'ok' : 'warn'}`} role="status">{msg.text}</div>}
    </div>
  );
}
