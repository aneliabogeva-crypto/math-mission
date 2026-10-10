# Topic 5 "Разлагане на множители": practice tests for the lessons C11 (common factor), C12 (formulas and
# grouping) and C13 (applications), plus class-test preparation. Answers are computed exactly and
# re-checked by validate_content.ts with the app's own engine (equivalence + "fully factorised").
import math
import random

import gen_tests as g
import lessons as L
import topic_formulas as TF

P, nz, lin, val, paren = g.P, g.nz, g.lin, g.val, g.paren
mc, q = g.mc, g.q
X = P.mono(1, x=1)

CF, CF_OUT = "C.common-factor", "Разлага чрез изнасяне на общ множител"
FF, FF_OUT = "C.factor-formulas", "Разлага чрез формули и групиране"
AP, AP_OUT = "C.applications", "Използва разлагане за пресмятане"


def num(n):
    return f"{n:,}".replace(",", " ")


def steps_factor(key, skill, outcome, prompt, start, answer, wrong, wrong_expl, hints, solution):
    return q(key, skill, outcome, "STEPS", "ADVANCED", 180, 3, "FACTORISATION", prompt, "C",
             answer=answer, form="FACTORISED", parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": wrong, "misconception": "FACTORISATION", "explanation": wrong_expl}],
             hints=hints, solution=solution, rubric="3 т. за верни преходи и пълно разлагане; частични точки за верните редове.")


# ------------------------------------------------------------------ C11: common factor

