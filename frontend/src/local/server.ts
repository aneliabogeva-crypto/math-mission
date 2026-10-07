// On-device implementation of the student and guardian API (standalone mode: phones, tablets,
// GitHub Pages, Android app). Mirrors the Java services; data is stored only on this device.
import { check, MISCONCEPTION_BG, MISTAKE_LESSON, round, type AnswerKey, type Misconception, type Payload, type ResponseType, type Status } from './checker';
import contentJson from './data/content.json';
import curriculumJson from './data/curriculum.json';
import referenceJson from './data/reference.json';
import consentJson from './data/consent.json';

// ------------------------------------------------------------------ static content

interface Prompt { text: string; options?: { id: string; text: string }[]; parts?: { id: string; label: string; type: string; points: number }[]; startExpression?: string; inputHint?: string }
interface Draft { path: string; skill: string; learningOutcome: string; responseType: ResponseType; difficulty: string; estimatedSeconds: number;
  maxPoints: number; misconception: Misconception; prompt: Prompt; key: AnswerKey; hints: string[]; sourceDeclaration: string }
interface Question extends Draft { key_: string }
interface Stop { key: string; path: string; order: number; title: string; skill: string; learningOutcome: string; prerequisites: string[] }

const QUESTIONS = new Map<string, Question>(
  (contentJson as unknown as { questions: { key: string; draft: Draft }[] }).questions.map((q) => [q.key, { ...q.draft, key_: q.key }]));
const LESSONS = (contentJson as unknown as { lessons: { lessonKey: string; content: { [k: string]: unknown } & { prerequisiteCheck: { questionKeys: string[] }; guidedPractice: { questionKey: string }[]; check: { questionKeys: string[] } } }[] }).lessons;
const TESTS = (contentJson as unknown as { tests: { testKey: string; title: string; kind: string; path: string; timeLimitMin: number; hintPolicy: string; questionKeys: string[] }[] }).tests;
const CURRICULUM = curriculumJson as { paths: Record<string, string>; lessons: Stop[] };
const PATH_COLOUR: Record<string, string> = { A: 'blue', B: 'purple', C: 'coral', D: 'gold' };
const ACADEMIC_YEAR = '2026/2027';
const TEXT_VERSION = '2026-10-v1';
const LESSON_SECTIONS = 11;
const GRADING = [{ minPercent: 0, grade: 2, labelBg: 'Слаб' }, { minPercent: 40, grade: 3, labelBg: 'Среден' }, { minPercent: 55, grade: 4, labelBg: 'Добър' },
  { minPercent: 70, grade: 5, labelBg: 'Много добър' }, { minPercent: 85, grade: 6, labelBg: 'Отличен' }];
const NOT_OFFICIAL = 'Това е учебна оценка за подготовка. Тя не е официална училищна или държавна оценка.';
const NOT_NEA = 'Тестът е тематичен (алгебра) и не е пълна симулация на НВО след 7. клас.';
const AVATARS = ['fox', 'owl', 'rocket', 'cat', 'robot', 'planet', 'dragon', 'turtle'];

const stopBySkill = (skill: string) => CURRICULUM.lessons.find((l) => l.skill === skill);
const skillTitle = (skill: string) => stopBySkill(skill)?.title ?? skill;
const lessonKeys = new Set(LESSONS.flatMap((l) => [...l.content.prerequisiteCheck.questionKeys, ...l.content.guidedPractice.map((g) => g.questionKey), ...l.content.check.questionKeys]));
const reserved = new Set(TESTS.flatMap((t) => t.questionKeys)); // test items never appear in practice

function view(q: Question) {
  return { id: q.key_, key: q.key_, skill: q.skill, skillTitle: skillTitle(q.skill), responseType: q.responseType, prompt: q.prompt,
    maxPoints: q.maxPoints, estimatedSeconds: q.estimatedSeconds, hintLevels: q.hints.length, difficulty: q.difficulty };
}

// ------------------------------------------------------------------ persistence

interface User { id: string; role: 'STUDENT' | 'GUARDIAN'; status: 'ACTIVE' | 'PENDING_CONSENT' | 'RESTRICTED'; nickname: string; avatar?: string;
  ageBand?: string; goal?: string; confidence?: number; weeklyGoal: number; xp: number; createdAt: string; username?: string; passHash?: string; recovery?: string }
interface Consent { id: string; studentId: string; guardianId?: string; code: string; textVersion: string; scope: string; status: 'PENDING' | 'GRANTED' | 'WITHDRAWN';
  verification: string; notificationFrequency: string; createdAt: string; grantedAt?: string; withdrawnAt?: string }
interface LessonProg { studentId: string; lessonKey: string; position: number; maxPosition: number; status: 'IN_PROGRESS' | 'COMPLETED'; updatedAt: string }
interface Practice { requestId: string; studentId: string; questionKey: string; lessonKey?: string; correct: boolean; hintsUsed: number; misconception?: Misconception; correctsPrevious: boolean; createdAt: string }
interface Mastery { studentId: string; skill: string; score: number; attempts: number; correct: number; hintsUsed: number; consecutiveErrors: number;
  state: 'NOT_STARTED' | 'NEEDS_PRACTICE' | 'PRACTISING' | 'SECURE' | 'MASTERED'; reviewStage: number; nextReviewAt?: string; updatedAt: string }
interface Item { position: number; questionKey: string; answer?: Payload; marked: boolean; hintsUsed: number; lastRequestId?: string; savedAt?: string;
  status?: Status; points?: number; misconception?: Misconception; feedback?: string }
interface Attempt { id: string; testId: string; studentId: string; status: 'IN_PROGRESS' | 'SUBMITTED'; startedAt: string; deadlineAt: string; submittedAt?: string;
  lastPosition: number; points?: number; maxPoints?: number; percent?: number; grade?: number; scoringSource?: string; items: Item[] }
interface Reward { studentId: string; code: string; title: string; xp: number; sourceRef: string; createdAt: string }
interface DB { users: User[]; consents: Consent[]; lessons: LessonProg[]; practice: Practice[]; mastery: Mastery[]; attempts: Attempt[]; rewards: Reward[] }

const KEY = 'mm.local.v1';
let cache: DB | null = null;
function db(): DB {
  if (cache) return cache;
  try { cache = JSON.parse(localStorage.getItem(KEY) ?? 'null'); } catch { cache = null; }
  cache ??= { users: [], consents: [], lessons: [], practice: [], mastery: [], attempts: [], rewards: [] };
  return cache;
}
function save() { try { localStorage.setItem(KEY, JSON.stringify(cache)); } catch { /* storage full or blocked: keep in memory */ } }

