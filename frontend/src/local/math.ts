// On-device port of the server maths engine (bg.mathmission.math): restricted grammar → AST →
// polynomial / rational normal form. Student input is parsed, never evaluated as code.

export class MathInputError extends Error {
  constructor(public code: string, message: string) { super(message); }
}

const MAX_INPUT = 300, MAX_TOKENS = 200, MAX_DEPTH = 40, MAX_EXP = 12, MAX_TERMS = 300, MAX_DEGREE = 24;

// ------------------------------------------------------------------ rationals (exact, BigInt)

const abs = (a: bigint) => (a < 0n ? -a : a);
function gcd(a: bigint, b: bigint): bigint { a = abs(a); b = abs(b); while (b) { [a, b] = [b, a % b]; } return a; }
function isqrt(n: bigint): bigint { if (n < 2n) return n; let x = BigInt(Math.floor(Math.sqrt(Number(n)))); while (x * x > n) x--; while ((x + 1n) * (x + 1n) <= n) x++; return x; }

export class Q {
  readonly n: bigint; readonly d: bigint;
  constructor(n: bigint, d: bigint = 1n) {
    if (d === 0n) throw new MathInputError('DIVISION_BY_ZERO', 'Деление на нула не е позволено.');
    if (d < 0n) { n = -n; d = -d; }
    const g = gcd(n, d);
    if (g > 1n) { n /= g; d /= g; }
    if (n === 0n) d = 1n;
    this.n = n; this.d = d;
  }
  static of(n: number | bigint, d: number | bigint = 1) { return new Q(BigInt(n), BigInt(d)); }
  static parse(text: string): Q {
    const s = text.trim().replace(',', '.').replace('−', '-');
    if (!s || s.length > 40) throw new MathInputError('INVALID_NUMBER', 'Въведи число.');
    const slash = s.indexOf('/');
    if (slash > 0) return Q.parse(s.slice(0, slash)).div(Q.parse(s.slice(slash + 1)));
    const m = /^([+-]?)(\d+)(?:\.(\d+))?$/.exec(s);
    if (!m) throw new MathInputError('INVALID_NUMBER', 'Въведи число, например 2,5 или 3/4.');
    const frac = m[3] ?? '';
    const n = BigInt(m[2] + frac) * (m[1] === '-' ? -1n : 1n);
    return new Q(n, 10n ** BigInt(frac.length));
  }
  add(o: Q) { return new Q(this.n * o.d + o.n * this.d, this.d * o.d); }
  sub(o: Q) { return this.add(o.neg()); }
  mul(o: Q) { return new Q(this.n * o.n, this.d * o.d); }
  div(o: Q) { if (o.n === 0n) throw new MathInputError('DIVISION_BY_ZERO', 'Деление на нула не е позволено.'); return new Q(this.n * o.d, this.d * o.n); }
  neg() { return new Q(-this.n, this.d); }
  pow(e: number) { return new Q(this.n ** BigInt(e), this.d ** BigInt(e)); }
  isZero() { return this.n === 0n; }
  isOne() { return this.n === 1n && this.d === 1n; }
  isInt() { return this.d === 1n; }
  sign() { return this.n > 0n ? 1 : this.n < 0n ? -1 : 0; }
  eq(o: Q) { return this.n === o.n && this.d === o.d; }
  display(): string {
    if (this.isInt()) return this.n.toString();
    let d = this.d; while (d % 2n === 0n) d /= 2n; while (d % 5n === 0n) d /= 5n;
    if (d === 1n) {
      let k = 0; while ((10n ** BigInt(k)) % this.d !== 0n) k++;
      const scaled = (this.n * 10n ** BigInt(k)) / this.d;
      const neg = scaled < 0n; const digits = abs(scaled).toString().padStart(k + 1, '0');
      const out = digits.slice(0, digits.length - k) + ',' + digits.slice(digits.length - k);
      return (neg ? '-' : '') + out.replace(/0+$/, '').replace(/,$/, '');
    }
    return `${this.n}/${this.d}`;
  }
}
const ZERO = Q.of(0), ONE = Q.of(1);

// ------------------------------------------------------------------ monomials and polynomials

type Powers = Record<string, number>;
const monoKey = (p: Powers) => Object.keys(p).sort().map((v) => `${v}${p[v]}`).join('');
const monoDeg = (p: Powers) => Object.values(p).reduce((a, b) => a + b, 0);
function monoMul(a: Powers, b: Powers): Powers { const r = { ...a }; for (const [v, e] of Object.entries(b)) r[v] = (r[v] ?? 0) + e; return r; }
function monoDiv(a: Powers, b: Powers): Powers | null {
  const r = { ...a };
  for (const [v, e] of Object.entries(b)) { const have = r[v] ?? 0; if (have < e) return null; if (have === e) delete r[v]; else r[v] = have - e; }
  return r;
}

