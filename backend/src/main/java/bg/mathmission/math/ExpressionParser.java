package bg.mathmission.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the restricted Grade 7 grammar:
 *
 * <pre>
 *   expr    := term (('+' | '-') term)*
 *   term    := implicit (('*' | '·' | ':' | '/') implicit)*
 *   implicit:= unary (power)*          // juxtaposition, binds tighter
 *   unary   := ('-' | '+') unary | power
 *   power   := primary ('^' INT | SUPERSCRIPT)*
 *   primary := NUMBER | VARIABLE | '(' expr ')' | '[' expr ']'
 * </pre>
 *
 * Variables are single Latin letters; Cyrillic look-alikes (а, х, у, ...) are mapped to Latin.
 * Hard limits on length, token count, nesting depth and exponents protect the server.
 */
public final class ExpressionParser {

    public static final int MAX_INPUT_LENGTH = 300;
    public static final int MAX_TOKENS = 200;
    public static final int MAX_DEPTH = 40;
    public static final int MAX_EXPONENT = 12;

    private enum Kind { NUM, VAR, OP, LPAREN, RPAREN, POW_SUPER, END }

    private record Token(Kind kind, String text, int pos) {}

    private final List<Token> tokens;
    private int index;
    private int depth;

    private ExpressionParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Expr parse(String input) {
        if (input == null || input.isBlank()) {
            throw new MathInputException("EMPTY", "Отговорът е празен.");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            throw new MathInputException("TOO_LONG", "Изразът е твърде дълъг.");
        }
        ExpressionParser p = new ExpressionParser(tokenize(input));
        Expr e = p.parseExpr();
        if (p.peek().kind != Kind.END) {
            throw new MathInputException("UNEXPECTED", "Неочакван символ „" + p.peek().text + "“.");
        }
        return e;
    }

    // ---------------------------------------------------------------- tokenizer

    private static char normalise(char c) {
        return switch (c) {
            case 'а', 'А' -> 'a';
            case 'в', 'В' -> 'b';
            case 'с', 'С' -> 'c';
            case 'е', 'Е' -> 'e';
            case 'к', 'К' -> 'k';
            case 'м', 'М' -> 'm';
            case 'о', 'О' -> 'o';
            case 'р', 'Р' -> 'p';
            case 'т', 'Т' -> 't';
            case 'х', 'Х' -> 'x';
            case 'у', 'У' -> 'y';
            case '−', '–', '—' -> '-';
            case '·', '×', '⋅', '∙' -> '*';
            case '÷' -> ':';
            default -> c;
        };
    }

    private static int superscriptDigit(char c) {
        return switch (c) {
            case '⁰' -> 0; case '¹' -> 1; case '²' -> 2; case '³' -> 3; case '⁴' -> 4;
            case '⁵' -> 5; case '⁶' -> 6; case '⁷' -> 7; case '⁸' -> 8; case '⁹' -> 9;
            default -> -1;
        };
    }