const now = () => new Date().toISOString();
const uid = () => (crypto as Crypto & { randomUUID?: () => string }).randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const code = (n: number) => Array.from({ length: n }, () => CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)]).join('');
async function hash(s: string) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`mm:${s}`));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export class LocalError extends Error { constructor(public status: number, public code: string, message: string) { super(message); } }
const notFound = (what: string) => new LocalError(404, 'NOT_FOUND', `${what} не е намерен(а).`);
const bad = (c: string, m: string) => new LocalError(400, c, m);

// ------------------------------------------------------------------ progress, mastery, rewards

const REVIEW_DAYS = [1, 3, 7, 14];
function recordMastery(studentId: string, skill: string, correct: boolean, hints: number) {
  const d = db();
  let m = d.mastery.find((x) => x.studentId === studentId && x.skill === skill);
  if (!m) { m = { studentId, skill, score: 0, attempts: 0, correct: 0, hintsUsed: 0, consecutiveErrors: 0, state: 'NOT_STARTED', reviewStage: 0, updatedAt: now() }; d.mastery.push(m); }
  const before = m.state;
  const t = Date.now();
  const credit = correct ? Math.max(0.25, 1 - 0.25 * hints) : 0;
  m.score = m.attempts === 0 ? credit * 0.6 : 0.7 * m.score + 0.3 * credit;
  m.attempts++; m.hintsUsed += hints;
  if (correct) { m.correct++; m.consecutiveErrors = 0; } else m.consecutiveErrors++;
  const due = m.nextReviewAt != null && t >= Date.parse(m.nextReviewAt);
  if (m.score >= 0.8 && m.attempts >= 4) {
    if (m.state === 'SECURE' && due && correct && hints === 0) { m.reviewStage++; if (m.reviewStage >= REVIEW_DAYS.length) m.state = 'MASTERED'; }
    else if (m.state !== 'MASTERED' && m.state !== 'SECURE') { m.state = 'SECURE'; m.reviewStage = 0; }
    const idx = Math.min(m.reviewStage, REVIEW_DAYS.length - 1);
    if (due || !m.nextReviewAt) m.nextReviewAt = new Date(t + REVIEW_DAYS[idx] * 86400000).toISOString();
  } else if (m.score >= 0.4) m.state = 'PRACTISING';
  else m.state = 'NEEDS_PRACTICE';
  if (!correct && (m.state === 'MASTERED' || m.state === 'SECURE') && m.score < 0.8) { m.state = 'PRACTISING'; m.reviewStage = 0; m.nextReviewAt = new Date(t + 86400000).toISOString(); }
  m.updatedAt = now();
  if (before !== 'SECURE' && before !== 'MASTERED' && m.state === 'SECURE') grant(studentId, 'BADGE_SKILL', `Значка: ${skillTitle(skill)}`, 40, skill);
}

function grant(studentId: string, code_: string, title: string, xp: number, sourceRef: string) {
  const d = db();
  if (d.rewards.some((r) => r.studentId === studentId && r.code === code_ && r.sourceRef === sourceRef)) return;
  d.rewards.push({ studentId, code: code_, title, xp, sourceRef, createdAt: now() });
  const u = d.users.find((x) => x.id === studentId);
  if (u) u.xp += xp;
}

const level = (xp: number) => { let l = 1, need = 100; while (xp >= need) { xp -= need; l++; need += 50; } return l; };

// ------------------------------------------------------------------ handlers

type Handler = (ctx: { user?: User; body: any; params: string[]; query: URLSearchParams }) => unknown | Promise<unknown>;

function me(token: string | null): User | undefined {
  return token ? db().users.find((u) => u.id === token) : undefined;
}
function requireStudent(u?: User): User {
  if (!u) throw new LocalError(401, 'UNAUTHENTICATED', 'Влез в профила си.');
  if (u.role !== 'STUDENT' || u.status !== 'ACTIVE') throw new LocalError(403, 'FORBIDDEN', 'Нямаш достъп до това действие.');
  return u;
}
function requireGuardian(u?: User): User {
  if (!u) throw new LocalError(401, 'UNAUTHENTICATED', 'Влезте в профила си.');
  if (u.role !== 'GUARDIAN') throw new LocalError(403, 'FORBIDDEN', 'Нямате достъп до това действие.');
  return u;
}

function published(key: string): Question {
  const q = QUESTIONS.get(key);
  if (!q || reserved.has(key)) throw notFound('Задача');
  return q;
}

function correctAnswer(q: Question): string | undefined {
  const k = q.key;
  switch (q.responseType) {
    case 'SINGLE_CHOICE': { const o = q.prompt.options?.find((x) => x.id === k.correctOptionId); return o ? `${o.id}) ${o.text}` : k.correctOptionId; }
    case 'STRUCTURED': return k.parts?.map((p) => `${p.id}: ${p.answer.replace('.', ',')}`).join('; ');
    case 'FREE_TEXT': return k.rubric;
    default: return k.answer?.replace('.', ',');
  }
}

function similar(q: Question, studentId: string): string | undefined {
  const seen = new Set(db().practice.filter((p) => p.studentId === studentId).map((p) => p.questionKey));
  const c = [...QUESTIONS.values()].filter((x) => x.skill === q.skill && x.key_ !== q.key_ && !reserved.has(x.key_));
  c.sort((a, b) => Number(seen.has(a.key_)) - Number(seen.has(b.key_)) || Number(a.misconception !== q.misconception) - Number(b.misconception !== q.misconception) || a.key_.localeCompare(b.key_));
  return c[0]?.key_;
}

function lessonStatus(studentId: string) {
  return new Map(db().lessons.filter((l) => l.studentId === studentId).map((l) => [l.lessonKey, l]));
}

