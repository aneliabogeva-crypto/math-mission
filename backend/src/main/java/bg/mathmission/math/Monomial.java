package bg.mathmission.math;

import java.util.Map;
import java.util.TreeMap;

/** Literal part of a monomial: variables with positive integer exponents. */
public final class Monomial implements Comparable<Monomial> {

    public static final Monomial ONE = new Monomial(new TreeMap<>());

    private final TreeMap<Character, Integer> powers;

    private Monomial(TreeMap<Character, Integer> powers) {
        this.powers = powers;
    }

    public static Monomial of(char v, int e) {
        TreeMap<Character, Integer> p = new TreeMap<>();
        if (e > 0) p.put(v, e);
        return new Monomial(p);
    }

    public Map<Character, Integer> powers() {
        return java.util.Collections.unmodifiableMap(powers);
    }

    public int degree() {
        return powers.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Monomial multiply(Monomial o) {
        TreeMap<Character, Integer> p = new TreeMap<>(powers);
        o.powers.forEach((v, e) -> p.merge(v, e, Integer::sum));
        return new Monomial(p);
    }

    /** Returns null if o does not divide this monomial. */
    public Monomial divide(Monomial o) {
        TreeMap<Character, Integer> p = new TreeMap<>(powers);
        for (var e : o.powers.entrySet()) {
            int have = p.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) return null;
            if (have == e.getValue()) p.remove(e.getKey()); else p.put(e.getKey(), have - e.getValue());
        }
        return new Monomial(p);
    }

    public Rational evaluate(Map<Character, Rational> values) {
        Rational r = Rational.ONE;
        for (var e : powers.entrySet()) {
            Rational v = values.get(e.getKey());
            if (v == null) {
                throw new MathInputException("MISSING_VALUE", "Липсва стойност за " + e.getKey() + ".");
            }
            r = r.multiply(v.pow(e.getValue()));
        }
        return r;
    }

    /** Orders by degree, then lexicographically, so that descending order is the textbook order. */
    @Override
    public int compareTo(Monomial o) {
        int d = Integer.compare(degree(), o.degree());
        if (d != 0) return d;
        return o.toString().compareTo(toString());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Monomial m && m.powers.equals(powers);
    }

    @Override
    public int hashCode() {
        return powers.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        powers.forEach((v, e) -> {
            sb.append(v);
            if (e > 1) sb.append('^').append(e);
        });
        return sb.toString();
    }
}
