# DAL alternatives to Hibernate / JPA

Context: the upgrade path to Spring Boot 3 implies Hibernate 6 (see
[UPGRADE-PERFORMANCE.md](UPGRADE-PERFORMANCE.md)). If Hibernate 6 is not wanted, this
document compares the realistic alternatives for the data access layer, given two
constraints from the project owner:

- **Spring Data programming style is liked** (repository interfaces, derived queries).
- **JPA / full ORM is not a requirement.**

A fact that shapes everything below: in this codebase the persistence technology
touches only **six files** — `Person`, `Address`, `PhoneNumber`, `BaseEntity`
(annotations) and `PersonRepository` + `PersonDal`. The service, mapper, GraphQL and
validation layers see only DTOs. Swapping the DAL is a contained migration by design.

## The candidates

| | Type | Spring Data style? | Runs on Boot 2.7 / 3 / 4 |
|---|---|---|---|
| **Spring Data JDBC** | aggregate-oriented mapper, no ORM machinery | ✅ native | ✅ / ✅ / ✅ |
| **MyBatis** | SQL mapper (you write all SQL) | ⚠️ own mapper style; `mybatis-spring-boot-starter` | ✅ / ✅ / ✅ |
| **jOOQ** | typesafe SQL DSL + code generation | ⚠️ no repositories; integrates with Spring tx | ✅ / ✅ / ✅ |
| **JDBI 3** | lightweight declarative SQL objects | ⚠️ own style | ✅ / ✅ / ✅ |
| **JdbcTemplate / JdbcClient** | raw Spring JDBC helpers | ❌ hand-rolled | ✅ / ✅ / `JdbcClient` needs 3.2+ |
| **JPA with EclipseLink** | full ORM, different provider | ✅ (Spring Data JPA) | possible but upstream-swimming |
| **Spring Data R2DBC** | reactive, no ORM | ✅ | needs a reactive stack |

## Feature comparison

| | Spring Data JDBC | MyBatis | jOOQ | JDBI 3 | JdbcTemplate |
|---|---|---|---|---|---|
| Derived queries (`findByAddressCityIgnoreCase`) | ✅ | ❌ write SQL | ❌ write DSL | ❌ write SQL | ❌ write SQL |
| Nested objects (our embedded `Address`) | ✅ `@Embedded` | ✅ resultMap | ✅ nested records/multiset | ⚠️ row-mapper work | ⚠️ row-mapper work |
| Child collections (our `phoneNumbers`) | ✅ `@MappedCollection` | ✅ collection resultMap | ✅ `MULTISET` (excellent) | ⚠️ manual reduction | ⚠️ manual reduction |
| Change tracking / dirty checking | ❌ (explicit save) | ❌ | ❌ | ❌ | ❌ |
| Lazy loading | ❌ (whole aggregate loads eagerly) | ❌ | ❌ | ❌ | ❌ |
| Compile-time query safety | ❌ | ❌ (runtime) | ✅✅ generated types | ❌ | ❌ |
| Complex SQL (window fns, CTEs, vendor features) | ⚠️ via `@Query` strings | ✅ full SQL | ✅✅ best in class | ✅ full SQL | ✅ full SQL |
| Schema generation for tests/demo | ❌ (bring your own DDL/Flyway) | ❌ | ❌ | ❌ | ❌ |
| Auditing (`createdAt`/`updatedAt`) | ✅ Spring Data auditing | manual | manual | manual | manual |

Note the first column of "❌ change tracking / lazy loading": for JPA dislikers these
are usually **features**, not gaps — no session to manage, no `LazyInitializationException`
(we already hit one in this project), no surprise UPDATE statements.

## Ease of use

- **Spring Data JDBC** — smallest mental shift from today: same repository
  interfaces, same `@Query` escape hatch, same Spring Data auditing for `BaseEntity`
  timestamps. New concept to learn: *aggregates* — the repository loads/saves the
  whole `Person` aggregate (address + phones) as a unit, references between
  aggregates are ids, not object graphs. Caveats for this project: DDL is on you
  (Flyway/Liquibase or schema.sql — no `ddl-auto`), and a `Set<String>` element
  collection like `hobbies` needs a tiny wrapper record for the child table.
- **MyBatis** — trivial to understand (SQL in, objects out), but every query and
  every nested mapping is explicit XML/annotation work; verbosity grows linearly
  with the model. Two files per aggregate (mapper interface + mapping) is typical.
- **jOOQ** — a build step generates typesafe classes from the schema; queries are
  compile-checked Java that reads like SQL. Superb once set up; the setup (codegen
  wired into Maven, schema-first workflow) is the tax. License: OSS databases (H2,
  Postgres, MySQL) are free; Oracle/SQL Server need a paid license.
- **JDBI 3** — pleasant, small API (`@SqlQuery`/`@SqlUpdate` on interfaces feels a
  bit like Spring Data with explicit SQL); weaker Spring Boot integration (community
  starter), you assemble more yourself.
