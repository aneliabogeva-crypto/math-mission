package bg.mathmission.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradingScaleTest {

    @ParameterizedTest
    @CsvSource({"0,2", "39.9,2", "40,3", "54.9,3", "55,4", "69.9,4", "70,5", "84.9,5", "85,6", "100,6"})
    void defaultThresholdsMatchSpecification(double percent, int grade) {
        assertThat(GradingScale.defaultScale().bandFor(percent).grade()).isEqualTo(grade);
    }
}
