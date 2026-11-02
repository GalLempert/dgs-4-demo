# Implementation brief: filtering on fields inside a `jsonb` column

**Audience:** an AI coding agent (or a developer) working in a service built on the
`graphql-infrastructure` framework. Read this whole document before changing code.
Then read the framework docs it points to. Do not skip the "Constraints" section.

**Goal in one sentence:** let a GraphQL client filter a resource by a value stored at
an arbitrary path inside a `jsonb` column, without modelling the JSON in the GraphQL
schema, by sending a free-text JSON path that is validated server-side and turned into
a Postgres `jsonb_extract_path_text(...)` expression inside the existing Spring Data
JPA `Specification` pipeline.

---

## 1. Context you must know

### 1.1 The stack
- Java 11, Spring Boot 2.4.x, Spring Data JPA, Hibernate 5.4, Netflix DGS 4.9.x,
  graphql-java 17, PostgreSQL in production.
- `jsonb` columns are already mapped with Vlad Mihalcea's `hibernate-types`
  (`@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)` +
  `@Type(type = "jsonb")`). Do not add a second JSON mapping library.
- Hibernate 5.4 criteria API **cannot** express Postgres operators (`->`, `->>`, `@>`).
  It can call named SQL functions via `CriteriaBuilder.function(name, type, args...)`.
  That is why this design uses `jsonb_extract_path_text(col, 'a', 'b', ...)`, a plain
  function whose arguments are bound parameters, instead of operator syntax.

### 1.2 How filtering works today (read these classes first)
All in the `graphql-infrastructure` module, package `...infrastructure.filter`:

| Class | Role |
|---|---|
| `FilterParser` | Turns the raw GraphQL `filter` argument (nested `Map`s) into a `FilterCriteria`. Rule: inside a field's map, if every key is a known predicate name it is a leaf (`{equals: {value: ..}}`); if no key is a predicate it is a nested object and is recursed into with a dotted path (`address.city`); mixing both is an error. |
| `FieldFilter` | One leaf: `fieldPath` (dotted entity attribute path), `predicate` name, `arguments` map. |
| `FilterCriteria` | List of `FieldFilter` + combinator (AND only today). Framework-neutral; the service layer sees this, never JPA types. |
| `FilterPredicateStrategy` | Interface: `predicateName()` and `toPredicate(Path<?> path, Map args, CriteriaBuilder cb, FilterValueCoercer coercer)`. One `@Component` per predicate: `equals`, `notEquals`, `in`, `like`, `greaterThan`, `lessThan`, `between`. |
| `FilterPredicateRegistry` | Indexes strategies by name (fails boot on duplicates); tells the parser which keys are predicates. |
| `FilterValueCoercer` | Jackson `convertValue(raw, path.getJavaType())`. |
| `FilterSpecificationBuilder` | Builds the `Specification<T>`. `resolvePath` walks `root.get(segment)` per dotted segment, then calls the predicate strategy. Unknown attribute → `ApiException(INVALID_ARGUMENT, "... is not filterable ...")`. |
| `ResourceDal.findAll / count / matchingIds` (package `...replication`) | Consume the `Specification`; COUNT first, then `QueryResultCap` check, then fetch. |

GraphQL side: `schema/common.graphqls` (inside the infrastructure jar) declares
`StringFilter`, `IntFilter`, `FloatFilter`, `DateFilter`, `BooleanFilter` and their
`*ValuePredicate` / `*RangePredicate` / `*ListPredicate` inputs. Each domain declares
its own `<Resource>Filter` input listing filterable fields (e.g. `PersonFilter`).

Validation side: a `GraphQLResolver` may override `argumentJsonSchemas()` returning
`{ argumentName -> schemaName }`; `GraphQLDispatchController` validates that argument
against `classpath:json-schema/<schemaName>.json` (everit-json-schema) **before** the
resolver runs. Today only mutation inputs use it; the `filter` argument can use it too.

Docs to read: `docs/FILTERING.md`, `docs/EXTENDING.md` (section on adding predicates),
`docs/ARCHITECTURE.md` (three layers and their boundary rules).

### 1.3 Boundary rules you must respect
1. `graphql-infrastructure` is domain-agnostic. Nothing in it may know about a
   specific entity, column name, or JSON shape.
2. GraphQL/DGS types stop at the dispatch controller. Services see `FilterCriteria`.
3. Spring Data / JPA stops at the DAL. Do not put criteria-API code in a service.
4. Startup is fail-fast: registries throw on duplicates; schema coordinates are
   verified at boot.
