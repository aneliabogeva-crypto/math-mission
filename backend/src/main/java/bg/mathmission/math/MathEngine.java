package bg.mathmission.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalisation and equivalence checking for the supported Grade 7 domain: rational numbers,
 * single-letter variables, natural exponents, and division (algebraic fractions are kept as
 * numerator/denominator pairs and compared by cross-multiplication).
 */
public final class MathEngine {

    private MathEngine() {}

    /** Normal form: numerator / denominator, both polynomials. */
    public record Normalised(Polynomial numerator, Polynomial denominator) {
        public boolean isPolynomial() {
            return denominator.isConstant();
        }

        public Polynomial asPolynomial() {
            if (!isPolynomial()) {
                throw new MathInputException("NOT_POLYNOMIAL", "Изразът не е цял израз.");
            }
            return numerator.scale(Rational.ONE.divide(denominator.constantValue()));
        }

        @Override
        public String toString() {
            return isPolynomial() ? asPolynomial().toString() : "(" + numerator + ") / (" + denominator + ")";
        }
    }

    public static Normalised normalise(String input) {
        return normalise(ExpressionParser.parse(input));
    }

    public static Normalised normalise(Expr e) {
        return switch (e) {
            case Expr.Num n -> new Normalised(Polynomial.constant(n.value()), Polynomial.ONE);
            case Expr.Var v -> new Normalised(Polynomial.variable(v.name()), Polynomial.ONE);
            case Expr.Group g -> normalise(g.inner());
            case Expr.Neg n -> {
                Normalised a = normalise(n.operand());
                yield new Normalised(a.numerator.negate(), a.denominator);
            }
            case Expr.Add a -> add(normalise(a.left()), normalise(a.right()));
            case Expr.Sub s -> {
                Normalised r = normalise(s.right());
                yield add(normalise(s.left()), new Normalised(r.numerator.negate(), r.denominator));
            }
            case Expr.Mul m -> {
                Normalised a = normalise(m.left());
                Normalised b = normalise(m.right());
                yield simplify(a.numerator.multiply(b.numerator), a.denominator.multiply(b.denominator));
            }
            case Expr.Div d -> {
                Normalised a = normalise(d.left());
                Normalised b = normalise(d.right());
                if (b.numerator.isZero()) {
                    throw new MathInputException("DIVISION_BY_ZERO", "Деление на нула не е позволено.");
                }
                yield simplify(a.numerator.multiply(b.denominator), a.denominator.multiply(b.numerator));
            }
            case Expr.Pow p -> {
                Normalised a = normalise(p.base());
                if (p.exponent() == 0 && a.numerator.isZero()) {
                    throw new MathInputException("ZERO_POWER_ZERO", "0⁰ не е определено.");
                }
                yield new Normalised(a.numerator.pow(p.exponent()), a.denominator.pow(p.exponent()));
            }
        };
    }

    private static Normalised add(Normalised a, Normalised b) {
        if (a.denominator.equals(b.denominator)) {
            return simplify(a.numerator.add(b.numerator), a.denominator);
        }
        return simplify(a.numerator.multiply(b.denominator).add(b.numerator.multiply(a.denominator)),
                a.denominator.multiply(b.denominator));
    }

    /** Cheap simplification: constant or monomial denominators are divided out where exact. */
    private static Normalised simplify(Polynomial num, Polynomial den) {
        if (num.isZero()) return new Normalised(Polynomial.ZERO, Polynomial.ONE);
        if (den.isConstant()) {
            return new Normalised(num.scale(Rational.ONE.divide(den.constantValue())), Polynomial.ONE);
        }
        Polynomial q = num.divideByMonomial(den);
        if (q != null) return new Normalised(q, Polynomial.ONE);
        if (num.equals(den)) return new Normalised(Polynomial.ONE, Polynomial.ONE);
        return new Normalised(num, den);
    }

    /** True if the two expressions are mathematically equivalent (as rational functions). */
    public static boolean equivalent(String a, String b) {
        return equivalent(normalise(a), normalise(b));
    }

    public static boolean equivalent(Normalised a, Normalised b) {
        return a.numerator.multiply(b.denominator).equals(b.numerator.multiply(a.denominator));
    }

    public static Rational evaluate(String input, Map<Character, Rational> values) {
        Normalised n = normalise(input);
        Rational den = n.denominator.evaluate(values);
        if (den.isZero()) {
            throw new MathInputException("DIVISION_BY_ZERO", "При тези стойности изразът няма смисъл (деление на нула).");
        }
        return n.numerator.evaluate(values).divide(den);
    }

