# TechDebt Cleanup Platform — User Stories

## Personas

| Persona | Description |
|---|---|
| **DEV** — Developer | Owns a Spring Boot microservice; fixes issues day-to-day |
| **TL** — Tech Lead | Approves PRs, reviews safety blocks, configures per-project policy |
| **PM** — Project Manager | Tracks tech-debt burn-down for delivery planning |
| **ADM** — Platform Admin | Onboards projects, manages credentials, controls global settings |
| **SEC** — Security Officer / InfoSec | Audits actions, validates compliance of fixes |
| **EXEC** — Management Executive | Consumes org-wide tech-debt health |
| **SRE** — Platform Operator | Runs the platform itself; monitors health and incidents |

## Story Sizing

Estimates use the Fibonacci scale: **1, 2, 3, 5, 8, 13**.
- 1–2: trivial, half-day or less
- 3: one developer-day
- 5: 2–3 developer-days
- 8: most of a sprint for one dev
- 13: spans more than a sprint — split before pulling

## Definition of Ready

A story is "ready" when: persona is clear, acceptance criteria are testable, dependencies are listed, designs (if UI) are linked, and the team has agreed on the estimate.

## Definition of Done

Code merged, unit + integration tests passing, security checks green, observability hooks added (logs/metrics), documentation updated, demoed to PO, audit-log writes verified.

---

# Epic A — Platform Foundation & Auth

### A1 — Azure AD SSO login [5]
**As a** DEV, **I want** to log in to the platform with my corporate Azure AD account, **so that** I don't need a separate credential and my actions are tied to my identity.
**Acceptance:**
- Hitting any platform URL when unauthenticated redirects to Azure AD OIDC.
- After successful auth, my email/UPN appears in the header.
- JWT validated on every API call; expiry triggers silent re-auth.
- A user record is upserted on first login.

### A2 — Role-based access control [5]
**As an** ADM, **I want** to assign users one of `Viewer`, `Developer`, `Approver`, `Admin` per project, **so that** least-privilege is enforced.
**Acceptance:**
- API endpoints annotated with required role; 403 returned otherwise.
- Per-project permissions stored in `project_permission` table.
- UI hides actions the user isn't authorized for.
- Role changes are audit-logged.

### A3 — Secrets in Azure Key Vault [5]
**As an** SEC, **I want** Veracode/SonarQube/Copilot tokens stored in Azure Key Vault, **so that** plaintext secrets never live in the database or logs.
**Acceptance:**
- Spring Cloud Azure Key Vault starter wired in.
- Credential write/read goes through a `SecretStore` abstraction.
- DB stores only Key Vault references, never raw token bytes.
- Logs scrub any field tagged `@Sensitive`.

### A4 — Health and readiness endpoints [2]
**As an** SRE, **I want** `/actuator/health` and `/actuator/info`, **so that** I can wire the app into k8s probes and our monitoring.
**Acceptance:** Liveness, readiness, and dependency checks (DB, Key Vault, Veracode reachability) exposed; readiness fails fast if a critical dep is down.

---

# Epic B — Project Management

### B1 — Register a new project [3]
**As an** ADM, **I want** to register a Spring Boot service by providing name, Azure Repos URL, and default branch, **so that** issues can be ingested for it.
**Acceptance:**
- Validation: URL pattern, unique name.
- Audit event written.
- Project appears in the dashboard immediately.

### B2 — Configure scan sources per project [5]
**As an** ADM, **I want** to attach Veracode app ID + SonarQube project key + tokens to a project, **so that** the platform knows where to fetch issues.
**Acceptance:**
- "Test connection" button validates each token before save.
- Tokens stored in Key Vault, not DB.
- One Veracode and one SonarQube config per project (initial).

### B3 — Edit / archive a project [2]
**As an** ADM, **I want** to edit project metadata and archive projects that are decommissioned, **so that** the inventory stays clean.
**Acceptance:** Archived projects hidden from default views; their historical issues and runs remain readable.

### B4 — Onboarding wizard [5]
**As an** ADM, **I want** a step-by-step wizard for onboarding a project (basics → credentials → test → first scan), **so that** I don't miss configuration steps.
**Acceptance:** Wizard cannot proceed past a step until validations pass; abandoning saves draft state.

---

