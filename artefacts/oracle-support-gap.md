# Oracle Database — support gap assessment

**Internal document.** Not for client submission. Written for the bid team and whoever owns the
product roadmap.

Compiled 2026-11-09 · Product version 0.1.0

---

## 1. Position in one paragraph

**Oracle is not a supported database for this product today, and supporting it is a delivery scope
item rather than a runtime setting.** The operational databases are parameterized across SQL Server,
PostgreSQL and H2 (ADR-0018); the warehouse is SQL Server only, in T-SQL, with no dialect abstraction
of any kind. One of the gaps is a **security defect rather than missing work**: pointed at Oracle today,
every service would start, run, and silently fail to apply row-level security. Nothing in the code
references Oracle. If a bank mandates Oracle, it needs estimating, scheduling and agreeing in the
contract — not answering with a profile name.

---

## 2. What is supported today, with evidence

| Surface | Engines | Evidence |
|---|---|---|
| Per-service operational databases | SQL Server · PostgreSQL · H2 | ADR-0018; `db/<service>/sqlserver/` and `db/<service>/postgresql/`, 48 migration files each across 22 services |
| Row-level security session context | SQL Server · PostgreSQL (H2 and anything else: no-op) | `libs/security/.../RlsSessionContext.java` |
| **Data warehouse** | **SQL Server only** | `db/warehouse/` — a single T-SQL set, 5,981 lines, no per-vendor folders |
| Warehouse generator | **Emits T-SQL only** | `db/tools/sql.mjs` — no dialect switch exists |
| Seed portfolio load | SQL Server only | `seed/out/sql/load.sql`, 92,444 lines, `SET NOCOUNT ON`, run with `sqlcmd` |
| Integration tests | SQL Server (Testcontainers) | CLAUDE.md rule 10 |
| Container default | `sqlserver` | `deploy/helm/rm-platform/values-microservices.yaml`, `databaseProfile` |

ADR-0018's title is explicit: *"**Operational** databases parameterized across SQL Server, PostgreSQL
and H2."* The warehouse was never in its scope.

---

## 3. Gap 1 — Row-level security would silently no-op on Oracle (and already does not run at all)

**This is the first item, ahead of all effort estimates — but read the second half first, because the
Oracle issue sits on top of a larger one that is already logged.**

`context/09-known-gaps.md` severity-1 items 3 and 4 record that **`RlsSessionContext` has no callers at
all** (`grep RlsSessionContext.` → 0) and that its SQL Server path carries a bug: `apply` sets the key
with `@read_only = 1` and `clear` then tries to reset it, which SQL Server rejects, poisoning the next
borrower of a pooled connection. So row-level security is not working on any engine today. Oracle does
not break something that currently works; it adds a failure mode that **survives fixing gap 3**, which
is why it belongs in this assessment rather than only in the register.

`RlsSessionContext` resolves the engine from the JDBC product name into three cases:

```java
private enum Vendor { SQLSERVER, POSTGRESQL, OTHER }
...
return Vendor.OTHER; // H2 (even in PostgreSQL mode it reports "H2"), and anything else
```

and for `OTHER`, `apply` does nothing:

```java
case OTHER -> { /* no engine-level session scope (e.g. H2 dev/test) */ }
```

Oracle reports `Oracle` as its product name, so it falls to `OTHER`. Once gap 3 is fixed and the
library is actually called, the consequence on Oracle is:

- The per-request user scope is **never set**.
- Any security predicate that reads it therefore **never filters**.
- **No error is raised.** Services start, requests succeed, screens render.

The behaviour is correct for its intended case — H2 in local development, where scoping is enforced in
the query layer. It is wrong for a production engine. This product governs who may see which clients,
including coverage, delegations, information barriers and wall-crossings (`db/catalogue/warehouse/95-security.yaml`),
so the failure mode is a user seeing clients they are not entitled to, with nothing in the logs.

