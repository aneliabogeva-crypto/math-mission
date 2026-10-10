// Topic sections ("теми") shared by the Tests page and the Map.
// A topic is a set of tests whose keys share a prefix (e.g. OPS-1A … OPS-K4). Any new topic added to the
// content with a new key prefix appears on the Map automatically; lessons and a note are optional extras.
import type { TestSummary } from './api';

export interface LessonLink { key: string; title: string }

export const GROUP_LESSONS: Record<string, LessonLink[]> = {
  'Рационални изрази': [{ key: 'A2', title: 'Цели и дробни изрази' }, { key: 'A3', title: 'Числена стойност' }],
  'Едночлени': [{ key: 'B3', title: 'Подобни едночлени' }, { key: 'B4', title: 'Умножение' }, { key: 'B5', title: 'Степенуване' }],
  'Многочлени': [{ key: 'C6', title: 'Квадрат на двучлен' }, { key: 'C7', title: 'Разлика на квадрати' }, { key: 'C11', title: 'Общ множител' }],
  'Действия с многочлени': [{ key: 'C3', title: 'Събиране и изваждане' }, { key: 'C4', title: 'Многочлен по едночлен' }, { key: 'C5', title: 'Многочлен по многочлен' }],
  'Тъждества и формули': [{ key: 'C6', title: 'Квадрат на двучлен' }, { key: 'C7', title: 'Разлика на квадрати' }, { key: 'C8', title: 'Куб на двучлен' },
    { key: 'C9', title: 'Сбор и разлика на кубове' }, { key: 'C10', title: 'Тъждества' }],
  'Обобщителни тестове': [{ key: 'D1', title: 'Карта на формулите' }, { key: 'D2', title: 'Типични грешки' }],
};

export const GROUP_NOTE: Record<string, string> = {
  'Действия с многочлени': 'Тренировки по всяко действие поотделно — с подсказки.',
  'Тъждества и формули': 'Тренировки по всяка формула и по тъждества — с подсказки.',
  'Обобщителни тестове': 'Смесени задачи от трите теми, без подсказки.',
  'Подготовка за контролно': 'Като истинско контролно: 20 задачи, 40 минути, без подсказки. За всяка тема реши поне два варианта.',
};

export const EXAM_GROUP = 'Подготовка за контролно';
/** Tests of the first programme (BT1, D1-A … D7-M) belong to the curriculum zones, not to a topic. */
const BASE_KEY = /^(BT\d+|D\d+-)/;

export const groupOf = (t: TestSummary) => /^([^·]+) · /.exec(t.title)?.[1].trim() ?? 'Още тестове';
export const shortTitle = (t: TestSummary) => t.title.replace(/^[^·]+ · /, '');

export interface Topic { prefix: string; name: string; lessons: LessonLink[]; note?: string; practice: TestSummary[]; exams: TestSummary[] }

/** Topics in the order their tests were published (by key), each with its practice tests and class-test variants. */
export function topicsFrom(tests: TestSummary[]): Topic[] {
  const byPrefix = new Map<string, TestSummary[]>();
  for (const t of [...tests].sort((a, b) => a.key.localeCompare(b.key))) {
    if (BASE_KEY.test(t.key) || !t.key.includes('-')) continue;
    const p = t.key.split('-')[0];
    byPrefix.set(p, [...(byPrefix.get(p) ?? []), t]);
  }
  const topics = [...byPrefix.entries()].map(([prefix, list]) => {
    const practice = list.filter((t) => groupOf(t) !== EXAM_GROUP);
    const exams = list.filter((t) => groupOf(t) === EXAM_GROUP);
    const name = practice[0] ? groupOf(practice[0]) : shortTitle(list[0]).replace(/\s+—.*$/, '');
    return { prefix, name, lessons: GROUP_LESSONS[name] ?? [], note: GROUP_NOTE[name], practice, exams };
  });
  // Study order: topics whose lessons come earlier in the curriculum first.
  const rank = (t: Topic) => Math.min(...t.lessons.map((l) => l.key.charCodeAt(0) * 100 + Number(l.key.slice(1))), 99999);
  return topics.sort((a, b) => rank(a) - rank(b));
}
