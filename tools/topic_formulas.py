# Topic "Тъждества и формули": practice tests for the formulas for abbreviated multiplication
# (square of a binomial, difference of squares, cubes) and identities, plus class-test preparation.
# Answers are computed exactly; validate_content.ts re-checks every item with the app's engine.
import random

import gen_tests as g
import lessons as L

P, F, nz, lin, val, paren = g.P, g.F, g.nz, g.lin, g.val, g.paren
mc, q = g.mc, g.q
X = P.mono(1, x=1)
Y = P.mono(1, y=1)


def sgn(v):
    return "+" if v > 0 else "−"


def steps_item(key, skill, outcome, misc, prompt, start, result, wrong, wrong_expl, hints, solution):
    return q(key, skill, outcome, "STEPS", "ADVANCED", 180, 3, misc, prompt, "C",
             answer=result.fmt(False), form="NORMAL_FORM", parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": wrong.fmt(False), "misconception": misc, "explanation": wrong_expl}],
             hints=hints, solution=solution, rubric="3 т. за верни преходи и верен краен резултат; частични точки за верните редове.")


# ------------------------------------------------------------------ 1. square of a binomial

SQ, SQ_OUT = "C.square-binomial", "Прилага формулите (a ± b)²"


def sq_xy_mc(rng, key):
    k, m, s = nz(rng, 1, 4), nz(rng, 1, 4), rng.choice([1, -1])
    if k == m:
        raise ValueError
    b = k * X + s * m * Y
    r = b ** 2
    wrongs = [((k * k) * X * X + (m * m) * Y * Y).fmt(), ((k * k) * X * X + (s * k * m) * X * Y + (m * m) * Y * Y).fmt(),
              ((k * k) * X * X + (2 * s * k * m) * X * Y - (m * m) * Y * Y).fmt()]
    expl = ["Липсва удвоеното произведение 2ab.", f"Средният член е 2·{k}x·{m}y = {2 * k * m}xy — удвоен.", "Квадратът на второто събираемо винаги е положителен."]
    return mc(rng, key, SQ, SQ_OUT, "INTERMEDIATE", "FORMULA_APPLICATION", f"({b.fmt()})² е равно на:", r.fmt(),
              [(w, "FORMULA_APPLICATION" if i < 2 else "SIGN", e) for i, (w, e) in enumerate(zip(wrongs, expl))],
              [f"(a {sgn(s)} b)² = a² {sgn(s)} 2ab + b².", f"Тук a = {P.mono(k, x=1).fmt()}, b = {P.mono(m, y=1).fmt()}.", f"2ab = 2·{P.mono(k, x=1).fmt()}·{P.mono(m, y=1).fmt()} = {P.mono(2 * k * m, x=1, y=1).fmt()}."],
              f"({b.fmt()})² = ({P.mono(k, x=1).fmt()})² {sgn(s)} 2·{P.mono(k, x=1).fmt()}·{P.mono(m, y=1).fmt()} + ({P.mono(m, y=1).fmt()})² = {r.fmt()}.", "C", expect=r.fmt(False))


def sq_missing_mc(rng, key):
    a, s = nz(rng, 2, 9), rng.choice([1, -1])
    mid = P.mono(2 * a * s, x=1)
    wrongs = [(P.mono(a * s, x=1).fmt(), "FORMULA_APPLICATION", "Средният член е удвоеното произведение: 2·x·" + str(a) + "."),
              (P.mono(a * a * s, x=1).fmt(), "FORMULA_APPLICATION", f"{a * a} е квадратът на {a}; средният член е 2·{a}·x."),
              (P.mono(-2 * a * s, x=1).fmt(), "SIGN", "Знакът на средния член е като знака в скобата.")]
    return mc(rng, key, SQ, "Допълва формула за квадрат на двучлен", "FOUNDATIONAL", "FORMULA_APPLICATION",
              f"Кой член липсва: ({lin(1, s * a).fmt()})² = x² ··· + {a * a}? (на мястото на ··· стои липсващият член със знака си)", mid.fmt(), wrongs,
              ["(a ± b)² = a² ± 2ab + b².", f"Средният член е 2·x·{a}.", "Знакът му е като знака в скобата."],
              f"({lin(1, s * a).fmt()})² = x² {sgn(s)} {2 * a}x + {a * a}, затова липсва {mid.fmt()}.", "C", expect=mid.fmt(False))


