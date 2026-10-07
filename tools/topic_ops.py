# Topic "Действия с многочлени": practice tests for adding/subtracting polynomials, multiplying a
# polynomial by a monomial and multiplying polynomials, plus summary tests that prepare for the
# class test (контролно). Answers are computed exactly; validate_content.ts re-checks every item.
import random

import gen_tests as g

P, F, nz, lin, val, paren = g.P, g.F, g.nz, g.lin, g.val, g.paren
mc, q = g.mc, g.q
X = P.mono(1, x=1)


def quad(rng, lead=(1, 6)):
    return P.mono(nz(rng, *lead), x=2) + P.mono(nz(rng, -7, 7), x=1) + P.const(nz(rng, -9, 9))


def xy_poly(rng):
    return P.mono(nz(rng, -6, 6), x=2) + P.mono(nz(rng, -6, 6), x=1, y=1) + P.mono(nz(rng, -6, 6), y=2)


def first_only(m, p):
    """m·(first term) + the other terms unchanged — the classic 'multiplied only the first term'."""
    po = p.ordered()
    return m * P({po[0][0]: po[0][1]}) + P({k: v for k, v in po[1:]})


def only_first_sign(p, qq):
    """p − q where only the first term of q changed sign."""
    qo = qq.ordered()
    return p - P({qo[0][0]: qo[0][1]}) + P({k: v for k, v in qo[1:]})


def steps_item(key, skill, outcome, misc, text_pretty, start, result, wrong, wrong_expl, hints, solution, path="C"):
    return q(key, skill, outcome, "STEPS", "ADVANCED", 180, 3, misc,
             f"Опрости стъпка по стъпка (всеки ред трябва да е равен на предишния): {text_pretty}", path,
             answer=result.fmt(False), form="NORMAL_FORM", parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": wrong.fmt(False), "misconception": misc, "explanation": wrong_expl}] if wrong != result else [],
             hints=hints, solution=solution, rubric="3 т. за верни преходи и нормален вид; частични точки за верните редове.")


# ------------------------------------------------------------------ 1. adding and subtracting

AS, AS_OUT = "C.add-subtract", "Събира и изважда многочлени"


def s_add_mc(rng, key):
    p, qq = quad(rng), quad(rng)
    r = p + qq
    if len(r.t) < 3:
        raise ValueError
    wrongs = [((p - qq).fmt(), "SIGN", "Това е разликата, а търсим сбора."),
              ((r + P.mono(2 * qq.t.get((("x", 1),), 0), x=1) * -1).fmt(), "SIGN", "Провери знака на члена с x от втория многочлен."),
              ((r - P.const(2 * qq.t.get((), 0))).fmt(), "SIGN", "Провери знака на свободния член от втората скоба.")]
    return mc(rng, key, AS, AS_OUT, "FOUNDATIONAL", "LIKE_TERMS", f"({p.fmt()}) + ({qq.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Плюсът пред скобата не променя знаците в нея.", "Групирай x² с x², x с x и числата с числата.", "Събери коефициентите на подобните членове."],
              f"({p.fmt()}) + ({qq.fmt()}) = {p.fmt()} + {qq.fmt()} = {r.fmt()}.", "C", expect=r.fmt(False))


def s_opposite_mc(rng, key):
    p = P.mono(nz(rng, -7, 7), x=2) + P.mono(nz(rng, -7, 7), x=1) + P.const(nz(rng, -9, 9))
    po = p.ordered()
    wrongs = [((-P({po[0][0]: po[0][1]}) + P({k: v for k, v in po[1:]})).fmt(), "SIGN", "Сменя се знакът на всеки член, а не само на първия."),
              ((p - P({po[-1][0]: 2 * po[-1][1]})).fmt(), "SIGN", "Сменя се знакът на всеки член — тук е сменен само свободният член."),
              (p.fmt(), "REASONING", "Това е същият многочлен. Противоположният има обратни знаци на всички членове.")]
    return mc(rng, key, AS, "Намира противоположен многочлен", "FOUNDATIONAL", "SIGN",
              f"Кой е противоположният многочлен на {p.fmt()}?", (-p).fmt(), wrongs,
              ["Противоположният многочлен е −(…).", "Минусът сменя знака на всеки член.", "Сборът на многочлена и противоположния му е 0."],
              f"−({p.fmt()}) = {(-p).fmt()}: сменяме знака на всеки член.", "C", expect=(-p).fmt(False))


