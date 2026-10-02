package bg.mathmission.reporting;

import bg.mathmission.assessment.AnswerChecker;
import bg.mathmission.assessment.ItemResponse;
import bg.mathmission.assessment.ItemResponseRepository;
import bg.mathmission.assessment.TestAttempt;
import bg.mathmission.assessment.TestAttemptRepository;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.classroom.ClassroomService;
import bg.mathmission.common.ApiException;
import bg.mathmission.content.Question;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionRepository;
import bg.mathmission.curriculum.CurriculumCatalog;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import bg.mathmission.progress.SkillMastery;
import bg.mathmission.progress.SkillMasteryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class results by skill and misconception (US-TCH-03). Only recorded activity is counted:
 * enrolment alone is never treated as participation. No labels about ability are produced.
 */
@Service
public class TeacherAnalyticsService {

    private final ClassroomService classroom;
    private final TestDefinitionRepository tests;
    private final TestAttemptRepository attempts;
    private final ItemResponseRepository responses;
    private final QuestionRepository questions;
    private final UserAccountRepository users;
    private final SkillMasteryRepository mastery;

    public TeacherAnalyticsService(ClassroomService classroom, TestDefinitionRepository tests, TestAttemptRepository attempts,
                                   ItemResponseRepository responses, QuestionRepository questions,
                                   UserAccountRepository users, SkillMasteryRepository mastery) {
        this.classroom = classroom;
        this.tests = tests;
        this.attempts = attempts;
        this.responses = responses;
        this.questions = questions;
        this.users = users;
        this.mastery = mastery;
    }

    public record Bucket(String label, int count) {}

    public record ItemStat(int questionNumber, String questionKey, String skill, int answered, double averagePercent,
                           String topMisconception) {}

    public record SkillStat(String skill, String title, double averagePercent, int students, String lessonKey) {}

    public record MisconceptionStat(String code, String label, int occurrences, int students) {}

    public record StudentRow(UUID studentId, String nickname, int attempts, Double firstPercent, Double latestPercent,
                             Double change, String scoringSource) {}

    public record TestReport(UUID testId, String title, int enrolled, int participated, int completed,
                             Double averagePercent, List<Bucket> distribution, List<ItemStat> items, List<SkillStat> skills,
                             List<MisconceptionStat> misconceptions, List<StudentRow> students, String note) {}

