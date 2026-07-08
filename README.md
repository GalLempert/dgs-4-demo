# DGS 4 GraphQL Demo — Person Service

A demo GraphQL service built with **Netflix DGS 4.9.x** / **graphql-java 17** on
**Spring Boot 2.4.2**, **Spring Data JPA / Hibernate 5.4**, **Java 11**.

## Module layout — infrastructure vs. domain

The project is split so the generic parts can be reused for future domains:

```
dgs-demo (parent pom, dependency management, version conflict resolution)
├── graphql-infrastructure     <- domain-agnostic, reusable
│   └── com.example.infrastructure
│       ├── graphql            GraphQLDispatchController, GraphQLResolver contract,
│       │                      GraphQLResolverRegistry, GraphQLArgumentMapper
│       ├── graphql.scalars    Date / DateTime scalars
│       ├── exception          EntityNotFoundException (framework-neutral)
│       └── persistence        BaseEntity (id + audit timestamps)
└── person-service             <- concrete Person domain, runnable Spring Boot app
    └── com.example.person
        ├── graphql.query      PersonByIdResolver, AllPersonsResolver, PersonsByCityResolver
        ├── graphql.mutation   CreatePersonResolver, UpdatePersonSalaryResolver, DeletePersonResolver
        ├── service            PersonService (calculations) + dto (views / inputs)
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

2. **Service layer** — `PersonService` holds the business logic and calculates derived
   fields before returning `PersonView` DTOs: `fullName`, `age` (from `birthDate`),
   `yearsOfService` (from `hireDate`), `monthlyNetSalary` (annual gross → monthly after
   flat tax), `bmi` (from height/weight). It throws the framework-neutral
   `EntityNotFoundException`, which the dispatch controller translates into a
   `NOT_FOUND` GraphQL error — services never see GraphQL types.

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

- GraphiQL UI: http://localhost:8080/graphiql
- GraphQL endpoint: `POST http://localhost:8080/graphql`
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:persondb`, user `sa`)

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