def s_sub_xy_mc(rng, key):
    p, qq = xy_poly(rng), xy_poly(rng)
    r = p - qq
    if len(r.t) < 2:
        raise ValueError
    wrongs = [(only_first_sign(p, qq).fmt(), "BRACKETS", "Минусът пред скобата сменя знака на всеки член в нея, не само на първия."),
              ((p + qq).fmt(), "SIGN", "Това е сборът, а търсим разликата."),
              ((qq - p).fmt(), "REASONING", "Изваждаме втория многочлен от първия, а не обратно.")]
    return mc(rng, key, AS, AS_OUT, "INTERMEDIATE", "BRACKETS", f"({p.fmt()}) − ({qq.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Разкрий втората скоба, като смениш всички знаци.", "Подобни са само членовете с еднаква буквена част: x², xy, y².", "Приведи подобните."],
              f"({p.fmt()}) − ({qq.fmt()}) = {p.fmt()} + {(-qq).fmt()} = {r.fmt()}.", "C", expect=r.fmt(False))


def s_add_expr(rng, key, pts=2):
    p, qq = quad(rng), quad(rng)
    r = p + qq
    return q(key, AS, AS_OUT, "EXPRESSION", "FOUNDATIONAL", 60, pts, "LIKE_TERMS",
             f"Събери и запиши в нормален вид: ({p.fmt()}) + ({qq.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (p - qq).fmt(False), "misconception": "SIGN", "explanation": "Това е разликата. При събиране знаците във втората скоба се запазват."}],
             hints=["Махни скобите — пред тях има плюс.", "Групирай подобните членове.", "Събери коефициентите им."],
             solution=f"({p.fmt()}) + ({qq.fmt()}) = {r.fmt()}.")


def s_sub_expr(rng, key, pts=2):
    p, qq = quad(rng), quad(rng)
    r = p - qq
    return q(key, AS, AS_OUT, "EXPRESSION", "INTERMEDIATE", 75, pts, "BRACKETS",
             f"Извади и запиши в нормален вид: ({p.fmt()}) − ({qq.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": only_first_sign(p, qq).fmt(False), "misconception": "BRACKETS", "explanation": "Минусът пред скобата сменя знака на всеки член в нея."},
                          {"match": (p + qq).fmt(False), "misconception": "SIGN", "explanation": "Това е сборът, а търсим разликата."}],
             hints=["Минусът пред втората скоба сменя всички знаци в нея.", f"−({qq.fmt()}) = {(-qq).fmt()}.", "После приведи подобните."],
             solution=f"({p.fmt()}) − ({qq.fmt()}) = {p.fmt()} + {(-qq).fmt()} = {r.fmt()}.")


def s_missing(rng, key, pts=2):
    p, r = quad(rng), quad(rng)
    m = r - p
    if len(m.t) < 2:
        raise ValueError
    return q(key, AS, "Намира неизвестен събираем многочлен", "EXPRESSION", "ADVANCED", 90, pts, "REASONING",
             f"Кой многочлен трябва да прибавим към {p.fmt()}, за да получим {r.fmt()}?", "C", answer=m.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (p - r).fmt(False), "misconception": "SIGN", "explanation": "Търсим M = (резултат) − (даден), а не обратно."},
                          {"match": (p + r).fmt(False), "misconception": "REASONING", "explanation": "Неизвестният събираем се намира чрез изваждане."}],
             hints=["Ако A + M = B, то M = B − A.", f"M = ({r.fmt()}) − ({p.fmt()}).", "Смени знаците във втората скоба и приведи."],
             solution=f"M = ({r.fmt()}) − ({p.fmt()}) = {m.fmt()}. Проверка: {p.fmt()} + ({m.fmt()}) = {r.fmt()}.")


