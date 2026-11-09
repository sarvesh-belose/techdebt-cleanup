# CLVWorkbench — Microservices Pivot: Architecture & Realization

**Author role:** Microservices Architect (`skills/Microservices-Architect/SKILLS.md`)
**Status:** Accepted direction — realization in progress (reference slice first)
**Prepared on:** 2026-10-22
**Scope:** How CLVWorkbench becomes a horizontally scalable microservices platform for
**~4,000 provisioned / ~2,000 concurrent users per bank** (growth envelope 10,000 / ~5,000),
CRUD-heavy, without discarding the
business requirements.

This folder is the design for the pivot. It does **not** replace the generated technical design under
`docs/architecture/` (v0.4) — it **realizes** it and hardens it for scale. Read it with
`docs/architecture/04-services.md` (the 22→14 service map) and the 12 ADRs.

---

## 1. The brutal assessment (what is really being asked)

The request is to "pivot this application to a microservices-based application." The honest finding:

> **CLVWorkbench is already designed as microservices on paper, but none of it is built.**

- The documented architecture (`docs/architecture` v0.4 + ADR-0001…0012) already specifies Spring Boot
  on Java 21, RabbitMQ, SQL Server, Redis on Kubernetes, **database-per-service** (ADR-0009),
  **transactional outbox + idempotent consumers** (ADR-0005), **Spring Cloud Gateway**,
  **K8s DNS service discovery** (ADR-0001), **Resilience4j**, a **model gateway** (ADR-0007/8),
  **Flowable behind a no-code designer** (ADR-0006), and a concrete **22-service → 14-deployable map**.
- What physically exists today is a **Node/TypeScript `mock-api` modular monolith** that serves all 24
  OpenAPI contracts from the seed, plus the React web app, the SQL Server warehouse, the AI prompt layer,
  and the seed portfolio. There is **no Java, no `services/`/`libs/`, no API gateway, no autoscaling, no
  observability, and no CI pipeline**; RabbitMQ/Redis/SQL/object-store exist only in local Compose.

Therefore the pivot is **not a greenfield rewrite**. A rewrite would throw away:

- 24 OpenAPI contracts + 16 AsyncAPI events already generated and consumed by the web app;
- the 186-table operational model and the SCD2 warehouse the whole product reconciles against;
- the AI layer (13 agents, tool catalogue, prompts, quality gates) and the 12 E2E scenarios CI gates on;
- a responsive React app that already covers every screen.

The correct move — and the one a brutal architect defends in the steering committee — is a
**strangler-fig realization**: put a real gateway in front, stand up the Spring service tier and the
scale infrastructure that the ADRs already call for, and move **one bounded context at a time** off the
mock-api until it is retired. Risk stays bounded, the contracts/web/warehouse stay stable, and every
step is shippable.

### Where a rewrite *is* warranted
- The **backend runtime**: the Node `mock-api` is a stand-in; it is replaced by Spring Boot services.
- The **folder structure**: we add `libs/`, `services/`, per-service `db/<service>/`, a parent Maven
  build and CI — the layout `docs/architecture/14-repo.md` already prescribes but that does not exist.
- The **infrastructure**: gateway, autoscaling, observability, and the broker/cache/DB as first-class
  deployables — none of which exist yet.

### Where a rewrite is *malpractice*
- The React web app, the 24 contracts, the 16 events, the warehouse catalogue, the AI layer, the seed.
  These are the business requirements made executable. They are **kept**.

---

## 2. Skill vs. project ADRs — divergences (resolved in favour of the ADRs)

The provided `Microservices-Architect/SKILLS.md` is a generic competency template. Where it conflicts
with this project's accepted ADRs, the ADRs win, because they were chosen for this product's constraints:

