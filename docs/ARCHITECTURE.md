# Architecture — in depth

This document explains how the service is put together and *why*: the module split,
the three layers, what happens at startup, the full life of a request, and the design
principles that keep the codebase uniform. For hands-on recipes ("how do I add a
field / predicate / domain?") see [EXTENDING.md](EXTENDING.md).

---

## 1. Modules: infrastructure vs. domain

```
dgs-demo (parent POM: dependency management, version-conflict resolution)
│
├── graphql-infrastructure      domain-agnostic library. Knows NOTHING about Person.
├── graphql-playground          domain-agnostic self-hosted playground UI (/playground)
├── person-service              the concrete domain + the runnable Spring Boot app
└── perf-tests                  k6 black-box load scenarios (not a Maven module)
```

The rule that keeps the split honest: **`graphql-infrastructure` must compile and make
sense with zero knowledge of any domain.** It ships contracts (`GraphQLResolver`,
`FilterPredicateStrategy`, `EnumCatalog`, `ExceptionMapper`…), machinery
(dispatch, validation, filtering, mapping, error rendering) and even shared schema
(`schema/common.graphqls` — DGS merges every `schema/*.graphqls` found on the
classpath, including inside jars). A domain module contributes *beans and schema
files*; the infrastructure discovers them through Spring's dependency injection.

`person-service` is deliberately boring: entities, DTOs, a service with business
rules, thin resolvers, seed data. That's the point — everything clever lives once, in
the infrastructure.

### Package map (infrastructure)

| Package | Responsibility |
|---|---|
| `graphql.dispatch` | the controller layer: resolver contracts, registry, dispatch controller |
| `graphql.arguments` | typed access to raw GraphQL arguments |
| `graphql.model` | `@GraphQLModel` / `@GraphQLEnum` / `@GraphQLTemporal` + the factory that turns them into field resolvers |
| `graphql.format` | `TemporalFormatter` strategies (ISO / UNIX / RFC_1123) |
| `graphql.scalars` | `Date` / `DateTime` scalar coercion (template + two subclasses) |
| `graphql.error` | the global `DataFetcherExceptionHandler` |
| `error` | the shared error model: `ApiException`, `ErrorCode`, `ErrorDetail`, concrete exceptions |
| `error.mapping` | `ExceptionMapper` strategy — pluggable exception→ApiException translation |
| `filter` | filter model, parser, JPA `Specification` builder, predicate strategies, result cap |
| `validation` | JSON Schema validation (`classpath:json-schema/*.json`) |
| `mapping` | `DeclarativeMapper` — Jackson-based same-shape object copying |
| `enums` | `EnumCatalog` / `EnumEntry` — enum enrichment source |
| `persistence` | `BaseEntity` (surrogate id + audit timestamps) |
| `support` | shared building blocks (`UniqueIndex` for fail-fast registries) |

Every package has a `package-info.java` restating its purpose next to the code.

---

## 2. The three layers

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. GraphQL controller (infrastructure: graphql.dispatch)        │
│    GraphQLDispatchController — the single entry point.          │
│    Knows WHICH operation arrived; logs it; runs JSON-schema     │
│    validation; dispatches to the matching resolver bean.        │
│    Domain resolvers translate GraphQL args -> service calls.    │
├─────────────────────────────────────────────────────────────────┤
│ 2. Service (domain: person.service)                             │
│    PersonService — orchestration + business rules (uniqueness). │
│    PersonCalculations — pure derived values (age, BMI, …).      │
│    PersonMapper — entity <-> DTO via DeclarativeMapper.         │
│    Sees FilterCriteria and ApiExceptions, NEVER GraphQL types.  │
├─────────────────────────────────────────────────────────────────┤
│ 3. DAL (domain: person.dal)                                     │
│    PersonDal — the only class that touches the repository.      │
│    Builds dynamic WHERE clauses from FilterCriteria; enforces   │
│    the result cap (COUNT first). PersonRepository = Spring Data │
│    JPA + JpaSpecificationExecutor.                              │
└─────────────────────────────────────────────────────────────────┘
```

Two boundary rules make the layering real, not decorative:

- **GraphQL types stop at layer 1.** Resolvers convert `DataFetchingEnvironment`
  content into DTOs (`CreatePersonInput`), primitives, or `FilterCriteria` before
  calling the service. If DGS were swapped for another transport tomorrow, layers 2–3
  would not change.
- **Spring Data stops at layer 3.** The service depends on `PersonDal`, never on the
  repository. Swapping JPA for Spring Data JDBC / jOOQ (see
  [DAL-ALTERNATIVES.md](DAL-ALTERNATIVES.md)) touches six files.

Exceptions cross layers in one direction only: services and DAL throw framework-
neutral `ApiException` subclasses; only the GraphQL error boundary knows how to render
them (see §5).

---

## 3. What happens at startup

Order matters here; all of it is fail-fast — a misconfigured resolver stops the app
from booting rather than failing at request time.

1. **Component scan** (`scanBasePackages = "com.example"`) picks up infrastructure and
   domain beans alike.
2. **Registries index their strategies** (constructor injection of `List<T>`):
   `GraphQLResolverRegistry` (operations + field resolvers),
   `FilterPredicateRegistry`, `TemporalFormatterRegistry` — all via
   `UniqueIndex.byKey(...)`, which throws on duplicate keys naming both classes.
3. **Annotation scan**: `AnnotatedFieldResolverFactory` reads every class registered
   through a `GraphQLModelSource` bean, validates the presentation annotations
   (`@GraphQLTemporal` on a non-temporal field → boot failure) and generates
   `GraphQLFieldResolver`s from them.
4. **JSON schemas load** from `classpath*:json-schema/*.json` into
   `JsonSchemaValidationService`, keyed by file name.
5. **DGS builds the executable schema**: it merges every `schema/*.graphqls` on the
   classpath (the infrastructure's `common.graphqls` + each domain's files), then
   calls the `@DgsCodeRegistry` method on `GraphQLDispatchController`, which registers
   one `DataFetcher` per resolver at its `FieldCoordinates` — after verifying the
   coordinate actually exists in the SDL (typo in a resolver → boot failure).
6. **Seed data** loads through the real service layer (`DemoDataLoader`), so startup
   exercises the same code path as the `createPerson` mutation.

---

## 4. The life of a request

### A filtered query: `persons(filter: {...}) { fullName gender { label } iso: birthDate }`

```
HTTP POST /graphql
  │
  ▼
DGS / graphql-java            parse, validate against schema, plan execution
  │
  ▼
GraphQLDispatchController     "Received GraphQL QUERY 'persons'" (INFO)
  │                           no JSON schemas declared for this resolver -> skip
  ▼
PersonsResolver               FilterParser.parse(raw filter map)
  │                             -> FilterCriteria [address.city equals ..., AND]
  ▼
PersonService.findPersons     @Transactional(readOnly)
  │
  ▼
PersonDal.findAll(criteria)   FilterSpecificationBuilder -> JPA Specification
  │                           COUNT with the same WHERE ── over cap? ──> throw
  │                           TooManyResultsException (422) ── else fetch
  ▼
PersonMapper.toView           DeclarativeMapper copies same-named fields;
  │                           PersonCalculations fills age/bmi/…
  ▼
graphql-java completion       per selected field:
  │                             fullName        -> property fetcher on PersonView
  │                             gender          -> EnumEnrichmentResolver (catalog lookup)
  │                             iso: birthDate  -> FormattedTemporalResolver (ISO)
  ▼
HTTP 200 with data
```

### A mutation: `createPerson(input: {...})`

Same shape, plus two validation stations *before* the resolver body runs:

1. GraphQL type check (schema): wrong types/missing required fields never enter.
2. **JSON Schema validation** (dispatch controller): the resolver declared
   `argumentJsonSchemas() = {input -> person-create}`, so the raw argument map is
   validated against `json-schema/person-create.json`. Ranges, patterns and sizes the
   GraphQL type system cannot express are rejected here with one `details` entry per
   violated constraint — the service layer never sees invalid payloads.

Then: `CreatePersonInput` via `GraphQLArgumentMapper` → `PersonService.createPerson`
(uniqueness rule) → `PersonMapper.toEntity` (declarative, `@JsonManagedReference`
wires phone back-references) → `PersonDal.save` → view back through the same
presentation pipeline.

### When anything above throws

Every exception funnels into `GraphQLExceptionHandler` (the DGS handler bean):

```
exception ──unwrap (CompletionException…)──> ExceptionMapper strategies (first match)
   │                                              │ none matched
   ▼                                              ▼
ApiException (code, details)              generic INTERNAL_ERROR (500),
   │                                      stack trace logged, internals hidden
   ▼
GraphQL error with extensions:
  literal, errorType, httpStatus, timestamp, details[]
```

`ErrorCode` owns its own semantics (`httpStatus()`, `graphqlErrorType()`,
`isServerFault()` deciding warn-vs-error logging) — call sites never compare enum
identities.

---

## 5. Design principles (and where to see them)

| Principle | Where it shows |
|---|---|
| **Strategy beans for open sets** | `GraphQLResolver`, `GraphQLFieldResolver`, `FilterPredicateStrategy`, `TemporalFormatter`, `ExceptionMapper`, `EnumCatalog` — domains extend by adding beans, never by editing infrastructure |
| **Enum-as-strategy for closed sets** | `FilterCriteria.Combinator` (AND/OR implement `combine()` themselves), `ErrorCode.isServerFault()` — no switch statements, no enum-identity conditionals |
| **Fail fast at startup** | duplicate registry keys, resolvers targeting undeclared schema fields, invalid presentation annotations — all boot failures, not request-time surprises |
| **No `instanceof`** | `Class::isInstance` + typed lookups (`TypeDefinitionRegistry.getType(name, type)`, `Path.getJavaType()`) everywhere a type decision is needed |
| **Constructor injection only** | every component; registries receive `List<Strategy>` |
| **Declarative over imperative** | field presentation via annotations, mapping via Jackson, validation via JSON schema files, filters via schema-typed inputs |
| **One concern per package** | see the package map; each has `package-info.java` |

## 6. Configuration reference

| Property | Default | Meaning |
|---|---|---|
| `graphql.query.max-results` | `100` | result cap for non-paginated list queries (COUNT-first) |
| `graphql.playground.enabled` | `true` | serve the self-hosted playground |
| `graphql.playground.path` | `/playground` | playground URL |
| `graphql.playground.endpoint` | `/graphql` | endpoint the playground targets |
| `logging.level.com.example` | `DEBUG` (demo) | `INFO` for business events only |
| Spring profile `perf` | off | INFO logging for load testing (`--spring.profiles.active=perf`) |

## 7. Testing strategy

- **Infrastructure unit tests** (95, no Spring context): every contract and strategy
  in isolation — parser structures, predicate coercion, scalar coercion edge cases,
  error rendering with mocked handler parameters, registries' duplicate detection,
  annotation misuse failing fast.
- **Domain integration tests** (38, `@SpringBootTest` + `DgsQueryExecutor`): real
  GraphQL execution against H2 — CRUD, filtering scenarios (nested paths, enum and
  BigDecimal coercion), the result cap with a lowered limit, structured errors,
  field presentation, zero-mapping field flow.
- **Black-box performance tests** (k6, outside the Maven lifecycle): latency
  thresholds as a contract; see [../perf-tests/README.md](../perf-tests/README.md).

A note from experience: `@SpringBootTest` classes share one context and one H2, so
integration tests that create rows affect siblings — write assertions with
`contains`/`doesNotContain` on known seeds, not exact counts.
