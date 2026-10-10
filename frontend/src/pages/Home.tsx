import { CLOUD, getCloudSession } from '../cloud';
import { Link } from 'react-router-dom';
import { STANDALONE, type TestSummary } from '../api';
import { Avatar } from '../components/Avatar';
import { Helper } from '../components/Helper';
import { JoinClass } from '../components/JoinClass';
import { ErrorNote, Loading } from '../components/Layout';
import { useApi } from '../session';

interface Home {
  profile: { nickname: string; avatar: string; xp: number; level: number; weeklyGoal: number };
  continueAction?: { type: 'LESSON' | 'TEST'; key?: string; id?: string; title: string; detail: string };
  recommended: { type: string; key?: string; title: string; reason: string };
  secureSkills: Chip[]; practiceSkills: Chip[];
  nextTest?: TestSummary; offerDiagnostic: boolean;
  weeklyGoal: { target: number; activeDays: number; days: boolean[]; message: string };
  assignments: { id: string; title: string; targetType: string; targetKey: string; testId?: string; deadline?: string; statusLabel: string; status: string }[];
  recentRewards: { code: string; title: string; xp: number }[];
  badges: { code: string; title: string }[];
  corrections: string[];
}
interface Chip { skill: string; title: string; stateLabel: string; percent: number; lessonKey?: string }

const DAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Нд'];

/** FR-02: continue, recommended mission, skills, next test and a non-punitive weekly goal. */
export function HomePage() {
  const { data, error, loading, reload } = useApi<Home>('/api/student/home');
  if (loading && !data) return <Loading />;
  if (!data) return <ErrorNote error={error} onRetry={reload} />;
  const levelStart = xpForLevel(data.profile.level);
  const levelNext = xpForLevel(data.profile.level + 1);
  const pct = Math.round(((data.profile.xp - levelStart) / (levelNext - levelStart)) * 100);
  const c = data.continueAction;

  return (
    <div className="stack">
      <div className="row">
        <Avatar id={data.profile.avatar} size={52} />
        <div style={{ flex: 1 }}>
          <h1 style={{ marginBottom: 0 }}>Здравей, {data.profile.nickname}!</h1>
          <div className="small muted">Ниво {data.profile.level} · {data.profile.xp} т. опит</div>
          <div className="progress" role="progressbar" aria-label="Напредък към следващото ниво" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
            <span style={{ width: `${pct}%` }} />
          </div>
        </div>
      </div>

      {data.corrections.length > 0 && (
        <div className="alert info" role="status"><strong>Корекция на резултат</strong>{data.corrections.map((m, i) => <p key={i}>{m}</p>)}</div>
      )}

      {c && (
        <div className="card accent">
          <h2>Продължи</h2>
          <p><strong>{c.title}</strong> · <span className="muted">{c.detail}</span></p>
          <Link className="btn block" to={c.type === 'TEST' ? `/attempt/${c.id}` : `/lesson/${c.key}`}>Продължи оттам, докъдето стигна</Link>
        </div>
      )}

      {data.assignments.length > 0 && (
        <div className="card">
          <h2>Задания от учителя</h2>
          <ul className="stops">
            {data.assignments.map((a) => (
              <li key={a.id} className="stop">
                <span className="chip info">{a.statusLabel}</span>
                <div className="body">
                  <div className="title">{a.title}</div>
                  {a.deadline && <div className="small muted">Срок: {new Date(a.deadline).toLocaleString('bg-BG', { timeZone: 'Europe/Sofia' })}</div>}
                </div>
                {a.status !== 'COMPLETED' && a.status !== 'OVERDUE' && (
                  <Link className="btn secondary" to={a.targetType === 'TEST' ? `/tests/${a.testId}?assignment=${a.id}` : `/lesson/${a.targetKey}`}>Отвори</Link>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {CLOUD && !getCloudSession() && (
        <div className="card accent">
          <h2>Запази профила си</h2>
          <p>Създай вход с имейл и парола — така напредъкът ти няма да се загуби и ще можеш да смениш паролата, ако я забравиш.</p>
          <Link className="btn" to="/account">Създай вход с имейл</Link>
        </div>
      )}

      <div className="card" style={{ borderLeft: '6px solid var(--revision)' }}>
        <h2>Мисия за днес</h2>
        <p><strong>{data.recommended.title}</strong></p>
        <p className="muted small">{data.recommended.reason}</p>
        {data.recommended.key
          ? <Link className="btn" to={`/lesson/${data.recommended.key}`}>Започни</Link>
          : <Link className="btn" to="/plan">Виж плана за седмицата</Link>}
      </div>

      {data.offerDiagnostic && (
        <Helper>Не знаеш откъде да започнеш? Диагностичният тест ще ти подскаже — без оценка и без стрес.</Helper>
      )}

      <div className="grid two">
        <div className="card">
          <h2>Седмична цел</h2>
          <p>{data.weeklyGoal.activeDays} от {data.weeklyGoal.target} дни с учене</p>
          <div className="week" aria-label="Дни с учене тази седмица">
            {data.weeklyGoal.days.map((on, i) => (
              <span key={i} className={on ? 'on' : undefined} aria-label={`${DAYS[i]}: ${on ? 'учил(а)' : 'няма'}`}>{DAYS[i]}{on ? ' ✓' : ''}</span>
            ))}
          </div>
          <p className="small muted">{data.weeklyGoal.message}</p>
        </div>
        <div className="card">
          <h2>Следващ тест</h2>
          {data.nextTest ? (
            <>
              <p><strong>{data.nextTest.title}</strong></p>
              <p className="small muted">{data.nextTest.questionCount} въпроса · {data.nextTest.timeLimitMin} мин.</p>
              <Link className="btn secondary" to={`/tests/${data.nextTest.id}`}>Виж теста</Link>
            </>
          ) : <p className="muted">Няма публикувани тестове.</p>}
        </div>
      </div>

      <div className="grid two">
        <div className="card">
          <h2>Затвърдени умения</h2>
          {data.secureSkills.length === 0 ? <p className="muted small">Все още няма — всяка решена задача те приближава.</p> : (
            <div className="row">{data.secureSkills.map((s) => <span key={s.skill} className="chip ok">✓ {s.title}</span>)}</div>
          )}
        </div>
        <div className="card">
          <h2>За упражнение</h2>
          {data.practiceSkills.length === 0 ? <p className="muted small">Нищо спешно. Продължи по картата!</p> : (
            <ul className="stops">
              {data.practiceSkills.slice(0, 4).map((s) => (
                <li key={s.skill} className="stop"><div className="body"><div className="title">{s.title}</div><div className="small muted">{s.stateLabel}</div></div>
                  {s.lessonKey && <Link className="btn secondary" to={`/lesson/${s.lessonKey}`}>Упражни</Link>}</li>
              ))}
            </ul>
          )}
          <Link to="/plan" className="small">План за 7 дни →</Link>
        </div>
      </div>

      {!STANDALONE && <JoinClass onJoined={() => void reload()} />}
      {STANDALONE && <p className="small muted">Версия за телефон: всичко се пази само на това устройство и работи без интернет.</p>}

      {data.badges.length > 0 && (
        <div className="card">
          <h2>Значки</h2>
          <div className="row">{data.badges.map((b) => <span key={b.code + b.title} className="chip" style={{ background: 'var(--warn-bg)' }}>★ {b.title.replace('Значка: ', '')}</span>)}</div>
        </div>
      )}
    </div>
  );
}

function xpForLevel(level: number): number {
  let xp = 0;
  let need = 100;
  for (let l = 1; l < level; l++) { xp += need; need += 50; }
  return xp;
}
