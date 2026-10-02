package bg.mathmission.content;

import bg.mathmission.common.Json;
import bg.mathmission.content.QuestionModel.AnswerKey;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionModel.Prompt;
import bg.mathmission.content.QuestionModel.ResponseType;
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

/** One immutable-after-review version of a question, with the metadata from spec 4.3. */
@Entity
@Table(name = "question")
public class Question {

    public enum Difficulty { FOUNDATIONAL, INTERMEDIATE, ADVANCED }

    @Id
    private UUID id;
    @Column(name = "question_key")
    private String questionKey;
    private int version;
    @Enumerated(EnumType.STRING)
    private ContentStatus status;
    private String path;
    private String skill;
    @Column(name = "learning_outcome")
    private String learningOutcome;
    @Column(name = "academic_year")
    private String academicYear;
    @Enumerated(EnumType.STRING)
    @Column(name = "response_type")
    private ResponseType responseType;
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;
    @Column(name = "estimated_seconds")
    private int estimatedSeconds;
    @Column(name = "max_points")
    private double maxPoints;
    @Enumerated(EnumType.STRING)
    private Misconception misconception;
    @Column(name = "prompt_json")
    private String promptJson;
    @Column(name = "key_json")
    private String keyJson;
    @Column(name = "hints_json")
    private String hintsJson;
    @Column(name = "source_declaration")
    private String sourceDeclaration;
    @Column(name = "author_id")
    private UUID authorId;
    @Column(name = "reviewer_id")
    private UUID reviewerId;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_comment")
    private String reviewComment;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Question() {}

    public Question(String questionKey, int version, UUID authorId, QuestionDraft d, String academicYear) {
        this.id = UUID.randomUUID();
        this.questionKey = questionKey;
        this.version = version;
        this.status = ContentStatus.DRAFT;
        this.authorId = authorId;
        this.academicYear = academicYear;
        this.createdAt = Instant.now();
        apply(d);
    }

    public void apply(QuestionDraft d) {
        this.path = d.path();
        this.skill = d.skill();
        this.learningOutcome = d.learningOutcome();
        this.responseType = d.responseType();
        this.difficulty = d.difficulty();
        this.estimatedSeconds = d.estimatedSeconds();
        this.maxPoints = d.maxPoints();
        this.misconception = d.misconception();
        this.promptJson = Json.write(d.prompt());
        this.keyJson = Json.write(d.key());
        this.hintsJson = Json.write(d.hints() == null ? List.of() : d.hints());
        this.sourceDeclaration = d.sourceDeclaration();
    }

    public QuestionDraft toDraft() {
        return new QuestionDraft(path, skill, learningOutcome, responseType, difficulty, estimatedSeconds, maxPoints,
                misconception, prompt(), key(), hints(), sourceDeclaration);
    }

    public Prompt prompt() {
        return Json.read(promptJson, Prompt.class);
    }

    public AnswerKey key() {
        return Json.read(keyJson, AnswerKey.class);
    }

    public List<String> hints() {
        return Json.read(hintsJson, new TypeReference<List<String>>() {});
    }

    /** Fingerprint used to reject duplicate items inside one test (same key or same prompt text). */
    public String fingerprint() {
        return prompt().text().replaceAll("\\s+", " ").trim().toLowerCase();
    }

    public void markReviewed(UUID reviewerId, String comment) {
        this.reviewerId = reviewerId;
        this.reviewedAt = Instant.now();
        this.reviewComment = comment;
    }

    public UUID getId() { return id; }
    public String getQuestionKey() { return questionKey; }
    public int getVersion() { return version; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus status) { this.status = status; }
    public String getPath() { return path; }
    public String getSkill() { return skill; }
    public String getLearningOutcome() { return learningOutcome; }
    public String getAcademicYear() { return academicYear; }
    public ResponseType getResponseType() { return responseType; }
    public Difficulty getDifficulty() { return difficulty; }
    public int getEstimatedSeconds() { return estimatedSeconds; }
    public double getMaxPoints() { return maxPoints; }
    public Misconception getMisconception() { return misconception; }
    public String getSourceDeclaration() { return sourceDeclaration; }
    public UUID getAuthorId() { return authorId; }
    public UUID getReviewerId() { return reviewerId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String c) { this.reviewComment = c; }
    public Instant getCreatedAt() { return createdAt; }
}
