# Templates for factorisation at "excellent" level (МОН, 7 клас, т. 1.16–1.20): formulas (C12),
# grouping (C14), combined methods (C15) and applications. Every factorisation is built from its
# factors, so the answer is exact; wrong options are dropped if they happen to equal the answer.
# validate_content.ts re-checks keys, "fully factorised" and every option with the app's engine.
import math

import gen_tests as g

P, nz, lin, val, paren, mc, q = g.P, g.nz, g.lin, g.val, g.paren, g.mc, g.q
X, Y = P.mono(1, x=1), P.mono(1, y=1)
ONE = P.const(1)

FF, FF_OUT = "C.factor-formulas", "Разлага чрез формулите за съкратено умножение"
GR, GR_OUT = "C.grouping", "Разлага чрез групиране"
CB, CB_OUT = "C.factor-combined", "Разлага напълно, като комбинира методи"
AP, AP_OUT = "C.applications", "Използва разлагане за пресмятане и доказване"


def num(n):
    return f"{n:,}".replace(",", " ")


def _parts(parts):
    for p in parts:
        yield (p if isinstance(p, tuple) else (p, 1))


def fac(parts, pretty=True):
    """Product of factors as text: monomials stay bare, sums go in brackets."""
    out = []
    for p, e in _parts(parts):
        s = p.fmt(pretty)
        if len(p.t) > 1:
            s = f"({s})"
        if e > 1:
            s += g.sup(e) if pretty else f"^{e}"
        out.append(s)
    return "".join(out)


def prod(parts):
    r = ONE
    for p, e in _parts(parts):
        r = r * p ** e
    return r


def content(p):
    """gcd of the integer coefficients of p."""
    gg = 0
    for c in p.t.values():
        gg = math.gcd(gg, int(c))
    return gg


def wrong_list(target, cands):
    out, seen = [], set()
    for parts, misc, why in cands:
        t = fac(parts)
        if t in seen or prod(parts) == target:
            continue
        seen.add(t)
        out.append((t, misc, why))
    return out


def mc_factor(rng, key, skill, outcome, diff, full, parts, cands, hints, way, prefix="Разложи напълно на множители", shown=None):
    assert prod(parts) == full, key
    good = fac(parts)
    return mc(rng, key, skill, outcome, diff, "FACTORISATION", f"{prefix}: {shown or full.fmt()}", good, wrong_list(full, cands), hints,
              f"{shown or full.fmt()} = {way}{good}.", "C", secs=120, expect=fac(parts, False))


def expr_factor(key, skill, outcome, diff, pts, full, parts, cands, hints, way, prefix="Разложи напълно на множители", shown=None, secs=150):
    assert prod(parts) == full, key
    ds = [{"match": fac(p, False), "misconception": m, "explanation": w} for p, m, w in cands if prod(p) != full]
    if not ds:
        raise ValueError
    return q(key, skill, outcome, "EXPRESSION", diff, secs, pts, "FACTORISATION", f"{prefix}: {shown or full.fmt()}", "C",
             answer=fac(parts, False), form="FACTORISED", distractors=ds[:2], hints=hints, solution=f"{shown or full.fmt()} = {way}{fac(parts)}.")


def steps_factor(key, skill, outcome, full, parts, wrong, wrong_why, hints, way, shown=None):
    assert prod(parts) == full, key
    start = full.fmt(False)
    return q(key, skill, outcome, "STEPS", "ADVANCED", 210, 3, "FACTORISATION",
             f"Разложи напълно стъпка по стъпка: {shown or full.fmt()}", "C", answer=fac(parts, False), form="FACTORISED",
             parts=[{"id": "start", "type": "EXPRESSION", "answer": start, "points": 0}], start=start,
             distractors=[{"match": fac(wrong, False), "misconception": "FACTORISATION", "explanation": wrong_why}],
             hints=hints, solution=f"{shown or full.fmt()} = {way}{fac(parts)}.",
             rubric="3 т.: 1 т. за първата вярна стъпка, 1 т. за вярно приложен метод, 1 т. за пълно разлагане.")


def sgn_term(c, body):
    """' + 3(…)' / ' − 3(…)' after a first group."""
    return f" − {abs(c)}{body}" if c < 0 else f" + {c}{body}"


def br(p):
    return f"({p.fmt()})"


# ================================================================== C12: formulas (excellent level)

def fm_square2_mc(rng, key):
    m, n = rng.sample(range(1, 6), 2)
    if math.gcd(m, n) != 1:
        raise ValueError
    s = rng.choice([1, -1])
    A, B = P.mono(m, x=1), P.mono(n, y=1)
    base = A + s * B
    full = base ** 2
    cands = [([(A - s * B, 2)], "SIGN", "Знакът в скобата е като знака на средния член."),
             ([A - B, A + B], "FORMULA_APPLICATION", "Разлика на квадрати няма среден член с xy."),
             ([(P.mono(n, x=1) + s * P.mono(m, y=1), 2)], "FORMULA_APPLICATION", f"Коренът от {P.mono(m * m, x=2).fmt()} е {A.fmt()}, а от {P.mono(n * n, y=2).fmt()} — {B.fmt()}."),
             ([(A + s * P.mono(2 * n, y=1), 2)], "FORMULA_APPLICATION", f"Вторият член в скобата е {B.fmt()}: 2·{A.fmt()}·{B.fmt()} = {P.mono(2 * m * n, x=1, y=1).fmt()}.")]
    return mc_factor(rng, key, FF, FF_OUT, "ADVANCED", full, [(base, 2)], cands,
                     ["Провери дали първият и последният член са квадрати.", f"{P.mono(m * m, x=2).fmt()} = ({A.fmt()})², {P.mono(n * n, y=2).fmt()} = ({B.fmt()})².",
                      f"Средният член трябва да е ±2·{A.fmt()}·{B.fmt()}."], f"({A.fmt()})² {'+' if s > 0 else '−'} 2·{A.fmt()}·{B.fmt()} + ({B.fmt()})² = ")


