# Generates backend/src/main/resources/seed/content.json (original Bulgarian content).
import json, os

SRC = "Оригинално съдържание, Math Mission (2026)"

def q(key, skill, outcome, rtype, diff, secs, pts, misc, text, answer=None, options=None, correct=None,
      form=None, distractors=(), hints=(), solution="", confirmation=None, parts=None, part_prompts=None,
      start=None, input_hint=None, rubric=None, default_misc=None, path="B"):
    prompt = {"text": text}
    if options: prompt["options"] = [{"id": i, "text": t} for i, t in options]
    if part_prompts: prompt["parts"] = part_prompts
    if start: prompt["startExpression"] = start
    if input_hint: prompt["inputHint"] = input_hint
    key_ = {"solution": solution, "distractors": [
        {"match": m, "misconception": mc, "explanation": ex} for m, mc, ex in distractors]}
    if correct: key_["correctOptionId"] = correct
    if answer is not None: key_["answer"] = answer
    if form: key_["form"] = form
    if parts: key_["parts"] = parts
    if confirmation: key_["confirmation"] = confirmation
    if rubric: key_["rubric"] = rubric
    key_["defaultMisconception"] = default_misc or misc
    assert len(hints) == 3, key
    return {"key": key, "draft": {
        "path": path, "skill": skill, "learningOutcome": outcome, "responseType": rtype, "difficulty": diff,
        "estimatedSeconds": secs, "maxPoints": pts, "misconception": misc, "prompt": prompt, "key": key_,
        "hints": list(hints), "sourceDeclaration": SRC}}

LT = "B.like-terms"
LT_OUT = "Разпознава подобни едночлени и ги събира и изважда"

