-- Sprint 8 — per-project retention window for terminal fix runs and read notifications.
-- Audit events are NOT subject to reap; that's enforced at the application layer
-- (RetentionReaper never touches audit_event) and at the DB layer once trigger-based
-- append-only enforcement lands on MSSQL.

ALTER TABLE project ADD retention_days INT NOT NULL DEFAULT 90;
