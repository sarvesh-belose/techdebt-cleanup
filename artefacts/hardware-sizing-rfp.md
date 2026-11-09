# RM Platform — Infrastructure Sizing (On-Premises)

**Client-facing response document.** Prepared for RFP submission, to support infrastructure procurement.

Document version 3.2 · Product version 0.1.0 · 2026-11-09

> **Internal note — delete this block before submission.**
> Sized for the client's stated profile: **on-premises, 10,000 provisioned users, 30% peak concurrency,
> ~100,000 wholesale banking customers, three environments (UAT, Production DC, Production DR)**, and
> presented as two deployment options — **with Kubernetes (Option A)** and **without (Option B)**.
> Nine things the bid team must settle before this goes out:
> 1. **Option B is a delivery scope addition, not a configuration choice.** The repository ships a Helm
>    chart (production) and Docker Compose (local development only). There are **no systemd units, no
>    Ansible or Puppet roles, and no VM deployment automation** — verified against `deploy/`. Section 3.2
>    states this in client-facing terms and section 3.4 quantifies what we would have to build and
>    support. **Do not price Option B as though it ships today.** If the bid must offer it, it needs an
>    effort estimate and a support position from delivery and from whoever owns the product roadmap.
> 2. **The client has also relaxed the performance targets** to API p95 < 2 s, drill page < 5 s and
>    message processing < 10 s, against spec NFR-2's 500 ms / 2 s / 5 s. That is worth 86 cores on Oracle
>    — 780 down to 694, or 732 down to 664 on SQL Server — and section 2.3 shows the working. **The spec is deliberately not changing.** Spec NFR-2
>    and `docs/source/architecture/5-ai-ops.html` keep the tighter figures, and so does the SLO table in
>    `capacity-model.md`: those remain what the product builds and tests to. This document sizes what
>    *this bank* has asked to be held to, which is less. The two are meant to differ, so do not "fix" the
>    gap by editing the spec. One thing still to settle: the relaxation is a commitment to the client, so
>    confirm the product is content to be held only to the looser numbers at this bank before the bid
>    goes out — and note that it buys the bank a cheaper platform than the product is capable of, which
>    is a point worth making in the bid rather than hiding.
> 3. **Option B costs the same and delivers less.** Both options land at 694 cores. Option B is 5 fewer
>    servers and gives up autoscaling, self-healing and rolling deployment. That is the single most
>    useful finding in this document for a procurement conversation — lead with it rather than burying
>    it, because a client asking "can we avoid Kubernetes?" is usually asking "can we spend less?"
> 4. **The client's concurrency is lower than the product's own assumption.** `capacity-model.md` and
>    spec NFR-7 assume 50% of the provisioned population is active at peak; the client states 30%. At
>    10,000 users that is **3,000 concurrent, not 5,000** — so this sizing sits *inside* the envelope the
>    spec already commits to. Defensible position; state it. Do not quietly raise the product's 50%
>    assumption to match — the two figures measure different things and both are correct.
> 5. **The bank has said it will work with Oracle, and the product does not support Oracle.** The
>    operational databases are parameterized across SQL Server, PostgreSQL and H2; the warehouse is
>    SQL Server alone, in T-SQL, with 38 stored procedures and a columnstore dependency.
>    `architecture/oracle-support-gap.md` has the evidence and the scope. This document now sizes Oracle
>    because that is what was asked for — **it is not a statement that the product runs on it**, and
>    section 3.5 says so in client-facing terms. Get the delivery estimate before this goes out.
> 6. **Oracle needs three separately licensed options, and that is the largest licence line.**
>    Partitioning (required), Active Data Guard (for the readable standby) and Database In-Memory (the
>    columnar store) are all extra-cost on top of Enterprise Edition, where SQL Server Enterprise
>    includes all three. The sizing assumes In-Memory is **not** taken, which is why the warehouse is
>    34 cores and 11 TB rather than 24 and 6 TB. Price it both ways — taking In-Memory trades cores for
>    licence. **Engines were raised to reduce licence cost; Oracle may well increase it.**
> 7. **A readable secondary is an active replica, so it is licensed.** Software Assurance covers a
>    genuinely passive DR replica; it does not cover the DC readable secondary. Confirm with the client's
>    licensing partner rather than asserting it.
> 8. **On-premises is not air-gapped.** Entra ID sign-in and the model gateway both reach outside the
>    data centre. If the client mandates zero egress, section 8 option B is the answer and it changes the
>    bill materially. Raise it as a question; do not price it silently.
> 9. **Nothing here has been load-tested, and Helm has not been rendered on a developer machine** (none
>    has it installed). CI lints and renders the chart on every push — take any readiness claim from a
>    green build. The confirmation commitment in section 11.3 is deliberate; do not delete it.

---

## 1. Purpose and scope

This document states the physical and virtual infrastructure the bank procures to run RM Platform in
its own data centre. It is written to be used as a procurement input: every line is a server type with a
core count, a memory figure, a storage figure, the operating systems it is supported on, the database it
carries where applicable, and the software that must be installed on it.

### 1.1 Environments

| Environment | Purpose | Site |
|---|---|---|
| **UAT** | User acceptance testing, training, and release validation before production | Primary data centre |
| **Production — DC** | The live service | Primary data centre |
| **Production — DR** | Warm standby for the live service | Disaster recovery data centre |

Development and system integration run on our side and are not part of the bank's procurement.

### 1.2 Deployment options

Both options are on-premises, inside the bank's data centre, under the bank's operational control.

| | **Option A — With Kubernetes** | **Option B — Without Kubernetes** |
|---|---|---|
| How services run | 23 containerised services on a Kubernetes cluster, deployed by Helm chart | 23 Spring Boot executable JARs as supervised processes on application VMs |
| Status in the product today | **Ships today.** Helm chart, container images, CI lint and render on every push | **Requires deployment automation we would build and support.** See section 3.2 |
| Scaling | Automatic — CPU-driven for services, queue-depth-driven for consumers | Static. Capacity is provisioned for peak and does not move |
| Recommendation | **Recommended** | Offered where the bank has no container platform and no intention of acquiring one |

Section 3.4 compares the two directly. The short version: **they cost the same and Option B does less.**

No infrastructure listed in this document is licensed, resold or operated by us. The product is deployed
once for this bank alone (single tenant), with no shared infrastructure and no data leaving the bank's
environment.

### 1.3 Working the figures yourself

`architecture/hardware-sizing-calculator.html` carries this document's sizing model as a page that opens
in a browser with no build and no server. Change the user population, the concurrency ratio, the customer
count, the data volumes or **any of the four performance targets** — API p95, drill page, message
processing and cache hit rate — and every server count, core count and volume below recomputes, in either
deployment option, with the environments and the reused capability of section 3.3 as switches.

It is the right tool for the question a bid conversation actually asks — *what if concurrency is 40%, or
the bank has 250,000 customers?* — and for exporting a table into a procurement spreadsheet. At this
document's inputs it reproduces every server, core and memory figure here exactly; storage runs 1–3%
high, because the backup target is derived from a ratio there where this document rounded it.

---

## 2. Sizing basis

The sizing below is derived from the following workload and holds only for it. It is common to both
deployment options.

| Quantity | Assumption | Source |
|---|---|---|
| Provisioned users | **10,000** | Client requirement |
| Peak concurrent users | **3,000** (30% of provisioned) | Client requirement |
| Wholesale banking customers (client entities) | **~100,000** | Client requirement |
| Client groups · accounts · facilities | ~25,000 · ~500,000 · ~150,000 | Derived from the customer count |
| Read / write mix | 85 : 15 — a read-heavy relationship workbench | Product workload model |
| Requests per active user per minute at peak | ~12 (screen loads, drill-down, saves) | Product workload model |
| **Peak request rate** | **~600 requests/second aggregate** (~510 read, ~90 write) | 3,000 × 12 ÷ 60 |
| Postings | 2–5 million per day; 1.5–3.5 billion rows over 24 months of history | Product workload model |
| Daily snapshots | ~650,000 rows per day across balances and facilities | Product workload model |
| AI model calls | 50–150 per relationship manager per day | Product workload model |
| **Performance targets** | **API p95 < 2 s · drill page < 5 s · message processing < 10 s · cache hit > 80%** | **Client requirement** |
| Availability · recovery | 99.5% in business hours · RPO 15 minutes · RTO 4 hours | Spec NFR |

### 2.1 Why the data tier is the larger share of the bill

Six hundred requests per second at a single bank is a modest aggregate load. The footprint is driven
less by raw throughput than by three structural properties of the design:

1. **The read path does not touch the transactional services.** Client 360, dashboards, drill-down and
   scorecards — the 85% — are served from the data warehouse aggregates and event-fed projections, not
   from the operational database. The warehouse and its **readable secondary** absorb the read volume;
   the transactional services stay small. This is the most important sizing consequence of the
   architecture, and it is why the warehouse pair is the biggest pair of servers in either option.
2. **A cache carries better than 80% of repeat reads.** Entitlement snapshots, reference configuration,
   drill-down levels 1–3 and the metric catalogue are cached, which keeps both the database connection
   budget and the service instance count bounded.
3. **Writes are local and asynchronous.** A write commits to one service's database and its outbox in a
   single transaction and returns; downstream effects propagate as events. Spikes are absorbed by the
   message broker, not the database.

### 2.2 Application tier demand at this workload

The application is 23 independently scalable services. This table states the *demand*; how each option
provisions for it differs, and that difference is section 3.4.