5. Every infrastructure package has a `package-info.java`; update it when you add
   classes to a package.

---

## 2. Design decisions (already made; do not re-open unless blocked)

**D1. Opaque filter input, not a modelled JSON type.** The JSON can be anything, so the
schema exposes one generic input:

```graphql
"""
Filter on a value inside a JSON column. `path` is a dot-separated sequence of
object keys (e.g. "contract.region"); array indexing is not supported in v1.
The value at the path is compared as text. Exactly one predicate must be set.
"""
input JsonFieldFilter {
    path: String!
    equals: StringValuePredicate
    notEquals: StringValuePredicate
    like: StringValuePredicate
    in: StringListPredicate
}
```

This goes in `common.graphqls` next to the other `*Filter` inputs. A domain opts in by
adding a field typed `[JsonFieldFilter!]` to its `<Resource>Filter` input, named after
the entity attribute holding the JSON (e.g. `metadata: [JsonFieldFilter!]`). A list is
used so a client can filter on several paths of the same column (combined with AND).

**D2. v1 compares as text only.** `jsonb_extract_path_text` returns `text`, so the four
string predicates above are the full v1 set. Numeric/date comparison on JSON values is
out of scope (would need a registered cast function). Keep the door open: the design
below makes adding `JsonNumberFieldFilter` later a matter of one new resolver strategy.

**D3. Path grammar is enforced by JSON Schema, not by hand.** The `filter` argument
gets a JSON Schema that constrains every `path` string to
`^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$`, `maxLength` 200, and the array to `maxItems` 10.
Wire it via `argumentJsonSchemas()`. This is the "validated after the GraphQL schema"
step. Because the schema is per-resource (it must know the `<Resource>Filter` shape),
the JSON Schema file lives in the **domain** module; the infrastructure only provides
the reusable `JsonFieldFilter` definition fragment that domain schemas `$ref`.

**D4. Path segments become bound SQL parameters.** Never concatenate user input into
SQL. Each dotted segment becomes `criteriaBuilder.literal(segment)` passed as a
separate argument to `function("jsonb_extract_path_text", String.class, column, seg1,
seg2, ...)`. Hibernate renders literals as either inline-escaped or bound parameters;
both are safe. No raw `@Query` strings.

**D5. The infrastructure change is a pluggable "expression resolver", not a special
case.** Today `FilterSpecificationBuilder.resolvePath` returns a `Path<?>`. Widen the
concept: a `FieldFilter` resolves to a JPA `Expression<?>`. Plain attribute paths keep
resolving to a `Path` (which is an `Expression`). A JSON leaf resolves to a function
expression. The predicate strategies are changed to accept `Expression<?>`.

**D6. Postgres-only, explicitly.** The function name `jsonb_extract_path_text` is held
in a small strategy bean (`JsonPathExpressionFactory`, default Postgres impl). A
service on another database can override the bean. Integration tests that exercise it
must run against Postgres (Testcontainers). Unit tests for parsing/validation must not
need a database.

---

## 3. Implementation steps

Work in this order. Each step should compile and keep existing tests green.

### Step 1 — Carry the JSON leaf through `FieldFilter`
Package `...infrastructure.filter`.
- Add to `FieldFilter` an optional `jsonPath` (a `List<String>` of segments, empty for
  plain attribute filters) and a boolean accessor `isJsonLeaf()`. Keep the existing
  constructor for backward compatibility; add a second one taking the segments.
  `fieldPath` for a JSON leaf is the **entity attribute** holding the JSON column
  (e.g. `metadata`), not the JSON path.
- Update `toString()` so logs show `metadata{contract.region} equals {value=EU}`.

### Step 2 — Teach `FilterParser` the opaque shape
- In `walk`, before the all-predicates / none-predicates check: if the node's value is a
  `List`, treat each element as a JSON-field filter object for the current path.
  (Today a list value fails `requireMap`; that is the hook.)
- For each element map: require a `path` key of type `String`; split on `.`; every
  remaining key must be a predicate name (reuse `predicateRegistry::isPredicate`),
  and there must be exactly one. Emit `new FieldFilter(path, predicate, args, segments)`.
- Malformed → the existing `invalidFilter(path, reason)` helper (`INVALID_ARGUMENT`).
- Do **not** add a JSON-specific predicate name. The predicate is still `equals`/`like`/etc.

