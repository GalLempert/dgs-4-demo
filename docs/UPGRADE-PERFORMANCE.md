# How future upgrades are likely to impact performance

This service intentionally runs an old stack: **Java 11 bytecode, Spring Boot 2.4.2
(Spring 5.3, Hibernate 5.4, Tomcat 9), DGS 4.9.25 (graphql-java 17.3)**. This document
estimates, per upgrade milestone, how much performance movement to expect and where it
would come from. No code changes are implied — it's a planning aid.

Baseline to compare against (see `perf-tests/README.md`): read p95 ≈ 26 ms, write p95
≈ 38 ms, ~2,500 req/s mixed load, zero failures, in-memory H2, same-host load
generator.

## Where this service actually spends time

Upgrades move different slices of the request, so first the anatomy of one request:

1. **HTTP transport + JSON body parsing** (Tomcat, Jackson) — small slice.
2. **graphql-java execution** (parse/validate/plan the operation, walk the selection
   set, invoke data fetchers, assemble the response) — significant slice, and the one
   DGS upgrades move, because **DGS is a thin programming model over graphql-java**;
   most performance change between DGS majors is really the pinned graphql-java.
3. **Our dispatch + JSON schema validation** — negligible-to-small (a map lookup and
   a lambda; validation is ~ms per mutation and short-circuits before any DB work).
4. **JPA/Hibernate + DB** — the largest slice for reads with nested collections.

⚠️ The single biggest performance factor is **not** on this list: H2 runs in-process
with zero network cost. Moving to a real database will dwarf every framework delta
below. Framework upgrades tune the overhead *around* the DB; they don't change the DB.

## Milestone-by-milestone estimates

### JVM (the cheapest wins — no code changes, this project already builds on new JDKs)

| Milestone | Expected impact | Why |
|---|---|---|
| **Java 11 → 17** | ~5–10% throughput, smoother GC | Six releases of accumulated JIT and G1 improvements; nothing to migrate. Practically arrives together with Boot 2.7/3 (Boot 2.4 is not certified on 17). |
| **Java 17 → 21** | Small steady-state gain (~few %); **large concurrency headroom via virtual threads** | Generational ZGC and JIT work help modestly. The big lever is virtual threads — for this *blocking* JPA stack it removes thread-pool starvation under load, flattening tail latency at high concurrency. Requires Spring Boot 3.2+ to switch on (`spring.threads.virtual.enabled=true`); Java 21 alone doesn't use them. |
| **Java 21 → 25** | Modest steady-state; notable **memory** and **startup** wins | Compact object headers (production in 25) shrink every object by ~4–8 bytes — allocation-heavy GraphQL execution benefits from better cache locality; AOT/Leyden work cuts startup. |

### Spring Boot

| Milestone | Expected impact | Why |
|---|---|---|
| **2.4.2 → 2.7** (your planned step) | ≈ neutral (±0–2%) | Same Spring 5.3 / Hibernate 5.4 generation. This is a compatibility milestone (enables DGS 5.x and Java 17), not a performance one. |
| **2.7 → 3.x** | Small-to-moderate direct gain (~5–15% on DB-touching paths), plus the virtual-threads lever | The real perf content is **Hibernate 6**: rewritten query engine (SQM) and JDBC layer with fewer allocations and better fetch handling — reads that hydrate nested collections (`allPersons` with phones/hobbies) are the likely winners. Boot 3.2+ unlocks virtual threads (see above). Micrometer observability, if enabled, adds a small per-request cost. Optional GraalVM native image: startup ~10–50× faster and much lower RSS, at typically somewhat lower peak throughput than warmed-up JIT. **This is also the most laborious migration** (javax→jakarta, Hibernate 6 behavior changes) — most effort, most total performance movement. |
| **3.x → 4.x** (GA since late 2025) | Neutral-to-small | Framework 7 continues startup/memory work (JSpecify null-safety and API modularization are correctness/design features, not hot-path changes). Its perf relevance is mainly that it's the ticket to DGS 11/12 and the newest graphql-java. Too new for broad community numbers — measure. |

### DGS (really: graphql-java, verified pins from Maven Central)

| DGS line | graphql-java | Boot pairing | Expected impact |
|---|---|---|---|
| 4.9 (current) | 17.3 | 2.4–2.5 | baseline |
| **5.6** | 19.x | 2.6/2.7 | Modest engine improvements (17→19: incremental allocation and execution work). Our `@DgsCodeRegistry`/`DataFetcher` APIs are unchanged — cheap step. |
| **6.x / 7.x** | 19/20 | 3.0 / 3.1–3.3 | Ride-along with the Boot 3 migration; graphql-java 20 continues incremental gains. |
| **8.x** | 21.x | 3.x | graphql-java 21 had notable execution-engine performance work. DGS 8 also introduces the optional spring-graphql integration. |
| **9.x** | 22.x | 3.x | **The big engine step**: graphql-java 22 reworked execution internals to drastically cut per-request allocations — the most measurable GraphQL-layer win in the whole chain, felt most on large responses (`allPersons` with full nesting). |
| **10.x** | 24.0 | 3.4/3.5 | spring-graphql becomes the default transport — roughly equivalent performance, slightly different interception stack; continued engine polish. |
| **11.x / 12.x** | 25.0 | 4.x | Newest engine; pairs with the Boot 4 milestone. Incremental. |

Our infrastructure is insulated from all of this: resolvers are plain `DataFetcher`
lambdas registered programmatically, which every one of these versions supports, and
the per-request dispatch overhead (registry lookup + lambda) is constant and
negligible regardless of version.

### Side upgrade worth taking along

`com.networknt:json-schema-validator` 1.0.52 → 1.5.x gained significant validation
performance over the years (caching, fewer allocations). It's independent of the
framework milestones and touches the hot path of every mutation — cheap win during
any of the steps above.

## Summary: recommended order vs. expected payoff

| Step | Stack after | Effort | Perf payoff |
|---|---|---|---|
| 1 | Boot 2.7 + DGS 5.6 + Java 17 | Low | Small (mostly JVM ~5–10%) |
| 2 | Boot 3.x + DGS 9/10 + Hibernate 6 + Java 21 + virtual threads | **High** | **Largest**: Hibernate 6 on reads, graphql-java 22+ on GraphQL execution, virtual threads on tail latency under load |
| 3 | Boot 4.x + DGS 11/12 + Java 25 | Moderate | Incremental: compact object headers (memory), newest engine, faster startup |

## How to verify each step

All estimates above are directional, drawn from release notes and community numbers —
your dataset, query shapes and hardware decide the real outcome. The k6 suite exists
exactly for this:

```bash
k6 run perf-tests/k6/smoke.js            # still correct?
k6 run perf-tests/k6/load.js             # compare against the recorded baseline
```

Run the full load test before and after each milestone on the same machine, with the
`perf` Spring profile, and compare the per-scenario p95s. The thresholds are the
contract; the baseline table in `perf-tests/README.md` is the reference point. Since
the tests are black-box, they survive every migration above unchanged.
