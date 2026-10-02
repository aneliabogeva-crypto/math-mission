package bg.mathmission.math;

/**
 * Abstract syntax tree for the restricted expression grammar. Student input is only ever
 * turned into these immutable nodes; it is never evaluated as code.
 */
public sealed interface Expr {

    record Num(Rational value) implements Expr {}

    record Var(char name) implements Expr {}

    record Neg(Expr operand) implements Expr {}

    record Add(Expr left, Expr right) implements Expr {}

    record Sub(Expr left, Expr right) implements Expr {}

    record Mul(Expr left, Expr right, boolean implicit) implements Expr {}

    record Div(Expr left, Expr right) implements Expr {}

    record Pow(Expr base, int exponent) implements Expr {}

    /** Explicit brackets are kept so that form checks (e.g. "is it factorised?") can see them. */
    record Group(Expr inner) implements Expr {}
}