### Step 3 — Widen predicates from `Path<?>` to `Expression<?>`
Package `...infrastructure.filter` and `.predicates`.
- `FilterPredicateStrategy.toPredicate(Expression<?> expression, ...)`.
- `EqualsFilterPredicate`, `NotEqualsFilterPredicate`, `InFilterPredicate`,
  `LikeFilterPredicate`: replace `Path` with `Expression`; `expression.getJavaType()` and
  `expression.as(String.class)` exist on `Expression`, so bodies barely change.
- `ComparisonFilterPredicate` and its three subclasses: same mechanical change
  (`comparableExpression` instead of `comparablePath`).
- This is a source-compatible rename for every call site except the builder.

### Step 4 — Expression resolution in `FilterSpecificationBuilder`
- Introduce `JsonPathExpressionFactory` interface in `...infrastructure.filter`:
  `Expression<String> extractText(CriteriaBuilder cb, Path<?> jsonColumn, List<String> segments)`.
- Default `@Component` `PostgresJsonPathExpressionFactory`:
  ```java
  Expression<?>[] args = new Expression<?>[segments.size() + 1];
  args[0] = jsonColumn;
  for (int i = 0; i < segments.size(); i++) args[i + 1] = cb.literal(segments.get(i));
  return cb.function("jsonb_extract_path_text", String.class, args);
  ```
  Annotate with `@ConditionalOnMissingBean(JsonPathExpressionFactory.class)` so a
  service can replace it.
- In `FilterSpecificationBuilder.toPredicate`: resolve the attribute path as today; if
  `filter.isJsonLeaf()`, pass the resulting `Path` and segments to the factory and hand
  the returned `Expression<String>` to the predicate strategy; otherwise pass the `Path`.
- Keep the existing `IllegalArgumentException` → `INVALID_ARGUMENT` translation for an
  unknown attribute. Add a guard: if the attribute's Java type is not something the
  jsonb mapping produces (`JsonNode`, `Map`, or `String` for raw-JSON mappings), throw
  `INVALID_ARGUMENT` "attribute 'x' is not a JSON column". Make the accepted types a
  method on `JsonPathExpressionFactory` so it stays pluggable.
- `FilterValueCoercer` is called with `expression.getJavaType()`, which for the function
  expression is `String.class`; nothing else changes.

### Step 5 — Schema
- `common.graphqls` (infrastructure): add `JsonFieldFilter` as in D1, with doc strings.
- Domain schema: add `<attribute>: [JsonFieldFilter!]` to the resource's filter input,
  with a doc string that names the column and states "path is validated server-side;
  see docs/FILTERING.md#json-columns".
- Boot will fail if the coordinate wiring is wrong; run the app once to confirm.

### Step 6 — Server-side path validation (JSON Schema)
- Infrastructure: add `classpath:json-schema/fragments/json-field-filter.json` defining
  `JsonFieldFilter` (object with required `path` matching the regex in D3, `maxLength`
  200, `additionalProperties` limited to the four predicate keys, `minProperties` 2,
  `maxProperties` 2). Check how `JsonSchemaValidationService` resolves `$ref` (everit
  needs a `SchemaLoader` with a classpath `SchemaClient`); if `$ref` across files is not
  supported today, inline the fragment in the domain schema and leave a TODO.
- Domain: add `classpath:json-schema/<resource>-filter.json` describing the
  `<Resource>Filter` argument: `additionalProperties: true` for every field except the
  JSON one, which is `type: array, maxItems: 10, items: { $ref: fragment }`.
- Wire it: the standard resolvers come from `ReplicationResolverFactory` /
  `MutationResolverFactory`. Add an overload (or builder option) that lets the domain's
  `@Bean` registration pass `argumentJsonSchemas` for the `filter` argument, so
  `FilteredListResolver`, `CountByFilterResolver`, `BySequenceResolver` and the
  filter-taking mutations all validate it. Do not duplicate the validation in the parser.

### Step 7 — Documentation
- `docs/FILTERING.md`: new section "JSON columns" — the input shape, the path grammar,
  text-only comparison, the AND semantics across list items, the Postgres-only note,
  and the indexing caveat (expression index per hot path, or GIN + containment later).
- `docs/EXTENDING.md`: recipe "Exposing a JSON column for filtering" (annotate column,
  add schema field, add JSON Schema file, pass it to the resolver factory).
- `package-info.java` of `...infrastructure.filter`: mention expression resolution and
  the JSON path factory.
