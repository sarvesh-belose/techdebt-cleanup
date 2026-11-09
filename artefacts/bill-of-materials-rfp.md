# RM Platform — Bill of Materials

**Client-facing response document.** Prepared for RFP submission.

Document version 1.0 · Product version 0.1.0 · 2026-11-06

> **Internal note — delete this block before submission.**
> This is the client-facing cut of `architecture/bill-of-materials.md`. It states versions at major/minor
> level only and deliberately carries no delivery-readiness claims per component — section 9 is a
> placeholder for the bid team to complete from a green CI build. Review sections 7 and 9 and the
> licence posture in section 8 with whoever owns the bid before this goes out.

---

## 1. Purpose and scope

This document lists every software component that makes up one deployment of RM Platform: the
application components delivered by us, the third-party platform software those components run on,
the open-source libraries they are built from, and the services the bank provides from its own
estate.

It covers the product as deployed for a single bank. RM Platform is deployed once per bank, inside
the bank's own cloud tenancy or data centre, under the bank's own operational control. No component
listed here is shared with another client, and no client data leaves the bank's environment.

---

## 2. Deployment model at a glance

| Characteristic | Position |
|---|---|
| Tenancy | Single tenant. One deployment per bank; no shared infrastructure, no shared data store. |
| Hosting | The bank's Kubernetes cluster, in the bank's cloud tenancy or data centre. |
| Image distribution | Container images delivered to the bank's own registry. |
| Configuration | One build for every bank. Bank and environment settings are supplied at run time; nothing bank-specific is compiled into an image. |
| Authentication | The bank's Microsoft Entra ID. The product issues no credentials of its own. |
| AI models | Whichever model endpoints the bank approves, reached through a provider-neutral gateway. No model provider is embedded in the product. |
| Channels | A responsive web application in any modern browser, plus Microsoft Teams. No native mobile application. |

---

## 3. Application components delivered

The application is delivered as **14 deployable units** containing 23 service modules, plus the web
and Microsoft Teams channel components. Each unit is an independently deployable, independently
scalable container image.

### 3.1 Deployable units

| Unit | Service modules inside | Responsibility |
|---|---|---|
| Edge | gateway | Single entry point: token validation, routing, rate limiting, circuit breaking |
| Platform core | identity, reference | Users, roles, entitlements and data scope; the client master |
| Ingestion | ingestion | Inbound transaction, client and booking feeds |
| Analytics | analytics | Metric catalogue, semantic layer and drill-down to underlying transactions |
| Audit | audit | Append-only audit and data-access ledger |
| Agent runtime | agent-runtime | AI control plane and the model gateway |
| Documents | documents | Document ingestion and field extraction |
| Operating layer | work, inbox, nudges, planner | Open items, decision inbox, nudges, day plan |
| Relationship | engagement, monitoring, compliance, performance | Meetings and call reports, portfolio health and alerts, covenants and KYC, targets and scorecards |
| Content | content | Content templates and drafts |
| Value | value, planning | Wallet, client lifetime value, budgets, initiatives and account plans |
| Deals | deals | Leads, opportunities, deal rooms and pricing |
| Intelligence | intelligence | Market events and insight digests |
| Teams | teams-adapter | Microsoft Teams tabs, bot, activity feed and message extensions |

### 3.2 Channel and data components

| Component | Description |
|---|---|
| Web application | Responsive single-page application, served as static assets from a hardened, non-root web server image |
| Microsoft Teams application | Teams app package and bot adapter: tabs, bot, activity feed, link unfurling, client search and the meeting side panel |
| Database migration component | Versioned, forward-only schema migrations for the operational databases and the warehouse, applied as a controlled job at deployment |

Each service module owns its own database schema. No service reads or writes another service's
data; integration is by published events and APIs only. Every state change is published through a
transactional outbox and every consumer is idempotent, so no message is lost or applied twice.

### 3.3 Development and demonstration tooling (not deployed to the bank)

A contract-driven mock API and a fictional seed portfolio are used for development, automated
testing and product demonstrations. They are not part of the bank's deployment and are not
installed in the bank's environment. All demonstration data is invented; the product repository
contains no real client data.

---

## 4. Runtime platform