lesson_questions = [
  # ---------------- prerequisite check
  q("B3-P1", "B.definition", "Определя коефициента на едночлен", "SINGLE_CHOICE", "FOUNDATIONAL", 30, 1, "SIGN",
    "Какъв е коефициентът на едночлена −3x²y?",
    options=[("a", "−3"), ("b", "3"), ("c", "x²y"), ("d", "2")], correct="a",
    distractors=[("b", "SIGN", "Знакът минус е част от коефициента. Коефициентът е −3."),
                 ("c", "REASONING", "x²y е буквената част. Коефициентът е числото пред нея."),
                 ("d", "REASONING", "2 е степенният показател на x, а не коефициент.")],
    hints=["Коефициентът е числото, с което започва едночленът.", "Не забравяй знака пред числото.", "Числото пред x²y е −3."],
    solution="Едночленът −3x²y = (−3)·x²·y. Числовият множител е −3, затова коефициентът е −3.",
    confirmation="Точно така: коефициентът включва и знака."),
  q("B3-P2", "B.normal-form", "Записва едночлен в нормален вид", "EXPRESSION", "FOUNDATIONAL", 45, 1, "LIKE_TERMS",
    "Запиши в нормален вид: 2x · 3x", answer="6x^2", form="NORMAL_FORM",
    distractors=[("5x", "REASONING", "При умножение коефициентите се умножават (2·3 = 6), а не се събират."),
                 ("6x", "TECHNICAL", "x·x = x², а не x. Показателите се събират: 1 + 1 = 2."),
                 ("5x^2", "REASONING", "Коефициентите се умножават: 2·3 = 6.")],
    hints=["Умножи числата отделно и буквите отделно.", "2·3 = ? и x·x = ?", "2·3 = 6, x·x = x²."],
    solution="2x · 3x = (2·3)·(x·x) = 6x².", input_hint="Пример: 6x^2"),

  # ---------------- guided practice
  q("B3-G1", LT, LT_OUT, "EXPRESSION", "FOUNDATIONAL", 30, 1, "LIKE_TERMS",
    "Събери подобните едночлени: 4a + 7a", answer="11a", form="NORMAL_FORM",
    distractors=[("11a^2", "LIKE_TERMS", "При събиране буквената част не се променя: a остава a, не става a²."),
                 ("28a", "REASONING", "Коефициентите се събират (4 + 7), не се умножават.")],
    hints=["Буквената част и на двата едночлена е a.", "Събери само коефициентите: 4 + 7.", "4a + 7a = (4 + 7)a."],
    solution="4a + 7a = (4 + 7)a = 11a. Буквената част остава същата.",
    confirmation="Вярно! Събра коефициентите и запази буквената част."),
  q("B3-G2", LT, LT_OUT, "EXPRESSION", "INTERMEDIATE", 40, 1, "SIGN",
    "Опрости: 5x²y − 8x²y", answer="-3x^2y", form="NORMAL_FORM",
    distractors=[("3x^2y", "SIGN", "5 − 8 е отрицателно число: −3."),
                 ("-3", "LIKE_TERMS", "Буквената част x²y не изчезва — тя остава в отговора."),
                 ("-3x^4y^2", "LIKE_TERMS", "При събиране и изваждане степените не се променят.")],
    hints=["Двата едночлена имат еднаква буквена част x²y.", "Пресметни 5 − 8.", "5 − 8 = −3, затова отговорът е −3 по x²y."],
    solution="5x²y − 8x²y = (5 − 8)x²y = −3x²y."),
  q("B3-G3", LT, LT_OUT, "EXPRESSION", "INTERMEDIATE", 60, 1, "LIKE_TERMS",
    "Приведи подобните едночлени: 3a − 2b + 5a + 4b", answer="8a+2b", form="NORMAL_FORM",
    distractors=[("10ab", "LIKE_TERMS", "a и b са различни буквени части: 8a и 2b не се събират в едно."),
                 ("8a-6b", "SIGN", "−2b + 4b = +2b. Внимавай със знаците."),
                 ("2a+2b", "SIGN", "3a + 5a = 8a.")],
    hints=["Подреди: членовете с a заедно, с b заедно.", "(3a + 5a) + (−2b + 4b).", "3a + 5a = 8a, −2b + 4b = 2b."],
    solution="3a − 2b + 5a + 4b = (3a + 5a) + (−2b + 4b) = 8a + 2b. 8a и 2b не са подобни, затова това е крайният отговор."),

  # ---------------- ungraded knowledge check (5 items)
  q("B3-C1", LT, LT_OUT, "SINGLE_CHOICE", "FOUNDATIONAL", 30, 1, "LIKE_TERMS",
    "Кой едночлен е подобен на 3x²y?",
    options=[("a", "3xy²"), ("b", "−x²y"), ("c", "3x²"), ("d", "x²y²")], correct="b",
    distractors=[("a", "LIKE_TERMS", "В 3xy² степените на x и y са разменени — буквените части са различни."),
                 ("c", "LIKE_TERMS", "Еднаквият коефициент не е важен. В 3x² липсва y."),
                 ("d", "LIKE_TERMS", "В x²y² степента на y е 2, а в 3x²y е 1.")],
    hints=["Сравни само буквените части, не коефициентите.", "Търсим точно x²y — x на втора и y на първа.", "−x²y има буквена част x²y."],
    solution="Подобни са едночлени с еднаква буквена част. Буквената част на 3x²y е x²y; само −x²y има същата."),
  q("B3-C2", LT, LT_OUT, "EXPRESSION", "FOUNDATIONAL", 30, 1, "LIKE_TERMS",
    "Опрости: 7m − m", answer="6m", form="NORMAL_FORM",
    distractors=[("7", "LIKE_TERMS", "m = 1·m. Изваждаме коефициентите: 7 − 1 = 6, и пазим m."),
                 ("7m", "LIKE_TERMS", "m не е нула — коефициентът ѝ е 1. 7 − 1 = 6."),
                 ("6", "LIKE_TERMS", "Буквената част m остава в отговора.")],
    hints=["Какъв е коефициентът на m?", "m = 1·m.", "7m − 1m = (7 − 1)m."],
    solution="7m − m = 7m − 1·m = (7 − 1)m = 6m."),
  q("B3-C3", LT, LT_OUT, "EXPRESSION", "INTERMEDIATE", 45, 1, "SIGN",
    "Опрости: −2ab + 5ab − 4ab", answer="-ab", form="NORMAL_FORM",
    distractors=[("ab", "SIGN", "−2 + 5 − 4 = −1, затова резултатът е −ab."),
                 ("-11ab", "SIGN", "+5ab е положителен: −2 + 5 − 4 = −1."),
                 ("-1", "LIKE_TERMS", "Буквената част ab остава.")],
    hints=["Всички членове имат буквена част ab.", "Пресметни −2 + 5 − 4.", "−2 + 5 = 3, 3 − 4 = −1."],
    solution="−2ab + 5ab − 4ab = (−2 + 5 − 4)ab = −1·ab = −ab."),
  q("B3-C4", LT, LT_OUT, "EXPRESSION", "FOUNDATIONAL", 30, 1, "LIKE_TERMS",
    "Събери: 0,5x + 1,5x", answer="2x", form="NORMAL_FORM",
    distractors=[("2x^2", "LIKE_TERMS", "Буквената част x не се променя при събиране."),
                 ("1,5x", "TECHNICAL", "0,5 + 1,5 = 2.")],
    hints=["Можеш да пишеш десетична запетая или точка.", "Събери 0,5 + 1,5.", "0,5 + 1,5 = 2."],
    solution="0,5x + 1,5x = (0,5 + 1,5)x = 2x."),
  q("B3-C5", LT, LT_OUT, "EXPRESSION", "INTERMEDIATE", 60, 1, "LIKE_TERMS",
    "Приведи подобните едночлени: 2x² + 3x − x² + x", answer="x^2+4x", form="NORMAL_FORM",
    distractors=[("5x^3", "LIKE_TERMS", "x² и x не са подобни — събирай само еднакви буквени части."),
                 ("x^2+3x", "LIKE_TERMS", "3x + x = 4x (x има коефициент 1)."),
                 ("3x^2+4x", "SIGN", "2x² − x² = x², защото −x² е с минус.")],
    hints=["Групирай x² с x² и x с x.", "(2x² − x²) + (3x + x).", "2x² − x² = x², 3x + x = 4x."],
    solution="2x² + 3x − x² + x = (2x² − x²) + (3x + x) = x² + 4x."),

  # ---------------- extra practice for "similar question" (same skill, not used in tests)
  q("B3-S1", LT, LT_OUT, "EXPRESSION", "FOUNDATIONAL", 30, 1, "LIKE_TERMS",
    "Опрости: 9y − 4y", answer="5y", form="NORMAL_FORM",
    distractors=[("5", "LIKE_TERMS", "Буквената част y остава."), ("13y", "SIGN", "Това е изваждане: 9 − 4 = 5.")],
    hints=["Еднаква буквена част: y.", "9 − 4 = ?", "(9 − 4)y = 5y."], solution="9y − 4y = (9 − 4)y = 5y."),
  q("B3-S2", LT, LT_OUT, "EXPRESSION", "FOUNDATIONAL", 30, 1, "SIGN",
    "Опрости: −x + 4x", answer="3x", form="NORMAL_FORM",
    distractors=[("-5x", "SIGN", "−1 + 4 = 3."), ("4x", "LIKE_TERMS", "−x има коефициент −1, не 0.")],
    hints=["Какъв е коефициентът на −x?", "−x = −1·x.", "−1 + 4 = 3."], solution="−x + 4x = (−1 + 4)x = 3x."),
  q("B3-S3", LT, LT_OUT, "EXPRESSION", "INTERMEDIATE", 45, 1, "LIKE_TERMS",
    "Опрости: 6ab² − 2ab² + ab²", answer="5ab^2", form="NORMAL_FORM",
    distractors=[("4ab^2", "LIKE_TERMS", "ab² има коефициент 1: 6 − 2 + 1 = 5."),
                 ("5a^3b^6", "LIKE_TERMS", "При събиране степените не се събират.")],
    hints=["Всички имат буквена част ab².", "6 − 2 + 1 = ?", "(6 − 2 + 1)ab² = 5ab²."],
    solution="6ab² − 2ab² + ab² = (6 − 2 + 1)ab² = 5ab²."),
  q("B3-S4", LT, LT_OUT, "EXPRESSION", "INTERMEDIATE", 60, 1, "LIKE_TERMS",
    "Приведи подобните едночлени: 4p − 3q − p + 5q", answer="3p+2q", form="NORMAL_FORM",
    distractors=[("5pq", "LIKE_TERMS", "p и q са различни — не се събират."), ("5p+2q", "SIGN", "4p − p = 3p.")],
    hints=["Групирай p с p и q с q.", "(4p − p) + (−3q + 5q).", "3p + 2q."],
    solution="4p − 3q − p + 5q = (4p − p) + (−3q + 5q) = 3p + 2q."),
]

