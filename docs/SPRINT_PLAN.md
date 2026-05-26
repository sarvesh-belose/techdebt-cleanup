# TechDebt Cleanup Platform — Sprint Plan

## Plan Summary

- **Cadence:** 2-week sprints
- **Total horizon:** 8 sprints (~4 months) covering Milestones M1–M4 from the approved plan
- **Anchor date:** Sprint 1 starts **2026-06-01** (week after today, 2026-05-25)
- **Hardening + UAT:** Sprint 9 (buffer, not a feature sprint)
- **Production pilot rollout:** end of Sprint 6
- **GA across enterprise:** end of Sprint 8

## Assumed Team

| Role | FTE | Responsibility |
|---|---|---|
| Engineering Manager | 1.0 | Delivery, stakeholders, risk |
| Tech Lead / Architect | 1.0 | Hands-on architecture, hard problems, reviews |
| Backend Developers | 3.0 | Spring Boot modules |
| Frontend Developer | 1.0 | React UI |
| DevOps / SRE | 0.5 | k8s runner pool, Helm, Key Vault, pipelines |
| QA Engineer | 0.5 | Test plans, integration harness, regression |
| UX Designer | 0.5 | Wireframes for Fix Wizard, Inbox, Dashboard |

**Capacity assumption:** 30 story points per sprint sustained, with 35–40 in middle sprints and a deliberate dip in Sprint 1 (setup/onboarding overhead).

## Definition of Ready (sprint entry)

Story has: persona, acceptance criteria, estimate, design (if UI), dependencies resolved or sequenced, audit/observability requirements noted.

## Definition of Done (sprint exit)

Merged, unit + integration tests passing, security scan clean, observability hooks added, audit-log writes verified, demoed to PO, no open Sev-1/Sev-2 bugs against the story.

## Milestone → Sprint Map

| Milestone | Theme | Sprints |
|---|---|---|
| **M1** Foundation | Auth, project mgmt, Veracode SCA, pom-fix, fast build, audit | S1, S2 |
| **M2** Source-fix capability | SAST + Sonar, Copilot driver, Safety Guard v1 | S3, S4 |
| **M3** Pipeline canonicalization + auto | Azure Pipelines, PR automation, multi-module, auto-mode | S5, S6 |
| **M4** Scale & polish | Org dashboard, Teams, advanced filters, compliance export, safety tuning | S7, S8 |
| **Hardening** | UAT, perf, DR rehearsal | S9 |

---

# Sprint 1 — Foundation: shell, auth, project model (2026-06-01 → 2026-06-12)

**Goal:** A logged-in user can register a project and see it in a dashboard. No issue ingestion yet.

**Capacity target:** 27 points (deliberately lower for setup overhead)

| Story | Pts | Owner |
|---|---|---|
| A1 — Azure AD SSO login | 5 | Backend + FE |
| A2 — RBAC (Viewer/Dev/Approver/Admin) | 5 | Backend |
| A3 — Secrets in Azure Key Vault | 5 | DevOps + Backend |
| A4 — Health/readiness endpoints | 2 | Backend |
| B1 — Register a new project | 3 | Backend + FE |
| B3 — Edit/archive project | 2 | Backend + FE |
| Q1 — Structured JSON logs | 3 | Backend |
| NF2 — DB schema portable to MSSQL (set up CI dual-target) | 3 | DevOps |
| **Spike** — gh copilot CLI behavior on a sandbox repo | — | Tech Lead |

**Exit demo:** Login → register a project → see it on dashboard → archived projects hidden.

**Risks / dependencies:** Azure AD app registration must be filed Week 0. Key Vault per-environment provisioning starts Week 0.

---

# Sprint 2 — Foundation: Veracode SCA ingestion + Maven pom fix (2026-06-15 → 2026-06-26)

**Goal:** End-to-end manual single-issue SCA fix on a pilot service: fetch → list → fix pom → fast build → manual PR. Closes M1.

**Capacity target:** 35 points

