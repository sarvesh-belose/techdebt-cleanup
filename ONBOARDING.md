# Onboarding your Spring Boot service to TechDebt Cleanup

This guide is for **service owners**: developers, tech leads, or platform engineers responsible for a Spring Boot microservice in this enterprise. After completing it, the platform will be ingesting your Veracode and SonarQube findings, generating fixes, and opening Azure Repos PRs for your review.

Expected time: **20 minutes** end-to-end, plus credentials gathering.

---

## What the platform does for you

You currently spend bandwidth on:

- Reading Veracode SCA reports, finding the vulnerable dependency in `pom.xml`, picking a safe version, bumping, running `mvn clean install`, opening a PR
- Reading Veracode SAST and SonarQube findings, fixing them in source, opening PRs
- Tracking what's fixed vs. open across multiple services
- Producing audit trails for InfoSec

This platform automates the mechanical parts and gates the risky parts:

1. **Pulls findings** from Veracode SCA, Veracode SAST, SonarQube, and JaCoCo-via-Sonar on a schedule
2. **Generates fixes** — deterministic `pom.xml` edits for SCA, LLM-assisted source edits for SAST/Sonar
3. **Refuses behavior-changing diffs** via a Safety Guard before any commit touches your branch
4. **Builds and verifies** with `mvn -o clean install -DskipITs` before opening the PR
5. **Opens an Azure Repos PR** with the full issue list, safety score, and an audit-log link
6. **Optionally auto-merges** when every gate (canonical build, safety, required reviewers) passes — subject to your explicit per-(source, severity) policy

You stay in control of:
- Which issues are eligible for auto-mode (per source and severity)
- Per-project rate limits and a global kill switch
- The safety-guard threshold
- Won't-fix decisions with auditable reasons

---

## Prerequisites

Before you start, gather:

### From Veracode (if you use Veracode)
- **API ID** and **API Key** (both hex strings, e.g. `abcdef0123456789`). Generated under Veracode → Account → API credentials.
- **Workspace GUID** (for SCA) — find under Veracode Workspaces, copy from URL
- **Application GUID** (for SAST) — find under Veracode Applications, copy from URL
- Confirm your service's SAST scans run regularly (monthly or per release)

### From SonarQube (if you use SonarQube)
- **Sonar user token** — generate at `https://sonar.<your-domain>/account/security`
- **Project key** — the same one your CI passes as `-Dsonar.projectKey=...`
- Your Maven pipeline already publishes JaCoCo coverage to Sonar. If not, add the `jacoco-maven-plugin` `prepare-agent` + `report` to your build, and set `sonar.coverage.jacoco.xmlReportPaths` in `sonar-project.properties`.

### From Azure DevOps
- The full **clone URL** of your repo, e.g. `https://dev.azure.com/<org>/<project>/_git/<repo>`
- A **default branch** (typically `main`)
- The platform's service-principal PAT must have **Pull Request: Read & write** + **Code: Read & write** on this repo. Ask the platform team if you're unsure.