# ---------------------------------------------------------------- thematic test: monomials (12 MC / 5 SA / 3 MS)
T = []
def mc(key, skill, outcome, diff, misc, text, opts, correct, distractors, hints, solution):
    T.append(q(key, skill, outcome, "SINGLE_CHOICE", diff, 60, 1, misc, text, options=opts, correct=correct,
               distractors=distractors, hints=hints, solution=solution))

mc("BT1-01", "B.definition", "Разпознава едночлен", "FOUNDATIONAL", "REASONING", "Кой от изразите е едночлен?",
   [("a", "3x + 1"), ("b", "−5a²b"), ("c", "2 : x"), ("d", "x − y")], "b",
   [("a", "REASONING", "3x + 1 е сбор на два члена — това е многочлен."),
    ("c", "REASONING", "Деление на променлива дава дробен израз, а не едночлен."),
    ("d", "REASONING", "x − y е разлика на два члена.")],
   ["Едночленът е произведение на число и степени на променливи.", "Има ли събиране, изваждане или деление на буква?", "Само −5a²b е произведение."],
   "−5a²b = (−5)·a·a·b е произведение на число и променливи, затова е едночлен.")
mc("BT1-02", "B.definition", "Определя коефициент на едночлен", "FOUNDATIONAL", "SIGN", "Коефициентът на едночлена −x³y е:",
   [("a", "−1"), ("b", "1"), ("c", "3"), ("d", "0")], "a",
   [("b", "SIGN", "Минусът пред x³y е част от коефициента: −1."),
    ("c", "REASONING", "3 е степенният показател на x."), ("d", "REASONING", "Ако коефициентът беше 0, едночленът би бил 0.")],
   ["Какво число стои пред буквите?", "−x³y = (−1)·x³y.", "Коефициентът е −1."], "−x³y = (−1)·x³y, коефициентът е −1.")
