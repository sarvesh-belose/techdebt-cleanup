-- Sprint 4 — per-project Safety Guard threshold.
-- Score scale: 0 = benign, 100 = maximum impact. Diffs scoring >= safety_threshold are blocked.
-- Default 60 is calibrated against the curated signal corpus (Sprint 8 will retune from prod data).
ALTER TABLE project ADD safety_threshold INT NOT NULL DEFAULT 60;