Two things follow, and the first does not depend on Oracle at all:

1. **`OTHER` must fail closed for an unrecognised production engine**, rather than no-op. An engine the
   library does not know should refuse to serve, not serve unscoped. Today that costs nothing, because
   the only engine reaching `OTHER` is H2 in development. Left as it is, it becomes the way an Oracle
   deployment passes a demo and fails an audit.
2. An Oracle branch would use `DBMS_SESSION.SET_CONTEXT` against a dedicated namespace, with Virtual
   Private Database policies in place of SQL Server's security predicates and PostgreSQL's row policies.

---

## 4. Gap 2 — Operational tier

Adding a third dialect alongside two that already exist. The structure is in place, which makes this
the most predictable part of the work.

| Item | Scope |
|---|---|
| Flyway migration sets | A third folder per service: `db/<service>/oracle/`, matching the existing version numbers — 22 services, 48 files to mirror |
| Spring profile | A fourth value for `SPRING_PROFILES_ACTIVE`, plus Oracle JDBC driver and Hikari settings |
| Row-level security | VPD policies per table, and the `RlsSessionContext` branch from section 3 |
| Integration tests | A third Testcontainers vendor in CI (`-Dtest.db.vendor=oracle`), against an Oracle image the bank's licence permits |
| Helm | `databaseProfile: oracle` and the per-service secret contract unchanged |

**Runtime SQL is mostly portable already.** ADR-0018 confined it to a subset chosen for three engines,
and Oracle 12c and later accepts most of it: `OFFSET … ROWS FETCH NEXT n ROWS ONLY`, `COALESCE`,
`CONCAT`, `CURRENT_TIMESTAMP`, correlated scalar subselects. Timestamps, identifiers and date arithmetic
are already computed in Java rather than in SQL, which removes the usual portability problems.

Two semantic differences need a deliberate sweep rather than a search-and-replace:

- **Oracle treats the empty string as NULL.** Any code distinguishing `''` from NULL changes meaning.
  The outbox's `published` flag is `SMALLINT` (0/1), which is safe, but the sweep is still needed.
- **No SQL `BOOLEAN` before Oracle 23c.** Check every flag column against the chosen Oracle version.

---

## 5. Gap 3 — The warehouse

The larger piece, and not the same kind of work as section 4. **There is no PostgreSQL warehouse
either**, so this is not adding a dialect to an existing abstraction — it is building the first one.

| Item | Scope |
|---|---|
| Generator | `db/tools/sql.mjs` emits T-SQL with no dialect switch: `CREATE CLUSTERED COLUMNSTORE INDEX`, `NVARCHAR`, `DATETIME2(0)`, `CONVERT(…, 23/126)`, `DECLARE @t TABLE`, `GO` separators. An Oracle target has to be written, not configured |
| Generated DDL | 5,981 lines across V1–V11 |
| Procedural code | **38 `CREATE OR ALTER PROCEDURE`**, 5 functions, 1 `CREATE SECURITY POLICY`. T-SQL to PL/SQL is a rewrite, not a translation. Oracle has no `CREATE OR ALTER` (it is `CREATE OR REPLACE`) and no table variables |
| Partitioning | 4 partition functions and 4 partition schemes become Oracle interval partitioning. **Oracle Partitioning is a separately licensed option** |
| Columnstore | 4 clustered columnstore indexes have no core-Oracle equivalent. Options: **Database In-Memory** (separately licensed), Exadata Hybrid Columnar Compression (Exadata only), or neither |
| Seed portfolio | `load.sql` is 92,444 generated lines of T-SQL. Needed for UAT, training and demos, so it is in scope, not optional |
| Performance | Drill-down level 4 scans 1.5–3.5 billion posting rows. The 18-core-per-node sizing assumes columnstore compression with batch-mode execution |

