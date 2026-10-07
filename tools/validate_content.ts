// Re-checks every question in the content file with the app's own maths engine and scorer:
// the key must score as CORRECT, every wrong option/distractor must not, every test must obey
// the blueprint and contain no duplicates. Run: npx esbuild ... (see CI) or node after bundling.
import content from '../frontend/src/local/data/content.json';
import side from './generated-checks.json';
import { check, type AnswerKey, type Payload, type ResponseType } from '../frontend/src/local/checker';
import { equivalent, isFullyFactorised, isNormalForm, parse } from '../frontend/src/local/math';

type Q = { key: string; draft: { responseType: ResponseType; maxPoints: number; skill: string; key: AnswerKey; prompt: { text: string; options?: { id: string; text: string }[]; parts?: unknown[] }; hints: string[] } };
const qs = (content as unknown as { questions: Q[] }).questions;
const tests = (content as unknown as { tests: { testKey: string; kind: string; questionKeys: string[] }[] }).tests;
const expect = side as Record<string, string>;
const errors: string[] = [];
const err = (k: string, m: string) => errors.push(`${k}: ${m}`);
const tryParse = (s: string) => { try { parse(s.replace(/\s*=\s*.*/, '')); return !/=/.test(s); } catch { return false; } };

for (const q of qs) {
  const d = q.draft; const k = d.key;
  if (d.hints.length !== 3) err(q.key, 'needs 3 hints');
  if (!k.solution) err(q.key, 'no solution');
  let payload: Payload;
  switch (d.responseType) {
    case 'SINGLE_CHOICE': payload = { optionId: k.correctOptionId }; break;
    case 'STEPS': payload = { steps: [k.answer!] }; break;
    case 'STRUCTURED': payload = { parts: Object.fromEntries((k.parts ?? []).map((p) => [p.id, p.answer])) }; break;
    default: payload = { value: k.answer };
  }
  const r = check(d.responseType, k, d.maxPoints, payload);
  if (r.status !== 'CORRECT') err(q.key, `key does not score CORRECT (${r.status}: ${r.message})`);
  if ((d.responseType === 'EXPRESSION' || d.responseType === 'STEPS') && k.form === 'NORMAL_FORM' && !isNormalForm(k.answer!)) err(q.key, 'key not in normal form');
  if (d.responseType === 'EXPRESSION' && k.form === 'FACTORISED' && !isFullyFactorised(k.answer!)) err(q.key, 'key not fully factorised');
  for (const ds of k.distractors ?? []) {
    if (d.responseType === 'SINGLE_CHOICE' || !ds.match) continue;
    try { if (equivalent(ds.match, k.answer!)) err(q.key, `distractor ${ds.match} equals the answer`); } catch { /* non-parsable distractor */ }
  }
  if (d.responseType === 'SINGLE_CHOICE') {
    const opts = d.prompt.options ?? [];
    if (new Set(opts.map((o) => o.text)).size !== opts.length) err(q.key, 'duplicate options');
    for (const o of opts) {
      if (o.id !== k.correctOptionId && check('SINGLE_CHOICE', k, 1, { optionId: o.id }).status === 'CORRECT') err(q.key, `wrong option ${o.id} scores correct`);
      if (o.id !== k.correctOptionId && !(k.distractors ?? []).some((x) => x.match === o.id && x.explanation)) err(q.key, `option ${o.id} lacks explanation`);
    }
    const exp = expect[q.key];
    if (exp !== undefined) {
      const correctText = opts.find((o) => o.id === k.correctOptionId)!.text;
      if (!tryParse(correctText) || !equivalent(correctText, exp)) err(q.key, `correct option "${correctText}" ≠ expected ${exp}`);
      for (const o of opts) if (o.id !== k.correctOptionId && tryParse(o.text) && equivalent(o.text, exp)) err(q.key, `wrong option "${o.text}" is mathematically correct too`);
    }
  }
}
const byKey = new Map(qs.map((q) => [q.key, q]));
for (const t of tests) {
  const items = t.questionKeys.map((k) => byKey.get(k));
  if (items.some((x) => !x)) { err(t.testKey, 'missing question'); continue; }
  const n = t.kind === 'INTEGRATED' ? 24 : 20;
  if (items.length !== n) err(t.testKey, `has ${items.length} questions, needs ${n}`);
  if (new Set(items.map((x) => x!.draft.prompt.text.trim().toLowerCase())).size !== items.length) err(t.testKey, 'duplicate prompts');
  const g = (rt: string) => items.filter((x) => (rt === 'MC' ? x!.draft.responseType === 'SINGLE_CHOICE' : rt === 'SA' ? ['NUMERIC', 'EXPRESSION'].includes(x!.draft.responseType) : ['STEPS', 'STRUCTURED', 'FREE_TEXT'].includes(x!.draft.responseType))).length;
  const want = t.kind === 'INTEGRATED' ? [14, 7, 3] : [12, 5, 3];
  if (g('MC') !== want[0] || g('SA') !== want[1] || g('MS') !== want[2]) err(t.testKey, `blueprint ${g('MC')}/${g('SA')}/${g('MS')} ≠ ${want.join('/')}`);
}
if (new Set(qs.map((q) => q.key)).size !== qs.length) errors.push('duplicate question keys');