mc("BT1-03", "B.normal-form", "Определя степен на едночлен", "FOUNDATIONAL", "REASONING", "Степента на едночлена 4x²y³ е:",
   [("a", "4"), ("b", "5"), ("c", "6"), ("d", "9")], "b",
   [("a", "REASONING", "4 е коефициентът, а не степента."), ("c", "REASONING", "Показателите се събират: 2 + 3, а не 2·3."),
    ("d", "REASONING", "Коефициентът не участва в степента.")],
   ["Степента е сборът на показателите на променливите.", "Показателите са 2 и 3.", "2 + 3 = 5."], "Степента е 2 + 3 = 5.")
mc("BT1-04", "B.normal-form", "Записва едночлен в нормален вид", "INTERMEDIATE", "SIGN", "Нормалният вид на 2a · (−3) · a² · b е:",
   [("a", "−6a³b"), ("b", "−6a²b"), ("c", "6a³b"), ("d", "−5a³b")], "a",
   [("b", "TECHNICAL", "a · a² = a³ (показателите се събират: 1 + 2)."), ("c", "SIGN", "2·(−3) = −6 — произведението е отрицателно."),
    ("d", "REASONING", "Числата се умножават: 2·(−3) = −6.")],
   ["Умножи числата, после еднаквите букви.", "2·(−3) = ?, a·a² = ?", "−6 и a³, после b."], "2·(−3)·a·a²·b = −6a³b.")
mc("BT1-05", "B.like-terms", "Разпознава подобни едночлени", "FOUNDATIONAL", "LIKE_TERMS", "Кой едночлен е подобен на 5xy²?",
   [("a", "5x²y"), ("b", "−xy²"), ("c", "5xy"), ("d", "xy²z")], "b",
   [("a", "LIKE_TERMS", "Степените на x и y са разменени."), ("c", "LIKE_TERMS", "Еднаквият коефициент не прави едночлените подобни."),
    ("d", "LIKE_TERMS", "Има допълнителна променлива z.")],
   ["Сравни буквените части.", "Търсим точно xy².", "−xy²."], "Подобни са едночлени с еднаква буквена част; xy² има само −xy².")
mc("BT1-06", "B.like-terms", "Събира и изважда подобни едночлени", "INTERMEDIATE", "LIKE_TERMS", "3x² − 7x² е равно на:",
   [("a", "−4x²"), ("b", "4x²"), ("c", "−4x⁴"), ("d", "−4")], "a",
   [("b", "SIGN", "3 − 7 = −4."), ("c", "LIKE_TERMS", "При изваждане степента не се променя."), ("d", "LIKE_TERMS", "x² остава в отговора.")],
   ["Буквената част е еднаква.", "3 − 7 = ?", "(3 − 7)x² = −4x²."], "3x² − 7x² = (3 − 7)x² = −4x².")
mc("BT1-07", "B.multiplication", "Умножава едночлени", "INTERMEDIATE", "SIGN", "(−2x³) · (5x²) е равно на:",
   [("a", "−10x⁵"), ("b", "−10x⁶"), ("c", "3x⁵"), ("d", "10x⁵")], "a",
   [("b", "TECHNICAL", "x³·x² = x⁵ — показателите се събират."), ("c", "REASONING", "Коефициентите се умножават, не се събират."),
    ("d", "SIGN", "Отрицателно по положително е отрицателно.")],
   ["Умножи коефициентите и степените поотделно.", "(−2)·5 = ?, x³·x² = ?", "−10 и x⁵."], "(−2)·5 = −10, x³·x² = x⁵ → −10x⁵.")
mc("BT1-08", "B.multiplication", "Умножава едночлени", "INTERMEDIATE", "SIGN", "3ab · (−ab²) · 2a е равно на:",
   [("a", "−6a³b³"), ("b", "−6a²b³"), ("c", "6a³b³"), ("d", "−5a³b³")], "a",
   [("b", "TECHNICAL", "a·a·a = a³ — има три множителя a."), ("c", "SIGN", "Има един отрицателен множител — произведението е отрицателно."),
    ("d", "REASONING", "3·(−1)·2 = −6.")],
   ["Преброй множителите a и b.", "Коефициенти: 3·(−1)·2.", "−6, a³, b³."], "3·(−1)·2 = −6; a·a·a = a³; b·b² = b³ → −6a³b³.")
