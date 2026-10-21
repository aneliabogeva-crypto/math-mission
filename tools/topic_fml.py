# Practice by formula: two tests for each of the seven formulas for abbreviated multiplication
#   (a + b)², (a − b)², (a − b)(a + b), (a + b)³, (a − b)³, a³ + b³, a³ − b³
# plus two mixed tests with all formulas. Answers are computed exactly; validate_content.ts re-checks them.
import math
import random
from functools import partial

import gen_tests as g
import topic_formulas as TF

P, nz, lin, val, paren = g.P, g.nz, g.lin, g.val, g.paren
mc, q = g.mc, g.q
X, Y = P.mono(1, x=1), P.mono(1, y=1)
SQ, DS, CB, SC = "C.square-binomial", "C.difference-squares", "C.cube-binomial", "C.sum-cubes"


def sg(s):
    return "+" if s > 0 else "−"


def steps(key, skill, prompt, start, result, wrong, wrong_expl, hints, solution, form="NORMAL_FORM"):
    return q(key, skill, "Опростява изрази с формули", "STEPS", "ADVANCED", 180, 3, "FORMULA_APPLICATION", prompt, "C",
             answer=result.fmt(False) if isinstance(result, P) else result, form=form,
             parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": wrong.fmt(False) if isinstance(wrong, P) else wrong, "misconception": "FORMULA_APPLICATION", "explanation": wrong_expl}],
             hints=hints, solution=solution, rubric="3 т. за верни преходи и верен краен резултат.")


# ------------------------------------------------------------------ (a ± b)²

def sq_expand_mc(s, rng, key):
    a, b = nz(rng, 1, 5), nz(rng, 1, 9)
    if a == b:
        raise ValueError
    p = lin(a, s * b)
    r = p ** 2
    A2, B2 = P.mono(a * a, x=2), P.const(b * b)
    wrongs = [((A2 + B2).fmt(), "FORMULA_APPLICATION", "Липсва удвоеното произведение 2ab."),
              ((A2 + P.mono(s * a * b, x=1) + B2).fmt(), "FORMULA_APPLICATION", f"Средният член е 2·{P.mono(a, x=1).fmt()}·{b} = {2 * a * b}x — удвоен."),
              ((A2 - P.mono(s * 2 * a * b, x=1) + B2).fmt() if s < 0 else (A2 + P.mono(2 * a * b, x=1) - B2).fmt(),
               "SIGN", "Знакът на средния член е като знака в скобата." if s < 0 else "Квадратът b² винаги е положителен.")]
    return mc(rng, key, SQ, f"Прилага формулата (a {sg(s)} b)²", "FOUNDATIONAL", "FORMULA_APPLICATION", f"({p.fmt()})² е равно на:", r.fmt(), wrongs,
              [f"(a {sg(s)} b)² = a² {sg(s)} 2ab + b².", f"a = {P.mono(a, x=1).fmt()}, b = {b}.", f"2ab = 2·{P.mono(a, x=1).fmt()}·{b} = {2 * a * b}x."],
              f"({p.fmt()})² = ({P.mono(a, x=1).fmt()})² {sg(s)} 2·{P.mono(a, x=1).fmt()}·{b} + {b}² = {r.fmt()}.", "C", expect=r.fmt(False))


def sq_xy_mc(s, rng, key):
    k, m = nz(rng, 1, 5), nz(rng, 1, 5)
    if k == m:
        raise ValueError
    b = k * X + s * m * Y
    r = b ** 2
    wrongs = [((k * k) * X * X + (m * m) * Y * Y).fmt(), ((k * k) * X * X + (s * k * m) * X * Y + (m * m) * Y * Y).fmt(),
              ((k * k) * X * X - (2 * s * k * m) * X * Y + (m * m) * Y * Y).fmt()]
    expl = ["Липсва удвоеното произведение 2ab.", f"Средният член е 2·{P.mono(k, x=1).fmt()}·{P.mono(m, y=1).fmt()} = {2 * k * m}xy — удвоен.",
            "Знакът на средния член е като знака в скобата."]
    return mc(rng, key, SQ, f"Прилага формулата (a {sg(s)} b)²", "INTERMEDIATE", "FORMULA_APPLICATION", f"({b.fmt()})² е равно на:", r.fmt(),
              [(w, "FORMULA_APPLICATION" if i < 2 else "SIGN", e) for i, (w, e) in enumerate(zip(wrongs, expl))],
              [f"(a {sg(s)} b)² = a² {sg(s)} 2ab + b².", f"a = {P.mono(k, x=1).fmt()}, b = {P.mono(m, y=1).fmt()}.", f"2ab = {2 * k * m}xy."],
              f"({b.fmt()})² = {r.fmt()}.", "C", expect=r.fmt(False))


