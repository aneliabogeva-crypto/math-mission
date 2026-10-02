package bg.mathmission.math;

import java.util.Map;
import java.util.TreeMap;

/**
 * Polynomial in several variables with rational coefficients, kept in normal form:
 * like terms are combined and zero terms are removed.
 */
public final class Polynomial {

    public static final int MAX_TERMS = 300;
    public static final int MAX_DEGREE = 24;

    /** Key: monomial literal part (sorted variable to exponent), value: coefficient. */
    private final TreeMap<Monomial, Rational> terms;

    private Polynomial(TreeMap<Monomial, Rational> terms) {
        if (terms.size() > MAX_TERMS) {
            throw new MathInputException("TOO_COMPLEX", "Изразът е твърде сложен за проверка.");
        }
        this.terms = terms;
    }

    public static Polynomial constant(Rational c) {
        TreeMap<Monomial, Rational> t = new TreeMap<>();
        if (!c.isZero()) t.put(Monomial.ONE, c);
        return new Polynomial(t);
    }

    public static Polynomial variable(char v) {
        TreeMap<Monomial, Rational> t = new TreeMap<>();
        t.put(Monomial.of(v, 1), Rational.ONE);
        return new Polynomial(t);
    }

    public static final Polynomial ZERO = constant(Rational.ZERO);
    public static final Polynomial ONE = constant(Rational.ONE);

    public Map<Monomial, Rational> terms() {
        return java.util.Collections.unmodifiableMap(terms);
    }

    public int termCount() {
        return terms.size();
    }

    public boolean isZero() {
        return terms.isEmpty();
    }

    public boolean isConstant() {
        return terms.isEmpty() || (terms.size() == 1 && terms.containsKey(Monomial.ONE));
    }

    public Rational constantValue() {
        return terms.getOrDefault(Monomial.ONE, Rational.ZERO);
    }

    public boolean isMonomial() {
        return terms.size() == 1;
    }

    public int degree() {
        return terms.keySet().stream().mapToInt(Monomial::degree).max().orElse(0);
    }

    public Polynomial add(Polynomial o) {
        TreeMap<Monomial, Rational> t = new TreeMap<>(terms);
        o.terms.forEach((m, c) -> {
            Rational sum = t.getOrDefault(m, Rational.ZERO).add(c);
            if (sum.isZero()) t.remove(m); else t.put(m, sum);
        });
        return new Polynomial(t);
    }

    public Polynomial negate() {
        TreeMap<Monomial, Rational> t = new TreeMap<>();
        terms.forEach((m, c) -> t.put(m, c.negate()));
        return new Polynomial(t);
    }

    public Polynomial subtract(Polynomial o) {
        return add(o.negate());
    }

    public Polynomial multiply(Polynomial o) {
        if ((long) terms.size() * o.terms.size() > 4L * MAX_TERMS) {
            throw new MathInputException("TOO_COMPLEX", "Изразът е твърде сложен за проверка.");
        }
        TreeMap<Monomial, Rational> t = new TreeMap<>();
        for (var a : terms.entrySet()) {
            for (var b : o.terms.entrySet()) {
                Monomial m = a.getKey().multiply(b.getKey());
                if (m.degree() > MAX_DEGREE) {
                    throw new MathInputException("TOO_COMPLEX", "Степента на израза е твърде голяма.");
                }
                Rational sum = t.getOrDefault(m, Rational.ZERO).add(a.getValue().multiply(b.getValue()));
                if (sum.isZero()) t.remove(m); else t.put(m, sum);
            }
        }
        return new Polynomial(t);
    }

    public Polynomial scale(Rational c) {
        return multiply(constant(c));
    }

    public Polynomial pow(int e) {
        Polynomial r = ONE;
        for (int i = 0; i < e; i++) r = r.multiply(this);
        return r;
    }

    /** Exact division by a single monomial; returns null when the result is not a polynomial. */
    public Polynomial divideByMonomial(Polynomial divisor) {
        if (!divisor.isMonomial()) return null;
        var d = divisor.terms.firstEntry();
        TreeMap<Monomial, Rational> t = new TreeMap<>();
        for (var e : terms.entrySet()) {
            Monomial q = e.getKey().divide(d.getKey());
            if (q == null) return null;
            t.put(q, e.getValue().divide(d.getValue()));
        }
        return new Polynomial(t);
    }

    public Rational evaluate(Map<Character, Rational> values) {
        Rational sum = Rational.ZERO;
        for (var e : terms.entrySet()) {
            sum = sum.add(e.getValue().multiply(e.getKey().evaluate(values)));
        }
        return sum;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Polynomial p && p.terms.equals(terms);
    }

    @Override
    public int hashCode() {
        return terms.hashCode();
    }

    /** Canonical display, highest degree first, e.g. "3x^2 - 2xy + 5". */
    @Override
    public String toString() {
        if (terms.isEmpty()) return "0";
        StringBuilder sb = new StringBuilder();
        for (var e : terms.descendingMap().entrySet()) {
            Rational c = e.getValue();
            Monomial m = e.getKey();
            boolean first = sb.isEmpty();
            if (c.signum() < 0) sb.append(first ? "-" : " - ");
            else if (!first) sb.append(" + ");
            Rational abs = c.signum() < 0 ? c.negate() : c;
            String lit = m.toString();
            if (lit.isEmpty()) sb.append(abs.display());
            else {
                if (!abs.isOne()) sb.append(abs.isInteger() ? abs.display() : "(" + abs.display() + ")");
                sb.append(lit);
            }
        }
        return sb.toString();
    }
}