def fm_ds_mono_mc(rng, key):
    a, b = rng.sample(range(1, 8), 2)
    if math.gcd(a, b) != 1:
        raise ValueError
    p, r = rng.choice([(1, 1), (2, 1), (2, 2), (3, 1), (3, 2)])
    A, B = P.mono(a, x=p), P.mono(b, y=r)
    full = A ** 2 - B ** 2
    cands = [([(A - B, 2)], "FORMULA_APPLICATION", "(A − B)² ≠ A² − B² — има и среден член."),
             ([A - B, A - B], "SIGN", "Разликата на квадрати е (A − B)(A + B) — множителите са с различни знаци."),
             ([P.mono(a, x=2 * p) - P.mono(b, y=r), P.mono(a, x=2 * p) + P.mono(b, y=r)], "FORMULA_APPLICATION", f"Коренът от {P.mono(a * a, x=2 * p).fmt()} е {A.fmt()} — показателят се дели на 2.")]
    cands[1] = ([(A + B, 2)], "FORMULA_APPLICATION", "(A + B)² = A² + 2AB + B² — това не е разлика на квадрати.")
    if a != b:
        cands.append(([P.mono(b, x=p) - P.mono(a, y=r), P.mono(b, x=p) + P.mono(a, y=r)], "FORMULA_APPLICATION", f"Коренът от {P.mono(a * a, x=2 * p).fmt()} е {A.fmt()}."))
    return mc_factor(rng, key, FF, FF_OUT, "ADVANCED", full, [A - B, A + B], cands,
                     ["Това е разлика на два квадрата.", f"{P.mono(a * a, x=2 * p).fmt()} = ({A.fmt()})², {P.mono(b * b, y=2 * r).fmt()} = ({B.fmt()})².", "a² − b² = (a − b)(a + b)."],
                     f"({A.fmt()})² − ({B.fmt()})² = ")


def _cube_parts(m, n, s, v="x"):
    A = P.mono(m, **{v: 1})
    return A + P.const(s * n), P.mono(m * m, **{v: 2}) - P.mono(s * m * n, **{v: 1}) + P.const(n * n)


def fm_cubes_mc(rng, key):
    m, n = rng.choice([(1, 2), (1, 3), (1, 4), (1, 5), (2, 1), (2, 3), (2, 5), (3, 1), (3, 2), (4, 1), (4, 3), (5, 1)])
    s = rng.choice([1, -1])
    lin_, sq_ = _cube_parts(m, n, s)
    full = lin_ * sq_
    A = P.mono(m, x=1)
    cands = [([lin_, P.mono(m * m, x=2) + P.mono(s * m * n, x=1) + P.const(n * n)], "SIGN", f"В непълния квадрат средният член е с обратния знак: {'−' if s > 0 else '+'}ab."),
             ([lin_, P.mono(m * m, x=2) - P.mono(2 * s * m * n, x=1) + P.const(n * n)], "FORMULA_APPLICATION", "Непълният квадрат има среден член ab, а не 2ab."),
             ([A - P.const(s * n), P.mono(m * m, x=2) + P.mono(s * m * n, x=1) + P.const(n * n)], "SIGN", f"Знакът в двучлена е като знака в {'сбора' if s > 0 else 'разликата'} на кубовете."),
             ([(lin_, 3)], "FORMULA_APPLICATION", "(a ± b)³ има четири члена — това не е a³ ± b³.")]
    word = "сбор" if s > 0 else "разлика"
    return mc_factor(rng, key, FF, FF_OUT, "ADVANCED", full, [lin_, sq_], cands,
                     [f"Това е {word} на кубове.", f"{full.fmt().split(' ')[0]} = ({A.fmt()})³, {n ** 3} = {n}³.",
                      "a³ + b³ = (a + b)(a² − ab + b²); a³ − b³ = (a − b)(a² + ab + b²)."], f"({A.fmt()})³ {'+' if s > 0 else '−'} {n}³ = ")


def fm_cubes_expr(rng, key, pts=2):
    m, n = rng.choice([(1, 2), (1, 3), (2, 1), (3, 1), (1, 4), (2, 3), (3, 2)])
    s = rng.choice([1, -1])
    A, B = P.mono(m, x=1), P.mono(n, y=1)
    lin_ = A + s * B
    sq_ = A * A - s * A * B + B * B
    full = lin_ * sq_
    return expr_factor(key, FF, FF_OUT, "ADVANCED", pts, full, [lin_, sq_],
                       [([lin_, A * A + s * A * B + B * B], "SIGN", "Средният член в непълния квадрат е с обратния знак."),
                        ([lin_, A * A - 2 * s * A * B + B * B], "FORMULA_APPLICATION", "В непълния квадрат е ab, не 2ab.")],
                       [f"Това е {'сбор' if s > 0 else 'разлика'} на кубове.", f"a = {A.fmt()}, b = {B.fmt()}.", "a³ ± b³ = (a ± b)(a² ∓ ab + b²)."],
                       f"({A.fmt()})³ {'+' if s > 0 else '−'} ({B.fmt()})³ = ")