interface Term { p: Powers; c: Q }

export class Poly {
  constructor(readonly t: Map<string, Term> = new Map()) {
    if (t.size > MAX_TERMS) throw new MathInputError('TOO_COMPLEX', 'Изразът е твърде сложен за проверка.');
  }
  static c(q: Q) { const m = new Map<string, Term>(); if (!q.isZero()) m.set('', { p: {}, c: q }); return new Poly(m); }
  static v(name: string) { return new Poly(new Map([[`${name}1`, { p: { [name]: 1 }, c: ONE }]])); }
  get size() { return this.t.size; }
  isZero() { return this.t.size === 0; }
  isConst() { return this.t.size === 0 || (this.t.size === 1 && this.t.has('')); }
  constVal() { return this.t.get('')?.c ?? ZERO; }
  add(o: Poly) {
    const m = new Map(this.t);
    for (const [k, term] of o.t) {
      const s = (m.get(k)?.c ?? ZERO).add(term.c);
      if (s.isZero()) m.delete(k); else m.set(k, { p: term.p, c: s });
    }
    return new Poly(m);
  }
  neg() { const m = new Map<string, Term>(); for (const [k, x] of this.t) m.set(k, { p: x.p, c: x.c.neg() }); return new Poly(m); }
  sub(o: Poly) { return this.add(o.neg()); }
  mul(o: Poly) {
    if (this.t.size * o.t.size > 4 * MAX_TERMS) throw new MathInputError('TOO_COMPLEX', 'Изразът е твърде сложен за проверка.');
    const m = new Map<string, Term>();
    for (const a of this.t.values()) for (const b of o.t.values()) {
      const p = monoMul(a.p, b.p);
      if (monoDeg(p) > MAX_DEGREE) throw new MathInputError('TOO_COMPLEX', 'Степента на израза е твърде голяма.');
      const k = monoKey(p);
      const s = (m.get(k)?.c ?? ZERO).add(a.c.mul(b.c));
      if (s.isZero()) m.delete(k); else m.set(k, { p, c: s });
    }
    return new Poly(m);
  }
  scale(q: Q) { return this.mul(Poly.c(q)); }
  pow(e: number) { let r = Poly.c(ONE); for (let i = 0; i < e; i++) r = r.mul(this); return r; }
  divMono(d: Poly): Poly | null {
    if (d.t.size !== 1) return null;
    const dt = [...d.t.values()][0];
    const m = new Map<string, Term>();
    for (const x of this.t.values()) { const p = monoDiv(x.p, dt.p); if (!p) return null; m.set(monoKey(p), { p, c: x.c.div(dt.c) }); }
    return new Poly(m);
  }
  equals(o: Poly) {
    if (o.t.size !== this.t.size) return false;
    for (const [k, x] of this.t) { const y = o.t.get(k); if (!y || !y.c.eq(x.c)) return false; }
    return true;
  }
  evaluate(vals: Record<string, Q>) {
    let s = ZERO;
    for (const x of this.t.values()) {
      let m = ONE;
      for (const [v, e] of Object.entries(x.p)) { const q = vals[v]; if (!q) throw new MathInputError('MISSING_VALUE', `Липсва стойност за ${v}.`); m = m.mul(q.pow(e)); }
      s = s.add(x.c.mul(m));
    }
    return s;
  }
}

// ------------------------------------------------------------------ AST and parser

export type Expr =
  | { k: 'num'; v: Q } | { k: 'var'; v: string } | { k: 'neg'; e: Expr } | { k: 'add'; l: Expr; r: Expr }
  | { k: 'sub'; l: Expr; r: Expr } | { k: 'mul'; l: Expr; r: Expr } | { k: 'div'; l: Expr; r: Expr }
  | { k: 'pow'; b: Expr; e: number } | { k: 'grp'; e: Expr };

type Tok = { kind: 'NUM' | 'VAR' | 'OP' | 'LP' | 'RP' | 'SUP' | 'END'; text: string };

const CYR: Record<string, string> = { 'а': 'a', 'А': 'a', 'в': 'b', 'В': 'b', 'с': 'c', 'С': 'c', 'е': 'e', 'Е': 'e', 'к': 'k', 'К': 'k',
  'м': 'm', 'М': 'm', 'о': 'o', 'О': 'o', 'р': 'p', 'Р': 'p', 'т': 't', 'Т': 't', 'х': 'x', 'Х': 'x', 'у': 'y', 'У': 'y',
  '−': '-', '–': '-', '—': '-', '·': '*', '×': '*', '⋅': '*', '∙': '*', '÷': ':' };
