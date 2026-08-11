# The lite track: a bare-minimum DGS replacement for an in-house GraphQL layer

The main framework in this repository (`graphql-infrastructure` + person/company
services) demonstrates a *full* GraphQL stack: filtering, replication, standard
mutations, JSON-schema validation, declarative mapping, structured errors. When the
goal is only to **replace an in-house graphql-java-annotations wrapper with DGS**,
most of that is noise. The lite track is the same idea reduced to its essentials —
three modules that live entirely beside the main ones and share no code with them:

| Module | Role | Port |
|---|---|---|
| `graphql-infrastructure-lite` | The reusable "how": four classes, no domain knowledge | — |
| `person-service-lite` | First consumer; models a service *migrating* off the old library (legacy fetchers reused unchanged) | 8082 |
| `company-service-lite` | Second consumer; models the *end state* (operations wired as lambdas, zero fetcher classes) | 8083 |

The infrastructure–service relationship is identical to the main track's: the lite
infrastructure compiles with zero knowledge of any domain, and each service is a
standalone Spring Boot app with its own schema, own API, own process. Adding a third
lite service means copying the company-service-lite shape; nothing in the
infrastructure changes.

## The three guarantees (the "same principles" as the old library)

1. **On startup, the schema is loaded.** The DGS starter collects every
   `classpath:schema/*.graphqls` file (including inside jars) and builds the
   executable schema. Schema-first replaces the old code-first generation: the SDL
   file is now the single source of truth. In a migration you print it **once** from
   the running legacy service (introspection query or graphql-java's
   `SchemaPrinter`) rather than writing it by hand.

2. **The schema is supplied via API.** DGS serves `POST /graphql` (path configurable
   with `dgs.graphql.path`), full introspection, and GraphiQL at `/graphiql`. The
   lite track adds no playground dependency — the main track shows how if wanted.

3. **A controller dispatches to your resolvers.** `GraphQLDispatchController` is the
   single entry point. At startup it wires every `GraphQLResolver` bean to its schema
   coordinate — **verifying the coordinate exists in the SDL, so a typo fails boot**,
   not a production request. At request time it logs the operation and dispatches;
   the resolver calls your existing service and DAL layers, which stay untouched.

## The whole infrastructure — four classes

```
com.example.lite.graphql
├── GraphQLResolver           the one contract: parentType() + fieldName() + resolve(env)
├── GraphQLResolvers          adapters: query()/mutation()/field() wrap any existing DataFetcher
├── GraphQLResolverRegistry   collects resolver beans, fails boot on duplicate coordinates
└── GraphQLDispatchController @DgsCodeRegistry wiring + request-time dispatch + logging
```

There is deliberately no second contract for type fields (the main track's
`GraphQLFieldResolver`): `parentType()` already says whether the resolver serves
`Query`, `Mutation`, or a field of an object type like `Person.fullName`.

## Wiring your existing resolvers

Your fetcher classes already implement `graphql.schema.DataFetcher` — that is the
interface graphql-java-annotations runs them through. They are reused unchanged;
what replaces the old annotated Query/Mutation registry classes is one small
`@Configuration` with one bean per operation:

```java
@Bean
GraphQLResolver personById(PersonService service) {
    return GraphQLResolvers.query("personById", new PersonByIdFetcher(service));
}

@Bean
GraphQLResolver createPerson(PersonService service) {
    return GraphQLResolvers.mutation("createPerson", new CreatePersonFetcher(service));
}

// the @GraphQLDataFetcher-on-an-entity-field case:
@Bean
GraphQLResolver personFullName(PersonService service) {
    return GraphQLResolvers.field("Person", "fullName",
            env -> service.fullNameOf(env.getSource()));
}
```

Fields without a fetcher need nothing at all — graphql-java reads them from the
getter of the same name, exactly as before. New operations written after the
migration can skip fetcher classes entirely: implement `GraphQLResolver` directly as
a `@Component` (see `PersonCountResolver`) or pass a lambda to `GraphQLResolvers`
(see `CompanyLiteGraphQLConfig`, where the whole service is wired this way).

## Standing up a new service on the module

1. Depend on `graphql-infrastructure-lite` (plus `spring-boot-starter-web`).
2. Put your SDL in `src/main/resources/schema/*.graphqls`, declaring your own
   `type Query` / `type Mutation`.
3. Scan the infrastructure package in your application class:
   `@SpringBootApplication(scanBasePackages = "com.example.lite")` — or list your
   package and `com.example.lite.graphql`.
4. Declare one `GraphQLResolver` bean per operation, as above.

That is the complete checklist — `company-service-lite` is the living proof, and the
shape to copy.

## What was deliberately left out, and where to find it

Each omission exists fully built in the main track; the lite track stays useful
precisely because it refuses them:

| Concern | Lite behavior | Full version |
|---|---|---|
| Error rendering | DGS default handler: `INTERNAL` error whose message **includes the exception class and message** — register a DGS `DataFetcherExceptionHandler` bean if internals must not reach clients | `GraphQLExceptionHandler` + `ApiException`/`ErrorCode` (`graphql-infrastructure`) |
| Input validation | GraphQL type system only (non-null, enums) | JSON-schema validation in the dispatch controller (`docs/ARCHITECTURE.md`) |
| Filtering | Write your own arguments per query | `FilterCriteria` + predicate strategy beans (`docs/FILTERING.md`) |
| Replication / soft deletes | — | `docs/REPLICATION.md` |
| Standard CRUD factories | Hand-wire each operation | `ReplicationResolverFactory` / `MutationResolverFactory` (`docs/MUTATIONS.md`) |
| Entity↔DTO mapping | Resolvers return domain POJOs directly | `DeclarativeMapper`, view DTOs (`docs/ARCHITECTURE.md`) |
| Enum/temporal presentation | Plain getter values | `@GraphQLModel`/`@GraphQLEnum`/`@GraphQLTemporal` |
| Custom scalars (`Long`, `Date`…) | Built-in scalars only | `graphql.scalars` package + `common.graphqls` |
| Persistence base (`BaseEntity`, JPA) | None — bring your own DAL | `replication`/`persistence` packages |

If one of these becomes necessary later, port the corresponding package across (they
are all self-contained Spring beans) or move the service onto the full
`graphql-infrastructure` — the resolver contract is deliberately near-identical, so
the move is mechanical.

## Relationship to the migration guide

`docs/MIGRATION-FROM-GRAPHQL-JAVA-ANNOTATIONS.md` describes migrating onto the
**full** framework; every step of its §5.1 adapter route applies to the lite track
verbatim with one substitution: `DataFetcherAdapters.query(...)` becomes
`GraphQLResolvers.query(...)` (and there is no `.validating(...)`, because the lite
track has no JSON-schema validation). The fundamental shift — code-first to
schema-first — is the same, and §3 (printing the SDL from the old schema) is the
recommended way to produce the `.graphqls` file.