def fm_ds_binom_expr(rng, key, pts=2):
    a, b = nz(rng, -6, 6), rng.randint(2, 9)
    if abs(a) == b:
        raise ValueError
    base = lin(1, a)
    full, shown = base ** 2 - P.const(b * b), f"({base.fmt()})² − {b * b}"
    parts = [lin(1, a - b), lin(1, a + b)]
    way = f"({base.fmt()} − {b})({base.fmt()} + {b}) = "
    cands = [([(lin(1, a - b), 2)], "FORMULA_APPLICATION", "Разлика на квадрати е произведение на разлика и сбор."),
             ([lin(1, a - b * b), lin(1, a + b * b)], "FORMULA_APPLICATION", f"{b * b} = {b}² — в скобите е {b}, не {b * b}.")]
    return expr_factor(key, FF, FF_OUT, "ADVANCED", pts, full, parts, cands,
                       ["Не разкривай квадрата — виж израза като A² − B².", f"A = {base.fmt()}, B = {b}.", "(A − B)(A + B), после опрости всяка скоба."],
                       way, shown=shown)


def fm_steps(rng, key):
    m, n = rng.choice([(2, 1), (3, 1), (1, 2), (1, 3), (2, 3), (3, 2), (4, 1), (1, 4)])
    s = rng.choice([1, -1])
    A, B = P.mono(m, a=1), P.const(n)
    lin_ = A + s * B
    sq_ = A * A - s * A * B + B * B
    full = lin_ * sq_
    return steps_factor(key, FF, FF_OUT, full, [lin_, sq_], [lin_, A * A + s * A * B + B * B], "Средният член в непълния квадрат е с обратния знак.",
                        [f"Запиши като ({A.fmt()})³ {'+' if s > 0 else '−'} {n}³.", "Приложи формулата за сбор/разлика на кубове.", "Провери знака на средния член в непълния квадрат."],
                        f"({A.fmt()})³ {'+' if s > 0 else '−'} {n}³ = ")


def fm_structured(rng, key):
    n = rng.randint(1, 4)
    v = rng.choice([10, 11, 12, 20, 21])
    full = X ** 3 - P.const(n ** 3)
    lin_, sq_ = X - P.const(n), X * X + P.mono(n, x=1) + P.const(n * n)
    return q(key, FF, "Разлага и прилага формула за разлика на кубове", "STRUCTURED", "ADVANCED", 270, 3, "FACTORISATION",
             f"Даден е изразът A = {full.fmt()}.", "C",
             part_prompts=[{"id": "a", "label": "а) Разложи A на множители", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {v}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Стойността на x, при която A = 0", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": fac([lin_, sq_], False), "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": str(v ** 3 - n ** 3), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(n), "points": 1}],
             distractors=[{"match": fac([lin_, X * X - P.mono(n, x=1) + P.const(n * n)], False), "misconception": "SIGN", "explanation": "При разлика на кубове непълният квадрат е a² + ab + b²."}],
             hints=["Разлика на кубове: a³ − b³ = (a − b)(a² + ab + b²).", f"a = x, b = {n}.", f"x² + {n}x + {n * n} е винаги положително, затова A = 0 само при x − {n} = 0."],
             solution=f"а) A = {fac([lin_, sq_])}; б) {v}³ − {n ** 3} = {num(v ** 3 - n ** 3)}; в) x = {n}.", rubric="По 1 т. за всяка вярна част.")


# ================================================================== C14: grouping

def _two_by_two(rng, allow_coef=True):
    """(m·x + a)(n·y + b) with both brackets free of a common factor."""
    m, n = (rng.choice([1, 1, 2, 3]), rng.choice([1, 1, 2, 3])) if allow_coef else (1, 1)
    a, b = nz(rng, -7, 7), nz(rng, -7, 7)
    if math.gcd(m, abs(a)) != 1 or math.gcd(n, abs(b)) != 1 or (m == n and abs(a) == abs(b)):
        raise ValueError
    return m, n, a, b


def gr_four_mc(rng, key):
    m, n, a, b = _two_by_two(rng)
    F1, F2 = P.mono(m, x=1) + P.const(a), P.mono(n, y=1) + P.const(b)
    full = F1 * F2
    cands = [([P.mono(m, x=1) + P.const(b), P.mono(n, y=1) + P.const(a)], "FACTORISATION", "Числата са разменени — умножи обратно и сравни."),
             ([P.mono(m, x=1) - P.const(a), F2], "SIGN", "Провери знака при изнасянето от втората група."),
             ([F1, P.mono(n, y=1) - P.const(b)], "SIGN", "Провери знака в общата скоба."),
             ([P.mono(m, x=1) + P.const(b), P.mono(n, y=1) - P.const(a)], "FACTORISATION", "Умножи обратно — получава се друг многочлен.")]
    way = f"{P.mono(m, x=1).fmt() if m > 1 else 'x'}{br(F2)}{sgn_term(a, br(F2))} = "
    return mc_factor(rng, key, GR, GR_OUT, "ADVANCED", full, [F1, F2], cands,
                     ["Групирай първите два и последните два члена.", f"От първата група изнеси {P.mono(m, x=1).fmt()}, от втората — {val(a)}.", f"Общата скоба е {br(F2)}."],
                     way, prefix="Разложи чрез групиране")


def gr_four_expr(rng, key, pts=2):
    m, n, a, b = _two_by_two(rng)
    F1, F2 = P.mono(m, a=1) + P.const(a), P.mono(n, b=1) + P.const(b)
    full = F1 * F2
    way = f"{P.mono(m, a=1).fmt()}{br(F2)}{sgn_term(a, br(F2))} = "
    return expr_factor(key, GR, GR_OUT, "ADVANCED", pts, full, [F1, F2],
                       [([P.mono(m, a=1) - P.const(a), F2], "SIGN", "При изнасяне на отрицателно число знаците в скобата се сменят."),
                        ([P.mono(m, a=1) + P.const(b), P.mono(n, b=1) + P.const(a)], "FACTORISATION", "Числата са разменени — умножи обратно.")],
                       ["Групирай членовете по двойки.", f"Изнеси {P.mono(m, a=1).fmt()} от първата двойка и {val(a)} от втората.", f"Изнеси общата скоба {br(F2)}."],
                       way, prefix="Разложи чрез групиране")