const SUPD: Record<string, string> = { '⁰': '0', '¹': '1', '²': '2', '³': '3', '⁴': '4', '⁵': '5', '⁶': '6', '⁷': '7', '⁸': '8', '⁹': '9' };

function tokenize(raw: string): Tok[] {
  const out: Tok[] = [];
  let i = 0;
  while (i < raw.length) {
    const c = CYR[raw[i]] ?? raw[i];
    if (/\s/.test(c)) { i++; continue; }
    if (/\d/.test(c)) {
      let s = ''; let sep = false;
      while (i < raw.length) {
        const d = raw[i];
        if (/\d/.test(d)) s += d;
        else if ((d === '.' || d === ',') && !sep && /\d/.test(raw[i + 1] ?? '')) { s += '.'; sep = true; }
        else break;
        i++;
      }
      if (s.length > 18) throw new MathInputError('NUMBER_TOO_LONG', 'Числото е твърде дълго.');
      out.push({ kind: 'NUM', text: s });
    } else if (SUPD[c]) {
      let s = ''; while (i < raw.length && SUPD[raw[i]]) { s += SUPD[raw[i]]; i++; }
      out.push({ kind: 'SUP', text: s });
    } else if (/[a-zA-Z]/.test(c)) { out.push({ kind: 'VAR', text: c.toLowerCase() }); i++; }
    else if (c === '(' || c === '[') { out.push({ kind: 'LP', text: c }); i++; }
    else if (c === ')' || c === ']') { out.push({ kind: 'RP', text: c }); i++; }
    else if ('+-*/:^'.includes(c)) { out.push({ kind: 'OP', text: c }); i++; }
    else throw new MathInputError('UNSUPPORTED_SYMBOL', `Символът „${raw[i]}“ не се поддържа.`);
    if (out.length > MAX_TOKENS) throw new MathInputError('TOO_COMPLEX', 'Изразът е твърде сложен.');
  }
  out.push({ kind: 'END', text: 'край' });
  return out;
}

export function parse(input: string | undefined | null): Expr {
  if (input == null || !input.trim()) throw new MathInputError('EMPTY', 'Отговорът е празен.');
  if (input.length > MAX_INPUT) throw new MathInputError('TOO_LONG', 'Изразът е твърде дълъг.');
  const toks = tokenize(input);
  let i = 0; let depth = 0;
  const peek = () => toks[i];
  const next = () => toks[i++];
  const isOp = (o: string) => peek().kind === 'OP' && peek().text === o;
  const enter = () => { if (++depth > MAX_DEPTH) throw new MathInputError('TOO_DEEP', 'Изразът има твърде много вложени скоби.'); };
  const startsPrimary = (t: Tok) => t.kind === 'NUM' || t.kind === 'VAR' || t.kind === 'LP';
  const exponent = (s: string) => {
    if (s.includes('.')) throw new MathInputError('BAD_EXPONENT', 'Степенният показател трябва да е цяло число.');
    const e = Number(s);
    if (e > MAX_EXP) throw new MathInputError('BAD_EXPONENT', `Степенният показател е твърде голям (най-много ${MAX_EXP}).`);
    return e;
  };

  function expr(): Expr {
    enter();
    let l = term();
    while (isOp('+') || isOp('-')) { const o = next().text; const r = term(); l = o === '+' ? { k: 'add', l, r } : { k: 'sub', l, r }; }
    depth--;
    return l;
  }
  function term(): Expr {
    let l = implicit();
    for (;;) {
      if (isOp('*')) { next(); l = { k: 'mul', l, r: implicit() }; }
      else if (isOp('/') || isOp(':')) { next(); l = { k: 'div', l, r: implicit() }; }
      else return l;
    }
  }
  function implicit(): Expr {
    let l = unary();
    while (startsPrimary(peek())) {
      if (peek().kind === 'NUM') throw new MathInputError('NUMBER_AFTER_TERM', 'Число след буква или скоба е двусмислено. За степен използвай ^, напр. x^2.');
      l = { k: 'mul', l, r: power() };
    }
    return l;
  }
  function unary(): Expr {
    if (isOp('-')) { next(); enter(); const e: Expr = { k: 'neg', e: unary() }; depth--; return e; }
    if (isOp('+')) { next(); return unary(); }
    return power();
  }
  function power(): Expr {
    let b = primary();
    for (;;) {
      if (isOp('^')) {
        next();
        const t = next();
        if (t.kind === 'NUM') b = { k: 'pow', b, e: exponent(t.text) };
        else if (t.kind === 'LP' && peek().kind === 'NUM') {
          const e = exponent(next().text);
          if (next().kind !== 'RP') throw new MathInputError('BAD_EXPONENT', 'Степенният показател трябва да е цяло число.');
          b = { k: 'pow', b, e };
        } else throw new MathInputError('BAD_EXPONENT', 'Степенният показател трябва да е естествено число, напр. x^3.');
      } else if (peek().kind === 'SUP') b = { k: 'pow', b, e: exponent(next().text) };
      else return b;
    }
  }
  function primary(): Expr {
    const t = next();
    switch (t.kind) {
      case 'NUM': return { k: 'num', v: Q.parse(t.text) };
      case 'VAR': return { k: 'var', v: t.text };
      case 'LP': {
        enter();
        const e = expr();
        if (next().kind !== 'RP') throw new MathInputError('UNBALANCED', 'Липсва затваряща скоба.');
        depth--;
        return { k: 'grp', e };
      }
      case 'RP': throw new MathInputError('UNBALANCED', 'Има затваряща скоба без отваряща.');
      case 'END': throw new MathInputError('INCOMPLETE', 'Изразът е незавършен.');
      default: throw new MathInputError('UNEXPECTED', `Неочакван символ „${t.text}“.`);
    }
  }
  const e = expr();
  if (peek().kind !== 'END') throw new MathInputError('UNEXPECTED', `Неочакван символ „${peek().text}“.`);
  return e;
}