| Layer | Component | Version | Licence family |
|---|---|---|---|
| Application runtime | Java (Eclipse Temurin), long-term support release | 21 | GPL-2.0 with Classpath Exception (OpenJDK) |
| Application framework | Spring Boot, with Spring Cloud and Spring Modulith | Spring Boot 3.3 | Apache-2.0 |
| Channel runtime | Node.js, long-term support release | 24 | MIT |
| Web framework | React with TypeScript | React 19 | MIT |
| Web server | NGINX (unprivileged, non-root image) | 1.29 | BSD-2-Clause |
| Operational database | Microsoft SQL Server | 2022 | Microsoft commercial — bank-held |
| Operational database (alternative) | PostgreSQL | Supported alternative dialect | PostgreSQL Licence |
| Data warehouse | Microsoft SQL Server, full history (SCD Type 2) | 2022 | Microsoft commercial — bank-held |
| Messaging | RabbitMQ | 4 | MPL-2.0 |
| Cache | Redis | 8 | Redis Source Available / RSALv2 — see 8.2 |
| Object storage | S3-compatible object store | — | Apache-2.0 (reference implementation) |
| Schema migration | Flyway (Community) | 12 | Apache-2.0 |
| Orchestration | Kubernetes, deployed by Helm chart | Helm 3 | Apache-2.0 |

The operational database layer is abstracted behind a configurable dialect. SQL Server is the
primary and recommended platform; PostgreSQL is supported for banks standardised on it. The choice
is a deployment-time setting, not a code change.

### 4.1 Observability

| Component | Version | Licence |
|---|---|---|
| OpenTelemetry Collector | 0.111 | Apache-2.0 |
| Prometheus (metrics) | 2 | Apache-2.0 |
| Grafana (dashboards) | 11 | AGPL-3.0 — see 8.2 |
| Micrometer metrics and tracing instrumentation | Bundled with the application framework | Apache-2.0 |

Every service exposes health, metrics and distributed traces over open standards (Prometheus
exposition format and OTLP). Banks that already run an observability platform can point the
collector at it and omit the bundled Prometheus and Grafana entirely.

---

## 5. Third-party libraries

All third-party libraries are pinned to exact versions and locked, so a given product version always
resolves to an identical set of components. The counts below are direct, first-party-declared
dependencies; the full resolved tree, including transitive components, is provided as a machine-readable
SBOM with each release (section 8.3).

### 5.1 Server-side (Java)

Centrally version-managed across all 23 service modules from a single dependency manifest, so every
service runs an identical library set.

| Area | Components |
|---|---|
| Application framework | Spring Boot (web, actuator, data access, validation, JSON), Spring Modulith |
| Edge | Spring Cloud Gateway, Resilience4j circuit breakers |
| Security | Spring Security, OAuth2 resource server (Entra ID token validation) |
| Messaging | Spring AMQP (RabbitMQ) |
| Data access | Spring Data JDBC; Microsoft JDBC driver for SQL Server; PostgreSQL JDBC driver |
| Schema migration | Flyway core, with SQL Server and PostgreSQL support |
| Caching | Spring Data Redis |
| Observability | Micrometer Prometheus registry, Micrometer OpenTelemetry tracing bridge, OpenTelemetry OTLP exporter |
| Test only | Spring Boot test, Testcontainers (SQL Server, PostgreSQL, RabbitMQ), H2 |

No AI model provider SDK is present in any service. All model calls pass through the product's own
model gateway, which enforces the bank's approved-endpoint and data-classification rules.

No charting, PDF or document-generation library is bundled into the services.

### 5.2 Web and Microsoft Teams channel

| Area | Components |
|---|---|
| Web framework | React, React DOM, React Router |
| Server state | TanStack Query |
| Design system | Microsoft Fluent UI components and icons |
| Authentication | Microsoft Authentication Library (MSAL) for browsers |
| Microsoft Teams | Microsoft Teams JavaScript SDK; Teams API, apps, cards and common packages |
| API access | Typed client generated directly from the published OpenAPI contracts |
| Build and test only | Vite, Vitest, TypeScript, Playwright, axe accessibility engine, Testing Library |

All libraries in the web and Teams channel are MIT-licensed, including the Microsoft Fluent UI,
MSAL and Teams packages.

---

## 6. Standards and interfaces