def sq_missing_mc(s, rng, key):
    a, k = nz(rng, 2, 9), rng.choice([1, 1, 2, 3])
    mid = P.mono(2 * a * k * s, x=1)
    lead = P.mono(k * k, x=2)
    wrongs = [(P.mono(a * k * s, x=1).fmt(), "FORMULA_APPLICATION", f"Средният член е удвоеното произведение: 2·{P.mono(k, x=1).fmt()}·{a}."),
              (P.mono(a * a * s, x=1).fmt(), "FORMULA_APPLICATION", f"{a * a} е квадратът на {a}; средният член е 2·{P.mono(k, x=1).fmt()}·{a}."),
              (P.mono(-2 * a * k * s, x=1).fmt(), "SIGN", "Знакът на средния член е като знака в скобата.")]
    return mc(rng, key, SQ, "Допълва формула за квадрат на двучлен", "FOUNDATIONAL", "FORMULA_APPLICATION",
              f"Кой член липсва: ({lin(k, s * a).fmt()})² = {lead.fmt()} ··· + {a * a}? (··· е липсващият член със знака си)", mid.fmt(), wrongs,
              [f"(a {sg(s)} b)² = a² {sg(s)} 2ab + b².", f"Средният член е 2·{P.mono(k, x=1).fmt()}·{a}.", "Знакът му е като знака в скобата."],
              f"({lin(k, s * a).fmt()})² = {lead.fmt()} {sg(s)} {2 * a * k}x + {a * a}, затова липсва {mid.fmt()}.", "C", expect=mid.fmt(False))


def sq_reverse_mc(s, rng, key):
    a, k = nz(rng, 1, 9), rng.choice([1, 1, 2, 3])
    if math.gcd(a, k) != 1:
        raise ValueError
    full = lin(k, s * a) ** 2
    good = f"({lin(k, s * a).fmt()})²"
    wrongs = [(f"({lin(k, -s * a).fmt()})²", "SIGN", "Знакът в скобата е като знака на средния член."),
              (f"({lin(k, -a).fmt()})({lin(k, a).fmt()})", "FORMULA_APPLICATION", "Произведението на разлика и сбор няма среден член."),
              (f"({lin(k, s * a * a).fmt()})²", "FORMULA_APPLICATION", f"Вторият член в скобата е корен от {a * a}, т.е. {a}.")]
    return mc(rng, key, SQ, "Разпознава точен квадрат", "INTERMEDIATE", "FACTORISATION", f"Запиши като квадрат на двучлен: {full.fmt()}", good, wrongs,
              ["Провери: a² ± 2ab + b².", f"{P.mono(k * k, x=2).fmt()} = ({P.mono(k, x=1).fmt()})², {a * a} = {a}².", "Знакът в скобата е като знака на средния член."],
              f"{full.fmt()} = ({P.mono(k, x=1).fmt()})² {sg(s)} 2·{P.mono(k, x=1).fmt()}·{a} + {a}² = {good}.", "C", expect=full.fmt(False))


def sq_expr(s, rng, key, pts=2):
    a, b = nz(rng, 1, 6), nz(rng, 1, 9)
    p = lin(a, s * b)
    r = p ** 2
    return q(key, SQ, f"Прилага формулата (a {sg(s)} b)²", "EXPRESSION", "INTERMEDIATE", 75, pts, "FORMULA_APPLICATION",
             f"Повдигни на квадрат и запиши в нормален вид: ({p.fmt()})²", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (P.mono(a * a, x=2) + P.const(b * b)).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Липсва удвоеното произведение 2ab."},
                          {"match": (P.mono(a * a, x=2) - P.mono(s * 2 * a * b, x=1) + P.const(b * b)).fmt(False), "misconception": "SIGN", "explanation": "Знакът на средния член е като знака в скобата."}],
             hints=[f"(a {sg(s)} b)² = a² {sg(s)} 2ab + b².", f"a = {P.mono(a, x=1).fmt()}, b = {b}.", f"a² = {P.mono(a * a, x=2).fmt()}, 2ab = {2 * a * b}x, b² = {b * b}."],
             solution=f"({p.fmt()})² = {r.fmt()}.")


def sq_number(s, rng, key, pts=1):
    base, d = rng.choice([20, 30, 40, 50, 60, 70, 80, 90, 100]), rng.randint(1, 3)
    n = base + s * d
    return q(key, SQ, "Пресмята рационално с формула", "NUMERIC", "INTERMEDIATE", 60, pts, "FORMULA_APPLICATION",
             f"Пресметни рационално с формулата (a {sg(s)} b)²: {n}²", "C", answer=str(n * n),
             distractors=[{"match": str(base * base + d * d), "misconception": "FORMULA_APPLICATION", "explanation": f"Липсва удвоеното произведение 2·{base}·{d} = {2 * base * d}."}],
             hints=[f"{n} = {base} {sg(s)} {d}.", f"({base} {sg(s)} {d})² = {base}² {sg(s)} 2·{base}·{d} + {d}².", f"{base * base} {sg(s)} {2 * base * d} + {d * d}."],
             solution=f"{n}² = ({base} {sg(s)} {d})² = {base * base} {sg(s)} {2 * base * d} + {d * d} = {n * n}.")