def _cubic_group(rng):
    a = nz(rng, -6, 6)
    b = rng.choice([1, 2, 3, 5, 6, 7, -2, -3, -5, -6, -7])
    return a, b, lin(1, a), X * X + P.const(b)


def gr_cubic_mc(rng, key):
    a, b, F1, F2 = _cubic_group(rng)
    full = F1 * F2
    cands = [([F1, X * X - P.const(b)], "SIGN", f"От втората група изнасяме {val(b)} — провери знака."),
             ([lin(1, -a), F2], "SIGN", "Провери знака в общата скоба."),
             ([lin(1, b), X * X + P.const(a)], "FACTORISATION", "Числата са разменени — умножи обратно."),
             ([X, F1, lin(1, b)], "FACTORISATION", "Умножи обратно — получава се друг многочлен.")]
    return mc_factor(rng, key, GR, GR_OUT, "ADVANCED", full, [F1, F2], cands,
                     ["Групирай първите два и последните два члена.", f"x²{br(F1)}{sgn_term(b, br(F1))}.", f"Изнеси {br(F1)}."],
                     f"x²{br(F1)}{sgn_term(b, br(F1))} = ", prefix="Разложи чрез групиране")


def gr_cubic_expr(rng, key, pts=2):
    a, b, F1, F2 = _cubic_group(rng)
    full = F1 * F2
    return expr_factor(key, GR, GR_OUT, "ADVANCED", pts, full, [F1, F2],
                       [([F1, X * X - P.const(b)], "SIGN", "Провери знака при изнасяне от втората група."),
                        ([lin(1, -a), F2], "SIGN", "Провери знака в общата скоба.")],
                       ["Групирай: (x³ + …x²) и (…x + …).", f"x²{br(F1)}{sgn_term(b, br(F1))}.", f"Изнеси общата скоба {br(F1)}."],
                       f"x²{br(F1)}{sgn_term(b, br(F1))} = ", prefix="Разложи чрез групиране")


def _split(rng):
    m = rng.choice([1, 1, 2, 3])
    p, r = nz(rng, -9, 9), nz(rng, -9, 9)
    if math.gcd(m, abs(p)) != 1 or (m == 1 and (p == r or p == -r)):
        raise ValueError
    F1, F2 = P.mono(m, x=1) + P.const(p), lin(1, r)
    full = F1 * F2
    if len(full.t) != 3 or (m > 1 and F1 * 1 == F2 * m):
        raise ValueError
    # split the middle term: m·x² + m·r·x + p·x + p·r
    sx = lambda c: f" {'−' if c < 0 else '+'} {'' if abs(c) == 1 else abs(c)}x"
    split = f"{P.mono(m, x=2).fmt()}{sx(m * r)}{sx(p)}{g.signed(p * r)}"
    way = f"{split} = {P.mono(m, x=1).fmt()}{br(F2)}{sgn_term(p, br(F2))} = "
    return m, p, r, F1, F2, full, way


def gr_split_mc(rng, key):
    m, p, r, F1, F2, full, way = _split(rng)
    cands = [([P.mono(m, x=1) - P.const(p), lin(1, -r)], "SIGN", "Умножи обратно — средният член е с друг знак."),
             ([P.mono(m, x=1) + P.const(r), lin(1, p)], "FACTORISATION", "Числата са разменени — умножи обратно."),
             ([P.mono(m, x=1) + P.const(p), lin(1, -r)], "SIGN", "Провери знака на свободния член."),
             ([P.mono(m, x=1) - P.const(p), F2], "SIGN", "Провери знака на свободния член."),
             ([P.mono(m, x=1) + P.const(p * r), X + P.const(1)], "FACTORISATION", "Умножи обратно — средният член не съвпада.")]
    return mc_factor(rng, key, GR, GR_OUT, "ADVANCED", full, [F1, F2], cands,
                     ["Раздели средния член на два члена.", f"Търси числа с произведение {m * p * r} и сбор {m * r + p}.", "След това групирай по двойки."], way)


def gr_split_expr(rng, key, pts=2):
    m, p, r, F1, F2, full, way = _split(rng)
    return expr_factor(key, GR, GR_OUT, "ADVANCED", pts, full, [F1, F2],
                       [([P.mono(m, x=1) - P.const(p), lin(1, -r)], "SIGN", "Умножи обратно — средният член е с друг знак."),
                        ([P.mono(m, x=1) + P.const(p), lin(1, -r)], "SIGN", "Провери знака на свободния член.")],
                       ["Раздели средния член на два.", f"Числата имат произведение {m * p * r} и сбор {m * r + p}.", "Групирай по двойки и изнеси общата скоба."], way)


def gr_number(rng, key, pts=2):
    a = rng.randint(13, 47)
    b = 100 - a
    d = rng.randint(3, 9)
    c = d + rng.choice([10, 20, 30])
    res = (a + b) * (c - d)
    text = f"{a}·{c} − {a}·{d} + {b}·{c} − {b}·{d}"
    return q(key, AP, "Пресмята рационално чрез групиране", "NUMERIC", "ADVANCED", 90, pts, "FACTORISATION",
             f"Пресметни рационално: {text}", "C", answer=str(res),
             distractors=[{"match": str((a + b) * (c + d)), "misconception": "SIGN", "explanation": f"Във втората скоба е {c} − {d}."}],
             hints=["Групирай първите два и последните два члена.", f"{a}({c} − {d}) + {b}({c} − {d}).", f"({a} + {b})({c} − {d})."],
             solution=f"{text} = {a}({c} − {d}) + {b}({c} − {d}) = ({a} + {b})({c} − {d}) = 100·{c - d} = {num(res)}.")


