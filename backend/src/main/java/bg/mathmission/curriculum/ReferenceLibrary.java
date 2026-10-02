package bg.mathmission.curriculum;

import java.util.List;
import java.util.Locale;

/**
 * Formula and concept reference (US-STU-11). Every entry has meaning, conditions, a worked
 * example and a common mistake, plus a plain-language reading for screen readers.
 */
public final class ReferenceLibrary {

    private ReferenceLibrary() {}

    public static final String ACADEMIC_YEAR = "2026/2027";

    public record Entry(String id, String path, String kind, String title, String formula, String spoken,
                        String meaning, String conditions, String example, String commonMistake, String lessonKey,
                        String academicYear) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry("numeric-value", "A", "DEFINITION", "Числена стойност на израз", "",
                    "Числена стойност е числото, което се получава след заместване на променливите.",
                    "Заменяме всяка променлива с дадената стойност и пресмятаме по реда на действията.",
                    "Стойностите са дадени за всички променливи; знаменателят не става нула.",
                    "2a − 3b при a = −1, b = 2: 2·(−1) − 3·2 = −2 − 6 = −8.",
                    "Отрицателно число се замества в скоби: (−1)², а не −1².", "A3", ACADEMIC_YEAR),
            new Entry("order", "A", "RULE", "Ред на действията", "( ) → степени → · и : → + и −",
                    "Първо скоби, после степени, после умножение и деление, накрая събиране и изваждане.",
                    "Действията от една и съща степен се извършват отляво надясно.",
                    "Важи за всички числови изрази и изрази с променливи.",
                    "3 + 2·4² = 3 + 2·16 = 35.", "3 + 2·4² ≠ 5·16: умножението не е преди степенуването.", "A4", ACADEMIC_YEAR),
            new Entry("monomial", "B", "DEFINITION", "Едночлен", "k·xᵃ·yᵇ…",
                    "Едночленът е произведение на число и степени на променливи.",
                    "Числото е коефициент, а произведението на променливите е буквена част.",
                    "Степенните показатели са естествени числа или нула.",
                    "−3x²y: коефициент −3, буквена част x²y, степен 2 + 1 = 3.",
                    "Коефициентът на −x² е −1, не 0.", "B1", ACADEMIC_YEAR),
            new Entry("like-terms", "B", "RULE", "Събиране на подобни едночлени", "a·M + b·M = (a + b)·M",
                    "a по M плюс b по M е равно на сбора на a и b, умножен по M.",
                    "Подобните едночлени имат еднаква буквена част; събираме само коефициентите.",
                    "Буквените части трябва да са напълно еднакви (същите променливи със същите степени).",
                    "5x²y − 2x²y = 3x²y.", "3x + 2x² не може да се събере: буквените части са различни.", "B3", ACADEMIC_YEAR),
            new Entry("product-powers", "B", "RULE", "Умножение на степени с равни основи", "aᵐ · aⁿ = aᵐ⁺ⁿ",
                    "a на степен m по a на степен n е равно на a на степен m плюс n.",
                    "При умножение показателите се събират.",
                    "Основите са равни.", "x³ · x⁴ = x⁷.", "x³ · x⁴ ≠ x¹²: показателите се събират, не се умножават.", "B4", ACADEMIC_YEAR),
            new Entry("power-of-product", "B", "RULE", "Степен на произведение", "(a·b)ⁿ = aⁿ·bⁿ",
                    "a по b, на степен n, е равно на a на степен n по b на степен n.",
                    "Всеки множител се степенува.", "n е естествено число.",
                    "(−2x²)³ = −8x⁶.", "(2x)² ≠ 2x²: и числото се степенува.", "B5", ACADEMIC_YEAR),
            new Entry("square-sum", "C", "FORMULA", "Квадрат на сбор", "(a + b)² = a² + 2ab + b²",
                    "a плюс b на квадрат е равно на a на квадрат плюс две a b плюс b на квадрат.",
                    "Квадратът на двучлен има три члена: два квадрата и удвоено произведение.",
                    "a и b са произволни изрази.", "(x + 3)² = x² + 6x + 9.",
                    "(x + 3)² ≠ x² + 9: липсва удвоеното произведение 6x.", "C6", ACADEMIC_YEAR),
            new Entry("square-diff", "C", "FORMULA", "Квадрат на разлика", "(a − b)² = a² − 2ab + b²",
                    "a минус b на квадрат е равно на a на квадрат минус две a b плюс b на квадрат.",
                    "Само удвоеното произведение е със знак минус.", "a и b са произволни изрази.",
                    "(2x − 1)² = 4x² − 4x + 1.", "Последният член +b² винаги е положителен.", "C6", ACADEMIC_YEAR),
            new Entry("diff-squares", "C", "FORMULA", "Разлика на квадрати", "(a − b)(a + b) = a² − b²",
                    "a минус b, по a плюс b, е равно на a на квадрат минус b на квадрат.",
                    "Средните членове се унищожават.", "a и b са произволни изрази.",
                    "(x − 5)(x + 5) = x² − 25.", "x² + 25 не се разлага с тази формула — трябва разлика.", "C7", ACADEMIC_YEAR),
            new Entry("cube-sum", "C", "FORMULA", "Куб на сбор", "(a + b)³ = a³ + 3a²b + 3ab² + b³",
                    "a плюс b на трета степен е a на трета плюс три a квадрат b плюс три a b квадрат плюс b на трета.",
                    "Коефициентите са 1, 3, 3, 1.", "a и b са произволни изрази.",
                    "(x + 1)³ = x³ + 3x² + 3x + 1.", "(x + 1)³ ≠ x³ + 1.", "C8", ACADEMIC_YEAR),
            new Entry("sum-cubes", "C", "FORMULA", "Сбор и разлика на кубове", "a³ ± b³ = (a ± b)(a² ∓ ab + b²)",
                    "a на трета плюс или минус b на трета е равно на a плюс или минус b, по a квадрат минус или плюс a b плюс b квадрат.",
                    "Вторият множител е „непълен квадрат“.", "a и b са произволни изрази.",
                    "x³ − 8 = (x − 2)(x² + 2x + 4).", "Във втория множител е ab, а не 2ab.", "C9", ACADEMIC_YEAR),
            new Entry("common-factor", "C", "METHOD", "Изнасяне на общ множител", "ab + ac = a(b + c)",
                    "a b плюс a c е равно на a по скоба b плюс c.",
                    "Изнасяме най-големия общ множител на всички членове.", "Множителят е общ за всеки член.",
                    "6x² − 9x = 3x(2x − 3).", "Проверявай чрез умножение обратно: 3x·2x − 3x·3 = 6x² − 9x.", "C11", ACADEMIC_YEAR));

    public static List<Entry> search(String query, String path) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return ENTRIES.stream()
                .filter(e -> path == null || path.isBlank() || e.path().equals(path))
                .filter(e -> q.isEmpty() || (e.title() + " " + e.meaning() + " " + e.formula() + " " + e.example())
                        .toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }
}
