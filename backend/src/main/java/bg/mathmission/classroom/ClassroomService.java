package bg.mathmission.classroom;

import bg.mathmission.assessment.AssignmentPolicy;
import bg.mathmission.assessment.TestAttempt;
import bg.mathmission.assessment.TestAttemptRepository;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.common.Codes;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.LessonRepository;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import bg.mathmission.progress.LessonProgress;
import bg.mathmission.progress.LessonProgressRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService implements AssignmentPolicy {

    private final ClassGroupRepository classes;
    private final ClassMembershipRepository memberships;
    private final AssignmentRepository assignments;
    private final UserAccountRepository users;
    private final TestDefinitionRepository tests;
    private final TestAttemptRepository attempts;
    private final LessonRepository lessons;
    private final LessonProgressRepository lessonProgress;
    private final AuditService audit;

    public ClassroomService(ClassGroupRepository classes, ClassMembershipRepository memberships,
                            AssignmentRepository assignments, UserAccountRepository users, TestDefinitionRepository tests,
                            TestAttemptRepository attempts, LessonRepository lessons,
                            LessonProgressRepository lessonProgress, AuditService audit) {
        this.classes = classes;
        this.memberships = memberships;
        this.assignments = assignments;
        this.users = users;
        this.tests = tests;
        this.attempts = attempts;
        this.lessons = lessons;
        this.lessonProgress = lessonProgress;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ classes

    public record ClassView(UUID id, String name, String code, boolean codeRevoked, int activeStudents) {}

    public record MemberView(UUID studentId, String nickname, String avatar, Instant joinedAt) {}

    @Transactional
    public ClassView create(UUID teacherId, String name) {
        if (name == null || name.isBlank() || name.length() > 80) throw ApiException.badRequest("NAME", "Въведете име на класа.");
        ClassGroup c = classes.save(new ClassGroup(teacherId, name.trim(), uniqueCode()));
        audit.record(teacherId, "CLASS_CREATED", "ClassGroup", c.getId(), c.getName());
        return view(c);
    }

    @Transactional(readOnly = true)
    public List<ClassView> mine(UUID teacherId) {
        return classes.findByTeacherIdOrderByCreatedAtAsc(teacherId).stream().map(this::view).toList();
    }

    @Transactional
    public ClassView revokeCode(UUID teacherId, UUID classId) {
        ClassGroup c = owned(teacherId, classId);
        c.revokeCode();
        audit.record(teacherId, "CLASS_CODE_REVOKED", "ClassGroup", c.getId(), null);
        return view(c);
    }

    @Transactional
    public ClassView newCode(UUID teacherId, UUID classId) {
        ClassGroup c = owned(teacherId, classId);
        c.replaceCode(uniqueCode());
        audit.record(teacherId, "CLASS_CODE_REGENERATED", "ClassGroup", c.getId(), null);
        return view(c);
    }

    @Transactional
    public ClassView join(UUID studentId, String code) {
        ClassGroup c = classes.findByCode(code == null ? "" : code.trim().toUpperCase())
                .filter(g -> !g.isCodeRevoked())
                .orElseThrow(() -> ApiException.badRequest("CLASS_CODE", "Кодът на класа не е валиден. Провери го при учителя."));
        Optional<ClassMembership> existing = memberships.findByClassIdAndStudentId(c.getId(), studentId);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == ClassMembership.Status.ACTIVE) return view(c);
            existing.get().rejoin();
        } else {
            memberships.save(new ClassMembership(c.getId(), studentId));
        }
        audit.record(studentId, "CLASS_JOINED", "ClassGroup", c.getId(), "student=" + studentId);
        return view(c);
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID teacherId, UUID classId) {
        owned(teacherId, classId);
        List<ClassMembership> ms = memberships.findByClassIdAndStatus(classId, ClassMembership.Status.ACTIVE);
        var byId = users.findAllById(ms.stream().map(ClassMembership::getStudentId).toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
        return ms.stream().map(m -> {
            UserAccount u = byId.get(m.getStudentId());
            return new MemberView(m.getStudentId(), u.getNickname(), u.getAvatar(), m.getJoinedAt());
        }).toList();
    }

    @Transactional
    public void removeStudent(UUID teacherId, UUID classId, UUID studentId) {
        owned(teacherId, classId);
        ClassMembership m = memberships.findByClassIdAndStudentId(classId, studentId)
                .filter(x -> x.getStatus() == ClassMembership.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Ученик в класа"));
        m.remove(); // the student account is not deleted
        audit.record(teacherId, "CLASS_MEMBER_REMOVED", "ClassGroup", classId, "student=" + studentId);
    }

    public Set<UUID> activeStudentIds(UUID classId) {
        return memberships.findByClassIdAndStatus(classId, ClassMembership.Status.ACTIVE).stream()
                .map(ClassMembership::getStudentId).collect(Collectors.toSet());
    }

    /** Students in any of the teacher's classes (for review queues and analytics). */
    public Set<UUID> studentsOfTeacher(UUID teacherId) {
        Set<UUID> ids = new HashSet<>();
        classes.findByTeacherIdOrderByCreatedAtAsc(teacherId).forEach(c -> ids.addAll(activeStudentIds(c.getId())));
        return ids;
    }

    public ClassGroup owned(UUID teacherId, UUID classId) {
        ClassGroup c = classes.findById(classId).orElseThrow(() -> ApiException.notFound("Клас"));
        if (!c.getTeacherId().equals(teacherId)) throw ApiException.forbidden("Класът не е ваш.");
        return c;
    }

    private ClassView view(ClassGroup c) {
        return new ClassView(c.getId(), c.getName(), c.getCode(), c.isCodeRevoked(),
                memberships.findByClassIdAndStatus(c.getId(), ClassMembership.Status.ACTIVE).size());
    }

    private String uniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = Codes.generate(6);
            if (classes.findByCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not generate a unique class code");
    }

    // ------------------------------------------------------------------ assignments

    public record AssignmentRequest(Assignment.TargetType targetType, String targetKey, List<UUID> studentIds,
                                    Instant deadline, int allowedAttempts, TestDefinition.HintPolicy hintPolicy) {}

    public record AssignmentView(UUID id, UUID classId, String title, Assignment.TargetType targetType, String targetKey,
                                 UUID testId, Instant deadline, int allowedAttempts, TestDefinition.HintPolicy hintPolicy,
                                 boolean wholeClass, String status, String statusLabel, int attemptsUsed) {}

    @Transactional
    public AssignmentView assign(UUID teacherId, UUID classId, AssignmentRequest r) {
        owned(teacherId, classId);
        if (r.allowedAttempts() < 1 || r.allowedAttempts() > 10) {
            throw ApiException.badRequest("ATTEMPTS", "Разрешените опити трябва да са между 1 и 10.");
        }
        if (r.deadline() != null && r.deadline().isBefore(Instant.now())) {
            throw ApiException.badRequest("DEADLINE", "Крайният срок е в миналото.");
        }
        String title = resolveTitle(r.targetType(), r.targetKey(), teacherId);
        if (r.studentIds() != null && !r.studentIds().isEmpty() && !activeStudentIds(classId).containsAll(r.studentIds())) {
            throw ApiException.badRequest("STUDENTS", "Някои ученици не са в този клас.");
        }
        Assignment a = assignments.save(new Assignment(classId, teacherId, r.targetType(), r.targetKey(), title,
                r.studentIds(), r.deadline(), r.allowedAttempts(),
                r.hintPolicy() == null ? TestDefinition.HintPolicy.NONE : r.hintPolicy()));
        audit.record(teacherId, "ASSIGNMENT_CREATED", "Assignment", a.getId(), r.targetType() + ":" + r.targetKey());
        return view(a, null);
    }

    private String resolveTitle(Assignment.TargetType type, String key, UUID teacherId) {
        if (type == Assignment.TargetType.LESSON) {
            return lessons.findByLessonKeyAndStatus(key, ContentStatus.PUBLISHED)
                    .orElseThrow(() -> ApiException.badRequest("TARGET", "Може да се зададе само публикуван урок.")).getTitle();
        }
        TestDefinition t = tests.findByTestKey(key)
                .filter(x -> x.getStatus() == TestDefinition.Status.PUBLISHED)
                .orElseThrow(() -> ApiException.badRequest("TARGET", "Може да се зададе само публикуван, прегледан тест."));
        if (t.getKind() == TestDefinition.Kind.TEACHER && !t.getCreatedBy().equals(teacherId)) {
            throw ApiException.forbidden("Тестът е създаден от друг учител.");
        }
        return t.getTitle();
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> forClass(UUID teacherId, UUID classId) {
        owned(teacherId, classId);
        return assignments.findByClassIdOrderByCreatedAtDesc(classId).stream().map(a -> view(a, null)).toList();
    }

    /** Clear status per assignment; no push notifications are sent (students see it on their home screen). */
    @Transactional(readOnly = true)
    public List<AssignmentView> forStudent(UUID studentId) {
        List<UUID> classIds = memberships.findByStudentIdAndStatus(studentId, ClassMembership.Status.ACTIVE).stream()
                .map(ClassMembership::getClassId).toList();
        if (classIds.isEmpty()) return List.of();
        return assignments.findByClassIdInOrderByCreatedAtDesc(classIds).stream()
                .filter(a -> a.targets(studentId)).map(a -> view(a, studentId)).toList();
    }

    private AssignmentView view(Assignment a, UUID studentId) {
        UUID testId = a.getTargetType() == Assignment.TargetType.TEST
                ? tests.findByTestKey(a.getTargetKey()).map(TestDefinition::getId).orElse(null) : null;
        String status = null;
        String label = null;
        int used = 0;
        if (studentId != null) {
            boolean done;
            boolean started;
            if (a.getTargetType() == Assignment.TargetType.TEST) {
                List<TestAttempt> mine = attempts.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                        .filter(x -> a.getId().equals(x.getAssignmentId())).toList();
                used = mine.size();
                done = mine.stream().anyMatch(x -> x.getStatus() == TestAttempt.Status.SUBMITTED);
                started = !mine.isEmpty();
            } else {
                Optional<LessonProgress> p = lessonProgress.findByStudentIdAndLessonKey(studentId, a.getTargetKey());
                done = p.map(x -> x.getStatus() == LessonProgress.Status.COMPLETED).orElse(false);
                started = p.isPresent();
            }
            boolean overdue = a.getDeadline() != null && Instant.now().isAfter(a.getDeadline());
            if (done) { status = "COMPLETED"; label = "Изпълнено"; }
            else if (overdue) { status = "OVERDUE"; label = "Срокът изтече"; }
            else if (started) { status = "IN_PROGRESS"; label = "Започнато"; }
            else { status = "NOT_STARTED"; label = "Ново"; }
        }
        return new AssignmentView(a.getId(), a.getClassId(), a.getTitle(), a.getTargetType(), a.getTargetKey(), testId,
                a.getDeadline(), a.getAllowedAttempts(), a.getHintPolicy(), a.studentIds() == null, status, label, used);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rules> rulesFor(UUID assignmentId, UUID studentId) {
        return assignments.findById(assignmentId)
                .filter(a -> a.targets(studentId))
                .filter(a -> memberships.findByClassIdAndStudentId(a.getClassId(), studentId)
                        .map(m -> m.getStatus() == ClassMembership.Status.ACTIVE).orElse(false))
                .map(a -> new Rules(a.getId(), a.getTargetType().name(), a.getTargetKey(), a.getDeadline(),
                        a.getAllowedAttempts(), a.getHintPolicy()));
    }
}
