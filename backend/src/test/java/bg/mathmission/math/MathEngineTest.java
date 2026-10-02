package bg.mathmission.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class MathEngineTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2(x + 1)        | 2x + 2",
            "2x + 2          | 2 + 2x",
            "(a + b)^2       | a^2 + 2ab + b^2",
            "(a - b)(a + b)  | a² - b²",
            "(х - 3)(х + 3)  | x^2 - 9",          // Cyrillic х is accepted
            "6x^3 : 2x       | 3x^2",             // implicit product binds tighter than ':'
            "(x^2 - 1)/(x - 1) | x + 1",
            "2,5x            | 5x/2",
            "2.5x            | 2,5x",
            "-2x·3y          | -6xy",
            "(2a)^3          | 8a^3",
            "(-2a^3b)^3      | -8a^9b^3",
            "(x+1)^3         | x^3 + 3x^2 + 3x + 1",
            "x^3 - 8         | (x - 2)(x^2 + 2x + 4)",
            "[2 - (3 - x)]   | x - 1"})
    void recognisesEquivalentForms(String a, String b) {
        assertThat(MathEngine.equivalent(a, b)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "x + x      | x^2",
            "(a + b)^2  | a^2 + b^2",
            "3x + 2x    | 5x^2",
            "-(x - 1)   | -x - 1",
            "2^3        | 6"})
    void rejectsTypicalMistakes(String a, String b) {
        assertThat(MathEngine.equivalent(a, b)).isFalse();
    }

    @Test
    void normalFormCheck() {
        assertThat(MathEngine.isNormalForm("3x^2 - 2x + 1")).isTrue();
        assertThat(MathEngine.isNormalForm("-2xy + 5")).isTrue();
        assertThat(MathEngine.isNormalForm("-7")).isTrue();
        assertThat(MathEngine.isNormalForm("2x + 3x")).isFalse();
        assertThat(MathEngine.isNormalForm("x(x + 1)")).isFalse();
        assertThat(MathEngine.isNormalForm("3x·2")).isFalse();
    }

    @Test
    void factorisationCheck() {
        assertThat(MathEngine.isFullyFactorised("2(x + 1)")).isTrue();
        assertThat(MathEngine.isFullyFactorised("-2(x + 1)")).isTrue();
        assertThat(MathEngine.isFullyFactorised("(x - 1)(x + 1)")).isTrue();
        assertThat(MathEngine.isFullyFactorised("(x + 3)^2")).isTrue();
        assertThat(MathEngine.isFullyFactorised("2(2x + 2)")).isFalse();      // common factor left inside
        assertThat(MathEngine.isFullyFactorised("3x(x^2 - 4)")).isFalse();    // difference of squares left
        assertThat(MathEngine.isFullyFactorised("x(x^2 + 2x + 1)")).isFalse(); // perfect square left
        assertThat(MathEngine.isFullyFactorised("2x + 2")).isFalse();
    }

    @Test
    void evaluatesWithDecimalComma() {
        Rational v = MathEngine.evaluate("2a^2 - 3b", Map.of('a', Rational.parse("-1,5"), 'b', Rational.of(2)));
        assertThat(v.display()).isEqualTo("-1,5");
    }

    @Test
    void stepCheckFindsTheBrokenStep() {
        var r = MathEngine.checkSteps(List.of("(x+1)^2", "x^2 + 2x + 1", "x^2 + x + 1"));
        assertThat(r.allValid()).isFalse();
        assertThat(r.firstInvalidStep()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x2", "2^x", "((((", "x$", "1/0", "x^99", "System.exit(0)", ""})
    void rejectsUnsupportedOrDangerousInput(String input) {
        assertThatThrownBy(() -> MathEngine.normalise(input)).isInstanceOf(MathInputException.class);
    }

    @Test
    void enforcesComplexityLimits() {
        String deep = "(".repeat(60) + "x" + ")".repeat(60);
        assertThatThrownBy(() -> MathEngine.normalise(deep)).isInstanceOf(MathInputException.class);
        assertThatThrownBy(() -> MathEngine.normalise("(a+b+c+d+e+f)^12")).isInstanceOf(MathInputException.class);
        assertThatThrownBy(() -> MathEngine.normalise("x".repeat(400))).isInstanceOf(MathInputException.class);
    }

    @Test
    void rationalParsingAcceptsCommaAndPoint() {
        assertThat(Rational.parse("2,5")).isEqualTo(Rational.of(5, 2));
        assertThat(Rational.parse("2.5")).isEqualTo(Rational.of(5, 2));
        assertThat(Rational.parse("-3/4")).isEqualTo(Rational.of(-3, 4));
        assertThat(Rational.of(1, 3).display()).isEqualTo("1/3");
    }
}
