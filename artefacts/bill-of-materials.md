# Bill of Materials — RM Platform

Every component that makes up one deployment of the product at one bank: the units we ship, the
software they run on, the third-party packages they are built from, and the services the bank
supplies. Compiled on 2026-11-06 from the manifests in the repository (`pom.xml`, the module POMs,
the workspace `package.json` files, the Dockerfiles, `deploy/compose` and `deploy/helm`). It is a
static read of the declared (direct) dependencies — it is not generated from a resolved build, so
transitive packages are counted, not listed.

## 1. Product identity

| Field | Value |
|---|---|
| Product | RM Platform (repository `CLVWorkbench`) |
| Product version | 0.1.0 (`appVersion` in the Helm chart; `0.1.0` across the Maven reactor) |
| Helm chart version | 0.1.0 |
| Maven group | `com.bank.rmplatform`, parent `rm-platform-parent` |
| npm root | `rm-platform` (private, UNLICENSED), npm workspaces |
| Deployment model | Single tenant: one deployment per bank, in the bank's own cloud tenancy or data centre |
| Release model | One release; milestones M0–M9 are internal build checkpoints |

## 2. Shipped components

### 2.1 Java services (23 images)

All built from one golden-path template: Spring Boot 3.3.5 on Java 21, Spring Modulith module
boundaries, a per-service database, outbox publishing and idempotent consumers. Grouped into 14
deployable units (see `service-catalogue.md`); each image listens on 8080.

`gateway`, `identity`, `reference`, `deals`, `work`, `inbox`, `nudges`, `planner`, `analytics`,
`ingestion`, `audit`, `agent-runtime`, `engagement`, `monitoring`, `compliance`, `performance`,
`documents`, `content`, `value`, `planning`, `intelligence`, `team`, `admin`.

Image names follow `rm-platform/<service>:<product version>`.

### 2.2 Shared Java libraries (6 Maven modules, not shipped on their own)

| Module | Purpose |
|---|---|
| `libs/platform-bom` | Aggregation point; versions are managed in the parent reactor |
| `libs/web-common` | Shared web/MVC concerns |
| `libs/events` | Outbox and event publishing/consumption over AMQP |
| `libs/security` | Resource-server security, identity propagation |
| `libs/model-gateway-client` | The only path to a model provider (rule 6) |
| `libs/testing` | Test fixtures, Testcontainers wiring |

### 2.3 TypeScript and data components

| Component | What it is | Image base | Port |
|---|---|---|---|
| `web` | React 19 + TypeScript single-page app, served as static files | `nginxinc/nginx-unprivileged:1.29-alpine` (built with `node:24-alpine`) | 8080 |
| `teams-adapter` | Microsoft Teams app package and bot adapter | `node:24-alpine` | 3978 |
| `mock-api` | Every REST contract served from the seed, for Docker-free local runs and demos | `node:24-alpine` | 4010 |
| `db` (warehouse migrator) | Flyway migrations V1–V9 generated from the table catalogue, V10+ hand-written | `flyway/flyway:12.10` | — |
| `seed` | Fictional portfolio and its warehouse load | `mcr.microsoft.com/mssql/server:2022-CU26-ubuntu-22.04` (built with `node:24-alpine`) | — |

### 2.4 Non-image workspaces (build-time only)

`ai` (house rules, agent prompts A1–A13, task templates T1–T10, tool catalogue, output schemas),
`contracts` (OpenAPI, AsyncAPI, JSON Schemas and generated types), `docs` (spec and architecture,
generated from `docs/source`), `eval` (agent quality gates and gold sets), `format` (amount and
date formatting shared by web and Teams), `deploy` (Compose, Helm and their tests).

## 3. Runtime platform

| Layer | Component | Version | Notes |
|---|---|---|---|
| Runtime | Java (Eclipse Temurin JRE) | 21.0.5_11, Alpine | Base image of every service |
| Runtime | Node.js | 24 (`>=24` enforced by `engines`) | web build, Teams adapter, mock API, generators |
| Operational DB | Microsoft SQL Server | 2022-CU26 (Ubuntu 22.04) | Primary; parameterized across SQL Server / PostgreSQL / H2 by Spring profile (ADR-0018) |
| Operational DB (alt) | PostgreSQL | via `org.postgresql:postgresql` (Boot-managed) | Supported dialect |
| Test DB | H2 in PostgreSQL mode | Boot-managed | Default for `mvn package`, so local builds need no container runtime |
| Warehouse | SQL Server | same image | SCD Type 2 history, drill-down to transactions |
| Messaging | RabbitMQ | 4.2 (management-alpine) | Outbox delivery and event consumption |
| Cache | Redis | 8.10-alpine | Used by `analytics`, `reference`, and reactively by `gateway` |
| Object storage | SeaweedFS | 4.32 | S3-compatible store (local Compose stack) |
| Schema migration | Flyway | 12.10 (image); `flyway-core` Boot-managed in services | |
| Orchestration | Kubernetes via Helm chart `rm-platform` | chart 0.1.0 | Deployments, HPA, network policies, ServiceMonitor, warehouse jobs |
| Local stack | Docker Compose | `deploy/compose/compose.yaml`, `compose.microservices.yaml` | |