def f_gcf_mc(rng, key):
    a, b = rng.choice([(6, 9), (4, 10), (8, 12), (6, 15), (10, 14), (12, 18), (9, 12)])
    p, r = rng.randint(2, 3), rng.randint(2, 3)
    m1, m2 = P.mono(a, x=p, y=1), P.mono(b, x=1, y=r)
    gcd = math.gcd(a, b)
    good = P.mono(gcd, x=1, y=1)
    wrongs = [(P.mono(gcd, x=p, y=r).fmt(), "FACTORISATION", "От всяка буква взимаме най-малката степен, която се среща и в двата едночлена."),
              (P.mono(a * b // gcd, x=1, y=1).fmt(), "FACTORISATION", f"{a * b // gcd} е най-малкото общо кратно. Общият множител е делител и на {a}, и на {b}."),
              (P.mono(1, x=1, y=1).fmt(), "FACTORISATION", f"Числото {gcd} също е общ множител — не го забравяй.")]
    return mc(rng, key, CF, "Намира общ множител", "FOUNDATIONAL", "FACTORISATION",
              f"Кой е най-големият общ множител на {m1.fmt()} и {m2.fmt()}?", good.fmt(), wrongs,
              [f"Най-големият общ делител на {a} и {b}.", "Всяка обща буква с най-малката ѝ степен.", f"НОД({a}, {b}) = {gcd}."],
              f"НОД({a}, {b}) = {gcd}; x се среща най-малко на първа степен, y — също. Общият множител е {good.fmt()}.", "C", expect=good.fmt(False))


def f_cf_mc(rng, key):
    k, a, b = nz(rng, 2, 5), nz(rng, 2, 5), nz(rng, 1, 7)
    if math.gcd(a, b) != 1:
        raise ValueError
    s = rng.choice([1, -1])
    inner = P.mono(a, x=1) + P.const(s * b)
    full = P.mono(k, x=2) * inner
    good = f"{k}x²({inner.fmt()})"
    wrongs = [(f"{k}x²({(P.mono(a, x=1) - P.const(s * b)).fmt()})", "SIGN", "Провери знака на втория член — умножи обратно."),
              (f"{k}x²({P.mono(a, x=1).fmt()} {'+' if s > 0 else '−'} {k * b})", "FACTORISATION", f"Делим всеки член на {k}x²: {val(s * k * b)}x² : {k}x² = {val(s * b)}."),
              (f"{k}x({inner.fmt()})", "FACTORISATION", f"{k}x({inner.fmt()}) = {(P.mono(k, x=1) * inner).fmt()} — степените не съвпадат с началния израз.")]
    return mc(rng, key, CF, CF_OUT, "INTERMEDIATE", "FACTORISATION", f"Разложи на множители: {full.fmt()}", good, wrongs,
              ["Намери общия множител на коефициентите.", "Общата буква е x — с най-малката степен.", f"Изнеси {k}x²."],
              f"{full.fmt()} = {k}x²({inner.fmt()}). Проверка: {k}x²·{P.mono(a, x=1).fmt()} = {P.mono(k * a, x=3).fmt()}.", "C", expect=full.fmt(False))


def f_cf_binomial_mc(rng, key):
    m, s = nz(rng, 2, 7), rng.choice([1, -1])
    lp = f"(a {'+' if s > 0 else '−'} b)"
    good = f"{lp}(x + {m})"
    wrongs = [(f"{lp}(x − {m})", "SIGN", f"Вторият член е +{m}{lp}, затова в скобата е +{m}."),
              (f"(a {'−' if s > 0 else '+'} b)(x + {m})", "SIGN", f"Общият множител е точно {lp}."),
              (f"{m}x{lp}", "FACTORISATION", f"{m}x{lp} е произведение, а изразът е сбор от две произведения.")]
    expect = f"(a{'+' if s > 0 else '-'}b)*(x+{m})"
    return mc(rng, key, CF, "Изнася многочлен като общ множител", "INTERMEDIATE", "FACTORISATION",
              f"Разложи на множители: x{lp} + {m}{lp}", good, wrongs,
              [f"И двата члена съдържат {lp}.", f"Изнеси {lp} пред скоби.", f"Остават x и {m}."],
              f"x{lp} + {m}{lp} = {lp}(x + {m}).", "C", expect=expect)


def f_cf_three(rng, key, pts=2):
    k = nz(rng, 2, 5)
    while True:
        a, b, c = nz(rng, 1, 5), nz(rng, -7, 7), nz(rng, -7, 7)
        disc = b * b - 4 * a * c
        if math.gcd(math.gcd(a, abs(b)), abs(c)) == 1 and (disc < 0 or math.isqrt(disc) ** 2 != disc):
            break
    inner = P.mono(a, x=2) + P.mono(b, x=1) + P.const(c)
    full = P.mono(k, x=1) * inner
    return q(key, CF, CF_OUT, "EXPRESSION", "INTERMEDIATE", 90, pts, "FACTORISATION",
             f"Изнеси общия множител: {full.fmt()}", "C", answer=f"{k}x({inner.fmt(False)})", form="FACTORISED",
             distractors=[{"match": f"{k}x({(P.mono(a, x=2) + P.mono(b, x=1)).fmt(False)})", "misconception": "FACTORISATION",
                           "explanation": f"Последният член {P.mono(k * c, x=1).fmt()} : {k}x = {val(c)} — той не изчезва."}],
             hints=["Общият множител на коефициентите.", "Най-малката степен на x.", f"Изнеси {k}x и раздели всеки член."],
             solution=f"{full.fmt()} = {k}x({inner.fmt()}). Проверка: умножи обратно.")


def f_cf_number(rng, key, pts=1):
    a = rng.randint(13, 49)
    b = rng.randint(11, 89)
    c = 100 - b
    return q(key, AP, "Пресмята рационално с общ множител", "NUMERIC", "INTERMEDIATE", 60, pts, "FACTORISATION",
             f"Пресметни рационално: {a}·{b} + {a}·{c}", "C", answer=str(a * 100),
             distractors=[{"match": str(a * b), "misconception": "FACTORISATION", "explanation": f"И двата члена съдържат {a}: {a}·({b} + {c})."}],
             hints=[f"Общият множител е {a}.", f"{a}·({b} + {c}).", f"{b} + {c} = 100."],
             solution=f"{a}·{b} + {a}·{c} = {a}·({b} + {c}) = {a}·100 = {a * 100}.")


def f_cf_steps(rng, key):
    k, a = nz(rng, 2, 5), nz(rng, 2, 6)
    full = P.mono(k, x=3) - P.mono(k * a * a, x=1)
    ans = f"{k}x(x-{a})(x+{a})"
    return steps_factor(key, CF, "Разлага напълно", f"Разложи напълно стъпка по стъпка: {full.fmt()}", full.fmt(False), ans,
                        f"{k}x(x-{a})^2", f"(x − {a})² ≠ x² − {a * a}.",
                        [f"Първо изнеси {k}x.", f"В скобата остава x² − {a * a}.", "Това е разлика на квадрати."],
                        f"{full.fmt()} = {k}x(x² − {a * a}) = {k}x(x − {a})(x + {a}).")


def f_cf_structured(rng, key):
    k, m = nz(rng, 2, 5), nz(rng, 2, 7)
    v = nz(rng, -4, 4, (0, m))
    A = P.mono(k, x=2) - P.mono(k * m, x=1)
    return q(key, CF, CF_OUT, "STRUCTURED", "ADVANCED", 240, 3, "FACTORISATION", f"Даден е изразът A = {A.fmt()}. Ще пресмятаме и при x = {val(v)}.", "C",
             part_prompts=[{"id": "a", "label": "а) Разложи A на множители", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Положителното x, при което A = 0", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": f"{k}x(x-{m})", "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(m), "points": 1}],
             distractors=[{"match": f"{k}x(x+{m})", "misconception": "SIGN", "explanation": "Провери знака — умножи обратно."}],
             hints=[f"Изнеси {k}x.", f"A = {k}x(x − {m}).", f"Произведение е 0, когато някой множител е 0: x = 0 или x − {m} = 0."],
             solution=f"а) A = {k}x(x − {m}); б) {k}·{paren(v)}·{paren(v - m)} = {val(A.eval(x=v))}; в) x = {m}.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ C12: formulas and grouping

def f_square_mc(rng, key):
    a, s = nz(rng, 2, 9), rng.choice([1, -1])
    full = lin(1, s * a) ** 2
    good = f"({lin(1, s * a).fmt()})²"
    wrongs = [(f"({lin(1, -s * a).fmt()})²", "SIGN", "Знакът в скобата е като знака на средния член."),
              (f"(x − {a})(x + {a})", "FORMULA_APPLICATION", f"(x − {a})(x + {a}) = x² − {a * a} — няма среден член."),
              (f"({lin(1, s * a * a).fmt()})²", "FORMULA_APPLICATION", f"Вторият член в скобата е корен от {a * a}, т.е. {a}.")]
    return mc(rng, key, FF, "Разлага точен квадрат", "INTERMEDIATE", "FACTORISATION", f"Разложи на множители: {full.fmt()}", good, wrongs,
              ["Провери дали е точен квадрат: a² ± 2ab + b².", f"{a * a} = {a}², а {2 * a}x = 2·x·{a}.", "Знакът в скобата е като знака на средния член."],
              f"{full.fmt()} = x² {'+' if s > 0 else '−'} 2·x·{a} + {a}² = {good}.", "C", expect=full.fmt(False))


def f_group_mc(rng, key):
    a, b = nz(rng, 1, 7), nz(rng, 1, 7)
    if a == b:
        raise ValueError
    full = f"xy + {b}x + {a}y + {a * b}"
    good = f"(x + {a})(y + {b})"
    wrongs = [(f"(x + {b})(y + {a})", "FACTORISATION", f"(x + {b})(y + {a}) = xy + {a}x + {b}y + {a * b} — коефициентите са разменени."),
              (f"(x + {a})(y − {b})", "SIGN", "Всички членове са с плюс — и в скобите е плюс."),
              (f"(x + y)({a} + {b})", "FACTORISATION", "Групирай първите два члена (общ множител x) и последните два (общ множител " + str(a) + ").")]
    expect = f"(x+{a})*(y+{b})"
    return mc(rng, key, FF, "Разлага чрез групиране", "ADVANCED", "FACTORISATION", f"Разложи чрез групиране: {full}", good, wrongs,
              ["Групирай първите два и последните два члена.", f"x(y + {b}) + {a}(y + {b}).", f"Изнеси (y + {b})."],
              f"{full} = x(y + {b}) + {a}(y + {b}) = (y + {b})(x + {a}).", "C", expect=expect)


def f_group_expr(rng, key, pts=2):
    a, b = nz(rng, 2, 7), nz(rng, 2, 7)
    if a == b:
        raise ValueError
    full = f"ab + {b}a + {a}b + {a * b}"
    return q(key, FF, "Разлага чрез групиране", "EXPRESSION", "ADVANCED", 120, pts, "FACTORISATION",
             f"Разложи чрез групиране: {full}", "C", answer=f"(a+{a})(b+{b})", form="FACTORISED",
             distractors=[{"match": f"(a+{b})(b+{a})", "misconception": "FACTORISATION", "explanation": "Коефициентите са разменени — умножи обратно и сравни."}],
             hints=["Групирай: (ab + " + str(b) + "a) + (" + str(a) + "b + " + str(a * b) + ").", f"a(b + {b}) + {a}(b + {b}).", f"Изнеси (b + {b})."],
             solution=f"{full} = a(b + {b}) + {a}(b + {b}) = (a + {a})(b + {b}).")


def f_full_expr(rng, key, pts=2):
    k, a = nz(rng, 2, 5), nz(rng, 1, 6)
    full = P.mono(k, x=2) - P.const(k * a * a)
    return q(key, FF, "Разлага напълно", "EXPRESSION", "ADVANCED", 90, pts, "FACTORISATION",
             f"Разложи напълно: {full.fmt()}", "C", answer=f"{k}(x-{a})(x+{a})", form="FACTORISED",
             distractors=[{"match": f"{k}(x-{a})^2", "misconception": "FORMULA_APPLICATION", "explanation": f"(x − {a})² = x² − {2 * a}x + {a * a} — има среден член."}],
             hints=[f"Първо изнеси {k}.", f"Остава x² − {a * a}.", "Разлика на квадрати."],
             solution=f"{full.fmt()} = {k}(x² − {a * a}) = {k}(x − {a})(x + {a}).")


def f_formula_steps(rng, key):
    k, a = nz(rng, 2, 4), nz(rng, 1, 5)
    full = k * lin(1, a) ** 2
    ans = f"{k}(x+{a})^2"
    return steps_factor(key, FF, "Разлага напълно", f"Разложи напълно стъпка по стъпка: {full.fmt()}", full.fmt(False), ans,
                        f"{k}(x-{a})^2", "Знакът в скобата е като знака на средния член.",
                        [f"Изнеси {k}.", f"В скобата остава x² + {2 * a}x + {a * a}.", "Това е точен квадрат."],
                        f"{full.fmt()} = {k}(x² + {2 * a}x + {a * a}) = {k}(x + {a})².")


def f_area_structured(rng, key):
    a, b = rng.sample(range(1, 8), 2)
    v = rng.randint(1, 5)
    area = lin(1, a) * lin(1, b)
    per = 2 * (lin(1, a) + lin(1, b))
    lo, hi = sorted((a, b))
    return q(key, FF, "Прилага разлагане в геометрична задача", "STRUCTURED", "ADVANCED", 240, 3, "FACTORISATION",
             f"Лицето на правоъгълник е {area.fmt()} см², а страните му са двучлени от вида (x + число). Ще пресмятаме и при x = {v}.", "C",
             part_prompts=[{"id": "a", "label": "а) Разложи лицето на множители", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Периметърът в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "c", "label": f"в) Лицето при x = {v} (в см²)", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": f"(x+{lo})(x+{hi})", "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "EXPRESSION", "answer": per.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str((v + a) * (v + b)), "points": 1}],
             distractors=[{"match": f"(x+{a * b})(x+1)", "misconception": "FACTORISATION", "explanation": f"Търсим две числа със сбор {a + b} и произведение {a * b}."}],
             hints=[f"Търси две числа: сбор {a + b}, произведение {a * b}.", f"Числата са {lo} и {hi}.", "P = 2·(първа страна + втора страна)."],
             solution=f"а) {area.fmt()} = (x + {lo})(x + {hi}); б) P = 2(x + {lo} + x + {hi}) = {per.fmt()}; в) {v + lo}·{v + hi} = {(v + a) * (v + b)}.",
             rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ C13: applications

def f_ds_number(rng, key, pts=1):
    c = rng.choice([50, 60, 70, 80, 90, 100])
    d = rng.randint(2, 9)
    a, b = c + d, c - d
    return q(key, AP, AP_OUT, "NUMERIC", "INTERMEDIATE", 60, pts, "FORMULA_APPLICATION",
             f"Пресметни рационално: {a}² − {b}²", "C", answer=str(a * a - b * b),
             distractors=[{"match": str((a - b) ** 2), "misconception": "FORMULA_APPLICATION", "explanation": "a² − b² = (a − b)(a + b), а не (a − b)²."}],
             hints=["Разлика на квадрати.", f"({a} − {b})({a} + {b}).", f"{a - b}·{a + b}."],
             solution=f"{a}² − {b}² = ({a} − {b})({a} + {b}) = {a - b}·{a + b} = {num(a * a - b * b)}.")


def f_square_value_mc(rng, key):
    a = nz(rng, 1, 5)
    x0 = rng.choice([100, 200, 50]) - a
    good = (x0 + a) ** 2
    wrongs = [(str((x0 - a) ** 2), "SIGN", f"Изразът е (x + {a})², не (x − {a})²."),
              (str(x0 * x0 + a * a), "FORMULA_APPLICATION", "Липсва удвоеното произведение — по-лесно е първо да разложиш."),
              (str(x0 + a), "FORMULA_APPLICATION", f"Получи основата {x0 + a}; остава да я повдигнеш на квадрат.")]
    return mc(rng, key, AP, AP_OUT, "INTERMEDIATE", "FORMULA_APPLICATION",
              f"Колко е стойността на x² + {2 * a}x + {a * a} при x = {x0}?", str(good), wrongs,
              ["Разложи израза — това е точен квадрат.", f"x² + {2 * a}x + {a * a} = (x + {a})².", f"({x0} + {a})² = {x0 + a}²."],
              f"x² + {2 * a}x + {a * a} = (x + {a})² = ({x0} + {a})² = {x0 + a}² = {num(good)}.", "C", expect=str(good))


def f_value_after_factor(rng, key, pts=2):
    k = nz(rng, 2, 6)
    v = rng.choice([25, 50, 75, 125])
    val_ = 4 * k * v
    return q(key, AP, AP_OUT, "NUMERIC", "INTERMEDIATE", 90, pts, "FORMULA_APPLICATION",
             f"Опрости и пресметни: (x + {k})² − (x − {k})² при x = {v}", "C", answer=str(val_),
             distractors=[{"match": "0", "misconception": "FORMULA_APPLICATION", "explanation": "Удвоените произведения са с различни знаци — при изваждане се събират."}],
             hints=["Първо опрости — после замести.", f"(x + {k})² − (x − {k})² = {4 * k}x.", f"{4 * k}·{v}."],
             solution=f"(x + {k})² − (x − {k})² = {4 * k}x; при x = {v}: {4 * k}·{v} = {num(val_)}.")


def f_number_steps(rng, key):
    n = rng.choice([99, 101, 98, 102, 49, 51])
    d = rng.choice([1, 2, 3]) if n not in (49, 51) else 1
    res = n * n - d * d
    start = f"{n}^2-{d * d}"
    return q(key, AP, AP_OUT, "STEPS", "ADVANCED", 150, 3, "FORMULA_APPLICATION",
             f"Пресметни рационално стъпка по стъпка: {n}² − {d * d}", "C", answer=str(res),
             parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": str((n - d) ** 2), "misconception": "FORMULA_APPLICATION", "explanation": "a² − b² = (a − b)(a + b), а не (a − b)²."}],
             hints=[f"{d * d} = {d}².", f"{n}² − {d}² = ({n} − {d})({n} + {d}).", f"{n - d}·{n + d}."],
             solution=f"{n}² − {d * d} = ({n} − {d})({n} + {d}) = {n - d}·{n + d} = {num(res)}.",
             rubric="3 т. за верни преходи и верен резултат.")


# ------------------------------------------------------------------ tests

POOLS = {
    "CF": ([f_gcf_mc, f_cf_mc, f_cf_binomial_mc], [g.c_common_factor, f_cf_three, f_cf_number], [f_cf_steps, f_cf_structured]),
    "FF": ([f_square_mc, TF.ds_factor_ok, f_group_mc, L.c9_factor_mc], [g.c_factor_formula, f_group_expr, f_full_expr], [f_formula_steps, f_area_structured]),
    "AP": ([f_square_value_mc, f_cf_mc, f_square_mc, TF.ds_factor_ok], [f_ds_number, f_value_after_factor, f_cf_number], [f_number_steps, f_area_structured]),
}
TOPIC = "Разлагане на множители"
EXAM = "Подготовка за контролно"
TESTS = [
    ("FAC-1A", ["CF"], f"{TOPIC} · Общ множител — тренировка 1", "ALLOWED"),
    ("FAC-1B", ["CF"], f"{TOPIC} · Общ множител — тренировка 2", "ALLOWED"),
    ("FAC-2A", ["FF"], f"{TOPIC} · Формули и групиране — тренировка 1", "ALLOWED"),
    ("FAC-2B", ["FF"], f"{TOPIC} · Формули и групиране — тренировка 2", "ALLOWED"),
    ("FAC-3A", ["AP"], f"{TOPIC} · Пресмятане с разлагане — тренировка 1", "ALLOWED"),
    ("FAC-3B", ["AP"], f"{TOPIC} · Пресмятане с разлагане — тренировка 2", "ALLOWED"),
    ("FAC-K1", ["CF", "FF", "AP"], f"{EXAM} · Разлагане на множители — вариант 1", "NONE"),
    ("FAC-K2", ["CF", "FF", "AP"], f"{EXAM} · Разлагане на множители — вариант 2", "NONE"),
    ("FAC-K3", ["CF", "FF", "AP"], f"{EXAM} · Разлагане на множители — вариант 3", "NONE"),
    ("FAC-K4", ["CF", "FF", "AP"], f"{EXAM} · Разлагане на множители — вариант 4", "NONE"),
]


def build(existing_prompts):
    rng = random.Random(20261010)
    seen = set(existing_prompts)
    questions, tests = [], []
    for key, topics, title, hints in TESTS:
        items, texts = [], set()
        for slot, n in enumerate((12, 5, 3)):
            pools = [(t, POOLS[t][slot]) for t in topics]
            for i in range(n):
                t, pool = pools[i % len(pools)]
                made = None
                for attempt in range(120):
                    fn = pool[(i // len(pools) + attempt) % len(pool)]
                    k = f"{key}-{len(items) + 1:02d}"
                    g.SIDE.pop(k, None)
                    try:
                        qd = fn(rng, k)
                    except (ValueError, ZeroDivisionError, KeyError):
                        continue
                    text = qd["draft"]["prompt"]["text"]
                    if text.lower() in texts or TF.sig(qd) in seen or (not qd["draft"]["prompt"].get("options") and text in seen):
                        continue
                    made = qd
                    break
                if not made:
                    raise RuntimeError(f"cannot build {key} item {len(items) + 1}")
                texts.add(made["draft"]["prompt"]["text"].lower())
                seen.add(TF.sig(made))
                if not made["draft"]["prompt"].get("options"):
                    seen.add(made["draft"]["prompt"]["text"])
                items.append(made)
        questions += items
        tests.append({"testKey": key, "title": title, "kind": "THEMATIC", "path": "C", "blueprint": "Тематичен тест (20)",
                      "timeLimitMin": 40, "hintPolicy": hints, "reviewMoment": "AFTER_SUBMIT", "questionKeys": [x["key"] for x in items]})
    return questions, tests
