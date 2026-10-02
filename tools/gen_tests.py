# Generates practice tests (3 per topic + mixed) for the 7-day programme.
# Every answer is computed with exact polynomial arithmetic; tools/validate_content.ts then
# re-checks every key and every wrong option with the app's own maths engine.
import math
import random
from fractions import Fraction as F

SIDE = {}  # key -> ascii expected value of the correct option (for automatic verification)

SRC = "Оригинално съдържание, Math Mission (2026), генерирано по шаблон и проверено автоматично"
VARS_ORDER = "abcmnpqxyz"

# ------------------------------------------------------------------ exact polynomials

class P:
    def __init__(self, terms=None):
        self.t = {}
        for m, c in (terms or {}).items():
            if c != 0:
                self.t[m] = F(c)

    @staticmethod
    def mono(_coef, /, **pw):
        return P({tuple(sorted((v, e) for v, e in pw.items() if e)): F(_coef)})

    @staticmethod
    def const(c):
        return P({(): F(c)})

    def __add__(self, o):
        r = dict(self.t)
        for m, c in o.t.items():
            r[m] = r.get(m, 0) + c
        return P(r)

    def __neg__(self):
        return P({m: -c for m, c in self.t.items()})

    def __sub__(self, o):
        return self + (-o)

    def __mul__(self, o):
        if not isinstance(o, P):
            o = P.const(o)
        r = {}
        for m1, c1 in self.t.items():
            for m2, c2 in o.t.items():
                d = dict(m1)
                for v, e in m2:
                    d[v] = d.get(v, 0) + e
                m = tuple(sorted(d.items()))
                r[m] = r.get(m, 0) + c1 * c2
        return P(r)

    __rmul__ = __mul__

    def __pow__(self, n):
        r = P.const(1)
        for _ in range(n):
            r = r * self
        return r

    def eval(self, **vals):
        s = F(0)
        for m, c in self.t.items():
            x = c
            for v, e in m:
                x *= F(vals[v]) ** e
            s += x
        return s

    def degree(self):
        return max((sum(e for _, e in m) for m in self.t), default=0)

    def __eq__(self, o):
        return isinstance(o, P) and self.t == o.t

    def __hash__(self):
        return hash(tuple(sorted(self.t.items())))

    def ordered(self):
        def key(m):
            deg = sum(e for _, e in m)
            return (-deg, [(VARS_ORDER.index(v), -e) for v, e in m])
        return sorted(self.t.items(), key=lambda kv: key(kv[0]))

    def fmt(self, pretty=True):
        if not self.t:
            return "0"
        out = []
        for i, (m, c) in enumerate(self.ordered()):
            neg = c < 0
            a = -c if neg else c
            if pretty:
                lit = "".join(v + (sup(e) if e > 1 else "") for v, e in m)
            else:
                lit = "".join(v + (f"^{e}" if e > 1 else "") for v, e in m)
            num = num_str(a, pretty)
            body = (num if (a != 1 or not lit) else "") + lit
            if not lit:
                body = num
            if i == 0:
                out.append(("−" if pretty else "-") + body if neg else body)
            else:
                out.append((" − " if pretty else "-") + body if neg else (" + " if pretty else "+") + body)
        return "".join(out)


def sup(e):
    return "".join("⁰¹²³⁴⁵⁶⁷⁸⁹"[int(d)] for d in str(e))


def num_str(q, pretty=True):
    q = F(q)
    if q.denominator == 1:
        return str(q.numerator)
    d = q.denominator
    while d % 2 == 0:
        d //= 2
    while d % 5 == 0:
        d //= 5
    if d == 1:
        s = format(float(q), "g")
        return s.replace(".", ",") if pretty else s
    return f"{q.numerator}/{q.denominator}" if not pretty else f"{q.numerator}/{q.denominator}"


def val(q, pretty=True):
    q = F(q)
    s = num_str(abs(q), pretty)
    return ("−" if pretty else "-") + s if q < 0 else s


def mono_text(_coef, /, pretty=True, **pw):
    return P.mono(_coef, **pw).fmt(pretty)


def signed(q):
    """' + 5' / ' − 5' for writing a sum term by term."""
    q = F(q)
    return f" − {val(-q)}" if q < 0 else f" + {val(q)}"


def paren(q):
    """Number as it is substituted: negatives in brackets."""
    return f"({val(q)})" if q < 0 else val(q)

# ------------------------------------------------------------------ question builders

LETTERS = "abcd"


def mc(rng, key, skill, outcome, diff, misc, text, correct, wrongs, hints, solution, path, secs=60, expect=None):
    """correct: display text; wrongs: list of (display, misconception, explanation)."""
    seen = {correct}
    clean = []
    for w in wrongs:
        if w[0] not in seen:
            seen.add(w[0])
            clean.append(w)
    if len(clean) < 3:
        raise ValueError("not enough distinct distractors")
    clean = clean[:3]
    opts = [(correct, None)] + [(w[0], w) for w in clean]
    rng.shuffle(opts)
    options, distractors, correct_id = [], [], None
    for i, (txt, w) in enumerate(opts):
        oid = LETTERS[i]
        options.append({"id": oid, "text": txt})
        if w is None:
            correct_id = oid
        else:
            distractors.append({"match": oid, "misconception": w[1], "explanation": w[2]})
    if expect is not None:
        SIDE[key] = expect
    return q(key, skill, outcome, "SINGLE_CHOICE", diff, secs, 1, misc, text, path, options=options, correct=correct_id,
             distractors=distractors, hints=hints, solution=solution)


def q(key, skill, outcome, rtype, diff, secs, pts, misc, text, path, options=None, correct=None, answer=None, form=None,
      distractors=(), hints=(), solution="", parts=None, part_prompts=None, start=None, rubric=None):
    prompt = {"text": text}
    if options:
        prompt["options"] = options
    if part_prompts:
        prompt["parts"] = part_prompts
    if start:
        prompt["startExpression"] = start
    k = {"solution": solution, "distractors": [d for d in distractors if d.get("match") != answer], "defaultMisconception": misc}
    if correct:
        k["correctOptionId"] = correct
    if answer is not None:
        k["answer"] = answer
    if form:
        k["form"] = form
    if parts:
        k["parts"] = parts
    if rubric:
        k["rubric"] = rubric
    assert len(hints) == 3, key
    return {"key": key, "draft": {"path": path, "skill": skill, "learningOutcome": outcome, "responseType": rtype,
                                  "difficulty": diff, "estimatedSeconds": secs, "maxPoints": pts, "misconception": misc,
                                  "prompt": prompt, "key": k, "hints": list(hints), "sourceDeclaration": SRC}}


def nz(rng, lo, hi, exclude=(0,)):
    while True:
        v = rng.randint(lo, hi)
        if v not in exclude:
            return v

# ------------------------------------------------------------------ path A: rational expressions