    /**
     * A concrete numeric check that shows why two expressions differ, e.g.
     * "при x = 2 твоят израз дава 10, а верният — 12". Returns null if none is found.
     */
    public static String counterexample(String expected, String given) {
        try {
            Normalised e = normalise(expected);
            Normalised g = normalise(given);
            java.util.TreeSet<Character> vars = new java.util.TreeSet<>();
            for (Normalised n : List.of(e, g)) {
                for (Polynomial p : List.of(n.numerator(), n.denominator())) {
                    p.terms().keySet().forEach(m -> vars.addAll(m.powers().keySet()));
                }
            }
            if (vars.isEmpty()) return null;
            int[][] tries = {{2, 3, 5, 7}, {3, 2, 4, 5}, {-1, 2, 3, 4}, {5, -2, 2, 3}, {1, 4, -3, 2}};
            for (int[] t : tries) {
                Map<Character, Rational> vals = new java.util.LinkedHashMap<>();
                int i = 0;
                for (char v : vars) vals.put(v, Rational.of(t[i++ % t.length]));
                Rational ed = e.denominator().evaluate(vals);
                Rational gd = g.denominator().evaluate(vals);
                if (ed.isZero() || gd.isZero()) continue;
                Rational ev = e.numerator().evaluate(vals).divide(ed);
                Rational gv = g.numerator().evaluate(vals).divide(gd);
                if (!ev.equals(gv)) {
                    StringBuilder at = new StringBuilder();
                    vals.forEach((v, q) -> at.append(at.isEmpty() ? "" : ", ").append(v).append(" = ").append(q.display()));
                    return "при " + at + " твоят израз дава " + gv.display() + ", а верният — " + ev.display();
                }
            }
        } catch (MathInputException ignored) {
            // not comparable
        }
        return null;
    }

    // ------------------------------------------------------------------ form checks

    /**
     * Normal (standard) form of a polynomial: a sum of monomials in normal form, with no brackets
     * and with like terms already combined.
     */
    public static boolean isNormalForm(String input) {
        Expr e = ExpressionParser.parse(input);
        Normalised n = normalise(e);
        if (!n.isPolynomial()) return false;
        List<Expr> addends = new ArrayList<>();
        flattenSum(e, addends);
        for (Expr t : addends) {
            if (!isNormalMonomial(t)) return false;
        }
        int nonZero = n.asPolynomial().termCount();
        return addends.size() == Math.max(nonZero, 1);
    }

    private static void flattenSum(Expr e, List<Expr> out) {
        switch (e) {
            case Expr.Add a -> { flattenSum(a.left(), out); flattenSum(a.right(), out); }
            case Expr.Sub s -> { flattenSum(s.left(), out); out.add(s.right()); }
            default -> out.add(e);
        }
    }