    private static List<Token> tokenize(String raw) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            char c = normalise(raw.charAt(i));
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (Character.isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                boolean seenSep = false;
                while (i < raw.length()) {
                    char d = raw.charAt(i);
                    if (Character.isDigit(d)) {
                        sb.append(d);
                    } else if ((d == '.' || d == ',') && !seenSep && i + 1 < raw.length()
                            && Character.isDigit(raw.charAt(i + 1))) {
                        sb.append('.');
                        seenSep = true;
                    } else {
                        break;
                    }
                    i++;
                }
                if (sb.length() > 18) {
                    throw new MathInputException("NUMBER_TOO_LONG", "Числото е твърде дълго.");
                }
                out.add(new Token(Kind.NUM, sb.toString(), start));
            } else if (superscriptDigit(c) >= 0) {
                StringBuilder sb = new StringBuilder();
                while (i < raw.length() && superscriptDigit(raw.charAt(i)) >= 0) {
                    sb.append(superscriptDigit(raw.charAt(i)));
                    i++;
                }
                out.add(new Token(Kind.POW_SUPER, sb.toString(), start));
            } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                out.add(new Token(Kind.VAR, String.valueOf(Character.toLowerCase(c)), start));
                i++;
            } else if (c == '(' || c == '[') {
                out.add(new Token(Kind.LPAREN, String.valueOf(c), start));
                i++;
            } else if (c == ')' || c == ']') {
                out.add(new Token(Kind.RPAREN, String.valueOf(c), start));
                i++;
            } else if ("+-*/:^".indexOf(c) >= 0) {
                out.add(new Token(Kind.OP, String.valueOf(c), start));
                i++;
            } else {
                throw new MathInputException("UNSUPPORTED_SYMBOL",
                        "Символът „" + raw.charAt(i) + "“ не се поддържа.");
            }
            if (out.size() > MAX_TOKENS) {
                throw new MathInputException("TOO_COMPLEX", "Изразът е твърде сложен.");
            }
        }
        out.add(new Token(Kind.END, "край", raw.length()));
        return out;
    }

    // ---------------------------------------------------------------- parser

    private Token peek() {
        return tokens.get(index);
    }

    private Token next() {
        return tokens.get(index++);
    }

    private boolean peekOp(String op) {
        Token t = peek();
        return t.kind == Kind.OP && t.text.equals(op);
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw new MathInputException("TOO_DEEP", "Изразът има твърде много вложени скоби.");
        }
    }

    private void leave() {
        depth--;
    }

    private Expr parseExpr() {
        enter();
        Expr left = parseTerm();
        while (peekOp("+") || peekOp("-")) {
            String op = next().text;
            Expr right = parseTerm();
            left = op.equals("+") ? new Expr.Add(left, right) : new Expr.Sub(left, right);
        }
        leave();
        return left;
    }

    private boolean startsPrimary(Token t) {
        return t.kind == Kind.NUM || t.kind == Kind.VAR || t.kind == Kind.LPAREN;
    }

    private Expr parseTerm() {
        Expr left = parseImplicitProduct();
        while (true) {
            if (peekOp("*")) {
                next();
                left = new Expr.Mul(left, parseImplicitProduct(), false);
            } else if (peekOp("/") || peekOp(":")) {
                next();
                left = new Expr.Div(left, parseImplicitProduct());
            } else {
                return left;
            }
        }
    }

    /**
     * Implicit multiplication binds tighter than explicit operators, matching school notation:
     * 6x^3 : 2x means 6x^3 divided by the monomial 2x.
     */
    private Expr parseImplicitProduct() {
        Expr left = parseUnary();
        while (startsPrimary(peek())) {
            if (peek().kind == Kind.NUM) {
                throw new MathInputException("NUMBER_AFTER_TERM",
                        "Число след буква или скоба е двусмислено. За степен използвай ^, напр. x^2.");
            }
            left = new Expr.Mul(left, parsePower(), true);
        }
        return left;
    }

    private Expr parseUnary() {
        if (peekOp("-")) {
            next();
            enter();
            Expr e = new Expr.Neg(parseUnary());
            leave();
            return e;
        }
        if (peekOp("+")) {
            next();
            return parseUnary();
        }
        return parsePower();
    }

    private Expr parsePower() {
        Expr base = parsePrimary();
        while (true) {
            if (peekOp("^")) {
                next();
                base = new Expr.Pow(base, parseExponent());
            } else if (peek().kind == Kind.POW_SUPER) {
                base = new Expr.Pow(base, checkExponent(next().text));
            } else {
                return base;
            }
        }
    }

    private int parseExponent() {
        Token t = next();
        if (t.kind == Kind.NUM) {
            return checkExponent(t.text);
        }
        if (t.kind == Kind.LPAREN && peek().kind == Kind.NUM) {
            int e = checkExponent(next().text);
            if (next().kind != Kind.RPAREN) {
                throw new MathInputException("BAD_EXPONENT", "Степенният показател трябва да е цяло число.");
            }
            return e;
        }
        throw new MathInputException("BAD_EXPONENT", "Степенният показател трябва да е естествено число, напр. x^3.");
    }

    private int checkExponent(String text) {
        if (text.contains(".")) {
            throw new MathInputException("BAD_EXPONENT", "Степенният показател трябва да е цяло число.");
        }
        int e;
        try {
            e = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new MathInputException("BAD_EXPONENT", "Степенният показател е твърде голям.");
        }
        if (e > MAX_EXPONENT) {
            throw new MathInputException("BAD_EXPONENT", "Степенният показател е твърде голям (най-много " + MAX_EXPONENT + ").");
        }
        return e;
    }

    private Expr parsePrimary() {
        Token t = next();
        switch (t.kind) {
            case NUM:
                return new Expr.Num(Rational.parse(t.text));
            case VAR:
                return new Expr.Var(t.text.charAt(0));
            case LPAREN: {
                enter();
                Expr inner = parseExpr();
                Token close = next();
                if (close.kind != Kind.RPAREN) {
                    throw new MathInputException("UNBALANCED", "Липсва затваряща скоба.");
                }
                leave();
                return new Expr.Group(inner);
            }
            case RPAREN:
                throw new MathInputException("UNBALANCED", "Има затваряща скоба без отваряща.");
            case END:
                throw new MathInputException("INCOMPLETE", "Изразът е незавършен.");
            default:
                throw new MathInputException("UNEXPECTED", "Неочакван символ „" + t.text + "“.");
        }
    }
}