def a_integral(rng, key):
    k, m = nz(rng, 2, 9), nz(rng, 1, 9)
    good = f"x²/{k} − {m}x"
    wrongs = [(f"{k}/(x − {m})", "REASONING", "Тук делим на израз с променлива (x − " + str(m) + ") — това е дробен израз."),
              (f"(a + {m})/a", "REASONING", "Делим на променливата a — изразът е дробен."),
              (f"{k}/x + {m}", "REASONING", f"Членът {k}/x съдържа деление на x — изразът е дробен.")]
    return mc(rng, key, "A.rational-integral", "Различава цели и дробни рационални изрази", "FOUNDATIONAL", "REASONING",
              "Кой от изразите е цял (не съдържа деление на израз с променлива)?", good, wrongs,
              ["Цял израз: няма деление на променлива.", "Делението на число е позволено (x²/3 = ⅓·x²).", f"Само в „{good}“ делим на число."],
              f"В „{good}“ делим само на числото {k}, затова изразът е цял. Останалите съдържат деление на израз с променлива.", "A")


def a_value_quadratic(rng, key):
    a, b, c = nz(rng, -4, 5), nz(rng, -6, 6), rng.randint(-9, 9)
    v = nz(rng, -4, -1)
    e = P.mono(a, x=2) + P.mono(b, x=1) + P.const(c)
    right = e.eval(x=v)
    wrong_sq = a * (-(v * v)) + b * v + c          # −v² instead of (−v)²
    wrong_ord = (a * v) ** 2 + b * v + c            # (a·x)² instead of a·x²
    wrong_c = a * v * v + b * v                     # forgot the constant
    wrong_sign = a * v * v - b * v + c
    wrongs = [(val(wrong_sq), "SIGN", f"Отрицателното число се замества в скоби: ({val(v)})² = {v*v}, не −{v*v}."),
              (val(wrong_ord), "ORDER_OF_OPERATIONS", "Първо степенуваме x, после умножаваме по коефициента."),
              (val(wrong_c), "TECHNICAL", "Не забравяй свободния член."),
              (val(wrong_sign), "SIGN", "Провери знака при умножението по отрицателното число.")]
    return mc(rng, key, "A.substitution", "Намира числена стойност на израз", "INTERMEDIATE", "SIGN",
              f"Каква е стойността на израза {e.fmt()} при x = {val(v)}?", val(right), wrongs,
              ["Замести x навсякъде с числото — в скоби.", f"({val(v)})² = {v*v}.", "Пресметни по реда на действията: степен, умножение, събиране."],
              f"{e.fmt()} при x = {val(v)}: {val(a)}·{paren(v)}² + {paren(b)}·{paren(v)}{signed(c) if c else ''} = {val(a*v*v)}{signed(b*v)}{signed(c) if c else ''} = {val(right)}.", "A", expect=val(right, False))


def a_value_two_vars(rng, key, pts=2):
    p, qq = nz(rng, 2, 6), nz(rng, 1, 5)
    a, b = nz(rng, -5, 5), nz(rng, -4, 4)
    e = P.mono(p, a=1, b=1) - P.mono(qq, b=2)
    right = e.eval(a=a, b=b)
    d = [{"match": val(p * a * b + qq * b * b, False), "misconception": "SIGN", "explanation": "Внимавай със знака пред втория член."},
         {"match": val(p * a * b, False), "misconception": "TECHNICAL", "explanation": "Не забравяй втория член."}]
    return q(key, "A.substitution", "Намира числена стойност на израз с две променливи", "NUMERIC", "INTERMEDIATE", 75, pts, "SIGN",
             f"Намери стойността на {e.fmt()} при a = {val(a)} и b = {val(b)}.", "A", answer=val(right, False), distractors=d,
             hints=["Замести всяка буква с числото ѝ — в скоби.", f"b² = {paren(b)}² = {b*b}.", "Първо умножи, после извади."],
             solution=f"{p}·{paren(a)}·{paren(b)} − {qq}·{paren(b)}² = {val(p*a*b)} − {qq*b*b} = {val(right)}.")


def a_no_meaning(rng, key):
    k, m = nz(rng, 2, 9), nz(rng, -7, 7)
    sign = "−" if m > 0 else "+"
    expr = f"{k}/(x {sign} {abs(m)})"
    wrongs = [(val(-m), "SIGN", f"Знаменателят x {sign} {abs(m)} е нула при x = {val(m)}, не при {val(-m)}."),
              ("0", "REASONING", "При x = 0 знаменателят не е нула."),
              (str(k), "REASONING", "Числителят не влияе — гледаме кога знаменателят е 0.")]
    return mc(rng, key, "A.rational-integral", "Определя кога дробен израз няма смисъл", "INTERMEDIATE", "REASONING",
              f"За коя стойност на x изразът {expr} няма смисъл?", val(m), wrongs,
              ["Израз няма смисъл, когато делим на нула.", "Кога знаменателят е 0?", f"x {sign} {abs(m)} = 0."],
              f"Знаменателят x {sign} {abs(m)} = 0 при x = {val(m)}. Тогава делим на нула и изразът няма смисъл.", "A", expect=val(m, False))


def a_order(rng, key):
    a, b, c = rng.randint(2, 9), rng.randint(2, 5), rng.randint(2, 4)
    right = a + b * c * c
    wrongs = [(str((a + b) * c * c), "ORDER_OF_OPERATIONS", "Умножението е преди събирането: първо b·c², после +a."),
              (str(a + (b * c) ** 2), "ORDER_OF_OPERATIONS", f"Степента е само на {c}: {b}·{c}² = {b}·{c*c}."),
              (str(a + b * c * 2), "TECHNICAL", f"{c}² = {c}·{c} = {c*c}, не {c}·2.")]
    return mc(rng, key, "A.order-of-operations", "Спазва реда на действията", "FOUNDATIONAL", "ORDER_OF_OPERATIONS",
              f"Пресметни: {a} + {b}·{c}²", str(right), wrongs,
              ["Ред: степени → умножение → събиране.", f"{c}² = {c*c}.", f"{b}·{c*c} = {b*c*c}."],
              f"{a} + {b}·{c}² = {a} + {b}·{c*c} = {a} + {b*c*c} = {right}.", "A", expect=str(right))


PHRASES = [
    ("сборът на удвоеното число a и квадрата на числото b", "2a + b²", [("(2a + b)²", "ORDER_OF_OPERATIONS", "На квадрат е само b, не целият сбор."),
      ("2(a + b²)", "BRACKETS", "Удвоено е само a."), ("2a + 2b", "TECHNICAL", "Квадрат на b е b², не 2b.")]),
    ("разликата на числото x и утроеното число y", "x − 3y", [("3(x − y)", "BRACKETS", "Утроено е само y."),
      ("3y − x", "REASONING", "Разликата на x и … започва с x."), ("x − y³", "TECHNICAL", "Утроено означава 3·y, не y³.")]),
    ("квадратът на сбора на числата m и n", "(m + n)²", [("m² + n²", "FORMULA_APPLICATION", "Квадратът на сбора е (m + n)², а не сбор от квадрати."),
      ("m + n²", "ORDER_OF_OPERATIONS", "На квадрат е целият сбор."), ("2(m + n)", "TECHNICAL", "Квадрат не означава удвояване.")]),
    ("половината от произведението на числата a и b", "ab/2", [("a/2 + b", "REASONING", "Половината е от произведението ab."),
      ("2ab", "TECHNICAL", "Половината означава деление на 2."), ("(a + b)/2", "REASONING", "Произведение означава умножение, не сбор.")]),
    ("сборът на числото p и половината от числото q", "p + q/2", [("(p + q)/2", "BRACKETS", "Половината е само от q."),
      ("2p + q", "TECHNICAL", "Половината означава деление на 2."), ("p/2 + q", "REASONING", "Половината е от q, не от p.")]),
    ("произведението на числото x и сбора на числата y и 5", "x(y + 5)", [("xy + 5", "BRACKETS", "Сборът y + 5 трябва да е в скоби."),
      ("x + y·5", "REASONING", "Произведение на x и сбора, не сбор."), ("5xy", "TECHNICAL", "Сборът y + 5 не е 5y.")]),
]


