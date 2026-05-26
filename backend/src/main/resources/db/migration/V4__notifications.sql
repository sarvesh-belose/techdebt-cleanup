-- Sprint 7 — in-app notification inbox + per-user channel preferences.
-- A notification can be addressed to a specific user (user_id), or scoped to a project
-- (user_id null, project_id set) for project-watchers; per-user fan-out happens at read
-- time via project_permission.

CREATE TABLE notification (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT          NULL,
    project_id      BIGINT          NULL,
    fix_run_id      BIGINT          NULL,
    event_type      VARCHAR(40)     NOT NULL,
    severity        VARCHAR(20)     NOT NULL DEFAULT 'INFO',
    title           VARCHAR(500)    NOT NULL,
    body            NVARCHAR(MAX)   NULL,
    deeplink        VARCHAR(1000)   NULL,
    read_at         DATETIME2       NULL,
    created_at      DATETIME2       NOT NULL,
    CONSTRAINT fk_notif_user    FOREIGN KEY (user_id)    REFERENCES app_user(id),
    CONSTRAINT fk_notif_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE INDEX ix_notification_user_unread ON notification (user_id, read_at, created_at);
CREATE INDEX ix_notification_project     ON notification (project_id, created_at);

-- Per-user preferences for which channels deliver which events. Absence of a row =
-- platform defaults (in-app on, Teams off, email off).
CREATE TABLE notification_preference (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    event_type      VARCHAR(40)     NOT NULL,
    channel         VARCHAR(20)     NOT NULL,   -- IN_APP / TEAMS / EMAIL
    enabled         BIT             NOT NULL DEFAULT 1,
    updated_at      DATETIME2       NOT NULL,
    CONSTRAINT uq_notif_pref UNIQUE (user_id, event_type, channel),
    CONSTRAINT fk_notif_pref_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