| Story | Pts | Owner |
|---|---|---|
| B2 — Configure scan sources per project | 5 | Backend + FE |
| B4 — Onboarding wizard | 5 | FE |
| C1 — Pluggable connector interface | 3 | Tech Lead |
| C2 — Veracode SCA fetch | 8 | Backend |
| C3 — Dedup and persist issues | 5 | Backend |
| C5 — On-demand "Scan now" | 2 | Backend + FE |
| G1 — Fix Run state machine | 8 (start; finish S3) | Tech Lead |
| G3 — Bump direct dependency version | 5 | Backend |
| J2 — Run fast build (`mvn -o clean install -DskipITs`) | 5 | DevOps + Backend |
| N1 — Append-only audit log | 5 | Backend |

**Note:** G1 is sized 8 but planned to span S2–S3. Half its points realistically land in S2.

**Exit demo (M1 close):** Onboard pilot service → scan pulls SCA issues → select one direct-dependency CVE → platform creates branch, bumps version, fast build green → user manually opens PR. Audit log shows every action.

**Risks:** Veracode HMAC auth is fiddly — Tech Lead pairs on it. If pilot service has no SCA findings, seed via a deliberately old `log4j` for the demo.

---

# Sprint 3 — Source-fix engine + SAST/Sonar (2026-06-29 → 2026-07-10)

**Goal:** Platform can apply Copilot-driven source fixes for SAST/Sonar findings with a basic safety guard, in addition to pom fixes.

**Capacity target:** 38 points

| Story | Pts | Owner |
|---|---|---|
| G1 — Fix Run state machine (complete) | 4 (carry) | Tech Lead |
| G2 — Clone repo and create fix branch | 3 | Backend |
| H1 — Copilot CLI subprocess wrapper | 8 | Tech Lead + Backend |
| H2 — Apply Copilot diff and commit | 3 | Backend |
| D1 — Veracode SAST fetch | 8 | Backend |
| E1 — SonarQube issue fetch | 5 | Backend |
| E2 — SonarQube coverage fetch | 5 | Backend |
| E3 — Confirm coverage via Sonar (doc) | 1 | Tech Lead |
| F1 — List view with filters | 5 | FE |

**Exit demo:** Fetch SAST + Sonar issues; user selects a Sonar code smell → Copilot generates diff → diff committed to branch → fast build runs (still no safety guard gate). Verify list view filters work end-to-end.

**Risks:** Copilot CLI prompt quality. Allocate 2 days at start of sprint for prompt engineering & evaluation harness.

---

# Sprint 4 — Safety Guard v1 + refix loop + UI core (2026-07-13 → 2026-07-24)

**Goal:** Safety guard blocks high-impact diffs; refix loop self-heals trivial build breaks; Fix Wizard UI is usable. Closes M2.

**Capacity target:** 38 points

| Story | Pts | Owner |
|---|---|---|
| H3 — Bounded refix loop | 5 | Backend |
| H4 — Single-issue and batch fix modes | 3 | Backend + FE |
| I1 — File-classification signal | 3 | Backend |
| I2 — AST-diff signal | 8 | Tech Lead |
| I3 — Test-delta signal | 3 | Backend |
| I5 — Safety block UI | 5 | FE |
| I6 — Safety threshold config per project | 2 | Backend + FE |
| F2 — Issue detail view | 3 | FE |
| F3 — Bulk select for fix | 3 | FE |
| J3 — Stream build logs to UI | 5 | Backend + FE |

**Exit demo (M2 close):** End-to-end Sonar fix run with safety guard catching a deliberately-injected behavior-changing diff; refix loop recovers from a synthetic compile error; build logs stream live to the UI.

**Risks:** Safety guard recall/precision tuning. Build the labeled corpus in Sprint 3 in parallel so it's ready when I2 starts.

---

# Sprint 5 — Azure Pipelines canonical + PR automation + multi-module Maven (2026-07-27 → 2026-08-07)

**Goal:** Platform triggers Azure Pipelines as the canonical gate; opens real PRs with full context. Multi-module Maven works.