def sq_sum_of_squares(s, rng, key, pts=2):
    a, b = rng.randint(1, 9), rng.randint(1, 9)
    if a == b:
        raise ValueError
    a, b = max(a, b), min(a, b)
    t, p = (a + b if s > 0 else a - b), a * b
    ans = a * a + b * b
    given = f"a + b = {t}" if s > 0 else f"a − b = {t}"
    return q(key, SQ, "Използва формулата в обратна посока", "NUMERIC", "ADVANCED", 90, pts, "FORMULA_APPLICATION",
             f"Ако {given} и ab = {p}, намери a² + b².", "C", answer=str(ans),
             distractors=[{"match": str(t * t), "misconception": "FORMULA_APPLICATION", "explanation": f"({given.split(' = ')[0]})² = a² + b² {sg(s)} 2ab — трябва да {'извадиш' if s > 0 else 'прибавиш'} 2ab."},
                          {"match": str(t * t - s * p), "misconception": "FORMULA_APPLICATION", "explanation": "Средният член е 2ab, не ab."}],
             hints=[f"({given.split(' = ')[0]})² = a² {sg(s)} 2ab + b².", f"{t}² = a² + b² {sg(s)} 2·{p}.", f"a² + b² = {t * t} {'−' if s > 0 else '+'} {2 * p}."],
             solution=f"({given.split(' = ')[0]})² = a² + b² {sg(s)} 2ab, затова a² + b² = {t}² {'−' if s > 0 else '+'} 2·{p} = {ans}.")


def sq_steps(s, rng, key):
    a, c = nz(rng, 1, 7), nz(rng, 1, 9)
    res = lin(1, s * a) ** 2 - X * lin(1, s * c)
    if res == P() or len(res.t) < 1:
        raise ValueError
    wrong = X * X + P.const(a * a) - X * lin(1, s * c)
    return steps(key, SQ, f"Опрости стъпка по стъпка: (x {sg(s)} {a})² − x(x {sg(s)} {c})", f"(x+({s * a}))^2-x(x+({s * c}))", res, wrong,
                 "(x ± a)² има и среден член 2ax.",
                 [f"(x {sg(s)} {a})² = x² {sg(s)} {2 * a}x + {a * a}.", f"x(x {sg(s)} {c}) = x² {sg(s)} {c}x.", "Минусът сменя знаците; x² − x² = 0."],
                 f"= x² {sg(s)} {2 * a}x + {a * a} − x² {'−' if s > 0 else '+'} {c}x = {res.fmt()}.")


