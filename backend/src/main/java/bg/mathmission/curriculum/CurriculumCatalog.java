package bg.mathmission.curriculum;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The MVP learning sequence (spec 4.1): 28 lesson stops in four paths, mapped to the Grade 7
 * curriculum area "integral expressions". Titles are original wording; lesson content lives in
 * the content module and only published versions are visible to students.
 */
public final class CurriculumCatalog {

    private CurriculumCatalog() {}

    public enum Path {
        A("Рационални изрази", "blue"),
        B("Едночлени", "purple"),
        C("Многочлени", "coral"),
        D("Обобщителен преговор", "gold");

        public final String titleBg;
        public final String colour;

        Path(String titleBg, String colour) {
            this.titleBg = titleBg;
            this.colour = colour;
        }
    }

    public record LessonStop(String key, Path path, int order, String title, String skill,
                             List<String> prerequisites, String learningOutcome) {}

    private static LessonStop s(String key, Path p, int order, String title, String skill, String outcome, String... prereq) {
        return new LessonStop(key, p, order, title, skill, List.of(prereq), outcome);
    }

    public static final List<LessonStop> LESSONS = List.of(
            s("A1", Path.A, 1, "Числови изрази и изрази с променливи", "A.expressions", "Разпознава числов израз и израз с променливи"),
            s("A2", Path.A, 2, "Променливи и константи. Цели и дробни изрази", "A.rational-integral", "Различава цели и дробни рационални изрази", "A1"),
            s("A3", Path.A, 3, "Заместване и числена стойност на израз", "A.substitution", "Намира числена стойност на израз при дадени стойности на променливите", "A2"),
            s("A4", Path.A, 4, "Ред на действията в изрази с променливи", "A.order-of-operations", "Спазва реда на действията при пресмятане", "A3"),
            s("A5", Path.A, 5, "Съставяне на изрази по условие", "A.applications", "Съставя израз по текстово условие", "A4"),

            s("B1", Path.B, 1, "Едночлен. Коефициент и буквена част", "B.definition", "Разпознава едночлен и определя коефициента и буквената част", "A3"),
            s("B2", Path.B, 2, "Нормален вид и степен на едночлен", "B.normal-form", "Записва едночлен в нормален вид и определя степента му", "B1"),
            s("B3", Path.B, 3, "Подобни едночлени. Събиране и изваждане", "B.like-terms", "Разпознава подобни едночлени и ги събира и изважда", "B2"),
            s("B4", Path.B, 4, "Умножение на едночлени", "B.multiplication", "Умножава едночлени", "B2"),
            s("B5", Path.B, 5, "Степенуване на едночлен", "B.powers", "Степенува едночлен с естествен степенен показател", "B4"),
            s("B6", Path.B, 6, "Деление на едночлен с едночлен", "B.division", "Дели едночлен на едночлен, когато частното е едночлен", "B5"),
            s("B7", Path.B, 7, "Едночлени: обобщение и приложения", "B.applications", "Прилага действията с едночлени в задачи", "B3", "B6"),

            s("C1", Path.C, 1, "Многочлен. Членове и коефициенти", "C.definition", "Разпознава многочлен, неговите членове и коефициенти", "B3"),
            s("C2", Path.C, 2, "Нормален вид и степен на многочлен", "C.normal-form", "Привежда многочлен в нормален вид и определя степента му", "C1"),
            s("C3", Path.C, 3, "Събиране и изваждане на многочлени", "C.add-subtract", "Събира и изважда многочлени, като разкрива скоби", "C2"),
            s("C4", Path.C, 4, "Умножение на многочлен с едночлен", "C.multiply-monomial", "Умножава многочлен с едночлен", "C3", "B4"),
            s("C5", Path.C, 5, "Умножение на многочлени", "C.multiply", "Умножава многочлени", "C4"),
            s("C6", Path.C, 6, "Квадрат на двучлен", "C.square-binomial", "Прилага формулите (a ± b)²", "C5"),
            s("C7", Path.C, 7, "Разлика на квадрати", "C.difference-squares", "Прилага формулата (a − b)(a + b) = a² − b²", "C5"),
            s("C8", Path.C, 8, "Куб на двучлен", "C.cube-binomial", "Прилага формулите (a ± b)³", "C6"),
            s("C9", Path.C, 9, "Сбор и разлика на кубове", "C.sum-cubes", "Прилага формулите a³ ± b³", "C8"),
            s("C10", Path.C, 10, "Тъждества", "C.identities", "Доказва тъждества чрез тъждествени преобразувания", "C7", "C9"),
            s("C11", Path.C, 11, "Разлагане чрез изнасяне на общ множител", "C.common-factor", "Разлага многочлен чрез изнасяне на общ множител", "C4"),
            s("C12", Path.C, 12, "Разлагане чрез формули и групиране", "C.factor-formulas", "Разлага многочлен чрез формули и групиране", "C11", "C7"),
            s("C13", Path.C, 13, "Числена стойност и приложения на многочлени", "C.applications", "Използва преобразувания за пресмятане на числена стойност", "C12"),

            s("D1", Path.D, 1, "Карта на понятията и формулите", "D.concept-map", "Свързва понятията от трите теми", "B7", "C13"),
            s("D2", Path.D, 2, "Типични грешки и как да се проверяваме", "D.checking", "Открива и поправя типични грешки", "D1"),
            s("D3", Path.D, 3, "Смесена практика", "D.mixed", "Решава смесени задачи от трите теми", "D2"));

    private static final Map<String, LessonStop> BY_KEY =
            LESSONS.stream().collect(Collectors.toMap(LessonStop::key, Function.identity()));

    public static Optional<LessonStop> lesson(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Skill code to the lesson that teaches it (for "link to theory" in feedback). */
    public static Optional<LessonStop> lessonForSkill(String skill) {
        return LESSONS.stream().filter(l -> l.skill().equals(skill)).findFirst();
    }

    public static String skillTitle(String skill) {
        return lessonForSkill(skill).map(LessonStop::title).orElse(skill);
    }
}
