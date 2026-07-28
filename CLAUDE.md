# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A demo GraphQL service built with Netflix DGS 4.9.x / graphql-java 17 on Spring Boot 2.4.2, Spring Data JPA / Hibernate 5.4, Java 11. Multi-module Maven project split into a reusable, domain-agnostic GraphQL infrastructure and two concrete domains (Person, plus a deliberately minimal Company domain that demonstrates the reuse).

## Commands

```bash
mvn package                          # build everything + run all tests
mvn test                             # run all tests (unit + Spring Boot integration tests, all via surefire)
mvn test -pl person-service -am      # tests for one module (-am builds required sibling modules)
mvn test -pl person-service -am -Dtest=PersonGraphQLIntegrationTest          # single test class
mvn test -pl person-service -am -Dtest=PersonCalculationsTest#ageIsWholeYearsSinceBirthDate  # single test method
java -jar person-service/target/person-service-1.0.0-SNAPSHOT.jar            # run the app
```

- Playground UI: http://localhost:8080/playground (self-hosted, offline); GraphiQL at /graphiql (needs CDN access); endpoint `POST /graphql`; H2 console at /h2-console (JDBC URL `jdbc:h2:mem:persondb`, user `sa`).
- Sources target **Java 11** (`java.version=11` in the parent POM). The stack predates recent JDKs; JDK 11 is the supported toolchain, though it happens to build on newer ones.
- Performance tests (`perf-tests/k6/`) are k6 scripts deliberately outside the Maven lifecycle — see `perf-tests/README.md`. Run the service with `--spring.profiles.active=perf` during load tests so DEBUG logging doesn't skew latency.

## Architecture

Detailed docs exist and should be consulted before structural changes: `docs/ARCHITECTURE.md` (module split, startup wiring, life of a request), `docs/EXTENDING.md` (step-by-step recipes for adding fields, filters, predicates, error codes, whole domains), `docs/FILTERING.md`, `docs/FILTER-COMPOSITION.md` (designed but NOT yet implemented), `docs/REPLICATION.md` (sequence-based replication feed: `personsBySequence`, soft deletes, filtered replication), `docs/DAL-ALTERNATIVES.md`, `docs/UPGRADE-PERFORMANCE.md`.

### Module split (the core invariant)

