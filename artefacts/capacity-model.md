# Capacity & Scalability Model

**Baseline:** ~4,000 provisioned users / **~2,000 concurrently active** per bank (single tenant),
**read-heavy** (~85% read / 15% write), CRUD across all contexts.

**Growth envelope:** 10,000 provisioned / ~5,000 concurrent, which is what section 7 sizes. The design
reaches it on configuration alone, so the baseline is a floor, not a ceiling.

Both figures come from **spec NFR-7**, which states the population (2,000 RMs, about 4,000 users once
credit, operations and finance are counted), the peak concurrency, and the growth envelope. The
envelope is there because **lead-management NFR-001** asks for 5,000–10,000+ internal users with
"concurrency and workload profile to be confirmed".

The one number underneath all of them is **not** given anywhere: peak concurrency is taken as ~50% of
the provisioned population, for a workbench people keep open all day. That ratio produces the 2,000 in
the baseline and the 5,000 in the envelope, so it is the assumption to challenge first — a bank whose
users sign in differently changes every figure below it.

A bank may state a lower ratio, and the first one has: the on-premises sizing in
`architecture/hardware-sizing-rfp.md` answers **10,000 provisioned at 30% concurrency — 3,000 active,
~600 RPS**. That sits *inside* the envelope in section 7 rather than outside it, so the figures here
are unchanged and that bid needs no concession. Treat 50% as the product's planning default, not as a
floor every bank must meet.

**That bank has also relaxed the latency targets** — API p95 2 s, drill 5 s, message processing 10 s,
against the 500 ms / 2 s / 5 s in section 6 — which lets the autoscaler hold 85% CPU instead of 65% and
each pod carry 65–105 RPS instead of 50–80. It takes that sizing from 732 cores to 664. The SLO table in
section 6 is unchanged and remains **what the product builds and tests to**; a bank may be held to less
than the product achieves, but the product is not re-engineered down to a bid. Keep the two apart, and
see section 2.3 of the sizing document for the derivation.

All numbers are **planning assumptions to confirm in sprint 0** (as `docs/architecture/12-ops.md`
insists). The point is the method and the ratios, not false precision.

## 1. Workload model

| Quantity | Assumption | Basis |
|---|---|---|
| Provisioned users | ~4,000 (2,000 of them RMs) | spec NFR-7 |
| Concurrent active (peak) | ~2,000 | derived: ~50% of provisioned, read-heavy RM workbench |
| Requests / active user / min (peak) | ~12 | screen loads + drill + saves; bursty |
| Peak request rate | 2,000 × 12 / 60 ≈ **400 RPS** aggregate | derived |
| Read : write | 85 : 15 | ≈ 340 read RPS, 60 write RPS |
| p95 budget | API < 500 ms; drill < 2 s/page; msg < 5 s | NFR-2 / `12-ops.md` |
| Cache hit target | > 80% (drill L1–L3, config, entitlements) | `12-ops.md` |
| Model calls | 50–150 / RM / day → sizes gateway concurrency, not web RPS | `12-ops.md` |

400 aggregate RPS at a single-tenant bank is **modest** for a microservices mesh — the engineering risk
is not raw throughput, it is **tail latency under fan-out, connection-pool exhaustion, and cache-miss
storms**. The design optimises those.

## 2. Per-service sizing (reference slice; others follow the ratio)

Rule of thumb: each pod handles ~50–80 RPS at p95 < 500 ms for these workloads; min 2 for HA; HPA to the
max below. Pools sized by **Little's Law** (concurrency = arrival_rate × service_time) with headroom,
never "more is better" — oversized pools exhaust SQL Server.

| Service | Share of RPS | Pods (min→max) | HikariCP max/pod | DB connections (max pods × pool) |
|---|---|---|---|---|
| gateway | 100% ingress (~400) | 3 → 10 | n/a (reactive) | n/a |
| identity | ~15% (auth on every call is cached) | 2 → 6 | 10 | 60 |
| reference (read-heavy) | ~30% | 3 → 10 | 12 | 120 |
| deals (write-heavy) | ~15% | 2 → 8 | 12 | 96 |
| analytics (drill/reads) | ~20% | 2 → 8 | 10 (to warehouse read pool) | 80 |
| each other service | remainder | 2 → 6 | 8–10 | ~60 |