mc("BT1-09", "B.powers", "Степенува едночлен", "INTERMEDIATE", "SIGN", "(−3x²)² е равно на:",
   [("a", "9x⁴"), ("b", "−9x⁴"), ("c", "6x⁴"), ("d", "9x²")], "a",
   [("b", "SIGN", "Четна степен на отрицателно число е положителна: (−3)² = 9."), ("c", "REASONING", "(−3)² = (−3)·(−3) = 9, не 2·3."),
    ("d", "TECHNICAL", "(x²)² = x⁴ — показателите се умножават.")],
   ["Степенувай всеки множител.", "(−3)² = ?, (x²)² = ?", "9 и x⁴."], "(−3x²)² = (−3)²·(x²)² = 9x⁴.")
mc("BT1-10", "B.powers", "Степенува едночлен", "ADVANCED", "SIGN", "(−2a³b)³ е равно на:",
   [("a", "−8a⁹b³"), ("b", "−6a⁹b³"), ("c", "8a⁹b³"), ("d", "−8a⁶b³")], "a",
   [("b", "REASONING", "(−2)³ = −8, не −2·3."), ("c", "SIGN", "Нечетна степен на отрицателно число е отрицателна."),
    ("d", "TECHNICAL", "(a³)³ = a⁹ — показателите се умножават.")],
   ["Всеки множител на трета степен.", "(−2)³ = ?, (a³)³ = ?", "−8, a⁹, b³."], "(−2)³·(a³)³·b³ = −8a⁹b³.")
mc("BT1-11", "B.division", "Дели едночлен на едночлен", "INTERMEDIATE", "REASONING", "12x⁵ : 4x² е равно на:",
   [("a", "3x³"), ("b", "8x³"), ("c", "3x⁷"), ("d", "48x⁷")], "a",
   [("b", "REASONING", "Коефициентите се делят: 12 : 4 = 3."), ("c", "TECHNICAL", "При деление показателите се изваждат: 5 − 2."),
    ("d", "REASONING", "Това е деление, не умножение.")],
   ["Раздели коефициентите и степените поотделно.", "12 : 4 = ?, x⁵ : x² = ?", "3 и x³."], "12 : 4 = 3; x⁵ : x² = x³ → 3x³.")
mc("BT1-12", "B.applications", "Прилага действията с едночлени", "ADVANCED", "REASONING",
   "Правоъгълник има страни 3a и 4ab. Лицето му е:",
   [("a", "12a²b"), ("b", "7a²b"), ("c", "12ab"), ("d", "14ab")], "a",
   [("b", "REASONING", "Лицето е произведение на страните: 3·4 = 12."), ("c", "TECHNICAL", "a·a = a²."),
    ("d", "REASONING", "Коефициентите се умножават (3·4 = 12), а a·a = a².")],
   ["Лице на правоъгълник = дължина · ширина.", "3a · 4ab.", "12a²b."], "S = 3a · 4ab = 12a²b.")

T.append(q("BT1-13", "B.like-terms", "Събира и изважда подобни едночлени", "EXPRESSION", "INTERMEDIATE", 60, 2, "SIGN",
   "Опрости: 4xy − 9xy + 2xy", answer="-3xy", form="NORMAL_FORM",
   distractors=[("3xy", "SIGN", "4 − 9 + 2 = −3."), ("-3", "LIKE_TERMS", "xy остава в отговора.")],
   hints=["Еднаква буквена част xy.", "4 − 9 + 2 = ?", "−3xy."], solution="(4 − 9 + 2)xy = −3xy."))
T.append(q("BT1-14", "B.normal-form", "Определя степен на едночлен", "NUMERIC", "FOUNDATIONAL", 45, 2, "REASONING",
   "Каква е степента на едночлена 7a³b²c?", answer="6",
   distractors=[("7", "REASONING", "7 е коефициентът."), ("5", "REASONING", "c има показател 1 — не го пропускай.")],
   hints=["Събери показателите на всички букви.", "c = c¹.", "3 + 2 + 1."], solution="3 + 2 + 1 = 6."))