def gr_steps(rng, key):
    if rng.random() < 0.5:
        m, n, a, b = _two_by_two(rng)
        F1, F2 = P.mono(m, x=1) + P.const(a), P.mono(n, y=1) + P.const(b)
        way = f"{P.mono(m, x=1).fmt()}{br(F2)}{sgn_term(a, br(F2))} = "
        wrong = [P.mono(m, x=1) - P.const(a), F2]
    else:
        a, b, F1, F2 = _cubic_group(rng)
        way = f"x²{br(F1)}{sgn_term(b, br(F1))} = "
        wrong = [F1, X * X - P.const(b)]
    full = F1 * F2
    return steps_factor(key, GR, GR_OUT, full, [F1, F2], wrong, "Провери знака при изнасянето от втората група.",
                        ["Раздели членовете на две групи.", "От всяка група изнеси общия множител — скобите трябва да са еднакви.", "Изнеси общата скоба."], way)


def gr_structured(rng, key):
    a = nz(rng, -6, 6)
    b = rng.randint(1, 9)
    F1, F2 = lin(1, a), X * X + P.const(b)
    full = F1 * F2
    v = rng.choice([v for v in range(-3, 6) if v != -a])
    return q(key, GR, "Разлага чрез групиране и прилага разлагането", "STRUCTURED", "ADVANCED", 270, 3, "FACTORISATION",
             f"Даден е изразът A = {full.fmt()}.", "C",
             part_prompts=[{"id": "a", "label": "а) Разложи A чрез групиране", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {val(v)}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Единствената стойност на x, при която A = 0", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": fac([F1, F2], False), "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(full.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": val(-a, False), "points": 1}],
             distractors=[{"match": fac([lin(1, -a), F2], False), "misconception": "SIGN", "explanation": "Провери знака в общата скоба — умножи обратно."}],
             hints=["Групирай първите два и последните два члена.", f"A = x²{br(F1)} + {b}{br(F1)} = {fac([F1, F2])}.", f"x² + {b} > 0 за всяко x, затова A = 0 само когато {F1.fmt()} = 0."],
             solution=f"а) A = {fac([F1, F2])}; б) {paren(v + a)}·{v * v + b} = {val(full.eval(x=v))}; в) x = {val(-a)}.",
             rubric="По 1 т. за всяка вярна част.")


def gr_ds_group(rng, key, pts=2):
    """x² − y² + k·x ± k·y → (x ∓ y)(x ± y + k): grouping + difference of squares."""
    k = nz(rng, -7, 7)
    s = rng.choice([1, -1])
    common = X - s * Y          # common bracket
    other = X + s * Y + P.const(k)
    full = common * other
    way = f"(x² − y²){sgn_term(k, br(common))} = {br(common)}{br(X + s * Y)}{sgn_term(k, br(common))} = "
    return expr_factor(key, GR, GR_OUT, "ADVANCED", pts, full, [common, other],
                       [([common, X + s * Y - P.const(k)], "SIGN", "Провери знака на числото в скобата."),
                        ([X + s * Y, X - s * Y + P.const(k)], "FACTORISATION", "Умножи обратно — получава се друг многочлен.")],
                       ["Групирай x² − y² и останалите два члена.", "x² − y² = (x − y)(x + y).", f"Изнеси общата скоба {br(common)}."], way)


# ================================================================== C15: combined

def cb_cf_ds_mc(rng, key):
    k = rng.randint(2, 7)
    a = rng.randint(1, 6)
    p = rng.choice([1, 1, 2])
    v = rng.choice(["x", "y"])
    mono = P.mono(k, x=p)
    Vv = P.mono(1, **{v: 1})
    F1, F2 = Vv - P.const(a), Vv + P.const(a)
    parts = [mono, F1, F2]
    full = prod(parts)
    cands = [([mono, (F1, 2)], "FORMULA_APPLICATION", f"{Vv.fmt()}² − {a * a} е разлика на квадрати, а не точен квадрат."),
             ([P.const(k), F1, F2], "FACTORISATION", f"Забравен е общият множител {P.mono(1, x=p).fmt()}."),
             ([mono, Vv - P.const(a * a), Vv + P.const(a * a)], "FORMULA_APPLICATION", f"{a * a} = {a}² — в скобите е {a}."),
             ([mono, Vv * Vv + P.const(a * a)], "SIGN", "Сборът на квадрати не се получава от разлика.")]
    return mc_factor(rng, key, CB, CB_OUT, "ADVANCED", full, parts, cands,
                     [f"Първо изнеси общия множител {mono.fmt()}.", f"В скобата остава {Vv.fmt()}² − {a * a}.", "Това е разлика на квадрати — разложи и нея."],
                     f"{mono.fmt()}({(Vv * Vv - P.const(a * a)).fmt()}) = ")


def cb_cf_sq_expr(rng, key, pts=2):
    k = rng.randint(2, 6)
    a = nz(rng, -5, 5)
    mono = P.mono(k, x=1) if rng.random() < 0.6 else P.const(k)
    base = lin(1, a)
    full = mono * base ** 2
    return expr_factor(key, CB, CB_OUT, "ADVANCED", pts, full, [mono, (base, 2)],
                       [([mono, (lin(1, -a), 2)], "SIGN", "Знакът в скобата е като знака на средния член."),
                        ([mono, lin(1, -a), lin(1, a)], "FORMULA_APPLICATION", "В скобата има точен квадрат, а не разлика на квадрати.")],
                       [f"Изнеси общия множител {mono.fmt()}.", f"В скобата остава {(base ** 2).fmt()}.", "Това е точен квадрат."],
                       f"{mono.fmt()}({(base ** 2).fmt()}) = ")