def a_compose(rng, key, idx):
    text, good, wrongs = PHRASES[idx % len(PHRASES)]
    return mc(rng, key, "A.applications", "Съставя израз по текстово условие", "INTERMEDIATE", "REASONING",
              f"Кой израз означава „{text}“?", good, wrongs,
              ["Раздели изречението на части: какво е последното действие?", "Последното действие определя вида на израза (сбор, разлика, произведение…).", f"Отговорът започва така: {good[:2]}…"],
              f"„{text}“ се записва като {good}.", "A")


def a_fraction_value(rng, key, pts=2):
    r = nz(rng, -4, 4)
    v = r + nz(rng, -3, 3)
    denom = v - r
    p = nz(rng, 1, 5)
    qq = denom * nz(rng, -5, 5) - p * v
    right = F(p * v + qq, v - r)
    return q(key, "A.substitution", "Намира стойност на дробен израз", "NUMERIC", "INTERMEDIATE", 75, pts, "SIGN",
             f"Пресметни стойността на ({P.mono(p, x=1).fmt()} {('+ ' + str(qq)) if qq >= 0 else ('− ' + str(-qq))})/(x {('− ' + str(r)) if r >= 0 else ('+ ' + str(-r))}) при x = {val(v)}.",
             "A", answer=val(right, False), distractors=[{"match": val(-right, False), "misconception": "SIGN", "explanation": "Провери знаците в числителя и знаменателя."}],
             hints=["Пресметни отделно числителя и знаменателя.", f"Знаменател: {val(v)} − ({val(r)}) = {val(v - r)}.", "Раздели числителя на знаменателя."],
             solution=f"Числител: {p}·{paren(v)} + ({val(qq)}) = {val(p*v+qq)}. Знаменател: {val(v-r)}. Стойност: {val(right)}.")