**Capacity target:** 36 points

| Story | Pts | Owner |
|---|---|---|
| K1 — Trigger Azure Pipeline on fix branch | 5 | Backend + DevOps |
| K2 — Poll status and pull logs | 3 | Backend |
| K3 — Block PR auto-merge until canonical green | 3 | Backend |
| L1 — Open Azure Repos PR with rich body | 5 | Backend |
| L2 — Link work items | 2 | Backend |
| L3 — Update PR on refix | 3 | Backend |
| G4 — Override transitive via dependencyManagement | 5 | Backend |
| G5 — Verify no new CVE introduced | 5 | Backend + SEC pairing |
| G6 — Multi-module project support | 5 | Backend |

**Exit demo:** Run on a real multi-module pilot service → fast build green → canonical Azure Pipeline triggered & green → PR opened with issues, safety score, audit link.

**Risks:** Azure DevOps service principal permissions. File the IT ticket Sprint 4 Week 1 so the SP is provisioned before Sprint 5.

---

# Sprint 6 — Auto-mode, J4/F4/F5, scheduled scans (2026-08-10 → 2026-08-21)

**Goal:** A project can be flipped to fully automatic with safety brakes. Closes M3. Pilot starts running unattended.

**Capacity target:** 36 points

| Story | Pts | Owner |
|---|---|---|
| M1 — Per-project auto-mode flag | 2 | Backend + FE |
| M2 — Per-source severity policy | 3 | Backend + FE |
| M3 — Safety brake (rate limit + global kill) | 3 | Backend |
| M4 — Auto-mode notification digest | 2 | Backend |
| C4 — Scheduled scans (cron) | 3 | Backend |
| D2 — SAST mitigation status sync | 3 | Backend |
| F4 — Mark "won't fix" with reason | 2 | Backend + FE |
| F5 — Quick "Fix All Critical" action | 3 | FE |
| J1 — Maven runner pool (productionize) | 8 | DevOps |
| J4 — Surface failure summary | 3 | Backend + FE |
| L4 — Auto-merge after gates | 3 | Backend |
| I4 — LLM second-opinion signal | 3 | Backend |

**Exit demo (M3 close + pilot launch):** Pilot project flipped to auto. Overnight, a seeded CVE generates a PR with full audit trail, no human intervention, and auto-merges after canonical green. Safety brake activates if rate exceeds threshold.

**Risks:** Operational risk of auto-mode in pilot. Roll out with conservative defaults (rate limit = 5/hour, severities = Critical only) and a 1-week "soak" before relaxing.

---

# Sprint 7 — Org dashboards, Teams, advanced filters, audit export (2026-08-24 → 2026-09-04)

**Goal:** Management gets a single pane of glass; teams get Teams notifications; InfoSec gets one-click compliance dossiers.

**Capacity target:** 36 points

| Story | Pts | Owner |
|---|---|---|
| P1 — Per-project tile | 3 | FE |
| P2 — Org-wide rollup view | 5 | Backend + FE |
| P3 — Burn-down chart | 3 | FE |
| P4 — Fix success metrics | 5 | Backend + FE |
| P5 — Pending review queue | 3 | FE |
| O1 — In-app inbox | 3 | Backend + FE |
| O2 — Teams webhook | 3 | Backend |
| O3 — Email digest | 2 | Backend |
| O4 — Notification preferences | 2 | FE |
| N2 — Audit every mutation (aspect) | 3 | Backend |
| N3 — Per-run compliance export (PDF) | 5 | Backend |

**Exit demo:** Exec views org dashboard with rollup + burn-down + success metrics; Teams channel receives a `SAFETY_BLOCKED`; one-click PDF export of a Fix Run hands to InfoSec.

---

# Sprint 8 — Polish, scale, GA readiness (2026-09-07 → 2026-09-18)

**Goal:** Onboard remaining services; tune safety guard from real production data; production-grade observability and DR. M4 close → GA.

**Capacity target:** 34 points

