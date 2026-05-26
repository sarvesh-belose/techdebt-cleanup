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
@Table(name = "issue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IssueSource source;

    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IssueStatus status;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "file_path", length = 2000)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(length = 50)
    private String cwe;

    @Column(length = 50)
    private String cve;

    @Column(name = "component_name", length = 500)
    private String componentName;

    @Column(name = "current_version", length = 100)
    private String currentVersion;

    @Column(name = "safe_version", length = 100)
    private String safeVersion;

    @Column(name = "is_transitive")
    private Boolean isTransitive;

    @Column(name = "transitive_path", columnDefinition = "NVARCHAR(MAX)")
    private String transitivePath;

    @Column(name = "raw_payload", columnDefinition = "NVARCHAR(MAX)")
    private String rawPayload;

    @Column(name = "wont_fix_reason", columnDefinition = "NVARCHAR(MAX)")
    private String wontFixReason;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