def a_structured_compare(rng, key):
    a, b = nz(rng, 1, 4), nz(rng, 1, 6)
    v1, v2 = nz(rng, -3, -1), nz(rng, 1, 3)
    e = P.mono(a, x=2) - P.mono(b, x=1)
    r1, r2 = e.eval(x=v1), e.eval(x=v2)
    return q(key, "A.substitution", "Сравнява стойности на израз", "STRUCTURED", "ADVANCED", 180, 3, "SIGN",
             f"Даден е изразът E = {e.fmt()}.", "A",
             part_prompts=[{"id": "a", "label": f"а) E при x = {val(v1)}", "type": "NUMERIC", "points": 1},
                           {"id": "b", "label": f"б) E при x = {val(v2)}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) По-голямата от двете стойности", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "NUMERIC", "answer": val(r1, False), "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(r2, False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(max(r1, r2), False), "points": 1}],
             distractors=[{"match": val(-r1, False), "misconception": "SIGN", "explanation": "Отрицателно число на квадрат е положително."}],
             hints=["Замести x в скоби.", f"({val(v1)})² = {v1*v1}.", "Сравни двете числа."],
             solution=f"а) {val(r1)}; б) {val(r2)}; в) по-голямата стойност е {val(max(r1, r2))}.", rubric="По 1 т. за всяка вярна част.")


def a_steps_value(rng, key):
    m1, k1, m2, k2 = nz(rng, 2, 5), nz(rng, 1, 6), nz(rng, 2, 4), nz(rng, 1, 6)
    v = nz(rng, 2, 6)
    start_pretty = f"{m1}({v} − {k1}) − {m2}({v} + {k2})"
    start = f"{m1}({v}-{k1})-{m2}({v}+{k2})"
    ans = m1 * (v - k1) - m2 * (v + k2)
    return q(key, "A.order-of-operations", "Пресмята стъпка по стъпка", "STEPS", "INTERMEDIATE", 150, 3, "SIGN",
             f"Пресметни стъпка по стъпка (всеки ред трябва да е равен на предишния): {start_pretty}", "A",
             answer=str(ans), parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": str(m1 * (v - k1) + m2 * (v + k2)), "misconception": "SIGN", "explanation": "Минусът пред втората скоба се отнася за целия ѝ резултат."}],
             hints=["Първо пресметни скобите.", f"{v} − {k1} = {val(v-k1)}, {v} + {k2} = {val(v+k2)}.", "После умножи и извади."],
             solution=f"{start_pretty} = {m1}·{paren(v-k1)} − {m2}·{paren(v+k2)} = {val(m1*(v-k1))} − {val(m2*(v+k2))} = {val(ans)}.",
             rubric="3 т. за верни преходи и верен резултат; до 1,5 т. за верни преходи без довършване.")

# ------------------------------------------------------------------ path B: monomials

def rand_mono(rng, vars_="xy", maxe=3, coef=(-9, 9)):
    chosen = sorted(rng.sample(vars_, rng.randint(1, len(vars_))))
    pw = {v: rng.randint(1, maxe) for v in chosen}
    return nz(rng, *coef), pw


def b_coefficient(rng, key):
    c, pw = rand_mono(rng, "abxy")
    m = mono_text(c, **pw)
    deg = sum(pw.values())
    wrongs = [(val(-c), "SIGN", "Знакът е част от коефициента."), (str(deg) if deg != c else str(deg + 10), "REASONING", "Това е степента, не коефициентът."),
              (P.mono(1, **pw).fmt(), "REASONING", "Това е буквената част.")]
    return mc(rng, key, "B.definition", "Определя коефициента на едночлен", "FOUNDATIONAL", "SIGN",
              f"Какъв е коефициентът на едночлена {m}?", val(c), wrongs,
              ["Коефициентът е числото пред буквите.", "Знакът се включва.", f"Числото пред буквите е {val(c)}."],
              f"{m} = ({val(c)})·{P.mono(1, **pw).fmt()}, коефициентът е {val(c)}.", "B", expect=val(c, False))


def b_degree_mc(rng, key):
    c, pw = rand_mono(rng, "abxy")
    while sum(pw.values()) < 2:
        c, pw = rand_mono(rng, "abxy")
    deg = sum(pw.values())
    prod = 1
    for e in pw.values():
        prod *= e
    wrongs = [(str(deg + abs(c)), "REASONING", "Коефициентът не участва в степента."), (str(max(pw.values())), "REASONING", "Събираме показателите на всички букви."),
              (str(prod if prod != deg else deg + 1), "REASONING", "Показателите се събират, не се умножават.")]
    return mc(rng, key, "B.normal-form", "Определя степен на едночлен", "FOUNDATIONAL", "REASONING",
              f"Каква е степента на едночлена {mono_text(c, **pw)}?", str(deg), wrongs,
              ["Степента е сборът от показателите.", "Буква без показател има показател 1.", f"Сбор: {' + '.join(str(e) for e in pw.values())}."],
              f"Степента е {' + '.join(str(e) for e in pw.values())} = {deg}.", "B", expect=str(deg))


def b_normal_form(rng, key):
    c1, c2 = nz(rng, -5, 5, (0, 1)), nz(rng, -4, 4, (0, 1))
    e1, e2 = rng.randint(1, 3), rng.randint(1, 3)
    right = P.mono(c1 * c2, a=e1 + e2, b=1)
    text = f"{val(c1)}a{sup(e1) if e1 > 1 else ''} · ({val(c2)}) · a{sup(e2) if e2 > 1 else ''} · b"
    wrongs = [(P.mono(c1 * c2, a=e1 * e2 if e1 * e2 != e1 + e2 else e1 + e2 + 1, b=1).fmt(), "TECHNICAL", "При умножение показателите се събират."),
              (P.mono(-c1 * c2, a=e1 + e2, b=1).fmt(), "SIGN", "Провери знака на произведението."),
              (P.mono(c1 + c2 if c1 + c2 not in (0, c1 * c2) else c1 * c2 + 1, a=e1 + e2, b=1).fmt(), "REASONING", "Числата се умножават, не се събират.")]
    return mc(rng, key, "B.normal-form", "Записва едночлен в нормален вид", "INTERMEDIATE", "SIGN",
              f"Нормалният вид на {text} е:", right.fmt(), wrongs,
              ["Умножи числата, после еднаквите букви.", f"{val(c1)}·({val(c2)}) = {val(c1*c2)}.", f"a{sup(e1)}·a{sup(e2)} = a{sup(e1+e2)}."],
              f"{val(c1)}·({val(c2)}) = {val(c1*c2)}; a{sup(e1)}·a{sup(e2)} = a{sup(e1+e2)} → {right.fmt()}.", "B", expect=right.fmt(False))


def b_like(rng, key):
    e1, e2 = rng.randint(1, 3), rng.randint(1, 3)
    while e1 == e2:
        e2 = rng.randint(1, 3)
    c = nz(rng, 2, 9)
    base = mono_text(c, x=e1, y=e2)
    good = mono_text(-nz(rng, 1, 7), x=e1, y=e2)
    wrongs = [(mono_text(c, x=e2, y=e1), "LIKE_TERMS", "Степените на x и y са разменени."),
              (mono_text(c, x=e1), "LIKE_TERMS", "Липсва y — буквените части са различни."),
              (mono_text(c, x=e1, y=e2, a=1), "LIKE_TERMS", "Има допълнителна буква.")]
    return mc(rng, key, "B.like-terms", "Разпознава подобни едночлени", "FOUNDATIONAL", "LIKE_TERMS",
              f"Кой едночлен е подобен на {base}?", good, wrongs,
              ["Сравни само буквените части.", "Еднакви букви с еднакви показатели.", f"Търсим {P.mono(1, x=e1, y=e2).fmt()}."],
              f"Подобен е {good}: буквената част е {P.mono(1, x=e1, y=e2).fmt()}.", "B")


def b_add_like(rng, key, pts=2):
    pw = {"a": rng.randint(1, 2), "b": rng.randint(1, 3)}
    c1, c2, c3 = nz(rng, 2, 9), nz(rng, 2, 9), nz(rng, 1, 9)
    total = c1 - c2 + c3
    if total == 0:
        c3 += 1
        total += 1
    m = lambda c: P.mono(c, **pw).fmt()
    ans = P.mono(total, **pw)
    return q(key, "B.like-terms", "Събира и изважда подобни едночлени", "EXPRESSION", "INTERMEDIATE", 60, pts, "SIGN",
             f"Опрости: {m(c1)} − {m(c2)} + {m(c3)}", "B", answer=ans.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": P.mono(c1 + c2 + c3, **pw).fmt(False), "misconception": "SIGN", "explanation": f"Вторият член е със знак минус: {c1} − {c2} + {c3}."},
                          {"match": val(total, False), "misconception": "LIKE_TERMS", "explanation": "Буквената част остава в отговора."}],
             hints=["Всички членове са подобни.", f"Пресметни {c1} − {c2} + {c3}.", f"= {total}, буквената част остава."],
             solution=f"({c1} − {c2} + {c3})·{P.mono(1, **pw).fmt()} = {ans.fmt()}.")


def b_multiply(rng, key):
    c1, c2 = nz(rng, -6, 6, (0, 1, -1)), nz(rng, -5, 5, (0, 1, -1))
    a1, a2, b1, b2 = rng.randint(1, 4), rng.randint(1, 3), rng.randint(0, 2), rng.randint(1, 3)
    m1, m2 = P.mono(c1, x=a1, y=b1), P.mono(c2, x=a2, y=b2)
    right = m1 * m2
    wrongs = [(P.mono(c1 * c2, x=a1 * a2 if a1 * a2 != a1 + a2 else a1 + a2 + 1, y=b1 + b2).fmt(), "TECHNICAL", "При умножение показателите се събират."),
              (P.mono(-c1 * c2, x=a1 + a2, y=b1 + b2).fmt(), "SIGN", "Провери знака на произведението."),
              (P.mono(c1 + c2 if c1 + c2 not in (0, c1 * c2) else c1 * c2 + 2, x=a1 + a2, y=b1 + b2).fmt(), "REASONING", "Коефициентите се умножават.")]
    return mc(rng, key, "B.multiplication", "Умножава едночлени", "INTERMEDIATE", "SIGN",
              f"({m1.fmt()}) · ({m2.fmt()}) е равно на:", right.fmt(), wrongs,
              ["Умножи коефициентите и степените поотделно.", f"{val(c1)}·({val(c2)}) = {val(c1*c2)}.", "Показателите на еднаквите букви се събират."],
              f"({m1.fmt()})·({m2.fmt()}) = {right.fmt()}.", "B", expect=right.fmt(False))


def b_power(rng, key):
    c = nz(rng, -3, 3, (0, 1))
    a, b, n = rng.randint(1, 3), rng.randint(1, 2), rng.randint(2, 3)
    base = P.mono(c, a=a, b=b)
    right = base ** n
    wrongs = [(P.mono(-(c ** n), a=a * n, b=b * n).fmt(), "SIGN", "Четна степен на отрицателно е положително; нечетна — отрицателно."),
              (P.mono(c * n, a=a * n, b=b * n).fmt(), "REASONING", f"({val(c)})^{n} ≠ {val(c)}·{n}: коефициентът се степенува."),
              (P.mono(c ** n, a=a + n, b=b + n).fmt(), "TECHNICAL", "При степенуване показателите се умножават.")]
    return mc(rng, key, "B.powers", "Степенува едночлен", "ADVANCED" if n == 3 else "INTERMEDIATE", "SIGN",
              f"({base.fmt()}){sup(n)} е равно на:", right.fmt(), wrongs,
              ["Степенувай всеки множител.", f"({val(c)}){sup(n)} = {val(c**n)}.", "Показателите се умножават по степента."],
              f"({base.fmt()}){sup(n)} = ({val(c)}){sup(n)}·a{sup(a*n)}·b{sup(b*n)} = {right.fmt()}.", "B", expect=right.fmt(False))


def b_divide(rng, key, pts=2, as_expr=True):
    c2 = nz(rng, -6, 6, (0, 1, -1))
    k = nz(rng, -5, 5, (0,))
    a2, b2 = rng.randint(1, 3), rng.randint(0, 2)
    qa, qb = rng.randint(1, 3), rng.randint(0, 2)
    divisor = P.mono(c2, a=a2, b=b2)
    quotient = P.mono(k, a=qa, b=qb)
    dividend = divisor * quotient
    return q(key, "B.division", "Дели едночлен на едночлен", "EXPRESSION", "ADVANCED", 75, pts, "SIGN",
             f"Раздели: ({dividend.fmt()}) : ({divisor.fmt()})", "B", answer=quotient.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (-quotient).fmt(False), "misconception": "SIGN", "explanation": "Провери знака на частното."}],
             hints=["Раздели коефициентите, после степените.", "При деление показателите се изваждат.", f"Коефициент: {val(dividend.ordered()[0][1])} : ({val(c2)}) = {val(k)}."],
             solution=f"({dividend.fmt()}) : ({divisor.fmt()}) = {quotient.fmt()}.")


