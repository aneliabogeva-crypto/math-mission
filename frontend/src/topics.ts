// The course is organised in topics ("теми"). Each topic has its own colour, its lessons in order,
// the tests that belong to each lesson and, at the end, a summary with the key points and the summary tests.
// Used by the Map and the Tests page. A test that is not assigned here is still shown (under "Още тестове").
import type { TestSummary } from './api';

export interface TopicDef {
  id: string;
  name: string;
  colour: string; // CSS variable name, see styles.css (--t1 … --t6)
  lessons: string[];
  /** Tests placed under a lesson: lesson key → test keys. */
  lessonTests: Record<string, string[]>;
  /** Summary tests at the end of the topic (topic tests, mixed tests). */
  summaryTests: string[];
  /** Class-test (контролно) preparation variants at the end of the topic. */
  examTests: string[];
  keyPoints: string[];
  formulas?: string[];
}

export const TOPICS: TopicDef[] = [
  {
    id: 'T1', name: 'Рационални изрази', colour: '--t1', lessons: ['A1', 'A2', 'A3', 'A4', 'A5'],
    lessonTests: {}, summaryTests: ['D1-A', 'D3-A', 'D4-A'], examTests: [],
    keyPoints: [
      'Числов израз — само числа, действия и скоби. Израз с променливи — има и букви.',
      'Цял израз: няма деление на израз с променлива. Дробен израз: има такова деление.',
      'Дробният израз няма смисъл, когато знаменателят е 0 — намираме кога знаменателят е 0.',
      'Числена стойност: заместваме всяка буква с числото ѝ (отрицателните — в скоби) и пресмятаме.',
      'Ред на действията: скоби → степени → умножение и деление → събиране и изваждане (равноправните — отляво надясно).',
    ],
  },
  {
    id: 'T2', name: 'Едночлени', colour: '--t2', lessons: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B7'],
    lessonTests: {}, summaryTests: ['BT1', 'D2-B', 'D4-B', 'D5-B'], examTests: [],
    keyPoints: [
      'Едночлен е произведение на число (коефициент) и степени на променливи (буквена част).',
      'Нормален вид: един коефициент отпред, всяка променлива — веднъж. Степента е сборът на показателите.',
      'Подобни едночлени имат еднаква буквена част; при събиране и изваждане събираме само коефициентите.',
      'При умножение коефициентите се умножават, а показателите на една и съща буква се събират.',
      'Знакът пред едночлена е част от коефициента: в −x²y коефициентът е −1.',
    ],
    formulas: ['aᵐ · aⁿ = aᵐ⁺ⁿ', '(aᵐ)ⁿ = aᵐ·ⁿ', '(ab)ⁿ = aⁿbⁿ', 'aᵐ : aⁿ = aᵐ⁻ⁿ'],
  },
  {
    id: 'T3', name: 'Многочлени и действия с тях', colour: '--t3', lessons: ['C1', 'C2', 'C3', 'C4', 'C5'],
    lessonTests: { C3: ['OPS-1A', 'OPS-1B'], C4: ['OPS-2A', 'OPS-2B'], C5: ['OPS-3A', 'OPS-3B'] },
    summaryTests: [], examTests: ['OPS-K1', 'OPS-K2', 'OPS-K3', 'OPS-K4'],
    keyPoints: [
      'Многочлен е сбор от едночлени. Нормален вид: без подобни членове.',
      'Степента на многочлена е най-голямата от степените на членовете му.',
      'Минус пред скоба сменя знака на всеки член в нея: −(a − b) = −a + b.',
      'Едночлен по многочлен: умножаваме едночлена по всеки член.',
      'Многочлен по многочлен: всеки член по всеки член, после привеждаме подобните.',
    ],
    formulas: ['a(b + c) = ab + ac', '(a + b)(c + d) = ac + ad + bc + bd'],
  },
  {
    id: 'T4', name: 'Формули за съкратено умножение и тъждества', colour: '--t4', lessons: ['C6', 'C7', 'C8', 'C9', 'C10'],
    lessonTests: { C6: ['FRM-1A', 'FRM-1B'], C7: ['FRM-2A', 'FRM-2B'], C9: ['FRM-3A', 'FRM-3B'], C10: ['FRM-4A', 'FRM-4B'] },
    summaryTests: [], examTests: ['FRM-K1', 'FRM-K2', 'FRM-K3', 'FRM-K4'],
    keyPoints: [
      'Средният член на (a ± b)² е удвоеното произведение 2ab — не го забравяй.',
      'Разлика по сбор дава разлика на квадрати.',
      'В (a ± b)³ коефициентите са 1, 3, 3, 1.',
      'При a³ ± b³ средният член в непълния квадрат е ab, с обратния знак.',
      'Тъждество е равенство, вярно за всички стойности. Доказваме го, като преобразуваме едната страна до другата.',
    ],
    formulas: ['(a ± b)² = a² ± 2ab + b²', '(a − b)(a + b) = a² − b²', '(a ± b)³ = a³ ± 3a²b + 3ab² ± b³', 'a³ + b³ = (a + b)(a² − ab + b²)', 'a³ − b³ = (a − b)(a² + ab + b²)'],
  },
  {
    id: 'T5', name: 'Разлагане на множители', colour: '--t5', lessons: ['C11', 'C12', 'C13'],
    lessonTests: {}, summaryTests: [], examTests: [],
    keyPoints: [
      'Първо изнеси общия множител: ab + ac = a(b + c).',
      'После провери за формула: a² − b², a² ± 2ab + b², a³ ± b³.',
      'При четири члена опитай групиране.',
      'Проверка: умножи обратно — трябва да получиш началния израз.',
      'Разлагането помага за бързо пресмятане: 98² − 4 = (98 − 2)(98 + 2) = 9600.',
    ],
    formulas: ['ab + ac = a(b + c)', 'a² − b² = (a − b)(a + b)', 'a² ± 2ab + b² = (a ± b)²'],
  },
  {
    id: 'T6', name: 'Обобщителен преговор', colour: '--t6', lessons: ['D1', 'D2', 'D3'],
    lessonTests: {}, summaryTests: ['D2-C', 'D3-C', 'D5-C', 'D6-M', 'D7-M'], examTests: [],
    keyPoints: [
      'Прочети какво се иска: стойност, опростяване или разлагане.',
      'За стойност — първо опрости, после замести.',
      'Провери отговора с число (например x = 2): началният и крайният израз дават едно и също.',
      'Най-честите грешки: знак пред скоба, забравено 2ab, събиране на неподобни членове.',
    ],
  },
];