| Story | Pts | Owner |
|---|---|---|
| N4 — Retention policy + reaper | 3 | Backend |
| Q2 — Prometheus metrics | 3 | Backend |
| Q3 — Distributed tracing | 3 | Backend |
| Q4 — Graceful degradation on dep outage | 5 | Backend |
| NF1 — Maven-runner cold-start tuning | 5 | DevOps |
| NF3 — Source code never written to logs | 3 | Backend |
| NF4 — Disaster recovery runbook | 3 | DevOps + EM |
| **Safety Guard tuning** from S6/S7 production data | 5 | Tech Lead |
| **Onboarding sweep** — register all in-scope services | 3 | EM + ADM |
| **GA dry run** — full E2E with InfoSec & ops sign-off | — | Whole team |

**Exit demo (M4 close + GA):** All in-scope services onboarded; safety guard recall ≥ 95% on production-derived corpus; DR rehearsal passes; sign-off from InfoSec, SRE, and product.

---

# Sprint 9 — Hardening & UAT buffer (2026-09-21 → 2026-10-02)

Reserved for: bug burn-down, performance, user-acceptance feedback, late InfoSec asks, any slipped story from S1–S8. No new feature commits.

---

# Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Enterprise Copilot ToS prohibits sending banking source to Copilot CLI | Medium | High — blocks H1 onwards | Confirmed in pre-kickoff (Open Item #1 in plan). If blocked, swap `CopilotDriver` for Azure OpenAI w/ private endpoint — same interface, ~5pts extra. |
| Azure DevOps service principal permission delays | Medium | High — blocks K1, L1 | File IT ticket Sprint 1 Week 1; reconfirm by Sprint 4. |
| Safety guard recall too low | Medium | High — banking-grade fixes require it | Build labeled corpus in S3 in parallel; allocate S8 tuning. If still below threshold at S6, escalate to PO for scope reduction (auto-mode delayed). |
| Veracode rate limits in scheduled scans | Low | Medium | Throttle implemented in S2 (C2); coordinate with Veracode admin. |
| Maven runner pool perf at scale | Medium | Medium | NF1 in S8; alternate plan = burst to ephemeral runners. |
| H2 production durability concerns | Low (pilot) → High (GA) | Medium | Pilot on H2 acceptable; commit to MSSQL swap before broad GA — open item in plan. |
| Pilot service has no representative findings | Low | Medium | Seed deliberately old deps (e.g., `log4j 2.14`) on a pilot fork. |

---

# Decisions & Open Items Carried From Plan

1. Confirm Copilot enterprise terms — **before Sprint 3 start**.
2. Provision Azure DevOps service principal — **before Sprint 5 start**.
3. Decide DB residency / GA database choice (H2 → MSSQL) — **before Sprint 8 start**.
4. Confirm every in-scope project publishes JaCoCo to SonarQube — **before Sprint 6 start** (auto-mode onboarding).

---

# Burn-up Forecast (story points)

| Sprint | Planned | Cumulative | Cumulative target |
|---|---|---|---|
| S1 | 27 | 27 | 27 |
| S2 | 35 | 62 | 62 |
| S3 | 38 | 100 | 100 |
| S4 | 38 | 138 | 138 |
| S5 | 36 | 174 | 174 |
| S6 | 36 | 210 | 210 |
| S7 | 36 | 246 | 246 |
| S8 | 34 | 280 | 280 |

Total ≈ 280 story points across 8 sprints. Buffer in S9 absorbs late churn.

---

# Stakeholder Cadence

- **Daily standup:** team, 15 min
- **Sprint planning:** end of each sprint, 2 hr
- **Sprint review / demo:** end of each sprint, 1 hr; invite PM stakeholders + InfoSec on M-close sprints (S2, S4, S6, S8)
- **Sprint retro:** end of each sprint, 45 min
- **Steering update to EXEC:** every other sprint
- **InfoSec checkpoint:** end of S2 (audit), S4 (safety guard), S6 (auto-mode), S8 (GA)