def b_value(rng, key, pts=2):
    c = nz(rng, -4, 4, (0,))
    x, y = nz(rng, -3, 3), nz(rng, -3, 3)
    m = P.mono(c, x=2, y=1)
    r = m.eval(x=x, y=y)
    return q(key, "B.applications", "Намира стойност на едночлен", "NUMERIC", "FOUNDATIONAL", 60, pts, "SIGN",
             f"Намери стойността на {m.fmt()} при x = {val(x)} и y = {val(y)}.", "B", answer=val(r, False),
             distractors=[{"match": val(-r, False), "misconception": "SIGN", "explanation": f"({val(x)})² = {x*x} е положително."}],
             hints=["Замести с числата в скоби.", f"x² = {paren(x)}² = {x*x}.", "Умножи всичко."],
             solution=f"{val(c)}·{paren(x)}²·{paren(y)} = {val(c)}·{x*x}·{paren(y)} = {val(r)}.")


def b_degree_num(rng, key, pts=2):
    c, pw = rand_mono(rng, "abcx")
    deg = sum(pw.values())
    return q(key, "B.normal-form", "Определя степен на едночлен", "NUMERIC", "FOUNDATIONAL", 45, pts, "REASONING",
             f"Каква е степента на едночлена {mono_text(c, **pw)}?", "B", answer=str(deg),
             distractors=[{"match": str(abs(c)) if abs(c) != deg else str(deg + 7), "misconception": "REASONING", "explanation": "Коефициентът не е степента."}],
             hints=["Събери показателите.", "Буква без показател има показател 1.", f"{' + '.join(str(e) for e in pw.values())}."],
             solution=f"Степента е {' + '.join(str(e) for e in pw.values())} = {deg}.")


def b_steps(rng, key):
    c = [nz(rng, 1, 7) for _ in range(5)]
    s = [rng.choice([1, -1]) for _ in range(5)]
    t1, t2 = {"x": 2, "y": 1}, {"x": 1, "y": 2}
    terms = [(s[0] * c[0], t1), (s[1] * c[1], t2), (s[2] * c[2], t1), (s[3] * c[3], t2), (s[4] * c[4], t1)]
    total = P()
    pretty, ascii_ = [], []
    for i, (cc, pw) in enumerate(terms):
        m = P.mono(cc, **pw)
        total = total + m
        pt, at = m.fmt(), m.fmt(False)
        if i == 0:
            pretty.append(pt); ascii_.append(at)
        else:
            pretty.append((" − " + pt[1:]) if pt.startswith("−") else " + " + pt)
            ascii_.append(at if at.startswith("-") else "+" + at)
    if len(total.t) < 2:
        return b_steps(rng, key)
    return q(key, "B.like-terms", "Привежда подобни едночлени", "STEPS", "INTERMEDIATE", 180, 3, "LIKE_TERMS",
             f"Опрости стъпка по стъпка. Всеки ред трябва да е равен на предишния: {''.join(pretty)}", "B",
             answer=total.fmt(False), form="NORMAL_FORM", parts=[{"id": "start", "type": "EXPRESSION", "answer": "".join(ascii_), "points": 0}],
             start="".join(ascii_),
             distractors=[{"match": (P.mono(sum(abs(x[0]) for x in terms if x[1] == t1), **t1) + P.mono(sum(x[0] for x in terms if x[1] == t2), **t2)).fmt(False),
                           "misconception": "SIGN", "explanation": "Пази знака пред всеки член."}],
             hints=["Подчертай членовете с x²y и тези с xy².", "Събери коефициентите на всяка група със знаците им.", f"Отговорът е {total.fmt()}."],
             solution=f"Групираме подобните: {total.fmt()}.", rubric="3 т. за верни преходи и нормален вид; до 1,5 т. частично.")