export interface Placed { byLesson: Map<string, TestSummary[]>; summary: Map<string, TestSummary[]>; exams: Map<string, TestSummary[]>; other: TestSummary[] }

/** Puts every test in its place (under a lesson, in a topic summary, or among the rest). */
export function placeTests(tests: TestSummary[]): Placed {
  const byKey = new Map(tests.map((t) => [t.key, t]));
  const used = new Set<string>();
  const pick = (keys: string[]) => keys.map((k) => byKey.get(k)).filter((t): t is TestSummary => { if (t) used.add(t.key); return Boolean(t); });
  const byLesson = new Map<string, TestSummary[]>();
  const summary = new Map<string, TestSummary[]>();
  const exams = new Map<string, TestSummary[]>();
  for (const topic of TOPICS) {
    for (const [lesson, keys] of Object.entries(topic.lessonTests)) byLesson.set(lesson, pick(keys));
    summary.set(topic.id, pick(topic.summaryTests));
    exams.set(topic.id, pick(topic.examTests));
  }
  return { byLesson, summary, exams, other: tests.filter((t) => !used.has(t.key)) };
}

/** Short label for a test inside its topic. */
export function testLabel(t: TestSummary, topic?: TopicDef): string {
  const [group, rest] = t.title.includes(' · ') ? t.title.split(' · ', 2) : ['', t.title];
  if (/вариант \d+/.test(rest)) return `Контролно — ${rest.match(/вариант \d+/)![0]}`;
  const training = rest.match(/тренировка \d+/);
  if (training) return training[0].replace(/^т/, 'Т');
  if (topic && (group === topic.name || topic.id === 'T1' || topic.id === 'T2')) return rest.replace(/^Тест/, 'Обобщаващ тест');
  return group && group !== 'Обобщителни тестове' ? `${group} — ${rest.toLowerCase()}` : rest;
}
