import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { Helper } from './components/Helper';
import { Layout, Loading } from './components/Layout';
import { AdminPage } from './pages/AdminPage';
import { AttemptPlayer } from './pages/AttemptPlayer';
import { ConsentPage } from './pages/ConsentPage';
import { ContentPage } from './pages/ContentPage';
import { GuardianPage } from './pages/GuardianPage';
import { HomePage } from './pages/Home';
import { LessonPlayer } from './pages/LessonPlayer';
import { Login } from './pages/Login';
import { MapPage } from './pages/MapPage';
import { MistakesPage } from './pages/MistakesPage';
import { PlanPage } from './pages/PlanPage';
import { PracticePage } from './pages/PracticePage';
import { ReferencePage } from './pages/ReferencePage';
import { ResultPage } from './pages/ResultPage';
import { TeacherPage } from './pages/TeacherPage';
import { TestIntro, TestsPage } from './pages/TestsPage';
import { Welcome } from './pages/Welcome';
import { AccountPage, CloudWelcome } from './pages/Account';
import { CLOUD, URL_AUTH, getCloudSession } from './cloud';
import { useState } from 'react';
import { useSession } from './session';
import type { Role } from './api';

const HOME_BY_ROLE: Record<Role, string> = { STUDENT: '/', TEACHER: '/teacher', GUARDIAN: '/guardian', AUTHOR: '/content', REVIEWER: '/content', ADMIN: '/admin' };

function Only({ roles, children }: { roles: Role[]; children: JSX.Element }) {
  const { me, signOut } = useSession();
  if (!me) return <Navigate to="/welcome" replace />;
  if (!roles.includes(me.role)) return <Navigate to={HOME_BY_ROLE[me.role]} replace />;
  // Only accounts with e-mail and password: a device-only profile must be linked to an account first.
  if (CLOUD && me.role === 'STUDENT' && !getCloudSession()) {
    return (
      <>
        <CloudWelcome initial="register" notice={`Вече се влиза само с имейл и парола. Създай вход за профила „${me.displayName}“ — напредъкът ти се запазва. Ако вече имаш профил с имейл, натисни „Имам профил — вход“.`} />
        <p className="small" style={{ textAlign: 'center' }}>Това не е твоят профил? <button type="button" className="btn ghost" onClick={() => void signOut()}>Изход</button></p>
      </>
    );
  }
  if (me.role === 'STUDENT' && me.status !== 'ACTIVE') return <PendingConsent />;
  return children;
}

function PendingConsent() {
  const { me, refresh } = useSession();
  return (
    <div className="stack">
      <Helper mood="thinking">Почти сме готови! Нужно е родител или настойник да даде съгласие.</Helper>
      {me?.consentCode ? (
        <div className="card">
          <p>Покажи този код на родител и натиснете бутона по-долу заедно.</p>
          <p className="math-block">{me.consentCode}</p>
          <Link className="btn" to={`/consent/${me.consentCode}`}>Родителят е до мен — отвори съгласието</Link>
        </div>
      ) : <div className="card"><p>Профилът е ограничен. Попитай родителя си.</p></div>}
      <button className="btn" onClick={() => void refresh()}>Провери отново</button>
    </div>
  );
}

export function App() {
  const { me, loading } = useSession();
  // A "new password" link from the e-mail opens the reset form whatever page it lands on.
  const [recovering, setRecovering] = useState(() => CLOUD && URL_AUTH.type === 'recovery');
  if (loading) return <Layout><Loading /></Layout>;
  if (recovering) return <Layout><CloudWelcome onDone={() => setRecovering(false)} /></Layout>;
  const S: Role[] = ['STUDENT'];
  return (
    <Layout>
      <Routes>
        <Route path="/welcome" element={me ? <Navigate to={HOME_BY_ROLE[me.role]} replace /> : CLOUD ? <CloudWelcome /> : <Welcome />} />
        <Route path="/account" element={<Only roles={S}><AccountPage /></Only>} />
        <Route path="/login" element={CLOUD ? <Navigate to="/welcome" replace /> : <Login />} />
        <Route path="/consent/:code" element={<ConsentPage />} />
        <Route path="/" element={<Only roles={S}><HomePage /></Only>} />
        <Route path="/map" element={<Only roles={S}><MapPage /></Only>} />
        <Route path="/lesson/:key" element={<Only roles={S}><LessonPlayer /></Only>} />
        <Route path="/practice/:key" element={<Only roles={S}><PracticePage /></Only>} />
        <Route path="/tests" element={<Only roles={S}><TestsPage /></Only>} />
        <Route path="/tests/:id" element={<Only roles={S}><TestIntro /></Only>} />
        <Route path="/attempt/:id" element={<Only roles={S}><AttemptPlayer /></Only>} />
        <Route path="/result/:id" element={<Only roles={S}><ResultPage /></Only>} />
        <Route path="/mistakes" element={<Only roles={S}><MistakesPage /></Only>} />
        <Route path="/plan" element={<Only roles={S}><PlanPage /></Only>} />
        <Route path="/reference" element={<Only roles={S}><ReferencePage /></Only>} />
        <Route path="/teacher" element={<Only roles={['TEACHER']}><TeacherPage /></Only>} />
        <Route path="/guardian" element={<Only roles={['GUARDIAN']}><GuardianPage /></Only>} />
        <Route path="/content" element={<Only roles={['AUTHOR', 'REVIEWER', 'ADMIN']}><ContentPage /></Only>} />
        <Route path="/admin" element={<Only roles={['ADMIN']}><AdminPage /></Only>} />
        <Route path="*" element={<Navigate to={me ? HOME_BY_ROLE[me.role] : '/welcome'} replace />} />
      </Routes>
    </Layout>
  );
}