- **JdbcTemplate/JdbcClient** — no framework to learn, every row mapper and joined
  collection is hand-written. Fine for 2 tables, painful for 20.

## Community & popularity (honest, approximate)

| | Signal |
|---|---|
| Spring Data JDBC | Part of the Spring Data umbrella → maintained by VMware/Broadcom with Spring's release train and docs; smaller mindshare than Spring Data JPA but growing as the "JPA escape hatch"; first-class Boot starter. |
| MyBatis | Huge installed base (the dominant DAL in Chinese/Korean enterprise, common in older US/EU enterprise); ~20k GitHub stars; active but conservative development; enormous Stack Overflow corpus. |
| jOOQ | The reference for typesafe SQL on the JVM; very active single-vendor development (Data Geekery), excellent docs/blog; smaller but passionate community; ~6k stars. |
| JDBI | Respected niche (used by Dropwizard ecosystem); ~2k stars; steady maintenance, smaller ecosystem and hiring pool. |
| JdbcTemplate | Ubiquitous by definition (ships with Spring); infinite examples. |
| EclipseLink | The JPA reference implementation, but community/docs/tooling are a fraction of Hibernate's; Boot doesn't manage it — you maintain the integration. Listed for completeness: it answers "I dislike Hibernate" but not "I don't need ORM". |

## Performance

All of these are thin layers over JDBC, so **for simple CRUD they cluster within a
few percent of raw JDBC and generally beat JPA**, because there is no persistence
context, dirty-checking, first-level cache or entity proxying per request. What
differentiates them:

- **MyBatis / JDBI / JdbcTemplate / jOOQ** — essentially raw-JDBC cost plus mapping;
  jOOQ's `MULTISET` lets you fetch a nested aggregate (person + phones + hobbies) in
  **one** round trip, which is the best possible shape for our `allPersons` query.
- **Spring Data JDBC** — the aggregate model loads children with separate selects
  (per aggregate root — effectively N+1 when listing persons with their phones). For
  our demo dataset that's irrelevant; at scale, list endpoints need a hand-written
  `@Query` with a join (or pagination) — a known and manageable hot spot.
- **JPA/Hibernate** — pays per-request overhead for its machinery, but *wins back*
  performance in write-heavy flows via batched dirty-check flushes and in read flows
  via caches — none of which this read-mostly, DTO-mapping service uses.
- Perspective from our k6 baseline: end-to-end p95 is ~26 ms with H2 in-process; on
  a real network database, driver + network dominate and the DAL choice moves single
  milliseconds. **Choose by ergonomics and maintenance, not microseconds** — and
  verify with `perf-tests/` after any swap (black-box tests survive the migration
  unchanged).

## What each choice means for this codebase

Files touched in every scenario: `PersonRepository`, `PersonDal`, the 3 domain
classes + `BaseEntity`, plus DDL (a `schema.sql`/Flyway migration replaces
`ddl-auto`). Untouched in every scenario: `PersonService`, `PersonMapper`,
`PersonCalculations`, all GraphQL resolvers, dispatch/validation/error
infrastructure, all tests except wiring.

- **Spring Data JDBC**: swap `javax.persistence` annotations for
  `org.springframework.data.relational` ones (`@Table`, `@MappedCollection`,
  `@Embedded`), keep `PersonRepository` almost as-is (`CrudRepository` + derived
  queries), add a wrapper for `hobbies`, write the DDL. Smallest migration.
- **MyBatis**: delete the annotations, write `PersonMapper.xml` with a resultMap for
  the nested address/phones/hobbies and 7 SQL statements matching `PersonDal`'s
  methods. Most SQL to write up front.
- **jOOQ**: add codegen to the build (point it at the DDL), rewrite `PersonDal`
  against the DSL with `MULTISET` for the nested read. Best long-term query power,
  most build setup.
- **JDBI/JdbcTemplate**: rewrite `PersonDal` with SQL + row mappers and manual
  aggregation of phones/hobbies. No new dependency concepts, most hand-written code.

## Recommendation

For this project's stated preferences — Spring Data yes, ORM no:

1. **Spring Data JDBC** as the default choice. It keeps the exact programming model
   you like (repositories, derived queries, auditing), deletes the entire
   ORM/session mental model, has first-party Spring support on every Boot version in
   the upgrade path, and its one real weakness (child-collection selects on list
   queries) is fixable per-query where it matters.
2. **Add jOOQ selectively** if/when queries outgrow derived methods — the two
   coexist happily (repositories for CRUD, jOOQ for reporting/complex reads).
3. Choose **MyBatis** instead only if the team prefers *all* SQL to be explicit and
   external (or already knows MyBatis) — it's proven and popular but trades Spring
   Data ergonomics for hand-written mapping.
4. **EclipseLink** only if you discover you *do* want JPA semantics and merely
   dislike Hibernate — expect to maintain the integration yourself.