def cb_fourth_mc(rng, key):
    m, n = rng.choice([(1, 1), (1, 2), (1, 3), (2, 1), (3, 1), (2, 3), (3, 2)])
    two = rng.random() < 0.4
    A = P.mono(m, x=1)
    B = P.mono(n, y=1) if two else P.const(n)
    full = A ** 4 - B ** 4
    parts = [A - B, A + B, A * A + B * B]
    cands = [([A - B, A + B, A * A - B * B], "SIGN", "A² + B² е сбор — той не се разлага, но и не става разлика."),
             ([(A - B, 2), A * A + B * B], "FORMULA_APPLICATION", "A² − B² = (A − B)(A + B), а не (A − B)²."),
             ([A * A - B, A * A + B], "FORMULA_APPLICATION", f"Коренът от {(A ** 4).fmt()} е {(A * A).fmt()}, а от {(B ** 4).fmt()} — {(B * B).fmt()}."),
             ([(A - B, 2), (A + B, 2)], "FORMULA_APPLICATION", "Това е (A² − B²)², а не A⁴ − B⁴.")]
    return mc_factor(rng, key, CB, CB_OUT, "ADVANCED", full, parts, cands,
                     [f"Запиши като ({(A * A).fmt()})² − ({(B * B).fmt()})².", "Приложи разлика на квадрати два пъти.", "Сборът на квадрати не се разлага."],
                     f"({(A * A - B * B).fmt()})({(A * A + B * B).fmt()}) = ")


def _group_ds(rng):
    a, b = nz(rng, -6, 6), rng.randint(1, 5)
    if abs(a) == b:
        raise ValueError
    return a, b, lin(1, a), lin(1, -b), lin(1, b)


def cb_group_ds_mc(rng, key):
    a, b, F, L1, L2 = _group_ds(rng)
    full = F * L1 * L2
    cands = [([lin(1, -a), L1, L2], "SIGN", "Провери знака в общата скоба."),
             ([F, (L1, 2)], "FORMULA_APPLICATION", "x² − b² е разлика на квадрати, а не точен квадрат."),
             ([F, X * X + P.const(b * b)], "SIGN", f"От втората група изнасяме −{b * b}."),
             ([lin(1, b), lin(1, -a), lin(1, a)], "FACTORISATION", "Числата са разменени — умножи обратно.")]
    return mc_factor(rng, key, CB, CB_OUT, "ADVANCED", full, [F, L1, L2], cands,
                     ["Групирай по двойки.", f"x²{br(F)} − {b * b}{br(F)} = {br(F)}(x² − {b * b}).", "Разложи и разликата на квадрати."],
                     f"x²{br(F)} − {b * b}{br(F)} = {br(F)}(x² − {b * b}) = ")


def cb_group_ds_expr(rng, key, pts=2):
    a, b, F, L1, L2 = _group_ds(rng)
    full = F * L1 * L2
    return expr_factor(key, CB, CB_OUT, "ADVANCED", pts, full, [F, L1, L2],
                       [([F, (L1, 2)], "FORMULA_APPLICATION", "x² − b² е разлика на квадрати, а не точен квадрат."),
                        ([lin(1, -a), L1, L2], "SIGN", "Провери знака в общата скоба.")],
                       ["Групирай първите два и последните два члена.", f"Общата скоба е {br(F)}.", f"Остава x² − {b * b} — разложи и нея."],
                       f"x²{br(F)} − {b * b}{br(F)} = {br(F)}(x² − {b * b}) = ")


def _sq_minus(rng):
    n = rng.randint(1, 6)
    s = rng.choice([1, -1])
    if rng.random() < 0.5:
        base = X + s * Y
        full = base ** 2 - P.const(n * n)
        parts = [base - P.const(n), base + P.const(n)]
        way = f"({base.fmt()})² − {n}² = "
        wrong = [(base - P.const(n), 2)]
    else:
        inner = Y + P.const(s * n)
        full = X * X - inner ** 2
        parts = [X - inner, X + inner]
        way = f"x² − ({inner.fmt()})² = "
        wrong = [X - Y + P.const(s * n), X + inner]
    return full, parts, way, wrong


def cb_sq_minus_mc(rng, key):
    full, parts, way, wrong = _sq_minus(rng)
    p1, p2 = parts
    flip = lambda p: P({m: (-c if m == (("y", 1),) else c) for m, c in p.t.items()})
    dbl = lambda p: P({m: (2 * c if m == () else c) for m, c in p.t.items()})
    cands = [(wrong, "FORMULA_APPLICATION", "Първо събери точния квадрат, после — разлика на квадрати."),
             ([(p1, 2)], "SIGN", "Разликата на квадрати е (A − B)(A + B), а не (A − B)²."),
             ([flip(p1), flip(p2)], "SIGN", "Провери знака пред y — умножи обратно."),
             ([dbl(p1), dbl(p2)], "FORMULA_APPLICATION", "Числото в скобите е коренът от свободния член.")]
    return mc_factor(rng, key, CB, CB_OUT, "ADVANCED", full, parts, cands,
                     ["Потърси точен квадрат сред членовете.", "Запиши израза като A² − B².", "Приложи разлика на квадрати."], way)


def cb_sq_minus_expr(rng, key, pts=2):
    full, parts, way, wrong = _sq_minus(rng)
    return expr_factor(key, CB, CB_OUT, "ADVANCED", pts, full, parts,
                       [(wrong, "FORMULA_APPLICATION", "Първо събери точния квадрат, после — разлика на квадрати."),
                        ([parts[0], parts[0]], "SIGN", "Разликата на квадрати е (A − B)(A + B).")],
                       ["Потърси три члена, които образуват точен квадрат.", "Запиши израза като A² − B².", "(A − B)(A + B)."], way)


