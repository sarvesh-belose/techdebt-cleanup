# TechDebt Cleanup Platform

An enterprise platform that **ingests** Veracode SCA, Veracode SAST, and SonarQube findings across Spring Boot microservices, **generates fixes** (deterministic pom bumps for SCA, LLM-assisted source edits for SAST/Sonar), **runs them through a Safety Guard** that refuses behavior-changing diffs, **builds and verifies** in a fast Maven tier plus the project's canonical Azure Pipeline, and **opens PRs** to Azure Repos — with optional auto-merge when every gate passes.

Built for banking-domain delivery teams. Designed so the audit trail, safety gates, and override paths satisfy InfoSec without slowing down developers.

---

## Quick start

### Prerequisites
- **JDK 17** on PATH
- **Maven 3.9+** on PATH
- **Node.js 20+** and **npm 10+**
- **git** on PATH (used by the orchestrator for clone/branch/commit)

### Local development

**Windows — one-liner via the helper scripts at the project root:**

```cmd
build.bat              REM full build with tests
build.bat fast         REM skip tests
start.bat              REM opens backend (9200) and frontend (9211) in separate windows
stop.bat               REM kills whatever's listening on 9200 and 9211
```

`start.bat` refuses to launch if either port is already in use (points you at `stop.bat`), so the zombie-process trap doesn't bite. Each service runs in its own cmd window so logs stay visible.

**Manual / cross-platform:**

```bash
# Backend on http://localhost:9200
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Frontend on http://localhost:9211 (in another shell)
cd frontend
npm install
npm run dev
```

In `local` profile:
- H2 in-memory DB with MSSQL compatibility mode — schema rebuilt on every start
- `techdebt.security.dev-bypass=true` — no real Azure AD needed, all requests get a synthetic dev user
- `secretStoreImpl=in-memory`, `fixGenerator=noop`, `azureDevOpsClient=noop` — every external dep is stubbed
- H2 console at `/h2-console` (jdbc URL `jdbc:h2:mem:techdebt`)

Open **http://localhost:9211** in your browser.

### Useful URLs in local dev

| URL | Purpose |
|---|---|
| `http://localhost:9211/` | React app |
| `http://localhost:9200/swagger-ui.html` | OpenAPI / Swagger UI for all REST endpoints |
| `http://localhost:9200/v3/api-docs` | Raw OpenAPI JSON |
| `http://localhost:9200/actuator/health` | Liveness + readiness |
| `http://localhost:9200/actuator/info` | Build info + runtime config (no secrets) |
| `http://localhost:9200/actuator/prometheus` | Micrometer metrics |
| `http://localhost:9200/h2-console` | In-memory DB browser (local profile only) |

---

## Tech stack

- **Backend** — Spring Boot 3.4.1, Java 17, Spring Data JPA, Flyway, Micrometer/Prometheus, Springdoc OpenAPI
- **Frontend** — Vite, React 18, TypeScript, React Router 6, TanStack Query 5, Tailwind CSS 3, MSAL React (for Azure AD)
- **Database** — H2 (MSSQL compatibility) for dev; production target is Microsoft SQL Server with the same Flyway migrations
- **External integrations** — Veracode REST API (HMAC-SHA-256), SonarQube REST API, Azure Repos / Azure Pipelines REST API, GitHub Copilot CLI

---

## Project layout