def s_steps(rng, key):
    p, qq, r = quad(rng), quad(rng), lin(nz(rng, -6, 6), nz(rng, -9, 9))
    res = p - qq + r
    wrong = only_first_sign(p, qq) + r
    return steps_item(key, AS, AS_OUT, "BRACKETS", f"({p.fmt()}) − ({qq.fmt()}) + ({r.fmt()})",
                      f"({p.fmt(False)})-({qq.fmt(False)})+({r.fmt(False)})", res, wrong, "Минусът пред втората скоба сменя знака на всеки член в нея.",
                      ["Разкрий скобите: пред втората има минус.", "Подреди подобните членове един до друг.", "Приведи ги и запиши в нормален вид."],
                      f"= {p.fmt()} + {(-qq).fmt()} + {r.fmt()} = {res.fmt()}.")


def s_structured(rng, key):
    A, B = quad(rng), quad(rng)
    v = nz(rng, -3, 3)
    d = A - B
    return q(key, AS, AS_OUT, "STRUCTURED", "ADVANCED", 240, 3, "BRACKETS", f"Дадени са A = {A.fmt()} и B = {B.fmt()}.", "C",
             part_prompts=[{"id": "a", "label": "а) A + B в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) A − B в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A − B при x = {val(v)}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": (A + B).fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "EXPRESSION", "answer": d.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(d.eval(x=v), False), "points": 1}],
             distractors=[{"match": only_first_sign(A, B).fmt(False), "misconception": "BRACKETS", "explanation": "При A − B се сменя знакът на всеки член на B."}],
             hints=["За A + B махни скобите и приведи.", "За A − B смени знаците на всички членове на B.", "За в) замести в опростения израз A − B."],
             solution=f"а) A + B = {(A + B).fmt()}; б) A − B = {d.fmt()}; в) при x = {val(v)}: {val(d.eval(x=v))}.",
             rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ 2. polynomial × monomial

MM, MM_OUT = "C.multiply-monomial", "Умножава многочлен с едночлен"


def m_mc(rng, key):
    m = P.mono(nz(rng, -5, 5, (0, 1)), x=1)
    p = quad(rng)
    r = m * p
    po = p.ordered()
    flip_last = m * (p - P({po[-1][0]: 2 * po[-1][1]}))
    no_power = P.const(m.t[(("x", 1),)]) * p
    wrongs = [(first_only(m, p).fmt(), "BRACKETS", "Едночленът се умножава по всеки член в скобата, не само по първия."),
              (flip_last.fmt(), "SIGN", f"Провери знака на {(m * P({po[-1][0]: po[-1][1]})).fmt()}: умножаваш {m.fmt()} по {P({po[-1][0]: po[-1][1]}).fmt()}."),
              (no_power.fmt(), "TECHNICAL", "Умножени са само числата — забравен е x. Всеки член се умножава и по x, а показателите се събират: x·x² = x³.")]
    return mc(rng, key, MM, MM_OUT, "INTERMEDIATE", "BRACKETS", f"{m.fmt()}({p.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Умножи едночлена по всеки член в скобата.", "Знак по знак, число по число, x по x.", "x·x² = x³, x·x = x²."],
              f"{m.fmt()}({p.fmt()}) = {r.fmt()}.", "C", expect=r.fmt(False))


