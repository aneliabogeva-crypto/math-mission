package bg.mathmission.assessment;

import java.util.Comparator;
import java.util.List;

/**
 * Configurable educational grade scale (2 to 6). Thresholds are shown to the student before a
 * test starts. The result is never presented as an official school or state grade.
 */
public record GradingScale(List<Band> bands) {

    public record Band(double minPercent, int grade, String labelBg) {}

    public static GradingScale defaultScale() {
        return new GradingScale(List.of(
                new Band(0, 2, "Слаб"),
                new Band(40, 3, "Среден"),
                new Band(55, 4, "Добър"),
                new Band(70, 5, "Много добър"),
                new Band(85, 6, "Отличен")));
    }

    public GradingScale {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("A grading scale needs at least one band");
        }
        bands = bands.stream().sorted(Comparator.comparingDouble(Band::minPercent)).toList();
        if (bands.get(0).minPercent() != 0) {
            throw new IllegalArgumentException("The lowest band must start at 0%");
        }
        for (Band b : bands) {
            if (b.grade() < 2 || b.grade() > 6) {
                throw new IllegalArgumentException("Grades must be between 2 and 6");
            }
        }
    }

    public Band bandFor(double percent) {
        Band result = bands.get(0);
        for (Band b : bands) {
            if (percent + 1e-9 >= b.minPercent()) result = b;
        }
        return result;
    }
}
