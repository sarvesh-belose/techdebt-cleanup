-- TechDebt Cleanup Platform — initial schema.
-- Written in MSSQL-compatible SQL so the same migration runs against
-- H2 (MODE=MSSQLServer) for local/dev and Microsoft SQL Server for production.

CREATE TABLE app_user (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    upn             VARCHAR(255)  NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    display_name    VARCHAR(255)  NULL,
    enabled         BIT           NOT NULL DEFAULT 1,
    created_at      DATETIME2     NOT NULL,
    last_login_at   DATETIME2     NULL,
    CONSTRAINT uq_app_user_upn UNIQUE (upn)
);

CREATE TABLE project (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    name                VARCHAR(200)  NOT NULL,
    description         VARCHAR(2000) NULL,
    azure_repo_url      VARCHAR(1000) NOT NULL,
    default_branch      VARCHAR(200)  NOT NULL DEFAULT 'main',
    owner_user_id       BIGINT        NOT NULL,
    auto_mode_enabled   BIT           NOT NULL DEFAULT 0,
    archived            BIT           NOT NULL DEFAULT 0,
    created_at          DATETIME2     NOT NULL,
    updated_at          DATETIME2     NOT NULL,
    CONSTRAINT uq_project_name UNIQUE (name),
    CONSTRAINT fk_project_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);

CREATE INDEX ix_project_archived ON project (archived);

CREATE TABLE project_permission (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    project_id            BIGINT       NOT NULL,
    user_id               BIGINT       NOT NULL,
    role                  VARCHAR(40)  NOT NULL,
    granted_at            DATETIME2    NOT NULL,
    granted_by_user_id    BIGINT       NOT NULL,
    CONSTRAINT uq_project_permission UNIQUE (project_id, user_id),
    CONSTRAINT fk_pp_project   FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_pp_user      FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_pp_granted_by FOREIGN KEY (granted_by_user_id) REFERENCES app_user(id)
);

CREATE TABLE scan_source_config (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    project_id               BIGINT        NOT NULL,
    source                   VARCHAR(40)   NOT NULL,
    external_project_id      VARCHAR(500)  NOT NULL,
    api_base_url             VARCHAR(1000) NOT NULL,
    secret_key_vault_ref     VARCHAR(500)  NOT NULL,
    enabled                  BIT           NOT NULL DEFAULT 1,
    cron_expression          VARCHAR(100)  NULL,
    coverage_threshold_pct   INT           NULL,
    created_at               DATETIME2     NOT NULL,
    updated_at               DATETIME2     NOT NULL,
    CONSTRAINT uq_scan_source UNIQUE (project_id, source),
    CONSTRAINT fk_ssc_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE issue (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    project_id         BIGINT          NOT NULL,
    source             VARCHAR(40)     NOT NULL,
    external_id        VARCHAR(500)    NOT NULL,
    severity           VARCHAR(20)     NOT NULL,
    status             VARCHAR(40)     NOT NULL,
    title              VARCHAR(1000)   NOT NULL,
    description        NVARCHAR(MAX)   NULL,
    file_path          VARCHAR(2000)   NULL,
    line_number        INT             NULL,
    cwe                VARCHAR(50)     NULL,
    cve                VARCHAR(50)     NULL,
    component_name     VARCHAR(500)    NULL,
    current_version    VARCHAR(100)    NULL,
    safe_version       VARCHAR(100)    NULL,
    is_transitive      BIT             NULL,
    transitive_path    NVARCHAR(MAX)   NULL,
    raw_payload        NVARCHAR(MAX)   NULL,
    first_seen         DATETIME2       NOT NULL,
    last_seen          DATETIME2       NOT NULL,
    created_at         DATETIME2       NOT NULL,
    updated_at         DATETIME2       NOT NULL,
    CONSTRAINT uq_issue_external UNIQUE (project_id, source, external_id),
    CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE INDEX ix_issue_status   ON issue (project_id, status);
CREATE INDEX ix_issue_severity ON issue (project_id, severity);
CREATE INDEX ix_issue_source   ON issue (project_id, source);

CREATE TABLE fix_run (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    project_id               BIGINT          NOT NULL,
    initiated_by_user_id     BIGINT          NOT NULL,
    auto_mode                BIT             NOT NULL DEFAULT 0,
    state                    VARCHAR(40)     NOT NULL,
    branch_name              VARCHAR(500)    NULL,
    commit_sha               VARCHAR(80)     NULL,
    pr_url                   VARCHAR(1000)   NULL,
    safety_score             INT             NULL,
    safety_blocked_reason    NVARCHAR(MAX)   NULL,
    issue_ids_json           NVARCHAR(MAX)   NULL,
    failure_reason           NVARCHAR(MAX)   NULL,
    created_at               DATETIME2       NOT NULL,
    updated_at               DATETIME2       NOT NULL,
    completed_at             DATETIME2       NULL,
    CONSTRAINT fk_fr_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_fr_user    FOREIGN KEY (initiated_by_user_id) REFERENCES app_user(id)
);

CREATE INDEX ix_fix_run_state ON fix_run (project_id, state);

CREATE TABLE fix_attempt (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    fix_run_id              BIGINT          NOT NULL,
    attempt_number          INT             NOT NULL,
    diff                    NVARCHAR(MAX)   NULL,
    build_outcome           VARCHAR(40)     NULL,
    build_log_excerpt       NVARCHAR(MAX)   NULL,
    safety_score            INT             NULL,
    safety_signals_json     NVARCHAR(MAX)   NULL,
    started_at              DATETIME2       NOT NULL,
    completed_at            DATETIME2       NULL,
    CONSTRAINT uq_attempt_number UNIQUE (fix_run_id, attempt_number),
    CONSTRAINT fk_fa_run FOREIGN KEY (fix_run_id) REFERENCES fix_run(id)
);

-- Append-only audit log.
-- Java-side discipline: AuditEventRepository exposes save() and query methods only, no update/delete.
-- DB-level enforcement (INSTEAD OF UPDATE/DELETE triggers) is added in a vendor-specific migration
-- when the platform is promoted to MSSQL — H2 trigger syntax differs and is not used in pilot.
CREATE TABLE audit_event (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    occurred_at       DATETIME2       NOT NULL,
    actor_user_id     BIGINT          NULL,
    actor_upn         VARCHAR(255)    NULL,
    action            VARCHAR(100)    NOT NULL,
    resource_type     VARCHAR(100)    NOT NULL,
    resource_id       VARCHAR(100)    NULL,
    project_id        BIGINT          NULL,
    correlation_id    VARCHAR(100)    NULL,
    before_value      NVARCHAR(MAX)   NULL,
    after_value       NVARCHAR(MAX)   NULL,
    detail            NVARCHAR(MAX)   NULL
);

CREATE INDEX ix_audit_project ON audit_event (project_id, occurred_at);
CREATE INDEX ix_audit_actor   ON audit_event (actor_user_id, occurred_at);
CREATE INDEX ix_audit_action  ON audit_event (action, occurred_at);