    private static boolean isNormalMonomial(Expr t) {
        if (t instanceof Expr.Neg n) t = n.operand();
        List<Expr> factors = new ArrayList<>();
        flattenProduct(t, factors);
        if (!factors.isEmpty() && factors.get(0) instanceof Expr.Neg n0) {
            factors.set(0, n0.operand()); // −a·b: the sign belongs to the coefficient
        }
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < factors.size(); i++) {
            Expr f = unwrapSignedNumber(factors.get(i));
            if (f instanceof Expr.Num) {
                if (i != 0) return false;
            } else if (f instanceof Expr.Var v) {
                if (!seen.add(v.name())) return false;
            } else if (f instanceof Expr.Pow p && p.base() instanceof Expr.Var v && p.exponent() >= 2) {
                if (!seen.add(v.name())) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    private static Expr unwrapSignedNumber(Expr e) {
        return e instanceof Expr.Neg n && n.operand() instanceof Expr.Num num ? num : e;
    }

    private static void flattenProduct(Expr e, List<Expr> out) {
        if (e instanceof Expr.Mul m) {
            flattenProduct(m.left(), out);
            flattenProduct(m.right(), out);
        } else {
            out.add(e);
        }
    }

    /**
     * Factorised form: a product containing at least one bracketed sum, where no bracket can be
     * factorised further by a common factor or a special-product formula within Grade 7 scope.
     */
    public static boolean isFullyFactorised(String input) {
        Expr e = ExpressionParser.parse(input);
        if (e instanceof Expr.Neg n) e = n.operand();
        List<Expr> factors = new ArrayList<>();
        flattenProduct(e, factors);
        boolean hasBracketSum = false;
        for (Expr f : factors) {
            Expr base = f instanceof Expr.Pow p ? p.base() : unwrapSignedNumber(f);
            if (base instanceof Expr.Group g && isSum(g.inner())) {
                hasBracketSum = true;
                Polynomial p = normalise(g.inner()).asPolynomial();
                if (hasCommonFactor(p) || isSpecialProduct(p)) return false;
            } else if (base instanceof Expr.Group g && !(isSum(g.inner()))) {
                // a bracket around a single term is harmless, e.g. (-3)
                continue;
            } else if (!(base instanceof Expr.Num || base instanceof Expr.Var)) {
                return false;
            }
        }
        return hasBracketSum;
    }

    private static boolean isSum(Expr e) {
        return e instanceof Expr.Add || e instanceof Expr.Sub;
    }

    private static boolean hasCommonFactor(Polynomial p) {
        if (p.termCount() < 2) return false;
        BigInteger g = BigInteger.ZERO;
        Monomial common = null;
        for (var e : p.terms().entrySet()) {
            Rational c = e.getValue();
            if (!c.isInteger()) return false;
            g = g.gcd(c.num());
            common = common == null ? e.getKey() : gcd(common, e.getKey());
        }
        return g.compareTo(BigInteger.ONE) > 0 || (common != null && common.degree() > 0);
    }

    private static Monomial gcd(Monomial a, Monomial b) {
        Monomial r = Monomial.ONE;
        for (var e : a.powers().entrySet()) {
            int other = b.powers().getOrDefault(e.getKey(), 0);
            int m = Math.min(e.getValue(), other);
            if (m > 0) r = r.multiply(Monomial.of(e.getKey(), m));
        }
        return r;
    }

    /** Detects a² − b² and a² ± 2ab + b² with monomial a, b and square rational coefficients. */
    static boolean isSpecialProduct(Polynomial p) {
        List<Map.Entry<Monomial, Rational>> t = new ArrayList<>(p.terms().entrySet());
        if (t.size() == 2) {
            var x = t.get(0);
            var y = t.get(1);
            if (x.getValue().signum() == y.getValue().signum()) return false;
            return squareRoot(x.getKey(), x.getValue().signum() < 0 ? x.getValue().negate() : x.getValue()) != null
                    && squareRoot(y.getKey(), y.getValue().signum() < 0 ? y.getValue().negate() : y.getValue()) != null;
        }
        if (t.size() == 3) {
            for (int i = 0; i < 3; i++) {
                for (int j = i + 1; j < 3; j++) {
                    var a = t.get(i);
                    var b = t.get(j);
                    if (a.getValue().signum() <= 0 || b.getValue().signum() <= 0) continue;
                    Polynomial ra = squareRoot(a.getKey(), a.getValue());
                    Polynomial rb = squareRoot(b.getKey(), b.getValue());
                    if (ra == null || rb == null) continue;
                    if (ra.add(rb).pow(2).equals(p) || ra.subtract(rb).pow(2).equals(p)) return true;
                }
            }
        }
        return false;
    }

    private static Polynomial squareRoot(Monomial m, Rational c) {
        BigInteger rn = c.num().sqrt();
        BigInteger rd = c.den().sqrt();
        if (!rn.multiply(rn).equals(c.num()) || !rd.multiply(rd).equals(c.den())) return null;
        Monomial root = Monomial.ONE;
        for (var e : m.powers().entrySet()) {
            if (e.getValue() % 2 != 0) return null;
            root = root.multiply(Monomial.of(e.getKey(), e.getValue() / 2));
        }
        Polynomial r = Polynomial.constant(new Rational(rn, rd));
        return r.multiply(monomialPoly(root));
    }

    private static Polynomial monomialPoly(Monomial m) {
        Polynomial r = Polynomial.ONE;
        for (var e : m.powers().entrySet()) r = r.multiply(Polynomial.variable(e.getKey()).pow(e.getValue()));
        return r;
    }

    // ------------------------------------------------------------------ step checking

    /** Result of checking an ordered chain of transformations. */
    public record StepCheck(boolean allValid, int firstInvalidStep, String message) {}

    /**
     * Checks that every step is equivalent to the previous one. Index 0 is the starting expression.
     * Returns the first step (1-based in the student's list) that breaks equivalence.
     */
    public static StepCheck checkSteps(List<String> steps) {
        if (steps.size() < 2) return new StepCheck(true, -1, "");
        Normalised prev = normalise(steps.get(0));
        for (int i = 1; i < steps.size(); i++) {
            Normalised cur;
            try {
                cur = normalise(steps.get(i));
            } catch (MathInputException ex) {
                return new StepCheck(false, i, "Ред " + i + ": " + ex.getMessage());
            }
            if (!equivalent(prev, cur)) {
                return new StepCheck(false, i, "Ред " + i + " не е равен на предишния ред. Провери този преход.");
            }
            prev = cur;
        }
        return new StepCheck(true, -1, "Всички преходи са верни.");
    }
}
