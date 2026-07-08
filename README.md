# DGS 4 GraphQL Demo — Person Service

A demo GraphQL service built with **Netflix DGS 4.9.x** / **graphql-java 17** on
**Spring Boot 2.4.2**, **Spring Data JPA / Hibernate 5.4**, **Java 11**.

## Module layout — infrastructure vs. domain

The project is split so the generic parts can be reused for future domains:

```
dgs-demo (parent pom, dependency management, version conflict resolution)
├── graphql-infrastructure     <- domain-agnostic, reusable
│   └── com.example.infrastructure   (each package has a package-info.java)
│       ├── graphql.dispatch   the GraphQL controller layer: GraphQLDispatchController,
│       │                      GraphQLResolver + GraphQLFieldResolver contracts,
│       │                      GraphQLResolverRegistry, GraphQLOperationType
│       ├── graphql.arguments  GraphQLArgumentMapper (typed access to raw arguments)
│       ├── graphql.model      @GraphQLModel/@GraphQLEnum/@GraphQLTemporal annotations
│       │                      + AnnotatedFieldResolverFactory (field presentation)
│       ├── graphql.format     TemporalFormatter strategies (ISO/UNIX/RFC_1123)
│       ├── graphql.error      GraphQLExceptionHandler (global error boundary)
│       ├── graphql.scalars    TemporalScalar template + Date / DateTime scalars
│       ├── enums              EnumCatalog + EnumEntry (enum enrichment)
│       ├── mapping            InputMapper (declarative input->entity mapping)
│       ├── error              the error model: ApiException, ErrorCode catalog,
│       │                      ErrorDetail, EntityNotFound / DuplicateResource
│       ├── error.mapping      ExceptionMapper strategy (pluggable error translation)
│       ├── validation         JsonSchemaValidationService + SchemaValidationException
│       └── persistence        BaseEntity (id + audit timestamps)
├── graphql-playground         <- domain-agnostic, reusable
│   └── com.example.playground Self-hosted playground UI at /playground (no CDN)
├── perf-tests                 <- k6 black-box performance scenarios (see its README)
└── person-service             <- concrete Person domain, runnable Spring Boot app
    └── com.example.person
        ├── graphql.query      PersonByIdResolver, AllPersonsResolver, PersonsByCityResolver
        ├── graphql.mutation   CreatePersonResolver, UpdatePersonSalaryResolver, DeletePersonResolver
        ├── service            PersonService (orchestration + business rules),
        │                      PersonCalculations (pure math), PersonMapper (entity<->dto),
        │                      dto (views / inputs)
        ├── dal                PersonDal + PersonRepository (Spring Data JPA)
        ├── domain             Person, Address (embedded), PhoneNumber (one-to-many), enums
        └── bootstrap          DemoDataLoader (seed data)
```

To add a new domain later: add a module with its own `schema/*.graphqls` file and a set of
`GraphQLResolver` beans — nothing in `graphql-infrastructure` changes.

## The 3 layers

1. **GraphQL controller** — `GraphQLDispatchController` (infrastructure) is the single
   entry point. At startup it wires every `GraphQLResolver` bean into the schema via
   DGS's `@DgsCodeRegistry`. At request time it knows exactly which query/mutation
   arrived, logs it, and dispatches to the corresponding resolver class, which receives
   the full `DataFetchingEnvironment` (all arguments, selection set, context…).

   ```
   Received GraphQL QUERY 'allPersons', dispatching to AllPersonsResolver
   Received GraphQL MUTATION 'updatePersonSalary', dispatching to UpdatePersonSalaryResolver
   ```

2. **Service layer** — `PersonService` orchestrates and enforces business rules
   (e.g. email uniqueness); the derived fields — `fullName`, `age` (from `birthDate`),
   `yearsOfService` (from `hireDate`), `monthlyNetSalary` (annual gross → monthly after
   flat tax), `bmi` (from height/weight) — are computed by `PersonCalculations` (pure,
   unit-tested) and assembled into `PersonView` DTOs by `PersonMapper`. Failures are
   framework-neutral `ApiException`s — services never see GraphQL types.

3. **DAL** — `PersonDal` wraps `PersonRepository` (Spring Data JPA / Hibernate 5). The
   service layer never touches Spring Data directly.

## The Person entity

Simple columns (names, email, birth/hire dates, gender enum, salary, active flag,
height/weight) plus nested structures:

- `Address` — `@Embeddable` value object (nested object, stored in `address_*` columns)
- `PhoneNumber` — `@OneToMany` child entity list
- `hobbies` — `@ElementCollection` of strings