```
techdebt-cleanup/
├── backend/                          # Spring Boot app
│   ├── pom.xml
│   └── src/main/java/com/enterprise/techdebt/
│       ├── api/                      # REST controllers + DTOs
│       ├── audit/                    # Append-only audit log
│       ├── automode/                 # M1–M3: policy, rate limit, kill switch
│       ├── azuredevops/              # AzureDevOpsClient (Noop + REST skeleton)
│       ├── analytics/                # Org rollup + fix-success metrics
│       ├── build/                    # FastBuildRunner (mvn subprocess)
│       ├── compliance/               # Per-run dossier (HTML)
│       ├── config/                   # SecurityConfig, AsyncConfig, MDC, OpenAPI
│       ├── copilot/                  # FixGenerator interface, Copilot CLI driver
│       ├── domain/                   # JPA entities + enums
│       ├── ingestion/                # IssueSourceConnector + impls
│       │   ├── sonarqube/
│       │   └── veracode/
│       ├── logging/                  # LogScrubber
│       ├── maven/                    # PomFixer (direct + transitive + multi-module)
│       ├── metrics/                  # PlatformMetrics
│       ├── notification/             # NotificationService + Teams/Email dispatchers
│       ├── orchestrator/             # FixOrchestrator + state machine
│       ├── repo/                     # Spring Data repositories
│       ├── resilience/               # CircuitBreaker + registry
│       ├── retention/                # RetentionReaper
│       ├── safety/                   # Safety Guard signals
│       ├── scheduler/                # @Scheduled jobs
│       ├── secret/                   # SecretStore (in-memory + Key Vault)
│       ├── security/                 # CurrentUser, RBAC
│       └── service/                  # Application services
│   └── src/main/resources/
│       ├── application.yml           # Profiles: local / dev / prod
│       └── db/migration/             # Flyway V1–V5
├── frontend/                         # Vite + React + TS
│   └── src/
│       ├── api/                      # Typed clients per controller
│       ├── auth/                     # AuthProvider (MSAL + dev bypass)
│       ├── components/               # Shared UI (badges, modals, layout)
│       └── pages/                    # Route entries
└── docs/
    ├── USER_STORIES.md               # Backlog with acceptance criteria
    └── SPRINT_PLAN.md                # 8-sprint plan with milestones M1–M4
```

---

## Architecture at a glance

```
┌────────────────────────────────────────────────────────────────────┐
│ React SPA (TypeScript)                                             │
│ ─ Dashboard, Issue Inbox, Fix Wizard, Run History, Settings        │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ REST (+ SSE later)
┌──────────────────────────────▼─────────────────────────────────────┐
│ Spring Boot API Gateway (Java 17)                                  │
│ ─ Azure AD OIDC, RBAC, audit logging, correlation-id MDC           │
├────────────────────────────────────────────────────────────────────┤
│ Core Modules                                                        │
│  ├─ Ingestion Service   (Veracode + SonarQube; coverage via Sonar) │
│  ├─ Issue Registry      (dedup, lifecycle, status tracking)        │
│  ├─ Fix Orchestrator    (state machine, refix loop, async exec)    │
│  ├─ Repo Workspace Mgr  (clone, branch, push)                      │
│  ├─ Copilot Driver      (subprocess wrapper for `gh copilot`)      │
│  ├─ Build Verifier      (fast tier: mvn -o clean install -DskipITs)│
│  ├─ Safety Guard        (file class, AST diff, test delta, LLM op) │
│  ├─ Auto-mode Gate      (kill switch + policy + rate limit)        │
│  ├─ Notification        (in-app + Teams + email)                   │
│  └─ Compliance Dossier  (per-run HTML for InfoSec)                 │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┬──────────────────┐
        ▼                  ▼                  ▼                  ▼
   H2 / MSSQL         Maven Runner       Azure DevOps        External APIs
   (issues, runs,     (subprocess        REST + Pipelines    (Veracode,
   audit, users,      today; k8s pool    (Noop today;        SonarQube,
   notifications)    in Sprint 6+)       REST skeleton)      Copilot CLI)
```

### Fix Run state machine

```
QUEUED → CLONING → FIXING → FAST_BUILD ─pass→ CANONICAL_BUILD ─pass→ PR_OPENED → MERGED
              │       │          │ fail               │ fail
              │       │          ▼                    ▼
              │       │       (back to FIXING       FAILED
              │       │        if attempts left)
              │       ▼
              │    SAFETY_BLOCKED ─(override)→ FAST_BUILD
              │
              └──→ FAILED                   Any non-terminal → ABORTED (manual)
```

Each transition commits in its own DB transaction (the orchestrator is **not** `@Transactional` at the method level), so a JVM crash mid-run leaves coherent persisted state and the run is resumable / inspectable.

---

## Configuration reference