def cb_cubes_expr(rng, key, pts=2):
    k = rng.randint(2, 5)
    n = rng.randint(1, 4)
    s = rng.choice([1, -1])
    withx = rng.random() < 0.5
    mono = P.mono(k, x=1) if withx else P.const(k)
    lin_, sq_ = X + P.const(s * n), X * X - P.mono(s * n, x=1) + P.const(n * n)
    full = mono * lin_ * sq_
    return expr_factor(key, CB, CB_OUT, "ADVANCED", pts, full, [mono, lin_, sq_],
                       [([mono, lin_, X * X + P.mono(s * n, x=1) + P.const(n * n)], "SIGN", "Средният член в непълния квадрат е с обратния знак."),
                        ([P.const(k), lin_, sq_], "FACTORISATION", "Изнеси целия общ множител.")],
                       [f"Изнеси общия множител {mono.fmt()}.", f"В скобата остава x³ {'+' if s > 0 else '−'} {n ** 3}.", "Приложи формулата за сбор/разлика на кубове."],
                       f"{mono.fmt()}(x³ {'+' if s > 0 else '−'} {n ** 3}) = ")


def cb_binoms_expr(rng, key, pts=2):
    m, n = rng.choice([(2, 1), (3, 1), (3, 2), (4, 1), (4, 3), (5, 2)])
    a, b = nz(rng, -5, 5), nz(rng, -5, 5)
    U, V = lin(m, a), lin(n, b)
    b1, b2 = U - V, U + V
    g1, g2 = content(b1), content(b2)
    k = g1 * g2
    f1, f2 = P({mm: c / g1 for mm, c in b1.t.items()}), P({mm: c / g2 for mm, c in b2.t.items()})
    if len(f1.t) < 2 or len(f2.t) < 2:
        raise ValueError
    parts = ([P.const(k)] if k > 1 else []) + [f1, f2]
    full = U ** 2 - V ** 2
    shown = f"({U.fmt()})² − ({V.fmt()})²"
    return expr_factor(key, CB, CB_OUT, "ADVANCED", pts, full, parts,
                       [([U - lin(n, -b), U + V], "BRACKETS", f"Минусът пред ({V.fmt()}) сменя знака на всеки член в нея."),
                        ([(b1, 2)], "FORMULA_APPLICATION", "A² − B² = (A − B)(A + B), а не (A − B)².")],
                       ["Не разкривай квадратите — това е A² − B².", f"({U.fmt()} − ({V.fmt()}))({U.fmt()} + {V.fmt()}).", "Опрости всяка скоба и изнеси общ множител, ако има."],
                       f"({U.fmt()} − {br(V)})({U.fmt()} + {V.fmt()}) = ", shown=shown)


def cb_number(rng, key, pts=2):
    kind = rng.choice(["sq", "cube"])
    if kind == "sq":
        c = rng.choice([50, 60, 70, 80, 90, 100])
        b = rng.randint(11, 29)
        a = c + b
        res = c * c
        text = f"{a}² − 2·{a}·{b} + {b}²"
        sol = f"{text} = ({a} − {b})² = {c}² = {num(res)}."
        wrong = str(a * a - b * b)
        hints = ["Това е точен квадрат на разлика.", f"a = {a}, b = {b}.", f"({a} − {b})² = {c}²."]
    else:
        n = rng.choice([99, 101, 49, 51, 199, 201])
        res = n ** 3 - n
        text = f"{n}³ − {n}"
        sol = f"{text} = {n}({n}² − 1) = {n}·{n - 1}·{n + 1} = {num(res)}."
        wrong = str(n * (n - 1) ** 2)
        hints = [f"Изнеси {n}.", f"{n}({n}² − 1).", f"{n}² − 1 = ({n} − 1)({n} + 1)."]
    return q(key, AP, "Пресмята рационално чрез разлагане", "NUMERIC", "ADVANCED", 120, pts, "FORMULA_APPLICATION",
             f"Пресметни рационално: {text}", "C", answer=str(res),
             distractors=[{"match": wrong, "misconception": "FORMULA_APPLICATION", "explanation": "Разложи израза по формула — после пресметни."}],
             hints=hints, solution=sol)


def cb_divis_mc(rng, key):
    a = rng.choice([2, 3, 4, 5, 6, 7])
    good = a * a - 1
    base = a * (a * a - 1)
    pool = [good + 1, good + 2, a * a + 1, 2 * good + 1, a + 2 * a * a, a * a * a, good - 2]
    wrongs, seen = [], {good}
    for d in pool:
        if d > 1 and d not in seen and base % d != 0:
            seen.add(d)
            wrongs.append((str(d), "REASONING", f"{a}ⁿ⁺² − {a}ⁿ = {a}ⁿ·{good}, а {d} не дели {a}·{good} = {base} (при n = 1)."))
    return mc(rng, key, AP, "Доказва делимост чрез разлагане", "ADVANCED", "REASONING",
              f"За всяко естествено n изразът {a}ⁿ⁺² − {a}ⁿ се дели на:", str(good), wrongs,
              [f"Изнеси общия множител {a}ⁿ.", f"{a}ⁿ⁺² = {a}ⁿ·{a}².", f"{a}ⁿ({a}² − 1) = {a}ⁿ·{good}."],
              f"{a}ⁿ⁺² − {a}ⁿ = {a}ⁿ({a * a} − 1) = {good}·{a}ⁿ — един от множителите е {good}, затова изразът се дели на {good}.", "C", secs=120)