def b_structured(rng, key):
    ca, cb = nz(rng, 2, 4), nz(rng, -4, -2)
    A = P.mono(ca, x=2, y=1)
    B = P.mono(cb, x=1, y=rng.randint(2, 3))
    AB = A * B
    return q(key, "B.powers", "Умножава и степенува едночлени", "STRUCTURED", "INTERMEDIATE", 180, 3, "TECHNICAL",
             f"Дадени са A = {A.fmt()} и B = {B.fmt()}.", "B",
             part_prompts=[{"id": "a", "label": "а) A · B", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) A²", "type": "EXPRESSION", "points": 1},
                           {"id": "c", "label": "в) Степента на A · B", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": AB.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "EXPRESSION", "answer": (A ** 2).fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(AB.degree()), "points": 1}],
             distractors=[{"match": (-AB).fmt(False), "misconception": "SIGN", "explanation": "Провери знака на произведението."}],
             hints=["При умножение събираш показателите.", "При степенуване умножаваш показателите.", "Степента е сборът от показателите."],
             solution=f"а) {AB.fmt()}; б) {(A**2).fmt()}; в) {AB.degree()}.", rubric="По 1 т. за всяка вярна част.")


def b_area(rng, key):
    k1, k2, k3 = rng.randint(2, 4), rng.randint(2, 5), rng.randint(2, 3)
    V = P.mono(k1, a=1) * P.mono(k2, a=1, b=1) * P.mono(k3, b=1)
    av, bv = rng.randint(1, 2), F(1, 2)
    return q(key, "B.applications", "Прилага действията с едночлени", "STRUCTURED", "ADVANCED", 240, 3, "REASONING",
             f"Правоъгълен паралелепипед има ръбове {k1}a, {k2}ab и {k3}b.", "B",
             part_prompts=[{"id": "a", "label": "а) Обемът като едночлен", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Обемът при a = {av} и b = 0,5", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Колко пъти се увеличава обемът, ако b се утрои?", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": V.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": num_str(V.eval(a=av, b=bv), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": "9", "points": 1}],
             distractors=[{"match": "3", "misconception": "REASONING", "explanation": "Обемът зависи от b², затова при утрояване на b става 9 пъти по-голям."}],
             hints=["Обемът е произведение на трите ръба.", "Замести a и b в едночлена.", "Ако b → 3b, то b² → 9b²."],
             solution=f"а) V = {V.fmt()}; б) {num_str(V.eval(a=av, b=bv))}; в) 9 пъти.", rubric="По 1 т. за всяка вярна част.")

# ------------------------------------------------------------------ path C: polynomials

def lin(a, b, v="x"):
    return P.mono(a, **{v: 1}) + P.const(b)


def c_degree(rng, key):
    p = P.mono(nz(rng, -5, 5), x=3) + P.mono(nz(rng, -5, 5), x=2, y=2) + P.mono(nz(rng, -6, 6), x=1) + P.const(nz(rng, -9, 9))
    deg = p.degree()
    wrongs = [("3", "REASONING", "Степента на x²y² е 2 + 2 = 4."), ("8", "REASONING", "Не събираме степените на всички членове — взимаме най-голямата."),
              ("2", "REASONING", "2 е показателят на y в един член, а не степента на многочлена.")]
    return mc(rng, key, "C.normal-form", "Определя степен на многочлен", "FOUNDATIONAL", "REASONING",
              f"Каква е степента на многочлена {p.fmt()}?", str(deg), wrongs,
              ["Намери степента на всеки член.", "x²y² има степен 4.", "Степента на многочлена е най-голямата от тях."],
              f"Степените на членовете са 3, 4, 1 и 0; най-голямата е {deg}.", "C", expect=str(deg))


def c_normal(rng, key, pts=2):
    a, b, c, d = nz(rng, 2, 6), nz(rng, 1, 5), nz(rng, 1, 7), nz(rng, 1, 6)
    e = rng.randint(1, 9)
    parts = [P.mono(a, x=2), P.mono(-b, x=1), P.mono(c, x=1), P.mono(-d, x=2), P.const(e)]
    total = sum(parts, P())
    text = f"{a}x² − {b}x + {c}x − {d}x² + {e}"
    return q(key, "C.normal-form", "Привежда многочлен в нормален вид", "EXPRESSION", "FOUNDATIONAL", 60, pts, "LIKE_TERMS",
             f"Запиши в нормален вид: {text}", "C", answer=total.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (P.mono(a + d, x=2) + P.mono(c - b, x=1) + P.const(e)).fmt(False), "misconception": "SIGN", "explanation": f"−{d}x² е с минус."}],
             hints=["Групирай x² с x² и x с x.", f"{a}x² − {d}x² = {val(a-d)}x².", f"−{b}x + {c}x = {val(c-b)}x."],
             solution=f"{text} = {total.fmt()}.")


def c_subtract(rng, key):
    p = P.mono(nz(rng, 2, 6), x=2) + P.mono(nz(rng, -6, 6), x=1) + P.const(nz(rng, -9, 9))
    qq = P.mono(nz(rng, 1, 5), x=2) + P.mono(nz(rng, -6, 6), x=1) + P.const(nz(rng, -9, 9))
    right = p - qq
    qo = qq.ordered()
    only_first = p - P({qo[0][0]: qo[0][1]}) + P({m: c for m, c in qo[1:]})
    wrongs = [(only_first.fmt(), "BRACKETS", "Минусът пред скобата сменя знака на всеки член в нея."),
              ((p + qq).fmt(), "SIGN", "Това е сборът, а търсим разликата."),
              ((p - qq + P.const(2)).fmt() if (p - qq + P.const(2)) != only_first else (p - qq + P.const(3)).fmt(), "TECHNICAL", "Провери свободния член.")]
    return mc(rng, key, "C.add-subtract", "Изважда многочлени", "INTERMEDIATE", "BRACKETS",
              f"({p.fmt()}) − ({qq.fmt()}) е равно на:", right.fmt(), wrongs,
              ["Разкрий скобите внимателно.", "Минусът пред скобата сменя всички знаци в нея.", "После приведи подобните."],
              f"Разкриваме втората скоба със сменени знаци: {p.fmt()} + ({(-qq).fmt()}) = {right.fmt()}.", "C", expect=right.fmt(False))


def c_mono_times_poly(rng, key, pts=2):
    m = P.mono(nz(rng, -5, 5, (0, 1)), x=1)
    p = P.mono(nz(rng, 1, 5), x=2) + P.mono(nz(rng, -6, 6), x=1) + P.const(nz(rng, -7, 7))
    r = m * p
    po = p.ordered()
    partial = m * P({po[0][0]: po[0][1]}) + P({k: v for k, v in po[1:]})
    return q(key, "C.multiply-monomial", "Умножава многочлен с едночлен", "EXPRESSION", "INTERMEDIATE", 75, pts, "BRACKETS",
             f"Умножи и запиши в нормален вид: {m.fmt()}({p.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": partial.fmt(False), "misconception": "BRACKETS", "explanation": "Умножи едночлена по всеки член в скобата."}],
             hints=["Умножи едночлена по всеки член.", "Пази знаците.", f"Първият член е {(m*P({po[0][0]: po[0][1]})).fmt()}."],
             solution=f"{m.fmt()}({p.fmt()}) = {r.fmt()}.")


def c_binomial_product(rng, key):
    a, b, c, d = nz(rng, 1, 4), nz(rng, -6, 6), nz(rng, 1, 3), nz(rng, -6, 6)
    p1, p2 = lin(a, b), lin(c, d)
    r = p1 * p2
    no_middle = P.mono(a * c, x=2) + P.const(b * d)
    wrongs = [(no_middle.fmt(), "BRACKETS", "Всеки член от първата скоба се умножава по всеки от втората — липсват средните членове."),
              ((P.mono(a * c, x=2) + P.mono(a * d - b * c if a * d - b * c != a * d + b * c else a * d + b * c + 1, x=1) + P.const(b * d)).fmt(), "SIGN", "Провери знаците на средните членове."),
              ((P.mono(a * c, x=2) + P.mono(a * d + b * c, x=1) - P.const(b * d)).fmt(), "SIGN", "Провери знака на свободния член.")]
    return mc(rng, key, "C.multiply", "Умножава многочлени", "INTERMEDIATE", "BRACKETS",
              f"({p1.fmt()})({p2.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Всеки член по всеки член.", "Получаваш 4 произведения.", "Приведи подобните (средните членове)."],
              f"({p1.fmt()})({p2.fmt()}) = {r.fmt()}.", "C", expect=r.fmt(False))


def c_square(rng, key):
    a, b = nz(rng, 1, 4), nz(rng, -7, 7)
    p = lin(a, b)
    r = p ** 2
    wrongs = [((P.mono(a * a, x=2) + P.const(b * b)).fmt(), "FORMULA_APPLICATION", "Липсва удвоеното произведение 2·a·b."),
              ((P.mono(a * a, x=2) + P.mono(a * b, x=1) + P.const(b * b)).fmt(), "FORMULA_APPLICATION", "Средният член е 2ab — удвоен."),
              ((P.mono(a * a, x=2) - P.mono(2 * a * b, x=1) + P.const(b * b)).fmt(), "SIGN", "Знакът на средния член е като знака в скобата.")]
    return mc(rng, key, "C.square-binomial", "Прилага формулите за квадрат на двучлен", "INTERMEDIATE", "FORMULA_APPLICATION",
              f"({p.fmt()})² е равно на:", r.fmt(), wrongs,
              ["(a ± b)² = a² ± 2ab + b².", f"Тук a = {P.mono(a, x=1).fmt()}, b = {val(abs(b))}.", "Не забравяй удвоеното произведение."],
              f"({p.fmt()})² = {r.fmt()}.", "C", expect=r.fmt(False))