**SQL Server connection budget:** with ~20 services at max fan-out the worst case is ~1,200 app
connections against a tenant SQL Server sized for several thousand — fine, and bounded because pools cap
per pod and HPA caps pods. Writes and reads use **separate pools** (write pool small; read pool larger,
pointed at read replicas / the warehouse).

## 3. The three levers that actually carry the load

1. **CQRS read path.** Client 360, dashboards, drill, scorecards — the 85% — are **reads from the SCD2
   warehouse aggregates and event-fed projections**, not from the OLTP services. The analytics service +
   warehouse columnstore + month partitioning absorb the read volume; OLTP services stay small and
   write-focused. Drill L1–L3 are **Redis-cached** with version-pinned drill tokens (`07-drill.md`).
2. **Redis cache (>80% hit).** Caches: entitlement/scope snapshots (per user, short TTL), reference
   config (account field map, dashboard catalogue), drill L1–L3 cells, metric catalogue, session. A cache
   miss never stampedes: single-flight / request-coalescing + jittered TTLs.
3. **Async writes via outbox + RabbitMQ.** A write commits to the owning service's DB + outbox in one
   transaction and returns; projections and downstream effects propagate via events. The user-facing
   write is one local transaction, not a distributed one — p95 stays low and spikes are absorbed by the
   broker, not the DB.

## 4. Autoscaling & resilience

- **HPA** on CPU (target ~65%) + custom metric **requests-in-flight / RPS per pod**; **KEDA** scales
  RabbitMQ consumers on **queue depth** so event backlogs drain by adding consumers, not by lag.
- **PodDisruptionBudget** `minAvailable: 1` and **topology spread** across zones (already in the web
  template; applied to every service).
- **Resilience4j** on every sync hop: connect/read timeouts (e.g. 300 ms/2 s), retry ×2 with jitter on
  idempotent GETs only, circuit breaker (50% error over rolling window → open 30 s), bulkhead (bound
  concurrent calls per dependency), and a defined fallback (cached value / `503` with `Retry-After`).
- **Gateway rate limiting:** token bucket per user (and per tenant), burst + sustained, returning `429`
  with `Retry-After`; protects the mesh from a hot client.

## 5. Data-tier scale

- **Reads:** SQL Server **Always On** readable secondaries for the warehouse; analytics read pool targets
  the secondary. Columnstore + month partitions already in the catalogue.
- **Writes:** per-service OLTP schemas; hot tables already indexed (owner/state, date). Partition only
  where a table proves large (audit, events, snapshots) — not prematurely.
- **RabbitMQ:** 3-node quorum cluster; queues rebuilt from outboxes on loss (`12-ops.md`); consumers
  idempotent so redelivery is safe.
- **Redis:** Sentinel/cluster; **cache only, never a system of record** — rebuilt from sources on loss.

## 6. SLO table (what CI/perf asserts)

| SLO | Target | How verified |
|---|---|---|
| API latency p95 | < 500 ms | k6/Gatling at ~2,000 concurrent against the gateway |
| Drill page | < 2 s | perf stage, L4 transactions |
| Message processing | < 5 s | consumer lag metric under load |
| Cache hit rate | > 80% | Redis metrics in Grafana |
| Availability (business hours) | 99.5% | synthetic probes + error budget |
| RPO / RTO | 15 min / 4 h | failover drill (SQL AG, RabbitMQ quorum) |
| Autoscale | pods scale out ≤ 60 s under 2× load | HPA/KEDA event timeline |
| Breaker | opens under injected dependency fault, recovers | fault-injection test |

## 7. Growth envelope: 10,000 provisioned / 5,000 concurrent

This is the top of lead-management NFR-001 at the baseline's concurrency ratio, and it is the figure to
answer a bid with when a bank states a population rather than a concurrency.

At 5,000 concurrent (~1,000 RPS) the same design holds by raising HPA maxima (~2.5×) and Redis/RabbitMQ
node counts; the **read path does not touch OLTP**, so the write tier barely moves. The binding
constraint becomes warehouse read-replica capacity and Redis memory, both scaled horizontally. No
re-architecture is needed between 2,000 and 5,000 concurrent — only configuration (replica counts, pool
sizes, cache memory), consistent with "one build, configured per bank at runtime".
