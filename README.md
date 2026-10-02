# Math Mission: NEA (MVP implementation, iteration 1)

A mobile-first PWA for Grade 7 algebra in Bulgaria. It covers rational expressions, monomials and polynomials, and is built from
*Math Mission NEA: Product Requirements v1.0*.

* **backend/**: Java 21+ / Spring Boot 3.5 modular monolith, REST + OpenAPI, Flyway, PostgreSQL (H2 for zero-setup dev and tests)
* **frontend/**: React 18 + TypeScript + Vite PWA, written in Bulgarian, mobile-first from 320 px, light and dark themes

## Run it

### In the browser, with nothing installed (GitHub Codespaces)
On GitHub, click **Code → Codespaces → Create codespace on main**. The environment installs Java 21, Maven and Node.
`./run.sh` then builds and starts the app, and port 8080 opens in a new browser tab. This takes about 3–5 minutes the first time.

### On your machine (IntelliJ or a terminal)
Requires JDK 21+, Maven 3.9+ and Node 18+.

```bash
./run.sh                       # builds the PWA into the backend → http://localhost:8080
```

For development with hot reload, run `mvn spring-boot:run` in `backend/` and `npm run dev` in `frontend/`.
Then open http://localhost:5173. In IntelliJ: *Open* the `backend/pom.xml` as a project and run `MathMissionApplication`.

### Docker / public demo
`docker build -t math-mission . && docker run -p 8080:8080 math-mission`. `render.yaml` deploys the same image to Render's free plan
(New → Blueprint).

API docs: `/api/docs/ui`. Tests: `cd backend && mvn test`. GitHub Actions runs the backend tests, the frontend type-check and build,
and the Docker build on every push.

PostgreSQL instead of the built-in H2: `docker compose up -d db`, then start with profile `postgres`.

### Demo accounts (dev profile only)

| Role | Username | Password | Notes |
|---|---|---|---|
| Teacher | `teacher` | `Demo-2026!` | |
| Content author | `author` | `Demo-2026!` | |
| Mathematics reviewer | `reviewer` | `Demo-2026!` | |
| Administrator | `admin` | `Demo-2026!` | MFA: add TOTP secret `JBSWY3DPEHPK3PXP` to any authenticator app |

Students register with a nickname only (`/welcome`). A student under 14 stays **pending** until a guardian opens
`/consent/<code>` and grants consent.

## What is implemented

| Area | Spec reference | Where |
|---|---|---|
| Restricted maths parser → AST → polynomial / rational normal form; equivalence (`2(x+1)` ≡ `2x+2`); normal-form and full-factorisation checks; step-chain checking; length, depth, term and degree limits. Input is never executed. | 5.3, 9.3 | `backend/.../math` |
| Answer checking: single choice, numeric (comma or point), fractions, symbolic, ordered steps, structured multi-part with partial credit, free text → teacher review | 5.3 | `assessment/AnswerChecker` |
| Feedback: correct/incorrect, error step, misconception, explanation, correct method, theory link, similar question, confirmation on correct answers; non-shaming wording | 5.4, US-STU-07 | `progress/PracticeService` |
| Learner profile: nickname, avatar, goal, confidence; recovery code instead of personal data; consent-gated activation | US-STU-01, 11 | `identity`, `consent` |
| Learning map with 4 zones and 28 lesson stops, soft prerequisites, status shown by icon + text | US-STU-03 | `curriculum`, `MapPage` |
| Lesson player for the 12-step pattern, validated server-side before review; autosaved position | 4.2, US-STU-04, 02 | `content/LessonModel`, `LessonPlayer` |
| Question player: maths keyboard, 44 px targets, scratch area, mark for review, graduated hints, autosave, resume | US-STU-05, 06 | `AttemptPlayer`, `MathKeyboard` |
| Tests: ≥ 20 questions enforced, no duplicates, blueprint composition, timing, randomised order per attempt, grade 2–6 scale shown before start, "not an official grade" disclaimer, skill analysis | 5, US-STU-08, 12 (release AC) | `TestAuthoringService`, `AssessmentService` |
| Answer key is never sent before the review moment, and practice never serves items from published tests | AC 24 | `StudentQuestionView`, `PracticeService.reservedForTests` |
| Idempotent offline sync: client-generated attempt ids and request ids, local outbox, service worker caches the open lesson | US-STU-02, 12 | `offline.ts`, `public/sw.js` |
| Mistake review by misconception with lesson and practice links; corrected mistakes earn rewards | US-STU-09 | |
| Seven-day plan with 1/3/7/14-day spaced review; stops escalating after 3 errors and proposes the prerequisite | US-STU-10 | `ProgressService` |
| Formula and concept reference with search, conditions, examples, common mistakes, spoken text for screen readers | US-STU-11 | `ReferenceLibrary` |
| Classes with revocable codes, nickname enrolment, removal without account deletion, audited | US-TCH-01 | `classroom` |
| Assignments (deadline, attempts, hint policy), student-view preview, status without push notifications | US-TCH-02 | |
| Teacher analytics: completion based on recorded activity only, distribution, item analysis, skills, misconceptions, first vs latest | US-TCH-03 | `TeacherAnalyticsService` |
| Test generator with full preview and answer key (teacher only) | US-TCH-04 | |
| Teacher review queue with rubric, partial points and comment; audited; automated vs teacher-reviewed shown | US-TCH-05 | |
| Guardian consent (adult and child texts, version/date/scope/verification), withdrawal with consequences shown, weekly summary, notification setting, export and deletion request | US-GUA-01, 02 | |
| Content authoring with 4.3 metadata validation, two-stage review (author ≠ reviewer), rejection with comments, versioning, withdrawal, key correction → new version → deterministic recalculation and correction notices | US-CNT-01..03 | `ContentService` |
| Server-side RBAC, admin TOTP MFA, append-only audit (DB trigger on PostgreSQL), access review, auth rate limiting | US-ADM-01 | `identity`, `audit` |
| Versioned assessment models; only approved models usable | US-ADM-02 | `TestBlueprint` |
| Responsible gamification: XP, levels, badges, corrected-mistake rewards; no streak penalties, chance mechanics or public ranking | 8.2 | `gamification` |

### Seed content (demo)
* **Lesson B3, "Подобни едночлени. Събиране и изваждане"**: all 12 pattern steps, 3 worked examples with a "why" for every step,
  4 "watch out" items, guided practice with fading support, and a 5-item ungraded check.
* **Test BT1, "Едночлени — тематичен тест 1"**: 20 questions following the 12 multiple-choice / 5 short-answer / 3 multi-step blueprint,
  with a 30/50/20 difficulty mix. Every item has a solution, three hints, distractor explanations and metadata.
* All content is original. It was approved by a *demo reviewer* account, and each item's review comment says it still needs a
  real mathematics review before production use.

## Not done yet (next iterations)
* The remaining 27 lessons, ~515 questions and 17 tests (spec stage 3: content production). The authoring API and validation are ready.
* A rich authoring UI. Authors currently create content through `/api/content/**`; the web UI covers the review workflow.
* Verifiable guardian identity, email/SMS delivery of the weekly summary, and a DPIA. These depend on the account-model decision in §14.
* Feature flags, load tests, a WCAG audit with assistive technology, and an OWASP ASVS L2 assessment.
* Java 25: the code targets 21+. Build with `-Djava.version=25` on a JDK 25.