def m_xy_mc(rng, key):
    k = nz(rng, -4, 4, (0, 1))
    m = P.mono(k, x=1, y=1)
    p = P.mono(nz(rng, 1, 5), x=1) + P.mono(nz(rng, -5, 5), y=1) + P.const(nz(rng, -6, 6))
    r = m * p
    wrongs = [(first_only(m, p).fmt(), "BRACKETS", "Едночленът се умножава по всеки член в скобата."),
              ((r - 2 * m * P.const(p.t[()])).fmt(), "SIGN", "Провери знака при умножението по свободния член."),
              ((P.mono(k, x=1) * p).fmt(), "TECHNICAL", f"Едночленът е {m.fmt()} — всеки член се умножава и по y.")]
    return mc(rng, key, MM, MM_OUT, "INTERMEDIATE", "BRACKETS", f"{m.fmt()}({p.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Умножи едночлена по всеки член.", "xy·x = x²y, xy·y = xy².", "Не забравяй знаците."],
              f"{m.fmt()}({p.fmt()}) = {r.fmt()}.", "C", expect=r.fmt(False))


def m_simplify_mc(rng, key):
    a, b = nz(rng, 1, 9), nz(rng, 1, 9)
    k = nz(rng, 2, 5)
    expr = f"x(x + {a}) − x(x − {b})"
    r = X * lin(1, a) - X * lin(1, -b)
    wrongs = [(P.mono(a - b, x=1).fmt() if a != b else P.mono(2 * a, x=2).fmt(), "BRACKETS", f"−x(x − {b}) = −x² + {b}x: минусът сменя и знака на −{b}x."),
              ((P.mono(2, x=2) + r).fmt(), "BRACKETS", "x² − x² = 0 — квадратите се унищожават."),
              (P.mono(a + b + k, x=1).fmt() if a + b + k != a + b else "0", "TECHNICAL", f"Провери: {a} + {b} = {a + b}.")]
    return mc(rng, key, MM, "Опростява изрази с произведения", "INTERMEDIATE", "BRACKETS", f"Опрости: {expr}", r.fmt(), wrongs,
              ["Разкрий двете скоби поотделно.", f"x(x + {a}) = x² + {a}x, x(x − {b}) = x² − {b}x.", "Минусът пред второто произведение сменя знаците."],
              f"{expr} = x² + {a}x − x² + {b}x = {r.fmt()}.", "C", expect=r.fmt(False))


def m_xy_expr(rng, key, pts=2):
    m = P.mono(nz(rng, -4, 4, (0,)) or 2, a=1, b=1)
    p = P.mono(nz(rng, 1, 5), a=1) + P.mono(nz(rng, -5, 5), b=1) + P.const(nz(rng, -6, 6))
    r = m * p
    return q(key, MM, MM_OUT, "EXPRESSION", "INTERMEDIATE", 75, pts, "BRACKETS",
             f"Умножи и запиши в нормален вид: {m.fmt()}({p.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": first_only(m, p).fmt(False), "misconception": "BRACKETS", "explanation": "Едночленът се умножава по всеки член в скобата."}],
             hints=["Умножи едночлена по всеки член.", "ab·a = a²b, ab·b = ab².", "Пази знаците."],
             solution=f"{m.fmt()}({p.fmt()}) = {r.fmt()}.")


def m_two_products(rng, key, pts=2):
    a, b, c, d = nz(rng, 2, 6), nz(rng, 1, 9), nz(rng, 2, 6), nz(rng, 1, 9)
    r = a * lin(1, b) - c * lin(1, -d)
    if len(r.t) < 1:
        raise ValueError
    wrong = a * lin(1, b) - c * lin(1, d)
    return q(key, MM, "Опростява изрази с произведения", "EXPRESSION", "INTERMEDIATE", 75, pts, "SIGN",
             f"Опрости: {a}(x + {b}) − {c}(x − {d})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": wrong.fmt(False), "misconception": "SIGN", "explanation": f"−{c}·(−{d}) = +{c * d}: минус по минус дава плюс."}],
             hints=["Разкрий всяка скоба поотделно.", f"−{c}(x − {d}) = −{c}x + {c * d}.", "Приведи подобните."],
             solution=f"{a}(x + {b}) − {c}(x − {d}) = {a}x + {a * b} − {c}x + {c * d} = {r.fmt()}.")


