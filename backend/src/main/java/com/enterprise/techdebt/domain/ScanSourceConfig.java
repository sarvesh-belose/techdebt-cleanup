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
@Table(name = "scan_source_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IssueSource source;

    @Column(name = "external_project_id", nullable = false, length = 500)
    private String externalProjectId;

    @Column(name = "api_base_url", nullable = false, length = 1000)
    private String apiBaseUrl;

    @Column(name = "secret_key_vault_ref", nullable = false, length = 500)
    private String secretKeyVaultRef;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "coverage_threshold_pct")
    private Integer coverageThresholdPct;

    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
