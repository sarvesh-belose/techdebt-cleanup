package com.enterprise.techdebt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "azure_repo_url", nullable = false, length = 1000)
    private String azureRepoUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "auto_mode_enabled", nullable = false)
    private boolean autoModeEnabled;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "safety_threshold", nullable = false)
    private int safetyThreshold;

    @Column(name = "auto_rate_limit_per_hour", nullable = false)
    private int autoRateLimitPerHour;

    @Column(name = "auto_policy_json", columnDefinition = "NVARCHAR(MAX)")
    private String autoPolicyJson;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