def sq_structured(s, rng, key):
    a, v = nz(rng, 1, 7), nz(rng, -4, 4)
    A = lin(2, s * a) ** 2
    return q(key, SQ, f"Прилага формулата (a {sg(s)} b)²", "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION",
             f"Даден е изразът A = (2x {sg(s)} {a})². Ще пресмятаме и при x = {val(v)}.", "C",
             part_prompts=[{"id": "a", "label": "а) Запиши A в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Коефициентът пред x", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": A.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(4 * s * a, False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1}],
             distractors=[{"match": f"4x^2+{a * a}", "misconception": "FORMULA_APPLICATION", "explanation": "Липсва удвоеното произведение."}],
             hints=[f"(2x {sg(s)} {a})² = (2x)² {sg(s)} 2·2x·{a} + {a}².", f"Средният член е {val(4 * s * a)}x.", "За в) можеш да заместиш направо в скобата."],
             solution=f"а) A = {A.fmt()}; б) {val(4 * s * a)}; в) (2·{paren(v)} {sg(s)} {a})² = {val(A.eval(x=v))}.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ (a − b)(a + b)

def ds_trick_mc(rng, key):
    a = nz(rng, 1, 9)
    r = X * X - P.const(a * a)
    good = r.fmt()
    wrongs = [(f"−x² − {a * a}", "SIGN", f"(−x)² = x², защото минус по минус дава плюс."),
              ((P.const(a * a) - X * X).fmt(), "SIGN", f"Квадратът на −x е x²; от него вадим {a}²."),
              ((X * X + P.const(a * a)).fmt(), "SIGN", "Произведението на разлика и сбор е разлика на квадрати.")]
    return mc(rng, key, DS, "Прилага формулата (a − b)(a + b)", "ADVANCED", "SIGN", f"(−x − {a})(−x + {a}) е равно на:", good, wrongs,
              ["Тук a = −x, b = " + str(a) + ".", "(a − b)(a + b) = a² − b².", "(−x)² = x²."],
              f"(−x − {a})(−x + {a}) = (−x)² − {a}² = {good}.", "C", expect=r.fmt(False))


def ds_xy_mc(rng, key):
    k, m = nz(rng, 1, 5), nz(rng, 1, 5)
    if k == m:
        raise ValueError
    A, B = P.mono(k, x=1), P.mono(m, y=1)
    r = A * A - B * B
    wrongs = [((A * A + B * B).fmt(), "SIGN", "Разлика по сбор дава разлика на квадрати: знакът е минус."),
              (((A - B) ** 2).fmt(), "FORMULA_APPLICATION", "Това е квадрат на разлика — тук множителите са различни."),
              ((P.mono(k, x=2) - P.mono(m, y=2)).fmt() if k > 1 or m > 1 else (A * A - P.mono(2, y=2)).fmt(), "FORMULA_APPLICATION", "На квадрат са и коефициентите.")]
    return mc(rng, key, DS, "Прилага формулата (a − b)(a + b)", "INTERMEDIATE", "FORMULA_APPLICATION",
              f"({(A - B).fmt()})({(A + B).fmt()}) е равно на:", r.fmt(), wrongs,
              ["(a − b)(a + b) = a² − b².", f"a = {A.fmt()}, b = {B.fmt()}.", f"a² = {(A * A).fmt()}, b² = {(B * B).fmt()}."],
              f"({(A - B).fmt()})({(A + B).fmt()}) = {r.fmt()}.", "C", expect=r.fmt(False))


# ------------------------------------------------------------------ (a ± b)³

def cube_mc(s, rng, key):
    b = nz(rng, 1, 4)
    p = lin(1, s * b)
    r = p ** 3
    wrongs = [((X ** 3 + P.const(s * b ** 3)).fmt(), "FORMULA_APPLICATION", "(a ± b)³ има четири члена: a³ ± 3a²b + 3ab² ± b³."),
              ((X ** 3 + P.mono(s * b, x=2) + P.mono(b * b, x=1) + P.const(s * b ** 3)).fmt(), "FORMULA_APPLICATION", "Коефициентите са 1, 3, 3, 1."),
              ((X ** 3 + P.mono(s * 3 * b, x=2) - P.mono(3 * b * b, x=1) + P.const(s * b ** 3)).fmt(), "SIGN", "Членът 3ab² е винаги положителен (b² > 0).")]
    return mc(rng, key, CB, f"Прилага формулата (a {sg(s)} b)³", "INTERMEDIATE", "FORMULA_APPLICATION", f"({p.fmt()})³ е равно на:", r.fmt(), wrongs,
              [f"(a {sg(s)} b)³ = a³ {sg(s)} 3a²b + 3ab² {sg(s)} b³.", f"a = x, b = {b}.", "Коефициентите са 1, 3, 3, 1."],
              f"({p.fmt()})³ = x³ {sg(s)} 3·x²·{b} + 3·x·{b}² {sg(s)} {b}³ = {r.fmt()}.", "C", expect=r.fmt(False))


def cube_kx_mc(s, rng, key):
    k, m = rng.choice([(2, 1), (3, 1), (2, 3), (3, 2), (2, 5), (4, 1)])
    p = lin(k, s * m)
    r = p ** 3
    K = P.mono(k, x=1)
    wrongs = [((K ** 3 + P.const(s * m ** 3)).fmt(), "FORMULA_APPLICATION", "Липсват средните членове 3a²b и 3ab²."),
              ((P.mono(k, x=3) + P.mono(s * 3 * k * k * m, x=2) + P.mono(3 * k * m * m, x=1) + P.const(s * m ** 3)).fmt(), "FORMULA_APPLICATION",
               f"({K.fmt()})³ = {(K ** 3).fmt()} — на трета степен е и числото {k}."),
              ((K ** 3 + P.mono(s * 3 * k * m * m, x=2) + P.mono(3 * k * k * m, x=1) + P.const(s * m ** 3)).fmt(), "FORMULA_APPLICATION",
               f"3a²b = 3·({K.fmt()})²·{m} = {3 * k * k * m}x², а 3ab² = 3·{K.fmt()}·{m}² = {3 * k * m * m}x.")]
    return mc(rng, key, CB, f"Прилага формулата (a {sg(s)} b)³", "ADVANCED", "FORMULA_APPLICATION", f"({p.fmt()})³ е равно на:", r.fmt(), wrongs,
              [f"(a {sg(s)} b)³ = a³ {sg(s)} 3a²b + 3ab² {sg(s)} b³.", f"a = {K.fmt()}, b = {m}.", f"({K.fmt()})³ = {(K ** 3).fmt()}, ({K.fmt()})² = {(K * K).fmt()}."],
              f"({p.fmt()})³ = {r.fmt()}.", "C", expect=r.fmt(False))


def cube_missing_mc(s, rng, key):
    b = nz(rng, 1, 5)
    mid = P.mono(s * 3 * b, x=2)
    wrongs = [(P.mono(s * b, x=2).fmt(), "FORMULA_APPLICATION", "Коефициентът е 3: 3a²b."),
              (P.mono(s * 3 * b * b, x=2).fmt(), "FORMULA_APPLICATION", f"3a²b = 3·x²·{b} — b е на първа степен."),
              (P.mono(-s * 3 * b, x=2).fmt(), "SIGN", "Знакът на 3a²b е като знака в скобата.")]
    return mc(rng, key, CB, "Допълва формула за куб на двучлен", "INTERMEDIATE", "FORMULA_APPLICATION",
              f"Кой член липсва: (x {sg(s)} {b})³ = x³ ··· + {3 * b * b}x {sg(s)} {b ** 3}? (··· е липсващият член със знака си)", mid.fmt(), wrongs,
              [f"(a {sg(s)} b)³ = a³ {sg(s)} 3a²b + 3ab² {sg(s)} b³.", f"Липсва 3a²b = 3·x²·{b}.", "Знакът е като знака в скобата."],
              f"Липсващият член е {sg(s)}3·x²·{b} = {mid.fmt()}.", "C", expect=mid.fmt(False))


def cube_number_mc(s, rng, key):
    base = rng.choice([10, 20, 30, 100])
    n = base + s
    good = n ** 3
    wrongs = [(str(base ** 3 + s), "FORMULA_APPLICATION", "(a ± b)³ ≠ a³ ± b³ — липсват 3a²b и 3ab²."),
              (str(base ** 3 + s * base * base + base + s), "FORMULA_APPLICATION", "Коефициентите са 1, 3, 3, 1."),
              (str(good + s * 2 * 3 * base), "FORMULA_APPLICATION", f"3ab² = 3·{base}·1 = {3 * base}; провери събирането.")]
    return mc(rng, key, CB, "Пресмята рационално с формула", "INTERMEDIATE", "FORMULA_APPLICATION", f"Колко е {n}³? Пресметни с формулата ({base} {sg(s)} 1)³.", str(good), wrongs,
              [f"({base} {sg(s)} 1)³ = {base}³ {sg(s)} 3·{base}²·1 + 3·{base}·1² {sg(s)} 1³.", f"{base}³ = {base ** 3}, 3·{base}² = {3 * base * base}.", "Събери внимателно."],
              f"{n}³ = {base ** 3} {sg(s)} {3 * base * base} + {3 * base} {sg(s)} 1 = {good}.", "C", expect=str(good))


def cube_expr(s, rng, key, pts=2):
    k, b = rng.choice([1, 1, 2]), nz(rng, 1, 5)
    if math.gcd(k, b) != 1:
        raise ValueError
    p = lin(k, s * b)
    r = p ** 3
    K = P.mono(k, x=1)
    return q(key, CB, f"Прилага формулата (a {sg(s)} b)³", "EXPRESSION", "INTERMEDIATE", 90, pts, "FORMULA_APPLICATION",
             f"Разкрий по формулата за куб на {'сбор' if s > 0 else 'разлика'}: ({p.fmt()})³", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (K ** 3 + P.const(s * b ** 3)).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "(a ± b)³ има четири члена."}],
             hints=[f"(a {sg(s)} b)³ = a³ {sg(s)} 3a²b + 3ab² {sg(s)} b³.", f"a = {K.fmt()}, b = {b}.", "Коефициентите са 1, 3, 3, 1."],
             solution=f"({p.fmt()})³ = ({K.fmt()})³ {sg(s)} 3·({K.fmt()})²·{b} + 3·{K.fmt()}·{b}² {sg(s)} {b}³ = {r.fmt()}.")


def cube_number(s, rng, key, pts=1):
    base = rng.choice([10, 20, 30, 40, 50, 60, 70, 80, 90])
    n = base + s
    return q(key, CB, "Пресмята рационално с формула", "NUMERIC", "ADVANCED", 90, pts, "FORMULA_APPLICATION",
             f"Пресметни с формулата за куб на {'сбор' if s > 0 else 'разлика'}: {n}³", "C", answer=str(n ** 3),
             distractors=[{"match": str(base ** 3 + s), "misconception": "FORMULA_APPLICATION", "explanation": "Липсват средните членове 3a²b и 3ab²."}],
             hints=[f"{n} = {base} {sg(s)} 1.", f"({base} {sg(s)} 1)³ = {base}³ {sg(s)} 3·{base}² + 3·{base} {sg(s)} 1.", f"{base ** 3} {sg(s)} {3 * base * base} + {3 * base} {sg(s)} 1."],
             solution=f"{n}³ = ({base} {sg(s)} 1)³ = {base ** 3} {sg(s)} {3 * base * base} + {3 * base} {sg(s)} 1 = {n ** 3}.")


def cube_steps(s, rng, key):
    b = nz(rng, 1, 4)
    res = lin(1, s * b) ** 3 - X * (X * X + P.mono(s * 3 * b, x=1))
    wrong = X ** 3 + P.const(s * b ** 3) - X * (X * X + P.mono(s * 3 * b, x=1))
    return steps(key, CB, f"Опрости стъпка по стъпка: (x {sg(s)} {b})³ − x(x² {sg(s)} {3 * b}x)", f"(x+({s * b}))^3-x(x^2+({3 * s * b})x)", res, wrong,
                 "(x ± b)³ ≠ x³ ± b³.",
                 [f"(x {sg(s)} {b})³ = x³ {sg(s)} {3 * b}x² + {3 * b * b}x {sg(s)} {b ** 3}.", f"x(x² {sg(s)} {3 * b}x) = x³ {sg(s)} {3 * b}x².", "Остават два члена."],
                 f"= {(lin(1, s * b) ** 3).fmt()} − ({(X * (X * X + P.mono(s * 3 * b, x=1))).fmt()}) = {res.fmt()}.")


def cube_structured(s, rng, key):
    b, v = nz(rng, 1, 3), nz(rng, -3, 3)
    A = lin(1, s * b) ** 3
    return q(key, CB, f"Прилага формулата (a {sg(s)} b)³", "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION",
             f"Даден е изразът A = (x {sg(s)} {b})³. Ще пресмятаме и при x = {val(v)}.", "C",
             part_prompts=[{"id": "a", "label": "а) Запиши A в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Коефициентът пред x²", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": A.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(3 * s * b, False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1}],
             distractors=[{"match": (X ** 3 + P.const(s * b ** 3)).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Липсват средните членове."}],
             hints=[f"(a {sg(s)} b)³ = a³ {sg(s)} 3a²b + 3ab² {sg(s)} b³.", f"Коефициентът пред x² е {sg(s)}3·{b}.", "За в) замести направо в скобата."],
             solution=f"а) A = {A.fmt()}; б) {val(3 * s * b)}; в) ({paren(v)} {sg(s)} {b})³ = {val(A.eval(x=v))}.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ a³ ± b³

def sc_product_mc(s, rng, key):
    b = nz(rng, 1, 6)
    first = lin(1, s * b)
    second = X * X + P.mono(-s * b, x=1) + P.const(b * b)
    res = first * second
    wrongs = [((first ** 3).fmt(), "FORMULA_APPLICATION", f"Това е ({first.fmt()})³, а не произведението от формулата."),
              ((first * (X * X + P.mono(s * b, x=1) + P.const(b * b))).fmt(), "SIGN", "Средният член във втората скоба е с обратен знак — тогава средните членове се унищожават."),
              ((X ** 3 - P.const(s * b ** 3)).fmt(), "SIGN", f"Знакът пред {b ** 3} е като знака в първата скоба.")]
    return mc(rng, key, SC, f"Прилага формулата a³ {sg(s)} b³", "INTERMEDIATE", "FORMULA_APPLICATION",
              f"({first.fmt()})({second.fmt()}) е равно на:", res.fmt(), wrongs,
              [f"a³ {sg(s)} b³ = (a {sg(s)} b)(a² {sg(-s)} ab + b²).", f"a = x, b = {b}.", "Средните членове се унищожават."],
              f"({first.fmt()})({second.fmt()}) = x³ {sg(s)} {b}³ = {res.fmt()}.", "C", expect=res.fmt(False))


def sc_factor_mc(s, rng, key):
    k, b = rng.choice([1, 1, 1, 2, 3]), nz(rng, 1, 5)
    if math.gcd(k, b) != 1:
        raise ValueError
    K = P.mono(k, x=1)
    target = K ** 3 + P.const(s * b ** 3)
    sq = lambda m: (K * K + m * b * K + P.const(b * b)).fmt()  # noqa: E731
    first = lin(k, s * b)
    good = f"({first.fmt()})({sq(-s)})"
    wrongs = [(f"({first.fmt()})³", "FORMULA_APPLICATION", f"({first.fmt()})³ има и средни членове."),
              (f"({first.fmt()})({sq(s)})", "SIGN", "Средният член в непълния квадрат е с обратния знак."),
              (f"({lin(k, -s * b).fmt()})({sq(s)})", "SIGN", f"Това е разлагането на {(K ** 3 - P.const(s * b ** 3)).fmt()}.")]
    return mc(rng, key, SC, f"Разлага {'сбор' if s > 0 else 'разлика'} на кубове", "ADVANCED", "FACTORISATION", f"Разложи на множители: {target.fmt()}", good, wrongs,
              [f"Запиши като a³ {sg(s)} b³: a = {K.fmt()}, b = {b}.", f"a³ {sg(s)} b³ = (a {sg(s)} b)(a² {sg(-s)} ab + b²).", "Средният член е ab с обратния знак."],
              f"{target.fmt()} = ({K.fmt()})³ {sg(s)} {b}³ = {good}.", "C", expect=target.fmt(False))


def sc_second_factor_mc(s, rng, key):
    b = nz(rng, 2, 6)
    gp = X * X + P.mono(-s * b, x=1) + P.const(b * b)
    good = gp.fmt()
    wrongs = [((X * X + P.mono(s * b, x=1) + P.const(b * b)).fmt(), "SIGN", "Средният член е с обратния знак на този в първата скоба."),
              ((X * X + P.mono(-s * 2 * b, x=1) + P.const(b * b)).fmt(), "FORMULA_APPLICATION", "В непълния квадрат средният член е ab, а не 2ab."),
              ((X * X + P.const(b * b)).fmt(), "FORMULA_APPLICATION", "Липсва средният член ab.")]
    return mc(rng, key, SC, f"Прилага формулата a³ {sg(s)} b³", "INTERMEDIATE", "FORMULA_APPLICATION",
              f"x³ {sg(s)} {b ** 3} = (x {sg(s)} {b})(···). Кой е вторият множител?", good, wrongs,
              [f"a³ {sg(s)} b³ = (a {sg(s)} b)(a² {sg(-s)} ab + b²).", f"a = x, b = {b}.", f"ab = {b}x, b² = {b * b}."],
              f"x³ {sg(s)} {b}³ = (x {sg(s)} {b})({good}).", "C", expect=gp.fmt(False))


def sc_factor_expr(s, rng, key, pts=2):
    b = nz(rng, 1, 6)
    target = X ** 3 + P.const(s * b ** 3)
    second = X * X + P.mono(-s * b, x=1) + P.const(b * b)
    return q(key, SC, f"Разлага {'сбор' if s > 0 else 'разлика'} на кубове", "EXPRESSION", "ADVANCED", 120, pts, "FACTORISATION",
             f"Разложи на множители: {target.fmt()}", "C", answer=f"({lin(1, s * b).fmt(False)})({second.fmt(False)})", form="FACTORISED",
             distractors=[{"match": f"({lin(1, s * b).fmt(False)})({(X * X + P.mono(s * b, x=1) + P.const(b * b)).fmt(False)})", "misconception": "SIGN",
                           "explanation": "Средният член в непълния квадрат е с обратния знак."}],
             hints=[f"{b ** 3} = {b}³.", f"a³ {sg(s)} b³ = (a {sg(s)} b)(a² {sg(-s)} ab + b²).", f"a = x, b = {b}."],
             solution=f"{target.fmt()} = x³ {sg(s)} {b}³ = ({lin(1, s * b).fmt()})({second.fmt()}).")


def sc_expand_expr(s, rng, key, pts=2):
    b = nz(rng, 1, 7)
    first = lin(1, s * b)
    second = X * X + P.mono(-s * b, x=1) + P.const(b * b)
    r = first * second
    return q(key, SC, f"Прилага формулата a³ {sg(s)} b³", "EXPRESSION", "INTERMEDIATE", 90, pts, "FORMULA_APPLICATION",
             f"Умножи и запиши в нормален вид: ({first.fmt()})({second.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (first ** 3).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Това е кубът на двучлена, а не произведението от формулата."}],
             hints=["Разпознай формулата.", "Средните членове се унищожават.", f"Остават x³ и {val(s * b ** 3)}."],
             solution=f"({first.fmt()})({second.fmt()}) = x³ {sg(s)} {b}³ = {r.fmt()}.")


def sc_number(s, rng, key, pts=1):
    a, b = rng.choice([(7, 3), (8, 2), (6, 4), (9, 1), (5, 5), (11, 9), (13, 7), (12, 8)]) if s > 0 else rng.choice([(7, 3), (8, 2), (6, 4), (9, 1), (11, 1), (12, 2), (13, 3)])
    ans = a ** 3 + s * b ** 3
    f1, f2 = a + s * b, a * a - s * a * b + b * b
    return q(key, SC, "Пресмята рационално с формула", "NUMERIC", "ADVANCED", 90, pts, "FORMULA_APPLICATION",
             f"Пресметни с формулата за {'сбор' if s > 0 else 'разлика'} на кубове: {a}³ {sg(s)} {b}³", "C", answer=str(ans),
             distractors=[{"match": str((a + s * b) ** 3), "misconception": "FORMULA_APPLICATION", "explanation": f"a³ {sg(s)} b³ ≠ (a {sg(s)} b)³."}],
             hints=[f"a³ {sg(s)} b³ = (a {sg(s)} b)(a² {sg(-s)} ab + b²).", f"a {sg(s)} b = {f1}.", f"a² {sg(-s)} ab + b² = {a * a} {sg(-s)} {a * b} + {b * b} = {f2}."],
             solution=f"{a}³ {sg(s)} {b}³ = ({a} {sg(s)} {b})({a * a} {sg(-s)} {a * b} + {b * b}) = {f1}·{f2} = {ans}.")


def sc_steps(s, rng, key):
    b = nz(rng, 1, 4)
    A = lin(1, s * b) * (X * X + P.mono(-s * b, x=1) + P.const(b * b))
    res = A - lin(1, s * b) ** 3
    wrong = A - (X ** 3 + P.const(s * b ** 3))
    start = f"(x+({s * b}))(x^2+({-s * b})x+{b * b})-(x+({s * b}))^3"
    second = (X * X + P.mono(-s * b, x=1) + P.const(b * b)).fmt()
    return steps(key, SC, f"Опрости стъпка по стъпка: (x {sg(s)} {b})({second}) − (x {sg(s)} {b})³", start, res, wrong,
                 "(x ± b)³ ≠ x³ ± b³ — разкрий куба по формулата.",
                 [f"Първото произведение е x³ {sg(s)} {b ** 3}.", f"(x {sg(s)} {b})³ = {(lin(1, s * b) ** 3).fmt()}.", "Внимавай с минуса пред куба."],
                 f"= x³ {sg(s)} {b ** 3} − ({(lin(1, s * b) ** 3).fmt()}) = {res.fmt()}.")


def sc_structured(s, rng, key):
    b, v = nz(rng, 1, 4), nz(rng, -3, 3)
    if v == -s * b:
        raise ValueError
    A = lin(1, s * b) * (X * X + P.mono(-s * b, x=1) + P.const(b * b))
    return q(key, SC, f"Прилага формулата a³ {sg(s)} b³", "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION",
             f"Даден е изразът A = (x {sg(s)} {b})({(X * X + P.mono(-s * b, x=1) + P.const(b * b)).fmt()}). Ще пресмятаме при x = {val(v)} и при x = {val(-s * b)}.", "C",
             part_prompts=[{"id": "a", "label": "а) Запиши A в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A при x = {val(-s * b)}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": A.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": "0", "points": 1}],
             distractors=[{"match": (lin(1, s * b) ** 3).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": f"Това е кубът на {'сбора' if s > 0 else 'разликата'}, а не {'сборът' if s > 0 else 'разликата'} на кубовете."}],
             hints=[f"Формулата за {'сбор' if s > 0 else 'разлика'} на кубове.", f"A = x³ {sg(s)} {b ** 3}.", f"При x = {val(-s * b)} първата скоба е 0."],
             solution=f"а) A = {A.fmt()}; б) {val(A.eval(x=v))}; в) 0, защото x {sg(s)} {b} = 0.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ tests

def pools(f):
    """(multiple choice, short answer, multi-step) templates for one formula."""
    if f in ("SQ+", "SQ-"):
        s = 1 if f == "SQ+" else -1
        return ([partial(sq_expand_mc, s), partial(sq_xy_mc, s), partial(sq_missing_mc, s), partial(sq_reverse_mc, s)],
                [partial(sq_expr, s), partial(sq_number, s), partial(sq_sum_of_squares, s)],
                [partial(sq_steps, s), partial(sq_structured, s)])
    if f == "DS":
        return ([TF.ds_mc, ds_xy_mc, TF.ds_factor_ok, ds_trick_mc], [TF.ds_xy_expr, TF.ds_number, g.c_diff_squares], [TF.ds_steps, TF.ds_structured])
    if f in ("CB+", "CB-"):
        s = 1 if f == "CB+" else -1
        return ([partial(cube_mc, s), partial(cube_kx_mc, s), partial(cube_missing_mc, s), partial(cube_number_mc, s)],
                [partial(cube_expr, s), partial(cube_number, s)],
                [partial(cube_steps, s), partial(cube_structured, s)])
    s = 1 if f == "SC+" else -1
    return ([partial(sc_product_mc, s), partial(sc_factor_mc, s), partial(sc_second_factor_mc, s)],
            [partial(sc_factor_expr, s), partial(sc_expand_expr, s), partial(sc_number, s)],
            [partial(sc_steps, s), partial(sc_structured, s)])


FORMULAS = [("SQ+", "Квадрат на сбор (a + b)²"), ("SQ-", "Квадрат на разлика (a − b)²"), ("DS", "Разлика на квадрати (a − b)(a + b)"),
            ("CB+", "Куб на сбор (a + b)³"), ("CB-", "Куб на разлика (a − b)³"), ("SC+", "Сбор на кубове a³ + b³"), ("SC-", "Разлика на кубове a³ − b³")]
TOPIC = "Формули за съкратено умножение"
TESTS = [(f"FML-{i + 1}{v}", [f], f"{TOPIC} · {name} — упражнение {n}", "ALLOWED")
         for i, (f, name) in enumerate(FORMULAS) for n, v in ((1, "A"), (2, "B"))]
TESTS += [("FML-MA", [f for f, _ in FORMULAS], f"{TOPIC} · Всички формули — смесено упражнение 1", "ALLOWED"),
          ("FML-MB", [f for f, _ in FORMULAS], f"{TOPIC} · Всички формули — смесено упражнение 2", "NONE")]


def build(existing_prompts):
    rng = random.Random(20261021)
    seen = set(existing_prompts)
    questions, tests = [], []
    for key, formulas, title, hints in TESTS:
        items, texts = [], set()
        for slot, n in enumerate((12, 5, 3)):
            pl = [(f, pools(f)[slot]) for f in formulas]
            for i in range(n):
                f, pool = pl[i % len(pl)]
                made = None
                for attempt in range(150):
                    fn = pool[(i // len(pl) + attempt) % len(pool)]
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
