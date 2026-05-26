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
@Table(name = "fix_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "initiated_by_user_id", nullable = false)
    private Long initiatedByUserId;

    @Column(name = "auto_mode", nullable = false)
    private boolean autoMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FixRunState state;

    @Column(name = "branch_name", length = 500)
    private String branchName;

    @Column(name = "commit_sha", length = 80)
    private String commitSha;

    @Column(name = "pr_url", length = 1000)
    private String prUrl;

    @Column(name = "safety_score")
    private Integer safetyScore;

    @Column(name = "safety_blocked_reason", columnDefinition = "NVARCHAR(MAX)")
    private String safetyBlockedReason;

    @Column(name = "issue_ids_json", columnDefinition = "NVARCHAR(MAX)")
    private String issueIdsJson;

    @Column(name = "failure_reason", columnDefinition = "NVARCHAR(MAX)")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