**The sizing may move, and not downwards.** Without a columnar store, the same scans on a row-store
with partitioning and bitmap indexes plausibly need **1.5–2× the CPU**. The current figures assume
columnstore. Treat the warehouse sizing as unvalidated on Oracle until benchmarked.

---

## 6. Licensing — check before assuming Oracle is cheaper

The reason engine choice came up was to reduce the SQL Server Enterprise licence. Oracle may invert
that, because the two features the warehouse depends on are included in SQL Server Enterprise and are
extra-cost options on Oracle.

| | SQL Server 2022 Enterprise | Oracle Enterprise Edition |
|---|---|---|
| Partitioning | Included | **Separately licensed option** |
| Columnar store | Included (columnstore) | **Separately licensed option** (Database In-Memory) |
| Readable secondary | Included (Always On) | Active Data Guard — **separately licensed option** |
| Core counting | Per core | Per core, with a 0.5 core factor on x86 |

Three extra-cost options against one product. Whether that is cheaper depends entirely on the bank's
existing Oracle agreement, and is a question for its licensing partner — the figures above are the
feature dependencies, not a price comparison.

---

## 7. Options

| | Approach | Work | Licensing |
|---|---|---|---|
| **A** | Oracle for everything | Sections 3, 4 and 5 in full. The warehouse dominates | Oracle EE + Partitioning + In-Memory (+ Active Data Guard for the readable replica) |
| **B** | **Oracle for the operational databases; SQL Server for the warehouse** | Sections 3 and 4 only. The warehouse stays as built and as sized | Oracle EE for 2 servers; SQL Server Enterprise for the warehouse pair (36 cores) |
| **C** | Product brings its own engine | None | SQL Server Enterprise, 56 cores — as currently sized |

**Option B is worth putting to the bank.** It meets an Oracle-standard estate where the bank's own data
actually lives — the transactional systems — while leaving the analytical store on the engine it was
built and sized for. It also avoids the only part of this with genuine delivery risk: 38 stored
procedures and a columnstore dependency.

Option C is not a refusal. Many banks will host a vendor appliance database alongside their standard,
and the sizing already assumes dedicated database servers rather than a slot on the bank's estate.

---

## 8. What is needed to decide

| Question | Who answers |
|---|---|
| Is Oracle a mandate, or the bank's existing standard that a vendor product may sit beside? | The bank |
| Which Oracle version and edition, and which options are already licensed? | The bank's licensing partner |
| Does the mandate cover analytical stores, or only systems of record? | The bank |
| Effort and schedule for sections 3–5, by option | Delivery |
| Does the roadmap accept a third operational dialect permanently? | Product |

**Nothing in this document should be quoted to the client as supported.** Until the work in sections 3
to 5 is estimated and scheduled, the supported engines are those in section 2.

---

## 9. Action independent of this decision

Section 3's fail-closed change should be raised as a defect now, whatever is decided about Oracle. A
library that silently disables row-level security on any engine it does not recognise is a finding a
bank's own security review would be right to raise, and it is cheap to fix while the only affected
engine is H2 in development.

It belongs with the row-level-security work already in the register rather than after it: gap 3 (no
caller), gap 4 (the `@read_only` clear bug on SQL Server) and this fail-closed change are one piece of
work on one class, and doing them together costs barely more than doing gap 3 alone.

---

## 10. Document control

| Field | Value |
|---|---|
| Document | Oracle Database — support gap assessment (internal) |
| Compiled | 2026-11-09 |
| Basis | `architecture/adr/0018-multi-database-parameterization.md`, `libs/security/.../RlsSessionContext.java`, `db/warehouse/`, `db/tools/sql.mjs`, `db/<service>/{sqlserver,postgresql}/`, `seed/out/sql/load.sql`, `deploy/helm/rm-platform/values-microservices.yaml` |
| Related | `architecture/bill-of-materials-rfp.md` section 7 · `architecture/hardware-sizing-rfp.md` section 3.5 · `context/09-known-gaps.md` |
