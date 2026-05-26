-- Sprint 6 — auto-mode policy + safety brake + scheduled scans.

-- Per-project rate limit for auto runs (per hour). 0 = unlimited (use the global kill switch
-- to halt everything across the org).
ALTER TABLE project ADD auto_rate_limit_per_hour INT NOT NULL DEFAULT 5;

-- JSON policy: which (source, severity) combinations are eligible for auto-mode.
-- Example: {"VERACODE_SCA":["CRITICAL","HIGH"],"VERACODE_SAST":[],"SONARQUBE":[],"JUNIT_COVERAGE":[]}
-- Null = nothing is eligible for auto (manual review required for every fix).
ALTER TABLE project ADD auto_policy_json NVARCHAR(MAX) NULL;

-- Track when each scan source last ran so the scheduler knows what's due.
ALTER TABLE scan_source_config ADD last_scan_at DATETIME2 NULL;

-- Global key/value system settings (kill switch, future tunables). Tiny table on purpose —
-- one row per setting key; reads cached in memory.
CREATE TABLE system_setting (
    setting_key   VARCHAR(100)   NOT NULL PRIMARY KEY,
    value_text    NVARCHAR(MAX)  NULL,
    updated_at    DATETIME2      NOT NULL,
    updated_by    VARCHAR(255)   NULL
);

-- Issue carries a separate column for the reason behind a WONT_FIX so the UI can
-- surface it without re-reading audit_event.
ALTER TABLE issue ADD wont_fix_reason NVARCHAR(MAX) NULL;