def m_steps(rng, key):
    k, a, m, b = nz(rng, 2, 5), nz(rng, -7, 7), nz(rng, 2, 5), nz(rng, -7, 7)
    if k == m:
        raise ValueError
    A, B = P.mono(k, x=1), P.mono(m, x=1)
    res = A * lin(1, a) - B * lin(1, b)
    wrong = A * lin(1, a) - B * lin(1, -b)
    return steps_item(key, MM, "Опростява изрази с произведения", "BRACKETS", f"{k}x(x {'+' if a > 0 else '−'} {abs(a)}) − {m}x(x {'+' if b > 0 else '−'} {abs(b)})",
                      f"{k}x(x+({a}))-{m}x(x+({b}))", res, wrong, "Минусът пред второто произведение сменя знака и на втория член.",
                      ["Умножи всеки едночлен по скобата си.", "Внимавай с минуса пред второто произведение.", "Приведи x² с x² и x с x."],
                      f"= {(A * lin(1, a)).fmt()} − ({(B * lin(1, b)).fmt()}) = {res.fmt()}.")


def m_structured(rng, key):
    k, a, b = nz(rng, 2, 5), nz(rng, 2, 4), nz(rng, 1, 9)
    w, l = P.mono(k, x=1), lin(a, b)
    area, per = w * l, 2 * (w + l)
    v = nz(rng, 1, 4)
    return q(key, MM, "Прилага умножение в геометрична задача", "STRUCTURED", "ADVANCED", 240, 3, "BRACKETS",
             f"Правоъгълник има страни {w.fmt()} см и ({l.fmt()}) см.", "C",
             part_prompts=[{"id": "a", "label": "а) Лицето S в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Периметърът P в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "c", "label": f"в) Лицето при x = {v} (в см²)", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": area.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "EXPRESSION", "answer": per.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(area.eval(x=v), False), "points": 1}],
             distractors=[{"match": first_only(w, l).fmt(False), "misconception": "BRACKETS", "explanation": "Едночленът се умножава и по свободния член."}],
             hints=["S = дължина · ширина.", "P = 2·(дължина + ширина).", "За в) замести x в лицето."],
             solution=f"а) S = {w.fmt()}({l.fmt()}) = {area.fmt()}; б) P = 2({w.fmt()} + {l.fmt()}) = {per.fmt()}; в) S = {val(area.eval(x=v))} см².",
             rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ 3. polynomial × polynomial

PP, PP_OUT = "C.multiply", "Умножава многочлени"


def p_trinomial_mc(rng, key):
    a = nz(rng, -5, 5)
    t = P.mono(1, x=2) + P.mono(nz(rng, -5, 5), x=1) + P.const(nz(rng, -6, 6))
    r = lin(1, a) * t
    to = t.ordered()
    partial = X * t + P.const(a) * P({to[0][0]: to[0][1]})
    sign = X * t - P.const(a) * t
    wrongs = [(partial.fmt(), "BRACKETS", f"{val(a)} също се умножава по всеки член на втората скоба."),
              (sign.fmt(), "SIGN", f"Вторият член на първата скоба е {val(a)} — провери знака."),
              ((X * t).fmt(), "BRACKETS", f"Липсва произведението на {val(a)} по втората скоба.")]
    return mc(rng, key, PP, PP_OUT, "INTERMEDIATE", "BRACKETS", f"({lin(1, a).fmt()})({t.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Всеки член от първата скоба по всеки член от втората.", "Ще получиш 2·3 = 6 произведения.", "Приведи подобните."],
              f"({lin(1, a).fmt()})({t.fmt()}) = {(X * t).fmt()} + ({(P.const(a) * t).fmt()}) = {r.fmt()}.", "C", expect=r.fmt(False))


