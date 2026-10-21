// Sends the learner's submitted tests to the account, so the administrator can follow results and mistakes.
// Runs after every submitted test, at start-up and when the connection comes back; already sent tests are skipped.
import { api, type ResultView } from './api';
import { CLOUD, currentSession, pushAttempt, type AttemptItem } from './cloud';
import { answerText } from './pages/ResultPage';

const sentKey = (uid: string) => `mm.cloud.sent.${uid}`;
const readSent = (uid: string): string[] => { try { return JSON.parse(localStorage.getItem(sentKey(uid)) ?? '[]'); } catch { return []; } };
const markSent = (uid: string, id: string) => { try { localStorage.setItem(sentKey(uid), JSON.stringify([...readSent(uid), id])); } catch { /* ignore */ } };

let running: Promise<void> | null = null;

export function syncAttempts(): Promise<void> {
  if (!CLOUD || !navigator.onLine) return Promise.resolve();
  running ??= run().catch(() => { /* try again next time */ }).finally(() => { running = null; });
  return running;
}

async function run() {
  const s = await currentSession();
  if (!s) return;
  const sent = new Set(readSent(s.user.id));
  const list = await api<{ id: string; status: string; startedAt: string }[]>('/api/student/attempts');
  for (const a of list.filter((x) => x.status === 'SUBMITTED' && !sent.has(x.id))) {
    const r = await api<ResultView>(`/api/student/attempts/${a.id}/result`);
    const items: AttemptItem[] = r.items.map((it) => ({
      n: it.position + 1, key: it.question.key, skill: it.question.skillTitle, prompt: it.question.prompt.text,
      options: it.question.prompt.options?.map((o) => `${o.id}) ${o.text}`),
      answer: answerText(it.answer, it.question.prompt.options), correct: it.correctAnswer, status: it.status ?? 'UNANSWERED',
      points: it.points, max: it.maxPoints, misconception: it.misconception, feedback: it.feedback,
    }));
    await pushAttempt(s, {
      id: a.id, test_key: r.testId ?? r.title, test_title: r.title, started_at: a.startedAt,
      submitted_at: r.submittedAt ?? new Date().toISOString(), points: r.points, max_points: r.maxPoints, percent: r.percent,
      grade: r.grade, grade_label: r.gradeLabel, items, misconceptions: r.misconceptions.map((m) => ({ label: m.label, count: m.count })),
    });
    markSent(s.user.id, a.id);
  }
}