- `graphql-infrastructure` — domain-agnostic library. **Must compile and make sense with zero knowledge of any domain.** Ships contracts (`GraphQLResolver`, `FilterPredicateStrategy`, `EnumCatalog`, `ExceptionMapper`), machinery (dispatch, JSON-schema validation, filtering, declarative mapping, error rendering), and shared schema `schema/common.graphqls` (DGS merges every `schema/*.graphqls` on the classpath, including inside jars).
- `graphql-playground` — domain-agnostic self-hosted playground UI.
- `company-service` — second, deliberately minimal domain proving the reuse: its four standard queries (filtered list, replication feed, count, max sequence) are entirely inherited — `CompanyRepository`/`CompanyDal` are empty subclasses, `CompanyService` only supplies entity→view mapping, and `CompanyGraphQLConfig` registers four factory-made resolver beans. Its schema uses `extend type Query`/`extend type Mutation` (only the hosting app's domain declares the base types).
- `person-service` — the runnable Spring Boot app (composes `company-service` in; its application class widens `@EntityScan`/`@EnableJpaRepositories` to `com.example` so sibling domain modules are discovered): entities, DTOs, `PersonService`, thin resolvers, seed data. A new domain is added as another module contributing beans + its own `schema/*.graphqls`; the infrastructure discovers them via Spring DI and nothing in it changes.
- **Replicated resources**: entities that extend `ReplicatedEntity` get the whole replication stack (see `docs/REPLICATION.md`) from the infrastructure `replication` package: `ReplicatedRepository` → `ReplicatedDal` → `ReplicatedResourceService` → `ReplicationResolverFactory`. A domain wires them with empty/near-empty subclasses plus one `@Bean` per standard query field. Every write stamps a fresh per-table sequence; deletes are soft (`softDelete`) and regular reads exclude deleted rows.
- Every infrastructure package has a `package-info.java` stating its purpose — keep these current.

### The three layers and their boundary rules

1. **GraphQL controller** — `GraphQLDispatchController` (infrastructure) is the single entry point. At startup its `@DgsCodeRegistry` method wires every `GraphQLResolver` bean to its schema coordinate (verifying the coordinate exists in the SDL — a typo fails boot). At request time it logs the operation, runs JSON-schema validation on raw arguments, and dispatches to the resolver.
2. **Service** — `PersonService` (business rules), `PersonCalculations` (pure derived values: age, BMI, monthlyNetSalary…), `PersonMapper` (entity↔DTO via `DeclarativeMapper`). **GraphQL/DGS types stop at layer 1** — services see only DTOs, `FilterCriteria`, and framework-neutral `ApiException`s.
3. **DAL** — `PersonDal` is the only class touching `PersonRepository`. **Spring Data stops at layer 3.** Builds dynamic WHERE clauses from `FilterCriteria` via `FilterSpecificationBuilder` and enforces the result cap (COUNT first; over `graphql.query.max-results`, default 100, → `RESULT_SET_TOO_LARGE` 422 before fetching).

Startup is fail-fast by design: strategy registries (`GraphQLResolverRegistry`, `FilterPredicateRegistry`, `TemporalFormatterRegistry`) index beans via `UniqueIndex`, which throws on duplicate keys; invalid presentation annotations fail boot.

### Key mechanisms (all extension points are Spring beans)

- **Field presentation**: `@GraphQLModel`/`@GraphQLEnum`/`@GraphQLTemporal` annotations on view DTOs; `AnnotatedFieldResolverFactory` turns them into per-coordinate field resolvers at startup. Domain modules register models with one `GraphQLModelSource` bean.
- **Declarative mapping**: `DeclarativeMapper` copies same-shaped objects by field name via Jackson in both directions. A simple field (same on GraphQL + DB) needs zero mapping code — just schema line + entity field + input/view field. Nulls are skipped so field defaults survive.
- **Filtering**: resolver parses raw args with `FilterParser` into a `FilterCriteria` tree (nested inputs become dotted paths like `address.city`); each predicate is a `FilterPredicateStrategy` bean — adding one is one bean plus one schema field. Multiple filters combine with AND only (explicit and/or/not is designed in docs but not implemented).
- **Validation**: mutation inputs are validated against JSON Schemas in `classpath:json-schema/<name>.json`; a resolver opts in via `argumentJsonSchemas()`. Validation runs in the dispatch controller before the resolver.
- **Errors**: `GraphQLExceptionHandler` is the global boundary. Known failures extend `ApiException` carrying an `ErrorCode` (maps to HTTP status + GraphQL classification + structured `extensions`); unknown exceptions render as generic `INTERNAL_ERROR` so internals don't leak. Third-party exceptions are translated via pluggable `ExceptionMapper` beans.

## Version-conflict landmines (do not "fix" these)

The parent POM deliberately resolves several tensions — see README "Library conflicts and your options" before touching versions:

- `<kotlin.version>1.5.32</kotlin.version>` override is required: Boot 2.4.2 pins Kotlin 1.4.21 but DGS 4.9.x is Kotlin-1.5 bytecode; removing it causes `NoSuchMethodError` at runtime. (The project is 100% Java — Kotlin is only a transitive DGS runtime dependency.)
- graphql-java's version is owned by the DGS BOM (`graphql-dgs-platform-dependencies`). Never pin graphql-java directly.
- Jackson runs on Boot-managed 2.11.4; if it must be raised, use Boot's `<jackson-bom.version>` property — never override `jackson-databind` alone.
- H2 was chosen over SQLite because Hibernate 5 has no SQLite dialect.