| Property | Value at this workload |
|---|---|
| Instance floor, all services (2 per service for HA, 3 for gateway and reference) | 48 instances |
| Instances at expected peak (600 RPS) | ~103 instances |
| Instance ceiling under burst | ~168 instances |
| CPU per service instance | 500 millicores requested (edge gateway: 1 core) |
| Memory per service instance | 1 GiB requested, 2 GiB ceiling (edge gateway: 512 MiB / 1 GiB) |
| Compute at the instance floor | ~26 cores, ~47 GiB |
| **Compute at expected peak** | **~53 cores, ~102 GiB** |
| Compute at the burst ceiling | ~86 cores, ~167 GiB |
| Autoscaler CPU target · throughput per pod | 85% · 65–105 requests/second |

Memory is capped per instance; CPU is not. A hard CPU cap throttles a service at exactly the moment it
is busiest, which surfaces as the tail latency the design exists to avoid.

### 2.3 What the performance targets are worth in hardware

The latency budget decides how hard an instance may be driven before p95 breaks. A looser budget permits
more queueing, so the autoscaler can hold a higher CPU target and each instance serves more requests
before another is needed.

| | At API p95 < 500 ms | **At API p95 < 2 s** |
|---|---|---|
| Autoscaler CPU target | 65% | **85%** |
| Throughput per instance | 50–80 req/s | **65–105 req/s** |
| Instances at expected peak | ~135 | **~103** |
| Application hosts, Option A | 8 | **7** |

**Four times the budget is nowhere near four times the throughput.** An instance's capacity is fixed by
CPU, so relaxing the budget only lets it run closer to saturation, and the gain stops once the autoscaler
can no longer react before the queue grows. That ceiling is reached at about 85% CPU: beyond roughly
2 seconds of budget, this sizing does not fall any further.

The drill budget works differently. It governs how much parallelism each columnstore scan needs, so
relaxing it from 2 seconds to 5 reduces the warehouse from 32 cores to 24. The cache hit rate decides how
much of the read path reaches the warehouse at all; at the stated 80%, about 102 of the 510 read
requests per second do. A bank that achieves only 60% doubles that and puts the warehouse back to 32
cores, which is why the cache hit rate is a sizing input and not a hope.

---

## 3. Production — Primary Data Centre (DC)

All core counts are **physical cores** where the server is bare metal, or **vCPU from a dedicated
reservation** where virtualised. Storage figures are usable capacity after RAID or erasure coding,
excluding snapshot reserve.

### 3.1 Option A — With Kubernetes

#### 3.1.1 Procurement specification