// Lessons: the 12-step pattern (as LessonModel.validatePattern), referenced items exist and are
// not used in tests, and every worked-example step is mathematically equal to the previous one.
type Step = { expression: string; why: string };
type Lesson = { lessonKey: string; content: {
  context?: { text: string }; objectives?: string[]; prerequisiteCheck?: { questionKeys: string[] }; explanation?: string[];
  definition?: { text: string }; visual?: { description: string }; workedExamples?: { level: string; problem: string; steps: Step[]; answer: string }[];
  watchOut?: unknown[]; guidedPractice?: { questionKey: string; support: string }[]; check?: { questionKeys: string[] };
  summary?: string[]; nextStep?: { text: string } } };
const lessons = (content as unknown as { lessons: Lesson[] }).lessons;
const inTests = new Set(tests.flatMap((t) => t.questionKeys));
const letters = (s: string) => /[a-z]/i.test(s.replace(/при.*/, ''));
const asExpr = (s: string) => s.replace(/,/g, '.').replace(/−/g, '-').replace(/·/g, '*').replace(/[⁰¹²³⁴⁵⁶⁷⁸⁹]+/g, (m) => '^' + [...m].map((ch) => '⁰¹²³⁴⁵⁶⁷⁸⁹'.indexOf(ch)).join('')).replace(/:/g, '/');
const parses = (s: string) => { if (/[=а-яА-Я]/.test(s)) return false; try { parse(asExpr(s)); return true; } catch { return false; } };
let lessonSteps = 0;
for (const l of lessons) {
  const c = l.content; const k = `lesson ${l.lessonKey}`;
  if (!c.context?.text) err(k, '1 context');
  if (!c.objectives?.length) err(k, '2 objectives');
  if (!c.prerequisiteCheck?.questionKeys?.length) err(k, '3 prerequisite check');
  if (!c.explanation?.length) err(k, '4 explanation');
  if (!c.definition?.text) err(k, '5 definition');
  if (!c.visual?.description) err(k, '6 visual');
  const ex = c.workedExamples ?? [];
  if (ex.length < 3 || !['BASIC', 'TYPICAL', 'CHALLENGE'].every((lv) => ex.some((e) => e.level === lv))) err(k, '7 examples');
  if (!c.watchOut?.length) err(k, '9 watch out');
  const gp = c.guidedPractice ?? [];
  if (!gp.length || gp[0].support !== 'FULL' || gp[gp.length - 1].support === 'FULL') err(k, '10 guided practice');
  const ck = c.check?.questionKeys ?? [];
  if (ck.length < 3 || ck.length > 7) err(k, '11 check size');
  if (!c.summary?.length || !c.nextStep?.text) err(k, '12 summary/next');
  for (const qk of [...(c.prerequisiteCheck?.questionKeys ?? []), ...gp.map((g) => g.questionKey), ...ck]) {
    if (!byKey.has(qk)) err(k, `missing question ${qk}`);
    if (inTests.has(qk)) err(k, `question ${qk} is also used in a test`);
  }
  for (const e of ex) {
    if (!e.steps.length || e.steps.some((s) => !s.why || !s.expression)) err(k, `example "${e.problem}" step without why`);
    const chain = [e.problem, ...e.steps.map((s) => s.expression)];
    for (let i = 1; i < chain.length; i++) {
      const a = chain[i - 1]; const b = chain[i];
      if (!parses(a) || !parses(b)) continue;
      if (letters(a) && !letters(b)) continue; // substitution step
      lessonSteps++;
      if (!equivalent(asExpr(a), asExpr(b))) err(k, `example "${e.problem}": "${a}" ≠ "${b}"`);
    }
    const last = e.steps[e.steps.length - 1]?.expression;
    if (last && parses(last) && parses(e.answer) && !equivalent(asExpr(last), asExpr(e.answer))) err(k, `example "${e.problem}": answer ${e.answer} ≠ last step ${last}`);
  }
}
if (new Set(lessons.map((l) => l.lessonKey)).size !== lessons.length) errors.push('duplicate lesson keys');
console.log(`${lessons.length} lessons, ${lessonSteps} worked-example steps checked`);
console.log(`${qs.length} questions, ${tests.length} tests checked`);
if (errors.length) { console.log(errors.join('\n')); process.exit(1); }
console.log('CONTENT OK');