# Epic C — Issue Ingestion: Veracode SCA

### C1 — Pluggable connector interface [3]
**As a** developer of the platform, **I want** a `IssueSourceConnector` interface, **so that** new scan sources can be added without changing ingestion orchestration.
**Acceptance:** Interface includes `source()`, `fetchIssues(cfg)`. A no-op connector ships with tests demonstrating the contract.

### C2 — Veracode SCA fetch [8]
**As a** DEV, **I want** the platform to pull SCA findings for my project from Veracode, **so that** I can see CVEs against my dependencies in one place.
**Acceptance:**
- HMAC auth implemented per Veracode REST spec.
- For each finding, capture: CVE, vulnerable component, current version, available fix version, transitive flag, severity, first/last seen.
- Pagination handled; throttled per Veracode rate limits.
- Errors logged with correlation ID; partial failures don't drop other findings.

### C3 — Deduplicate and persist issues [5]
**As a** DEV, **I want** repeated scans not to duplicate issues, **so that** my inbox doesn't grow uncontrollably.
**Acceptance:**
- Unique key `(project, source, external_id)`.
- Re-import updates `last_seen` and severity; new issues only insert.
- Resolved-upstream issues auto-transition to `FIXED_EXTERNALLY` when no longer returned.

### C4 — Scheduled scans [3]
**As an** ADM, **I want** scans to run on a cron schedule per project, **so that** the inbox stays fresh without manual intervention.
**Acceptance:** Cron expression per project, default daily 02:00 local. Skips if a scan is already running. Last scan time visible.

### C5 — On-demand scan [2]
**As a** DEV, **I want** a "Scan now" button, **so that** I can pull the latest issues before starting a fix run.
**Acceptance:** Button disabled while scan running; live progress shown; completes within SLA or surfaces error.

---

# Epic D — Issue Ingestion: Veracode SAST

### D1 — Veracode SAST fetch [8]
**As a** DEV, **I want** SAST findings (CWE, file, line, source/sink) pulled into the platform, **so that** all security debt is in one inbox.
**Acceptance:**
- Each finding maps to an `issue` row with `source=VERACODE_SAST`, populated `cwe`, `file_path`, `line`.
- Source/sink stored in a structured field for later display.
- Severity, criticality, and exploitability captured.

### D2 — SAST mitigation status sync [3]
**As a** DEV, **I want** the platform to respect SAST findings I've already mitigated/accepted in Veracode, **so that** they don't reappear as open work.
**Acceptance:** Mitigation status read from Veracode and reflected as `WONT_FIX` with the mitigation reason visible.

---

# Epic E — Issue Ingestion: SonarQube Issues + Coverage

### E1 — SonarQube issue fetch [5]
**As a** DEV, **I want** SonarQube bugs, code smells, and vulnerabilities pulled in, **so that** they live alongside Veracode issues in my inbox.
**Acceptance:**
- `/api/issues/search` paginated; capture rule, severity, type, file, line, creation date, effort, debt.
- Filters by `resolved=false` only.
- Idempotent re-import (same dedup key as SCA).

### E2 — SonarQube coverage fetch [5]
**As a** DEV, **I want** files below the coverage threshold surfaced as `JUNIT_COVERAGE` issues, **so that** coverage gaps are tracked uniformly.
**Acceptance:**
- `/api/measures/component_tree` queried for `coverage`, `line_coverage`, `uncovered_lines`.
- One synthetic issue per file below threshold (default 80%, configurable per project).
- Auto-resolves when file goes above threshold on next scan.

### E3 — Drop the JaCoCo connector path [1]
**As a** platform dev, **I want** to confirm in design docs that coverage flows through SonarQube only, **so that** we don't accidentally build duplicate plumbing.
**Acceptance:** Architecture doc updated; no `JaCoCoConnector` class is introduced.

---

# Epic F — Issue Inbox & Triage

### F1 — List view with filters [5]
**As a** DEV, **I want** a filterable, sortable list of issues for my project, **so that** I can focus on the right work.
**Acceptance:**
- Filters: source, severity, status, file path, CVE, date-created range.
- Sort by severity, age, file.
- Pagination, URL-shareable filter state.