| Server type | Qty | No. of cores (each) | RAM (each) | Hard drive / volumes (each) | OS supported | Database supported | Software required |
|---|---|---|---|---|---|---|---|
| **Orchestration** — Kubernetes control plane *(A1)* | 3 | 8 | 32 GiB | 200 GB OS (SSD) + 100 GB etcd (NVMe, low-latency) | RHEL 9.x · Oracle Linux 9.x · SLES 15 SP5 · Ubuntu 22.04/24.04 LTS | Not applicable | Kubernetes 1.29+ control plane (RKE2, OpenShift 4.14+ or kubeadm), etcd 3.5+, containerd 1.7+ |
| **Application** — Kubernetes worker node *(A2)* | 7 | 16 | 64 GiB | 200 GB OS (SSD) + 300 GB container images & ephemeral volumes (SSD) | RHEL 9.x · Oracle Linux 9.x · SLES 15 SP5 · Ubuntu 22.04/24.04 LTS | Not applicable | kubelet 1.29+, containerd 1.7+, CNI plug-in (Calico or Cilium), CSI driver for the bank's storage platform, OpenTelemetry node agent |
| **Orchestration** — Kubernetes infrastructure node *(A3)* | 2 | 8 | 32 GiB | 200 GB OS (SSD) + 200 GB (SSD) | RHEL 9.x · Oracle Linux 9.x · SLES 15 SP5 · Ubuntu 22.04/24.04 LTS | Not applicable | kubelet, containerd, CNI and CSI as above, plus ingress controller (NGINX or the bank's standard) and cert-manager or the bank's certificate tooling |
| **Routing** — Load balancer / reverse proxy *(A4)* | 2 | 4 | 8 GiB | 100 GB OS (SSD) | RHEL 9.x · Ubuntu 22.04 LTS · or the bank's appliance (F5, Citrix ADC, A10) | Not applicable | HAProxy 2.8+ or NGINX 1.24+, active/passive with a virtual IP, TLS 1.2/1.3 termination, WAF module where bank policy requires it |
| **Database** — Operational, primary *(A5)* | 1 | 12 | 128 GiB | 200 GB OS + **1.5 TB data** + 500 GB transaction log + 500 GB tempdb — NVMe/SSD, **separate volumes** | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai, Enterprise Edition + Partitioning** | Database engine, Always On Availability Groups, SQL Server Agent, Flyway 10+ client, enterprise backup agent |
| **Database** — Operational, synchronous secondary *(A6)* | 1 | 12 | 128 GiB | Identical to A5 | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | As A5, configured as a synchronous AG replica and as the backup source |
| **Database** — Data warehouse, primary *(A7)* | 1 | 34 | 512 GiB | 200 GB OS + **11 TB data** + 2 TB redo log + 2 TB temp tablespace — NVMe, **separate volumes** | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **SQL Server 2022 Enterprise** — columnstore, table partitioning and readable secondaries all required · PostgreSQL 16 alternative | Database engine, Always On AG, SQL Server Agent, enterprise backup agent |
| **Database** — Data warehouse, readable secondary *(A8)* | 1 | 34 | 512 GiB | Identical to A7 | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | As A7, configured as a **readable** synchronous AG replica |
| **Messaging** — RabbitMQ broker *(A9)* | 3 | 4 | 16 GiB | 100 GB OS + 200 GB message store (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | RabbitMQ 3.13+ with **quorum queues**, Erlang/OTP 26, management and Prometheus plug-ins |
| **Cache** — Redis *(A10)* | 3 | 4 | 32 GiB | 100 GB OS + 100 GB (SSD, persistence snapshots only) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | Redis 7.2+ in Sentinel or cluster mode, eviction policy `allkeys-lru`. **Cache only — never a system of record** |
| **Storage** — S3-compatible object store *(A11)* | 4 | 4 | 16 GiB | 100 GB OS + **3 TB data each** (12 TB raw → ~6 TB usable erasure-coded) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | MinIO (current LTS), or the bank's existing S3-compatible store (NetApp StorageGRID, Dell ECS, Ceph RGW) with Signature V4, versioning and server-side encryption |
| **Monitoring** — Observability stack *(A12)* | 3 | 8 | 32 GiB | 100 GB OS + 2 TB metric/trace/log retention (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | Prometheus 2.5x, Grafana 11+, OpenTelemetry Collector, Loki or the bank's log platform |
| **Repository** — Container registry *(A13)* | 2 | 4 | 16 GiB | 100 GB OS + 2 TB image store (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | Harbor 2.10+, or the bank's registry (JFrog Artifactory, Nexus), with image signing and vulnerability scanning |
| **Management** — Bastion / deployment host *(A14)* | 1 | 4 | 16 GiB | 200 GB OS (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | `kubectl` 1.29+, Helm 3.14+, Flyway 10+, the bank's secret-store CLI, OpenSSH |
| **Backup** — Backup target *(A15)* | 1 | 8 | 32 GiB | 100 GB OS + **21 TB** (nearline/SATA acceptable) | RHEL 9.x · Windows Server 2022 · or the bank's backup appliance | Not applicable | The bank's enterprise backup product (Veeam, Commvault, NetBackup) with database and object-store agents |

#### 3.1.2 Justification

| Server type | Why this quantity | Why this core, memory and storage specification |
|---|---|---|
| **Orchestration** — control plane *(A1)* | etcd is a Raft quorum. Three is the minimum that survives the loss of one node; two cannot form a majority after a failure, and one is not highly available | 8 cores serves the API traffic for up to 220 pod objects plus autoscaler reconciliation every 15 seconds. etcd is latency-bound rather than core-bound, which is why the etcd volume is specified as NVMe and given its own disk — write latency there determines cluster responsiveness |
| **Application** — worker node *(A2)* | Peak demand is ~53 cores and the burst ceiling ~86 (section 2.2). Seven nodes give 112 raw cores, ~98 schedulable after OS and kubelet overhead; losing one node still leaves ~84, well above peak. **Five is the minimum for N+1 at peak; the sixth and seventh cover the burst ceiling with N+1 intact.** At the 500 ms budget this row read eight — see section 2.3 | 16 cores and 64 GiB is a 4:1 memory-to-core ratio, matching the services' own 500 millicore / 1 GiB request ratio — so neither CPU nor memory strands first and the scheduler packs cleanly. 300 GB of ephemeral storage holds 23 service images plus JVM working space and log buffers |
| **Orchestration** — infrastructure node *(A3)* | Two so ingress survives one node loss. Separated from the application pool so a scale-out event cannot evict ingress, and so ingress CPU does not compete with application containers | Ingress at 600 RPS with TLS termination is roughly 2 cores; the remainder carries observability agents, the certificate controller and other cluster add-ons |
| **Routing** — load balancer *(A4)* | A single load balancer is a single point of failure for all ingress. Two in active/passive behind a virtual IP | 4 cores and 8 GiB covers TLS termination and health checking at 600 RPS with substantial margin. This tier is network-bound, not compute-bound |
| **Database** — operational primary *(A5)* | One primary. The 23 services hold per-service schemas on a single instance; splitting them further would multiply licence cost without reducing load | **12 cores** because the workload is 90 write RPS across 23 schemas with up to ~1,350 pooled connections — connection handling and log flush dominate, not query CPU, and the 2-second write budget leaves real headroom on the log flush. **128 GiB** buffers a 1.5 TB store whose hot fraction is the current month; memory follows data volume, not the latency target, so it does not move. **Separate log volume** because log write latency is still what binds write p95, even at the looser budget |
| **Database** — operational secondary *(A6)* | One synchronous replica. Required for the 99.5% availability target and automatic failover; it is also the backup source, so nightly backups do not load the primary | Must match the primary exactly — a secondary that cannot carry the primary's workload is not a failover target |
| **Database** — warehouse primary *(A7)* | One primary. This is the product's own warehouse, generated from its table catalogue; it is not the bank's enterprise warehouse and cannot be consolidated into it | **24 cores** because drill-down level 4 runs columnstore scans over 1.5–3.5 billion posting rows, and batch-mode execution is CPU-bound and parallelises across cores — this is the one place where core count directly buys query latency, so the 5-second drill budget takes it down from the 32 cores a 2-second budget needs. **256 GiB** for the columnstore object pool and aggregate caching; memory follows the 6 TB of data, not the budget. **6 TB** holds 24 months of postings under columnstore compression plus ~650,000 snapshot rows per day |
| **Database** — warehouse readable secondary *(A8)* | One, and **not optional**. The analytics read pool is pointed at it; it serves the ~510 read RPS that is 85% of the workload. Removing it does not save a server — it moves that load onto the primary and invalidates the p95 target | Matches the primary because it carries the heavier of the two workloads. Also provides the failover target for the warehouse |
| **Messaging** — RabbitMQ *(A9)* | Three. Quorum queues replicate by majority, so three tolerates the loss of one node. Two provides no quorum after a failure | 4 cores and 16 GiB: the broker is I/O-bound, not CPU-bound, at the ~90 events per second the write path generates. **200 GB** exists to hold a backlog if a consumer group stalls — the queue depth is the buffer that protects the database from write spikes |
| **Cache** — Redis *(A10)* | Three. Sentinel requires three for quorum-based failover election | **32 GiB** sized from the cached working set: entitlement and scope snapshots for 10,000 users, drill-down levels 1–3 across ~100,000 customers, the reference configuration and the metric catalogue — at the >80% hit rate the architecture depends on. 4 cores because Redis is single-threaded for command execution; cores beyond that serve I/O threads and persistence |
| **Storage** — object store *(A11)* | Four. Erasure coding needs a minimum of four nodes to tolerate one node loss while retaining usable parity | 3 TB each gives 12 TB raw and ~6 TB usable, covering ingested documents and their extraction artefacts with first-year growth. Compute is minimal; this tier is capacity and throughput |
| **Monitoring** — observability *(A12)* | Three: a Prometheus high-availability pair plus the collector, and a quorum for the log store | 8 cores and 32 GiB handles metric cardinality across 23 services at up to 220 instances each exposing per-endpoint histograms. 2 TB is retention, set by the bank's own policy |
| **Repository** — container registry *(A13)* | Two. **Image pull is on the critical path for every scale-out event and every pod restart** — a registry outage stops the cluster from recovering | 2 TB holds 23 service images plus the web and Teams components across retained tags and both environments |
| **Management** — bastion *(A14)* | One. It is not in the serving path, and is rebuilt from configuration if lost | Small by design: it runs `kubectl` and `helm`, not workloads |
| **Backup** — backup target *(A15)* | One, unless the bank's existing backup estate absorbs it | 20 TB covers a full backup plus differentials across the ~28 TB of database and object storage in this environment, at the retention the bank specifies |

**Option A DC totals**

| Scope | Servers | Cores | RAM | Storage (usable) |
|---|---|---|---|---|
| Kubernetes and database tier (A1–A8) | 18 | 252 | 1,904 GiB | ~41 TB |
| Supporting services (A9–A14) | 16 | 76 | 352 GiB | ~25 TB |
| Backup target (A15) | 1 | 8 | 32 GiB | ~31 TB |
| **Total, procuring everything** | **35** | **336** | **~2,288 GiB** | **~97 TB** |
| *Of which dedicated to this product and unavoidable (A2, A5–A8)* | *11* | *204* | *1,728 GiB* | *~39 TB* |

The worker pool (A2) holds the full burst ceiling with headroom for the ingress controller, observability
agents and cluster add-ons, and continues to serve expected peak load with **one node unavailable**,
whether through failure or rolling maintenance.

### 3.2 Option B — Without Kubernetes

> **What this option requires from us.** The product is delivered today as container images with a Helm
> chart. The 23 services are Spring Boot executable JARs on Java 21, so they run on a virtual machine
> with no container runtime and **no change to application code**. What does not exist today, and would
> be built and supported as part of this engagement, is the deployment layer Kubernetes currently
> provides: process supervision units, configuration and secret delivery to each host, service discovery
> and health-based routing, and a rolling deployment procedure across hosts. Section 3.4 states what is
> lost operationally. This option should be costed with that work included.

#### 3.2.1 Procurement specification

| Server type | Qty | No. of cores (each) | RAM (each) | Hard drive / volumes (each) | OS supported | Database supported | Software required |
|---|---|---|---|---|---|---|---|
| **Application** — JVM application server *(B1)* | 9 | 16 | 64 GiB | 200 GB OS (SSD) + 200 GB application binaries & logs (SSD) | RHEL 9.x · Oracle Linux 9.x · SLES 15 SP5 · Ubuntu 22.04/24.04 LTS · Windows Server 2022 | Not applicable | **Eclipse Temurin JDK 21**, systemd (or the bank's process supervisor), configuration management agent (Ansible, Puppet or Chef), OpenTelemetry Java agent, log shipper |
| **Routing** — Internal service router *(B2)* | 2 | 4 | 16 GiB | 100 GB OS (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | HAProxy 2.8+ or NGINX 1.24+ with **health-checked backend pools, one per service** — this replaces Kubernetes service discovery. Consul or Eureka as an alternative where the bank already runs one |
| **Routing** — Load balancer / reverse proxy *(B3)* | 2 | 4 | 8 GiB | 100 GB OS (SSD) | RHEL 9.x · Ubuntu 22.04 LTS · or the bank's appliance (F5, Citrix ADC, A10) | Not applicable | HAProxy 2.8+ or NGINX 1.24+, active/passive with a virtual IP, TLS 1.2/1.3 termination, WAF module where bank policy requires it |
| **Database** — Operational, primary *(B4)* | 1 | 12 | 128 GiB | 200 GB OS + **1.5 TB data** + 500 GB transaction log + 500 GB tempdb — NVMe/SSD, **separate volumes** | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | Database engine, Always On Availability Groups, SQL Server Agent, Flyway 10+ client, enterprise backup agent |
| **Database** — Operational, synchronous secondary *(B5)* | 1 | 12 | 128 GiB | Identical to B4 | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | As B4, as a synchronous AG replica and backup source |
| **Database** — Data warehouse, primary *(B6)* | 1 | 34 | 512 GiB | 200 GB OS + **11 TB data** + 2 TB redo log + 2 TB temp tablespace — NVMe, **separate volumes** | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **SQL Server 2022 Enterprise** — columnstore, partitioning and readable secondaries required · PostgreSQL 16 alternative | Database engine, Always On AG, SQL Server Agent, enterprise backup agent |
| **Database** — Data warehouse, readable secondary *(B7)* | 1 | 34 | 512 GiB | Identical to B6 | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | As B6, as a **readable** synchronous AG replica |
| **Messaging** — RabbitMQ broker *(B8)* | 3 | 4 | 16 GiB | 100 GB OS + 200 GB message store (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | RabbitMQ 3.13+ with quorum queues, Erlang/OTP 26, management and Prometheus plug-ins. **Consumer counts are fixed at deployment time** — there is no queue-depth autoscaling in this option |
| **Cache** — Redis *(B9)* | 3 | 4 | 32 GiB | 100 GB OS + 100 GB (SSD, persistence snapshots only) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | Redis 7.2+ in Sentinel or cluster mode, eviction policy `allkeys-lru`. Cache only — never a system of record |
| **Storage** — S3-compatible object store *(B10)* | 4 | 4 | 16 GiB | 100 GB OS + **3 TB data each** (12 TB raw → ~6 TB usable erasure-coded) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | MinIO (current LTS), or the bank's existing S3-compatible store, with Signature V4, versioning and server-side encryption |
| **Monitoring** — Observability stack *(B11)* | 3 | 8 | 32 GiB | 100 GB OS + 2 TB metric/trace/log retention (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | Prometheus 2.5x, Grafana 11+, OpenTelemetry Collector, Loki or the bank's log platform. **Process-level monitoring and alerting carry more weight here**, since nothing reschedules a failed instance |
| **Repository** — Artifact repository *(B12)* | 1 | 4 | 16 GiB | 100 GB OS + 2 TB artefact store (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | JFrog Artifactory or Nexus, holding **JAR artefacts** rather than container images |
| **Management** — Configuration management / bastion *(B13)* | 1 | 8 | 16 GiB | 200 GB OS + 100 GB (SSD) | RHEL 9.x · Ubuntu 22.04 LTS | Not applicable | Ansible (or Puppet/Chef) control node, the bank's secret-store CLI, Flyway 10+, OpenSSH |
| **Backup** — Backup target *(B14)* | 1 | 8 | 32 GiB | 100 GB OS + **21 TB** (nearline/SATA acceptable) | RHEL 9.x · Windows Server 2022 · or the bank's backup appliance | Not applicable | The bank's enterprise backup product (Veeam, Commvault, NetBackup) with database and object-store agents |

#### 3.2.2 Justification

The database, messaging, cache, storage and monitoring rows carry the same justification as Option A
(section 3.1.2) — those tiers are unaffected by how the application is deployed. Four rows differ, and
those differences are the entire cost of not running Kubernetes.

| Server type | Why this quantity | Why this specification |
|---|---|---|
| **Application** — JVM application server *(B1)* | **Nine rather than Option A's seven**, for three compounding reasons. **No autoscaling:** capacity is provisioned for peak and holds there all day, where Option A floats between a 26-core floor and a 53-core peak. This is the reason the latency budget bites hardest: Option A banks the looser budget as a higher CPU target it uses only when busy, while Option B pays for peak around the clock. **No bin-packing:** a scheduler places instances wherever capacity exists; fixed allocation to named hosts cannot, so every host carries unusable slack. **Coarser failure granularity:** losing one of nine hosts removes 11% of *every* service on it, with nothing to replace those instances elsewhere, so the pool must absorb that loss while still serving peak | 16 cores and 64 GiB for the same 4:1 reason as Option A — it matches the services' own 500 millicore / 1 GiB ratio. 200 GB of local storage holds the JAR artefacts for all 23 services, JVM working space and local log buffers prior to shipping |
| **Routing** — Internal service router *(B2)* | **Two, and this server type does not exist in Option A at all.** Kubernetes provides service discovery and health-based routing through cluster DNS and its Service objects. Without it, every internal hop between the 23 services must traverse an explicit routing tier, which therefore has to be highly available | 4 cores and 16 GiB carries 22 health-checked backend pools. The memory is above the external load balancer's 8 GiB because the backend tables, health state and connection pools for all internal traffic live here |
| **Repository** — Artifact repository *(B12)* | **One rather than Option A's two.** A JAR is pulled at deployment time only; a container image is pulled on **every** pod restart and every scale-out event. The registry is on Option A's critical recovery path and must be redundant; the artifact repository is not | 2 TB holds the JAR artefacts for 23 services across retained versions — materially smaller than a container image store, since there are no base-image layers |
| **Management** — Configuration management / bastion *(B13)* | One, as in Option A | **8 cores rather than 4**, because deployment orchestration runs here rather than in the cluster. An Ansible run forks across 9 hosts × 23 services during a rolling release, and that fan-out is CPU-bound on the control node |

**Option B DC totals**

| Scope | Servers | Cores | RAM | Storage (usable) |
|---|---|---|---|---|
| Application and database tier (B1–B7) | 17 | 252 | 1,904 GiB | ~40 TB |
| Supporting services (B8–B13) | 15 | 76 | 336 GiB | ~23 TB |
| Backup target (B14) | 1 | 8 | 32 GiB | ~31 TB |
| **Total, procuring everything** | **33** | **336** | **~2,272 GiB** | **~94 TB** |
| *Of which dedicated to this product and unavoidable (B1, B4–B7)* | *13* | *236* | *1,856 GiB* | *~39 TB* |

**Why ten application servers rather than eight workers.** Three effects compound, and together they
account for the whole difference:

- **No autoscaling.** Capacity is provisioned for peak and stays there. Option A autoscales from a
  26-core floor to meet a 68-core peak; Option B holds 68 cores' worth of running processes all day.
- **No bin-packing.** A scheduler places instances wherever capacity exists. Fixed allocation to named
  hosts cannot, so each host carries unusable slack.
- **Coarser failure granularity.** Losing one of ten application servers removes 10% of *every* service
  running on it, with no scheduler to replace those instances elsewhere. The pool must absorb that loss
  while still serving peak.

### 3.3 Segregation — what the bank may already have

Much of both lists is general platform capability rather than anything specific to this product. A bank
running 100,000 wholesale customers typically operates most of it already. This section separates each
list into **new, dedicated capacity** and **capability satisfiable from the bank's existing estate**,
with the verification each reuse depends on.

#### Category A — Dedicated capacity, always procured

New load cannot be served by an existing server simply because that server exists.

| What | Option A | Option B | Why there is no substitute |
|---|---|---|---|
| Application compute | A2 — 7 nodes, 112 cores | B1 — 9 servers, 144 cores | This is the application itself. Where the bank has a Kubernetes platform, Option A's nodes join that cluster rather than forming a new one — but the capacity is new either way |
| Operational database pair | A5–A6 — 2 servers, 24 cores | B4–B5 — 2 servers, 24 cores | 23 per-service transactional schemas with a synchronous replica. The bank has SQL Server; it does not have these instances |
| Data warehouse pair | A7–A8 — 2 servers, 68 cores | B6–B7 — 2 servers, 68 cores | Generated from the product's own table catalogue with SCD Type 2 dimensions, columnstore facts and its own metric definitions. **The bank's existing enterprise data warehouse is not a substitute** — it does not hold this model, and pointing the product at it would mean rebuilding the semantic layer |
| **Category A total (DC)** | **11 servers, 204 cores** | **13 servers, 236 cores** | |

#### Category B — Reuse from the bank's existing estate where present

| Component | Typical in a bank this size | What the product needs from it | Verify before assuming reuse | DC saving, Option A | DC saving, Option B |
|---|---|---|---|---|---|
| **Kubernetes platform** (A1, A3) | Often — OpenShift, Tanzu, Rancher or RKE2 | A dedicated project/namespace set, RBAC without cluster-admin, a CNI enforcing NetworkPolicy, a CSI storage class, three failure domains for topology spread | Version 1.29 or later; HPA and PodDisruptionBudget admitted; image pull from the bank's registry; quota sufficient for the burst ceiling | 5 servers, 40 cores | **Not applicable — a bank with this does not need Option B** |
| **Internal service routing** (B2) | Sometimes — Consul, Eureka, or an internal LB tier | Health-checked backend pools, one per service, or a service registry the gateway can resolve against | Health-check granularity per backend; that adding 22 pools is within the appliance's configuration limits | Not applicable — Kubernetes provides this | 2 servers, 8 cores |
| **Load balancer / ingress** (A4, B3) | Almost always — F5, Citrix ADC, A10 | One virtual IP, TLS termination or passthrough, health checks, WAF where policy requires | That a new VIP and certificate can be issued inside the project timeline — usually a lead-time risk, not a capacity one | 2 servers, 8 cores | 2 servers, 8 cores |
| **Message broker** (A9, B8) | Sometimes — **but usually Kafka, not RabbitMQ** | AMQP 0-9-1 with **quorum queues** and a dedicated virtual host | RabbitMQ 3.13+ with quorum queues enabled, and vhost isolation. **Kafka is not a drop-in replacement** — the outbox publisher and every consumer speak AMQP, so a Kafka-only estate is a product change, not a configuration change. Raise it early | 3 servers, 12 cores | 3 servers, 12 cores |
| **Cache** (A10, B9) | Often — Redis or Valkey | Redis 7.2+, 32 GiB usable, eviction policy `allkeys-lru`, Sentinel or cluster mode | A dedicated instance or logical database, not one shared with anything treating Redis as a system of record; memory headroom at peak | 3 servers, 12 cores | 3 servers, 12 cores |
| **Object store** (A11, B10) | Often — NetApp StorageGRID, Dell ECS, Ceph RGW, Isilon with S3 | S3-compatible API, one bucket, versioning, server-side encryption, ~6 TB usable | Signature V4 support, bucket policy and lifecycle rules, encryption keys in the bank's KMS | 4 servers, 16 cores | 4 servers, 16 cores |
| **Observability platform** (A12, B11) | Almost always — Dynatrace, AppDynamics, Splunk, Elastic, Grafana | OTLP ingest for traces and metrics, a Prometheus scrape or remote-write target, a log sink | An OTLP endpoint exists (not only a proprietary agent); metric cardinality budget accommodates per-service labels; trace retention meets the bank's standard | 3 servers, 24 cores | 3 servers, 24 cores |
| **Artifact repository / registry** (A13, B12) | Almost always — JFrog Artifactory, Nexus, Harbor | Option A: a container project with signing and scanning. Option B: a Maven/generic repository for JAR artefacts | Scanning does not block deployment on findings in base images the bank itself maintains; replication to DR is configurable | 2 servers, 8 cores | 1 server, 4 cores |
| **Bastion / configuration management** (A14, B13) | Often — a jump host or CI runner; a configuration management estate | Option A: `kubectl`, Helm, Flyway. Option B: an **Ansible/Puppet/Chef control node** that will own deployment orchestration | Outbound access to hosts, the cluster API and the repository; toolchain installable under the bank's endpoint policy | 1 server, 4 cores | 1 server, 8 cores |
| **Enterprise backup** (A15, B14) | Always — Veeam, Commvault, NetBackup | Database agents, object-store backup, retention per policy | Agent support for the chosen database edition and version; ~20 TB of additional protected capacity within licensed limits | 1 server, 8 cores | 1 server, 8 cores |
| **Secret store** | Always — CyberArk or HashiCorp Vault | **One database secret per service**, plus a messaging secret and a drill-token secret, delivered at run time | Option A: a Kubernetes integration (CSI driver or external-secrets). Option B: a host-level agent that can render secrets to files the supervisor reads. No server is listed either way | — | — |
| **Identity** | Always — Microsoft Entra ID | OAuth2 resource-server validation; the product issues no credentials of its own | An app registration with the required scopes, and outbound egress per section 9 | — | — |

#### Category C — Decided, not discovered

| Item | Question to settle | Consequence |
|---|---|---|
| Deployment option | Kubernetes or not? | Section 3.4. Option B carries a delivery scope addition and loses four operational capabilities |
| Model serving (section 8) | External approved endpoint, or on-premises inference? | Option B there adds 2 GPU servers per site: 64 cores, 512 GiB, 4 TB, 4 GPUs |
| Performance environment (section 6.3) | Size UAT to production shape, or build a fourth environment? | Adds 48 cores / 192 GiB / 2 TB to UAT, or a whole environment |
| Database consolidation | Dedicated instances, or the operational schemas and the warehouse sharing one Oracle instance? | Saves 2 DC servers and **adds 20 cores each of Partitioning and Active Data Guard**, because Oracle options are licensed on every core of the database they run on — section 3.6. **Not a saving on Oracle** |
| Sharing the bank's existing database estate | Dedicated servers, or schemas on an instance the bank already runs? | Removes servers that already exist, but the ~1,350-connection budget and temp tablespace contention must be assessed against that instance's current workload first. **Do not assume this saving** |

### 3.4 Comparing the two options

| | Option A — Kubernetes | Option B — No Kubernetes |
|---|---|---|
| DC servers, procuring everything | 35 | **33** |
| **DC cores** | **336** | **336** |
| DC memory | ~2,288 GiB | ~2,272 GiB |
| DC storage | ~97 TB | ~94 TB |
| DC dedicated capacity (Category A) | 11 servers, **204 cores** | 13 servers, **236 cores** |
| Scaling on demand | Automatic, CPU-driven (services) and queue-depth-driven (consumers) | **None.** Capacity is fixed at deployment |
| Recovery from a failed instance | Rescheduled automatically onto healthy capacity | Process supervisor restarts it in place. **A failed host's share of every service stays down** until the host returns |
| Deployment | Rolling, with readiness gating, by Helm chart | Scripted across hosts, with routing drained and restored per host |
| Availability guarantees | PodDisruptionBudget and topology spread across failure domains | Manual placement discipline, enforced by configuration management |
| Resource isolation between services | cgroup limits per container | **Shared JVM hosts.** One service's memory growth can affect its co-tenants |
| Status in the product today | Ships. Chart linted and rendered in CI on every push | Deployment automation to be built and supported |

**Option B costs the same and does less.** Two servers fewer and 16 GiB of memory, for the same 336
cores, in exchange for giving up autoscaling, automatic rescheduling, rolling deployment and per-service
resource isolation — and taking on a deployment layer that does not exist in the product today.

The relaxed latency budget widens this gap rather than closing it. Option A converts a looser budget into
a higher autoscaler CPU target, which it draws on only at peak; Option B has to hold peak capacity all
day whatever the budget says. At the dedicated-capacity line that shows as **204 cores against 236**.

Option B is the right answer in exactly one circumstance: **the bank has no container platform, no
operational experience running one, and no intention of acquiring either.** In that case the honest
comparison is not against Option A's bill but against the cost and risk of the bank standing up a
Kubernetes practice for a single application. That is a reasonable reason to choose Option B. Saving
money is not.

### 3.5 Database tier — three points that govern procurement

These apply identically to both options.

**The warehouse readable secondary is not optional.** The analytics read pool is pointed at it and the
85% read share of the workload depends on it. Removing it does not save a server; it moves 510 reads per
second onto the warehouse primary and invalidates the p95 target.

**Three Oracle options are required, and together they are the largest licence decision in this bill.**
The product needs table partitioning at these volumes, a readable standby to serve the read path, and —
for the drill scans — a columnar store. On Oracle those are **Partitioning**, **Active Data Guard** and
**Database In-Memory**, each separately licensed on top of Enterprise Edition. The sizing above assumes
In-Memory is *not* taken, which is why the warehouse carries 34 cores rather than 24; taking it would
reduce the cores and add a licence. Price both ways before choosing, and note the 0.5 core factor on
x86 when comparing per-core costs.

**The warehouse is 11 TB rather than 6 for the same data.** Without a columnar store the postings fact
compresses far less, and the figure moves with that choice. Section 7 states the growth rate on the same
basis.

**Oracle is not a supported engine for this product today** — see `architecture/oracle-support-gap.md`.
The operational databases are parameterized across SQL Server, PostgreSQL and H2; the warehouse is
SQL Server alone. The sizing in this document is what Oracle would need; it is not a statement that the
product runs on it. Nothing here should be quoted as deliverable until that work is estimated and
scheduled.

**The connection budget is bounded by design.** At full scale-out the worst case is approximately 1,350
application connections to the operational database — connection pools cap per instance, and the
instance count is capped by the autoscaler (Option A) or fixed at deployment (Option B). This is
comfortable for the servers as specified, but it is the figure to check if the bank intends to
consolidate these databases onto an existing shared instance.

**SQL Server 2022 Enterprise remains the reference platform** and is what the product is built and
tested against. It sizes smaller — 24 warehouse cores and 6 TB rather than 34 and 11 TB, and 664 total
cores rather than 694 — because partitioning, columnstore and readable secondaries are all included in
Enterprise rather than licensed separately. PostgreSQL 16 is supported for the **operational databases
only**, with Patroni or repmgr plus pgBackRest in place of Always On; it has no columnar store in core
and therefore sizes as Oracle does.

### 3.6 Why the warehouse is a separate instance, and not one bigger one

Four database servers invite an obvious question: can the operational schemas and the warehouse share
one Oracle instance, or one pair of them? Technically yes. **On Oracle it is the more expensive answer,
which is the opposite of what it looks like.**

Oracle's extra-cost options must be licensed on **every core of the database they run on**, not only on
the part of the workload that uses them. The operational schemas need neither Partitioning nor Active
Data Guard; the warehouse needs both. Separating them confines those options to the servers that use
them.

| Production DC database tier | Servers | Enterprise cores | Partitioning | Active Data Guard |
|---|---|---|---|---|
| **Separate instances — as sized** | 4 | 76 | **56** | **56** |
| One consolidated pair | 2 | 76 | **76** | **76** |

Consolidating saves **two server chassis** and adds **20 cores each of Partitioning and Active Data
Guard** — ten processor licences apiece at the x86 core factor, and ten more again if Database
In-Memory is taken. Enterprise cores do not move, because the work does not disappear when the servers
merge: each node then carries both jobs, at 38 cores rather than 10 and 28.

On the SQL Server reference platform the same consolidation is licence-neutral (56 Enterprise cores
either way), because partitioning and columnstore are included. The conclusion here is Oracle's, not a
general one.

**Three technical reasons hold whatever the licensing says:**

- **One buffer cache.** A drill-down scanning the postings fact evicts the operational working set, so
  write latency degrades according to what somebody happened to click. If Database In-Memory is taken,
  its column store also sits in the SGA and competes with that same cache directly.
- **One temp tablespace.** The warehouse's large sorts and hash joins share it with thousands of small
  operational transactions.
- **One failure and recovery domain.** A Data Guard failover takes both down together, and the
  operational database's 4-hour recovery time becomes hostage to an 11 TB warehouse restore beside it.

**If server count is the binding constraint**, one container database with two pluggable databases gives
the two-chassis saving with clean logical separation — separate backups, refresh schedules and
boundaries — and needs no Multitenant licence at two PDBs. It does not change the option licensing,
which applies at container level.

**Caveat.** Oracle licensing is contract-specific. If the bank's agreement already covers these options
estate-wide, the 20-core delta disappears and the decision becomes purely the three technical points
above. Confirm with the bank's licensing partner before settling it either way.

---

## 4. Production — Disaster Recovery (DR)

The recovery site is a **warm standby**: the data tier replicates continuously, the application tier runs
at reduced capacity, and failover brings it to full. This is what meets a 15-minute recovery point and a
4-hour recovery time.

### 4.1 Option A — With Kubernetes

| Server type | Qty | No. of cores (each) | RAM (each) | Hard drive / volumes (each) | OS supported | Database supported | Software required | Justification |
|---|---|---|---|---|---|---|---|---|
| **Orchestration** — Kubernetes control plane | 3 | 8 | 32 GiB | 200 GB OS + 100 GB etcd (NVMe) | As DC A1 | Not applicable | As DC A1 | Raft quorum; three is the HA minimum regardless of site size |
| **Application** — Kubernetes worker node | 5 | 16 | 64 GiB | 200 GB OS + 300 GB (SSD) | As DC A2 | Not applicable | As DC A2 | Five rather than seven: the site carries expected peak (~53 cores) after failover with N+1, but not the burst headroom the primary holds. Scaling out is part of the 4-hour recovery procedure |
| **Orchestration** — Kubernetes infrastructure node | 2 | 8 | 32 GiB | 200 GB OS + 200 GB (SSD) | As DC A3 | Not applicable | As DC A3 | Ingress must survive one node loss at the recovery site too |
| **Routing** — Load balancer / reverse proxy | 2 | 4 | 8 GiB | 100 GB OS | As DC A4 | Not applicable | As DC A4 | Active/passive; failover cannot depend on a single ingress node |
| **Database** — Operational, asynchronous replica | 1 | 12 | 128 GiB | As DC A5 | As DC A5 | **Oracle Database 19c or 23ai EE + Partitioning** | As DC A5, as an asynchronous AG replica | Must match the primary — it becomes the primary on failover. Asynchronous because the 15-minute recovery point does not require synchronous commit across sites |
| **Database** — Data warehouse, asynchronous replica | 1 | 34 | 512 GiB | As DC A7 | As DC A7 | **Oracle Database 19c or 23ai EE + Partitioning** | As DC A7, as an asynchronous AG replica | Must match the primary. One replica rather than the primary's pair: after failover it serves both the write and the read path until the primary site returns |
| **Messaging** — RabbitMQ broker | 3 | 4 | 16 GiB | 100 GB OS + 200 GB (SSD) | As DC A9 | Not applicable | As DC A9 | Quorum minimum. Queues are **not** replicated — they are rebuilt from the service outboxes, which are in the replicated database |
| **Cache** — Redis | 3 | 4 | 32 GiB | 100 GB OS + 100 GB (SSD) | As DC A10 | Not applicable | As DC A10 | Sentinel quorum minimum. Not replicated; holds no system of record and rebuilds from source on first read |
| **Storage** — S3-compatible object store | 4 | 4 | 16 GiB | 100 GB OS + 3 TB each | As DC A11 | Not applicable | As DC A11, with cross-site replication from the primary store | Erasure-coding minimum. Documents cannot be rebuilt from anywhere else, so this tier **is** replicated |
| **Monitoring** — Observability stack | 2 | 8 | 32 GiB | 100 GB OS + 2 TB (SSD) | As DC A12 | Not applicable | As DC A12 | Two rather than three: the recovery site needs working monitoring, not a quorum-backed long-retention store |
| **Repository** — Container registry (replica) | 1 | 4 | 16 GiB | 100 GB OS + 2 TB (SSD) | As DC A13 | Not applicable | As DC A13, replicating from the primary registry | One is sufficient, but it **must be present** — without a local registry the recovery site cannot start pods |
| **Management** — Bastion / deployment host | 1 | 4 | 16 GiB | 200 GB OS | As DC A14 | Not applicable | As DC A14 | Not in the serving path |

| Scope | Servers | Cores | RAM | Storage (usable) |
|---|---|---|---|---|
| **Option A DR total, procuring everything** | **28** | **238** | **~1,440 GiB** | **~43 TB** |
| *Of which dedicated and unavoidable (A2, A5–A6)* | *7* | *126* | *960 GiB* | *~20 TB* |

### 4.2 Option B — Without Kubernetes

| Server type | Qty | No. of cores (each) | RAM (each) | Hard drive / volumes (each) | OS supported | Database supported | Software required | Justification |
|---|---|---|---|---|---|---|---|---|
| **Application** — JVM application server | 7 | 16 | 64 GiB | 200 GB OS + 200 GB app & logs (SSD) | As DC B1 | Not applicable | As DC B1 | Seven rather than the primary site's nine: carries expected peak after failover without burst headroom. Two more than Option A's five for the same no-bin-packing and coarse-failure reasons as section 3.2.2 |
| **Routing** — Internal service router | 2 | 4 | 16 GiB | 100 GB OS | As DC B2 | Not applicable | As DC B2 | Every internal hop traverses it, so it cannot be a single point of failure at the recovery site either |
| **Routing** — Load balancer / reverse proxy | 2 | 4 | 8 GiB | 100 GB OS | As DC B3 | Not applicable | As DC B3 | Active/passive; failover cannot depend on a single ingress node |
| **Database** — Operational, asynchronous replica | 1 | 12 | 128 GiB | As DC B4 | As DC B4 | **Oracle Database 19c or 23ai EE + Partitioning** | As DC B4, asynchronous replica | Must match the primary — it becomes the primary on failover |
| **Database** — Data warehouse, asynchronous replica | 1 | 34 | 512 GiB | As DC B6 | As DC B6 | **Oracle Database 19c or 23ai EE + Partitioning** | As DC B6, asynchronous replica | Must match the primary; serves both read and write paths after failover |
| **Messaging** — RabbitMQ broker | 3 | 4 | 16 GiB | 100 GB OS + 200 GB (SSD) | As DC B8 | Not applicable | As DC B8 | Quorum minimum. Queues rebuilt from the replicated outboxes |
| **Cache** — Redis | 3 | 4 | 32 GiB | 100 GB OS + 100 GB (SSD) | As DC B9 | Not applicable | As DC B9 | Sentinel quorum minimum. Not replicated; rebuilds from source |
| **Storage** — S3-compatible object store | 4 | 4 | 16 GiB | 100 GB OS + 3 TB each | As DC B10 | Not applicable | As DC B10, with cross-site replication | Erasure-coding minimum. Documents are replicated because they cannot be rebuilt |
| **Monitoring** — Observability stack | 2 | 8 | 32 GiB | 100 GB OS + 2 TB (SSD) | As DC B11 | Not applicable | As DC B11 | Working monitoring rather than a quorum-backed retention store |
| **Repository** — Artifact repository (replica) | 1 | 4 | 16 GiB | 100 GB OS + 2 TB (SSD) | As DC B12 | Not applicable | As DC B12, replicating from the primary | Needed locally so the recovery site can deploy without the primary site |
| **Management** — Configuration management / bastion | 1 | 8 | 16 GiB | 200 GB OS + 100 GB | As DC B13 | Not applicable | As DC B13 | **Must be present and current.** In this option the recovery site cannot be deployed or scaled without a working control node — there is no cluster to accept a manifest |

| Scope | Servers | Cores | RAM | Storage (usable) |
|---|---|---|---|---|
| **Option B DR total, procuring everything** | **27** | **242** | **~1,440 GiB** | **~42 TB** |
| *Of which dedicated and unavoidable (B1, B4–B5)* | *9* | *158* | *1,088 GiB* | *~21 TB* |

Seven application servers rather than nine: the recovery site carries expected peak after failover
without the burst headroom the primary site holds. Option A's five workers achieve the same because the
scheduler packs them; Option B needs two more for the reasons in section 3.2.

### 4.3 What is not duplicated, and why

| Component | DR position |
|---|---|
| **Message queues** | Not replicated. Queues are rebuilt and messages republished from each service's transactional outbox, which lives in the database and therefore *is* replicated. Consumers are idempotent, so redelivery is safe |
| **Cache** | Not replicated. The cache holds no system of record and is rebuilt from source on first read |
| **Backup target** | The DC backup target replicates to the bank's existing offsite or tape estate. A second 20 TB target is not listed; confirm against the bank's own backup topology |
| **Application capacity** | Reduced, not absent. A bank requiring active/active must size DR identically to DC in either option |

### 4.4 Inter-site requirement

| Item | Requirement |
|---|---|
| Bandwidth, DC ↔ DR | 1 Gbps minimum dedicated; 10 Gbps recommended for the initial warehouse seed |
| Latency, DC ↔ DR | < 10 ms round trip for asynchronous replication at a 15-minute recovery point |
| Replication mode | Asynchronous (both databases), plus object-store cross-site replication |

---

## 5. UAT Environment

UAT runs a single instance per service against a two-node database cluster. The second database node is
deliberately present: it is a **readable secondary**, and without it the 85% read path — the most
load-bearing part of the architecture — is never exercised before production.

### 5.1 Option A — With Kubernetes

| Server type | Qty | No. of cores (each) | RAM (each) | Hard drive / volumes (each) | OS supported | Database supported | Software required | Justification |
|---|---|---|---|---|---|---|---|---|
| **Orchestration** — Kubernetes control plane | 3 | 4 | 16 GiB | 200 GB OS + 100 GB etcd (SSD) | As DC A1 | Not applicable | As DC A1 | Three for quorum, but **half the cores of production** — UAT reconciles a fraction of the object count and has no autoscaling churn |
| **Application** — Kubernetes worker node | 3 | 16 | 64 GiB | 200 GB OS + 300 GB (SSD) | As DC A2 | Not applicable | As DC A2 | Three holds one instance of each of the 23 services with room to test a rolling deployment. Same node size as production so container behaviour is representative — see 5.3 before treating this as a load-test platform |
| **Routing** — Load balancer / reverse proxy | 1 | 4 | 8 GiB | 100 GB OS | As DC A4 | Not applicable | As DC A4, single node | Single node: an ingress outage in UAT interrupts testing, not service |
| **Database** — Primary (operational **and** warehouse) | 1 | 16 | 128 GiB | 200 GB OS + **2 TB data** + 500 GB log + 500 GB tempdb | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | Database engine, Always On AG, SQL Server Agent, Flyway 10+ | Both databases on one instance, since UAT load does not warrant separating them. 2 TB carries a representative data slice, not full history |
| **Database** — Readable secondary | 1 | 16 | 128 GiB | Identical to the primary | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | As the primary, as a **readable** AG replica | **Do not remove this to save a server.** Without it the 85% read path — the most load-bearing part of the architecture — is never exercised before production, and AG failover is never tested |
| **Messaging** — RabbitMQ broker | 1 | 4 | 16 GiB | 100 GB OS + 200 GB (SSD) | As DC A9 | Not applicable | As DC A9, single node | Single node: quorum behaviour is a production concern; message contracts are what UAT validates |
| **Cache** — Redis | 1 | 4 | 16 GiB | 100 GB OS + 100 GB (SSD) | As DC A10 | Not applicable | Redis 7.2+, single node | Single node, 16 GiB: UAT's cached working set is a fraction of production's |
| **Storage** — S3-compatible object store | 1 | 4 | 16 GiB | 100 GB OS + 1 TB | As DC A11 | Not applicable | MinIO single node, or a UAT bucket on the bank's existing store | Single node; 1 TB covers test document volumes |
| **Monitoring** — Observability stack | 1 | 8 | 32 GiB | 100 GB OS + 1 TB (SSD) | As DC A12 | Not applicable | As DC A12, single node | Needed in UAT so performance regressions are visible before production, with shorter retention |
| **Management** — Bastion / deployment host | 1 | 4 | 8 GiB | 200 GB OS | As DC A14 | Not applicable | As DC A14 | Not in the serving path |

| Scope | Servers | Cores | RAM | Storage (usable) |
|---|---|---|---|---|
| **Option A UAT total, procuring everything** | **14** | **120** | **~592 GiB** | **~12 TB** |
| *Of which dedicated and unavoidable (A2, A4–A5)* | *5* | *80* | *448 GiB* | *~8 TB* |

### 5.2 Option B — Without Kubernetes

| Server type | Qty | No. of cores (each) | RAM (each) | Hard drive / volumes (each) | OS supported | Database supported | Software required | Justification |
|---|---|---|---|---|---|---|---|---|
| **Application** — JVM application server | 3 | 16 | 64 GiB | 200 GB OS + 200 GB app & logs (SSD) | As DC B1 | Not applicable | As DC B1 | Three holds one instance of each of the 23 services across more than one host, so the routing tier and the rolling deployment procedure are both genuinely exercised |
| **Routing** — Internal service router | 1 | 4 | 16 GiB | 100 GB OS | As DC B2 | Not applicable | As DC B2, single node | Single node, but **present** — this tier does not exist in Option A, so UAT is where its 22 backend pools and health checks are validated before production |
| **Routing** — Load balancer / reverse proxy | 1 | 4 | 8 GiB | 100 GB OS | As DC B3 | Not applicable | As DC B3, single node | Single node: an ingress outage in UAT interrupts testing, not service |
| **Database** — Primary (operational **and** warehouse) | 1 | 16 | 128 GiB | 200 GB OS + **2 TB data** + 500 GB log + 500 GB tempdb | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | Database engine, Always On AG, SQL Server Agent, Flyway 10+ | Both databases on one instance; 2 TB carries a representative data slice, not full history |
| **Database** — Readable secondary | 1 | 16 | 128 GiB | Identical to the primary | Oracle Linux 8/9 · RHEL 8/9 · Windows Server 2022 | **Oracle Database 19c or 23ai EE + Partitioning** | As the primary, as a **readable** AG replica | **Do not remove this to save a server** — see the Option A justification |
| **Messaging** — RabbitMQ broker | 1 | 4 | 16 GiB | 100 GB OS + 200 GB (SSD) | As DC B8 | Not applicable | As DC B8, single node | Message contracts are what UAT validates; quorum behaviour is a production concern |
| **Cache** — Redis | 1 | 4 | 16 GiB | 100 GB OS + 100 GB (SSD) | As DC B9 | Not applicable | Redis 7.2+, single node | UAT's cached working set is a fraction of production's |
| **Storage** — S3-compatible object store | 1 | 4 | 16 GiB | 100 GB OS + 1 TB | As DC B10 | Not applicable | MinIO single node, or a UAT bucket on the bank's existing store | Single node; 1 TB covers test document volumes |
| **Monitoring** — Observability stack | 1 | 8 | 32 GiB | 100 GB OS + 1 TB (SSD) | As DC B11 | Not applicable | As DC B11, single node | Carries more weight in this option, since nothing reschedules a failed instance — process-level alerting is validated here |
| **Management** — Configuration management / bastion | 1 | 8 | 16 GiB | 200 GB OS + 100 GB | As DC B13 | Not applicable | As DC B13 | **8 cores, as in production.** The deployment automation itself is under test in UAT, so the control node must behave as it will in production |

| Scope | Servers | Cores | RAM | Storage (usable) |
|---|---|---|---|---|
| **Option B UAT total, procuring everything** | **12** | **116** | **~568 GiB** | **~10.9 TB** |
| *Of which dedicated and unavoidable (B1, B4–B5)* | *5* | *80* | *448 GiB* | *~8 TB* |

The artifact repository is shared with production in both options; UAT does not need its own.

### 5.3 Performance and load testing

UAT as specified is sized for functional acceptance, not for load. A load test at 3,000 concurrent users
against a three-node application tier and a 2 TB warehouse **proves nothing about production**, because
it reproduces neither the instance floor nor the data volume.

Two options, and the bank should pick one before contract:

| Option | What it costs | What it gives |
|---|---|---|
| **A — Size UAT to production shape** (recommended) | Raise the UAT application tier from 3 to **6 servers**, and the warehouse data volume from 2 TB to **4 TB** (+48 cores, +192 GiB, +2 TB) | One environment that validates both function and performance |
| **B — A fourth environment** | A production-shaped performance environment, ~200 cores / ~900 GiB / ~15 TB | Isolation of load testing from acceptance testing |

If neither is taken, the service levels in section 2 cannot be validated before go-live, and should be
stated in the bid as unvalidated. **This matters more in deployment Option B**, where there is no
autoscaling to absorb an under-estimate discovered in production.

---

## 6. Headroom against the product's committed envelope

The product's architecture commits to **10,000 provisioned users at 5,000 concurrent**. The client's
stated profile — 10,000 users at 30%, or **3,000 concurrent** — therefore sits *inside* that envelope
with meaningful headroom, and the sizing above is not at the edge of what the design supports.

| Measure | This sizing (3,000 concurrent) | Product committed envelope (5,000 concurrent) |
|---|---|---|
| Peak request rate | ~600 RPS | ~1,000 RPS |
| Service instances at burst ceiling | ~168 | ~280 |
| Application tier, Option A | 7 workers × 16 cores / 64 GiB | 11 workers × 16 cores / 64 GiB |
| Application tier, Option B | 9 servers × 16 cores / 64 GiB | 14 servers × 16 cores / 64 GiB |
| Warehouse readable secondaries | 1 | 2 |
| Cache memory | 32 GiB | 48 GiB |
| Message broker nodes | 3 | 3 |
| Operational database | Unchanged | Unchanged |

The broker holds at three nodes where the 5-second message budget needed five, because a 10-second budget
tolerates roughly twice the queue depth before processing time breaks.

In Option A, reaching the committed envelope is **configuration** — instance counts, connection pool
sizes and cache memory — consistent with the product's one-build-per-release position, under which every
bank-specific and environment-specific setting is supplied at run time.

In Option B it is **procurement and redeployment**: new hosts, new backend pools, and a change window.
This is the practical cost of the missing elasticity, and it compounds the point in section 5.3 — an
under-estimate in Option B is discovered in production and fixed by buying servers.

**If actual concurrency proves higher than 30%**, the sizing moves almost linearly in the application
tier and sub-linearly in the data tier, because the read path is cached and served from the readable
secondary. Each additional 10 percentage points of concurrency adds roughly 1–2 application hosts and
8 GiB of cache memory; the operational database does not move until well past the committed envelope.

**If the cache hit rate falls short of 80%**, the warehouse moves instead. At 60% the read load reaching
it doubles and it returns to 32 cores per node, undoing most of what the relaxed drill budget saved.
This is the assumption to watch second, after concurrency.

---

## 7. Storage growth

| Store | Initial (DC) | Annual growth | Driver |
|---|---|---|---|
| Data warehouse | 11 TB | 3–4 TB | Postings at 2–5 million per day, plus daily balance and facility snapshots across ~100,000 customers |
| Operational databases | 1.5 TB | 300–400 GB | Transactional state, the audit ledger and the event outboxes |
| Object store | 6 TB usable | 1–1.5 TB | Ingested documents and their extraction artefacts |
| Observability retention | 2 TB | Fixed by retention policy | Metrics, traces and logs |
| Backup target | 31 TB | Set by the bank's retention policy | Full and differential backups across all stores |

Audit, event and snapshot tables are partitioned where volume warrants it; the warehouse facts are
month-partitioned with columnstore indexes from the outset. **Provision storage with 30% free capacity
at handover** — columnstore rebuilds and partition switches need working room.

---

## 8. AI model serving

The product reaches models through a **provider-neutral gateway**; no model provider SDK is embedded in
any service, and the endpoint is bank configuration. This is identical in both deployment options, but
on-premises deployment forces a choice the bank must make:

| Option | Infrastructure | Consequence |
|---|---|---|
| **A — Approved external endpoint** (default) | None. The gateway calls the endpoint the bank approves | Requires outbound HTTPS egress (section 9). No GPU procurement |
| **B — On-premises inference** | 2 servers per site: 32 cores, 256 GiB RAM, 2 TB NVMe, **2 × NVIDIA L40S 48 GB or H100 80 GB each**; RHEL 9.x or Ubuntu 22.04 LTS; NVIDIA driver, CUDA 12.x, container toolkit, and an inference server (vLLM, TGI or NVIDIA NIM) | No model egress. Adds ~64 cores, ~512 GiB, 4 TB and 4 GPUs per site. Model quality and the agent evaluation thresholds must be re-validated against whichever open-weight model is deployed |

At 50–150 model calls per relationship manager per day across a 10,000-user population, option B is sized
for throughput, not for a single very large model. **Settle this before procurement** — it is the one
line item that materially changes the hardware bill in either deployment option.

---

## 9. Network, connectivity and supporting requirements

Common to both deployment options unless stated.

| Item | Requirement |
|---|---|
| Network within each site | 10 GbE to every server; redundant switching; separate VLANs for application, database and storage |
| **Outbound HTTPS egress** | Required for Microsoft Entra ID sign-in (`login.microsoftonline.com`, `graph.microsoft.com`) and, under section 8 option A, the approved model endpoint. Via the bank's proxy is fine. **On-premises is not air-gapped** |
| Microsoft Teams channel | Requires outbound egress to Microsoft 365. Omit if the bank deploys the web channel only |
| DNS · NTP | Internal resolution for all service and host names; time synchronised across all servers (Data Guard and token validation both depend on it). **Option B additionally depends on DNS for service routing**, where Option A uses cluster DNS |
| TLS certificates | Issued from the bank's internal or public CA for the ingress endpoint and for database connections |
| Secret store | The bank's existing store (HashiCorp Vault, CyberArk, or Kubernetes Secrets backed by one). The product requires **one database secret per service** plus a messaging secret and a drill-token secret, supplied at run time |
| Identity | Microsoft Entra ID. The product issues no credentials of its own |
| Virtualisation | VMware vSphere 8, Nutanix AHV, or KVM/RHV. **Bare metal recommended for the database tier** in both options — and note that Oracle core-factor licensing on a virtualised host usually counts the whole host unless the hypervisor is on its approved hard-partitioning list |
| Storage platform | Enterprise SAN or NVMe-backed hyperconverged storage. Option A additionally requires a **Kubernetes CSI driver**. Database volumes require a minimum of 20,000 IOPS at 8 KB random for the warehouse pair and 10,000 for the operational pair |

---

## 10. Software and licence summary

| Software | Where | Licence position |
|---|---|---|
| **Oracle Database EE + Partitioning** (+ Active Data Guard, + Database In-Memory) | Database tier in both options, all three environments | **Bank-procured. The largest licence line in this bill.** Partitioning is required; Active Data Guard is required for the readable standby; Database In-Memory is the columnar store the sizing assumes is NOT taken — see 3.5. Per-core with a 0.5 core factor on x86: 92 cores in the DC, 46 in DR, 32 in UAT |
| SQL Server 2022 Enterprise (reference platform) | The alternative the product is built and tested against | Bank-procured, per core. Includes partitioning, columnstore and readable secondaries, so it needs no extra-cost options and sizes smaller: 664 total cores against 694 |
| Windows Server 2022 | Database servers, if not deployed on Linux; Option B application servers if deployed on Windows | Bank-procured, per core |
| **Kubernetes distribution** | **Option A only** — all cluster nodes | Bank-procured if a supported distribution (OpenShift, RKE2 with support, Rancher). Free if self-managed kubeadm |
| **Eclipse Temurin JDK 21** | **Option B only** — application servers | Free and open source. Commercial support optional (Azul, Red Hat, IBM) |
| **Configuration management** (Ansible, Puppet, Chef) | **Option B only** — control node and agents | Bank-procured where a supported edition is used; free for Ansible core |
| RHEL / Oracle Linux / SLES | All Linux servers | Bank-procured subscription. Ubuntu LTS is free without a support contract |
| VMware vSphere / Nutanix | Virtualised servers | Bank-procured, per socket or per core |
| RabbitMQ · Redis · MinIO · Prometheus · Grafana · OpenTelemetry · Harbor | Supporting services in both options | Open source. Commercial support optional and bank-procured |
| JFrog Artifactory / Nexus | Artifact repository, where used instead of Harbor | Bank-procured |
| Enterprise backup product | Backup target | Bank-procured |
| NVIDIA driver, CUDA, inference server | Section 8 option B only | Driver and CUDA free; GPU hardware and any commercial inference product bank-procured |
| **RM Platform application** | Container images (Option A) or JAR artefacts (Option B), delivered to the bank's repository | Supplied by us under the commercial agreement. No per-core or per-server infrastructure licence is charged by us |

---

## 11. Totals and confirmation

### 11.1 Option A — With Kubernetes

| Environment | Procuring everything | | | | Dedicated capacity only | |
|---|---|---|---|---|---|---|
| | Servers | Cores | RAM | Storage | Servers | Cores |
| Production — DC | 35 | 336 | ~2,288 GiB | ~97 TB | 11 | 204 |
| Production — DR | 28 | 238 | ~1,440 GiB | ~43 TB | 7 | 126 |
| UAT | 14 | 120 | ~592 GiB | ~12 TB | 5 | 80 |
| **Total** | **77** | **694** | **~4,320 GiB** | **~151 TB** | **23** | **410** |

### 11.2 Option B — Without Kubernetes

| Environment | Procuring everything | | | | Dedicated capacity only | |
|---|---|---|---|---|---|---|
| | Servers | Cores | RAM | Storage | Servers | Cores |
| Production — DC | 33 | 336 | ~2,272 GiB | ~94 TB | 13 | 236 |
| Production — DR | 27 | 242 | ~1,440 GiB | ~42 TB | 9 | 158 |
| UAT | 12 | 116 | ~568 GiB | ~11 TB | 5 | 80 |
| **Total** | **72** | **694** | **~4,280 GiB** | **~146 TB** | **27** | **474** |

### 11.3 Reading these totals

**"Procuring everything" is the worst case, not the recommendation.** It assumes the bank operates none
of the Category B capability in section 3.3 — no load balancers, no object storage, no observability
platform, no artifact repository, no backup estate. A bank running 100,000 wholesale customers operates
most of it. "Dedicated capacity only" is the opposite bound: what remains when every reusable capability
is reused.

**The two options cost the same.** Both land at 694 cores. Option B is five servers and ~40 GiB fewer in
the all-procured case, and **64 cores more** in the dedicated case, because its application tier must be
statically provisioned for peak rather than autoscaled to it. The choice between them is operational, not
financial — section 3.4.

**The performance targets are worth about 11% of the bill.** Relaxing API p95 from 500 ms to 2 seconds and
the drill page from 2 seconds to 5 takes the total from 780 cores to 694, and the dedicated figure from
496 to 410. Section 2.3 explains why it is not more: the gain saturates once the autoscaler cannot react
before the queue grows, and beyond roughly 2 seconds of budget nothing further falls out.

**Excluded from every figure above**, and to be added once the bank decides (section 3.3, Category C):
UAT performance sizing (+48 cores, 192 GiB, 2 TB) and on-premises model serving (+64 cores, 512 GiB,
4 TB and 4 GPUs per site).

The right next step is a short infrastructure discovery against the "verify before assuming reuse" column
in section 3.3, which converts four bounds into one number.

### 11.4 Confirmation during mobilisation

Every figure in this document is a planning assumption derived from the workload in section 2, and is
stated as such deliberately. During sprint 0 we confirm the bank's actual volumes — the user population
and its real concurrency profile, posting and snapshot rates, portfolio counts and retained history — and
reissue this sizing against them. The performance environment chosen under section 5.3 then validates it
against the service levels in section 2 before the first production deployment.

Where the bank's volumes are materially larger, the sizing scales by the ratios in section 6 rather than
by redesign.

---

## 12. Deployment ladder

Sections 3 to 11 size one deployment. In practice this is a ladder, and the rung matters more to the
bill than any parameter in it. Every figure below is at 10,000 provisioned users and 20% peak
concurrency against 100,000 customers on Oracle, except where the rung itself changes that, and assumes
the bank's existing estate is reused (section 3.3).

| # | Rung | Servers | Cores | Memory | Storage | What it is |
|---|---|---|---|---|---|---|
| 1 | **Pilot** | **3** | **56** | 720 GiB | 18 TB | 6,000 users, single site, **no high availability** |
| 2 | **Minimum highly available** | 7 | 88 | 784 GiB | 19 TB | 10,000 users, single site, 6 months of history |
| 3 | **Production DC** | 7 | 124 | 1,472 GiB | 37 TB | 10,000 users, single site, full history |
| 4 | **Production DC + DR** | 12 | 210 | 2,304 GiB | 57 TB | Adds the recovery site |
| 5 | **DC + DR + UAT** | 17 | 290 | 2,752 GiB | 65 TB | Adds somewhere to test a release |
| 6 | *…procuring the platform estate too* | 71 | 574 | 3,936 GiB | 148 TB | Rung 5, where the bank runs none of Category B |

### 12.1 What each rung buys

| From → to | Costs | Buys |
|---|---|---|
| **1 → 2** | +4 servers, +32 cores | **Survives a server failure.** Paired databases, a readable standby, three application hosts, and the 99.5% target becoming assertable. Also 4,000 more users |
| **2 → 3** | +36 cores, +18 TB | **24 months of history instead of 6** — year-on-year comparison, and the cache hit target back to a realistic 80% from 95% |
| **3 → 4** | +5 servers, +86 cores | **A 4-hour recovery time.** Without it, recovery from losing the site is restore-from-backup, at whatever that takes |
| **4 → 5** | +5 servers, +80 cores | **Somewhere to test a release** before it reaches production users |

### 12.2 Where the rungs sit against a core budget

A 48–56 core budget reaches **rung 1 only**. That is not a matter of tuning: two database pairs at their
own floors are 48 cores before a single service runs, and a highly available application tier needs three
hosts on top. Across 49,920 combinations of users, customers, postings, retained history, host size and
pod request, **no highly available configuration fell inside 48–56 cores** — the leanest is rung 2 at 88.

Rung 1 fits by giving up high availability, not by being smaller: one application host, one operational
database, and one warehouse server carrying the read path as well as the daily load — which is why that
server is 28 cores rather than 16. Dropping a standby moves work onto the primary; it does not halve the
tier. At 6,500 users rung 1 no longer fits either, and the next reachable figure is 76 cores.

**Rung 1 is a way to try the product, not to run a bank on it.** It carries no availability target, no
recovery point, no recovery time, and no drill budget worth asserting. It is the right answer for a
proof of concept and the wrong answer for a first production deployment, and a bid should say which of
those it is quoting.

### 12.3 Working each rung

| Rung | File |
|---|---|
| 1 — Pilot | `architecture/hardware-sizing-pilot.html` |
| 2 — Minimum highly available | `architecture/hardware-sizing-minimum.html` |
| 3 — Production DC | `architecture/hardware-sizing-production-dc.html` |
| 5–6 — Full estate | `architecture/hardware-sizing-calculator.html` |
| The 48-core application tier | `architecture/hardware-sizing-48-core.html` |

Each opens on its own configuration and recomputes as the inputs change, so a rung can be moved rather
than argued about.

---

## 13. Document control

| Field | Value |
|---|---|
| Document | RM Platform — Infrastructure Sizing, On-Premises (client-facing) |
| Document version | 3.2 |
| Supersedes | Version 3.1 (2026-11-09), on SQL Server · Version 3.0 (2026-11-09), at spec NFR-2's performance targets · Version 2.0 (2026-11-09), on-premises, single deployment option · Version 1.0 (2026-11-06), cloud-shaped, 4,000 users |
| Product version | 0.1.0 |
| Compiled | 2026-11-09 |
| Sizing basis | 10,000 provisioned users · 30% peak concurrency · ~100,000 wholesale banking customers · API p95 < 2 s · drill < 5 s · message processing < 10 s · cache hit > 80% · **Oracle Database** · on-premises · UAT + Production DC + Production DR · with and without Kubernetes |
| Derived from | `architecture/capacity-model.md`, `docs/architecture/12-ops.md`, `deploy/helm/rm-platform/values-microservices.yaml`, `deploy/` |
| Companion document | RM Platform — Bill of Materials (`architecture/bill-of-materials-rfp.md`) |
| Related | `architecture/oracle-support-gap.md` — what running on Oracle would require |
| Interactive model | `architecture/hardware-sizing-calculator.html` (this document's baseline) · `architecture/hardware-sizing-48-core.html` · `architecture/hardware-sizing-production-dc.html` — **keep them in step**; each carries this document's published totals and flags its own drift from them |
