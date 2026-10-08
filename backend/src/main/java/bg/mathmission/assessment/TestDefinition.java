package bg.mathmission.assessment;

import bg.mathmission.common.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An assessed test: an ordered list of reviewed question versions plus scoring rules. */
@Entity
@Table(name = "test_definition")
public class TestDefinition {

    public enum Kind {
        DIAGNOSTIC, THEMATIC, INTEGRATED, TEACHER,
        /** Points out of 100 and skill analysis only; never shows a grade. */
        ASSESSMENT_STYLE
    }

    public enum Status { DRAFT, PUBLISHED, WITHDRAWN }

    public enum HintPolicy { NONE, ALLOWED }

    public enum ReviewMoment { AFTER_SUBMIT, AFTER_DEADLINE }

    public static final int MIN_ASSESSED_QUESTIONS = 20;

    @Id
    private UUID id;
    @Column(name = "test_key")
    private String testKey;
    private String title;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    private String path;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "blueprint_id")
    private UUID blueprintId;
    @Column(name = "time_limit_min")
    private int timeLimitMin;
    @Column(name = "grading_json")
    private String gradingJson;
    @Column(name = "question_ids_json")
    private String questionIdsJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "hint_policy")
    private HintPolicy hintPolicy;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_moment")
    private ReviewMoment reviewMoment;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "academic_year")
    private String academicYear;
    @Column(name = "created_at")
    private Instant createdAt;

    protected TestDefinition() {}

    public TestDefinition(String testKey, String title, Kind kind, String path, UUID blueprintId, int timeLimitMin,
                          GradingScale grading, List<UUID> questionIds, HintPolicy hintPolicy,
                          ReviewMoment reviewMoment, UUID createdBy, String academicYear) {
        this.id = UUID.randomUUID();
        this.testKey = testKey;
        this.title = title;
        this.kind = kind;
        this.path = path;
        this.status = Status.DRAFT;
        this.blueprintId = blueprintId;
        this.timeLimitMin = timeLimitMin;
        this.gradingJson = Json.write(grading);
        this.questionIdsJson = Json.write(questionIds);
        this.hintPolicy = hintPolicy;
        this.reviewMoment = reviewMoment;
        this.createdBy = createdBy;
        this.academicYear = academicYear;
        this.createdAt = Instant.now();
    }

    public List<UUID> questionIds() {
        return Json.read(questionIdsJson, new TypeReference<List<UUID>>() {});
    }

    public void replaceQuestion(UUID oldId, UUID newId) {
        List<UUID> ids = new java.util.ArrayList<>(questionIds());
        ids.replaceAll(id -> id.equals(oldId) ? newId : id);
        this.questionIdsJson = Json.write(ids);
    }

    public GradingScale grading() {
        return Json.read(gradingJson, GradingScale.class);
    }

    public boolean showsGrade() {
        return kind != Kind.ASSESSMENT_STYLE;
    }

    public UUID getId() { return id; }
    public String getTestKey() { return testKey; }
    public String getTitle() { return title; }
    public Kind getKind() { return kind; }
    public String getPath() { return path; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public UUID getBlueprintId() { return blueprintId; }
    public int getTimeLimitMin() { return timeLimitMin; }
    public HintPolicy getHintPolicy() { return hintPolicy; }
    public ReviewMoment getReviewMoment() { return reviewMoment; }
    public UUID getCreatedBy() { return createdBy; }
    public String getAcademicYear() { return academicYear; }
    public Instant getCreatedAt() { return createdAt; }

    /** Seed content may rename a test (e.g. regrouping by topic); attempts and results are unaffected. */
    public void setTitle(String title) {
        this.title = title;
    }
}
