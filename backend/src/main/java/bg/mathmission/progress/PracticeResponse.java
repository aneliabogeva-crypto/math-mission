package bg.mathmission.progress;

import bg.mathmission.content.QuestionModel.Misconception;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An answer given in a lesson, practice set or knowledge check (never graded). */
@Entity
@Table(name = "practice_response")
public class PracticeResponse {

    @Id
    private UUID id;
    @Column(name = "request_id")
    private String requestId;
    @Column(name = "student_id")
    private UUID studentId;
    @Column(name = "question_id")
    private UUID questionId;
    @Column(name = "lesson_key")
    private String lessonKey;
    private boolean correct;
    @Column(name = "hints_used")
    private int hintsUsed;
    @Enumerated(EnumType.STRING)
    private Misconception misconception;
    @Column(name = "corrects_previous")
    private boolean correctsPrevious;
    @Column(name = "created_at")
    private Instant createdAt;

    protected PracticeResponse() {}

    public PracticeResponse(String requestId, UUID studentId, UUID questionId, String lessonKey, boolean correct,
                            int hintsUsed, Misconception misconception, boolean correctsPrevious) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.studentId = studentId;
        this.questionId = questionId;
        this.lessonKey = lessonKey;
        this.correct = correct;
        this.hintsUsed = hintsUsed;
        this.misconception = misconception;
        this.correctsPrevious = correctsPrevious;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getRequestId() { return requestId; }
    public UUID getStudentId() { return studentId; }
    public UUID getQuestionId() { return questionId; }
    public String getLessonKey() { return lessonKey; }
    public boolean isCorrect() { return correct; }
    public int getHintsUsed() { return hintsUsed; }
    public Misconception getMisconception() { return misconception; }
    public boolean isCorrectsPrevious() { return correctsPrevious; }
    public Instant getCreatedAt() { return createdAt; }
}
