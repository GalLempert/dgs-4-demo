# `graphql-infrastructure-lite` — the class-by-class internals guide

`docs/LITE.md` explains *why* the lite track exists and *what* it leaves out. This
document is the reference for *how it works*: every class, every method, every data
member of the `graphql-infrastructure-lite` module, plus the exact points where it
hands over to Netflix DGS and graphql-java. Everything stated about DGS behavior
below was verified against the bytecode of the resolved versions (DGS 4.9.25,
graphql-java 17.3), not quoted from memory — see §6.

Read it when you need to know precisely what happens when a bean is registered,
why boot fails with a given message, what a resolver may return, or how to extend
the module without breaking its one invariant: *zero domain knowledge*.

---

## 1. The module in one screen

```
graphql-infrastructure-lite
├── pom.xml                                   depends on graphql-dgs-spring-boot-starter
│                                             (+ spring-boot-starter for @Component etc.)
└── src/main/java/com/example/lite/graphql
    ├── package-info.java                     the lifecycle in three steps (javadoc only)
    ├── GraphQLResolver.java                  interface — the single contract
    ├── GraphQLResolvers.java                 final utility — DataFetcher → GraphQLResolver adapters
    ├── GraphQLResolverRegistry.java          @Component — collects + indexes resolver beans
    └── GraphQLDispatchController.java        @DgsComponent — startup wiring + request dispatch
```

