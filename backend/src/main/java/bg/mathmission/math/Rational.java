package bg.mathmission.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Exact rational number with a normalised, positive denominator. */
public record Rational(BigInteger num, BigInteger den) implements Comparable<Rational> {

    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

    public Rational {
        if (den.signum() == 0) {
            throw new MathInputException("DIVISION_BY_ZERO", "Деление на нула не е позволено.");
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger g = num.gcd(den);
        if (!g.equals(BigInteger.ONE) && g.signum() != 0) {
            num = num.divide(g);
            den = den.divide(g);
        }
        if (num.signum() == 0) {
            den = BigInteger.ONE;
        }
    }

    public static Rational of(long n) {
        return new Rational(BigInteger.valueOf(n), BigInteger.ONE);
    }

    public static Rational of(long n, long d) {
        return new Rational(BigInteger.valueOf(n), BigInteger.valueOf(d));
    }

    /** Parses "2.5", "2,5", "-3", "7/4". Accepts decimal comma and decimal point. */
    public static Rational parse(String text) {
        String s = text.trim().replace(',', '.').replace('−', '-');
        if (s.isEmpty() || s.length() > 40) {
            throw new MathInputException("INVALID_NUMBER", "Въведи число.");
        }
        try {
            int slash = s.indexOf('/');
            if (slash > 0) {
                Rational a = parse(s.substring(0, slash));
                Rational b = parse(s.substring(slash + 1));
                return a.divide(b);
            }
            BigDecimal bd = new BigDecimal(s);
            BigInteger unscaled = bd.unscaledValue();
            int scale = bd.scale();
            return scale >= 0
                    ? new Rational(unscaled, BigInteger.TEN.pow(scale))
                    : new Rational(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
        } catch (NumberFormatException e) {
            throw new MathInputException("INVALID_NUMBER", "Въведи число, например 2,5 или 3/4.");
        }
    }

    public Rational add(Rational o) {
        return new Rational(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den));
    }

    public Rational subtract(Rational o) {
        return add(o.negate());
    }

    public Rational multiply(Rational o) {
        return new Rational(num.multiply(o.num), den.multiply(o.den));
    }

    public Rational divide(Rational o) {
        if (o.isZero()) {
            throw new MathInputException("DIVISION_BY_ZERO", "Деление на нула не е позволено.");
        }
        return new Rational(num.multiply(o.den), den.multiply(o.num));
    }

    public Rational negate() {
        return new Rational(num.negate(), den);
    }

    public Rational pow(int e) {
        return new Rational(num.pow(e), den.pow(e));
    }

    public boolean isZero() {
        return num.signum() == 0;
    }

    public boolean isOne() {
        return num.equals(BigInteger.ONE) && den.equals(BigInteger.ONE);
    }

    public boolean isInteger() {
        return den.equals(BigInteger.ONE);
    }

    public int signum() {
        return num.signum();
    }

    @Override
    public int compareTo(Rational o) {
        return num.multiply(o.den).compareTo(o.num.multiply(den));
    }

    public double toDouble() {
        return new BigDecimal(num).divide(new BigDecimal(den), MathContext.DECIMAL64).doubleValue();
    }

    /** Bulgarian-style display: decimal comma for terminating decimals, otherwise a/b. */
    public String display() {
        if (isInteger()) {
            return num.toString();
        }
        BigInteger d = den;
        while (d.mod(BigInteger.TWO).signum() == 0) d = d.divide(BigInteger.TWO);
        while (d.mod(BigInteger.valueOf(5)).signum() == 0) d = d.divide(BigInteger.valueOf(5));
        if (d.equals(BigInteger.ONE)) {
            return new BigDecimal(num).divide(new BigDecimal(den)).stripTrailingZeros().toPlainString().replace('.', ',');
        }
        return num + "/" + den;
    }

    @Override
    public String toString() {
        return isInteger() ? num.toString() : num + "/" + den;
    }
}