### Observability

| Component | Version |
|---|---|
| OpenTelemetry Collector (contrib) | 0.111.0 |
| Prometheus | v2.54.1 |
| Grafana | 11.2.2 |
| Micrometer Prometheus registry / tracing bridge (OTel) | Boot-managed, in all 23 services |
| OpenTelemetry OTLP exporter | Boot-managed, in all 23 services |

## 4. Java dependency inventory

Managed centrally in the parent reactor; module POMs declare dependencies without versions except
where noted.

### 4.1 Version anchors (parent `pom.xml`)

| Anchor | Version |
|---|---|
| `spring-boot-starter-parent` | 3.3.5 |
| `spring-cloud-dependencies` | 2023.0.3 |
| `spring-modulith-bom` | 1.2.4 |
| `testcontainers-bom` | 1.20.3 |
| `mssql-jdbc` | 12.8.1.jre11 |
| `java.version` / `maven.compiler.release` | 21 |

### 4.2 Direct dependencies (deduplicated, with reach)

"Modules" counts the libraries and services that declare it; all versions are BOM-managed unless
stated.

| Group:artifact | Scope | Modules |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-web` | compile | 24 |
| `org.springframework.boot:spring-boot-starter-actuator` | compile | 23 |
| `org.springframework.boot:spring-boot-starter-data-jdbc` | compile | 23 |
| `io.micrometer:micrometer-registry-prometheus` | compile | 23 |
| `io.micrometer:micrometer-tracing-bridge-otel` | compile | 23 |
| `io.opentelemetry:opentelemetry-exporter-otlp` | compile | 23 |
| `com.h2database:h2` | compile | 23 |
| `org.postgresql:postgresql` | compile | 23 |
| `com.microsoft.sqlserver:mssql-jdbc` (12.8.1.jre11) | compile | 22 |
| `org.flywaydb:flyway-core` | compile | 22 |
| `org.flywaydb:flyway-database-postgresql` | compile | 22 |
| `org.flywaydb:flyway-sqlserver` | compile | 22 |
| `org.springframework.modulith:spring-modulith-starter-core` | compile | 22 |
| `org.springframework.boot:spring-boot-starter-test` | test | 22 |
| `org.springframework.boot:spring-boot-starter-security` | compile | 1 (`security`) |
| `org.springframework.boot:spring-boot-starter-oauth2-resource-server` | compile | 2 (`security`, `gateway`) |
| `org.springframework.security:spring-security-core` | compile | 1 (`web-common`) |
| `org.springframework.boot:spring-boot-starter-amqp` | compile | 1 (`events`) |
| `org.springframework.boot:spring-boot-starter-json` | compile | 1 (`events`) |
| `org.springframework.boot:spring-boot-starter-data-redis` | compile | 2 (`analytics`, `reference`) |
| `org.springframework.boot:spring-boot-starter-data-redis-reactive` | compile | 1 (`gateway`) |
| `org.springframework.boot:spring-boot-starter-validation` | compile | 1 (`identity`) |
| `org.springframework.boot:spring-boot-starter` | compile | 1 (`model-gateway-client`) |
| `org.springframework.cloud:spring-cloud-starter-gateway` | compile | 1 (`gateway`) |
| `org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j` | compile | 1 (`gateway`) |
| `org.springframework.modulith:spring-modulith-starter-test` | test | 1 (`identity`) |
| `org.testcontainers:junit-jupiter`, `:jdbc`, `:postgresql`, `:mssqlserver`, `:rabbitmq` | compile (inside `libs/testing`) | 1 |
| `com.bank.rmplatform:web-common` / `:events` / `:security` | compile | 22 each |
| `com.bank.rmplatform:testing` | test | 22 |
| `com.bank.rmplatform:model-gateway-client` | compile | 1 (`agent-runtime`) |

Build plugins: `maven-surefire-plugin` and `maven-failsafe-plugin` (Boot-managed versions;
failsafe bound to `integration-test` and `verify`).

## 5. Node dependency inventory

Exact versions (`save-exact` in `.npmrc`); `package-lock.json` is committed at lockfile version 3
and resolves **538** packages in total across the workspaces.

### 5.1 `web` — runtime

| Package | Version |
|---|---|
| `react` / `react-dom` | 19.3.0 |
| `react-router` | 8.4.0 |
| `@tanstack/react-query` | 5.104.1 |
| `@fluentui/react-components` | 9.74.9 |
| `@fluentui/react-icons` | 2.0.343 |
| `@azure/msal-browser` | 5.23.0 |
| `@microsoft/teams-js` | 2.57.0 |
| `openapi-fetch` | 0.17.0 |
| `@rm/contracts`, `@rm/format` | workspace |

### 5.2 `web` — build and test

`vite` 8.3.2, `@vitejs/plugin-react` 6.1.1, `vitest` 5.0.3, `typescript` 5.9.3,
`@playwright/test` 1.63.0, `@axe-core/playwright` 4.13.0, `@testing-library/react` 16.3.3,
`@testing-library/dom` 10.4.2, `@testing-library/jest-dom` 7.0.1,
`@testing-library/user-event` 14.6.7, `jsdom` 30.1.1, `@types/react` 19.3.0,
`@types/react-dom` 19.3.0, `@rm/mock-api` (workspace).

### 5.3 `teams-adapter`

Runtime: `@microsoft/teams.api`, `@microsoft/teams.apps`, `@microsoft/teams.cards`,
`@microsoft/teams.common` — all 2.1.0; `@rm/contracts`, `@rm/format` (workspace).
Build/test: `typescript` 5.9.3, `@types/node` 24.19.1, `ajv` 8.20.0, `ajv-formats` 3.0.1,
`@rm/mock-api` (workspace).

### 5.4 `mock-api`

Runtime: `@rm/contracts`, `@rm/format`, `@rm/seed` (workspace).
Build/test: `typescript` 5.9.3, `@types/node` 24.19.1, `ajv` 8.20.0, `ajv-formats` 3.0.1,
`yaml` 2.9.1.

### 5.5 Build-time workspaces

| Workspace | Packages |
|---|---|
| `contracts` | `@asyncapi/parser` 3.6.3, `@redocly/cli` 2.57.0, `openapi-typescript` 7.13.0, `ajv` 8.20.0, `ajv-formats` 3.0.1, `yaml` 2.9.1, `typescript` 5.9.3 |
| `ai` | `ajv` 8.20.0, `yaml` 2.9.1 |
| `db` | `ajv` 8.20.0, `yaml` 2.9.1 |
| `eval` | `ajv` 8.20.0, `ajv-formats` 3.0.1, `@rm/mock-api` (workspace) |
| `seed` | `@rm/db` (workspace) |
| `deploy` | `yaml` 2.9.1 |
| `docs` | `jsdom` 30.1.1, `turndown` 7.2.4, `turndown-plugin-gfm` 1.0.2 |
| `format` | `typescript` 5.9.3, `@types/node` 24.19.1 |

No chart, PDF or document-generation library is bundled in the product: charts and documents are
platform services, not product code.

## 6. Build and CI toolchain

| Tool | Version |
|---|---|
| Maven | 3.9.9 (build image `maven:3.9.9-eclipse-temurin-21`) |
| JDK (build) | Eclipse Temurin 21 |
| Node / npm | 24 (npm workspaces) |
| GitHub Actions | `actions/checkout@v4`, `actions/setup-java@v4`, `actions/setup-node@v4`, `azure/setup-helm@v4` |
| Workflow | `.github/workflows/ci.yml` — Node checks, Java build and tests, dialect validation via Testcontainers, Dockerfile/Compose/Helm validation |

## 7. Supplied by the bank (not shipped)

These are integration points, configured per bank at run time; no bank-specific values are built
into an image.

| Dependency | Role |
|---|---|
| Microsoft Entra ID | Sign-in for the web app and Teams; OAuth2 resource-server tokens for the services |
| Microsoft Teams | Tabs, bot, activity feed, message extensions and meeting side panel |
| Kubernetes cluster + container registry | Hosting and image distribution |
| SQL Server (or PostgreSQL) instance | Operational databases and the warehouse, where the bank runs its own |
| AI model endpoints | Whichever models the bank approves, reached only through the model gateway (rule 6); no provider SDK is bundled |
| Source systems | Transaction, client and booking feeds consumed by `ingestion` |
| SMTP / notification transport | Where the bank routes notifications |

## 8. Licensing and provenance notes

- The product itself is UNLICENSED (proprietary, `package.json`).
- The Java stack is predominantly Apache-2.0 (Spring Boot, Spring Cloud, Spring Modulith,
  Micrometer, OpenTelemetry, Flyway Community, Testcontainers, `mssql-jdbc`); H2 is EPL-1.0/MPL-2.0
  and PostgreSQL's JDBC driver is BSD-2-Clause. The SQL Server image carries Microsoft's own
  licence terms, which the bank must hold.
- Node packages are predominantly MIT, with the Microsoft Teams and MSAL packages under MIT as
  published by Microsoft.
- This file is a hand-compiled inventory. For an attestable SBOM (CycloneDX or SPDX) that includes
  transitive packages and licence metadata, generate it from a resolved build in CI —
  `cyclonedx-maven-plugin` for the reactor and `@cyclonedx/cyclonedx-npm` for the workspaces — and
  attach it to the release artefacts.

## 9. Maintaining this file

Re-compile it whenever a version anchor in `pom.xml`, a workspace `package.json`, a Dockerfile base
image, `deploy/compose/*.yaml` or `deploy/helm/rm-platform/Chart.yaml` changes. New dependencies
follow the repository rules: exact versions, the lockfile committed, and a new workspace's
`package.json` copied into every Dockerfile that runs `npm ci`.