Four production classes, two of which hold state (the registry's map, the
controller's reference to the registry). No configuration properties, no custom
annotations, no scalars, no persistence. Everything the module does *not* do is
done by DGS: schema loading, HTTP, execution, error rendering, GraphiQL.

### 1.1 Dependencies and what each one contributes

| Dependency (from `pom.xml`) | Scope | What the lite module takes from it |
|---|---|---|
| `com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter` | compile | `@DgsComponent`, `@DgsCodeRegistry`, the schema loader, `POST /graphql`, GraphiQL, `DgsQueryExecutor` (used by consumer tests). Transitively brings graphql-java (`DataFetcher`, `DataFetchingEnvironment`, `FieldCoordinates`, `GraphQLCodeRegistry`, `TypeDefinitionRegistry`, `ObjectTypeDefinition`) and SLF4J. |
| `org.springframework.boot:spring-boot-starter` | compile | `@Component` and constructor injection for the registry. The DGS starter does not expose `spring-context` at compile scope, hence the explicit dependency. |
| `org.springframework.boot:spring-boot-starter-test` | test | JUnit 5, AssertJ, Mockito for the three unit test classes. |

Versions are owned by the parent POM (DGS BOM manages graphql-java; see the
"Version-conflict landmines" section of `CLAUDE.md` before touching any of them).

### 1.2 The two lifecycles the module implements

**Startup** (once, while DGS builds the executable schema):

```
Spring context refresh
  └─ GraphQLResolverRegistry(List<GraphQLResolver>)          ① index every resolver bean by "Parent.field"
       └─ throws IllegalStateException on a duplicate coordinate → boot fails
  └─ GraphQLDispatchController(registry)                      ② plain constructor injection
DGS DgsSchemaProvider.schema()
  ├─ finds classpath*:schema/**/*.graphql*, parses + merges them
  ├─ registers DGS-native things (@DgsScalar, @DgsDirective, @DgsData, @DgsTypeResolver)
  └─ calls every @DgsCodeRegistry method on every @DgsComponent bean
       └─ GraphQLDispatchController.registerResolvers(builder, typeRegistry)   ③
            for each resolver:
              verifyFieldExistsInSchema  → IllegalStateException on a typo → boot fails
              codeRegistryBuilder.dataFetcher(coords, env -> dispatch(resolver, env))
              log.info("Registered GraphQL resolver 'Query.personById' -> ...")
```

**Request** (per field, per request, on DGS's execution threads):

```
POST /graphql  →  DGS  →  graphql-java execution
  └─ for each selected field with a registered fetcher:
       lambda registered in ③
         └─ GraphQLDispatchController.dispatch(resolver, environment)
              ├─ log (INFO for Query/Mutation fields, DEBUG otherwise)
              └─ resolver.resolve(environment)
                   └─ your code (a GraphQLResolver implementation, or the wrapped DataFetcher)
```

That is the complete runtime surface. The rest of this document walks it member by
member.

---

## 2. `GraphQLResolver` — the contract

```java
public interface GraphQLResolver {
    String parentType();
    String fieldName();
    Object resolve(DataFetchingEnvironment environment) throws Exception;
    default String description() { return getClass().getSimpleName(); }
}
```

A `GraphQLResolver` is *one schema coordinate* plus *the code that resolves it*. A
coordinate is a `(parent type, field name)` pair: `Query.personById`,
`Mutation.createPerson`, `Person.fullName`. There is deliberately only one contract
for root operations and for fields of object types: `parentType()` distinguishes
them. (The full track has a second interface, `GraphQLFieldResolver`, for the latter;
the lite track folds it in.)

Every Spring bean implementing this interface is discovered automatically (§4). No
annotation on the implementation is needed beyond whatever makes it a bean.

### 2.1 `String parentType()`

The name of the GraphQL **object type** that owns the field, exactly as spelled in
the SDL. Returns `"Query"` or `"Mutation"` for operations, or any object type name
(`"Person"`) for a computed field of that type.

Rules that follow from how the controller uses it (§5.4, §5.5):

- It is matched **case-sensitively** against the SDL.
- It must name an *object* type (`type X { ... }`). Interfaces, unions, input types,
  scalars and enums are not object types; a resolver naming one fails boot with the
  "no such field is declared" error, even if the interface declares that field. Put
  the resolver on each concrete implementing type instead.
- `"Query"` and `"Mutation"` are special **only for logging**: the controller logs
  operations on those two types at INFO and everything else at DEBUG. A schema that
  renames its roots (`schema { query: Root }`) still works, the operations just log
  quietly. `"Subscription"` is treated like an ordinary object type (DEBUG logging);
  nothing else in the lite track supports subscriptions, and DGS subscription
  transport is not configured.

### 2.2 `String fieldName()`

The field's name on that type, exactly as in the SDL (`"personById"`). Also matched
case-sensitively. Together with `parentType()` it forms the registry key
`parentType + "." + fieldName` (§4.2) and the graphql-java
`FieldCoordinates` (§5.3).

### 2.3 `Object resolve(DataFetchingEnvironment environment) throws Exception`

Executes the operation or computes the field for one request. It is called on
graphql-java's execution path, once per occurrence of the field in the result — once
for a root query, once *per row* for a field of a list element type.

What the method receives — the full `DataFetchingEnvironment`:

| Accessor | Meaning in practice |
|---|---|
| `getArgument("name")` / `getArguments()` | Coerced argument values. GraphQL `ID` arrives as a `String`; `Int` as `Integer`; input objects as `Map<String, Object>`; lists as `List`. The lite track performs no further parsing: converting an `ID` to `long` is the resolver's job (see `PersonByIdFetcher` in person-service-lite). |
| `getSource()` | The parent object. `null` for root fields; for `Person.fullName` it is the `Person` the enclosing resolver returned. |
| `getSelectionSet()` | The sub-fields the client asked for, if you want to optimize fetching. |
| `getContext()` / `getGraphQlContext()` | DGS puts the request context (headers, etc.) here. |
| `getField()`, `getFieldDefinition()`, `getExecutionStepInfo()` | Static schema information about the field being resolved. |

What the method may return — anything graphql-java accepts from a `DataFetcher`,
because the controller passes the value through untouched:

- a plain value (`String`, `Integer`, `Boolean`, a domain POJO, a `List`);
  object fields are then read by graphql-java's default `PropertyDataFetcher`
  (getter of the same name, or a `Map` key);
- `null`, which graphql-java renders as `null` or, for a non-null field, as a
  "Cannot return null for non-nullable type" error that bubbles up to the nearest
  nullable parent;
- a `CompletionStage`/`CompletableFuture` for asynchronous resolution;
- a `graphql.execution.DataFetcherResult` carrying data plus partial errors.

What the method may throw: anything. The signature declares `throws Exception`, the
same as `DataFetcher.get`, so checked exceptions need no wrapping. The lite track has
**no error boundary** — see §7 for exactly how DGS renders a thrown exception.

Threading: DGS can execute independent fields concurrently, and the same resolver
instance serves every request. Implementations must be stateless or thread-safe.
Every resolver in both demo services is a stateless delegate to a service bean.

### 2.4 `default String description()`

Purely cosmetic: the label used in the startup line "Registered GraphQL resolver
'Query.personById' -> …", in the per-request dispatch log line, and in both
fail-fast error messages. The default returns the implementing class's simple
name, which is right for hand-written resolvers (`PersonCountResolver`). The
adapters in `GraphQLResolvers` override it (§3.4) because every adapter is the same
anonymous class, whose simple name is the empty string.

Override it when a single class serves several coordinates (e.g. a generic resolver
parameterized by constructor) and the class name alone would not identify which
instance a log line refers to.

---

## 3. `GraphQLResolvers` — adapters from `DataFetcher`

```java
public final class GraphQLResolvers {
    private static final String QUERY_TYPE = "Query";
    private static final String MUTATION_TYPE = "Mutation";
    private GraphQLResolvers() {}
    public static GraphQLResolver query(String fieldName, DataFetcher<?> dataFetcher)
    public static GraphQLResolver mutation(String fieldName, DataFetcher<?> dataFetcher)
    public static GraphQLResolver field(String parentType, String fieldName, DataFetcher<?> dataFetcher)
    private static String fetcherName(DataFetcher<?> dataFetcher)
}
```

A stateless utility class (`final`, private constructor, static methods only). Its
job is the migration bridge: graphql-java-annotations runs fetchers through
`graphql.schema.DataFetcher`, so a migrating service already has classes
implementing that interface. Wrapping one in a `GraphQLResolver` costs one line and
lets the class stay untouched. The lite counterpart of the full track's
`DataFetcherAdapters`, minus the `.validating(...)` option (no JSON-schema
validation here).

### 3.1 `QUERY_TYPE = "Query"`, `MUTATION_TYPE = "Mutation"`

The two root type names the convenience factories bake in. They match the names
the controller's `isRootOperation` checks (§5.4), so an adapter made by `query()` or
`mutation()` always logs at INFO.

### 3.2 `static GraphQLResolver query(String fieldName, DataFetcher<?> dataFetcher)`

Delegates to `field("Query", fieldName, dataFetcher)`. Use it for every field of the
`Query` root type.

### 3.3 `static GraphQLResolver mutation(String fieldName, DataFetcher<?> dataFetcher)`

Delegates to `field("Mutation", fieldName, dataFetcher)`. Use it for every field of
the `Mutation` root type.

### 3.4 `static GraphQLResolver field(String parentType, String fieldName, DataFetcher<?> dataFetcher)`

The general factory and the only one with logic. Step by step:

1. `Objects.requireNonNull` on all three arguments. A `null` fails immediately with a
   `NullPointerException` naming the parameter (`"parentType"`, `"fieldName"`,
   `"dataFetcher"`) — at bean-creation time, i.e. at boot, not at the first request.
   Empty strings are *not* rejected here; they fail later in schema verification
   (§5.5) because no SDL field has an empty name.
2. Returns an **anonymous `GraphQLResolver`** that captures the three arguments.
   Its members:

   | Member | Behavior |
   |---|---|
   | `parentType()` | returns the captured `parentType` |
   | `fieldName()` | returns the captured `fieldName` |
   | `resolve(environment)` | `return dataFetcher.get(environment);` — a pure pass-through, same environment object, same return value, same exceptions. The unit test `adaptedFetcherReceivesTheSameEnvironment` pins this. |
   | `description()` | `"adapter of " + fetcherName(dataFetcher)` — overridden because the anonymous class has no useful simple name |

   The `DataFetcher<?>` wildcard means any fetcher type parameter is accepted
   (`DataFetcher<Person>`, `DataFetcher<List<Person>>`, a lambda inferred as
   `DataFetcher<Object>`).

Typical uses, both from the demo services:

```java
// legacy fetcher class reused unchanged (person-service-lite)
GraphQLResolvers.query("personById", new PersonByIdFetcher(service));

// lambda, no fetcher class at all (company-service-lite)
GraphQLResolvers.query("companyById",
        env -> service.getCompany(Long.parseLong(env.getArgument("id"))));

// computed field of an object type: env.getSource() is the parent object
GraphQLResolvers.field("Person", "fullName", env -> service.fullNameOf(env.getSource()));
```

Fields *without* a fetcher need no resolver at all: graphql-java's default property
fetcher reads `Person.id`, `Person.email` etc. from the getter of the same name.

### 3.5 `private static String fetcherName(DataFetcher<?> dataFetcher)`

Produces a readable name for `description()`:

| Fetcher kind | `getClass().getSimpleName()` | Result |
|---|---|---|
| Named class `PersonByIdFetcher` | `PersonByIdFetcher` | `PersonByIdFetcher` |
| Lambda declared in `CompanyLiteGraphQLConfig` | `CompanyLiteGraphQLConfig$$Lambda$57/0x…` (JDK 11) or `CompanyLiteGraphQLConfig$$Lambda/0x…` (JDK 21 hidden classes) | `CompanyLiteGraphQLConfig lambda` |
| Anonymous class `new DataFetcher<>() { … }` | `""` | falls back to `getClass().getName()`, e.g. `com.example.X$1` |

The lambda branch looks for the `$$Lambda` marker with `indexOf(...) > 0` (strictly
greater than zero, so a name that *starts* with the marker is left alone) and keeps
only the declaring class's name. Both JDK naming schemes contain the marker, so the
output is stable across the Java 11 target and newer runtimes.

A log line therefore reads `Registered GraphQL resolver 'Query.companyById' ->
adapter of CompanyLiteGraphQLConfig lambda`, which tells you which config class to
open.

---

## 4. `GraphQLResolverRegistry` — collect and index

```java
@Component
public class GraphQLResolverRegistry {
    private final Map<String, GraphQLResolver> resolversByCoordinate;
    public GraphQLResolverRegistry(List<GraphQLResolver> resolvers)
    public Collection<GraphQLResolver> resolvers()
}
```

The registry is the module's only container of state. It exists for two reasons:
to give the controller a single collection to iterate, and to fail boot on the
one wiring mistake the schema check cannot catch — two beans claiming the same
coordinate. Without it, graphql-java's code registry would silently keep whichever
was registered last (§6.2).

### 4.1 `private final Map<String, GraphQLResolver> resolversByCoordinate`

Key: `"<parentType>.<fieldName>"`. Value: the resolver. A `LinkedHashMap`, so
iteration order equals bean injection order (Spring's order for a `List<T>`
injection: `@Order`/`Ordered` beans first, otherwise registration order). The map is
fully populated in the constructor and never mutated afterwards, which is what makes
the registry safe to read from any thread without synchronization.

### 4.2 `public GraphQLResolverRegistry(List<GraphQLResolver> resolvers)`

Spring satisfies the `List<GraphQLResolver>` parameter with **every bean** whose
type is assignable to `GraphQLResolver`, whatever its origin: `@Component` classes
implementing the interface, `@Bean` methods returning adapters from
`GraphQLResolvers`, beans contributed by another module on the classpath. Because
the class has a single constructor, Spring injects an *empty* list rather than
failing when there are no resolver beans at all, so an application with only a
schema still boots (the `emptyApplicationIsAllowed` test covers the constructor
side of that).

The loop:

```java
String coordinate = resolver.parentType() + "." + resolver.fieldName();
GraphQLResolver previous = byCoordinate.putIfAbsent(coordinate, resolver);
if (previous != null) {
    throw new IllegalStateException(String.format(
        "Two GraphQL resolvers claim the coordinate '%s': %s and %s",
        coordinate, previous.description(), resolver.description()));
}
```

- `putIfAbsent` makes the first claimant win *the map slot*, but the second claimant
  turns that into an exception, so in effect no claimant wins: the context fails to
  refresh and the application exits with the message above in the stack trace.
- `description()` rather than `getClass()` is used in the message deliberately:
  every `GraphQLResolvers` adapter is the same anonymous class, so class names could
  not tell `adapter of PersonByIdFetcher` from `adapter of AllPersonsFetcher`.
- Same field name under different parent types is allowed (`Query.name` and
  `Person.name` are distinct keys); the `sameFieldNameUnderDifferentParentTypesIsAllowed`
  test pins it.
- A `null` from `parentType()` or `fieldName()` is not checked here; it would
  produce keys like `"null.x"` and then fail in schema verification. The adapters
  already reject nulls (§3.4); hand-written resolvers are expected to return
  constants.

### 4.3 `public Collection<GraphQLResolver> resolvers()`

Returns `Collections.unmodifiableCollection(resolversByCoordinate.values())`: a
read-only, insertion-ordered live view. The only caller is the controller's
`registerResolvers`. There is intentionally no lookup-by-coordinate method, because
nothing at request time needs one: dispatch is wired per field at startup (§5.3),
not looked up per request.

---

## 5. `GraphQLDispatchController` — wiring and dispatch

```java
@DgsComponent
public class GraphQLDispatchController {
    private static final Logger log = LoggerFactory.getLogger(GraphQLDispatchController.class);
    private final GraphQLResolverRegistry resolverRegistry;
    public GraphQLDispatchController(GraphQLResolverRegistry resolverRegistry)
    @DgsCodeRegistry
    public GraphQLCodeRegistry.Builder registerResolvers(GraphQLCodeRegistry.Builder, TypeDefinitionRegistry)
    private Object dispatch(GraphQLResolver resolver, DataFetchingEnvironment environment) throws Exception
    private static boolean isRootOperation(GraphQLResolver resolver)
    private void verifyFieldExistsInSchema(TypeDefinitionRegistry, GraphQLResolver)
    private static boolean hasField(ObjectTypeDefinition type, String fieldName)
}
```

The single entry point, in two halves: a startup half (`registerResolvers` and its
two verification helpers) and a request-time half (`dispatch` and
`isRootOperation`). It is the only class in the module that imports anything from
DGS.

`@DgsComponent` is a DGS stereotype that is itself a Spring `@Component`, so the
class is an ordinary bean *and* is found by DGS's `getBeansWithAnnotation(DgsComponent)`
scan when it builds the schema. That scan is what causes `registerResolvers` to be
called; a plain `@Component` with a `@DgsCodeRegistry` method would be ignored.

### 5.1 `private static final Logger log`

SLF4J logger named after the class. Both demo services set `com.example` to DEBUG in
`application.yml`; at INFO you keep one line per registered resolver at boot and
one line per root operation per request.

### 5.2 `private final GraphQLResolverRegistry resolverRegistry` and the constructor

Plain constructor injection of the registry. The controller holds no other state;
after startup it is reachable only through the lambdas it registered, each of which
captured its own `GraphQLResolver`.

### 5.3 `@DgsCodeRegistry registerResolvers(GraphQLCodeRegistry.Builder codeRegistryBuilder, TypeDefinitionRegistry typeDefinitionRegistry)`

DGS calls this method once while building the executable schema and passes:

- `codeRegistryBuilder` — graphql-java's mutable registry of data fetchers for the
  schema under construction. DGS has already populated it with its own
  `@DgsData` fetchers at this point (§6.1).
- `typeDefinitionRegistry` — the parsed, merged SDL of *every*
  `schema/**/*.graphql*` file on the classpath. It is the AST, not the executable
  schema, which is why verification works with `ObjectTypeDefinition` nodes rather
  than `GraphQLObjectType`.

For each resolver from `resolverRegistry.resolvers()`, in registry order:

1. `verifyFieldExistsInSchema(typeDefinitionRegistry, resolver)` — fail boot on a
   coordinate the SDL does not declare (§5.5).
2. `FieldCoordinates.coordinates(resolver.parentType(), resolver.fieldName())` —
   graphql-java's key type for the code registry.
3. `DataFetcher<Object> dataFetcher = environment -> dispatch(resolver, environment);`
   — one lambda per resolver, closing over that resolver. This is the object
   graphql-java will call at request time. Note the lambda is *not* the resolver's
   own `resolve` method reference: the extra hop is where the request logging lives.
4. `codeRegistryBuilder.dataFetcher(coordinates, dataFetcher)` — registers it. In
   graphql-java this is a `Map.put`, so a later registration for the same
   coordinates replaces an earlier one (§6.2).
5. `log.info("Registered GraphQL resolver '{}.{}' -> {}", parentType, fieldName, description)`.

Returns the same builder, as the `@DgsCodeRegistry` contract requires.

The method is `public` and takes only graphql-java types, so it can be driven
without Spring or DGS: `GraphQLDispatchControllerTest` constructs the controller by
hand, passes `GraphQLCodeRegistry.newCodeRegistry()` and a `SchemaParser().parse(sdl)`
registry, then pulls the registered fetcher back out of the built registry and
invokes it against a mock environment. That is the fastest way to test a wiring
change.

### 5.4 `private Object dispatch(GraphQLResolver resolver, DataFetchingEnvironment environment) throws Exception` and `isRootOperation`

The request-time hop. Its whole body is logging plus one call:

```java
if (isRootOperation(resolver)) {
    log.info("Received GraphQL operation '{}.{}', dispatching to {}", ...);
    if (log.isDebugEnabled()) {
        log.debug("Arguments of '{}': {}", resolver.fieldName(),
                new LinkedHashMap<>(environment.getArguments()));
    }
} else {
    log.debug("Resolving field '{}.{}' via {}", ...);
}
return resolver.resolve(environment);
```

- `isRootOperation` is `"Query".equals(parentType) || "Mutation".equals(parentType)`.
  Root operations run once per request, so they earn an INFO line; type fields run
  once per row of a result and would flood the log, so they are DEBUG.
- The argument map is copied into a `LinkedHashMap` before logging because
  graphql-java's argument map implementation does not override `toString()`; the
  copy prints as `{id=1}` instead of an object identity hash. The copy is guarded by
  `isDebugEnabled()` so production at INFO pays nothing.
- **Arguments are logged verbatim at DEBUG.** If a mutation carries secrets or
  personal data, run production at INFO for this logger, or add masking here.
- The return value and any exception pass straight through. There is no `try`,
  no translation, no validation. If you later need JSON-schema validation or an
  error boundary, this method (and `registerResolvers`, which creates the lambda) is
  the one place to add them — that is exactly where the full track hooks them in.

### 5.5 `private void verifyFieldExistsInSchema(TypeDefinitionRegistry typeDefinitionRegistry, GraphQLResolver resolver)` and `hasField`

The fail-fast check that turns a typo into a boot failure instead of a dead field
that answers `null` in production. A field counts as declared when either:

1. the parent type's **base definition** declares it —
   `typeDefinitionRegistry.getType(parentType, ObjectTypeDefinition.class)` finds a
   `type Parent { ... }` block (an `Optional`; empty when the type does not exist or
   is not an object type) and `hasField` finds the name among its
   `getFieldDefinitions()`; **or**
2. any **`extend type Parent { ... }` block** declares it —
   `typeDefinitionRegistry.objectTypeExtensions()` is a `Map<String, List<ObjectTypeExtensionDefinition>>`
   keyed by type name; the check streams the list (empty default when the type was
   never extended) and matches any extension containing the field.

The second branch matters for multi-module schemas: one `.graphqls` file declares
the base `type Query`, other files (possibly inside other jars) add operations via
`extend type Query`, and all of them must be resolvable. The
`fieldContributedByTypeExtensionIsAccepted` test pins it.

On failure it throws

```
IllegalStateException: Resolver <description> resolves 'Parent.field' but no such field is declared in the GraphQL schema
```

Because the exception escapes from a `@DgsCodeRegistry` method, DGS's schema
provider fails, the DGS beans depending on the schema fail, and Spring Boot aborts
startup with that message in the "Caused by" chain. Both the unknown-field case
(`Query.missing`) and the unknown-type case (`Ghost.name`) are pinned by tests.

`hasField` is a one-liner over `ObjectTypeDefinition.getFieldDefinitions()`,
comparing names with `equals` (case-sensitive). It accepts an
`ObjectTypeExtensionDefinition` too, since that class extends `ObjectTypeDefinition`.

What the check does **not** do, by design:

- It does not verify argument names or types, nor the field's return type. The SDL
  remains the only source of truth for those; a resolver reading
  `environment.getArgument("ids")` when the schema says `id` simply gets `null`.
- It does not detect a schema field that has **no** resolver. A field with no
  registered fetcher falls back to graphql-java's property fetcher, which is the
  intended behavior for plain getter-backed fields, so there is nothing to warn
  about.
- It does not look at interfaces or unions (§2.1).

---

## 6. Where the module hands over to DGS and graphql-java

These are the behaviors of the libraries the lite track *relies on* without
re-implementing. They were confirmed against DGS 4.9.25 / graphql-java 17.3 class
files resolved by this build.

### 6.1 How DGS builds the schema, in order

`DgsSchemaProvider.schema()` does, in this order:

1. Finds schema files with the pattern **`classpath*:schema/**/*.graphql*`** (so
   `.graphqls` and `.graphql`, in any sub-folder of `schema/`, in every jar and
   directory on the classpath) and parses + merges them into one
   `TypeDefinitionRegistry`.
2. Creates a fresh `GraphQLCodeRegistry.Builder`.
3. Registers DGS-native contributions from `@DgsComponent` beans: `@DgsScalar`,
   `@DgsDirective`, **`@DgsData` / `@DgsQuery` / `@DgsMutation` data fetchers**,
   `@DgsTypeResolver`.
4. **Then** invokes every `@DgsCodeRegistry` method — including
   `GraphQLDispatchController.registerResolvers`.
5. Then `@DgsRuntimeWiring` methods, then builds the executable schema.

Consequence: a lite `GraphQLResolver` and a DGS `@DgsData` method on the same
coordinate do not clash at boot; the lite one, registered later, silently replaces
the DGS one (§6.2). The registry's duplicate check only sees `GraphQLResolver` beans.
Mixing the two styles in one service is possible but not recommended for that
reason; the lite track's demo services use only `GraphQLResolver` beans.

Each file is parsed as SDL, so an SDL syntax error anywhere in any schema file fails
boot before the lite controller is ever called.

### 6.2 `GraphQLCodeRegistry.Builder.dataFetcher` is "last write wins"

graphql-java stores fetchers in a `Map<FieldCoordinates, DataFetcherFactory>` and
`dataFetcher(coordinates, fetcher)` is a `put`. No exception, no warning on
overwrite. That is the gap `GraphQLResolverRegistry` closes for lite resolvers.

### 6.3 The HTTP surface

Provided entirely by the DGS web-MVC starter: `POST /graphql` (path configurable with
`dgs.graphql.path`), introspection, and GraphiQL at `/graphiql` (toggle with
`dgs.graphql.graphiql.enabled`, path with `dgs.graphql.graphiql.path`). The lite
module adds no controllers, filters or interceptors; the consumer adds
`spring-boot-starter-web` to get a servlet container, as both lite services do.

### 6.4 Testing through the stack without HTTP

`DgsQueryExecutor` (a DGS bean) executes a query string against the built schema in
process. `PersonLiteGraphQLIntegrationTest` uses
`executeAndExtractJsonPath(query, "data.allPersons[*].fullName")` to exercise
DGS → controller → fetcher → service → DAL end to end inside a `@SpringBootTest`.

---

## 7. Errors: what a client sees when a resolver throws

The lite track installs no `DataFetcherExceptionHandler`, so DGS's
`DefaultDataFetcherExceptionHandler` runs. Verified behavior:

1. It logs at **ERROR**, with the stack trace:
   `Exception while executing data fetcher for /personById: <exception message>`.
2. It maps the exception to a `TypedGraphQLError` placed at the field's path:

   | Thrown exception | GraphQL `errorType` | Message |
   |---|---|---|
   | `com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException` | `NOT_FOUND` | `<fully qualified class>: <message>` |
   | `com.netflix.graphql.dgs.exceptions.DgsBadRequestException` | `BAD_REQUEST` | `<fully qualified class>: <message>` |
   | Spring Security `AccessDeniedException` (only if Spring Security is on the classpath) | `PERMISSION_DENIED` | `<fully qualified class>: <message>` |
   | anything else (`IllegalArgumentException`, `NullPointerException`, your own exceptions…) | `INTERNAL` | `<fully qualified class>: <message>` |

So the response for an unhandled `IllegalStateException("db down")` from
`Query.personById` is roughly:

```json
{
  "errors": [{
    "message": "java.lang.IllegalStateException: db down",
    "path": ["personById"],
    "extensions": { "errorType": "INTERNAL" }
  }],
  "data": { "personById": null }
}
```

**The exception class name and message reach the client.** That is acceptable for
a demo and unacceptable for most production APIs. The two remedies, in increasing
order of effort:

- Register your own `graphql.execution.DataFetcherExceptionHandler` bean. DGS picks
  it up (it is an `Optional` constructor dependency of the schema provider) and
  uses it instead of the default. A ten-line handler that logs the cause and returns
  a fixed "internal error" message closes the leak.
- Port the full track's `GraphQLExceptionHandler` + `ApiException`/`ErrorCode`
  model (`graphql-infrastructure`, `graphql.errors` package) for structured,
  classified errors.

Note that the DGS exceptions in the table are usable from lite resolvers today: throw
`DgsEntityNotFoundException` from a by-id resolver and the client gets `NOT_FOUND`
without any infrastructure change. Both demo services instead return `null` for a
missing id, which the schema declares nullable (`personById(id: ID!): Person`).

---

## 8. Logging reference

| When | Level | Logger | Line |
|---|---|---|---|
| Boot, per resolver | INFO | `GraphQLDispatchController` | `Registered GraphQL resolver 'Query.personById' -> adapter of PersonByIdFetcher` |
| Boot, duplicate coordinate | — (exception) | — | `Two GraphQL resolvers claim the coordinate 'Query.personById': adapter of A and adapter of B` |
| Boot, unknown coordinate | — (exception) | — | `Resolver adapter of X resolves 'Query.missing' but no such field is declared in the GraphQL schema` |
| Request, Query/Mutation field | INFO | `GraphQLDispatchController` | `Received GraphQL operation 'Query.personById', dispatching to adapter of PersonByIdFetcher` |
| Request, Query/Mutation field | DEBUG | `GraphQLDispatchController` | `Arguments of 'personById': {id=1}` (verbatim arguments) |
| Request, any other object-type field | DEBUG | `GraphQLDispatchController` | `Resolving field 'Person.fullName' via adapter of PersonLiteGraphQLConfig lambda` |
| Request, resolver threw | ERROR | DGS `DefaultDataFetcherExceptionHandler` | `Exception while executing data fetcher for /personById: …` + stack trace |

Recommended production setting: `com.example.lite.graphql: INFO` (per-operation
lines without argument values).

---

## 9. The three ways to declare a resolver

All three end up as a `GraphQLResolver` bean and are treated identically by the
registry and controller. Pick by situation:

| Situation | Style | Example in this repo |
|---|---|---|
| Migrating: a `DataFetcher` class already exists | `@Bean` returning `GraphQLResolvers.query/mutation/field(name, new LegacyFetcher(deps))` | `PersonLiteGraphQLConfig` (five beans) |
| New operation, trivial body | `@Bean` returning `GraphQLResolvers.query(name, env -> service.call(...))` | `CompanyLiteGraphQLConfig` (whole service) |
| New operation, non-trivial body or wants its own unit test | `@Component class X implements GraphQLResolver` with constant `parentType()`/`fieldName()` | `PersonCountResolver` |

Checklist for any new coordinate:

1. Declare the field in a `src/main/resources/schema/*.graphqls` file — on the base
   type or in an `extend type` block.
2. Declare exactly one `GraphQLResolver` bean for it, with `parentType()` and
   `fieldName()` spelled exactly as in the SDL.
3. Make sure the bean is in a scanned package. The demo applications scan
   `com.example.lite`, which covers both the infrastructure package and the service's
   own packages; a consumer with a different root package must list
   `com.example.lite.graphql` explicitly in `scanBasePackages`, or neither the
   registry nor the controller will exist and no resolver will be wired (and,
   because the registry bean is missing, nothing will complain).
4. Boot. Two outcomes only: the INFO "Registered" line appears, or boot fails with
   one of the two messages in §8.

---

## 10. Invariants to preserve when changing the module

- **Zero domain knowledge.** The module must compile and be meaningful without any
  service on the classpath. No `Person`, no `Company`, no entity, no DTO.
- **Fail at boot, not at request time.** Both checks (duplicate coordinate, unknown
  coordinate) are in constructors or startup hooks. Keep new validations there too.
- **Request-time path stays a pass-through.** `dispatch` adds logging and nothing
  else; return values and exceptions must reach graphql-java unchanged unless you are
  deliberately adding an error boundary, in which case document it in `docs/LITE.md`'s
  omissions table.
- **No second contract.** `parentType()` already covers root operations and object
  fields. Resist adding a `GraphQLFieldResolver`; the full track has one and the lite
  track's reason to exist is not having it.
- **Keep `package-info.java` current.** It is the module's one-paragraph
  specification; the three numbered lifecycle steps there must match §1.2 here.

### 10.1 Mapping to the full track, if a service outgrows lite

| Lite | Full (`graphql-infrastructure`) |
|---|---|
| `GraphQLResolver.parentType()` returning `"Query"`/`"Mutation"` | `GraphQLResolver.operationType()` returning `GraphQLOperationType.QUERY`/`MUTATION` |
| `GraphQLResolver.parentType()` returning an object type | separate `GraphQLFieldResolver` contract |
| `GraphQLResolver.resolve(env)` | same signature |
| no `argumentJsonSchemas()` | `default Map<String,String> argumentJsonSchemas()` opts into JSON-schema validation |
| `GraphQLResolvers.query/mutation/field` | `DataFetcherAdapters.query/mutation` (+ `.validating(...)`) in `graphql.migration` |
| `GraphQLResolverRegistry` (`LinkedHashMap` + manual duplicate check) | `GraphQLResolverRegistry` over `UniqueIndex` |
| DGS default exception handler | `GraphQLExceptionHandler` + `ApiException`/`ErrorCode` + `ExceptionMapper` beans |

The move is mechanical because the method names and the environment-based
`resolve` signature were kept identical on purpose.

---

## 11. Test coverage map

| Test class | What it pins |
|---|---|
| `GraphQLResolversTest` | `query()` → parent `Query`; `mutation()` → parent `Mutation`; `field()` → any parent; the adapter passes the *same* environment object to the delegate; all three arguments null-checked. |
| `GraphQLResolverRegistryTest` | every resolver indexed; same field name under different parents allowed; duplicate coordinate → `IllegalStateException` naming the coordinate; empty list allowed. |
| `GraphQLDispatchControllerTest` | registered fetcher dispatches to the resolver; `Query`, `Mutation` and object-type coordinates all register; a field declared only in `extend type Query` is accepted; unknown field and unknown type both fail with `IllegalStateException` naming the coordinate. Uses a hand-parsed SDL and `GraphQLCodeRegistry.newCodeRegistry()`, no Spring, no DGS. |
| `PersonLiteGraphQLIntegrationTest` (consumer) | the whole stack through `DgsQueryExecutor`: list with computed field, by-id, unknown id → null, case-insensitive city filter, count, create, delete twice. |

Run just the module with `mvn test -pl graphql-infrastructure-lite`, or a consumer
end to end with `mvn test -pl person-service-lite -am`.