def p_xy_mc(rng, key):
    k, m = nz(rng, 1, 5), nz(rng, 1, 5)
    A = P.mono(1, x=1) + P.mono(k, y=1)
    B = P.mono(1, x=1) - P.mono(m, y=1)
    r = A * B
    no_mid = P.mono(1, x=2) - P.mono(k * m, y=2)
    wrongs = [(no_mid.fmt() if no_mid != r else (r + P.mono(1, x=1, y=1)).fmt(), "BRACKETS", "Липсват средните членове xy: всеки член по всеки."),
              ((P.mono(1, x=2) + P.mono(k - m, x=1, y=1) + P.mono(k * m, y=2)).fmt(), "SIGN", f"{k}y·(−{m}y) = −{k * m}y²."),
              ((P.mono(1, x=2) + P.mono(k + m, x=1, y=1) - P.mono(k * m, y=2)).fmt(), "SIGN", f"Средните членове са {k}xy и −{m}xy.")]
    return mc(rng, key, PP, PP_OUT, "INTERMEDIATE", "BRACKETS", f"({A.fmt()})({B.fmt()}) е равно на:", r.fmt(), wrongs,
              ["Всеки член по всеки член: 4 произведения.", "x·x, x·(−my), ky·x, ky·(−my).", "Приведи членовете с xy."],
              f"({A.fmt()})({B.fmt()}) = x² − {m}xy + {k}xy − {k * m}y² = {r.fmt()}.", "C", expect=r.fmt(False))


def p_coef_mc(rng, key):
    a, b = nz(rng, -7, 7), nz(rng, -7, 7)
    if a + b == 0 or a * b == a + b:
        raise ValueError
    wrongs = [(val(a * b), "BRACKETS", f"{val(a * b)} е свободният член (произведението {paren(a)}·{paren(b)})."),
              (val(a - b) if a - b != a + b else val(a + b + 1), "SIGN", "Коефициентът пред x е сборът на двете числа."),
              ("1", "REASONING", "1 е коефициентът пред x², а търсим коефициента пред x.")]
    return mc(rng, key, PP, "Намира коефициент в произведение", "INTERMEDIATE", "BRACKETS",
              f"Какъв е коефициентът пред x в произведението ({lin(1, a).fmt()})({lin(1, b).fmt()})?", val(a + b), wrongs,
              ["Членовете с x идват от x·b и a·x.", f"x·{paren(b)} + {paren(a)}·x.", "Събери коефициентите им."],
              f"({lin(1, a).fmt()})({lin(1, b).fmt()}) = {(lin(1, a) * lin(1, b)).fmt()}; коефициентът пред x е {val(a + b)}.", "C", expect=val(a + b, False))


def p_expr(rng, key, pts=2):
    a, b, c, d = nz(rng, 1, 4), nz(rng, -7, 7), nz(rng, 1, 3), nz(rng, -7, 7)
    if a * d + b * c == 0:
        raise ValueError
    p1, p2 = lin(a, b), lin(c, d)
    r = p1 * p2
    return q(key, PP, PP_OUT, "EXPRESSION", "INTERMEDIATE", 90, pts, "BRACKETS",
             f"Умножи и запиши в нормален вид: ({p1.fmt()})({p2.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (P.mono(a * c, x=2) + P.const(b * d)).fmt(False), "misconception": "BRACKETS", "explanation": "Липсват средните членове: всеки член по всеки."}],
             hints=["Всеки член от първата скоба по всеки от втората.", f"{P.mono(a, x=1).fmt()}·{P.mono(c, x=1).fmt()}, {P.mono(a, x=1).fmt()}·{paren(d)}, {paren(b)}·{P.mono(c, x=1).fmt()}, {paren(b)}·{paren(d)}.", "Приведи средните членове."],
             solution=f"({p1.fmt()})({p2.fmt()}) = {P.mono(a * c, x=2).fmt()} {'+' if a * d >= 0 else '−'} {abs(a * d)}x {'+' if b * c >= 0 else '−'} {abs(b * c)}x {'+' if b * d >= 0 else '−'} {abs(b * d)} = {r.fmt()}.")