function mapZones(studentId: string) {
  const prog = lessonStatus(studentId);
  const published_ = new Set(LESSONS.map((l) => l.lessonKey));
  return Object.entries(CURRICULUM.paths).map(([path, title]) => ({
    path, title, colour: PATH_COLOUR[path],
    stops: CURRICULUM.lessons.filter((l) => l.path === path).map((l) => {
      const p = prog.get(l.key);
      const available = published_.has(l.key);
      let status = 'NOT_STARTED', label = 'Не е започнат', pct = 0;
      if (!available) { status = 'COMING_SOON'; label = 'Подготвя се'; }
      else if (p?.status === 'COMPLETED') { status = 'COMPLETED'; label = 'Завършен'; pct = 100; }
      else if (p) { status = 'IN_PROGRESS'; label = 'Започнат'; pct = Math.min(95, Math.round((p.maxPosition * 100) / LESSON_SECTIONS)); }
      const missing = l.prerequisites.filter((k) => published_.has(k) && prog.get(k)?.status !== 'COMPLETED');
      return { key: l.key, order: l.order, title: l.title, skill: l.skill, status, statusLabel: label, progressPercent: pct, available,
        prerequisites: l.prerequisites,
        recommendation: missing.length ? 'Препоръчваме първо: ' + missing.map((k) => `${k} ${CURRICULUM.lessons.find((x) => x.key === k)?.title ?? ''}`).join(', ') : undefined };
    }),
  }));
}

const kindLabel = (k: string) => (k === 'DIAGNOSTIC' ? 'Диагностичен тест' : k === 'INTEGRATED' ? 'Обобщителен тест' : 'Тематичен тест');

function testSummary(t: (typeof TESTS)[number], studentId: string) {
  const mine = db().attempts.filter((a) => a.studentId === studentId && a.testId === t.testKey);
  const percents = mine.map((a) => a.percent).filter((x): x is number => x != null);
  return { id: t.testKey, key: t.testKey, title: t.title, kind: t.kind, kindLabel: kindLabel(t.kind),
    path: t.path, questionCount: t.questionKeys.length, timeLimitMin: t.timeLimitMin, gradingBands: GRADING, hintPolicy: t.hintPolicy,
    scoringNote: NOT_OFFICIAL, scopeNote: NOT_NEA, inProgressAttemptId: mine.find((a) => a.status === 'IN_PROGRESS')?.id,
    bestPercent: percents.length ? Math.max(...percents) : undefined, completedAttempts: mine.filter((a) => a.status === 'SUBMITTED').length };
}

function ownedAttempt(user: User, id: string): Attempt {
  const a = db().attempts.find((x) => x.id === id);
  if (!a) throw notFound('Опит');
  if (a.studentId !== user.id) throw new LocalError(403, 'FORBIDDEN', 'Опитът не е твой.');
  return a;
}

function attemptView(a: Attempt) {
  const t = TESTS.find((x) => x.testKey === a.testId)!;
  return { attemptId: a.id, testId: a.testId, title: t.title, kindLabel: kindLabel(t.kind), status: a.status, startedAt: a.startedAt,
    deadlineAt: a.deadlineAt, serverNow: now(), lastPosition: a.lastPosition, hintPolicy: t.hintPolicy,
    items: a.items.map((it) => { const q = QUESTIONS.get(it.questionKey)!; return { position: it.position, question: view(q), answer: it.answer,
      markedForReview: it.marked, answered: it.answer != null, hintsUsed: it.hintsUsed, revealedHints: q.hints.slice(0, it.hintsUsed), lastRequestId: it.lastRequestId }; }) };
}

function resultView(a: Attempt) {
  const t = TESTS.find((x) => x.testKey === a.testId)!;
  const skill = new Map<string, [number, number]>();
  const misc = new Map<Misconception, number>();
  const items = a.items.map((it) => {
    const q = QUESTIONS.get(it.questionKey)!;
    const acc = skill.get(q.skill) ?? [0, 0]; acc[0] += it.points ?? 0; acc[1] += q.maxPoints; skill.set(q.skill, acc);
    if (it.misconception && it.status !== 'CORRECT') misc.set(it.misconception, (misc.get(it.misconception) ?? 0) + 1);
    return { position: it.position, question: view(q), answer: it.answer, status: it.status, points: it.points, maxPoints: q.maxPoints,
      feedback: it.feedback, misconception: it.misconception ? MISCONCEPTION_BG[it.misconception] : undefined, correctAnswer: correctAnswer(q),
      solution: q.key.solution, theoryLessonKey: stopBySkill(q.skill)?.key, scoredBy: 'Автоматична проверка' };
  });
  return { attemptId: a.id, title: t.title, kindLabel: kindLabel(t.kind), points: a.points, maxPoints: a.maxPoints, percent: a.percent, grade: a.grade,
    gradeLabel: GRADING.find((g) => g.grade === a.grade)?.labelBg, disclaimer: NOT_OFFICIAL, scoringSource: a.scoringSource,
    skills: [...skill.entries()].map(([s, [p, m]]) => ({ skill: s, title: skillTitle(s), points: round(p), maxPoints: m, percent: m ? Math.round((p / m) * 100) : 0, lessonKey: stopBySkill(s)?.key })),
    misconceptions: [...misc.entries()].sort((x, y) => y[1] - x[1]).map(([c, n]) => ({ code: c, label: MISCONCEPTION_BG[c], count: n, lessonKey: MISTAKE_LESSON[c] })),
    answerKeyVisible: true, items, corrections: [], submittedAt: a.submittedAt };
}

const SOFIA_DAY = (iso: string | number) => new Date(iso).toLocaleDateString('en-CA', { timeZone: 'Europe/Sofia' });
function weekStart(): Date {
  const today = new Date(new Date().toLocaleString('en-US', { timeZone: 'Europe/Sofia' }));
  const day = (today.getDay() + 6) % 7;
  today.setHours(0, 0, 0, 0); today.setDate(today.getDate() - day);
  return today;
}