| Area | Standard |
|---|---|
| REST APIs | OpenAPI 3; every endpoint is published as a contract and the clients are generated from it |
| Events | AsyncAPI with JSON Schema payloads; a published event catalogue |
| Errors | RFC 7807 problem+json |
| Authentication | OAuth 2.0 / OpenID Connect bearer tokens issued by the bank's Entra ID |
| Metrics | Prometheus exposition format |
| Tracing | OpenTelemetry / OTLP |
| Accessibility | WCAG 2.1 AA, tested automatically on every build with the axe engine at desktop, tablet and phone widths |
| Data history | SCD Type 2 dimensions with durable keys and lineage columns on every fact |

---

## 7. Supplied by the bank

These are integration points and infrastructure the bank provides. They are not licensed, resold or
deployed by us.

| Dependency | Role | Required |
|---|---|---|
| Microsoft Entra ID | Sign-in and token issuance for web and Teams | Yes |
| Kubernetes cluster | Hosting for all deployable units | Yes |
| Container registry | Distribution of delivered images inside the bank's estate | Yes |
| Microsoft SQL Server (or PostgreSQL) | Operational databases and the warehouse | Yes |
| RabbitMQ, Redis and an S3-compatible object store | Messaging, cache and document storage, where the bank runs its own | Either bank-run or deployed from our chart |
| Microsoft Teams tenant | The Teams channel | Only if the Teams channel is in scope |
| Approved AI model endpoints | The models the product's agents are permitted to call | Yes, for AI features |
| Source systems | Transaction, client and booking feeds | Yes |
| Notification transport | Where the bank routes outbound notifications | Yes |
| Observability platform | Where metrics and traces are sent, if not using the bundled stack | Optional |

---

## 8. Licensing and compliance

### 8.1 Product licensing

RM Platform is proprietary software, licensed to the bank under the terms of the master agreement.
No source code is distributed as part of the deployment.

### 8.2 Open-source position

The overwhelming majority of third-party components are permissively licensed — Apache-2.0, MIT,
BSD or the PostgreSQL Licence — and are used unmodified, as libraries linked into our own
components or as unmodified container images. No open-source component is modified, and no
copyleft obligation attaches to the bank's use of the product.

Three components warrant a specific note for the bank's own open-source review:

| Component | Licence | Position |
|---|---|---|
| OpenJDK / Eclipse Temurin | GPL-2.0 with Classpath Exception | The Classpath Exception is expressly designed so that applications running on the JVM are unaffected. No obligation attaches to the product or to the bank. |
| Redis | RSALv2 (from Redis 7.4 onward) | Used unmodified, as a cache, and not offered as a database service to third parties. If the bank's policy requires an OSI-approved licence here, Valkey or an earlier BSD-licensed Redis is a drop-in substitute; this is a deployment-time choice. |
| Grafana | AGPL-3.0 | Used unmodified as a separate dashboard application; it is not linked into or distributed with our components. It can be omitted entirely where the bank has its own dashboarding. |

Microsoft SQL Server is commercially licensed and must be covered by the bank's own licensing. The
same applies to the bank's Microsoft Entra ID and Microsoft Teams entitlements.

### 8.3 Software Bill of Materials

A machine-readable SBOM in CycloneDX format, covering every direct and transitive component with
its resolved version and licence, is generated from the build pipeline and provided with each
release alongside the container images. Images are signed and digest-pinned. Vulnerability scanning
runs against each release and the SBOM supports the bank's own scanning and policy tooling.

### 8.4 Data residency and client data

All data stays in the bank's environment. No component in this bill of materials transmits client
data outside the bank's boundary, including to us. AI model calls go only to endpoints the bank has
approved and configured, and the model gateway refuses to send any data class an endpoint is not
approved for rather than silently downgrading it.

---

## 9. Component currency and support

> **To be completed by the bid team.** All platform components in sections 4 and 5 are on current,
> actively supported major releases — Java 21 LTS, Node 24 LTS, Spring Boot 3.3, React 19, SQL Server
> 2022 — and the product tracks them on a defined upgrade cadence. State that cadence, the supported
> product versions, the patch SLA for critical vulnerabilities, and the delivery-readiness position
> per deployable here, taking the readiness position from the current release build rather than from
> this document.

---

## 10. Document control

| Field | Value |
|---|---|
| Document | RM Platform — Bill of Materials (client-facing) |
| Document version | 1.0 |
| Product version | 0.1.0 |
| Compiled | 2026-11-06 |
| Source of record | The product's build manifests; re-compiled on every release |
| Internal counterpart | `architecture/bill-of-materials.md` (full version detail, internal) |
