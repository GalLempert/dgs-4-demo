# Extending the service — cookbook

Step-by-step recipes for the common changes, ordered from cheapest to most involved.
Background on how the pieces fit: [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Add a simple field (same on GraphQL and DB) — zero mapping code

Example to copy: `nickname`. Four declarations, no mapper/service/resolver changes:

1. `person.graphqls` — add the field to `Person` and (if writable) `CreatePersonInput`.
2. `Person` entity — add the column field + getter/setter.
3. `CreatePersonInput` — add the field + getter/setter.
4. `PersonView` — add the field + getter/setter.

The `DeclarativeMapper` carries it input→entity and entity→view by name. Done.

## Add validation constraints to an input — zero code

Edit `person-service/src/main/resources/json-schema/person-create.json`. Anything
draft-07 supports: ranges, patterns, lengths, array sizes, formats. Violations return
`SCHEMA_VALIDATION_FAILED` (400) with one `details` entry per broken constraint.

## Add a calculated (derived) output value

1. Add the math to `PersonCalculations` (pure method — add a unit test).
2. Add the field to `PersonView` + schema.
3. Set it in `PersonMapper.toView` (one line).

## Expose a field in a custom shape

- **Client-chosen time format**: annotate the view field with `@GraphQLTemporal` and
  declare the argument in the schema: `fieldName(format: DateFormat = ISO): String`.
- **Enriched enum** (`{ code label description }`): annotate with
  `@GraphQLEnum("catalogName")`, declare the schema field as `EnumValue`, and make
  sure an `EnumCatalog` bean answers for that catalog name.
- **Anything bespoke**: implement `GraphQLFieldResolver` (parentType + fieldName +
  resolve) as a `@Component`; the dispatcher registers it at that schema coordinate.
  `environment.getSource()` is the backing view object.

## Add a new time format

1. One bean: `implements TemporalFormatter` (`formatName()` + `format(...)`).
2. One schema literal: add it to `enum DateFormat` in
   `graphql-infrastructure/src/main/resources/schema/common.graphqls`.

## Add a query or mutation

1. Declare the field on `Query`/`Mutation` in the domain's `.graphqls` file.
2. Implement `GraphQLResolver` as a `@Component`: `operationType()`, `fieldName()`
   (must match the schema — verified at boot), `resolve(environment)`.
3. Parse arguments through `GraphQLArgumentMapper` (`argument`, `longArgument`,
   `decimalArgument`) — never hand-parse; malformed input becomes a 400, not a 500.
4. For validated inputs, override `argumentJsonSchemas()` → `{argName: schemaName}`.
5. Call the service; return views. Add an integration test with `DgsQueryExecutor`.

## Make a field filterable

Add it to the resource's filter input in the domain schema, choosing the filter type
that matches the attribute:

```graphql
input PersonFilter {
    ...
    shoeSize: IntFilter        # field path must match the entity attribute
}
```

Nested objects get their own filter input (see `AddressFilter`) — the parser turns
nesting into dotted attribute paths (`address.city`). Nothing else: the WHERE clause
is built dynamically and values are coerced to the attribute's Java type.

## Add a filter predicate (e.g. `startsWith`)

1. One bean in the domain or infrastructure: `implements FilterPredicateStrategy`
   (extend `ComparisonFilterPredicate` if it compares by natural order).
2. Add the predicate field to the relevant filter input(s) in `common.graphqls`
   with an arguments input type (`{ value }`, `{ from, to }`, …).

The parser recognizes it automatically (predicate names come from the registry).

## Add an error code / a new known exception

1. Add the constant to `ErrorCode` with its HTTP status + GraphQL classification
   (override `isServerFault()` only if it should log with a stack trace).
2. Create the exception `extends ApiException`, attaching `ErrorDetail`s.
3. Throw it from the service/DAL. Rendering is automatic.

To translate a *third-party* exception instead (e.g. a persistence exception into
`DUPLICATE_RESOURCE`): add one `ExceptionMapper` bean; the handler asks mappers in
bean order.

## Swap the enum catalog for a real enum service

Replace the `PersonEnumCatalog` bean with one that fetches/caches/hot-reloads from
your enum service — same `EnumCatalog` interface. Models, schema, resolvers unchanged.

## Add a whole new domain (e.g. Company)

1. New Maven module depending on `graphql-infrastructure` (+ `graphql-playground` if
   wanted); or a package in an existing app module.
2. `schema/company.graphqls` — types, queries, mutations, `CompanyFilter` composed
   from the shared filter inputs.
3. Entity/entities extending `BaseEntity`; repository (+ `JpaSpecificationExecutor`
   for filtering); a DAL wrapping it with the `QueryResultCap`.
4. Service + views (annotate with `@GraphQLModel("Company")` and register via one
   `GraphQLModelSource` bean); `DeclarativeMapper` for the pass-through mapping.
5. `GraphQLResolver` beans for each operation; JSON schemas under `json-schema/`.

Nothing in `graphql-infrastructure` changes — that's the acceptance test for the
module split.