T.append(q("BT1-15", "B.multiplication", "Умножава едночлени", "EXPRESSION", "INTERMEDIATE", 75, 2, "SIGN",
   "Умножи и запиши в нормален вид: (−0,5x²y) · (4xy³)", answer="-2x^3y^4", form="NORMAL_FORM",
   distractors=[("2x^3y^4", "SIGN", "(−0,5)·4 = −2."), ("-2x^2y^3", "TECHNICAL", "x²·x = x³ и y·y³ = y⁴.")],
   hints=["Умножи коефициентите: (−0,5)·4.", "Събери показателите при еднакви основи.", "−2, x³, y⁴."],
   solution="(−0,5)·4 = −2; x²·x = x³; y·y³ = y⁴ → −2x³y⁴."))
T.append(q("BT1-16", "B.division", "Дели едночлен на едночлен", "EXPRESSION", "ADVANCED", 75, 2, "SIGN",
   "Раздели: (−18a⁴b³) : (6a²b)", answer="-3a^2b^2", form="NORMAL_FORM",
   distractors=[("3a^2b^2", "SIGN", "Отрицателно делено на положително е отрицателно."),
                ("-3a^2b^3", "TECHNICAL", "b³ : b = b²."), ("-12a^2b^2", "REASONING", "Коефициентите се делят: −18 : 6 = −3.")],
   hints=["Раздели коефициентите, после степените.", "−18 : 6 = ?, a⁴ : a² = ?, b³ : b = ?", "−3a²b²."],
   solution="−18 : 6 = −3; a⁴ : a² = a²; b³ : b = b² → −3a²b²."))
T.append(q("BT1-17", "B.applications", "Намира числена стойност на едночлен", "NUMERIC", "FOUNDATIONAL", 60, 2, "SIGN",
   "Намери числената стойност на −2x²y при x = −1 и y = 3.", answer="-6",
   distractors=[("6", "SIGN", "(−1)² = 1, после −2·1·3 = −6."), ("-12", "ORDER_OF_OPERATIONS", "Първо степенувай: (−1)² = 1.")],
   hints=["Замести x с (−1) — в скоби.", "(−1)² = 1.", "−2·1·3."], solution="−2·(−1)²·3 = −2·1·3 = −6."))

T.append(q("BT1-18", "B.like-terms", "Привежда подобни едночлени", "STEPS", "INTERMEDIATE", 180, 3, "LIKE_TERMS",
   "Опрости стъпка по стъпка. Всеки ред трябва да е равен на предишния: 3x²y − 2xy² + 5x²y + 4xy² − x²y",
   answer="7x^2y+2xy^2", form="NORMAL_FORM",
   parts=[{"id": "start", "type": "EXPRESSION", "answer": "3x^2y-2xy^2+5x^2y+4xy^2-x^2y", "points": 0}],
   start="3x^2y - 2xy^2 + 5x^2y + 4xy^2 - x^2y",
   distractors=[("9x^2y+2xy^2", "SIGN", "Внимавай за −x²y: 3 + 5 − 1 = 7."),
                ("7x^2y-6xy^2", "SIGN", "−2xy² + 4xy² = +2xy²."), ("9x^3y^3", "LIKE_TERMS", "x²y и xy² не са подобни.")],
   hints=["Подчертай членовете с x²y и тези с xy².", "(3 + 5 − 1)x²y + (−2 + 4)xy².", "7x²y + 2xy²."],
   solution="3x²y + 5x²y − x²y = 7x²y; −2xy² + 4xy² = 2xy². Отговор: 7x²y + 2xy².",
   rubric="3 т. за верни преходи и краен отговор в нормален вид; до 1,5 т. за верни преходи без довършване."))
T.append(q("BT1-19", "B.powers", "Умножава и степенува едночлени", "STRUCTURED", "INTERMEDIATE", 180, 3, "TECHNICAL",
   "Дадени са A = 2x²y и B = −3xy³.",
   part_prompts=[{"id": "a", "label": "а) A · B", "type": "EXPRESSION", "points": 1},
                 {"id": "b", "label": "б) A²", "type": "EXPRESSION", "points": 1},
                 {"id": "c", "label": "в) Степента на A · B", "type": "NUMERIC", "points": 1}],
   parts=[{"id": "a", "type": "EXPRESSION", "answer": "-6x^3y^4", "form": "NORMAL_FORM", "points": 1},
          {"id": "b", "type": "EXPRESSION", "answer": "4x^4y^2", "form": "NORMAL_FORM", "points": 1},
          {"id": "c", "type": "NUMERIC", "answer": "7", "points": 1}],
   distractors=[("6x^3y^4", "SIGN", "2·(−3) = −6."), ("4x^4y", "TECHNICAL", "y² — и y се степенува."),
                ("2x^4y^2", "REASONING", "2² = 4.")],
   hints=["При умножение събираш показателите.", "При степенуване умножаваш показателите и степенуваш коефициента.", "A·B = −6x³y⁴; степента е 3 + 4."],
   solution="а) 2·(−3)·x²·x·y·y³ = −6x³y⁴. б) (2x²y)² = 4x⁴y². в) 3 + 4 = 7.",
   rubric="По 1 т. за всяка вярна част."))