- `docs/FILTER-COMPOSITION.md` already says and/or/not is unimplemented; add one line
  that `JsonFieldFilter` list items are ANDed like everything else.

---

## 4. Tests to write

**Unit (no DB), infrastructure module**
- `FilterParserTest`: list value → one `FieldFilter` per element with correct segments;
  missing `path` → `INVALID_ARGUMENT`; two predicates in one element → error; zero
  predicates → error; non-string `path` → error; plain filters still parse unchanged.
- `FilterSpecificationBuilderTest` with mocked `CriteriaBuilder`/`Root`: for a JSON leaf,
  verify `cb.function("jsonb_extract_path_text", String.class, column, lit("a"), lit("b"))`
  is called once and its result is passed to the predicate; non-JSON attribute with a
  JSON leaf → `INVALID_ARGUMENT`.
- Predicate tests: existing ones still pass after the `Expression` widening.
- JSON Schema: a test that loads the resource filter schema and asserts
  `{"metadata":[{"path":"a..b","equals":{"value":"x"}}]}` is rejected and
  `{"metadata":[{"path":"a.b","equals":{"value":"x"}}]}` is accepted; also reject
  `path` with spaces, quotes, `$`, leading dot, 201 chars, and an array of 11.

**Integration (Postgres via Testcontainers), domain module**
- Seed three rows with different JSON; query `equals`, `like`, `in`, `notEquals` on a
  nested path; assert row sets.
- Path that exists on some rows only: rows without it are excluded (NULL never equals).
- Combine a JSON filter with a column filter → AND.
- Two list items on different paths → AND.
- Result-cap interaction: COUNT runs with the JSON predicate too (set
  `graphql.query.max-results=1` and assert `RESULT_SET_TOO_LARGE`).
- Replication feed `…BySequence(filter: …)`: `nonMatchingIds` reflects the JSON filter.
- Boot test: application context starts with the new schema and coordinates.

If the repository's existing integration tests run on H2, add a Postgres Testcontainers
profile for these tests only; do not migrate the whole suite.

---

## 5. Acceptance criteria
- [ ] `mvn package` green (unit + integration).
- [ ] A client can send
  `filter: { metadata: [{ path: "contract.region", equals: { value: "EU" } }] }`
  and receive only matching rows; the generated SQL contains
  `jsonb_extract_path_text(... , ?, ?)` (or escaped literals), never the raw path string
  concatenated into the SQL.
- [ ] `path: "a b"` or `path: "a;drop"` is rejected with `INVALID_ARGUMENT` **before**
  the resolver runs (visible in the dispatch controller DEBUG log).
- [ ] A non-JSON attribute used with `[JsonFieldFilter!]` fails with a clear
  `INVALID_ARGUMENT`, not a Hibernate exception.
- [ ] No class in `graphql-infrastructure` references a domain entity or column name.
- [ ] Existing filter behaviour and all existing tests unchanged.
- [ ] `docs/FILTERING.md` and `docs/EXTENDING.md` updated.

---

## 6. Out of scope (do not do these now)
- Numeric / date / boolean comparison on JSON values (needs a cast function or
  `jsonb_path_query`). Leave a comment in `JsonPathExpressionFactory` naming it.
- Array indexing in paths (`items[0].id`).
- Containment (`@>`) / GIN-index-friendly queries.
- `and`/`or`/`not` composition.
- Exposing JSON content as typed GraphQL fields.

## 7. Known pitfalls
- `CriteriaBuilder.literal(String)` in Hibernate 5.4 may be rendered inline (escaped)
  rather than as a bind parameter depending on `hibernate.criteria.literal_handling_mode`.
  Both are safe; if you want guaranteed binding, set that property to `BIND` in the test
  profile and assert on the SQL.
- `cb.function(...)` returns `Expression<String>`; `like` already calls `.as(String.class)`,
  which is a no-op here. Do not add a `CAST` — Postgres rejects `CAST(text AS text)` in
  some Hibernate renderings.
- `jsonb_extract_path_text` returns NULL for a missing path; `notEquals` therefore does
  **not** match rows lacking the path. Document this in FILTERING.md.
- everit-json-schema `$ref` to another classpath file needs an explicit `SchemaClient`;
  check `JsonSchemaValidationService` before relying on it.
- Hibernate may need the function name registered in the dialect for HQL, but
  `CriteriaBuilder.function` with an arbitrary name works without registration. If
  startup validation complains, register it via a `MetadataBuilderContributor`.
