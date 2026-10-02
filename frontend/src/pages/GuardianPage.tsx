import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

interface Child {
  consentId: string; nickname: string; accountStatus: string; consentStatus: string; consentVersion: string; consentGrantedAt?: string;
  verificationStatus: string; notificationFrequency: string; weekStart: string; activeDays: number; lessonsCompletedThisWeek: number;
  practiceAnswersThisWeek: number; testsThisWeek: string[]; secureSkills: string[]; supportAreas: string[]; suggestions: string[];
}

/** US-GUA-02: weekly summary (not real-time monitoring) and data controls. */
export function GuardianPage() {
  const { data, error, loading, reload } = useApi<Child[]>('/api/guardian/children');
  const consequences = useApi<string[]>('/api/consent/text/withdrawal-consequences');
  const nav = useNavigate();
  const [code, setCode] = useState('');
  const [confirming, setConfirming] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  async function act(fn: () => Promise<unknown>, ok: string) {
    try { await fn(); setMsg(ok); void reload(); } catch (e) { setMsg((e as ApiError).message); }
  }
  async function download(consentId: string, nickname: string) {
    const d = await api(`/api/guardian/consents/${consentId}/export`);
    const url = URL.createObjectURL(new Blob([JSON.stringify(d, null, 2)], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url; a.download = `math-mission-${nickname}.json`; a.click();
    URL.revokeObjectURL(url);
  }

  if (loading && !data) return <Loading />;
  return (
    <div className="stack">
      <h1>Родителски портал</h1>
      <ErrorNote error={error} onRetry={reload} />
      {msg && <div className="alert info" role="status">{msg}</div>}
      <div className="card row">
        <label className="field" style={{ flex: 1 }}><span>Код за съгласие от детето</span><input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} /></label>
        <button className="btn" disabled={code.length < 6} onClick={() => nav(`/consent/${code}`)}>Отвори</button>
      </div>
      {data?.map((c) => (
        <section key={c.consentId} className="card stack">
          <h2>{c.nickname}</h2>
          <p className="small muted">Седмица от {new Date(c.weekStart).toLocaleDateString('bg-BG')} · Обобщение, а не наблюдение в реално време.</p>
          <div className="grid three">
            <div><strong>{c.activeDays}</strong><div className="small">дни с учене</div></div>
            <div><strong>{c.lessonsCompletedThisWeek}</strong><div className="small">завършени урока</div></div>
            <div><strong>{c.practiceAnswersThisWeek}</strong><div className="small">решени задачи</div></div>
          </div>
          {c.testsThisWeek.length > 0 && <p>Тестове: {c.testsThisWeek.join('; ')}</p>}
          <p><strong>Затвърдени умения:</strong> {c.secureSkills.length ? c.secureSkills.join(', ') : 'все още няма'}</p>
          <p><strong>Има нужда от подкрепа:</strong> {c.supportAreas.length ? c.supportAreas.join(', ') : 'няма'}</p>
          <div className="alert info"><strong>Как да помогнете</strong><ul>{c.suggestions.map((s) => <li key={s}>{s}</li>)}</ul></div>

          <details>
            <summary>Съгласие и данни</summary>
            <p className="small">Съгласие: {c.consentStatus} · версия {c.consentVersion} · {c.consentGrantedAt ? new Date(c.consentGrantedAt).toLocaleString('bg-BG', { timeZone: 'Europe/Sofia' }) : ''} · потвърждение: {c.verificationStatus}</p>
            <label className="field"><span>Седмично обобщение</span>
              <select value={c.notificationFrequency} onChange={(e) => void act(() => api(`/api/guardian/consents/${c.consentId}/notifications`, { method: 'PUT', body: { frequency: e.target.value } }), 'Настройката е запазена.')}>
                <option value="WEEKLY">Всяка седмица</option><option value="MONTHLY">Веднъж месечно</option><option value="OFF">Изключено</option>
              </select>
            </label>
            <div className="row">
              <button className="btn secondary" onClick={() => void download(c.consentId, c.nickname)}>Изтегли данните</button>
              <button className="btn secondary" onClick={() => void act(() => api(`/api/guardian/consents/${c.consentId}/deletion-request`, { method: 'POST' }), 'Заявката за изтриване е приета.')}>Поискай изтриване</button>
              {c.consentStatus === 'GRANTED' && <button className="btn ghost" onClick={() => setConfirming(c.consentId)}>Оттегли съгласието</button>}
            </div>
            {confirming === c.consentId && (
              <div className="alert warn" role="alertdialog" aria-label="Последици от оттеглянето">
                <strong>Какво ще се случи:</strong>
                <ul>{consequences.data?.map((x) => <li key={x}>{x}</li>)}</ul>
                <div className="row">
                  <button className="btn" onClick={() => void act(() => api(`/api/guardian/consents/${c.consentId}/withdraw`, { method: 'POST' }), 'Съгласието е оттеглено.').then(() => setConfirming(null))}>Потвърждавам оттеглянето</button>
                  <button className="btn secondary" onClick={() => setConfirming(null)}>Отказ</button>
                </div>
              </div>
            )}
          </details>
        </section>
      ))}
    </div>
  );
}