// ------------------------------------------------------------------ normalisation and equivalence

export interface Norm { num: Poly; den: Poly }

function simplify(num: Poly, den: Poly): Norm {
  if (num.isZero()) return { num: Poly.c(ZERO), den: Poly.c(ONE) };
  if (den.isConst()) return { num: num.scale(ONE.div(den.constVal())), den: Poly.c(ONE) };
  const q = num.divMono(den);
  if (q) return { num: q, den: Poly.c(ONE) };
  if (num.equals(den)) return { num: Poly.c(ONE), den: Poly.c(ONE) };
  return { num, den };
}

export function normalise(e: Expr | string): Norm {
  if (typeof e === 'string') e = parse(e);
  switch (e.k) {
    case 'num': return { num: Poly.c(e.v), den: Poly.c(ONE) };
    case 'var': return { num: Poly.v(e.v), den: Poly.c(ONE) };
    case 'grp': return normalise(e.e);
    case 'neg': { const a = normalise(e.e); return { num: a.num.neg(), den: a.den }; }
    case 'add': case 'sub': {
      const a = normalise(e.l); let b = normalise(e.r);
      if (e.k === 'sub') b = { num: b.num.neg(), den: b.den };
      if (a.den.equals(b.den)) return simplify(a.num.add(b.num), a.den);
      return simplify(a.num.mul(b.den).add(b.num.mul(a.den)), a.den.mul(b.den));
    }
    case 'mul': { const a = normalise(e.l), b = normalise(e.r); return simplify(a.num.mul(b.num), a.den.mul(b.den)); }
    case 'div': {
      const a = normalise(e.l), b = normalise(e.r);
      if (b.num.isZero()) throw new MathInputError('DIVISION_BY_ZERO', 'Деление на нула не е позволено.');
      return simplify(a.num.mul(b.den), a.den.mul(b.num));
    }
    case 'pow': {
      const a = normalise(e.b);
      if (e.e === 0 && a.num.isZero()) throw new MathInputError('ZERO_POWER_ZERO', '0⁰ не е определено.');
      return { num: a.num.pow(e.e), den: a.den.pow(e.e) };
    }
  }
}

export const isPolynomial = (n: Norm) => n.den.isConst();
export const asPoly = (n: Norm) => n.num.scale(ONE.div(n.den.constVal()));

export function equivalentN(a: Norm, b: Norm) { return a.num.mul(b.den).equals(b.num.mul(a.den)); }
export function equivalent(a: string, b: string) { return equivalentN(normalise(a), normalise(b)); }
export function safeEquivalent(a: string, b: string) { try { return equivalent(a, b); } catch { return false; } }

