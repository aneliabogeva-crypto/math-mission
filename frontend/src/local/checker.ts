// On-device port of bg.mathmission.assessment.AnswerChecker: deterministic scoring with
// explanatory, non-shaming feedback.
import { MathInputError, Q, asPoly, equivalent, isFullyFactorised, isNormalForm, isPolynomial, normalise, safeEquivalent, checkSteps } from './math';

export type Misconception = 'SIGN' | 'BRACKETS' | 'ORDER_OF_OPERATIONS' | 'LIKE_TERMS' | 'FORMULA_APPLICATION' | 'FACTORISATION' | 'REASONING' | 'TECHNICAL';
export const MISCONCEPTION_BG: Record<Misconception, string> = {
  SIGN: 'Знаци', BRACKETS: 'Скоби', ORDER_OF_OPERATIONS: 'Ред на действията', LIKE_TERMS: 'Подобни едночлени',
  FORMULA_APPLICATION: 'Формули за съкратено умножение', FACTORISATION: 'Разлагане на множители', REASONING: 'Разсъждение',
  TECHNICAL: 'Техническа грешка',
};
export const MISTAKE_LESSON: Record<Misconception, string> = {
  SIGN: 'C3', BRACKETS: 'C4', ORDER_OF_OPERATIONS: 'A4', LIKE_TERMS: 'B3', FORMULA_APPLICATION: 'C6', FACTORISATION: 'C11', REASONING: 'D2', TECHNICAL: 'D2',
};

export type ResponseType = 'SINGLE_CHOICE' | 'NUMERIC' | 'EXPRESSION' | 'STEPS' | 'STRUCTURED' | 'FREE_TEXT';
export type Form = 'ANY' | 'NORMAL_FORM' | 'FACTORISED';
export interface Distractor { match?: string; misconception?: Misconception; explanation?: string }
export interface PartKey { id: string; type: ResponseType; answer: string; form?: Form; points: number }
export interface AnswerKey {
  correctOptionId?: string; answer?: string; form?: Form; parts?: PartKey[]; distractors?: Distractor[];
  defaultMisconception?: Misconception; confirmation?: string; solution: string; rubric?: string;
}
export interface Payload { optionId?: string; value?: string; steps?: string[]; parts?: Record<string, string>; text?: string }
export type Status = 'CORRECT' | 'PARTIAL' | 'INCORRECT' | 'INVALID_INPUT' | 'NEEDS_REVIEW' | 'UNANSWERED';
export interface PartResult { id: string; status: Status; points: number; message: string }
export interface Result { status: Status; points: number; maxPoints: number; misconception?: Misconception; message: string; errorStep?: number; parts: PartResult[] }

export const round = (v: number) => Math.round(v * 100) / 100;
const fmt = (v: number) => String(v).replace('.', ',');
const blank = (s?: string | null) => s == null || s.trim() === '';
const confirmation = (k: AnswerKey) => k.confirmation ?? 'Вярно! Подходът ти е правилен.';
const R = (status: Status, points: number, maxPoints: number, message: string, misconception?: Misconception, errorStep?: number, parts: PartResult[] = []): Result =>
  ({ status, points, maxPoints, message, misconception, errorStep, parts });

function isBlank(p: Payload) {
  return blank(p.optionId) && blank(p.value) && blank(p.text) && (!p.steps || p.steps.every(blank))
    && (!p.parts || Object.values(p.parts).every(blank));
}

function findDistractor(k: AnswerKey, test: (m: string) => boolean) {
  return k.distractors?.find((d) => d.match != null && test(d.match));
}

function incorrect(k: AnswerKey, max: number, d?: Distractor): Result {
  const m = d?.misconception ?? k.defaultMisconception ?? 'REASONING';
  const msg = d?.explanation ?? 'Отговорът не съвпада с верния. Виж подсказките или решението и опитай подобна задача.';
  return R('INCORRECT', 0, max, msg, m);
}

function checkChoice(k: AnswerKey, max: number, optionId?: string): Result {
  if (k.correctOptionId === optionId) return R('CORRECT', max, max, confirmation(k));
  return incorrect(k, max, findDistractor(k, (s) => s === optionId));
}

function checkNumeric(k: AnswerKey, max: number, value?: string): Result {
  const expected = asPoly(normalise(k.answer!)).constVal();
  const given = normalise(value ?? '');
  if (!isPolynomial(given) || !asPoly(given).isConst()) return R('INVALID_INPUT', 0, max, 'Очаква се число, например 2,5 или −3/4.', 'TECHNICAL');
  const got: Q = asPoly(given).constVal();
  if (got.eq(expected)) return R('CORRECT', max, max, confirmation(k));
  let d = findDistractor(k, (s) => safeEquivalent(s, value!));
  if (!d && got.eq(expected.neg())) d = { misconception: 'SIGN', explanation: 'Получи същото число, но с обратен знак. Провери знаците.' };
  return incorrect(k, max, d);
}

function checkExpression(k: AnswerKey, expected: string, form: Form, max: number, value?: string): Result {
  if (equivalent(expected, value ?? '')) {
    if (form === 'NORMAL_FORM' && !isNormalForm(value!)) {
      return R('PARTIAL', round(max / 2), max, 'Изразът е равен на верния, но не е в нормален вид. Разкрий скобите и приведи подобните едночлени.', 'LIKE_TERMS');
    }
    if (form === 'FACTORISED' && !isFullyFactorised(value!)) {
      return R('PARTIAL', round(max / 2), max, 'Изразът е равен на верния, но не е разложен напълно. Провери за общ множител или формула.', 'FACTORISATION');
    }
    return R('CORRECT', max, max, confirmation(k));
  }
  let d = findDistractor(k, (s) => safeEquivalent(s, value!));
  if (!d && safeEquivalent(expected, `-(${value})`)) d = { misconception: 'SIGN', explanation: 'Отговорът ти е с обратен знак. Провери знака пред скобите.' };
  return incorrect(k, max, d);
}