### F2 — Issue detail view [3]
**As a** DEV, **I want** a detail panel showing the full payload of an issue (CVE description, transitive path, code excerpt, history), **so that** I can decide on a fix approach.
**Acceptance:** Side panel opens without leaving the list; deep-link URL works.

### F3 — Bulk select for fix [3]
**As a** DEV, **I want** to multi-select issues with checkboxes and trigger a single Fix Run, **so that** I can batch related fixes.
**Acceptance:** Selection persists across pagination; "Fix Selected" disabled when empty; selection cleared after run starts.

### F4 — Mark "won't fix" with reason [2]
**As a** TL, **I want** to mark an issue won't-fix with a required reason, **so that** noise is removed without losing the audit trail.
**Acceptance:** Reason text mandatory; status transitions to `WONT_FIX`; audit event includes reason and actor.

### F5 — Quick "Fix All Critical" action [3]
**As a** DEV, **I want** a one-click action to start a Fix Run on all critical-severity issues for my project, **so that** I can sweep urgent debt fast.
**Acceptance:** Confirmation modal lists count and projects affected; honors per-source policy.

---

# Epic G — Fix Orchestration: Maven SCA

### G1 — Fix Run state machine [8]
**As a** platform dev, **I want** Fix Runs to progress through a persistent state machine, **so that** crashes don't lose progress and state is always inspectable.
**Acceptance:**
- States: `QUEUED → CLONING → FIXING → FAST_BUILD → CANONICAL_BUILD → PR_OPENED → MERGED / FAILED / ABORTED / SAFETY_BLOCKED`.
- Every transition persisted before side effects.
- Crash recovery resumes from last persisted state.

### G2 — Clone repo and create fix branch [3]
**As a** platform, **I want** to clone the project's Azure Repo and create a branch named `techdebt/{run-id}-…`, **so that** fixes are isolated.
**Acceptance:** Shallow clone for speed; branch is reproducible from `(run-id, source, shortdesc)`; workspace path is unique per run.

### G3 — Bump direct dependency version [5]
**As a** DEV, **I want** the platform to update the `<version>` of a direct dependency in `pom.xml` to the safe version, **so that** the SCA finding is resolved.
**Acceptance:**
- Uses `maven-model` parser, not regex.
- Preserves formatting / property usage (e.g., `${spring.version}` references updated in the right place).
- Unit-tested for direct-dep, parent-managed, and property-driven cases.

### G4 — Override transitive via dependencyManagement [5]
**As a** DEV, **I want** the platform to pin a vulnerable transitive dependency via `<dependencyManagement>`, **so that** CVEs not fixable by a direct bump are resolved.
**Acceptance:**
- Tries strategies in order: direct bump → parent bump that fixes transitive → `dependencyManagement` override → mark `SAFETY_BLOCKED`.
- Annotates the override with an XML comment containing the CVE ID and date.
- `mvn dependency:tree` confirms vulnerable version is gone.

### G5 — Verify no new CVE introduced by the bump [5]
**As a** SEC, **I want** the platform to re-scan the dependency tree after a bump, **so that** we don't trade one CVE for another.
**Acceptance:**
- Post-fix `dependency:tree` cross-referenced against current Veracode SCA findings for the project.
- Run marked `SAFETY_BLOCKED` if the bump introduces a new known CVE of equal-or-higher severity.

### G6 — Multi-module project support [5]
**As a** DEV, **I want** SCA fixes to work on multi-module aggregator poms, **so that** monorepo services aren't excluded.
**Acceptance:** Correctly resolves which child module owns the dep; respects parent BOM imports; idempotent on already-fixed modules.

---

# Epic H — Fix Orchestration: Copilot Source Edits

### H1 — Copilot CLI subprocess wrapper [8]
**As a** platform, **I want** a `CopilotDriver` that invokes `gh copilot` in the run workspace with a structured prompt, **so that** SAST/Sonar source fixes can be generated deterministically.
**Acceptance:**
- Prompt template includes source, severity, CWE, file/line, code excerpt, **explicit "preserve business logic" instruction**, and (on refix) the last build error log.
- Timeout configurable (default 5 min); transient `gh` errors retried once.
- Captures stdout/stderr/exit code; result is a unified diff stored on `fix_attempt`.

### H2 — Apply Copilot diff and commit [3]
**As a** platform, **I want** Copilot's diff applied to the workspace and committed, **so that** the change is captured under version control.
**Acceptance:** Apply fails fast on conflicts; commit message references issue IDs and run ID; signed-off with platform service account.