T.append(q("BT1-20", "B.applications", "Прилага действията с едночлени в задача", "STRUCTURED", "ADVANCED", 240, 3, "REASONING",
   "Правоъгълен паралелепипед има ръбове 2a, 3ab и 4b.",
   part_prompts=[{"id": "a", "label": "а) Запиши обема като едночлен", "type": "EXPRESSION", "points": 1},
                 {"id": "b", "label": "б) Пресметни обема при a = 1 и b = 0,5", "type": "NUMERIC", "points": 1},
                 {"id": "c", "label": "в) Колко пъти се увеличава обемът, ако a се удвои?", "type": "NUMERIC", "points": 1}],
   parts=[{"id": "a", "type": "EXPRESSION", "answer": "24a^2b^2", "form": "NORMAL_FORM", "points": 1},
          {"id": "b", "type": "NUMERIC", "answer": "6", "points": 1},
          {"id": "c", "type": "NUMERIC", "answer": "4", "points": 1}],
   distractors=[("24ab", "TECHNICAL", "a·a = a² и b·b = b²."), ("2", "REASONING", "Обемът зависи от a², затова при удвояване на a той става 4 пъти по-голям.")],
   hints=["Обемът е произведение на трите ръба.", "V = 24a²b². Замести a = 1, b = 0,5.", "Ако a → 2a, то a² → 4a²."],
   solution="а) V = 2a·3ab·4b = 24a²b². б) 24·1²·0,5² = 24·0,25 = 6. в) (2a)² = 4a², обемът става 4 пъти по-голям.",
   rubric="По 1 т. за всяка вярна част."))

assert len(T) == 20

