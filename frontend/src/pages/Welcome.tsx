import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api';
import { Avatar, AVATARS } from '../components/Avatar';
import { Helper } from '../components/Helper';
import { useSession } from '../session';
import { linkLocal, saveProfile, type CloudSession } from '../cloud';

const GOALS = [
  { id: 'CLASSROOM', label: 'Подготовка за класни работи' },
  { id: 'IMPROVE', label: 'Да разбирам по-добре' },
  { id: 'ASSESSMENT', label: 'Подготовка за НВО' },
];

interface SignupResult { token: string; recoveryCode: string; status: string; consentCode?: string }

/** US-STU-01: nickname, avatar and goal only — no real name, phone, photo or date of birth. */
export function Welcome({ cloud }: { cloud?: CloudSession } = {}) {
  const { signIn } = useSession();
  const nav = useNavigate();
  const [step, setStep] = useState(0);
  const [nickname, setNickname] = useState('');
  const [avatar, setAvatar] = useState('fox');
  const [goal, setGoal] = useState('CLASSROOM');
  const [confidence, setConfidence] = useState(3);
  const [ageBand, setAgeBand] = useState<'UNDER_14' | 'FROM_14' | ''>('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<SignupResult | null>(null);
  const [busy, setBusy] = useState(false);

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const r = await api<SignupResult>('/api/auth/student', { method: 'POST', body: { nickname, avatar, goal, confidence, ageBand } });
      if (cloud) {
        // Save the profile to the account so the same e-mail and password open it on any device.
        await saveProfile(cloud, { nickname: nickname.trim(), avatar, age_band: ageBand || undefined, goal, confidence });
        linkLocal(cloud.user.id, r.token);
      }
      setDone(r);
    } catch (e) {
      setError((e as ApiError).message);
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="stack">
        {cloud ? (
          <>
            <Helper>Профилът е създаден и е запазен към имейла ти.</Helper>
            <div className="card accent">
              <h2>Вход от всяко устройство</h2>
              <p>Влизаш с <strong>{cloud.user.email}</strong> и паролата си. Ако я забравиш, натисни „Забравена парола“ на екрана за вход.</p>
            </div>
          </>
        ) : (
          <>
            <Helper>Профилът е създаден! Запиши този код — с него влизаш от друго устройство.</Helper>
            <div className="card accent">
              <h2>Твоят код за вход</h2>
              <p className="math-block" style={{ letterSpacing: '.1em' }}>{done.recoveryCode}</p>
              <p className="small muted">Пази го като парола. Не го споделяй с други ученици.</p>
            </div>
          </>
        )}
        {done.status === 'PENDING_CONSENT' ? (
          <div className="card">
            <h2>Нужно е съгласие от родител</h2>
            <p>Покажи този код на родител или настойник. На следващия екран натиснете заедно „Родителят е до мен — отвори съгласието“.</p>
            <p className="math-block">{done.consentCode}</p>
            <p className="small muted">Докато няма съгласие, профилът не е активен.</p>
            <button className="btn" onClick={async () => { await signIn(done.token); nav('/'); }}>Разбрах</button>
          </div>
        ) : (
          <div className="card">
            <h2>Искаш ли диагностичен тест?</h2>
            <p>Кратък тест ще покаже откъде е най-добре да започнеш. Не носи оценка.</p>
            <div className="row">
              <button className="btn" onClick={async () => { await signIn(done.token); nav('/tests'); }}>Към тестовете</button>
              <button className="btn secondary" onClick={async () => { await signIn(done.token); nav('/'); }}>По-късно</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="stack">
      <Helper>Здравей! Аз съм Компи. Ще ти помагам да разбираш математиката — стъпка по стъпка.</Helper>
      <div className="card">
        <div className="progress" aria-hidden="true"><span style={{ width: `${((step + 1) / 3) * 100}%` }} /></div>
        <p className="small muted">Стъпка {step + 1} от 3</p>

        {step === 0 && (
          <>
            <h1>Как да те наричаме?</h1>
            <p className="muted">Използвай прякор, не истинското си име.</p>
            <label className="field"><span>Прякор</span>
              <input value={nickname} maxLength={24} onChange={(e) => setNickname(e.target.value)} autoComplete="off" />
            </label>
            <fieldset style={{ border: 0, padding: 0 }}>
              <legend className="field">Избери аватар</legend>
              <div className="row" role="radiogroup" aria-label="Аватар">
                {Object.keys(AVATARS).map((a) => (
                  <button key={a} type="button" role="radio" aria-checked={avatar === a} className="option" style={{ width: 'auto' }}
                    onClick={() => setAvatar(a)} aria-label={AVATARS[a].label}>
                    <Avatar id={a} />
                  </button>
                ))}
              </div>
            </fieldset>
            <button className="btn block" disabled={nickname.trim().length < 2} onClick={() => setStep(1)}>Напред</button>
          </>
        )}

        {step === 1 && (
          <>
            <h1>Каква е целта ти?</h1>
            <div className="options" role="radiogroup" aria-label="Учебна цел">
              {GOALS.map((g) => (
                <button key={g.id} type="button" role="radio" className="option" aria-checked={goal === g.id} onClick={() => setGoal(g.id)}>{g.label}</button>
              ))}
            </div>
            <label className="field" style={{ marginTop: '1rem' }}>
              <span>Колко уверен(а) се чувстваш по алгебра? ({confidence} от 5)</span>
              <input type="range" min={1} max={5} value={confidence} onChange={(e) => setConfidence(Number(e.target.value))}
                aria-valuetext={`${confidence} от 5`} style={{ width: '100%', minHeight: 44 }} />
            </label>
            <div className="row">
              <button className="btn secondary" onClick={() => setStep(0)}>Назад</button>
              <button className="btn" onClick={() => setStep(2)}>Напред</button>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h1>На колко години си?</h1>
            <p className="muted">Питаме само дали си под 14 години — при по-малките е нужно съгласие от родител.</p>
            <div className="options" role="radiogroup" aria-label="Възраст">
              <button type="button" role="radio" className="option" aria-checked={ageBand === 'UNDER_14'} onClick={() => setAgeBand('UNDER_14')}>Под 14 години</button>
              <button type="button" role="radio" className="option" aria-checked={ageBand === 'FROM_14'} onClick={() => setAgeBand('FROM_14')}>14 или повече</button>
            </div>
            <details className="small" style={{ margin: '1rem 0' }}>
              <summary>Какво пазим за теб?</summary>
              <ChildPrivacy />
            </details>
            {error && <div className="alert bad" role="alert">{error}</div>}
            <div className="row">
              <button className="btn secondary" onClick={() => setStep(1)}>Назад</button>
              <button className="btn" disabled={!ageBand || busy} onClick={() => void create()}>Създай профил</button>
            </div>
          </>
        )}
      </div>
      {!cloud && <p className="small">Имаш профил? <Link to="/login">Влез с код</Link> · Учител или родител? <Link to="/login">Вход</Link></p>}
    </div>
  );
}

function ChildPrivacy() {
  return (
    <ul>
      <li>Учиш с прякор и аватар — без истинско име, телефон или снимка.</li>
      <li>Имейлът се използва само за вход и за нова парола. Паролата се пази защитено и никой не може да я прочете.</li>
      <li>Пазим отговорите и напредъка ти, за да ти препоръчваме какво да упражниш.</li>
      <li>Други ученици не виждат резултатите ти. Няма реклами.</li>
    </ul>
  );
}