### H3 — Bounded refix loop [5]
**As a** platform, **I want** failed builds to feed back to Copilot as context for up to N retries, **so that** simple compilation errors self-heal.
**Acceptance:**
- Configurable `max_refix_attempts` (default 3).
- Each attempt is a new `fix_attempt` row.
- After exhaustion, run transitions to `FAILED` with branch preserved.

### H4 — Single-issue and batch fix modes [3]
**As a** DEV, **I want** to fix one issue at a time or batch multiple in one run, **so that** I can choose between safe-and-slow and bulk-and-fast.
**Acceptance:** Batch mode generates one diff containing changes for all selected issues; per-issue status (`FIXED`, `SKIPPED`, `FAILED`) recorded.

---

# Epic I — Safety Guard (business-logic-change detector)

### I1 — File-classification signal [3]
**As a** SEC, **I want** the safety guard to flag diffs touching `service/`, `domain/`, `controller/` directories more heavily, **so that** the highest-risk code gets the most scrutiny.
**Acceptance:** Configurable path globs per project; pom-only and test-only diffs score 0.

### I2 — AST-diff signal [8]
**As a** SEC, **I want** the safety guard to parse before/after Java with JavaParser and flag method-signature changes, branch logic changes, arithmetic changes, SQL string changes, and security annotation changes, **so that** behavior changes don't slip through.
**Acceptance:**
- Per-change-type weighting configurable.
- False-negative recall ≥ 95% on the curated behavior-changing diff corpus.
- False-positive rate ≤ 20% on the behavior-preserving corpus.

### I3 — Test-delta signal [3]
**As a** SEC, **I want** the safety guard to flag diffs where Copilot also modified tests for the changed method, **so that** behavior changes hidden by test rewrites are surfaced.
**Acceptance:** Cross-references changed test methods to changed production methods; flags when overlap exists.

### I4 — LLM second-opinion signal [3]
**As a** SEC, **I want** a corroborating LLM check asking "does this diff change observable behavior?", **so that** subtle changes get a second look.
**Acceptance:** Used as one signal among many, never the sole gate; logged with the run.

### I5 — Safety block UI [5]
**As a** TL, **I want** a side-by-side diff view with flagged hunks and the signals that fired, **so that** I can decide whether to override.
**Acceptance:** Override action requires reason text; recorded in audit log; counts toward team safety-override metric.

### I6 — Safety threshold config per project [2]
**As an** ADM, **I want** to tune the safety guard threshold per project, **so that** noisier projects can be tightened and stable ones loosened.
**Acceptance:** Default threshold ships; per-project override available; threshold changes audit-logged.

---

# Epic J — Build Verification: Fast Tier

### J1 — Maven runner pool [8]
**As an** SRE, **I want** a pool of pre-warmed Docker-based Maven runners on Kubernetes, **so that** fix verifications run in seconds, not minutes.
**Acceptance:** Helm chart deploys the pool; runners scale on queue depth; shared `.m2` cache volume mounted; Nexus mirror configured.

### J2 — Run fast build (mvn -o clean install -DskipITs) [5]
**As a** DEV, **I want** the platform to run an offline `mvn clean install -DskipITs` on the fix branch, **so that** compile + unit-test failures are caught quickly.
**Acceptance:** Build runs in a fresh container; exit code, log, and duration captured on `fix_attempt`.

### J3 — Stream build logs to UI [5]
**As a** DEV, **I want** the live build log streamed to my browser, **so that** I see progress without refreshing.
**Acceptance:** SSE channel keyed by run ID; last 500 lines retained server-side for late-joiners; auto-scroll with pause-on-hover.

### J4 — Surface failure summary [3]
**As a** DEV, **I want** failed builds to highlight the failing test or compilation error at the top of the log, **so that** I don't scroll through thousands of lines.
**Acceptance:** Failure parser extracts the first `[ERROR]` / surefire failure and pins it; full log still accessible.

---

# Epic K — Build Verification: Azure Pipelines (Canonical)

### K1 — Trigger Azure Pipeline on fix branch [5]
**As a** TL, **I want** the platform to trigger the project's existing Azure Pipeline against the fix branch, **so that** the canonical enterprise build still gates the PR.
**Acceptance:** Pipeline run launched via REST; run URL stored on `fix_run`; required pipeline configurable per project.