## Running it

```bash
mvn package          # build + run integration tests
java -jar person-service/target/person-service-1.0.0-SNAPSHOT.jar
```

- **Playground UI: http://localhost:8080/playground** (self-hosted, works offline)
- GraphiQL UI: http://localhost:8080/graphiql (bundled with DGS, loads assets from CDN)
- GraphQL endpoint: `POST http://localhost:8080/graphql`
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:persondb`, user `sa`)

### The playground clients

DGS does ship a playground: the starter serves **GraphiQL at `/graphiql`** out of the
box. However, that page is only a thin HTML shell that loads all of its JavaScript/CSS
from the unpkg CDN — on a machine without internet access (or behind a strict proxy)
it renders as a blank page.

The **`graphql-playground` module** therefore provides a fully self-hosted playground
at **`/playground`**: a single self-contained HTML page served by the app itself, no
external requests. It introspects whatever schema the application exposes (so it is
domain-agnostic and reusable), lists every query/mutation in a sidebar, and clicking
one inserts a ready-to-run operation with placeholder arguments and a full selection
set. Run with the button or Ctrl+Enter; a variables pane accepts JSON.

Configuration (all optional):

| Property                       | Default       | Purpose                          |
|--------------------------------|---------------|----------------------------------|
| `graphql.playground.enabled`   | `true`        | Set `false` to disable the page  |
| `graphql.playground.path`      | `/playground` | Where the UI is served           |
| `graphql.playground.endpoint`  | `/graphql`    | GraphQL endpoint the UI targets  |

Three demo persons are seeded at startup. Example query:

```graphql
{
  allPersons {
    fullName
    age
    monthlyNetSalary
    bmi
    address { city country }
    phoneNumbers { type number }
    hobbies
  }
}
```

Example mutation:

```graphql
mutation {
  createPerson(input: {
    firstName: "Linus", lastName: "Torvalds", email: "linus@example.com"
    birthDate: "1969-12-28", gender: MALE, salary: 720000
    address: { street: "Kernel Rd", houseNumber: 1, city: "Herzliya", country: "Israel" }
    phoneNumbers: [{ type: MOBILE, number: "+972-52-0000000" }]
    hobbies: ["diving"]
  }) {
    id fullName age monthlyNetSalary
  }
}
```

## Field presentation: annotations on the model

The same stored value can be exposed in different shapes without extra DTOs or manual
mapping — the model declares its presentation, and reading the class tells you which
fields return more than their raw value:

```java
@GraphQLModel("Person")
public class PersonView {
    @GraphQLTemporal   private LocalDate birthDate;   // birthDate(format: ISO|UNIX|RFC_1123)
    @GraphQLEnum("gender") private Gender gender;     // gender { code label description }
    private Integer age;                              // plain field, raw value
    ...
}
```

At startup the `AnnotatedFieldResolverFactory` turns these annotations into field
resolvers that the dispatch controller registers per schema coordinate
(`Person.gender`, `Person.birthDate`…); unannotated fields keep the default property
fetcher. One query can request several shapes at once via aliases:

```graphql
{ personById(id: "1") {
    gender { code label description }         # enriched from the EnumCatalog
    iso:  birthDate                            # "1985-12-10"
    unix: birthDate(format: UNIX)              # "503020800"
    rfc:  birthDate(format: RFC_1123)          # "Tue, 10 Dec 1985 00:00:00 GMT"
} }
```

- **Time formats** are `TemporalFormatter` strategy beans (ISO, UNIX, RFC_1123) —
  adding a format is one bean plus one enum literal in the schema.
- **Enum enrichment** resolves codes through `EnumCatalog` beans; the demo uses a
  static catalog (`PersonEnumCatalog`), swappable for an external enum service later
  without touching models or schema. The lookup runs only when the field is selected.
- Domain modules register their annotated models with one `GraphQLModelSource` bean.

## Declarative input mapping (service layer)

`InputMapper` (infrastructure) maps input DTOs onto entities via Jackson by field
name, so `PersonMapper.toEntity` is a one-liner instead of field-by-field copying.
Behavior is declared as annotations on the entity: `@JsonManagedReference` /
`@JsonBackReference` wire each nested `PhoneNumber` back to its `Person` during
mapping, `@JsonIgnore`/`@JsonAlias` are available for exclusions and renames. Null
input fields are skipped, so entity field defaults (e.g. `active = true`) survive.

## Error handling

`GraphQLExceptionHandler` (infrastructure) replaces the DGS default handler and is the
global error boundary: **any** exception thrown anywhere below the GraphQL layer lands
there.

- Known failures extend `ApiException`, which carries an `ErrorCode` — a catalog entry
  that maps to an HTTP-equivalent status and a GraphQL classification
  (`ENTITY_NOT_FOUND`→404, `SCHEMA_VALIDATION_FAILED`→400, `DUPLICATE_RESOURCE`→409,
  `INVALID_ARGUMENT`→400, `INTERNAL_ERROR`→500) — plus optional per-field
  `ErrorDetail`s.
- Unknown exceptions are logged with their stack trace and rendered as a generic
  `INTERNAL_ERROR` (500) so internals don't leak into responses.

Every GraphQL error therefore has structured `extensions`:

```json
{
  "message": "Request failed JSON schema validation against schema 'person-create' (1 violation)",
  "path": ["createPerson"],
  "extensions": {
    "literal": "SCHEMA_VALIDATION_FAILED",
    "errorType": "BAD_REQUEST",
    "httpStatus": 400,
    "timestamp": "2026-07-08T17:42:19.296Z",
    "details": [
      { "field": "$.heightCm", "constraint": "maximum",
        "reason": "$.heightCm: must have a maximum value of 260" }
    ]
  }
}
```

Layering note: services throw framework-neutral `ApiException`s and never see
GraphQL/DGS types; only the handler knows how to render them.

## JSON Schema validation (stronger than the GraphQL schema)

GraphQL's type system can't express value ranges, string patterns, array sizes or
formats — so every mutation input is also validated server-side against a JSON Schema
(draft-07, via `com.networknt:json-schema-validator`).

- Domain modules drop schemas under `classpath:json-schema/<name>.json`
  (`person-service` ships `person-create.json`: `heightCm` 50–260, `weightKg` 2–500,
  email/date formats, phone/zip patterns, hobby array 1–10 items…).
- A resolver opts in by overriding `argumentJsonSchemas()`, e.g.
  `{"input" → "person-create"}`. The **dispatch controller** runs the validation on the
  raw argument before invoking the resolver, so invalid payloads never reach the
  service layer.
- Violations become a `SCHEMA_VALIDATION_FAILED` error with one `details` entry per
  broken constraint (field path, schema keyword, human-readable reason) — see the
  example above: GraphQL happily accepted `heightCm: 300` as an `Int`; the JSON schema
  rejected it with the exact reason.

## Logging

All layers log through SLF4J: INFO for business events (operation received and
dispatched, person created/updated/deleted, schemas loaded, resolver registrations)
and DEBUG for the detailed flow (raw arguments, JSON schema payloads and results, DTO
conversions, every DAL/database call, calculated view values). Known request failures
log at WARN (no stack trace); unexpected exceptions log at ERROR with the full stack.

`application.yml` ships with `logging.level.com.example: DEBUG` so the whole flow is
visible while playing with the demo — one request produces a trace like
*dispatch → schema validation → service → DAL → computed view*. Switch it to `INFO`
for quieter output.

## Performance tests

`perf-tests/` contains k6 scenarios that treat the service as a black box over HTTP:
a smoke script plus a load script with read, write and validation-reject scenarios and
latency thresholds that fail the run when breached. They are deliberately outside the
Maven lifecycle — see [perf-tests/README.md](perf-tests/README.md) for the rationale,
how to run them, and a measured baseline. Run the service with the `perf` profile
(`--spring.profiles.active=perf`) during load tests so DEBUG logging doesn't skew
latency.

## Library conflicts and your options

This stack (Boot 2.4.2 + DGS 4) has a few version tensions. What this repo does, and
what your alternatives are:

### 1. Kotlin: Boot 2.4.2 manages 1.4.21, DGS 4.9.x needs 1.5.x  ← the real conflict
The DGS framework is written in Kotlin and DGS 4.9.25 is compiled against Kotlin
1.5.32. Spring Boot 2.4.2's dependency management pins `kotlin-stdlib` to 1.4.21, which
causes `NoSuchMethodError`s inside DGS at runtime.

- **Chosen:** override `<kotlin.version>1.5.32</kotlin.version>` in the parent POM
  (works because we inherit from `spring-boot-starter-parent`). Kotlin 1.5 is
  backwards-compatible with 1.4 bytecode, so nothing else breaks.
- Alternative: drop to DGS **4.6.x/4.7.x** (built with Kotlin 1.4, era-matched with
  Boot 2.4, uses graphql-java 16.2) and keep Boot's Kotlin version untouched.
- Alternative: upgrade Spring Boot to 2.5/2.6 (manages Kotlin 1.5), if you're not
  hard-pinned to 2.4.2.

### 2. graphql-java version is owned by the DGS BOM
DGS 4.9.x requires graphql-java **17.x** (this repo gets 17.3 from the
`graphql-dgs-platform-dependencies` BOM). Don't pin graphql-java yourself.
If another library forces a different graphql-java: 16.x → use DGS 4.8.x or lower;
18.x → use DGS 4.10.x (the last 4.x line). Mixing DGS 4.9 with graphql-java 16/18
fails at startup.

### 3. Jackson: DGS built against 2.12, Boot 2.4.2 manages 2.11.4
Boot wins and everything runs consistently on 2.11.4 (verified by the tests). If you
ever hit a Jackson-related issue with DGS, raise the whole Jackson family via Boot's
property: `<jackson-bom.version>2.12.7</jackson-bom.version>`. Never override
`jackson-databind` alone — keep core/databind/modules on one version.

### 4. SQLite vs. Hibernate 5
Hibernate 5 ships **no SQLite dialect**, so SQLite isn't a drop-in here.

- **Chosen:** H2 in-memory — in-process and zero-setup like SQLite, but with
  first-class Hibernate support. Best fit for "runs easily as a demo".
- Alternative: real SQLite file DB — add `org.xerial:sqlite-jdbc` plus a community
  dialect (e.g. `com.github.gwenn:sqlite-dialect`) and set
  `spring.jpa.properties.hibernate.dialect` accordingly. Works, but community dialects
  have quirks (limited `ALTER TABLE`, identity-generation differences).
- Alternative: Hibernate 6 ships an official `SQLiteDialect` (community package), but
  that means Spring Boot 3 / Java 17 — a different stack than requested.

### Why is Kotlin on the classpath at all? DGS is used from Java, right?
Yes — your code here is 100% Java and the DGS API is designed to be used from Java.
But the DGS framework itself is *implemented* in Kotlin, so its jars are Kotlin
bytecode and need `kotlin-stdlib` at runtime, exactly like any other transitive
library dependency (think slf4j or Jackson). You never write or compile Kotlin.
The only reason it's visible in our POM is that Spring Boot's BOM also manages the
`kotlin-stdlib` version (for projects that do use Kotlin), and Boot 2.4.2 pins an
older one (1.4.21) than DGS 4.9's bytecode requires (1.5.x) — hence the one-line
`<kotlin.version>` override.

### DAL alternatives (avoiding Hibernate 6)

If the Boot 3 upgrade should not bring Hibernate 6 along, the data access layer can
be swapped with contained effort (persistence touches only six files by design).
[docs/DAL-ALTERNATIVES.md](docs/DAL-ALTERNATIVES.md) compares Spring Data JDBC,
MyBatis, jOOQ, JDBI, plain JdbcTemplate and EclipseLink across features, ease of use,
community, popularity and performance — with a recommendation.

### Performance impact of future upgrades

Estimated per-milestone performance impact (Java LTS steps, Spring Boot 3/4,
DGS 5→12) is documented in [docs/UPGRADE-PERFORMANCE.md](docs/UPGRADE-PERFORMANCE.md),
including the recommended order and how to verify each step with the k6 suite.

### Future upgrade path: Spring Boot 2.7 + DGS 5.x
The intended pairing is Boot 2.6/2.7 with the DGS 5.x line (latest is 5.6.2; DGS 6
requires Boot 3). When you upgrade:

1. Bump the parent to `spring-boot-starter-parent:2.7.x` and `dgs.version` to 5.6.x.
2. Re-check the Kotlin override: Boot 2.7 manages Kotlin 1.6.21, while late DGS 5.x
   is built against Kotlin 1.7.x — so the same one-line `<kotlin.version>` override
   (set to what that DGS release was built with) likely stays, just with new numbers.
3. DGS 5.x moves to graphql-java 19/20 (via its BOM, as here). The APIs used in
   `graphql-infrastructure` (`@DgsCodeRegistry`, `FieldCoordinates`, `Coercing`,
   `@DgsScalar`) are stable across 4.x → 5.x, so no code changes are expected.

### 5. Building/running JDK
Sources target **Java 11** (`java.version=11`). Boot 2.4.2 + Hibernate 5.4 predate
recent JDKs, so run it on JDK 11 for fidelity (it happens to build and pass tests on
newer JDKs, but that's not a supported combination for these old versions).