function home(u: User) {
  const d = db();
  const attempts = d.attempts.filter((a) => a.studentId === u.id).sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  const lessons = d.lessons.filter((l) => l.studentId === u.id).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  const cands: { type: string; key?: string; id?: string; title: string; detail: string; updatedAt: string }[] = [];
  const ip = attempts.find((a) => a.status === 'IN_PROGRESS');
  if (ip) cands.push({ type: 'TEST', id: ip.id, title: TESTS.find((t) => t.testKey === ip.testId)?.title ?? 'Тест', detail: `Въпрос ${ip.lastPosition + 1}`, updatedAt: ip.startedAt });
  const il = lessons.find((l) => l.status === 'IN_PROGRESS');
  if (il) cands.push({ type: 'LESSON', key: il.lessonKey, title: CURRICULUM.lessons.find((x) => x.key === il.lessonKey)?.title ?? il.lessonKey, detail: `Част ${il.position + 1} от ${LESSON_SECTIONS}`, updatedAt: il.updatedAt });
  cands.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  const chip = (m: Mastery) => ({ skill: m.skill, title: skillTitle(m.skill), state: m.state,
    stateLabel: { MASTERED: 'Усвоено', SECURE: 'Затвърдено', PRACTISING: 'Упражнява се', NEEDS_PRACTICE: 'Има нужда от упражнение', NOT_STARTED: 'Не е започнато' }[m.state],
    percent: Math.round(m.score * 100), lessonKey: stopBySkill(m.skill)?.key });
  const mastery = d.mastery.filter((m) => m.studentId === u.id);
  const secure = mastery.filter((m) => m.state === 'SECURE' || m.state === 'MASTERED').map(chip);
  const practise = mastery.filter((m) => m.state === 'NEEDS_PRACTICE' || m.state === 'PRACTISING').sort((a, b) => a.score - b.score).map(chip);
  const next = mapZones(u.id).flatMap((z) => z.stops).find((s) => s.available && s.status !== 'COMPLETED');
  const recommended = practise[0]?.lessonKey ? { type: 'PRACTICE', key: practise[0].lessonKey, title: `Упражни: ${practise[0].title}`, reason: 'Това умение още се затвърждава — 10 минути ще помогнат.' }
    : next ? { type: 'LESSON', key: next.key, title: next.title, reason: 'Следващата спирка на картата.' }
    : { type: 'REVISION', title: 'Смесен преговор', reason: 'Поддържай наученото свежо.' };
  const tests = TESTS.map((t) => testSummary(t, u.id));
  const ws = weekStart();
  const active = new Set<string>();
  d.practice.filter((p) => p.studentId === u.id && Date.parse(p.createdAt) >= ws.getTime()).forEach((p) => active.add(SOFIA_DAY(p.createdAt)));
  lessons.filter((l) => Date.parse(l.updatedAt) >= ws.getTime()).forEach((l) => active.add(SOFIA_DAY(l.updatedAt)));
  attempts.filter((a) => Date.parse(a.startedAt) >= ws.getTime()).forEach((a) => active.add(SOFIA_DAY(a.startedAt)));
  const days = Array.from({ length: 7 }, (_, i) => active.has(SOFIA_DAY(ws.getTime() + i * 86400000 + 43200000)));
  const left = u.weeklyGoal - active.size;
  const rewards = d.rewards.filter((r) => r.studentId === u.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const rv = (r: Reward) => ({ code: r.code, title: r.title, xp: r.xp, earnedAt: r.createdAt, badge: r.code.startsWith('BADGE_') });
  return {
    profile: { id: u.id, nickname: u.nickname, avatar: u.avatar, goal: u.goal, xp: u.xp, level: level(u.xp), weeklyGoal: u.weeklyGoal, status: u.status },
    continueAction: cands[0], recommended, secureSkills: secure, practiceSkills: practise,
    nextTest: tests.find((t) => t.completedAttempts === 0) ?? tests[0], offerDiagnostic: false,
    weeklyGoal: { target: u.weeklyGoal, activeDays: active.size, days, message: left <= 0 ? 'Седмичната цел е изпълнена. Браво!' : `Още ${left} ${left === 1 ? 'ден' : 'дни'} с учене тази седмица.` },
    assignments: [], recentRewards: rewards.slice(0, 5).map(rv), badges: rewards.filter((r) => r.code.startsWith('BADGE_')).map(rv), corrections: [],
    standalone: true,
  };
}

function plan(u: User) {
  const mastery = db().mastery.filter((m) => m.studentId === u.id);
  const weak = mastery.filter((m) => m.state === 'NEEDS_PRACTICE' || m.state === 'PRACTISING').sort((a, b) => a.score - b.score);
  const review = mastery.filter((m) => m.nextReviewAt).sort((a, b) => a.nextReviewAt!.localeCompare(b.nextReviewAt!));
  const out = [];
  const today = new Date();
  for (let d = 0; d < 7; d++) {
    const date = SOFIA_DAY(today.getTime() + d * 86400000);
    const tasks: { kind: string; skill?: string; title: string; lessonKey?: string; minutes: number; reason: string }[] = [];
    if (weak.length) {
      const w = weak[d % weak.length];
      const stop = stopBySkill(w.skill);
      if (w.consecutiveErrors >= 3 && stop?.prerequisites.length) {
        const pre = stop.prerequisites[0];
        tasks.push({ kind: 'PREREQUISITE', skill: w.skill, title: `Преговор: ${CURRICULUM.lessons.find((x) => x.key === pre)?.title ?? pre}`, lessonKey: pre, minutes: 10,
          reason: 'Няколко поредни грешки: първо затвърди основата, без по-трудни задачи.' });
      } else tasks.push({ kind: 'PRACTICE', skill: w.skill, title: `Упражнение: ${skillTitle(w.skill)}`, lessonKey: stop?.key, minutes: 10, reason: 'Умение, което още се затвърждава.' });
    }
    review.filter((m) => { const due = SOFIA_DAY(m.nextReviewAt!); return d === 0 ? due <= date : due === date; }).slice(0, 2)
      .forEach((m) => tasks.push({ kind: 'SPACED_REVIEW', skill: m.skill, title: `Кратък преговор: ${skillTitle(m.skill)}`, lessonKey: stopBySkill(m.skill)?.key, minutes: 5,
        reason: 'Повторение след няколко дни помага да запомниш трайно.' }));
    if (d % 3 === 2) tasks.push({ kind: 'MIXED', title: 'Смесени задачи (5 бр.)', minutes: 5, reason: 'Смесеният преговор държи всички теми свежи.' });
    if (!tasks.length) tasks.push({ kind: 'EXPLORE', title: 'Продължи по картата с нов урок', minutes: 15, reason: 'Няма слаби умения за днес — продължи напред!' });
    out.push({ date, tasks });
  }
  return out;
}

function mistakes(u: User) {
  const d = db();
  const mine = d.practice.filter((p) => p.studentId === u.id);
  const fixed = new Set(mine.filter((p) => p.correctsPrevious).map((p) => p.questionKey));
  const counts = new Map<Misconception, [number, number]>();
  const skills = new Map<Misconception, Set<string>>();
  for (const p of mine) {
    if (p.correct || !p.misconception) continue;
    const c = counts.get(p.misconception) ?? [0, 0];
    if (fixed.has(p.questionKey)) c[1]++; else c[0]++;
    counts.set(p.misconception, c);
    const s = skills.get(p.misconception) ?? new Set(); s.add(QUESTIONS.get(p.questionKey)?.skill ?? ''); skills.set(p.misconception, s);
  }
  for (const a of d.attempts.filter((x) => x.studentId === u.id && x.status === 'SUBMITTED')) {
    for (const it of a.items) if (it.misconception && it.status !== 'CORRECT') { const c = counts.get(it.misconception) ?? [0, 0]; c[0]++; counts.set(it.misconception, c); }
  }
  return [...counts.entries()].map(([m, [open, corrected]]) => {
    const lessonKey = MISTAKE_LESSON[m];
    const practiceKeys = [...QUESTIONS.values()].filter((q) => !reserved.has(q.key_) && (q.misconception === m || skills.get(m)?.has(q.skill)))
      .map((q) => q.key_).sort().slice(0, 5);
    return { code: m, label: MISCONCEPTION_BG[m], open, corrected, lessonKey, lessonTitle: CURRICULUM.lessons.find((x) => x.key === lessonKey)?.title ?? lessonKey, practiceKeys };
  }).sort((a, b) => b.open - a.open);
}

function score(a: Attempt) {
  let pts = 0, max = 0;
  for (const it of a.items) {
    const q = QUESTIONS.get(it.questionKey)!;
    const r = check(q.responseType, q.key, q.maxPoints, it.answer);
    it.status = r.status; it.points = r.points; it.misconception = r.misconception; it.feedback = r.message;
    pts += r.points; max += q.maxPoints;
    recordMastery(a.studentId, q.skill, r.status === 'CORRECT', it.hintsUsed);
  }
  a.points = round(pts); a.maxPoints = max;
  a.percent = max ? Math.round((pts / max) * 1000) / 10 : 0;
  let g = GRADING[0]; for (const b of GRADING) if (a.percent + 1e-9 >= b.minPercent) g = b;
  a.grade = g.grade; a.scoringSource = 'AUTOMATED';
}

function guardianSummary(c: Consent) {
  const d = db();
  const s = d.users.find((u) => u.id === c.studentId)!;
  const ws = weekStart().getTime();
  const days = new Set<string>();
  const answers = d.practice.filter((p) => p.studentId === s.id && Date.parse(p.createdAt) >= ws);
  answers.forEach((p) => days.add(SOFIA_DAY(p.createdAt)));
  const lessons = d.lessons.filter((l) => l.studentId === s.id);
  lessons.filter((l) => Date.parse(l.updatedAt) >= ws).forEach((l) => days.add(SOFIA_DAY(l.updatedAt)));
  const tests = d.attempts.filter((a) => a.studentId === s.id && a.status === 'SUBMITTED' && Date.parse(a.submittedAt!) >= ws);
  tests.forEach((a) => days.add(SOFIA_DAY(a.submittedAt!)));
  const mastery = d.mastery.filter((m) => m.studentId === s.id);
  const support = mastery.filter((m) => m.state === 'NEEDS_PRACTICE').map((m) => skillTitle(m.skill));
  const suggestions: string[] = [];
  if (support.length) suggestions.push(`Помолете детето да ви обясни как решава задача от „${support[0]}“. Обяснението на глас помага да се открие грешката.`);
  if (days.size < 2) suggestions.push('Кратки сесии от 10–15 минути, два-три пъти седмично, работят по-добре от едно дълго учене.');
  suggestions.push('Похвалете усилието и поправените грешки, а не само резултата.');
  return { consentId: c.id, studentId: s.id, nickname: s.nickname, accountStatus: s.status, consentStatus: c.status, consentVersion: c.textVersion,
    consentGrantedAt: c.grantedAt, verificationStatus: c.verification, notificationFrequency: c.notificationFrequency, weekStart: new Date(ws).toISOString(),
    activeDays: days.size, lessonsCompletedThisWeek: lessons.filter((l) => l.status === 'COMPLETED' && Date.parse(l.updatedAt) >= ws).length,
    practiceAnswersThisWeek: answers.length, testsThisWeek: tests.map((a) => `${TESTS.find((t) => t.testKey === a.testId)?.title}: ${String(a.percent).replace('.', ',')}%`),
    secureSkills: mastery.filter((m) => m.state === 'SECURE' || m.state === 'MASTERED').map((m) => skillTitle(m.skill)), supportAreas: support, suggestions };
}

const ROUTES: [string, RegExp, Handler][] = [
  // ---------------- identity
  ['POST', /^\/api\/auth\/student$/, async ({ body }) => {
    const nick = String(body.nickname ?? '').trim();
    if (nick.length < 2 || nick.length > 24 || !/^[\p{L}\p{N} _.-]+$/u.test(nick)) throw bad('VALIDATION', 'Прякорът трябва да е от 2 до 24 букви или цифри.');
    if (!AVATARS.includes(body.avatar)) throw bad('AVATAR', 'Избери аватар от списъка.');
    const recovery = `${code(6)}-${code(10)}`;
    const u: User = { id: uid(), role: 'STUDENT', status: body.ageBand === 'UNDER_14' ? 'PENDING_CONSENT' : 'ACTIVE', nickname: nick, avatar: body.avatar,
      ageBand: body.ageBand, goal: body.goal, confidence: body.confidence, weeklyGoal: 3, xp: 0, createdAt: now(), recovery: await hash(recovery) };
    db().users.push(u);
    let consentCode: string | undefined;
    if (u.status === 'PENDING_CONSENT') {
      consentCode = code(8);
      db().consents.push({ id: uid(), studentId: u.id, code: consentCode, textVersion: TEXT_VERSION, scope: consentJson.scope, status: 'PENDING',
        verification: 'NOT_VERIFIED', notificationFrequency: 'WEEKLY', createdAt: now() });
    }
    return { token: u.id, recoveryCode: recovery, status: u.status, consentCode };
  }],
  ['POST', /^\/api\/auth\/student\/recover$/, async ({ body }) => {
    const h = await hash(String(body.recoveryCode ?? '').trim().toUpperCase());
    const u = db().users.find((x) => x.recovery === h);
    if (!u) throw new LocalError(401, 'INVALID_LOGIN', 'Невалиден код. Кодът работи само на устройството, на което е създаден профилът.');
    return { token: u.id, role: u.role, status: u.status };
  }],
  ['POST', /^\/api\/auth\/login$/, async ({ body }) => {
    const u = db().users.find((x) => x.role === 'GUARDIAN' && x.username === body.username);
    if (!u || u.passHash !== await hash(`${body.username}:${body.password}`)) {
      throw new LocalError(401, 'INVALID_LOGIN', 'Невалидни данни за вход. Учителските профили са достъпни само в училищната версия.');
    }
    return { token: u.id, role: u.role, status: u.status };
  }],
  ['GET', /^\/api\/me$/, ({ user }) => {
    if (!user) throw new LocalError(401, 'UNAUTHENTICATED', 'Влез в профила си.');
    const c = db().consents.filter((x) => x.studentId === user.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
    return { id: user.id, role: user.role, status: user.status, displayName: user.nickname, avatar: user.avatar, mfa: false,
      ...(user.role === 'STUDENT' && user.status !== 'ACTIVE' && c ? { consentCode: c.status === 'PENDING' ? c.code : undefined, consentStatus: c.status } : {}) };
  }],
  ['POST', /^\/api\/me\/logout$/, () => ({ status: 'ok' })],

  // ---------------- consent (on the same device)
  ['GET', /^\/api\/consent\/text\/withdrawal-consequences$/, () => consentJson.withdrawal],
  ['GET', /^\/api\/consent\/text\/(adult|child)$/, ({ params }) => {
    const t = params[0] === 'child' ? consentJson.child : consentJson.adult;
    return { version: TEXT_VERSION, audience: params[0].toUpperCase(), title: t.title, paragraphs: t.paragraphs };
  }],
  ['GET', /^\/api\/consent\/code\/([A-Za-z0-9]+)$/, ({ params }) => {
    const c = db().consents.find((x) => x.code === params[0].toUpperCase());
    if (!c) throw new LocalError(404, 'CODE_NOT_FOUND', 'Кодът не е намерен.');
    return { studentNickname: db().users.find((u) => u.id === c.studentId)?.nickname, status: c.status, textVersion: c.textVersion, scope: c.scope };
  }],
  ['POST', /^\/api\/consent\/code\/([A-Za-z0-9]+)\/grant$/, async ({ params, body, user }) => {
    const d = db();
    const c = d.consents.find((x) => x.code === params[0].toUpperCase());
    if (!c) throw new LocalError(404, 'CODE_NOT_FOUND', 'Кодът не е намерен.');
    if (c.status === 'GRANTED') throw new LocalError(409, 'ALREADY_GRANTED', 'Съгласието вече е дадено.');
    if (body.acceptedVersion !== c.textVersion) throw bad('VERSION_MISMATCH', 'Текстът на съгласието е обновен. Моля, прочетете новата версия.');
    let guardian = user?.role === 'GUARDIAN' ? user : undefined;
    let token: string | undefined;
    if (!guardian) {
      if (!body.username || body.username.length < 4 || !body.password || body.password.length < 10) {
        throw bad('WEAK_CREDENTIALS', 'Изберете потребителско име (поне 4 знака) и парола (поне 10 знака).');
      }
      if (d.users.some((u) => u.username === body.username)) throw new LocalError(409, 'USERNAME_TAKEN', 'Това потребителско име е заето.');
      guardian = { id: uid(), role: 'GUARDIAN', status: 'ACTIVE', nickname: 'Родител', weeklyGoal: 0, xp: 0, createdAt: now(), username: body.username,
        passHash: await hash(`${body.username}:${body.password}`) };
      d.users.push(guardian);
      token = guardian.id;
    }
    c.guardianId = guardian.id; c.status = 'GRANTED'; c.verification = 'GUARDIAN_ON_SAME_DEVICE_SELF_DECLARED'; c.grantedAt = now();
    const s = d.users.find((u) => u.id === c.studentId); if (s) s.status = 'ACTIVE';
    return token ? { status: 'GRANTED', token } : { status: 'GRANTED' };
  }],

  // ---------------- guardian
  ['GET', /^\/api\/guardian\/children$/, ({ user }) => { const g = requireGuardian(user); return db().consents.filter((c) => c.guardianId === g.id).map(guardianSummary); }],
  ['POST', /^\/api\/guardian\/consents\/([\w-]+)\/withdraw$/, ({ user, params }) => {
    const g = requireGuardian(user);
    const c = db().consents.find((x) => x.id === params[0] && x.guardianId === g.id);
    if (!c || c.status !== 'GRANTED') throw new LocalError(409, 'NOT_GRANTED', 'Няма активно съгласие за оттегляне.');
    c.status = 'WITHDRAWN'; c.withdrawnAt = now();
    const s = db().users.find((u) => u.id === c.studentId); if (s) s.status = 'RESTRICTED';
    return { status: 'WITHDRAWN', consequences: consentJson.withdrawal };
  }],
  ['PUT', /^\/api\/guardian\/consents\/([\w-]+)\/notifications$/, ({ user, params, body }) => {
    const g = requireGuardian(user);
    const c = db().consents.find((x) => x.id === params[0] && x.guardianId === g.id); if (!c) throw notFound('Съгласие');
    c.notificationFrequency = body.frequency; return { frequency: body.frequency };
  }],
  ['GET', /^\/api\/guardian\/consents\/([\w-]+)\/export$/, ({ user, params }) => {
    const g = requireGuardian(user);
    const c = db().consents.find((x) => x.id === params[0] && x.guardianId === g.id); if (!c) throw notFound('Съгласие');
    const d = db(); const id = c.studentId;
    const { recovery: _r, ...profile } = d.users.find((u) => u.id === id)!;
    return { exportedAt: now(), profile, consent: c, skills: d.mastery.filter((m) => m.studentId === id), lessons: d.lessons.filter((l) => l.studentId === id),
      tests: d.attempts.filter((a) => a.studentId === id).map(({ items: _i, ...a }) => a), practiceAnswers: d.practice.filter((p) => p.studentId === id).length,
      rewards: d.rewards.filter((r) => r.studentId === id) };
  }],
  ['POST', /^\/api\/guardian\/consents\/([\w-]+)\/deletion-request$/, ({ user, params }) => {
    const g = requireGuardian(user);
    const d = db();
    const c = d.consents.find((x) => x.id === params[0] && x.guardianId === g.id); if (!c) throw notFound('Съгласие');
    const id = c.studentId;
    // On a single device the deletion is carried out immediately.
    d.users = d.users.filter((u) => u.id !== id); d.lessons = d.lessons.filter((x) => x.studentId !== id); d.practice = d.practice.filter((x) => x.studentId !== id);
    d.mastery = d.mastery.filter((x) => x.studentId !== id); d.attempts = d.attempts.filter((x) => x.studentId !== id); d.rewards = d.rewards.filter((x) => x.studentId !== id);
    d.consents = d.consents.filter((x) => x.id !== c.id);
    return { status: 'COMPLETED', message: 'Данните на детето бяха изтрити от това устройство.' };
  }],

  // ---------------- student
  ['GET', /^\/api\/student\/home$/, ({ user }) => home(requireStudent(user))],
  ['PATCH', /^\/api\/student\/profile$/, ({ user, body }) => {
    const u = requireStudent(user);
    if (body.avatar) { if (!AVATARS.includes(body.avatar)) throw bad('AVATAR', 'Избери аватар от списъка.'); u.avatar = body.avatar; }
    if (body.goal) u.goal = body.goal;
    if (body.weeklyGoal) u.weeklyGoal = body.weeklyGoal;
    if (body.confidence) u.confidence = body.confidence;
    return home(u).profile;
  }],
  ['GET', /^\/api\/student\/map$/, ({ user }) => mapZones(requireStudent(user).id)],
  ['GET', /^\/api\/student\/plan$/, ({ user }) => plan(requireStudent(user))],
  ['GET', /^\/api\/student\/lessons\/([\w-]+)$/, ({ user, params }) => {
    const u = requireStudent(user);
    const l = LESSONS.find((x) => x.lessonKey === params[0]); if (!l) throw notFound('Урок');
    const stop = CURRICULUM.lessons.find((x) => x.key === l.lessonKey)!;
    const keys = [...l.content.prerequisiteCheck.questionKeys, ...l.content.guidedPractice.map((g) => g.questionKey), ...l.content.check.questionKeys];
    const questions = Object.fromEntries(keys.filter((k) => QUESTIONS.has(k)).map((k) => [k, view(QUESTIONS.get(k)!)]));
    const p = lessonStatus(u.id).get(l.lessonKey);
    return { key: l.lessonKey, path: stop.path, title: stop.title, version: 1, academicYear: ACADEMIC_YEAR, learningOutcome: stop.learningOutcome,
      content: l.content, questions, position: p?.position ?? 0, maxPosition: p?.maxPosition ?? 0, status: p?.status ?? 'NOT_STARTED' };
  }],
  ['PUT', /^\/api\/student\/lessons\/([\w-]+)\/position$/, ({ user, params, body }) => {
    const u = requireStudent(user);
    if (!LESSONS.some((l) => l.lessonKey === params[0])) throw notFound('Урок');
    const d = db();
    let p = d.lessons.find((x) => x.studentId === u.id && x.lessonKey === params[0]);
    if (!p) { p = { studentId: u.id, lessonKey: params[0], position: 0, maxPosition: 0, status: 'IN_PROGRESS', updatedAt: now() }; d.lessons.push(p); }
    p.position = body.position; p.maxPosition = Math.max(p.maxPosition, body.position); p.updatedAt = now();
    if (body.completed && p.status !== 'COMPLETED') {
      p.status = 'COMPLETED';
      grant(u.id, 'LESSON_COMPLETED', 'Завършен урок', 50, params[0]);
      grant(u.id, 'BADGE_FIRST_LESSON', 'Значка: Първа мисия', 20, '1');
    }
    return { lessonKey: params[0], position: p.position, maxPosition: p.maxPosition, status: p.status };
  }],
  ['GET', /^\/api\/student\/practice\/([\w-]+)$/, ({ user, params }) => { requireStudent(user); return view(published(params[0])); }],
  ['GET', /^\/api\/student\/practice\/([\w-]+)\/hints\/(\d)$/, ({ user, params }) => {
    requireStudent(user);
    const q = published(params[0]); const level_ = Number(params[1]);
    if (level_ < 1 || level_ > q.hints.length) throw bad('HINT_LEVEL', 'Няма такава подсказка.');
    return { level: level_, text: q.hints[level_ - 1] };
  }],
  ['POST', /^\/api\/student\/practice\/([\w-]+)\/check$/, ({ user, params, body }) => {
    const u = requireStudent(user);
    const q = published(params[0]);
    const r = check(q.responseType, q.key, q.maxPoints, body.answer);
    const d = db();
    const existing = d.practice.find((p) => p.requestId === body.requestId);
    let corrected = existing?.correctsPrevious ?? false;
    if (!existing && r.status !== 'INVALID_INPUT' && r.status !== 'UNANSWERED') {
      const earlier = d.practice.filter((p) => p.studentId === u.id && p.questionKey === q.key_);
      corrected = r.status === 'CORRECT' && earlier.some((p) => !p.correct) && !earlier.some((p) => p.correctsPrevious);
      const hints = Math.max(0, Math.min(3, Number(body.hintsUsed) || 0));
      d.practice.push({ requestId: body.requestId, studentId: u.id, questionKey: q.key_, lessonKey: body.lessonKey, correct: r.status === 'CORRECT', hintsUsed: hints,
        misconception: r.misconception, correctsPrevious: corrected, createdAt: now() });
      recordMastery(u.id, q.skill, r.status === 'CORRECT', hints);
      if (r.status === 'CORRECT') grant(u.id, 'PRACTICE_CORRECT', 'Вярна задача', hints === 0 ? 10 : 5, body.requestId);
      if (corrected) {
        grant(u.id, 'CORRECTED_MISTAKE', 'Поправена грешка', 20, q.key_);
        if (d.rewards.filter((x) => x.studentId === u.id && x.code === 'CORRECTED_MISTAKE').length >= 5) grant(u.id, 'BADGE_FIXER', 'Значка: Ловец на грешки', 50, '5');
      }
    }
    const show = r.status !== 'INVALID_INPUT' && r.status !== 'UNANSWERED';
    const stop = stopBySkill(q.skill);
    return { questionKey: q.key_, status: r.status, correct: r.status === 'CORRECT',
      message: corrected ? `${r.message} Поправи грешка, която беше допуснал(а) по-рано — точно така се учи!` : r.message,
      misconception: r.misconception, misconceptionLabel: r.misconception ? MISCONCEPTION_BG[r.misconception] : undefined, errorStep: r.errorStep, parts: r.parts,
      correctAnswer: show ? correctAnswer(q)?.replace(/^[a-z]\) /, '') : undefined, solution: show ? q.key.solution : undefined,
      theoryLessonKey: stop?.key, theoryLessonTitle: stop?.title, similarQuestionKey: r.status === 'CORRECT' ? undefined : similar(q, u.id), correctedMistake: corrected };
  }],
  ['GET', /^\/api\/student\/mistakes$/, ({ user }) => mistakes(requireStudent(user))],
  ['GET', /^\/api\/student\/reference$/, ({ user, query }) => {
    requireStudent(user);
    const q = (query.get('q') ?? '').trim().toLowerCase(); const path = query.get('path') ?? '';
    return (referenceJson as { path: string; title: string; meaning: string; formula: string; example: string }[])
      .filter((e) => !path || e.path === path).filter((e) => !q || `${e.title} ${e.meaning} ${e.formula} ${e.example}`.toLowerCase().includes(q));
  }],
  ['GET', /^\/api\/student\/tests$/, ({ user }) => { const u = requireStudent(user); return TESTS.map((t) => testSummary(t, u.id)); }],
  ['POST', /^\/api\/student\/tests\/([\w-]+)\/attempts$/, ({ user, params, body }) => {
    const u = requireStudent(user);
    const d = db();
    const id = body?.attemptId ?? uid();
    const existing = d.attempts.find((a) => a.id === id);
    if (existing) return attemptView(ownedAttempt(u, id)); // retried start: no duplicate attempt
    const t = TESTS.find((x) => x.testKey === params[0]); if (!t) throw notFound('Тест');
    const open = d.attempts.find((a) => a.studentId === u.id && a.testId === t.testKey && a.status === 'IN_PROGRESS');
    if (open) throw new LocalError(409, `ATTEMPT_IN_PROGRESS:${open.id}`, 'Вече имаш започнат опит. Продължи от мястото, където спря.');
    const order = [...t.questionKeys];
    for (let i = order.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [order[i], order[j]] = [order[j], order[i]]; }
    const a: Attempt = { id, testId: t.testKey, studentId: u.id, status: 'IN_PROGRESS', startedAt: now(), deadlineAt: new Date(Date.now() + t.timeLimitMin * 60000).toISOString(),
      lastPosition: 0, items: order.map((k, i) => ({ position: i, questionKey: k, marked: false, hintsUsed: 0 })) };
    d.attempts.push(a);
    return attemptView(a);
  }],
  ['GET', /^\/api\/student\/attempts$/, ({ user }) => {
    const u = requireStudent(user);
    return db().attempts.filter((a) => a.studentId === u.id).map((a) => ({ id: a.id, testId: a.testId, status: a.status, startedAt: a.startedAt, percent: a.percent ?? '' }));
  }],
  ['GET', /^\/api\/student\/attempts\/([\w-]+)$/, ({ user, params }) => attemptView(ownedAttempt(requireStudent(user), params[0]))],
  ['PUT', /^\/api\/student\/attempts\/([\w-]+)\/items\/(\d+)$/, ({ user, params, body }) => {
    const a = ownedAttempt(requireStudent(user), params[0]);
    const it = a.items.find((x) => x.position === Number(params[1])); if (!it) throw notFound('Въпрос');
    if (it.lastRequestId === body.requestId) return { position: it.position, savedAt: it.savedAt, requestId: body.requestId, duplicate: true };
    if (a.status === 'SUBMITTED') throw new LocalError(409, 'ATTEMPT_SUBMITTED', 'Тестът вече е предаден.');
    if (Date.now() > Date.parse(a.deadlineAt) + 5 * 60000) throw new LocalError(409, 'TIME_OVER', 'Времето за теста изтече. Предай теста, за да видиш резултата.');
    it.answer = body.answer; it.marked = Boolean(body.markedForReview); it.lastRequestId = body.requestId; it.savedAt = now();
    if (body.lastPosition != null) a.lastPosition = body.lastPosition;
    return { position: it.position, savedAt: it.savedAt, requestId: body.requestId, duplicate: false };
  }],
  ['POST', /^\/api\/student\/attempts\/([\w-]+)\/items\/(\d+)\/hints\/(\d)$/, ({ user, params }) => {
    const a = ownedAttempt(requireStudent(user), params[0]);
    const t = TESTS.find((x) => x.testKey === a.testId)!;
    if (t.hintPolicy !== 'ALLOWED') throw new LocalError(403, 'FORBIDDEN', 'В този тест подсказките са изключени.');
    const it = a.items.find((x) => x.position === Number(params[1]))!;
    const q = QUESTIONS.get(it.questionKey)!; const lv = Number(params[2]);
    if (lv > it.hintsUsed + 1) throw bad('HINT_ORDER', 'Първо отвори предишната подсказка.');
    it.hintsUsed = Math.max(it.hintsUsed, lv);
    return { level: lv, text: q.hints[lv - 1] };
  }],
  ['POST', /^\/api\/student\/attempts\/([\w-]+)\/submit$/, ({ user, params }) => {
    const u = requireStudent(user);
    const a = ownedAttempt(u, params[0]);
    if (a.status !== 'SUBMITTED') {
      a.status = 'SUBMITTED'; a.submittedAt = now();
      score(a);
      grant(u.id, 'TEST_COMPLETED', `Завършен тест: ${TESTS.find((t) => t.testKey === a.testId)?.title}`, 60, a.id);
    }
    return resultView(a);
  }],
  ['GET', /^\/api\/student\/attempts\/([\w-]+)\/result$/, ({ user, params }) => {
    const a = ownedAttempt(requireStudent(user), params[0]);
    if (a.status !== 'SUBMITTED') throw new LocalError(409, 'NOT_SUBMITTED', 'Тестът още не е предаден.');
    return resultView(a);
  }],
  ['POST', /^\/api\/student\/classes\/join$/, () => { throw bad('NOT_AVAILABLE', 'Класовете са достъпни в училищната версия на Math Mission.'); }],
];

/** Routes an API call to the on-device implementation. */
export async function handleLocal(method: string, url: string, body: unknown, token: string | null): Promise<unknown> {
  const u = new URL(url, 'http://local');
  for (const [m, re, h] of ROUTES) {
    if (m !== method) continue;
    const match = re.exec(u.pathname);
    if (!match) continue;
    const result = await h({ user: me(token), body: body ?? {}, params: match.slice(1), query: u.searchParams });
    save();
    return result;
  }
  throw new LocalError(404, 'NOT_AVAILABLE', 'Тази функция е достъпна само в училищната (сървърната) версия.');
}

export const STANDALONE_NOTE = 'Версия за телефон: данните се пазят само на това устройство.';
export { lessonKeys };
