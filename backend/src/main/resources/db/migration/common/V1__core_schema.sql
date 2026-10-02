-- Math Mission core schema. Portable between PostgreSQL and H2 (PostgreSQL mode).
-- JSON documents are stored as text and mapped in Java; ids are UUIDs so the offline
-- client can create attempts and saves idempotently.

-- ---------------------------------------------------------------- identity
CREATE TABLE user_account (
    id               UUID PRIMARY KEY,
    role             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    nickname         VARCHAR(40),
    avatar           VARCHAR(40),
    username         VARCHAR(80) UNIQUE,
    password_hash    VARCHAR(100),
    totp_secret      VARCHAR(64),
    age_band         VARCHAR(20),
    learning_goal    VARCHAR(30),
    confidence       INT,
    weekly_goal      INT          NOT NULL DEFAULT 3,
    xp               INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    last_active_at   TIMESTAMP WITH TIME ZONE
);

CREATE TABLE auth_session (
    id          UUID PRIMARY KEY,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES user_account (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ---------------------------------------------------------------- consent and privacy
CREATE TABLE guardian_consent (
    id                   UUID PRIMARY KEY,
    student_id           UUID        NOT NULL REFERENCES user_account (id),
    guardian_id          UUID REFERENCES user_account (id),
    consent_code         VARCHAR(12) NOT NULL UNIQUE,
    text_version         VARCHAR(20) NOT NULL,
    scope                VARCHAR(200) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    verification_status  VARCHAR(30) NOT NULL,
    notification_freq    VARCHAR(20) NOT NULL DEFAULT 'WEEKLY',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_at           TIMESTAMP WITH TIME ZONE,
    withdrawn_at         TIMESTAMP WITH TIME ZONE
);

CREATE TABLE privacy_request (
    id            UUID PRIMARY KEY,
    student_id    UUID        NOT NULL,
    requested_by  UUID        NOT NULL,
    kind          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at  TIMESTAMP WITH TIME ZONE
);

-- ---------------------------------------------------------------- content
CREATE TABLE question (
    id                  UUID PRIMARY KEY,
    question_key        VARCHAR(60)  NOT NULL,
    version             INT          NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    path                VARCHAR(2)   NOT NULL,
    skill               VARCHAR(60)  NOT NULL,
    learning_outcome    VARCHAR(200) NOT NULL,
    academic_year       VARCHAR(9)   NOT NULL,
    response_type       VARCHAR(20)  NOT NULL,
    difficulty          VARCHAR(15)  NOT NULL,
    estimated_seconds   INT          NOT NULL,
    max_points          DOUBLE PRECISION NOT NULL,
    misconception       VARCHAR(30),
    prompt_json         VARCHAR(20000)  NOT NULL,
    key_json            VARCHAR(20000)  NOT NULL,
    hints_json          VARCHAR(5000)   NOT NULL,
    source_declaration  VARCHAR(200) NOT NULL,
    author_id           UUID         NOT NULL,
    reviewer_id         UUID,
    reviewed_at         TIMESTAMP WITH TIME ZONE,
    review_comment      VARCHAR(2000),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_question_version UNIQUE (question_key, version)
);
CREATE INDEX ix_question_status_skill ON question (status, skill);

CREATE TABLE lesson (
    id                 UUID PRIMARY KEY,
    lesson_key         VARCHAR(20)  NOT NULL,
    version            INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    path               VARCHAR(2)   NOT NULL,
    title              VARCHAR(200) NOT NULL,
    learning_outcome   VARCHAR(200) NOT NULL,
    academic_year      VARCHAR(9)   NOT NULL,
    content_json       VARCHAR(200000) NOT NULL,
    author_id          UUID         NOT NULL,
    reviewer_id        UUID,
    reviewed_at        TIMESTAMP WITH TIME ZONE,
    review_comment     VARCHAR(2000),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_lesson_version UNIQUE (lesson_key, version)
);

CREATE TABLE review_decision (
    id            UUID PRIMARY KEY,
    content_type  VARCHAR(20) NOT NULL,
    content_id    UUID        NOT NULL,
    content_key   VARCHAR(60) NOT NULL,
    version       INT         NOT NULL,
    reviewer_id   UUID        NOT NULL,
    decision      VARCHAR(20) NOT NULL,
    comment       VARCHAR(2000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ---------------------------------------------------------------- assessment
CREATE TABLE test_blueprint (
    id                UUID PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    academic_year     VARCHAR(9)   NOT NULL,
    version           INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    composition_json  VARCHAR(5000) NOT NULL,
    time_limit_min    INT          NOT NULL,
    question_count    INT          NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE test_definition (
    id                   UUID PRIMARY KEY,
    test_key             VARCHAR(60)  NOT NULL UNIQUE,
    title                VARCHAR(200) NOT NULL,
    kind                 VARCHAR(20)  NOT NULL,
    path                 VARCHAR(2)   NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    blueprint_id         UUID REFERENCES test_blueprint (id),
    time_limit_min       INT          NOT NULL,
    grading_json         VARCHAR(2000) NOT NULL,
    question_ids_json    VARCHAR(10000) NOT NULL,
    hint_policy          VARCHAR(20)  NOT NULL,
    review_moment        VARCHAR(20)  NOT NULL,
    created_by           UUID         NOT NULL,
    academic_year        VARCHAR(9)   NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE test_attempt (
    id               UUID PRIMARY KEY,
    test_id          UUID        NOT NULL REFERENCES test_definition (id),
    student_id       UUID        NOT NULL REFERENCES user_account (id),
    assignment_id    UUID,
    status           VARCHAR(20) NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline_at      TIMESTAMP WITH TIME ZONE,
    submitted_at     TIMESTAMP WITH TIME ZONE,
    last_position    INT         NOT NULL DEFAULT 0,
    points           DOUBLE PRECISION,
    max_points       DOUBLE PRECISION,
    percent          DOUBLE PRECISION,
    grade            INT,
    scoring_source   VARCHAR(20),
    version          BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_attempt_student ON test_attempt (student_id, started_at);

CREATE TABLE item_response (
    id                 UUID PRIMARY KEY,
    attempt_id         UUID        NOT NULL REFERENCES test_attempt (id),
    question_id        UUID        NOT NULL REFERENCES question (id),
    position           INT         NOT NULL,
    response_json      VARCHAR(10000),
    marked_for_review  BOOLEAN     NOT NULL DEFAULT FALSE,
    hints_used         INT         NOT NULL DEFAULT 0,
    status             VARCHAR(20),
    points             DOUBLE PRECISION,
    misconception      VARCHAR(30),
    feedback           VARCHAR(2000),
    scored_by          VARCHAR(20),
    reviewer_id        UUID,
    review_comment     VARCHAR(2000),
    last_request_id    VARCHAR(64),
    saved_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_response_item UNIQUE (attempt_id, position)
);

CREATE TABLE correction_notice (
    id           UUID PRIMARY KEY,
    attempt_id   UUID         NOT NULL REFERENCES test_attempt (id),
    student_id   UUID         NOT NULL,
    question_key VARCHAR(60)  NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    old_points   DOUBLE PRECISION NOT NULL,
    new_points   DOUBLE PRECISION NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ---------------------------------------------------------------- progress and practice
CREATE TABLE practice_response (
    id                 UUID PRIMARY KEY,
    request_id         VARCHAR(64) NOT NULL UNIQUE,
    student_id         UUID        NOT NULL REFERENCES user_account (id),
    question_id        UUID        NOT NULL REFERENCES question (id),
    lesson_key         VARCHAR(20),
    correct            BOOLEAN     NOT NULL,
    hints_used         INT         NOT NULL,
    misconception      VARCHAR(30),
    corrects_previous  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_practice_student ON practice_response (student_id, created_at);

CREATE TABLE skill_mastery (
    id               UUID PRIMARY KEY,
    student_id       UUID        NOT NULL REFERENCES user_account (id),
    skill            VARCHAR(60) NOT NULL,
    score            DOUBLE PRECISION NOT NULL,
    attempts         INT         NOT NULL,
    correct          INT         NOT NULL,
    hints_used       INT         NOT NULL,
    consecutive_errors INT       NOT NULL DEFAULT 0,
    state            VARCHAR(20) NOT NULL,
    review_stage     INT         NOT NULL DEFAULT 0,
    next_review_at   TIMESTAMP WITH TIME ZONE,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_mastery UNIQUE (student_id, skill)
);

CREATE TABLE lesson_progress (
    id            UUID PRIMARY KEY,
    student_id    UUID        NOT NULL REFERENCES user_account (id),
    lesson_key    VARCHAR(20) NOT NULL,
    position      INT         NOT NULL,
    max_position  INT         NOT NULL,
    status        VARCHAR(20) NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_lesson_progress UNIQUE (student_id, lesson_key)
);

CREATE TABLE reward (
    id          UUID PRIMARY KEY,
    student_id  UUID         NOT NULL REFERENCES user_account (id),
    code        VARCHAR(60)  NOT NULL,
    title       VARCHAR(120) NOT NULL,
    xp          INT          NOT NULL,
    source_ref  VARCHAR(80)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_reward_source UNIQUE (student_id, code, source_ref)
);

-- ---------------------------------------------------------------- classroom
CREATE TABLE class_group (
    id            UUID PRIMARY KEY,
    teacher_id    UUID         NOT NULL REFERENCES user_account (id),
    name          VARCHAR(80)  NOT NULL,
    code          VARCHAR(12)  NOT NULL UNIQUE,
    code_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE class_membership (
    id          UUID PRIMARY KEY,
    class_id    UUID        NOT NULL REFERENCES class_group (id),
    student_id  UUID        NOT NULL REFERENCES user_account (id),
    status      VARCHAR(20) NOT NULL,
    joined_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    removed_at  TIMESTAMP WITH TIME ZONE
);

CREATE TABLE assignment (
    id                UUID PRIMARY KEY,
    class_id          UUID         NOT NULL REFERENCES class_group (id),
    teacher_id        UUID         NOT NULL,
    target_type       VARCHAR(10)  NOT NULL,
    target_key        VARCHAR(60)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    student_ids_json  VARCHAR(10000),
    deadline          TIMESTAMP WITH TIME ZONE,
    allowed_attempts  INT          NOT NULL,
    hint_policy       VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ---------------------------------------------------------------- audit (append-only)
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY,
    actor_id     UUID,
    action       VARCHAR(60)  NOT NULL,
    entity_type  VARCHAR(40)  NOT NULL,
    entity_id    VARCHAR(80)  NOT NULL,
    details      VARCHAR(4000),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id);
