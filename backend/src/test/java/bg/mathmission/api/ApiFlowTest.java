package bg.mathmission.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bg.mathmission.identity.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end API journeys against the seeded content (H2 in PostgreSQL mode). Each test uses its
 * own fresh accounts, so tests are independent of execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ------------------------------------------------------------------ helpers

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, ResultMatcher expect) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = mvc.perform(req).andExpect(expect).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }

    JsonNode ok(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        return call(req, token, body, status().isOk());
    }

    String student(String nickname, String ageBand) throws Exception {
        return ok(post("/api/auth/student"), null, Map.of("nickname", nickname, "avatar", "fox", "ageBand", ageBand,
                "goal", "ASSESSMENT", "confidence", 3)).get("token").asText();
    }

    String staff(String username) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "password", "Test-Password-1"));
        if (username.equals("admin")) body.put("totp", Totp.generate("JBSWY3DPEHPK3PXP", Instant.now().getEpochSecond() / 30));
        return ok(post("/api/auth/login"), null, body).get("token").asText();
    }

    String req() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------ student journeys

    @Test
    void onboardingAndHomeUseOnlyANickname() throws Exception {
        JsonNode signup = ok(post("/api/auth/student"), null, Map.of("nickname", "Звездичка", "avatar", "owl",
                "ageBand", "FROM_14", "goal", "CLASSROOM", "confidence", 2));
        assertThat(signup.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(signup.get("recoveryCode").asText()).contains("-");
        String token = signup.get("token").asText();
        JsonNode home = ok(get("/api/student/home"), token, null);
        assertThat(home.at("/profile/nickname").asText()).isEqualTo("Звездичка");
        assertThat(home.get("offerDiagnostic").isBoolean()).isTrue();
        // recovery code signs in on another device
        JsonNode rec = ok(post("/api/auth/student/recover"), null, Map.of("recoveryCode", signup.get("recoveryCode").asText()));
        assertThat(rec.get("role").asText()).isEqualTo("STUDENT");
    }

    @Test
    void underAgeProfileNeedsGuardianConsentBeforeLearning() throws Exception {
        JsonNode signup = ok(post("/api/auth/student"), null, Map.of("nickname", "Малкият", "avatar", "cat",
                "ageBand", "UNDER_14", "goal", "IMPROVE", "confidence", 3));
        assertThat(signup.get("status").asText()).isEqualTo("PENDING_CONSENT");
        String token = signup.get("token").asText();
        String code = signup.get("consentCode").asText();
        call(get("/api/student/home"), token, null, status().isForbidden());

        JsonNode text = ok(get("/api/consent/text/adult"), null, null);
        ok(get("/api/consent/text/child"), null, null);
        call(post("/api/consent/code/" + code + "/grant"), null,
                Map.of("acceptedVersion", "old", "username", "parent-" + code, "password", "Parent-Pass-123"), status().isBadRequest());
        JsonNode grant = ok(post("/api/consent/code/" + code + "/grant"), null,
                Map.of("acceptedVersion", text.get("version").asText(), "username", "parent-" + code, "password", "Parent-Pass-123"));
        ok(get("/api/student/home"), token, null);

        String guardian = grant.get("token").asText();
        JsonNode children = ok(get("/api/guardian/children"), guardian, null);
        assertThat(children.get(0).get("consentVersion").asText()).isEqualTo(text.get("version").asText());
        String consentId = children.get(0).get("consentId").asText();
        assertThat(ok(get("/api/guardian/consents/" + consentId + "/export"), guardian, null).has("profile")).isTrue();

        JsonNode withdrawn = ok(post("/api/guardian/consents/" + consentId + "/withdraw"), guardian, null);
        assertThat(withdrawn.get("consequences").size()).isGreaterThan(0);
        call(get("/api/student/home"), token, null, status().isUnauthorized()); // sessions closed, account restricted
    }

    @Test
    void lessonPracticeGivesExplanatoryFeedbackAndIsIdempotent() throws Exception {
        String token = student("Практик", "FROM_14");
        JsonNode lesson = ok(get("/api/student/lessons/B3"), token, null);
        assertThat(lesson.at("/content/workedExamples").size()).isGreaterThanOrEqualTo(3);
        assertThat(lesson.at("/questions/B3-C1/prompt/text").asText()).isNotBlank();
        assertThat(lesson.toString()).doesNotContain("correctOptionId").doesNotContain("\"solution\"");

        String r1 = req();
        JsonNode wrong = ok(post("/api/student/practice/B3-C2/check"), token,
                Map.of("requestId", r1, "answer", Map.of("value", "7"), "hintsUsed", 0, "lessonKey", "B3"));
        assertThat(wrong.get("correct").asBoolean()).isFalse();
        assertThat(wrong.get("message").asText()).isNotBlank();
        assertThat(wrong.get("solution").asText()).isNotBlank();
        assertThat(wrong.get("theoryLessonKey").asText()).isEqualTo("B3");
        assertThat(wrong.get("similarQuestionKey").asText()).isNotBlank();
        assertThat(wrong.get("misconceptionLabel").asText()).isEqualTo("Подобни едночлени");

        JsonNode right = ok(post("/api/student/practice/B3-C2/check"), token,
                Map.of("requestId", req(), "answer", Map.of("value", "6m"), "hintsUsed", 0));
        assertThat(right.get("correct").asBoolean()).isTrue();
        assertThat(right.get("correctedMistake").asBoolean()).isTrue();

        int xp = ok(get("/api/student/home"), token, null).at("/profile/xp").asInt();
        ok(post("/api/student/practice/B3-C2/check"), token,
                Map.of("requestId", r1, "answer", Map.of("value", "7"), "hintsUsed", 0)); // replay
        assertThat(ok(get("/api/student/home"), token, null).at("/profile/xp").asInt()).isEqualTo(xp);

        JsonNode mistakes = ok(get("/api/student/mistakes"), token, null);
        assertThat(mistakes.get(0).get("lessonKey").asText()).isNotBlank();

        ok(put("/api/student/lessons/B3/position"), token, Map.of("position", 11, "completed", true));
        JsonNode map = ok(get("/api/student/map"), token, null);
        assertThat(map.toString()).contains("\"COMPLETED\"");
        assertThat(map.size()).isEqualTo(4);
    }

    @Test
    void testAnswerKeyLeaksNeitherThroughTheAttemptNorThroughPractice() throws Exception {
        String token = student("Тестов", "FROM_14");
        call(get("/api/student/practice/BT1-13"), token, null, status().isNotFound());
        JsonNode tests = ok(get("/api/student/tests"), token, null);
        JsonNode bt1 = tests.get(0);
        assertThat(bt1.get("questionCount").asInt()).isGreaterThanOrEqualTo(20);
        assertThat(bt1.get("gradingBands").size()).isEqualTo(5);
        assertThat(bt1.get("timeLimitMin").asInt()).isPositive();
        assertThat(bt1.get("scopeNote").asText()).contains("не е пълна симулация");

        UUID attemptId = UUID.randomUUID();
        JsonNode attempt = ok(post("/api/student/tests/" + bt1.get("id").asText() + "/attempts"), token, Map.of("attemptId", attemptId));
        assertThat(attempt.get("items").size()).isEqualTo(20);
        assertThat(attempt.toString()).doesNotContain("correctOptionId").doesNotContain("\"solution\"").doesNotContain("distractors");
        // retried start returns the same attempt instead of creating a duplicate
        JsonNode again = ok(post("/api/student/tests/" + bt1.get("id").asText() + "/attempts"), token, Map.of("attemptId", attemptId));
        assertThat(again.get("attemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(ok(get("/api/student/attempts"), token, null).size()).isEqualTo(1);
    }

    @Test
    void fullTestAttemptWithAutosaveRecoveryAndTransparentScoring() throws Exception {
        String token = student("Решавач", "FROM_14");
        String testId = ok(get("/api/student/tests"), token, null).get(0).get("id").asText();
        UUID attemptId = UUID.randomUUID();
        JsonNode attempt = ok(post("/api/student/tests/" + testId + "/attempts"), token, Map.of("attemptId", attemptId));

        // Answer every item with something plausible; choice items get option "a".
        for (JsonNode item : attempt.get("items")) {
            int pos = item.get("position").asInt();
            String type = item.at("/question/responseType").asText();
            Map<String, Object> answer = switch (type) {
                case "SINGLE_CHOICE" -> Map.of("optionId", "a");
                case "STRUCTURED" -> Map.of("parts", Map.of("a", "1", "b", "1", "c", "1"));
                case "STEPS" -> Map.of("steps", List.of("7x^2y + 2xy^2"));
                default -> Map.of("value", "1");
            };
            String requestId = req();
            JsonNode ack = ok(put("/api/student/attempts/" + attemptId + "/items/" + pos), token,
                    Map.of("requestId", requestId, "answer", answer, "markedForReview", pos == 3, "lastPosition", pos));
            assertThat(ack.get("duplicate").asBoolean()).isFalse();
            // network retry of the same save is acknowledged as a duplicate
            JsonNode dup = ok(put("/api/student/attempts/" + attemptId + "/items/" + pos), token,
                    Map.of("requestId", requestId, "answer", answer, "markedForReview", pos == 3, "lastPosition", pos));
            assertThat(dup.get("duplicate").asBoolean()).isTrue();
        }
        JsonNode resumed = ok(get("/api/student/attempts/" + attemptId), token, null);
        assertThat(resumed.get("items").get(3).get("markedForReview").asBoolean()).isTrue();
        assertThat(resumed.get("items").get(0).get("answered").asBoolean()).isTrue();

        JsonNode result = ok(post("/api/student/attempts/" + attemptId + "/submit"), token, null);
        assertThat(result.get("maxPoints").asDouble()).isEqualTo(31.0);
        assertThat(result.get("percent").isNumber()).isTrue();
        assertThat(result.get("grade").asInt()).isBetween(2, 6);
        assertThat(result.get("disclaimer").asText()).contains("не е официална");
        assertThat(result.get("skills").size()).isGreaterThan(1);
        assertThat(result.get("answerKeyVisible").asBoolean()).isTrue();
        assertThat(result.get("items").get(0).get("solution").asText()).isNotBlank();

        JsonNode resubmit = ok(post("/api/student/attempts/" + attemptId + "/submit"), token, null);
        assertThat(resubmit.get("percent").asDouble()).isEqualTo(result.get("percent").asDouble());
        call(put("/api/student/attempts/" + attemptId + "/items/0"), token,
                Map.of("requestId", req(), "answer", Map.of("optionId", "b")), status().isConflict());
    }

    // ------------------------------------------------------------------ teacher journeys

    @Test
    void teacherClassAssignmentAndSkillReport() throws Exception {
        String teacher = staff("teacher");
        JsonNode cls = ok(post("/api/teacher/classes"), teacher, Map.of("name", "7б"));
        String classId = cls.get("id").asText();
        String code = cls.get("code").asText();

        String s1 = student("Ани", "FROM_14");
        String s2 = student("Боби", "FROM_14");
        ok(post("/api/student/classes/join"), s1, Map.of("code", code));
        ok(post("/api/student/classes/join"), s2, Map.of("code", code));
        JsonNode members = ok(get("/api/teacher/classes/" + classId + "/members"), teacher, null);
        assertThat(members.size()).isEqualTo(2);

        JsonNode testsCatalog = ok(get("/api/teacher/tests"), teacher, null);
        String testKey = testsCatalog.get(0).get("key").asText();
        String testId = testsCatalog.get(0).get("id").asText();
        JsonNode preview = ok(get("/api/teacher/preview/test/" + testKey), teacher, null);
        assertThat(preview.toString()).doesNotContain("correctOptionId");
        JsonNode keyPreview = ok(get("/api/teacher/tests/" + testId + "/preview"), teacher, null);
        assertThat(keyPreview.get("items").get(0).get("correctAnswer").asText()).isNotBlank();

        JsonNode assignment = ok(post("/api/teacher/classes/" + classId + "/assignments"), teacher, Map.of(
                "targetType", "TEST", "targetKey", testKey, "allowedAttempts", 1, "hintPolicy", "NONE",
                "deadline", Instant.now().plusSeconds(86400).toString()));
        String assignmentId = assignment.get("id").asText();
        JsonNode mine = ok(get("/api/student/home"), s1, null).get("assignments");
        assertThat(mine.get(0).get("status").asText()).isEqualTo("NOT_STARTED");

        // s1 completes the assignment
        UUID a1 = UUID.randomUUID();
        ok(post("/api/student/tests/" + testId + "/attempts"), s1, Map.of("attemptId", a1, "assignmentId", assignmentId));
        ok(put("/api/student/attempts/" + a1 + "/items/0"), s1, Map.of("requestId", req(), "answer", Map.of("optionId", "b")));
        ok(post("/api/student/attempts/" + a1 + "/submit"), s1, null);
        // attempt rules are enforced
        call(post("/api/student/tests/" + testId + "/attempts"), s1, Map.of("attemptId", UUID.randomUUID(),
                "assignmentId", assignmentId), status().isConflict());

        JsonNode report = ok(get("/api/teacher/classes/" + classId + "/tests/" + testId + "/report"), teacher, null);
        assertThat(report.get("enrolled").asInt()).isEqualTo(2);
        assertThat(report.get("participated").asInt()).isEqualTo(1); // enrolment is not participation
        assertThat(report.get("skills").size()).isGreaterThan(0);
        assertThat(report.has("misconceptions")).isTrue();
        assertThat(report.get("distribution").size()).isEqualTo(5);

        // removing a student keeps the account
        String s2Id = members.findValues("studentId").stream().map(JsonNode::asText)
                .filter(id -> !id.isBlank()).toList().get(1);
        ok(delete("/api/teacher/classes/" + classId + "/members/" + s2Id), teacher, null);
        ok(get("/api/student/home"), s2, null);

        // generator refuses fewer than 20 questions
        call(post("/api/teacher/tests/generate"), teacher, Map.of("questionCount", 10, "timeLimitMin", 30), status().isBadRequest());
    }

    // ------------------------------------------------------------------ content governance

    @Test
    void authorCannotApproveOwnContentAndKeyCorrectionRecalculatesResults() throws Exception {
        String author = staff("author");
        String reviewer = staff("reviewer");

        Map<String, Object> draft = Map.of(
                "path", "B", "skill", "B.like-terms", "learningOutcome", "Събира подобни едночлени",
                "responseType", "EXPRESSION", "difficulty", "FOUNDATIONAL", "estimatedSeconds", 30, "maxPoints", 1,
                "misconception", "LIKE_TERMS", "prompt", Map.of("text", "Опрости: 2y + 3y (проверка " + UUID.randomUUID() + ")"),
                "key", Map.of("answer", "5y", "form", "NORMAL_FORM", "solution", "(2 + 3)y = 5y",
                        "distractors", List.of(Map.of("match", "5y^2", "misconception", "LIKE_TERMS", "explanation", "Степента не се променя."))),
                "hints", List.of("h1", "h2", "h3"), "sourceDeclaration", "Оригинално");
        JsonNode created = ok(post("/api/content/questions"), author, Map.of("draft", draft));
        String qid = created.get("id").asText();
        assertThat(created.get("validationProblems").size()).isZero();
        call(post("/api/content/questions/" + qid + "/review"), author, Map.of("approve", true), status().isForbidden());
        ok(post("/api/content/questions/" + qid + "/submit"), author, null);
        JsonNode rejected = ok(post("/api/content/questions/" + qid + "/review"), reviewer, Map.of("approve", false, "comment", "Добави пример"));
        assertThat(rejected.get("status").asText()).isEqualTo("DRAFT");
        ok(post("/api/content/questions/" + qid + "/submit"), author, null);
        JsonNode approved = ok(post("/api/content/questions/" + qid + "/review"), reviewer, Map.of("approve", true, "comment", "OK"));
        assertThat(approved.get("status").asText()).isEqualTo("PUBLISHED");

        // A student answers a test item; then the key of that item is corrected and the attempt is recalculated.
        String token = student("Коригиран", "FROM_14");
        String testId = ok(get("/api/student/tests"), token, null).get(0).get("id").asText();
        UUID attemptId = UUID.randomUUID();
        JsonNode attempt = ok(post("/api/student/tests/" + testId + "/attempts"), token, Map.of("attemptId", attemptId));
        JsonNode item = null;
        for (JsonNode i : attempt.get("items")) if (i.at("/question/key").asText().equals("BT1-14")) item = i;
        int pos = item.get("position").asInt();
        ok(put("/api/student/attempts/" + attemptId + "/items/" + pos), token, Map.of("requestId", req(), "answer", Map.of("value", "5")));
        double before = ok(post("/api/student/attempts/" + attemptId + "/submit"), token, null).get("points").asDouble();

        JsonNode corrected = ok(post("/api/content/questions/by-key/BT1-14/correct-key"), reviewer, Map.of(
                "reason", "Проверка на преизчисляването",
                "key", Map.of("answer", "5", "solution", "демо корекция",
                        "distractors", List.of(Map.of("match", "7", "misconception", "REASONING", "explanation", "7 е коефициентът.")))));
        assertThat(corrected.get("version").asInt()).isEqualTo(2);
        JsonNode after = ok(get("/api/student/attempts/" + attemptId + "/result"), token, null);
        assertThat(after.get("points").asDouble()).isEqualTo(before + 2);
        assertThat(after.get("corrections").get(0).asText()).contains("преизчислен");
        JsonNode history = ok(get("/api/content/history/BT1-14"), reviewer, null);
        assertThat(history.toString()).contains("ANSWER_KEY_CORRECTED");
    }

    // ------------------------------------------------------------------ security

    @Test
    void administratorsNeedMfaAndRolesAreEnforcedServerSide() throws Exception {
        JsonNode noCode = call(post("/api/auth/login"), null, Map.of("username", "admin", "password", "Test-Password-1"),
                status().isUnauthorized());
        assertThat(noCode.get("code").asText()).isEqualTo("MFA_REQUIRED");
        String admin = staff("admin");
        assertThat(ok(get("/api/admin/access-review"), admin, null).size()).isGreaterThan(0);
        assertThat(ok(get("/api/admin/audit"), admin, null).size()).isGreaterThan(0);

        String studentToken = student("Любопитко", "FROM_14");
        call(get("/api/admin/audit"), studentToken, null, status().isForbidden());
        call(get("/api/teacher/classes"), studentToken, null, status().isForbidden());
        call(get("/api/content/questions"), studentToken, null, status().isForbidden());
        call(get("/api/student/home"), null, null, status().isUnauthorized());
    }
}