function checkStepsAnswer(k: AnswerKey, max: number, steps?: string[]): Result {
  const nonEmpty = (steps ?? []).filter((s) => !blank(s));
  if (!nonEmpty.length) return R('UNANSWERED', 0, max, 'Няма отговор.');
  const chain: string[] = [];
  if (k.parts?.[0]?.id === 'start') chain.push(k.parts[0].answer);
  chain.push(...nonEmpty);
  const sc = checkSteps(chain);
  const last = nonEmpty[nonEmpty.length - 1];
  const fin = checkExpression(k, k.answer!, k.form ?? 'ANY', max, last);
  const offset = chain.length - nonEmpty.length;
  if (sc.ok && fin.status === 'CORRECT') return R('CORRECT', max, max, confirmation(k));
  if (!sc.ok) {
    const studentStep = sc.firstInvalid - offset + 1;
    const valid = Math.max(0, studentStep - 1);
    const pts = round(Math.min(max / 2, (max * valid) / Math.max(1, nonEmpty.length + 1)));
    const d = findDistractor(k, (s) => safeEquivalent(s, nonEmpty[Math.max(0, studentStep - 1)]));
    const m = d?.misconception ?? k.defaultMisconception ?? 'TECHNICAL';
    const msg = `Грешката е на ред ${studentStep}: този ред не е равен на предишния. ${d?.explanation ?? 'Провери този преход стъпка по стъпка.'}`;
    return R(pts > 0 ? 'PARTIAL' : 'INCORRECT', pts, max, msg, m, studentStep);
  }
  return R('PARTIAL', round(max / 2), max, `Всички преходи са верни, но решението не е довършено. ${fin.message}`, fin.misconception ?? k.defaultMisconception);
}

function checkStructured(k: AnswerKey, max: number, answers?: Record<string, string>): Result {
  const results: PartResult[] = [];
  let total = 0; let first: Misconception | undefined; let review = false;
  for (const part of k.parts ?? []) {
    const given = answers?.[part.id];
    const pk: AnswerKey = { correctOptionId: part.answer, answer: part.answer, form: part.form, distractors: k.distractors,
      defaultMisconception: k.defaultMisconception, confirmation: 'Вярно.', solution: '' };
    let r: Result;
    if (blank(given)) r = R('UNANSWERED', 0, part.points, 'Няма отговор.');
    else {
      try {
        r = part.type === 'NUMERIC' ? checkNumeric(pk, part.points, given)
          : part.type === 'EXPRESSION' ? checkExpression(pk, part.answer, part.form ?? 'ANY', part.points, given)
          : part.type === 'SINGLE_CHOICE' ? checkChoice(pk, part.points, given)
          : R('NEEDS_REVIEW', 0, part.points, 'За преглед от учител.');
      } catch (e) {
        r = R('INVALID_INPUT', 0, part.points, e instanceof Error ? e.message : 'Неразчетен запис.', 'TECHNICAL');
      }
    }
    if (r.status === 'NEEDS_REVIEW') review = true;
    if (!first && r.misconception) first = r.misconception;
    total += r.points;
    results.push({ id: part.id, status: r.status, points: r.points, message: r.message });
  }
  total = round(total);
  const s: Status = review ? 'NEEDS_REVIEW' : total >= max ? 'CORRECT' : total > 0 ? 'PARTIAL' : 'INCORRECT';
  const msg = s === 'CORRECT' ? confirmation(k) : s === 'NEEDS_REVIEW' ? 'Част от отговора ще бъде прегледана от учител.'
    : s === 'PARTIAL' ? `Частично вярно: получаваш ${fmt(total)} от ${fmt(max)} точки. Виж коя част има нужда от поправка.`
    : 'Нито една част не е вярна засега. Виж решението стъпка по стъпка.';
  return R(s, total, max, msg, s === 'CORRECT' ? undefined : first, undefined, results);
}

export function check(type: ResponseType, k: AnswerKey, max: number, p?: Payload | null): Result {
  if (!p || isBlank(p)) return R('UNANSWERED', 0, max, 'Няма отговор.');
  try {
    switch (type) {
      case 'SINGLE_CHOICE': return checkChoice(k, max, p.optionId);
      case 'NUMERIC': return checkNumeric(k, max, p.value);
      case 'EXPRESSION': return checkExpression(k, k.answer!, k.form ?? 'ANY', max, p.value);
      case 'STEPS': return checkStepsAnswer(k, max, p.steps);
      case 'STRUCTURED': return checkStructured(k, max, p.parts);
      case 'FREE_TEXT': return R('NEEDS_REVIEW', 0, max, 'Отговорът ще бъде прегледан от учител.');
    }
  } catch (e) {
    if (e instanceof MathInputError) return R('INVALID_INPUT', 0, max, `Не успяхме да прочетем записа: ${e.message}`, 'TECHNICAL');
    return R('INVALID_INPUT', 0, max, 'Не успяхме да прочетем записа.', 'TECHNICAL');
  }
}
