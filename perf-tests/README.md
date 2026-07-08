# Performance tests (k6)

Black-box performance tests for the person service: [k6](https://k6.io) drives the
real HTTP `/graphql` endpoint, exercising the full stack (dispatch → JSON schema
validation → service → DAL → H2) exactly as a client would.

## Why k6 / black box (and not JMH or JUnit-based timing)?

- What matters for a GraphQL API is end-to-end request latency and throughput —
  serialization, validation, transactions and the DB included. A black-box tool
  measures exactly that, stays decoupled from the implementation, and keeps working
  if internals are refactored (or the service is rewritten).
- Perf runs don't belong in the Maven test lifecycle: they're long, load the machine
  and their numbers are environment-dependent. Keeping them in `perf-tests/` makes
  them an explicit, separate activity.
- k6 scripts are plain JavaScript with scenarios/thresholds built in, so pass/fail
  is automated (thresholds fail the run → usable as a CI gate).
- Alternatives: **Gatling** if you prefer a JVM/Maven-integrated tool with HTML
  reports; **JMH** only for micro-benchmarking hot code paths (e.g. a calculation),
  not for service-level testing.

## Layout

```
perf-tests/k6/
├── lib/graphql.js   tiny GraphQL client + response helpers
├── lib/data.js      queries, mutations, input builders (unique emails per VU/iter)
├── smoke.js         1 VU / 10s - every operation works and is fast; run this first
└── load.js          3 parallel scenarios (~2 min, or QUICK=1 for ~20s)
```

`load.js` scenarios:

| Scenario             | Executor                    | What it does                                | Threshold        |
|----------------------|-----------------------------|---------------------------------------------|------------------|
| `read_load`          | ramping-vus 0→20→0          | mixed allPersons / personById / personsByCity | p95 < 250 ms     |
| `write_load`         | constant-arrival-rate 10/s  | createPerson → updateSalary → deletePerson  | p95 < 400 ms     |
| `validation_rejects` | constant-vus 5              | schema-invalid createPerson, expects fast 400-style reject | p95 < 200 ms |

Global thresholds: check success rate > 99%, HTTP failure rate < 1%. Any breached
threshold makes k6 exit non-zero.

## Running

Start the service with the `perf` profile (INFO logging — DEBUG skews latency):

```bash
mvn package -DskipTests
java -jar person-service/target/person-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=perf
```

Then ([install k6](https://grafana.com/docs/k6/latest/set-up/install-k6/), or use Docker):

```bash
k6 run perf-tests/k6/smoke.js                # sanity first
k6 run perf-tests/k6/load.js                 # full run (~2 min)
k6 run -e QUICK=1 perf-tests/k6/load.js      # short variant for CI/sanity
k6 run -e BASE_URL=http://other-host:8080 perf-tests/k6/load.js

# Docker alternative (host networking so localhost:8080 is reachable):
docker run --rm --network host -v "$PWD/perf-tests:/perf" grafana/k6 run /perf/k6/load.js
```

## Baseline (reference machine, in-memory H2, single instance)

Full `load.js` run — all thresholds passed:

| Metric                    | Result          |
|---------------------------|-----------------|
| Requests                  | 268,734 (~2,560 req/s) |
| HTTP failures             | 0               |
| Checks                    | 100% of 335,896 |
| read_load p95             | 25.7 ms         |
| write_load p95            | 38.0 ms         |
| validation_rejects p95    | 25.5 ms         |

Numbers are indicative only (demo dataset, in-memory DB, same-host load generator);
treat the thresholds, not the baseline, as the contract.