    @Transactional(readOnly = true)
    public TestReport testReport(UUID teacherId, UUID classId, UUID testId) {
        classroom.owned(teacherId, classId);
        TestDefinition t = tests.findById(testId).orElseThrow(() -> ApiException.notFound("Тест"));
        Set<UUID> students = classroom.activeStudentIds(classId);
        List<TestAttempt> all = students.isEmpty() ? List.of()
                : attempts.findByTestIdAndStudentIdInOrderByStartedAtAsc(testId, students);
        List<TestAttempt> submitted = all.stream().filter(a -> a.getStatus() == TestAttempt.Status.SUBMITTED).toList();
        Map<UUID, List<TestAttempt>> byStudent = submitted.stream().collect(Collectors.groupingBy(TestAttempt::getStudentId));
        // Latest submitted attempt per student is used for the class picture.
        List<TestAttempt> latest = byStudent.values().stream().map(l -> l.get(l.size() - 1)).toList();

        Double avg = latest.isEmpty() ? null
                : Math.round(latest.stream().mapToDouble(a -> a.getPercent() == null ? 0 : a.getPercent()).average().orElse(0) * 10) / 10.0;
        int[] buckets = new int[5];
        for (TestAttempt a : latest) {
            double p = a.getPercent() == null ? 0 : a.getPercent();
            buckets[p < 40 ? 0 : p < 55 ? 1 : p < 70 ? 2 : p < 85 ? 3 : 4]++;
        }
        List<Bucket> distribution = List.of(new Bucket("0–39%", buckets[0]), new Bucket("40–54%", buckets[1]),
                new Bucket("55–69%", buckets[2]), new Bucket("70–84%", buckets[3]), new Bucket("85–100%", buckets[4]));

        List<ItemResponse> items = latest.isEmpty() ? List.of()
                : responses.findByAttemptIdIn(latest.stream().map(TestAttempt::getId).toList());
        Map<UUID, Question> qs = questions.findAllById(items.stream().map(ItemResponse::getQuestionId).distinct().toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<UUID, UUID> attemptToStudent = latest.stream().collect(Collectors.toMap(TestAttempt::getId, TestAttempt::getStudentId));

        // Item analysis, keyed by question (positions are randomised per attempt).
        Map<String, List<ItemResponse>> byQuestion = items.stream().filter(r -> !"WITHDRAWN".equals(r.getScoredBy()))
                .collect(Collectors.groupingBy(r -> qs.get(r.getQuestionId()).getQuestionKey(), LinkedHashMap::new, Collectors.toList()));
        List<String> order = t.questionIds().stream().map(id -> questions.findById(id).map(Question::getQuestionKey).orElse(null)).toList();
        List<ItemStat> itemStats = new ArrayList<>();
        for (var e : byQuestion.entrySet()) {
            Question q = qs.get(e.getValue().get(0).getQuestionId());
            double avgItem = e.getValue().stream().mapToDouble(r -> (r.getPoints() == null ? 0 : r.getPoints()) / q.getMaxPoints()).average().orElse(0);
            String top = e.getValue().stream().map(ItemResponse::getMisconception).filter(java.util.Objects::nonNull)
                    .collect(Collectors.groupingBy(m -> m, Collectors.counting())).entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(x -> x.getKey().labelBg).orElse(null);
            int answered = (int) e.getValue().stream().filter(ItemResponse::hasAnswer).count();
            itemStats.add(new ItemStat(order.indexOf(e.getKey()) + 1, e.getKey(), q.getSkill(), answered,
                    Math.round(avgItem * 1000) / 10.0, top));
        }
        itemStats.sort(Comparator.comparingInt(ItemStat::questionNumber));

        Map<String, double[]> skillAcc = new LinkedHashMap<>();
        Map<String, Set<UUID>> skillStudents = new LinkedHashMap<>();
        Map<Misconception, int[]> misc = new LinkedHashMap<>();
        Map<Misconception, Set<UUID>> miscStudents = new LinkedHashMap<>();
        for (ItemResponse r : items) {
            if ("WITHDRAWN".equals(r.getScoredBy())) continue;
            Question q = qs.get(r.getQuestionId());
            double[] acc = skillAcc.computeIfAbsent(q.getSkill(), k -> new double[2]);
            acc[0] += r.getPoints() == null ? 0 : r.getPoints();
            acc[1] += q.getMaxPoints();
            skillStudents.computeIfAbsent(q.getSkill(), k -> new java.util.HashSet<>()).add(attemptToStudent.get(r.getAttemptId()));
            if (r.getMisconception() != null && r.getStatus() != AnswerChecker.Status.CORRECT) {
                misc.computeIfAbsent(r.getMisconception(), k -> new int[1])[0]++;
                miscStudents.computeIfAbsent(r.getMisconception(), k -> new java.util.HashSet<>()).add(attemptToStudent.get(r.getAttemptId()));
            }
        }
        List<SkillStat> skills = skillAcc.entrySet().stream().map(e -> new SkillStat(e.getKey(), CurriculumCatalog.skillTitle(e.getKey()),
                        Math.round(e.getValue()[0] / e.getValue()[1] * 1000) / 10.0, skillStudents.get(e.getKey()).size(),
                        CurriculumCatalog.lessonForSkill(e.getKey()).map(CurriculumCatalog.LessonStop::key).orElse(null)))
                .sorted(Comparator.comparingDouble(SkillStat::averagePercent)).toList();
        List<MisconceptionStat> miscStats = misc.entrySet().stream()
                .map(e -> new MisconceptionStat(e.getKey().name(), e.getKey().labelBg, e.getValue()[0], miscStudents.get(e.getKey()).size()))
                .sorted(Comparator.comparingInt(MisconceptionStat::occurrences).reversed()).toList();

        Map<UUID, UserAccount> people = users.findAllById(students).stream().collect(Collectors.toMap(UserAccount::getId, u -> u));
        Set<UUID> participated = all.stream().map(TestAttempt::getStudentId).collect(Collectors.toSet());
        List<StudentRow> rows = participated.stream().map(id -> {
            List<TestAttempt> mine = byStudent.getOrDefault(id, List.of());
            Double first = mine.isEmpty() ? null : mine.get(0).getPercent();
            Double last = mine.isEmpty() ? null : mine.get(mine.size() - 1).getPercent();
            Double change = first == null || last == null || mine.size() < 2 ? null : Math.round((last - first) * 10) / 10.0;
            String src = mine.isEmpty() ? "IN_PROGRESS" : String.valueOf(mine.get(mine.size() - 1).getScoringSource());
            return new StudentRow(id, people.get(id).getNickname(), mine.size(), first, last, change, src);
        }).sorted(Comparator.comparing(StudentRow::nickname)).toList();

        return new TestReport(testId, t.getTitle(), students.size(), participated.size(), byStudent.size(), avg, distribution,
                itemStats, skills, miscStats, rows,
                "Броят се само записани опити. Записването в класа не се счита за участие. Резултатите описват текущо ниво на умения, а не способности.");
    }

    public record ClassSkillOverview(String skill, String title, int practising, int secure, int needsPractice) {}

    /** Mastery states across the class, as counts per skill (no per-student labels). */
    @Transactional(readOnly = true)
    public List<ClassSkillOverview> skillOverview(UUID teacherId, UUID classId) {
        classroom.owned(teacherId, classId);
        Set<UUID> students = classroom.activeStudentIds(classId);
        if (students.isEmpty()) return List.of();
        Map<String, List<SkillMastery>> bySkill = mastery.findByStudentIdIn(students).stream()
                .collect(Collectors.groupingBy(SkillMastery::getSkill));
        return CurriculumCatalog.LESSONS.stream().filter(l -> bySkill.containsKey(l.skill())).map(l -> {
            List<SkillMastery> ms = bySkill.get(l.skill());
            int secure = (int) ms.stream().filter(m -> m.getState() == SkillMastery.State.SECURE || m.getState() == SkillMastery.State.MASTERED).count();
            int needs = (int) ms.stream().filter(m -> m.getState() == SkillMastery.State.NEEDS_PRACTICE).count();
            return new ClassSkillOverview(l.skill(), l.title(), ms.size() - secure - needs, secure, needs);
        }).toList();
    }
}