lesson = {
  "lessonKey": "B3",
  "content": {
    "context": {"title": "Пъзел с плочки",
      "text": "Мира подрежда плочки: 4 сини квадрата, 3 зелени правоъгълника и още 2 сини квадрата. Колко сини квадрата има? Лесно — 6. Но можем ли да „съберем“ квадратите с правоъгълниците в едно число? Не: те са различни видове. Точно така работят подобните едночлени."},
    "objectives": ["След този урок ще можеш да разпознаваш подобни едночлени.",
                   "След този урок ще можеш да събираш и изваждаш подобни едночлени.",
                   "След този урок ще можеш да привеждаш подобните членове в израз."],
    "prerequisiteCheck": {"intro": "Да проверим две неща, които ще ти трябват.", "questionKeys": ["B3-P1", "B3-P2"]},
    "explanation": [
      "Помниш ли, че всеки едночлен има две части: коефициент (числото) и буквена част (произведението на променливите)?",
      "Два едночлена са подобни, когато буквените им части са напълно еднакви: същите букви със същите степени. Коефициентите може да са различни.",
      "Например 5x²y и −2x²y са подобни. А 5x²y и 5xy² не са — степените на x и y са разменени.",
      "Подобните едночлени се събират като плочки от един вид: събираме коефициентите, а буквената част остава същата. 5x²y + (−2x²y) = 3x²y.",
      "Защо е вярно? Защото това е разпределителното свойство: 5·M + (−2)·M = (5 − 2)·M, където M = x²y."],
    "definition": {"title": "Подобни едночлени",
      "text": "Едночлени в нормален вид с еднаква буквена част се наричат подобни. Сборът на подобни едночлени е едночлен със същата буквена част и коефициент, равен на сбора от коефициентите: a·M + b·M = (a + b)·M."},
    "visual": {"kind": "tiles", "title": "Сортиране по вид",
      "description": "Три кутии. В първата са едночлените 4x², −x² и 2x² — общо 5x². Във втората са 3xy и −5xy — общо −2xy. В третата е само 7y. Едночлени от различни кутии не се събират.",
      "groups": [{"label": "x²", "items": ["4x²", "−x²", "2x²"], "colour": "purple"},
                 {"label": "xy", "items": ["3xy", "−5xy"], "colour": "blue"},
                 {"label": "y", "items": ["7y"], "colour": "coral"}]},
    "workedExamples": [
      {"level": "BASIC", "problem": "6a + 3a", "answer": "9a", "steps": [
        {"expression": "6a + 3a", "why": "И двата члена имат буквена част a, значи са подобни."},
        {"expression": "(6 + 3)a", "why": "Разпределително свойство: изнасяме общата буквена част a."},
        {"expression": "9a", "why": "Пресмятаме 6 + 3 = 9; буквената част не се променя."}]},
      {"level": "TYPICAL", "problem": "2x² − 5x + 4x² + 3x", "answer": "6x² − 2x", "steps": [
        {"expression": "(2x² + 4x²) + (−5x + 3x)", "why": "Разместително и съдружително свойство: групираме подобните членове, като всеки носи знака пред себе си."},
        {"expression": "(2 + 4)x² + (−5 + 3)x", "why": "Изнасяме буквените части x² и x."},
        {"expression": "6x² − 2x", "why": "6x² и −2x не са подобни (x² ≠ x), затова спираме тук."}]},
      {"level": "CHALLENGE", "problem": "3ab² − a²b + 0,5ab² − 2a²b − ab²", "answer": "2,5ab² − 3a²b", "steps": [
        {"expression": "(3ab² + 0,5ab² − ab²) + (−a²b − 2a²b)", "why": "ab² и a²b са различни буквени части — внимаваме къде е квадратът."},
        {"expression": "(3 + 0,5 − 1)ab² + (−1 − 2)a²b", "why": "−ab² има коефициент −1, а −a²b — също −1."},
        {"expression": "2,5ab² − 3a²b", "why": "Пресмятаме коефициентите. Двата резултата не са подобни."}]}],
    "watchOut": [
      {"wrong": "3x + 2x = 5x²", "right": "3x + 2x = 5x", "why": "При събиране буквената част не се променя. Показателите се събират само при умножение."},
      {"wrong": "4x² + 3x = 7x³", "right": "4x² + 3x не се опростява", "why": "x² и x не са подобни, затова не ги събираме."},
      {"wrong": "7m − m = 7", "right": "7m − m = 6m", "why": "m има коефициент 1, а буквената част остава."},
      {"wrong": "5a − 8a = 3a", "right": "5a − 8a = −3a", "why": "5 − 8 е отрицателно. Пази знаците."}],
    "guidedPractice": [
      {"questionKey": "B3-G1", "support": "FULL", "coaching": "Буквените части са еднакви — събери само числата пред тях."},
      {"questionKey": "B3-G2", "support": "PARTIAL", "coaching": "Внимавай със знака на разликата."},
      {"questionKey": "B3-G3", "support": "NONE", "coaching": "Сега сам(а): групирай и приведи."}],
    "check": {"intro": "Кратка проверка без оценка — само за теб.", "questionKeys": ["B3-C1", "B3-C2", "B3-C3", "B3-C4", "B3-C5"]},
    "summary": ["Подобните едночлени имат еднаква буквена част.",
                "Събираме/изваждаме коефициентите, а буквената част остава същата.",
                "Неподобни членове остават отделно — така изразът е в нормален вид.",
                "Проверка: замести с число (напр. x = 2) в началния и крайния израз — трябва да получиш едно и също."],
    "nextStep": {"lessonKey": "B4", "text": "Следва: умножение на едночлени — там показателите вече се събират."}}}

tests = [{
  "testKey": "BT1", "title": "Ден 1 · Едночлени — тест 1", "kind": "THEMATIC", "path": "B",
  "blueprint": "Тематичен тест (20)", "timeLimitMin": 40, "hintPolicy": "NONE", "reviewMoment": "AFTER_SUBMIT",
  "questionKeys": [x["key"] for x in T]}]

blueprints = [
  {"name": "Тематичен тест (20)", "academicYear": "2026/2027", "timeLimitMin": 40,
   "composition": {"MULTIPLE_CHOICE": 12, "SHORT_ANSWER": 5, "MULTI_STEP": 3}},
  {"name": "Обобщителен тест (24)", "academicYear": "2026/2027", "timeLimitMin": 60,
   "composition": {"MULTIPLE_CHOICE": 14, "SHORT_ANSWER": 7, "MULTI_STEP": 3}}]

import sys
sys.path.insert(0, os.path.dirname(__file__))
import gen_tests
gen_q, gen_t = gen_tests.generate({x["draft"]["prompt"]["text"] for x in lesson_questions + T})
tests += gen_t
out = {"questions": lesson_questions + T + gen_q, "lessons": [lesson], "tests": tests, "blueprints": blueprints}
with open(os.path.join(os.path.dirname(__file__), "generated-checks.json"), "w", encoding="utf-8") as f:
    json.dump(gen_tests.SIDE, f, ensure_ascii=False)
path = os.path.join(os.path.dirname(__file__), "..", "backend", "src", "main", "resources", "seed", "content.json")
with open(path, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
fpath = os.path.join(os.path.dirname(__file__), "..", "frontend", "src", "local", "data", "content.json")
with open(fpath, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("questions:", len(out["questions"]), "test items:", len(T))
