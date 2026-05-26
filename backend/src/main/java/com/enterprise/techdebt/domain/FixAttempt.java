package com.enterprise.techdebt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fix_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fix_run_id", nullable = false)
    private Long fixRunId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String diff;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_outcome", length = 40)
    private BuildOutcome buildOutcome;

    @Column(name = "build_log_excerpt", columnDefinition = "NVARCHAR(MAX)")
    private String buildLogExcerpt;

    @Column(name = "safety_score")
    private Integer safetyScore;

    @Column(name = "safety_signals_json", columnDefinition = "NVARCHAR(MAX)")
    private String safetySignalsJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