function flattenSum(e: Expr, out: Expr[]) {
  if (e.k === 'add') { flattenSum(e.l, out); flattenSum(e.r, out); }
  else if (e.k === 'sub') { flattenSum(e.l, out); out.push(e.r); }
  else out.push(e);
}
function flattenProd(e: Expr, out: Expr[]) { if (e.k === 'mul') { flattenProd(e.l, out); flattenProd(e.r, out); } else out.push(e); }
const unwrapSigned = (e: Expr): Expr => (e.k === 'neg' && e.e.k === 'num' ? e.e : e);

function isNormalMonomial(t: Expr) {
  if (t.k === 'neg') t = t.e;
  const fs: Expr[] = []; flattenProd(t, fs);
  if (fs.length && fs[0].k === 'neg') fs[0] = fs[0].e; // −a·b: the sign belongs to the coefficient
  const seen = new Set<string>();
  for (let i = 0; i < fs.length; i++) {
    const f = unwrapSigned(fs[i]);
    if (f.k === 'num') { if (i !== 0) return false; }
    else if (f.k === 'var') { if (seen.has(f.v)) return false; seen.add(f.v); }
    else if (f.k === 'pow' && f.b.k === 'var' && f.e >= 2) { if (seen.has(f.b.v)) return false; seen.add(f.b.v); }
    else return false;
  }
  return true;
}

export function isNormalForm(input: string) {
  const e = parse(input);
  const n = normalise(e);
  if (!isPolynomial(n)) return false;
  const adds: Expr[] = []; flattenSum(e, adds);
  if (!adds.every(isNormalMonomial)) return false;
  return adds.length === Math.max(asPoly(n).size, 1);
}

const isSum = (e: Expr) => e.k === 'add' || e.k === 'sub';

function hasCommonFactor(p: Poly) {
  if (p.size < 2) return false;
  let g = 0n; let common: Powers | null = null;
  for (const x of p.t.values()) {
    if (!x.c.isInt()) return false;
    g = gcd(g, x.c.n);
    if (common === null) common = { ...x.p };
    else for (const v of Object.keys(common)) { const m = Math.min(common[v], x.p[v] ?? 0); if (m > 0) common[v] = m; else delete common[v]; }
  }
  return g > 1n || (common !== null && monoDeg(common) > 0);
}

function sqrtTerm(p: Powers, c: Q): Poly | null {
  const rn = isqrt(c.n), rd = isqrt(c.d);
  if (rn * rn !== c.n || rd * rd !== c.d) return null;
  let r = Poly.c(new Q(rn, rd));
  for (const [v, e] of Object.entries(p)) { if (e % 2) return null; r = r.mul(Poly.v(v).pow(e / 2)); }
  return r;
}

function isSpecialProduct(p: Poly) {
  const t = [...p.t.values()];
  if (t.length === 2) {
    if (t[0].c.sign() === t[1].c.sign()) return false;
    const pos = (q: Q) => (q.sign() < 0 ? q.neg() : q);
    return sqrtTerm(t[0].p, pos(t[0].c)) !== null && sqrtTerm(t[1].p, pos(t[1].c)) !== null;
  }
  if (t.length === 3) {
    for (let i = 0; i < 3; i++) for (let j = i + 1; j < 3; j++) {
      if (t[i].c.sign() <= 0 || t[j].c.sign() <= 0) continue;
      const a = sqrtTerm(t[i].p, t[i].c), b = sqrtTerm(t[j].p, t[j].c);
      if (!a || !b) continue;
      if (a.add(b).pow(2).equals(p) || a.sub(b).pow(2).equals(p)) return true;
    }
  }
  return false;
}

export function isFullyFactorised(input: string) {
  let e = parse(input);
  if (e.k === 'neg') e = e.e;
  const fs: Expr[] = []; flattenProd(e, fs);
  let bracketSum = false;
  for (const f of fs) {
    const base = f.k === 'pow' ? f.b : unwrapSigned(f);
    if (base.k === 'grp' && isSum(base.e)) {
      bracketSum = true;
      const p = asPoly(normalise(base.e));
      if (hasCommonFactor(p) || isSpecialProduct(p)) return false;
    } else if (base.k === 'grp') continue;
    else if (base.k !== 'num' && base.k !== 'var') return false;
  }
  return bracketSum;
}

export function checkSteps(steps: string[]): { ok: boolean; firstInvalid: number } {
  if (steps.length < 2) return { ok: true, firstInvalid: -1 };
  let prev = normalise(steps[0]);
  for (let i = 1; i < steps.length; i++) {
    let cur: Norm;
    try { cur = normalise(steps[i]); } catch { return { ok: false, firstInvalid: i }; }
    if (!equivalentN(prev, cur)) return { ok: false, firstInvalid: i };
    prev = cur;
  }
  return { ok: true, firstInvalid: -1 };
}