def p_tri_expr(rng, key, pts=2):
    a = nz(rng, -5, 5)
    t = P.mono(1, x=2) + P.mono(nz(rng, -5, 5), x=1) + P.const(nz(rng, -6, 6))
    r = lin(1, a) * t
    return q(key, PP, PP_OUT, "EXPRESSION", "ADVANCED", 120, pts, "BRACKETS",
             f"Умножи и запиши в нормален вид: ({lin(1, a).fmt()})({t.fmt()})", "C", answer=r.fmt(False), form="NORMAL_FORM",
             distractors=[{"match": (X * t).fmt(False), "misconception": "BRACKETS", "explanation": f"Липсва произведението на {val(a)} по втората скоба."}],
             hints=["x по всеки член, после числото по всеки член.", "Ще получиш 6 произведения.", "Приведи x² с x² и x с x."],
             solution=f"({lin(1, a).fmt()})({t.fmt()}) = {(X * t).fmt()} + ({(P.const(a) * t).fmt()}) = {r.fmt()}.")


def p_equation(rng, key, pts=2):
    a, b = nz(rng, -6, 6), nz(rng, -6, 6)
    if a + b == 0:
        raise ValueError
    x0 = nz(rng, -5, 6)
    c = (a + b) * x0 + a * b
    return q(key, PP, "Решава уравнение след умножение на многочлени", "NUMERIC", "ADVANCED", 120, pts, "BRACKETS",
             f"Реши уравнението ({lin(1, a).fmt()})({lin(1, b).fmt()}) − x² = {val(c)}. Запиши x.", "C", answer=val(x0, False),
             distractors=[{"match": val(-x0, False), "misconception": "SIGN", "explanation": "Провери знака при пренасянето на свободния член."}] if x0 != 0 else [],
             hints=["Умножи скобите.", f"x² се унищожава: остава {(lin(1, a) * lin(1, b) - X * X).fmt()} = {val(c)}.", "Пренеси числото и раздели на коефициента пред x."],
             solution=f"({lin(1, a).fmt()})({lin(1, b).fmt()}) − x² = {(lin(1, a) * lin(1, b) - X * X).fmt()}, значи {(lin(1, a) * lin(1, b) - X * X).fmt()} = {val(c)}, "
                      f"{P.mono(a + b, x=1).fmt()} = {val(c - a * b)}, x = {val(x0)}.")


def p_steps(rng, key):
    a, b, c, d = nz(rng, -6, 6), nz(rng, -6, 6), nz(rng, -6, 6), nz(rng, -6, 6)
    res = lin(1, a) * lin(1, b) - lin(1, c) * lin(1, d)
    if len(res.t) < 1 or res == P():
        raise ValueError
    wrong = lin(1, a) * lin(1, b) - X * X - P.mono(c + d, x=1) + P.const(c * d)
    return steps_item(key, PP, PP_OUT, "BRACKETS", f"({lin(1, a).fmt()})({lin(1, b).fmt()}) − ({lin(1, c).fmt()})({lin(1, d).fmt()})",
                      f"(x+({a}))(x+({b}))-(x+({c}))(x+({d}))", res, wrong, "Минусът пред второто произведение сменя знака на всеки негов член — и на свободния.",
                      ["Умножи всяко произведение поотделно — пиши резултата в скоби.", "Минусът сменя знаците на целия втори резултат.", "x² − x² = 0; приведи останалото."],
                      f"= ({(lin(1, a) * lin(1, b)).fmt()}) − ({(lin(1, c) * lin(1, d)).fmt()}) = {res.fmt()}.")