All settings live in `backend/src/main/resources/application.yml`, profile-gated under `local`, `dev`, `prod`. Override via environment variables (Spring Boot's relaxed binding, e.g. `TECHDEBT_SECURITY_DEV_BYPASS=false`).

### Profiles

| Profile | DB | Auth | Secrets | Generator | Azure DevOps |
|---|---|---|---|---|---|
| `local` (default) | H2 in-memory, MSSQL mode | dev-bypass | in-memory | noop | noop |
| `dev` | H2 file-backed | Azure AD OIDC | Key Vault | configurable | configurable |
| `prod` | MSSQL via JDBC | Azure AD OIDC | Key Vault | copilot | rest |

### Key properties

| Property | Default | Purpose |
|---|---|---|
| `techdebt.security.dev-bypass` | `false` | Auto-authenticate as `dev@local` (local profile sets `true`) |
| `techdebt.security.azure-ad.tenant-id` | — | Required in dev/prod |
| `techdebt.security.azure-ad.client-id` | — | Required in dev/prod |
| `techdebt.secretstore.impl` | `in-memory` | `in-memory` or `keyvault` |
| `techdebt.secretstore.keyvault-uri` | — | Required when `impl=keyvault` |
| `techdebt.copilot.generator` | `noop` | `noop` or `copilot` |
| `techdebt.copilot.cli-path` | `gh` | Path to GitHub CLI binary |
| `techdebt.copilot.timeout-seconds` | `300` | Single-attempt LLM timeout |
| `techdebt.azuredevops.client` | `noop` | `noop` or `rest` |
| `techdebt.orchestrator.max-refix-attempts` | `3` | Bounded refix loop |
| `techdebt.safety.behavior-opiner` | `noop` | LLM second-opinion impl |
| `techdebt.scheduler.enabled` | `true` | Master switch for scheduled scans |
| `techdebt.scheduler.cron` | `0 * * * * *` | Cron evaluator cadence (every minute) |
| `techdebt.scheduler.zone` | `UTC` | Zone for cron evaluation |
| `techdebt.retention.enabled` | `true` | Master switch for retention reaper |
| `techdebt.retention.cron` | `0 0 3 * * *` | Daily at 03:00 UTC |
| `techdebt.resilience.failure-threshold` | `5` | Circuit-breaker open threshold |
| `techdebt.resilience.reset-seconds` | `120` | Cooldown before HALF_OPEN probe |
| `techdebt.build.mvn-path` | `mvn` | Path to Maven binary |
| `techdebt.build.fast-tier.mvn-args` | `-o clean install -DskipITs` | Fast-tier Maven args |
| `techdebt.build.fast-tier.timeout-seconds` | `600` | 10 minutes |
| `techdebt.notification.teams.enabled` | `false` | Activate Teams dispatcher |
| `techdebt.notification.teams.webhook-url` | — | Teams incoming webhook |
| `techdebt.notification.email.enabled` | `false` | Activate Email dispatcher (stub today) |
| `techdebt.workspace.root` | `${java.io.tmpdir}/techdebt-workspaces` | Where the orchestrator clones repos |
| `techdebt.workspace.committer-name` | `TechDebt Bot` | git committer identity |
| `techdebt.workspace.committer-email` | `techdebt-bot@local` | git committer identity |
| `server.port` | `9200` | Backend HTTP port |

---

## Database

H2 in local; MSSQL in production. Same Flyway migrations run against both (the schema is written in MSSQL-compatible SQL).

| Migration | Adds |
|---|---|
| `V1__init.sql` | `app_user`, `project`, `project_permission`, `scan_source_config`, `issue`, `fix_run`, `fix_attempt`, `audit_event` |
| `V2__safety_threshold.sql` | `project.safety_threshold` |
| `V3__auto_mode_and_scheduling.sql` | `project.auto_rate_limit_per_hour`, `project.auto_policy_json`, `scan_source_config.last_scan_at`, `system_setting`, `issue.wont_fix_reason` |
| `V4__notifications.sql` | `notification`, `notification_preference` |
| `V5__retention.sql` | `project.retention_days` |

The `audit_event` table is **append-only**: the JPA repository exposes only `save()` and read methods; DB-level `INSTEAD OF UPDATE/DELETE` triggers land with the MSSQL deployment script.

---

## Testing

```bash
cd backend && mvn test            # 78/78 unit + integration tests
cd frontend && npm run build      # tsc + Vite production build
```

The backend test pyramid:
- **Pure unit tests** — HMAC signer, PomFixer, Safety Guard signals, log scrubber, circuit breaker, state machine
- **Spring context tests** — Ingestion (dedup + auto-resolve), Notification service, Compliance dossier
- **E2E test** — `FixOrchestratorE2ETest` runs the orchestrator with the real state machine + safety guard + audit + metrics, stubbing only the IO seams (workspace, build, generator). Catches integration regressions unit tests miss.
- **Controller tests** — MockMvc against `ProjectController`

The git-touching `RepoWorkspaceManagerTest` skips itself when `git` isn't on PATH; the test exercises real `git clone`/`commit` against a bare repo on a temp directory.

---

## Production wiring (deferred stubs)

These ship with stub implementations marked `@ConditionalOnProperty` so the application boots cleanly. The wiring is documented in each stub class; only deployment specifics are missing.

| Stub | Location | What to add |
|---|---|---|
| Key Vault SDK | [`KeyVaultSecretStore`](backend/src/main/java/com/enterprise/techdebt/secret/KeyVaultSecretStore.java) | `azure-security-keyvault-secrets`; `SecretClient` from `DefaultAzureCredential`; replace `UnsupportedOperationException` |
| Azure DevOps REST | [`AzureDevOpsRestClient`](backend/src/main/java/com/enterprise/techdebt/azuredevops/AzureDevOpsRestClient.java) | PAT via SecretStore; configure git remote with PAT URL; POST `/_apis/git/repositories/{repoId}/pullrequests`; PATCH to merge |
| Azure Pipelines trigger | (Sprint 5 placeholder in orchestrator) | New class `AzurePipelinesRunner`; POST `pipelines/{id}/runs`; poll status |
| Copilot CLI | [`CopilotDriver`](backend/src/main/java/com/enterprise/techdebt/copilot/CopilotDriver.java) | Already shells out to `gh copilot suggest`. Confirm enterprise Copilot terms permit sending banking source |
| Email dispatcher | [`EmailDispatcher`](backend/src/main/java/com/enterprise/techdebt/notification/EmailDispatcher.java) | `spring-boot-starter-mail`; SMTP relay; HTML templates |
| OpenTelemetry tracing | Application defaults | `micrometer-tracing-bridge-otel`; Tempo or Zipkin endpoint |
| k8s Maven runner pool | (process-only today) | Helm chart; pre-warmed pods with `.m2` cache volume; replace `MavenSubprocessExecutor` |

---

## Running it without dev-bypass

Set up Azure AD app registration with these scopes (Backend exposes API, Frontend is a SPA):

```bash
export AZURE_AD_TENANT_ID=...
export AZURE_AD_CLIENT_ID=...
export AZURE_AD_ISSUER_URI=https://login.microsoftonline.com/$AZURE_AD_TENANT_ID/v2.0
export AZURE_AD_JWK_SET_URI=https://login.microsoftonline.com/$AZURE_AD_TENANT_ID/discovery/v2.0/keys
export TECHDEBT_SECURITY_DEV_BYPASS=false

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Frontend `.env.local`:
```
VITE_DEV_BYPASS=false
VITE_AZURE_AD_TENANT_ID=...
VITE_AZURE_AD_CLIENT_ID=...
VITE_AZURE_AD_REDIRECT_URI=http://localhost:9211/auth/callback
VITE_AZURE_AD_API_SCOPE=api://<backend-app-id>/access_as_user
```

---

## Onboarding a new Spring Boot service

See [ONBOARDING.md](ONBOARDING.md) for the project-owner walkthrough: prerequisites, registering the service, configuring scan sources, running the first scan, reviewing issues, and (optionally) enabling auto-mode.

---

## Documentation

- [docs/USER_STORIES.md](docs/USER_STORIES.md) — 17 epics, 60+ stories with acceptance criteria
- [docs/SPRINT_PLAN.md](docs/SPRINT_PLAN.md) — 8-sprint roadmap covering M1–M4
- The original architecture plan lives in the planning system (see plan file referenced at the top of the repo)

---

## License

Internal.
