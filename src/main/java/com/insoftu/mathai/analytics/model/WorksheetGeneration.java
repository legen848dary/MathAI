package com.insoftu.mathai.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worksheet_generations")
public class WorksheetGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false)
    private Integer grade;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "elapsed_ms", nullable = false)
    private Long elapsedMs;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "batch_count", nullable = false)
    private Integer batchCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WorksheetGeneration() {}

    public WorksheetGeneration(String provider, Integer grade, String topic, String difficulty,
                               Integer questionCount, Boolean success, Long elapsedMs,
                               String errorType, Integer batchCount) {
        this.provider = provider;
        this.grade = grade;
        this.topic = topic;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
        this.success = success;
        this.elapsedMs = elapsedMs;
        this.errorType = errorType;
        this.batchCount = batchCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public Integer getGrade() { return grade; }
    public String getTopic() { return topic; }
    public String getDifficulty() { return difficulty; }
    public Integer getQuestionCount() { return questionCount; }
    public Boolean getSuccess() { return success; }
    public Long getElapsedMs() { return elapsedMs; }
    public String getErrorType() { return errorType; }
    public Integer getBatchCount() { return batchCount; }
    public Instant getCreatedAt() { return createdAt; }
}
