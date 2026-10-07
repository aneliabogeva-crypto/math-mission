// Runs the student, test and parent journeys against the on-device (phone) API, including every test in the programme.
import { handleLocal } from '../frontend/src/local/server';
let fail = 0; const ok = (n: string, c: unknown) => { if (!c) { fail++; console.log('FAIL', n); } };
const call = (m: string, p: string, b: unknown, t: string | null) => handleLocal(m, p, b, t) as Promise<any>;
(async () => {
  const s = await call('POST', '/api/auth/student', { nickname: 'Ани', avatar: 'fox', ageBand: 'FROM_14', goal: 'IMPROVE', confidence: 3 }, null);
  const t = s.token; ok('active', s.status === 'ACTIVE');
  const home = await call('GET', '/api/student/home', null, t); ok('home', home.profile.nickname === 'Ани');
  const lesson = await call('GET', '/api/student/lessons/B3', null, t); ok('lesson', lesson.content.workedExamples.length >= 3 && lesson.questions['B3-C1']);
  ok('no key in lesson', !JSON.stringify(lesson).includes('correctOptionId'));
  const w = await call('POST', '/api/student/practice/B3-C2/check', { requestId: 'r1', answer: { value: '7' }, hintsUsed: 0 }, t);
  ok('wrong', !w.correct && w.misconceptionLabel === 'Подобни едночлени' && w.similarQuestionKey && w.solution);
  const r = await call('POST', '/api/student/practice/B3-C2/check', { requestId: 'r2', answer: { value: '6m' }, hintsUsed: 0 }, t);
  ok('corrected', r.correct && r.correctedMistake);
  const xp = (await call('GET', '/api/student/home', null, t)).profile.xp;
  await call('POST', '/api/student/practice/B3-C2/check', { requestId: 'r1', answer: { value: '7' }, hintsUsed: 0 }, t);
  ok('idempotent', (await call('GET', '/api/student/home', null, t)).profile.xp === xp);
  let reservedBlocked = false; try { await call('GET', '/api/student/practice/BT1-13', null, t); } catch { reservedBlocked = true; } ok('reserved', reservedBlocked);
  const lessonKeys = (await import('../frontend/src/local/data/lesson-keys.json')).default as string[];
  let lessonItems = 0;
  for (const lk of lessonKeys) {
    const L = await call('GET', `/api/student/lessons/${lk}`, null, t);
    const keys = [...L.content.prerequisiteCheck.questionKeys, ...L.content.guidedPractice.map((g: any) => g.questionKey), ...L.content.check.questionKeys];
    ok(`lesson ${lk} questions`, keys.every((k: string) => L.questions[k]));
    for (const k of keys) {
      const q = L.questions[k];
      const ans = q.responseType === 'SINGLE_CHOICE' ? { optionId: q.prompt.options[0].id } : q.responseType === 'STRUCTURED' ? { parts: { a: '12345' } }
        : q.responseType === 'STEPS' ? { steps: ['12345'] } : { value: '12345' };
      const fb = await call('POST', `/api/student/practice/${k}/check`, { requestId: 'L' + k, answer: ans, hintsUsed: 0 }, t);
      ok(`lesson item ${k} feedback`, fb.message && fb.solution && (fb.correct || (fb.correctAnswer && fb.message.length > 10)));
      if (!fb.correct && !(fb.correctAnswer && fb.message.length > 10)) console.log(k, fb.status, fb.message, fb.correctAnswer);
      lessonItems++;
    }
  }
  console.log(`${lessonKeys.length} lessons, ${lessonItems} lesson items answered`);
  await call('PUT', '/api/student/lessons/B3/position', { position: 10, completed: true }, t);
  ok('map', JSON.stringify(await call('GET', '/api/student/map', null, t)).includes('"COMPLETED"'));
  const tests = await call('GET', '/api/student/tests', null, t); ok('tests', tests[0].questionCount === 20);
  const a = await call('POST', '/api/student/tests/BT1/attempts', { attemptId: 'att1' }, t);
  ok('attempt', a.items.length === 20 && !JSON.stringify(a).includes('solution'));
  const again = await call('POST', '/api/student/tests/BT1/attempts', { attemptId: 'att1' }, t); ok('same attempt', again.attemptId === 'att1');
  for (const it of a.items) {
    const ans = it.question.responseType === 'SINGLE_CHOICE' ? { optionId: 'a' } : it.question.responseType === 'STRUCTURED' ? { parts: { a: '1' } }
      : it.question.responseType === 'STEPS' ? { optionId: 'b' } : { value: '1' };
    const ack = await call('PUT', `/api/student/attempts/att1/items/${it.position}`, { requestId: 'q' + it.position, answer: ans }, t);
    const dup = await call('PUT', `/api/student/attempts/att1/items/${it.position}`, { requestId: 'q' + it.position, answer: ans }, t);
    ok('dup', !ack.duplicate && dup.duplicate);
  }
  const res = await call('POST', '/api/student/attempts/att1/submit', null, t);
  ok('result', res.maxPoints === 31 && res.grade >= 2 && res.disclaimer.includes('не е официална') && res.skills.length > 1);
  ok('mistakes', (await call('GET', '/api/student/mistakes', null, t)).length > 0);
  ok('plan', (await call('GET', '/api/student/plan', null, t)).length === 7);
  ok('reference', (await call('GET', '/api/student/reference?q=квадрат', null, t)).length > 0);
  // under-14 consent on the same device
  const k = await call('POST', '/api/auth/student', { nickname: 'Малкия', avatar: 'cat', ageBand: 'UNDER_14', goal: 'IMPROVE', confidence: 3 }, null);
  let blocked = false; try { await call('GET', '/api/student/home', null, k.token); } catch { blocked = true; } ok('pending blocked', blocked);
  const text = await call('GET', '/api/consent/text/adult', null, null);
  const g = await call('POST', `/api/consent/code/${k.consentCode}/grant`, { acceptedVersion: text.version, username: 'mama', password: 'Parola-12345' }, k.token);
  ok('granted', g.status === 'GRANTED' && g.token);
  ok('child active', (await call('GET', '/api/student/home', null, k.token)).profile.nickname === 'Малкия');
  const login = await call('POST', '/api/auth/login', { username: 'mama', password: 'Parola-12345' }, null);
  const kids = await call('GET', '/api/guardian/children', null, login.token); ok('guardian', kids[0].nickname === 'Малкия');
  const rec = await call('POST', '/api/auth/student/recover', { recoveryCode: s.recoveryCode }, null); ok('recover', rec.token === t);
  // every test in the 7-day programme can be started, answered and submitted on the device
  for (const tt of tests) {
    const at = await call('POST', `/api/student/tests/${tt.id}/attempts`, { attemptId: 'x-' + tt.id }, t);
    ok('start ' + tt.id, at.items.length === tt.questionCount);
    await call('PUT', `/api/student/attempts/x-${tt.id}/items/0`, { requestId: 'r-' + tt.id, answer: { optionId: 'a' } }, t);
    const rr = await call('POST', `/api/student/attempts/x-${tt.id}/submit`, null, t);
    ok('submit ' + tt.id, rr.maxPoints > 0 && rr.grade >= 2);
  }
  ok('22 tests', tests.length === 22);
  console.log(fail === 0 ? "ALL PHONE API CHECKS PASS" : `${fail} failures`);
  if (fail) process.exit(1);
})().catch((e) => { console.log("CRASH", e); process.exit(1); });
