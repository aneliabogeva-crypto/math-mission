// Thin API client. All authorisation is enforced by the server; the token is an opaque bearer token.

export type Role = 'STUDENT' | 'TEACHER' | 'GUARDIAN' | 'AUTHOR' | 'REVIEWER' | 'ADMIN';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

const TOKEN_KEY = 'mm.token';

export function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token); else localStorage.removeItem(TOKEN_KEY);
  } catch { /* storage unavailable: session-only login */ }
}

/** Phone/standalone build: the API runs on the device (no server, works offline). */
export const STANDALONE = import.meta.env.VITE_STANDALONE === '1';

export async function api<T>(path: string, init: { method?: string; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
  if (STANDALONE) {
    const { handleLocal, LocalError } = await import('./local/server');
    try {
      return (await handleLocal(init.method ?? 'GET', path, init.body, getToken())) as T;
    } catch (e) {
      if (e instanceof LocalError) throw new ApiError(e.status, e.code, e.message);
      throw new ApiError(500, 'ERROR', 'Нещо се обърка. Опитай отново.');
    }
  }
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (init.body !== undefined) headers['Content-Type'] = 'application/json';
  let res: Response;
  try {
    res = await fetch(path, {
      method: init.method ?? 'GET',
      headers,
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      signal: init.signal,
    });
  } catch {
    throw new ApiError(0, 'OFFLINE', 'Няма връзка с интернет.');
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? 'Нещо се обърка. Опитай отново.');
  }
  return data as T;
}

export const uuid = (): string =>
  (crypto as Crypto & { randomUUID?: () => string }).randomUUID?.() ??
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });

// ------------------------------------------------------------------ shared types

export type ResponseType = 'SINGLE_CHOICE' | 'NUMERIC' | 'EXPRESSION' | 'STEPS' | 'STRUCTURED' | 'FREE_TEXT';

export interface Option { id: string; text: string }
export interface PartPrompt { id: string; label: string; type: ResponseType; points: number }
export interface Prompt { text: string; options?: Option[]; parts?: PartPrompt[]; startExpression?: string; inputHint?: string }

export interface QuestionView {
  id: string; key: string; skill: string; skillTitle: string; responseType: ResponseType; prompt: Prompt;
  maxPoints: number; estimatedSeconds: number; hintLevels: number; difficulty: string;
}

export interface AnswerPayload { optionId?: string; value?: string; steps?: string[]; parts?: Record<string, string>; text?: string }

export interface PartResult { id: string; status: string; points: number; message: string }

export interface Feedback {
  questionKey: string; status: string; correct: boolean; message: string; misconception?: string; misconceptionLabel?: string;
  errorStep?: number; parts?: PartResult[]; correctAnswer?: string; solution?: string; theoryLessonKey?: string;
  theoryLessonTitle?: string; similarQuestionKey?: string; correctedMistake: boolean;
}

export interface Me { id: string; role: Role; status: string; displayName: string; avatar?: string; consentCode?: string; consentStatus?: string }

export interface GradingBand { minPercent: number; grade: number; labelBg: string }

export interface TestSummary {
  id: string; key: string; title: string; kind: string; kindLabel: string; path: string; questionCount: number;
  timeLimitMin: number; gradingBands?: GradingBand[]; hintPolicy: string; scoringNote: string; scopeNote: string;
  inProgressAttemptId?: string; bestPercent?: number; completedAttempts: number;
}

export interface AttemptItem {
  position: number; question: QuestionView; answer?: AnswerPayload; markedForReview: boolean; answered: boolean;
  hintsUsed: number; revealedHints: string[]; lastRequestId?: string;
}

export interface AttemptView {
  attemptId: string; testId: string; title: string; kindLabel: string; status: string; startedAt: string;
  deadlineAt?: string; serverNow: string; lastPosition: number; hintPolicy: string; items: AttemptItem[];
}

export interface ResultView {
  attemptId: string; title: string; kindLabel: string; points: number; maxPoints: number; percent: number;
  grade?: number; gradeLabel?: string; disclaimer: string; scoringSource: string;
  skills: { skill: string; title: string; points: number; maxPoints: number; percent: number; lessonKey?: string }[];
  misconceptions: { code: string; label: string; count: number; lessonKey: string }[];
  answerKeyVisible: boolean;
  items: { position: number; question: QuestionView; answer?: AnswerPayload; status?: string; points?: number; maxPoints: number;
    feedback?: string; misconception?: string; correctAnswer?: string; solution?: string; theoryLessonKey?: string;
    scoredBy: string; teacherComment?: string }[];
  corrections: string[];
  submittedAt: string;
}