### Optional — for auto-mode later
- Your team's Teams channel **incoming-webhook URL** (for failure notifications)
- The list of required reviewers (if auto-merge requires approvals beyond the platform's bot account)

---

## Step 1 — Register your project

1. Open the platform UI (your platform team will share the URL — typically `https://techdebt.<your-domain>`).
2. Sign in with your corporate Azure AD account.
3. Click **Register project** (top right of Dashboard).
4. Fill in:
   - **Project name** — short, unique. Use the same name your team uses in Azure DevOps (e.g. `payments-svc`).
   - **Azure Repos URL** — the full `https://.../_git/<repo>` URL.
   - **Default branch** — usually `main`.
   - **Description** — one line, optional.
5. **Register project**.

You'll land on the project detail page. The defaults are conservative:
- Safety threshold = **60** (fixes scoring ≥60/100 are blocked for review)
- Retention = **90 days** (terminal fix runs and read notifications are reaped after 90 days)
- Auto-mode = **off**
- Auto-mode policy = **empty** (deny all)
- Rate limit = **5 auto runs/hour**

You can tune all of these later under Project settings.

---

## Step 2 — Configure scan sources

On the project detail page, find the **Scan sources** card and click **Add / update**.

### For Veracode SCA
- **Source**: `VERACODE SCA`
- **External project id**: your Veracode Workspace GUID
- **API base URL**: `https://api.veracode.com`
- **Secret**: paste `<apiId>:<apiKey>` (both hex, colon-separated)
- **Cron (optional)**: e.g. `0 0 2 * * *` runs every day at 02:00 UTC
- Click **Save scan source**.

### For Veracode SAST
- Same as SCA but **Source** = `VERACODE SAST` and **External project id** = your Veracode Application GUID. The HMAC credentials are the same `apiId:apiKey`.

### For SonarQube
- **Source**: `SONARQUBE`
- **External project id**: your Sonar project key
- **API base URL**: your Sonar host URL (e.g. `https://sonar.acme.com`)
- **Secret**: your Sonar user token (paste it raw — the platform handles Basic auth wrapping)
- **Coverage threshold %**: e.g. `80` — files below this coverage become synthetic `JUNIT_COVERAGE` issues
- **Cron**: same format as above

The secret values go to the SecretStore (Azure Key Vault in prod, in-memory in local dev). The database only persists an opaque reference.

---

## Step 3 — Run your first scan

Each configured scan source has a **Scan now** button. Click it.

The platform:
1. Fetches findings from the source (with circuit-breaker protection)
2. Deduplicates by `(project, source, external_id)`
3. Persists new findings, updates existing ones, and auto-resolves any that disappeared upstream
4. Shows a summary toast: *"Scan complete · created N · updated M · resolved K of T returned by upstream"*

Click **View Issues** to see the inbox.

---

## Step 4 — Review and fix issues

On the **Issues** page:

- **Filter** by source, severity, status, file path, CVE, or creation date range
- **Sort** by severity (default) or other columns
- **Click any row** to open the detail drawer with the full payload — including the transitive dependency path for SCA findings, source/sink for SAST, and rule for Sonar
- **Multi-select** with the checkboxes, then click **Fix Selected (N)** to launch the Fix Wizard

### Fix Wizard

The wizard shows:
- The list of selected issues with severity + source badges
- An optional **auto mode** toggle (most users leave this off for first runs)
- **Start Fix Run** — kicks off the orchestrator

You'll be redirected to the **Fix Run detail** page where you can watch state progress:

```
QUEUED → CLONING → FIXING → FAST_BUILD → CANONICAL_BUILD → PR_OPENED
```

Each transition is logged with timestamps. When the run reaches **PR_OPENED**, you'll see the Azure Repos PR URL — click it to review.

### When a run is blocked

If the Safety Guard flags the diff as behavior-changing, the run transitions to **SAFETY_BLOCKED**. You'll see a panel showing:
- The overall safety score (out of 100) and your project's threshold
- Each signal's contribution and the specific reasons (e.g. *"method removed: PaymentService#refund(BigDecimal)"*, *"@PreAuthorize annotation removed on TransferController#delete"*)

You can:
- **Abort** the run if the flag is correct (the fix really would change behavior)
- **Override** (audit-logged) if you've reviewed the diff and decided it's safe — this is reserved for Approver role
- Adjust your project's safety threshold in settings if the default is too strict

### Won't fix

If you decide an issue isn't worth fixing (false positive, accepted risk, etc.), open the issue detail and click **Mark won't fix**. You must provide a reason — it's surfaced to anyone who later sees the issue, and it's audit-logged.

Reopening is one click in the same drawer.

---

## Step 5 — Download a compliance dossier

For any Fix Run, click **Download dossier** on the run detail page. You get a self-contained HTML file with:

- Project, repo, branch, commit
- Full safety assessment (signals, scores, reasons)
- Every fix attempt with diff + build log tail
- The audit-event trail scoped to that run

InfoSec asks for this artifact during reviews. The HTML is print-friendly — your browser's "Save as PDF" works for archival.

---

## Step 6 — Enable auto-mode (optional, advanced)

Once you've watched a few manual runs complete successfully, you can let the platform run unattended.

On the **Project Detail** page:

### a) Configure the auto-mode policy matrix

The matrix is a grid of (source × severity). Tick the cells you want auto-mode to be eligible for. **An empty matrix denies everything** — flipping the auto-mode toggle on without ticking anything in the matrix has no effect.

Pragmatic starting policy:

| Source | Critical | High | Medium | Low | Info |
|---|---|---|---|---|---|
| Veracode SCA | ✓ | ✓ | | | |
| Veracode SAST | | | | | |
| SonarQube | | | | | |
| JUnit coverage | | | | | |

This means: auto-fix Critical + High SCA findings (deterministic pom bumps), but require manual review for all SAST + Sonar findings until you're confident in the Safety Guard's behavior on your codebase.

### b) Set the rate limit

`Auto-runs per hour` defaults to **5**. The orchestrator refuses new auto runs when you've exceeded this — useful as a sanity check if a connector misbehaves and floods Azure Pipelines.

### c) Flip the master toggle

In the **Project settings** section below, check **Enable auto-mode**.

### d) Understand the global kill switch

In the **Settings** page, an admin can flip the **Auto-mode kill switch**. While ON:
- No scheduled scans run
- The orchestrator refuses to auto-merge
- All auto behavior is paused org-wide
- Manual fix runs still work

This is the "stop the world" lever for incidents.

### What auto-mode does

For each scheduled scan, the orchestrator checks the gate stack:
1. Global kill switch OFF
2. Project auto-mode ON
3. Each affected issue's `(source, severity)` is in the policy
4. Rate limit not exceeded

If all four pass: the orchestrator runs the full pipeline, opens a PR, and auto-merges if the canonical Azure Pipeline is green and the safety guard cleared. If any gate fails, the run still happens but falls through to PR_OPENED for manual review — same as a manual run.

---

## What happens behind the scenes

- **Scheduled scans** fire every minute against your cron expression. The orchestrator dedups (idempotent re-runs are safe).
- **Veracode SAST mitigations** sync — if you mark a finding "Accepted" in Veracode, it lands as `WONT_FIX` on first ingest with the Veracode reason recorded.
- **Notifications** fire for `SAFETY_BLOCKED`, `RUN_FAILED`, `PR_OPENED`, `MERGED`. They land in your in-app inbox (bell icon, top right) and optionally Teams (if your team has configured a webhook).
- **Audit log** captures every state change, every override, every won't-fix, every config change. Visible via the compliance dossier or the audit-log endpoint.
- **Metrics** are exported on `/actuator/prometheus`: scan duration, fix-run state counts, safety decisions, refix depth, scan failures.

---

## FAQ

### Does the platform send my source code to Copilot?
Only when you use SAST/Sonar fixes (which call the `gh copilot` CLI in your enterprise's licensed environment). Pure SCA fixes are deterministic Maven edits — no LLM involvement. Confirm your enterprise Copilot terms with InfoSec before enabling.

### Can I review the diff before it lands?
Always. The fix is committed to a branch on your repo, but the PR is opened in your Azure Repos — you (and any required reviewers) approve before merge. Even in auto-mode, you can set required reviewers in Azure DevOps; the platform respects those rules.

### What if the platform breaks my build?
The fast tier (`mvn -o clean install -DskipITs`) runs before any PR is opened. If it fails, the orchestrator refixes up to 3 times feeding the build error back to the LLM. If those all fail, the run terminates as `FAILED` with the log preserved on the branch — no PR is opened. The canonical Azure Pipeline then runs as a second check before auto-merge.

### What's the rollback path?
- **Single fix**: close the PR, branch lives on but isn't merged
- **Per-issue**: reopen the issue, mark won't-fix
- **Per-project**: flip auto-mode off
- **Org-wide**: flip the global kill switch
- **A merged PR**: it's a normal git revert in your repo — the platform doesn't fight you

### How do I configure required PR reviewers?
That's an Azure DevOps repo-policy concern, not a platform concern. Set up branch policies in Azure DevOps; the platform respects them. If a PR can't be auto-merged due to required reviewers, it stays open in `PR_OPENED` state until reviewers approve and a human merges.

### How do I see what's running?
- **In-app bell** — recent notifications
- **Run History** under each project — all fix runs with state badges
- **Dashboard** — org-wide metrics (success rate, safety block rate, MTTR per source)
- **`/actuator/info`** — current platform config (which generator, which Azure DevOps impl, etc.)

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Scan returns 0 findings | Wrong project key / workspace GUID | Verify against the source system's UI; the external_id must match exactly |
| Scan fails with "circuit open" | Recent failures tripped the breaker | Wait 2 minutes; the breaker auto-probes. If persistent, check the source system's status |
| Fix run stuck in CLONING | Workspace can't reach Azure Repos | Verify the service-principal PAT and repo permissions |
| Fix run goes to SAFETY_BLOCKED on every diff | Threshold is too strict | Lower the safety threshold from 60 → 70 in project settings (max 100), or review the signal breakdown — sometimes the diff really does change behavior |
| Auto-mode isn't firing | Empty policy matrix, or kill switch on | Check Project Detail's auto-mode policy + Settings page kill switch |
| Won't-fix mark didn't stick | You're not an Approver on this project | Ask a project Admin to grant Approver role |
| Coverage findings have wrong threshold | Per-source threshold not configured | Edit the SonarQube scan source; set Coverage threshold % |

---

## Getting help

- **In-app docs**: hover over any badge or button for tooltips (where present)
- **Swagger UI** at `/swagger-ui.html` documents every API endpoint
- **Audit log** answers "who did what when" — accessible via the compliance dossier
- **Platform team**: `#techdebt-platform` in Teams, or `techdebt-platform@<your-domain>`

---

## Glossary

- **Auto-mode** — the project is willing to let the platform open AND merge fixes unattended for whitelisted (source, severity) pairs
- **Canonical build** — your project's own Azure Pipeline, which the orchestrator triggers as a second-tier verification after the fast Maven check passes
- **Fix attempt** — one iteration of the refix loop within a single fix run
- **Fix run** — the orchestrator's unit of work; from CLONING through (eventually) MERGED or FAILED
- **Refix loop** — when the fast build fails, the orchestrator feeds the error back to the fix generator and tries again, up to N attempts
- **Safety Guard** — the AST + heuristic stack that scores how likely a diff is to change observable behavior; high scores block before commit
- **Safety override** — Approver-only action that lets a SAFETY_BLOCKED run continue. Audit-logged, counts toward team safety metrics
- **Kill switch** — org-wide auto-mode shutoff. Manual runs unaffected