def c_diff_squares(rng, key, pts=2):
    a, b = nz(rng, 1, 5), nz(rng, 1, 9)
    r = lin(a, -b) * lin(a, b)
    return q(key, "C.difference-squares", "Прилага (a − b)(a + b) = a² − b²", "EXPRESSION", "FOUNDATIONAL", 60, pts, "FORMULA_APPLICATION",
             f"Умножи: ({lin(a, -b).fmt()})({lin(a, b).fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (P.mono(a * a, x=2) + P.const(b * b)).fmt(False), "misconception": "SIGN", "explanation": "Получава се разлика на квадрати."}],
             hints=["(a − b)(a + b) = a² − b².", f"a = {P.mono(a, x=1).fmt()}, b = {b}.", f"a² = {P.mono(a*a, x=2).fmt()}."],
             solution=f"= ({P.mono(a, x=1).fmt()})² − {b}² = {r.fmt()}.")


def c_cube(rng, key):
    b = nz(rng, -3, 3)
    r = lin(1, b) ** 3
    wrongs = [((P.mono(1, x=3) + P.const(b ** 3)).fmt(), "FORMULA_APPLICATION", "(x + b)³ има четири члена: x³ + 3x²b + 3xb² + b³."),
              ((P.mono(1, x=3) + P.mono(b, x=2) + P.mono(b * b, x=1) + P.const(b ** 3)).fmt(), "FORMULA_APPLICATION", "Коефициентите са 1, 3, 3, 1."),
              ((P.mono(1, x=3) + P.mono(3 * b, x=2) - P.mono(3 * b * b, x=1) + P.const(b ** 3)).fmt(), "SIGN", "3xb² е винаги положително (b² ≥ 0).")]
    return mc(rng, key, "C.cube-binomial", "Прилага формулите (a ± b)³", "ADVANCED", "FORMULA_APPLICATION",
              f"({lin(1, b).fmt()})³ е равно на:", r.fmt(), wrongs,
              ["(a + b)³ = a³ + 3a²b + 3ab² + b³.", f"Тук a = x, b = {val(b)}.", "Коефициентите са 1, 3, 3, 1."],
              f"({lin(1, b).fmt()})³ = {r.fmt()}.", "C", expect=r.fmt(False))


def c_common_factor(rng, key, pts=2):
    g = nz(rng, 2, 6)
    a, b = nz(rng, 1, 5), nz(rng, -7, 7)
    while math.gcd(a, abs(b)) != 1:
        b = nz(rng, -7, 7)
    inner = P.mono(a, x=1) + P.const(b)
    full = P.mono(g, x=1) * inner
    ans = f"{g}x({inner.fmt(False)})"
    return q(key, "C.common-factor", "Разлага чрез изнасяне на общ множител", "EXPRESSION", "INTERMEDIATE", 75, pts, "FACTORISATION",
             f"Разложи на множители: {full.fmt()}", "C", answer=ans, form="FACTORISED",
             distractors=[{"match": f"{g}x({lin(a, -b).fmt(False)})", "misconception": "SIGN", "explanation": "Провери знака на втория член в скобата — умножи обратно."},
                          {"match": f"{g}({inner.fmt(False)})", "misconception": "FACTORISATION", "explanation": "Изнесен е само числовият множител — забравено е x."}],
             hints=["Намери най-големия общ множител на коефициентите.", "Общата буква с най-малката степен.", f"Изнеси {g}x."],
             solution=f"{full.fmt()} = {g}x({inner.fmt()}). Проверка: умножи обратно.")


def c_factor_formula(rng, key, pts=2):
    a, b = nz(rng, 1, 4), nz(rng, 1, 7)
    while math.gcd(a, b) != 1:
        b = nz(rng, 1, 7)
    if rng.random() < 0.5:
        full = P.mono(a * a, x=2) - P.const(b * b)
        ans = f"({lin(a, -b).fmt(False)})({lin(a, b).fmt(False)})"
        hint = "Разлика на квадрати: a² − b² = (a − b)(a + b)."
        wrong, wrong_why = f"({lin(a, -b).fmt(False)})^2", "(a − b)² ≠ a² − b²: разликата на квадрати е (a − b)(a + b)."
    else:
        s = rng.choice([1, -1])
        full = lin(a, s * b) ** 2
        ans = f"({lin(a, s * b).fmt(False)})^2"
        hint = "Точен квадрат: a² ± 2ab + b² = (a ± b)²."
        wrong, wrong_why = f"({lin(a, -s * b).fmt(False)})^2", "Знакът в скобата е като знака на средния член."
    return q(key, "C.factor-formulas", "Разлага чрез формули", "EXPRESSION", "ADVANCED", 90, pts, "FACTORISATION",
             f"Разложи на множители: {full.fmt()}", "C", answer=ans, form="FACTORISED",
             distractors=[{"match": wrong, "misconception": "FORMULA_APPLICATION", "explanation": wrong_why}],
             hints=["Кои формули за съкратено умножение познаваш?", hint, f"Корен от {P.mono(a*a, x=2).fmt()} е {P.mono(a, x=1).fmt()}."],
             solution=f"{full.fmt()} = {ans.replace('^2', '²')}.")


def c_value_simplify(rng, key, pts=2):
    k = nz(rng, 1, 5)
    v = nz(rng, -6, 6)
    e1, e2 = lin(1, k) ** 2, lin(1, -k) ** 2
    r = (e1 - e2).eval(x=v)
    return q(key, "C.applications", "Пресмята стойност след опростяване", "NUMERIC", "ADVANCED", 120, pts, "FORMULA_APPLICATION",
             f"Опрости и пресметни: (x + {k})² − (x − {k})² при x = {val(v)}.", "C", answer=val(r, False),
             distractors=[{"match": "0", "misconception": "FORMULA_APPLICATION", "explanation": "Удвоените произведения не се унищожават — те се събират."}],
             hints=["Разкрий двата квадрата.", f"Остава 4·{k}·x = {4*k}x.", f"Замести x = {val(v)}."],
             solution=f"(x + {k})² − (x − {k})² = {4*k}x; при x = {val(v)}: {val(r)}.")