### K2 — Poll status and pull logs [3]
**As a** DEV, **I want** the platform to track the pipeline run status and pull logs on completion, **so that** I see the final canonical result in the UI.
**Acceptance:** Poll interval configurable; final status (`succeeded`, `failed`, `canceled`) reflected on `fix_run`; full log linkable.

### K3 — Block PR auto-merge until canonical green [3]
**As a** SEC, **I want** PR auto-merge gated on a green canonical pipeline, **so that** enterprise compliance checks always run.
**Acceptance:** Auto-merge guard enforced server-side; UI shows the gate state.

---

# Epic L — PR Creation & Workflow

### L1 — Open Azure Repos PR with rich body [5]
**As a** TL, **I want** the PR to include the list of issues fixed, source IDs, diff summary, safety-guard score, and audit-log link, **so that** review is fast.
**Acceptance:** PR title template configurable; body uses Markdown; required reviewers from project config added.

### L2 — Link work items [2]
**As a** PM, **I want** PRs to link any associated Azure Boards work items for the fixed issues, **so that** delivery tracking is automatic.
**Acceptance:** Work-item field per issue (if mapped); links rendered in PR description.

### L3 — Update PR on refix [3]
**As a** TL, **I want** the same PR updated when a refix succeeds, **so that** I don't see a flood of new PRs for the same effort.
**Acceptance:** Force-push protected after first review comment; otherwise updates branch and re-runs gates.

### L4 — Auto-merge after gates [3]
**As an** ADM, **I want** an opt-in auto-merge once all gates pass (canonical green, safety clean, required reviewers approved), **so that** trivial debt closes without human intervention.
**Acceptance:** Per-project flag; defaults to off; auto-merges only if zero outstanding comments.

---

# Epic M — Auto Mode

### M1 — Per-project auto-mode flag [2]
**As an** ADM, **I want** a toggle to switch a project from manual to fully automatic, **so that** projects ready for hands-off operation can be promoted.
**Acceptance:** Toggle audit-logged; warning modal explains implications.

### M2 — Per-source severity policy [3]
**As an** ADM, **I want** to set, per project, which (source, severity) pairs are eligible for auto-fix, **so that** I can auto-fix SCA Critical but require manual review for SAST.
**Acceptance:** Matrix UI; policy applied at run creation; respected by scheduler.

### M3 — Safety brake [3]
**As an** SRE, **I want** a per-project max-auto-runs-per-hour and a global kill switch, **so that** a misbehaving Copilot or scanner can't flood Azure Pipelines.
**Acceptance:** Rate limit enforced; kill switch flips all projects to manual instantly; both recorded in audit.

### M4 — Auto-mode notification digest [2]
**As a** PM, **I want** a daily digest of what auto-mode did yesterday (PRs opened, merged, blocked), **so that** I have visibility without watching every event.
**Acceptance:** Email + Teams; respects per-user preferences.

---

# Epic N — Audit & Compliance

### N1 — Append-only audit log [5]
**As a** SEC, **I want** an `audit_event` table that the application cannot UPDATE or DELETE, **so that** the trail is tamper-evident.
**Acceptance:** DB triggers block UPDATE/DELETE; integration test proves attempts fail; service account permissions restricted.

### N2 — Audit every mutation [3]
**As a** SEC, **I want** every state-changing API call to write a structured audit event, **so that** "who did what when" is always answerable.
**Acceptance:** Aspect-based interceptor auto-captures actor, action, before/after; coverage check in CI.

### N3 — Per-run compliance export (PDF) [5]
**As a** SEC, **I want** to export a complete Fix Run dossier as PDF (issues, diff, safety score, build outcome, approvals, timestamps), **so that** I can hand it to audit on request.
**Acceptance:** Single click; PDF generated server-side; signed with platform private key; downloadable for the run's retention window.

### N4 — Retention policy [3]
**As an** ADM, **I want** configurable retention for runs, workspaces, and audit log per bank policy, **so that** we comply with internal record-keeping rules.
**Acceptance:** Retention values configurable; reaper job removes expired data with audit event before deletion.

---

# Epic O — Notifications