def p_structured(rng, key):
    a, b = nz(rng, -6, 6), nz(rng, -6, 6)
    A = lin(2, a) * lin(1, b)
    v = nz(rng, -3, 3)
    return q(key, PP, PP_OUT, "STRUCTURED", "ADVANCED", 240, 3, "BRACKETS", f"Даден е изразът A = ({lin(2, a).fmt()})({lin(1, b).fmt()}).", "C",
             part_prompts=[{"id": "a", "label": "а) Запиши A в нормален вид", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Коефициентът пред x", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": f"в) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": A.fmt(False), "form": "NORMAL_FORM", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(A.t.get((("x", 1),), 0), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(A.eval(x=v), False), "points": 1}],
             distractors=[{"match": (P.mono(2, x=2) + P.const(a * b)).fmt(False), "misconception": "BRACKETS", "explanation": "Липсват средните членове."}],
             hints=["Всеки член по всеки член.", "Средните членове са 2x·b и a·x.", "За в) можеш да заместиш в скобите директно."],
             solution=f"а) A = {A.fmt()}; б) {val(A.t.get((('x', 1),), 0))}; в) A({val(v)}) = {paren(2 * v + a)}·{paren(v + b)} = {val(A.eval(x=v))}.",
             rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ tests

POOLS = {
    "AS": ([s_add_mc, g.c_subtract, s_opposite_mc, s_sub_xy_mc], [s_add_expr, s_sub_expr, s_missing], [s_steps, s_structured]),
    "MM": ([m_mc, m_xy_mc, m_simplify_mc], [g.c_mono_times_poly, m_xy_expr, m_two_products], [m_steps, m_structured]),
    "PP": ([g.c_binomial_product, p_trinomial_mc, p_xy_mc, p_coef_mc], [p_expr, p_tri_expr, p_equation], [p_steps, p_structured]),
}
TOPIC = "Действия с многочлени"
EXAM = "Подготовка за контролно"
TESTS = [
    ("OPS-1A", ["AS"], f"{TOPIC} · Събиране и изваждане — тренировка 1", "ALLOWED"),
    ("OPS-1B", ["AS"], f"{TOPIC} · Събиране и изваждане — тренировка 2", "ALLOWED"),
    ("OPS-2A", ["MM"], f"{TOPIC} · Многочлен по едночлен — тренировка 1", "ALLOWED"),
    ("OPS-2B", ["MM"], f"{TOPIC} · Многочлен по едночлен — тренировка 2", "ALLOWED"),
    ("OPS-3A", ["PP"], f"{TOPIC} · Многочлен по многочлен — тренировка 1", "ALLOWED"),
    ("OPS-3B", ["PP"], f"{TOPIC} · Многочлен по многочлен — тренировка 2", "ALLOWED"),
    ("OPS-K1", ["AS", "MM", "PP"], f"{EXAM} · Действия с многочлени — вариант 1", "NONE"),
    ("OPS-K2", ["AS", "MM", "PP"], f"{EXAM} · Действия с многочлени — вариант 2", "NONE"),
    ("OPS-K3", ["AS", "MM", "PP"], f"{EXAM} · Действия с многочлени — вариант 3", "NONE"),
    ("OPS-K4", ["AS", "MM", "PP"], f"{EXAM} · Действия с многочлени — вариант 4", "NONE"),
]


def build(existing_prompts):
    rng = random.Random(20261008)
    seen = set(existing_prompts)
    questions, tests = [], []
    for key, topics, title, hints in TESTS:
        items = []
        for slot, n in enumerate((12, 5, 3)):
            pools = [(t, POOLS[t][slot]) for t in topics]
            for i in range(n):
                t, pool = pools[i % len(pools)]
                made = None
                for attempt in range(60):
                    fn = pool[(i // len(pools) + attempt) % len(pool)]
                    k = f"{key}-{len(items) + 1:02d}"
                    try:
                        qd = fn(rng, k)
                    except (ValueError, ZeroDivisionError, KeyError):
                        continue
                    text = qd["draft"]["prompt"]["text"]
                    if text in seen:
                        continue
                    made = qd
                    break
                if not made:
                    raise RuntimeError(f"cannot build {key} item {len(items) + 1}")
                seen.add(made["draft"]["prompt"]["text"])
                items.append(made)
        questions += items
        tests.append({"testKey": key, "title": title, "kind": "THEMATIC", "path": "C", "blueprint": "Тематичен тест (20)",
                      "timeLimitMin": 40, "hintPolicy": hints, "reviewMoment": "AFTER_SUBMIT", "questionKeys": [x["key"] for x in items]})
    return questions, tests