def sq_expr(rng, key, pts=2):
    a, b = nz(rng, 2, 5), nz(rng, -7, 7)
    p = lin(a, b)
    r = p ** 2
    return q(key, SQ, SQ_OUT, "EXPRESSION", "INTERMEDIATE", 75, pts, "FORMULA_APPLICATION",
             f"Повдигни на квадрат и запиши в нормален вид: ({p.fmt()})²", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (P.mono(a * a, x=2) + P.const(b * b)).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Липсва удвоеното произведение 2ab."},
                          {"match": (P.mono(a * a, x=2) + P.mono(a * b, x=1) + P.const(b * b)).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Средният член е 2ab — удвоен."}],
             hints=["(a + b)² = a² + 2ab + b².", f"a = {a}x, b = {val(b)}.", f"a² = {a * a}x², 2ab = {val(2 * a * b)}x, b² = {b * b}."],
             solution=f"({p.fmt()})² = ({a}x)² {sgn(b)} 2·{a}x·{abs(b)} + {abs(b)}² = {r.fmt()}.")


def sq_number(rng, key, pts=1):
    base, d = rng.choice([30, 40, 50, 60, 70, 80, 90, 100, 200]), nz(rng, -3, 3)
    n = base + d
    return q(key, SQ, "Пресмята рационално с формула", "NUMERIC", "INTERMEDIATE", 60, pts, "FORMULA_APPLICATION",
             f"Пресметни рационално с формулата за квадрат на двучлен: {n}²", "C", answer=str(n * n),
             distractors=[{"match": str(base * base + d * d), "misconception": "FORMULA_APPLICATION", "explanation": f"Липсва удвоеното произведение 2·{base}·{abs(d)} = {2 * base * abs(d)}."}],
             hints=[f"Запиши {n} = {base} {sgn(d)} {abs(d)}.", f"({base} {sgn(d)} {abs(d)})² = {base}² {sgn(d)} 2·{base}·{abs(d)} + {abs(d)}².", f"{base * base} {sgn(d)} {2 * base * abs(d)} + {d * d}."],
             solution=f"{n}² = ({base} {sgn(d)} {abs(d)})² = {base * base} {sgn(d)} {2 * base * abs(d)} + {d * d} = {n * n}.")


def sq_steps(rng, key):
    a, b = nz(rng, 1, 6), nz(rng, 1, 6)
    res = lin(1, a) ** 2 - lin(1, -b) ** 2
    wrong = lin(1, a) ** 2 - X * X + P.mono(2 * b, x=1) + P.const(b * b)
    return steps_item(key, SQ, "Опростява изрази с формули", "BRACKETS",
                      f"Опрости стъпка по стъпка (всеки ред трябва да е равен на предишния): (x + {a})² − (x − {b})²",
                      f"(x+{a})^2-(x-{b})^2", res, wrong, "Минусът пред втория квадрат сменя знака на всеки негов член, и на +b².",
                      ["Разкрий всеки квадрат по формулата — резултата пиши в скоби.", f"(x − {b})² = x² − {2 * b}x + {b * b}.", "Минусът пред втората скоба сменя всички знаци."],
                      f"= x² + {2 * a}x + {a * a} − (x² − {2 * b}x + {b * b}) = {res.fmt()}.")


def sq_structured(rng, key):
    a, v = nz(rng, 1, 6), nz(rng, -4, 4)
    A, B = lin(1, a) ** 2, lin(1, -a) ** 2
    return q(key, SQ, SQ_OUT, "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION", f"Дадени са A = (x + {a})² и B = (x − {a})². Ще пресмятаме и при x = {val(v)}.", "C",
             part_prompts=[{"id": "a", "label": "а) A + B в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) A − B в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A − B при x = {val(v)}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": (A + B).fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "EXPRESSION", "answer": (A - B).fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val((A - B).eval(x=v), False), "points": 1}],
             distractors=[{"match": f"2x^2+{2 * a * a}", "misconception": "FORMULA_APPLICATION", "explanation": "Това е A + B — провери коя част е."},
                          {"match": "0", "misconception": "FORMULA_APPLICATION", "explanation": "Удвоените произведения са с различни знаци и не се унищожават при изваждане."}],
             hints=["Разкрий двата квадрата.", f"A = x² + {2 * a}x + {a * a}, B = x² − {2 * a}x + {a * a}.", "При A − B остава само удвоеното произведение, взето два пъти."],
             solution=f"а) A + B = {(A + B).fmt()}; б) A − B = {(A - B).fmt()}; в) {(A - B).fmt()} при x = {val(v)} е {val((A - B).eval(x=v))}.",
             rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ 2. difference of squares

DS, DS_OUT = "C.difference-squares", "Прилага формулата (a − b)(a + b) = a² − b²"


def ds_mc(rng, key):
    k, m = nz(rng, 1, 5), nz(rng, 1, 9)
    a = P.mono(k, x=1)
    r = a * a - P.const(m * m)
    wrongs = [((a * a) + P.const(m * m)).fmt(), (lin(k, -m) ** 2).fmt(), (P.mono(k, x=2) - P.const(m * m)).fmt() if k != 1 else (a * a - P.const(2 * m)).fmt()]
    expl = ["Произведението на разлика и сбор е разлика на квадрати: знакът е минус.", f"Това е ({lin(k, -m).fmt()})² — квадрат на разлика, а не произведение на разлика и сбор.",
            f"На квадрат е цялото {a.fmt()}: ({a.fmt()})² = {(a * a).fmt()}." if k != 1 else f"На квадрат е числото: {m}² = {m * m}."]
    return mc(rng, key, DS, DS_OUT, "FOUNDATIONAL", "FORMULA_APPLICATION", f"({lin(k, -m).fmt()})({lin(k, m).fmt()}) е равно на:", r.fmt(),
              [(w, "FORMULA_APPLICATION" if i else "SIGN", e) for i, (w, e) in enumerate(zip(wrongs, expl))],
              ["(a − b)(a + b) = a² − b².", f"a = {a.fmt()}, b = {m}.", "Средните членове се унищожават."],
              f"({lin(k, -m).fmt()})({lin(k, m).fmt()}) = ({a.fmt()})² − {m}² = {r.fmt()}.", "C", expect=r.fmt(False))


def ds_factor_mc(rng, key):
    k, m = nz(rng, 1, 6), nz(rng, 1, 9)
    a = P.mono(k, x=1)
    target = a * a - P.const(m * m)
    good = f"({lin(k, -m).fmt()})({lin(k, m).fmt()})"
    wrongs = [(f"({lin(k, -m).fmt()})²", "FORMULA_APPLICATION", f"({lin(k, -m).fmt()})² = {(lin(k, -m) ** 2).fmt()} — има и среден член."),
              (f"({lin(k, m).fmt()})²", "FORMULA_APPLICATION", f"({lin(k, m).fmt()})² = {(lin(k, m) ** 2).fmt()} — това не е разлика на квадрати."),
              (f"({P.mono(k * k, x=1).fmt()} − {m * m})({P.mono(k * k, x=1).fmt()} + {m * m})" if k > 1 else f"(x − {m * m})(x + {m * m})", "FORMULA_APPLICATION",
               f"В скобите са основите a = {a.fmt()} и b = {m}, а не квадратите им.")]
    return mc(rng, key, DS, "Разлага разлика на квадрати", "INTERMEDIATE", "FACTORISATION", f"Разложи на множители: {target.fmt()}", good, wrongs,
              [f"Запиши {target.fmt()} като a² − b².", f"a = {a.fmt()}, b = {m}.", "a² − b² = (a − b)(a + b)."],
              f"{target.fmt()} = ({a.fmt()})² − {m}² = {good}.", "C", expect=target.fmt(False))


def ds_xy_expr(rng, key, pts=2):
    k, m = nz(rng, 1, 5), nz(rng, 1, 5)
    A, B = P.mono(k, x=1), P.mono(m, y=1)
    r = A * A - B * B
    return q(key, DS, DS_OUT, "EXPRESSION", "INTERMEDIATE", 75, pts, "FORMULA_APPLICATION",
             f"Умножи с формула и запиши в нормален вид: ({(A - B).fmt()})({(A + B).fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (A * A + B * B).fmt(False), "misconception": "SIGN", "explanation": "Разлика по сбор дава разлика на квадрати: знакът е минус."},
                          {"match": ((A - B) ** 2).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Това е квадрат на разлика, а не произведение на разлика и сбор."}],
             hints=["(a − b)(a + b) = a² − b².", f"a = {A.fmt()}, b = {B.fmt()}.", f"a² = {(A * A).fmt()}, b² = {(B * B).fmt()}."],
             solution=f"({(A - B).fmt()})({(A + B).fmt()}) = ({A.fmt()})² − ({B.fmt()})² = {r.fmt()}.")


def ds_number(rng, key, pts=1):
    c, d = rng.choice([20, 30, 40, 50, 60, 70, 80, 100]), rng.randint(1, 4)
    return q(key, DS, "Пресмята рационално с формула", "NUMERIC", "INTERMEDIATE", 60, pts, "FORMULA_APPLICATION",
             f"Пресметни рационално с формулата за разлика на квадрати: {c - d}·{c + d}", "C", answer=str(c * c - d * d),
             distractors=[{"match": str(c * c + d * d), "misconception": "SIGN", "explanation": f"({c} − {d})({c} + {d}) = {c}² − {d}² — знакът е минус."}],
             hints=[f"{c - d} = {c} − {d}, {c + d} = {c} + {d}.", f"({c} − {d})({c} + {d}) = {c}² − {d}².", f"{c * c} − {d * d}."],
             solution=f"{c - d}·{c + d} = ({c} − {d})({c} + {d}) = {c}² − {d}² = {c * c} − {d * d} = {c * c - d * d}.")


def ds_steps(rng, key):
    a, b = nz(rng, 1, 6), nz(rng, 1, 6)
    res = lin(1, -a) * lin(1, a) - lin(1, -b) ** 2
    wrong = lin(1, -a) * lin(1, a) - X * X - P.mono(2 * b, x=1) + P.const(b * b)
    return steps_item(key, DS, "Опростява изрази с формули", "BRACKETS",
                      f"Опрости стъпка по стъпка (всеки ред трябва да е равен на предишния): (x − {a})(x + {a}) − (x − {b})²",
                      f"(x-{a})(x+{a})-(x-{b})^2", res, wrong, "Минусът пред квадрата сменя знака на всеки негов член.",
                      ["Първото произведение е разлика на квадрати.", f"(x − {b})² = x² − {2 * b}x + {b * b}.", "Внимавай с минуса пред втората скоба."],
                      f"= x² − {a * a} − (x² − {2 * b}x + {b * b}) = {res.fmt()}.")


def ds_structured(rng, key):
    a, b, v = nz(rng, 1, 6), nz(rng, 1, 6), nz(rng, -4, 4)
    A = lin(1, -a) * lin(1, a) - X * lin(1, -b)
    return q(key, DS, DS_OUT, "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION", f"Даден е изразът A = (x − {a})(x + {a}) − x(x − {b}).", "C",
             part_prompts=[{"id": "a", "label": "а) Запиши A в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Коефициентът пред x в нормалния вид на A", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": A.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(b), "points": 1}],
             distractors=[{"match": (P.mono(-b, x=1) - P.const(a * a)).fmt(False), "misconception": "SIGN", "explanation": f"−x·(−{b}) = +{b}x."}],
             hints=[f"(x − {a})(x + {a}) = x² − {a * a}.", f"x(x − {b}) = x² − {b}x.", "x² − x² = 0."],
             solution=f"а) A = x² − {a * a} − x² + {b}x = {A.fmt()}; б) {val(A.eval(x=v))}; в) {b}.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ 3. cubes

CB, CB_OUT = "C.cube-binomial", "Прилага формулите (a ± b)³"


def cube_expr(rng, key, pts=2):
    b = nz(rng, -4, 4)
    r = lin(1, b) ** 3
    return q(key, CB, CB_OUT, "EXPRESSION", "INTERMEDIATE", 90, pts, "FORMULA_APPLICATION",
             f"Повдигни на трета степен и запиши в нормален вид: ({lin(1, b).fmt()})³", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (X ** 3 + P.const(b ** 3)).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "(a + b)³ има четири члена: a³ + 3a²b + 3ab² + b³."}],
             hints=["(a ± b)³ = a³ ± 3a²b + 3ab² ± b³.", f"a = x, b = {abs(b)}.", "Коефициентите са 1, 3, 3, 1."],
             solution=f"({lin(1, b).fmt()})³ = x³ {sgn(b)} 3·x²·{abs(b)} + 3·x·{abs(b)}² {sgn(b)} {abs(b)}³ = {r.fmt()}.")


def cube_2x_mc(rng, key):
    s = rng.choice([1, -1])
    b = lin(2, s)
    r = b ** 3
    wrongs = [((8 * X ** 3) + P.const(s)).fmt(), (2 * X ** 3 + 3 * s * 2 * X * X + 3 * 2 * X + P.const(s)).fmt(),
              ((8 * X ** 3) + 6 * s * X * X + 12 * X + P.const(s)).fmt()]
    expl = ["Липсват средните членове 3a²b и 3ab².", "(2x)³ = 8x³ — на трета степен е и числото 2.", "3a²b = 3·(2x)²·1 = 12x², а 3ab² = 3·2x·1 = 6x."]
    return mc(rng, key, CB, CB_OUT, "ADVANCED", "FORMULA_APPLICATION", f"({b.fmt()})³ е равно на:", r.fmt(), [(w, "FORMULA_APPLICATION", e) for w, e in zip(wrongs, expl)],
              ["(a ± b)³ = a³ ± 3a²b + 3ab² ± b³.", "a = 2x, b = 1.", "(2x)³ = 8x³, (2x)² = 4x²."],
              f"({b.fmt()})³ = (2x)³ {sgn(s)} 3·(2x)²·1 + 3·2x·1² {sgn(s)} 1 = {r.fmt()}.", "C", expect=r.fmt(False))


def cube_steps(rng, key):
    b = nz(rng, -4, 4)
    res = lin(1, b) ** 3 - X * (X * X + P.mono(3 * b, x=1))
    wrong = X ** 3 + P.const(b ** 3) - X * (X * X + P.mono(3 * b, x=1))
    return steps_item(key, CB, "Опростява изрази с формули", "FORMULA_APPLICATION",
                      f"Опрости стъпка по стъпка (всеки ред трябва да е равен на предишния): ({lin(1, b).fmt()})³ − x(x² {sgn(b)} {3 * abs(b)}x)",
                      f"(x+({b}))^3-x(x^2+({3 * b})x)", res, wrong, "(x ± b)³ ≠ x³ ± b³ — липсват 3x²b и 3xb².",
                      ["Разкрий куба: (a ± b)³ = a³ ± 3a²b + 3ab² ± b³.", f"x(x² {sgn(b)} {3 * abs(b)}x) = x³ {sgn(b)} {3 * abs(b)}x².", "x³ и 3bx² се унищожават."],
                      f"= {(lin(1, b) ** 3).fmt()} − ({(X * (X * X + P.mono(3 * b, x=1))).fmt()}) = {res.fmt()}.")


def cube_structured(rng, key):
    b, v = rng.randint(1, 4), nz(rng, -3, 3)
    A = lin(1, -b) * (X * X + P.mono(b, x=1) + P.const(b * b))
    return q(key, "C.sum-cubes", "Прилага формулите a³ ± b³", "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION",
             (f"Даден е изразът A = (x − {b})(x² + {b}x + {b * b})." if b > 1 else "Даден е изразът A = (x − 1)(x² + x + 1).") + f" Ще пресмятаме при x = {val(v)} и x = {b}.", "C",
             part_prompts=[{"id": "a", "label": "а) Запиши A в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A при x = {b}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": A.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": "0", "points": 1}],
             distractors=[{"match": (lin(1, -b) ** 3).fmt(False), "misconception": "FORMULA_APPLICATION", "explanation": "Това е кубът на разликата, а не разликата на кубовете."}],
             hints=["Това е формулата за разлика на кубове.", f"A = x³ − {b ** 3}.", f"При x = {b} първата скоба е 0."],
             solution=f"а) A = x³ − {b}³ = {A.fmt()}; б) {val(A.eval(x=v))}; в) 0, защото x − {b} = 0.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ 4. identities

ID, ID_OUT = "C.identities", "Разпознава и доказва тъждества"


def id_equiv_mc(rng, key):
    a = nz(rng, 2, 9)
    r = lin(1, a) ** 2 - P.const(a * a)
    wrongs = [("x²", "FORMULA_APPLICATION", f"(x + {a})² ≠ x² + {a * a}: остава и удвоеното произведение {2 * a}x."),
              ((X * X + P.mono(a, x=1)).fmt(), "FORMULA_APPLICATION", f"Средният член е 2·{a}·x = {2 * a}x."),
              ((X * X + P.mono(2 * a, x=1) - P.const(2 * a * a)).fmt(), "SIGN", f"+{a * a} − {a * a} = 0.")]
    return mc(rng, key, ID, "Намира тъждествено равен израз", "INTERMEDIATE", "FORMULA_APPLICATION",
              f"Кой израз е тъждествено равен на (x + {a})² − {a * a}?", r.fmt(), wrongs,
              ["Тъждествено равни изрази имат равни стойности при всяко x.", f"Разкрий (x + {a})².", f"x² + {2 * a}x + {a * a} − {a * a}."],
              f"(x + {a})² − {a * a} = x² + {2 * a}x + {a * a} − {a * a} = {r.fmt()}.", "C", expect=r.fmt(False))


def id_when_true_mc(rng, key):
    a = nz(rng, 2, 9)
    good = "вярно само при x = 0"
    wrongs = [("тъждество (вярно за всяко x)", "FORMULA_APPLICATION", f"Провери с x = 1: (1 + {a})² = {(1 + a) ** 2}, а 1 + {a * a} = {1 + a * a}. Не е тъждество."),
              ("никога не е вярно", "REASONING", f"При x = 0 двете страни са равни на {a * a}."),
              (f"вярно само при x = {a}", "REASONING", f"При x = {a}: лявата страна е {(2 * a) ** 2}, а дясната {2 * a * a} — не са равни.")]
    return mc(rng, key, ID, "Разпознава тъждества", "ADVANCED", "FORMULA_APPLICATION", f"Равенството (x + {a})² = x² + {a * a} е:", good, wrongs,
              ["Разкрий лявата страна и сравни.", f"Разликата между страните е {2 * a}x.", f"{2 * a}x = 0 само при x = 0."],
              f"(x + {a})² = x² + {2 * a}x + {a * a}. Двете страни са равни само когато {2 * a}x = 0, т.е. при x = 0. Затова не е тъждество.", "C")


def id_find_k(rng, key, pts=1):
    k, s = nz(rng, 2, 9), rng.choice([1, -1])
    return q(key, ID, "Намира параметър за тъждество", "NUMERIC", "INTERMEDIATE", 60, pts, "FORMULA_APPLICATION",
             f"Намери положителното число k, за което (x {sgn(s)} k)² = x² {sgn(s)} {2 * k}x + {k * k} е тъждество.", "C", answer=str(k),
             distractors=[{"match": str(2 * k), "misconception": "FORMULA_APPLICATION", "explanation": f"Средният член е 2k·x, затова 2k = {2 * k} и k = {k}."},
                          {"match": str(k * k), "misconception": "FORMULA_APPLICATION", "explanation": f"Свободният член е k², затова k² = {k * k} и k = {k}."}],
             hints=["(x ± k)² = x² ± 2kx + k².", f"Сравни: 2k = {2 * k}.", f"Провери: k² = {k * k}."],
             solution=f"(x {sgn(s)} k)² = x² {sgn(s)} 2kx + k². От 2k = {2 * k} и k² = {k * k} следва k = {k}.")


def id_box(rng, key, pts=1):
    k, m = nz(rng, 2, 5), nz(rng, 1, 9)
    return q(key, ID, "Допълва тъждество", "NUMERIC", "INTERMEDIATE", 60, pts, "FORMULA_APPLICATION",
             f"Кое число трябва да стои на мястото на □, за да е тъждество: ({k}x − {m})({k}x + {m}) = {k * k}x² − □", "C", answer=str(m * m),
             distractors=[{"match": str(m), "misconception": "FORMULA_APPLICATION", "explanation": f"Вторият член е b² = {m}² = {m * m}."},
                          {"match": str(2 * m), "misconception": "FORMULA_APPLICATION", "explanation": f"b² = {m}·{m}, а не 2·{m}."}],
             hints=["(a − b)(a + b) = a² − b².", f"b = {m}.", f"b² = {m * m}."],
             solution=f"({k}x − {m})({k}x + {m}) = ({k}x)² − {m}² = {k * k}x² − {m * m}, значи □ = {m * m}.")


PROOFS = [
    # (lhs pretty, lhs ascii, rhs poly builder(k), explanation of key step)
    ("(x + {k})² − (x − {k})²", "(x+{k})^2-(x-{k})^2", lambda k: P.mono(4 * k, x=1), "Квадратите x² и {kk} се унищожават, остава 4·{k}·x."),
    ("(x + {k})² − {kk2}x", "(x+{k})^2-{kk2}x", lambda k: X * X + P.const(k * k), "Удвоеното произведение {kk2}x се унищожава."),
    ("(x − {k})(x + {k}) + {kk}", "(x-{k})(x+{k})+{kk}", lambda k: X * X, "Разлика на квадрати: x² − {kk} + {kk} = x²."),
    ("(x + {k})² + (x − {k})²", "(x+{k})^2+(x-{k})^2", lambda k: 2 * X * X + P.const(2 * k * k), "Удвоените произведения ±{kk2}x се унищожават."),
    ("(x + {k})(x − {k}) − (x − {k})²", "(x+{k})(x-{k})-(x-{k})^2", lambda k: P.mono(2 * k, x=1) - P.const(2 * k * k), "x² − {kk} − x² + {kk2}x − {kk}."),
]


def id_proof(rng, key):
    i, k = rng.randrange(len(PROOFS)), nz(rng, 1, 6)
    lp, la, rhs_f, why = PROOFS[i]
    fmtd = dict(k=k, kk=k * k, kk2=2 * k)
    lhs_p, lhs_a = lp.format(**fmtd), la.format(**fmtd)
    rhs = rhs_f(k)
    wrong = rhs + P.const(2 * k * k) if rhs.t.get((), 0) == 0 else rhs - P.const(2 * k * k)
    return steps_item(key, ID, ID_OUT, "FORMULA_APPLICATION",
                      f"Докажи тъждеството {lhs_p} = {rhs.fmt()}: преобразувай лявата страна стъпка по стъпка, докато получиш дясната.",
                      lhs_a, rhs, wrong, "Провери знаците при разкриване на скобите — дясната страна не се получава.",
                      ["Тъждество се доказва, като преобразуваме едната страна до другата.", "Разкрий скобите с формулите за съкратено умножение.", why.format(**fmtd)],
                      f"{lhs_p} = … = {rhs.fmt()}. {why.format(**fmtd)} Лявата страна е тъждествено равна на дясната.")


def id_structured(rng, key):
    k, v = nz(rng, 2, 6), nz(rng, 1, 5)
    lhs = lin(1, k) ** 2 - lin(1, -k) ** 2
    return q(key, ID, ID_OUT, "STRUCTURED", "ADVANCED", 240, 3, "FORMULA_APPLICATION", f"Дадено е равенството (x + {k})² − (x − {k})² = □·x. Ще пресмятаме и при x = {v}.", "C",
             part_prompts=[{"id": "a", "label": "а) Лявата страна в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Числото на мястото на □, за да е тъждество", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": f"в) Стойността на лявата страна при x = {v}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": lhs.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": str(4 * k), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(4 * k * v), "points": 1}],
             distractors=[{"match": "0", "misconception": "FORMULA_APPLICATION", "explanation": "Удвоените произведения са с различни знаци — при изваждане те се събират."},
                          {"match": str(2 * k), "misconception": "FORMULA_APPLICATION", "explanation": f"Удвоеното произведение е {2 * k}x и се взема два пъти: {4 * k}x."}],
             hints=["Разкрий двата квадрата.", f"x² + {2 * k}x + {k * k} − x² + {2 * k}x − {k * k}.", f"Остава {4 * k}x."],
             solution=f"а) {lhs.fmt()}; б) □ = {4 * k}; в) {4 * k}·{v} = {4 * k * v}.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ tests

POOLS = {
    "SQ": ([g.c_square, sq_xy_mc, sq_missing_mc], [sq_expr, sq_number, g.c_value_simplify], [sq_steps, sq_structured]),
    "DS": ([ds_mc, ds_factor_mc, g.c_identity], [g.c_diff_squares, ds_xy_expr, ds_number], [ds_steps, ds_structured]),
    "CB": ([g.c_cube, cube_2x_mc, L.c9_product_mc, L.c9_factor_mc], [cube_expr, L.c9_expand], [cube_steps, cube_structured]),
    "ID": ([g.c_identity, id_equiv_mc, id_when_true_mc], [id_find_k, id_box, g.c_factor_formula], [id_proof, id_structured]),
}
TOPIC = "Тъждества и формули"
EXAM = "Подготовка за контролно"
TESTS = [
    ("FRM-1A", ["SQ"], f"{TOPIC} · Квадрат на двучлен — тренировка 1", "ALLOWED"),
    ("FRM-1B", ["SQ"], f"{TOPIC} · Квадрат на двучлен — тренировка 2", "ALLOWED"),
    ("FRM-2A", ["DS"], f"{TOPIC} · Разлика на квадрати — тренировка 1", "ALLOWED"),
    ("FRM-2B", ["DS"], f"{TOPIC} · Разлика на квадрати — тренировка 2", "ALLOWED"),
    ("FRM-3A", ["CB"], f"{TOPIC} · Кубове — тренировка 1", "ALLOWED"),
    ("FRM-3B", ["CB"], f"{TOPIC} · Кубове — тренировка 2", "ALLOWED"),
    ("FRM-4A", ["ID"], f"{TOPIC} · Тъждества — тренировка 1", "ALLOWED"),
    ("FRM-4B", ["ID"], f"{TOPIC} · Тъждества — тренировка 2", "ALLOWED"),
    ("FRM-K1", ["SQ", "DS", "CB", "ID"], f"{EXAM} · Формули и тъждества — вариант 1", "NONE"),
    ("FRM-K2", ["SQ", "DS", "CB", "ID"], f"{EXAM} · Формули и тъждества — вариант 2", "NONE"),
    ("FRM-K3", ["SQ", "DS", "CB", "ID"], f"{EXAM} · Формули и тъждества — вариант 3", "NONE"),
    ("FRM-K4", ["SQ", "DS", "CB", "ID"], f"{EXAM} · Формули и тъждества — вариант 4", "NONE"),
]


def sig(qd):
    p = qd["draft"]["prompt"]
    return p["text"] + "|" + "|".join(o["text"] for o in p.get("options", []))


def build(existing_prompts):
    rng = random.Random(20261009)
    seen = set(existing_prompts)
    questions, tests = [], []
    for key, topics, title, hints in TESTS:
        items, texts = [], set()
        for slot, n in enumerate((12, 5, 3)):
            pools = [(t, POOLS[t][slot]) for t in topics]
            for i in range(n):
                t, pool = pools[i % len(pools)]
                made = None
                for attempt in range(80):
                    fn = pool[(i // len(pools) + attempt) % len(pool)]
                    k = f"{key}-{len(items) + 1:02d}"
                    g.SIDE.pop(k, None)  # a rejected earlier attempt must not leave its expected value behind
                    try:
                        qd = fn(rng, k)
                    except (ValueError, ZeroDivisionError, KeyError):
                        continue
                    text = qd["draft"]["prompt"]["text"]
                    if text.lower() in texts or sig(qd) in seen or (not qd["draft"]["prompt"].get("options") and text in seen):
                        continue
                    made = qd
                    break
                if not made:
                    raise RuntimeError(f"cannot build {key} item {len(items) + 1}")
                texts.add(made["draft"]["prompt"]["text"].lower())
                seen.add(sig(made))
                if not made["draft"]["prompt"].get("options"):
                    seen.add(made["draft"]["prompt"]["text"])
                items.append(made)
        questions += items
        tests.append({"testKey": key, "title": title, "kind": "THEMATIC", "path": "C", "blueprint": "Тематичен тест (20)",
                      "timeLimitMin": 40, "hintPolicy": hints, "reviewMoment": "AFTER_SUBMIT", "questionKeys": [x["key"] for x in items]})
    return questions, tests
