CREATE TABLE worksheet_generations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider        VARCHAR(50)     NOT NULL,
    grade           INTEGER         NOT NULL,
    topic           VARCHAR(255)    NOT NULL,
    difficulty      VARCHAR(20)     NOT NULL,
    question_count  INTEGER         NOT NULL,
    success         BOOLEAN         NOT NULL,
    elapsed_ms      BIGINT          NOT NULL,
    error_type      VARCHAR(100),
    batch_count     INTEGER         NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_generations_created_at ON worksheet_generations (created_at);
CREATE INDEX idx_generations_provider_created ON worksheet_generations (provider, created_at);
