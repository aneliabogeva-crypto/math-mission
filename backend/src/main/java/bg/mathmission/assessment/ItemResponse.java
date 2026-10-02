package bg.mathmission.assessment;

import bg.mathmission.common.Json;
import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Misconception;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One position in an attempt; created at start so the question order is fixed per attempt. */
@Entity
@Table(name = "item_response")
public class ItemResponse {

    @Id
    private UUID id;
    @Column(name = "attempt_id")
    private UUID attemptId;
    @Column(name = "question_id")
    private UUID questionId;
    private int position;
    @Column(name = "response_json")
    private String responseJson;
    @Column(name = "marked_for_review")
    private boolean markedForReview;
    @Column(name = "hints_used")
    private int hintsUsed;
    @Enumerated(EnumType.STRING)
    private AnswerChecker.Status status;
    private Double points;
    @Enumerated(EnumType.STRING)
    private Misconception misconception;
    private String feedback;
    @Column(name = "scored_by")
    private String scoredBy;
    @Column(name = "reviewer_id")
    private UUID reviewerId;
    @Column(name = "review_comment")
    private String reviewComment;
    @Column(name = "last_request_id")
    private String lastRequestId;
    @Column(name = "saved_at")
    private Instant savedAt;

    protected ItemResponse() {}

    public ItemResponse(UUID attemptId, UUID questionId, int position) {
        this.id = UUID.randomUUID();
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.position = position;
        this.savedAt = Instant.now();
    }

    public AnswerPayload payload() {
        return Json.read(responseJson, AnswerPayload.class);
    }

    public void save(AnswerPayload payload, boolean marked, String requestId) {
        this.responseJson = payload == null ? null : Json.write(payload);
        this.markedForReview = marked;
        this.lastRequestId = requestId;
        this.savedAt = Instant.now();
    }

    public void score(AnswerChecker.Result r, String scoredBy) {
        this.status = r.status();
        this.points = r.points();
        this.misconception = r.misconception();
        this.feedback = r.message();
        this.scoredBy = scoredBy;
    }

    public void teacherScore(double points, double max, UUID reviewerId, String comment) {
        this.points = points;
        this.status = points >= max ? AnswerChecker.Status.CORRECT : points > 0 ? AnswerChecker.Status.PARTIAL : AnswerChecker.Status.INCORRECT;
        this.reviewerId = reviewerId;
        this.reviewComment = comment;
        this.scoredBy = "TEACHER";
    }

    public void neutralise(String reason) {
        this.status = AnswerChecker.Status.CORRECT;
        this.points = null; // excluded from both points and maximum
        this.feedback = reason;
        this.scoredBy = "WITHDRAWN";
    }

    public void useHint(int level) {
        this.hintsUsed = Math.max(hintsUsed, level);
    }

    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public UUID getId() { return id; }
    public UUID getAttemptId() { return attemptId; }
    public UUID getQuestionId() { return questionId; }
    public int getPosition() { return position; }
    public boolean hasAnswer() { return responseJson != null; }
    public boolean isMarkedForReview() { return markedForReview; }
    public int getHintsUsed() { return hintsUsed; }
    public AnswerChecker.Status getStatus() { return status; }
    public Double getPoints() { return points; }
    public Misconception getMisconception() { return misconception; }
    public String getFeedback() { return feedback; }
    public String getScoredBy() { return scoredBy; }
    public UUID getReviewerId() { return reviewerId; }
    public String getReviewComment() { return reviewComment; }
    public String getLastRequestId() { return lastRequestId; }
    public Instant getSavedAt() { return savedAt; }
}