### O1 — In-app inbox [3]
**As a** DEV, **I want** a bell-icon inbox of events relevant to me (my run failed, my safety block, my PR merged), **so that** I notice things without checking the UI.
**Acceptance:** Unread count; mark-as-read; deep-links to the run.

### O2 — Teams webhook integration [3]
**As a** TL, **I want** Teams channel notifications for `SAFETY_BLOCKED`, `RUN_FAILED`, `PR_OPENED`, **so that** the team is alerted in their primary tool.
**Acceptance:** Webhook URL per project; per-event-type toggles; templated messages.

### O3 — Email digest [2]
**As a** PM, **I want** an opt-in daily/weekly email digest, **so that** I have a low-friction summary.
**Acceptance:** Cadence per user; digest content matches in-app inbox.

### O4 — Notification preferences per user [2]
**As a** DEV, **I want** to choose which events I'm notified about and via which channel, **so that** I don't get spammed.
**Acceptance:** Settings page; defaults sensible; preferences honored on every send.

---

# Epic P — Dashboards & Reporting

### P1 — Per-project tile [3]
**As a** DEV, **I want** a tile showing open counts by source/severity, last scan time, and auto-mode state, **so that** I see my project health at a glance.
**Acceptance:** Clickable to drill in; refreshes on dashboard load.

### P2 — Org-wide rollup view [5]
**As an** EXEC, **I want** a single org view aggregating open tech debt across all projects by source, severity, and team, **so that** I can plan investment.
**Acceptance:** Filters by team / business unit; exportable as PNG / CSV; respects viewer's RBAC.

### P3 — Burn-down chart [3]
**As a** PM, **I want** a 90-day trend of open vs. closed issues, **so that** I can show progress in steering.
**Acceptance:** Per project and org-wide; stacked by severity; CSV export.

### P4 — Fix success metrics [5]
**As an** EXEC, **I want** metrics on fix success rate, average refix-loop depth, safety-block rate, and MTTR by source, **so that** I can judge platform health and ROI.
**Acceptance:** Metrics surfaced as dashboard widgets and via Prometheus.

### P5 — Pending review queue [3]
**As a** TL, **I want** a list of all runs blocked on me (review, safety override, approval), **so that** nothing waits silently.
**Acceptance:** Sorted by age; deep-links to the run; emits a daily nag for items > 48h.

---

# Epic Q — Observability & Ops

### Q1 — Structured JSON logs [3]
**As an** SRE, **I want** all logs as JSON with `run_id`, `project_id`, `user_id` correlation, **so that** searches cross-cut a single workflow.
**Acceptance:** Logback JSON encoder; correlation IDs propagated through MDC; verified in Azure Log Analytics.

### Q2 — Prometheus metrics [3]
**As an** SRE, **I want** Micrometer metrics exported for scan duration, fix success rate, safety-block rate, refix depth, runner queue depth, **so that** I can alert on degradation.
**Acceptance:** `/actuator/prometheus` scraped by Prometheus; example dashboard JSON shipped.

### Q3 — Distributed tracing [3]
**As an** SRE, **I want** OpenTelemetry traces spanning API → orchestrator → external calls, **so that** I can diagnose slow runs.
**Acceptance:** Tempo / App Insights backend wired; sampling configurable.

### Q4 — Graceful degradation on dependency outage [5]
**As an** SRE, **I want** the platform to refuse new scans/fix-runs when Veracode/Sonar/Copilot/Azure DevOps is unreachable, **so that** half-done runs don't pile up.
**Acceptance:** Circuit breakers per external; user sees clear "X is unavailable" banner; in-flight runs pause and resume on recovery.

---

# Cross-cutting Non-Functional Stories

### NF1 — Maven-runner cold-start under 30s [5]
Capacity story: pre-warm pool keeps p95 cold start under 30s under 50 concurrent runs.

### NF2 — DB schema portable to MSSQL [3]
Validated by running the full Flyway migration set against MSSQL Server in CI; integration tests pass against both H2-MSSQL-mode and MSSQL.

### NF3 — Source code never written to logs [3]
Log scrubbing tested with a corpus of synthetic diffs containing secrets/PII markers.

### NF4 — Disaster recovery runbook [3]
Documented and rehearsed restore from DB backup; Key Vault contents inventoried.