def cb_steps(rng, key):
    kind = rng.choice(["cfsq", "fourth", "groupds"])
    if kind == "cfsq":
        k, a = rng.randint(2, 5), nz(rng, -5, 5)
        mono = P.mono(k, x=1)
        parts = [mono, (lin(1, a), 2)]
        full = prod(parts)
        return steps_factor(key, CB, CB_OUT, full, parts, [mono, (lin(1, -a), 2)], "Знакът в скобата е като знака на средния член.",
                            [f"Изнеси {mono.fmt()}.", f"В скобата остава {(lin(1, a) ** 2).fmt()}.", "Точен квадрат."], f"{mono.fmt()}({(lin(1, a) ** 2).fmt()}) = ")
    if kind == "fourth":
        n = rng.randint(1, 3)
        parts = [lin(1, -n), lin(1, n), X * X + P.const(n * n)]
        full = prod(parts)
        return steps_factor(key, CB, CB_OUT, full, parts, [lin(1, -n), lin(1, n), X * X - P.const(n * n)], "x² + b² е сбор на квадрати — не става разлика.",
                            [f"x⁴ − {n ** 4} = (x²)² − ({n * n})².", "Разлика на квадрати.", f"x² − {n * n} се разлага още, x² + {n * n} — не."],
                            f"(x² − {n * n})(x² + {n * n}) = ")
    a, b, F, L1, L2 = _group_ds(rng)
    parts = [F, L1, L2]
    full = prod(parts)
    return steps_factor(key, CB, CB_OUT, full, parts, [F, (L1, 2)], "x² − b² е разлика на квадрати.",
                        ["Групирай по двойки.", f"Общата скоба е {br(F)}.", f"Разложи и x² − {b * b}."], f"{br(F)}(x² − {b * b}) = ")


def cb_structured(rng, key):
    a, b = rng.sample(range(1, 7), 2)
    F, L1, L2 = lin(1, -a), lin(1, -b), lin(1, b)
    full = F * L1 * L2
    v = rng.choice([10, 11, 12, 20])
    return q(key, CB, "Разлага напълно и прилага разлагането", "STRUCTURED", "ADVANCED", 300, 3, "FACTORISATION",
             f"Даден е изразът A = {full.fmt()}.", "C",
             part_prompts=[{"id": "a", "label": "а) Разложи A напълно", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": f"б) Стойността на A при x = {v}", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Най-голямата стойност на x, при която A = 0", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": fac([F, L1, L2], False), "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": val(full.eval(x=v), False), "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(max(a, b)), "points": 1}],
             distractors=[{"match": fac([F, X * X - P.const(b * b)], False), "misconception": "FACTORISATION", "explanation": f"x² − {b * b} се разлага още."}],
             hints=["Групирай по двойки.", f"A = x²{br(F)} − {b * b}{br(F)} = {br(F)}(x² − {b * b}).", "Произведение е 0, когато някой множител е 0."],
             solution=f"а) A = {fac([F, L1, L2])}; б) {v - a}·{v - b}·{v + b} = {num(int(full.eval(x=v)))}; в) A = 0 при x = {a}, x = {b} или x = −{b}; най-голямата е {max(a, b)}.",
             rubric="По 1 т. за всяка вярна част.")


def cb_equation(rng, key):
    k, a = rng.randint(2, 5), rng.randint(2, 7)
    full = P.mono(k, x=3) - P.mono(k * a * a, x=1)
    return q(key, CB, "Решава уравнение чрез разлагане", "STRUCTURED", "ADVANCED", 270, 3, "FACTORISATION",
             f"Решаваме уравнението {full.fmt()} = 0.", "C",
             part_prompts=[{"id": "a", "label": "а) Разложи лявата страна напълно", "type": "EXPRESSION", "points": 1},
                           {"id": "b", "label": "б) Колко корена има уравнението?", "type": "NUMERIC", "points": 1},
                           {"id": "c", "label": "в) Най-големият корен", "type": "NUMERIC", "points": 1}],
             parts=[{"id": "a", "type": "EXPRESSION", "answer": fac([P.mono(k, x=1), lin(1, -a), lin(1, a)], False), "form": "FACTORISED", "points": 1},
                    {"id": "b", "type": "NUMERIC", "answer": "3", "points": 1},
                    {"id": "c", "type": "NUMERIC", "answer": str(a), "points": 1}],
             distractors=[{"match": fac([P.mono(k, x=1), (lin(1, -a), 2)], False), "misconception": "FORMULA_APPLICATION", "explanation": "x² − a² е разлика на квадрати."}],
             hints=[f"Изнеси {k}x.", f"{k}x(x² − {a * a}) = {k}x(x − {a})(x + {a}).", "Произведение е 0, когато поне един множител е 0."],
             solution=f"а) {k}x(x − {a})(x + {a}); б) x = 0, x = {a}, x = −{a} — три корена; в) {a}.", rubric="По 1 т. за всяка вярна част.")


# ------------------------------------------------------------------ lesson helpers

def gr_common_bracket_mc(rng, key):
    """x(y + b) + a(y + b) — take out the common bracket (prerequisite for grouping)."""
    a, b = nz(rng, -7, 7), nz(rng, -7, 7)
    if abs(a) == abs(b):
        raise ValueError
    F2 = lin(1, b, "y")
    full = X * F2 + P.const(a) * F2
    shown = f"x{br(F2)}{sgn_term(a, br(F2))}"
    cands = [([lin(1, -a), F2], "SIGN", "Вторият множител е числото пред скобата заедно със знака му."),
             ([X, F2], "FACTORISATION", "Изнесена е скобата, но е забравено числото."),
             ([lin(1, b), lin(1, a, "y")], "FACTORISATION", "Числата са разменени — умножи обратно."),
             ([lin(1, a), lin(1, -b, "y")], "SIGN", "Общата скоба не се променя.")]
    return mc_factor(rng, key, GR, "Изнася обща скоба", "INTERMEDIATE", full, [lin(1, a), F2], cands,
                     ["И двата члена съдържат една и съща скоба.", f"Изнеси {br(F2)}.", f"Остават x и {val(a)}."], "", prefix="Разложи", shown=shown)