| SKILLS.md suggests | This project uses | Why |
|---|---|---|
| Eureka service discovery | **Kubernetes Service DNS** | ADR-0001 — the platform targets K8s/OpenShift; a second registry is redundant and a failure mode. |
| H2 for dev/test | **SQL Server in Testcontainers** | ADR-0002 — H2 can't run temporal tables, columnstore, partitioning, RLS, `SESSION_CONTEXT`, `ROWVERSION`. Tests must hit the real engine. |
| Hystrix circuit breakers | **Resilience4j** | Hystrix is end-of-life; ADRs and `04-services.md` already standardise on Resilience4j. |
| Spring Cloud Config server | **Config in each service's DB (maker/checker) + ConfigMap/Vault for infra** | ADR-0001 — business config needs audit and two-person control, so it lives in the DB, not a config server. |

The rest of the skill (service decomposition by bounded context, sync/async split, saga over distributed
transactions, Redis caching, Resilience4j patterns, K8s/Docker, ADRs as governance) aligns with the
project and is applied.

---

## 3. Target architecture (one paragraph)

A **Spring Cloud Gateway** edge terminates Entra ID JWTs, applies per-user rate limits, and routes to
**stateless Spring Boot services** discovered by K8s DNS. Each service **owns its SQL Server schema-set**
and publishes state changes through a **transactional outbox** to **RabbitMQ** (`rm.events`); consumers
are **idempotent** and build **local read projections**. Heavy, cross-context reads are answered by the
**analytics service over the SCD2 warehouse** (CQRS), fronted by **Redis**. Every synchronous hop is
wrapped in **Resilience4j** (timeout, retry-with-jitter, circuit breaker, bulkhead, fallback). Services
**autoscale** on CPU and RPS/queue-depth (HPA + KEDA). Everything is **traced (OpenTelemetry), metered
(Micrometer→Prometheus) and logged (structured JSON)** with correlation IDs. The AI runtime calls models
**only through the model gateway**. All of this is deployed **once per bank, single-tenant**, configured
at runtime (no code forks), with **maker/checker** on every configuration change.

See `service-catalogue.md`, `capacity-model.md`, `target-structure.md`, and `adr/` for the detail.

For a client deployment: `hardware-sizing-rfp.md` sizes the on-premises infrastructure per environment
(UAT, production DC and DR), with and without Kubernetes, and
`hardware-sizing-calculator.html` is the same model as a page you can open and change the inputs on.

---

## 4. Invariants preserved (non-negotiable — from CLAUDE.md + ADRs)

1. Never read/write another service's database — events, projections or APIs only.
2. Every state change publishes through the **outbox**; every consumer is **idempotent**.
3. Every agent action passes the **autonomy check**; numbers come from tools, not the model.
4. Every admin configuration change goes through **maker/checker**; config tables are never written directly.
5. A case's steps/assignees/TATs/escalations come from the bank's **workflow definition** (P10), never code.
6. Agents never call a provider SDK; every model call goes through the **model gateway**.
7. Dimensions follow **SCD2**; facts carry version + durable keys + lineage.
8. Integration tests run against **SQL Server in Testcontainers**, never H2.
9. Single tenant per bank; **one build**, configured per bank at runtime.

---

## 5. Reality check — this machine cannot build Java

Per `CLAUDE.md`, this environment has Node 24 and Git but **no JDK and no container runtime**. Therefore:

- The Spring Boot services, libs and gateway in this pivot are **authored, structured and wired**, but
  **compiled and tested only in CI** (and a JDK+container dev/perf environment). This document and the
  scaffold are the deliverable; the green build is CI's job.
- Everything that *can* be validated locally is: contract lint, Helm render, YAML/JSON/XML
  well-formedness, SQL structural checks, and the existing Node `npm run check`.
- The capacity targets (p95 < 500 ms at ~2,000 concurrent, cache hit > 80%, HPA scale-out, breaker trips)
  are asserted in a **load/perf stage** that runs in a provisioned environment, not here.

This limitation is stated so no one mistakes "scaffolded" for "verified in production".