def c_identity(rng, key):
    a = nz(rng, 2, 6)
    good = f"(x + {a})² = x² + {2*a}x + {a*a}"
    wrongs = [(f"(x + {a})² = x² + {a*a}", "FORMULA_APPLICATION", "Липсва удвоеното произведение."),
              (f"(x − {a})² = x² − {a*a}", "FORMULA_APPLICATION", "(x − a)² ≠ x² − a²."),
              (f"(x − {a})(x + {a}) = x² + {a*a}", "SIGN", "Разликата на квадрати е x² − a².")]
    return mc(rng, key, "C.identities", "Разпознава тъждества", "INTERMEDIATE", "FORMULA_APPLICATION",
              "Кое равенство е тъждество (вярно за всяко x)?", good, wrongs,
              ["Провери с число, напр. x = 1.", "Тъждество е вярно за всяко x.", "Използвай формулите за съкратено умножение."],
              f"{good} — вярно е по формулата за квадрат на сбор.", "C")


def c_steps(rng, key):
    a, b = nz(rng, 1, 5), nz(rng, 1, 5)
    expr = lin(1, a) ** 2 - lin(1, -b) * lin(1, b)
    start = f"(x+{a})^2-(x-{b})(x+{b})"
    return q(key, "C.square-binomial", "Опростява изрази с формули", "STEPS", "ADVANCED", 180, 3, "FORMULA_APPLICATION",
             f"Опрости стъпка по стъпка: (x + {a})² − (x − {b})(x + {b})", "C", answer=expr.fmt(False), form="NORMAL_FORM",
             parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": (lin(1, a) ** 2 - P.mono(1, x=2) - P.const(b * b)).fmt(False), "misconception": "BRACKETS",
                           "explanation": "Минусът пред скобата сменя знака и на −b²."}],
             hints=["Разкрий (x + a)² по формулата.", f"(x − {b})(x + {b}) = x² − {b*b}.", "Внимавай с минуса пред втората скоба."],
             solution=f"= x² + {2*a}x + {a*a} − x² + {b*b} = {expr.fmt()}.", rubric="3 т. за верни преходи и нормален вид.")


def c_structured(rng, key):
    g = nz(rng, 2, 5)
    r = nz(rng, 1, 6)
    full = P.mono(g, x=2) - P.const(g * r * r)
    v = r + nz(rng, 1, 3)
    return q(key, "C.factor-formulas", "Разлага и пресмята", "STRUCTURED", "ADVANCED", 240, 3, "FACTORISATION",
             f"Даден е изразът A = {full.fmt()}.", "C",
             part_prompts=[{"id": "a", "label": "а) Изнеси общия множител и разложи напълно", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {v}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Положителната стойност на x, при която A = 0", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": f"{g}(x-{r})(x+{r})", "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(full.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(r), "points": 1}],
             distractors=[{"match": f"{g}(x^2-{r*r})", "misconception": "FACTORISATION", "explanation": "x² − b² може да се разложи още."}],
             hints=[f"Изнеси {g}.", "В скобата има разлика на квадрати.", f"Произведението е 0, когато x − {r} = 0."],
             solution=f"а) {g}(x − {r})(x + {r}); б) {val(full.eval(x=v))}; в) x = {r}.", rubric="По 1 т. за всяка вярна част.")

# ------------------------------------------------------------------ test assembly

# Questions whose wording is fixed (only options vary) may repeat across tests, never within one.
CONSTANT_TEXT_OK = {"Кой от изразите е цял (не съдържа деление на израз с променлива)?", "Кое равенство е тъждество (вярно за всяко x)?"}

MC = {"A": [a_integral, a_value_quadratic, a_no_meaning, a_order, "compose"],
      "B": [b_coefficient, b_degree_mc, b_normal_form, b_like, b_multiply, b_power],
      "C": [c_degree, c_subtract, c_binomial_product, c_square, c_cube, c_identity]}
SHORT = {"A": [a_value_two_vars, a_fraction_value],
         "B": [b_add_like, b_divide, b_value, b_degree_num],
         "C": [c_normal, c_mono_times_poly, c_diff_squares, c_common_factor, c_factor_formula, c_value_simplify]}
MULTI = {"A": [a_steps_value, a_structured_compare],
         "B": [b_steps, b_structured, b_area],
         "C": [c_steps, c_structured]}
TOPIC = {"A": "Рационални изрази", "B": "Едночлени", "C": "Многочлени"}


def build_test(rng, test_key, paths, n_mc, n_short, n_multi, seen):
    items = []
    local_seen = set()
    compose_i = rng.randint(0, len(PHRASES) - 1)

    def take(pool_by_path, n):
        nonlocal compose_i
        out = []
        i = 0
        while len(out) < n:
            path = paths[i % len(paths)]
            pool = pool_by_path[path]
            start = (i // len(paths)) if len(paths) == 1 else rng.randrange(len(pool))
            i += 1
            made = None
            for shift in range(len(pool)):
                fn = pool[(start + shift) % len(pool)]
                for _attempt in range(12):
                    key = f"{test_key}-{len(items) + len(out) + 1:02d}"
                    try:
                        if fn == "compose":
                            qd = a_compose(rng, key, compose_i)
                            compose_i += 1
                        else:
                            qd = fn(rng, key)
                    except (ValueError, ZeroDivisionError):
                        continue
                    text = qd["draft"]["prompt"]["text"]
                    if text in local_seen or text in seen:
                        continue
                    made = qd
                    break
                if made:
                    break
            if not made:
                raise RuntimeError(f"could not create a unique item for {test_key}")
            text = made["draft"]["prompt"]["text"]
            local_seen.add(text)
            if text not in CONSTANT_TEXT_OK:
                seen.add(text)
            out.append(made)
        return out

    items += take(MC, n_mc)
    items += take(SHORT, n_short)
    items += take(MULTI, n_multi)
    return items


# Seven-day programme: two tests a day; the existing BT1 is day 1.
SCHEDULE = [
    ("D1-A", 1, "A", "Рационални изрази — тест 1"),
    ("D2-C", 2, "C", "Многочлени — тест 1"),
    ("D2-B", 2, "B", "Едночлени — тест 2"),
    ("D3-A", 3, "A", "Рационални изрази — тест 2"),
    ("D3-C", 3, "C", "Многочлени — тест 2"),
    ("D4-B", 4, "B", "Едночлени — тест 3"),
    ("D4-A", 4, "A", "Рационални изрази — тест 3"),
    ("D5-C", 5, "C", "Многочлени — тест 3"),
    ("D5-B", 5, "B", "Едночлени — тест 4"),
    ("D6-M", 6, "ABC", "Смесен обобщителен тест 1"),
    ("D7-M", 7, "ABC", "Смесен обобщителен тест 2"),
]


def generate(existing_prompts):
    rng = random.Random(20261002)
    seen = set(existing_prompts)
    questions, tests = [], []
    for key, day, paths, title in SCHEDULE:
        integrated = len(paths) > 1
        n = (14, 7, 3) if integrated else (12, 5, 3)
        items = build_test(rng, key, list(paths), *n, seen)
        questions += items
        tests.append({"testKey": key, "title": f"Ден {day} · {title}", "kind": "INTEGRATED" if integrated else "THEMATIC",
                      "path": "D" if integrated else paths, "blueprint": "Обобщителен тест (24)" if integrated else "Тематичен тест (20)",
                      "timeLimitMin": 60 if integrated else 40, "hintPolicy": "ALLOWED" if not integrated else "NONE",
                      "reviewMoment": "AFTER_SUBMIT", "questionKeys": [x["key"] for x in items]})
    return questions, tests
