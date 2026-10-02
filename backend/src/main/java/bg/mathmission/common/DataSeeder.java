package bg.mathmission.common;

import bg.mathmission.assessment.GradingScale;
import bg.mathmission.assessment.TestAuthoringService;
import bg.mathmission.assessment.TestBlueprint;
import bg.mathmission.assessment.TestBlueprintRepository;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.content.ContentService;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.Lesson;
import bg.mathmission.content.LessonModel;
import bg.mathmission.content.Question;
import bg.mathmission.content.QuestionDraft;
import bg.mathmission.content.QuestionRepository;
import bg.mathmission.curriculum.CurriculumCatalog;
import bg.mathmission.identity.Role;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads demo content through the real authoring and review workflow, and (dev only) creates
 * staff demo accounts. Seeded items are approved by a "demo reviewer" account and are clearly
 * marked as needing a real mathematics review before production use.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_REVIEW_NOTE =
            "Демо преглед: съдържанието трябва да бъде проверено от учител по математика преди реална употреба.";

    record SeedQuestion(String key, QuestionDraft draft) {}

    record SeedLesson(String lessonKey, LessonModel content) {}

    record SeedTest(String testKey, String title, TestDefinition.Kind kind, String path, String blueprint, int timeLimitMin,
                    TestDefinition.HintPolicy hintPolicy, TestDefinition.ReviewMoment reviewMoment, List<String> questionKeys) {}

    record SeedBlueprint(String name, String academicYear, int timeLimitMin, Map<String, Integer> composition) {}

    record Seed(List<SeedQuestion> questions, List<SeedLesson> lessons, List<SeedTest> tests, List<SeedBlueprint> blueprints) {}

    private final AppProperties props;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final ContentService content;
    private final QuestionRepository questions;
    private final bg.mathmission.content.LessonRepository lessons;
    private final TestBlueprintRepository blueprints;
    private final TestDefinitionRepository tests;
    private final TestAuthoringService authoring;
    private final TransactionTemplate tx;

    public DataSeeder(AppProperties props, UserAccountRepository users, PasswordEncoder encoder, ContentService content,
                      QuestionRepository questions, bg.mathmission.content.LessonRepository lessons, TestBlueprintRepository blueprints, TestDefinitionRepository tests,
                      TestAuthoringService authoring, TransactionTemplate tx) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.content = content;
        this.questions = questions;
        this.lessons = lessons;
        this.blueprints = blueprints;
        this.tests = tests;
        this.authoring = authoring;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        tx.executeWithoutResult(s -> seedAccounts());
        // Incremental: content missing from this database (new questions, lessons, tests) is added on start-up.
        if (props.seed() != null && props.seed().enabled()) {
            Seed seed;
            try (InputStream in = new ClassPathResource("seed/content.json").getInputStream()) {
                seed = Json.MAPPER.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), new TypeReference<Seed>() {});
            }
            int[] added = new int[3];
            tx.executeWithoutResult(s -> seedContent(seed, added));
            if (added[0] + added[1] + added[2] > 0) {
                log.info("Seed content added: {} questions, {} lessons, {} tests", added[0], added[1], added[2]);
            }
        }
    }

    private UserAccount system(String username, Role role, String name) {
        return users.findByUsername(username).orElseGet(() -> users.save(UserAccount.staff(role, username, name, null)));
    }

    private void seedAccounts() {
        system("system-seed-author", Role.AUTHOR, "Демо автор");
        system("system-seed-reviewer", Role.REVIEWER, "Демо рецензент");
        AppProperties.DevAccounts dev = props.devAccountsOrDisabled();
        if (!dev.enabled() || dev.password() == null || dev.password().isBlank()) return;
        String hash = encoder.encode(dev.password());
        for (var e : Map.of("teacher", Role.TEACHER, "author", Role.AUTHOR, "reviewer", Role.REVIEWER, "admin", Role.ADMIN).entrySet()) {
            if (users.findByUsername(e.getKey()).isPresent()) continue;
            UserAccount u = UserAccount.staff(e.getValue(), e.getKey(), "Демо " + e.getKey(), hash);
            if (e.getValue() == Role.ADMIN) u.setTotpSecret(dev.adminTotpSecret());
            users.save(u);
        }
        log.warn("Dev demo staff accounts are enabled (teacher/author/reviewer/admin). Never enable this in production.");
    }

    private void seedContent(Seed seed, int[] added) {
        UUID author = users.findByUsername("system-seed-author").orElseThrow().getId();
        UUID reviewer = users.findByUsername("system-seed-reviewer").orElseThrow().getId();
        Map<String, UUID> blueprintIds = new java.util.HashMap<>();
        for (TestBlueprint existing : blueprints.findAll()) {
            blueprintIds.putIfAbsent(existing.getName(), existing.getId());
        }
        for (SeedBlueprint b : seed.blueprints()) {
            if (blueprintIds.containsKey(b.name())) continue;
            TestBlueprint bp = new TestBlueprint(b.name(), b.academicYear(), 1, b.composition(), b.timeLimitMin());
            bp.setStatus(TestBlueprint.Status.APPROVED);
            blueprints.save(bp);
            blueprintIds.put(b.name(), bp.getId());
        }
        for (SeedQuestion sq : seed.questions()) {
            if (questions.maxVersion(sq.key()) > 0) continue;
            Question keyed = content.createQuestionWithKey(author, sq.key(), sq.draft());
            content.submitQuestion(author, keyed.getId());
            content.reviewQuestion(reviewer, keyed.getId(), true, DEMO_REVIEW_NOTE);
            added[0]++;
        }
        for (SeedLesson sl : seed.lessons()) {
            if (lessons.maxVersion(sl.lessonKey()) > 0) continue;
            Lesson l = content.createLesson(author, sl.lessonKey(), sl.content());
            content.submitLesson(author, l.getId());
            content.reviewLesson(reviewer, l.getId(), true, DEMO_REVIEW_NOTE);
            added[1]++;
        }
        for (SeedTest st : seed.tests()) {
            if (tests.findByTestKey(st.testKey()).isPresent()) continue;
            List<UUID> ids = st.questionKeys().stream()
                    .map(k -> questions.findByQuestionKeyAndStatus(k, ContentStatus.PUBLISHED).orElseThrow().getId()).toList();
            TestDefinition t = tests.save(new TestDefinition(st.testKey(), st.title(), st.kind(), st.path(),
                    blueprintIds.get(st.blueprint()), st.timeLimitMin(), GradingScale.defaultScale(), ids,
                    st.hintPolicy(), st.reviewMoment(), author, props.academicYear()));
            authoring.publish(author, t.getId(), true);
            added[2]++;
        }
        // Sanity: every seeded lesson key exists in the curriculum.
        seed.lessons().forEach(l -> CurriculumCatalog.lesson(l.lessonKey()).orElseThrow());
    }
}
