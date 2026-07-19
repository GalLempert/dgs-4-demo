# Filtering

Filtering narrows a collection before the platform returns it. Use it when you know
something about the records you need—for example, a status, a date range, a category,
or a value threshold. The platform applies the filter while retrieving the data, so a
client does not need to request a broad collection and discard unwanted records
locally.

This page explains the filtering **concept**. The field names, collection name, and
filter type in the examples are intentionally illustrative. Always use the schema
exposed by the API for the fields, filter type, and predicates available for the
resource you are querying.

## The filter shape

A filter has three parts:

1. **A filterable field**, such as `status` or `metadata.category`.
2. **A predicate** supported by that field's type, such as `equals` or `between`.
3. **Predicate arguments**, such as `{ value: "PUBLISHED" }`.

In this illustrative query, the filter means “return records whose status is
published”:

```graphql
query PublishedItems {
  items(filter: {
    status: { equals: { value: "PUBLISHED" } }
  }) {
    id
    title
    status
  }
}
```

Predicates are objects rather than shorthand values so every comparison has an
unambiguous shape. Single-value predicates use `value`, ranges use `from` and `to`,
and membership predicates use `values`.

## Discover what is filterable

Filtering is opt-in. A resource exposes a dedicated filter input in the GraphQL
schema; only the fields in that input can be filtered. The schema also restricts each
field to the predicates that make sense for its data type.

For example, an illustrative filter input might look like this:

```graphql
input ItemFilter {
  title: StringFilter
  status: StringFilter
  score: FloatFilter
  publishedOn: DateFilter
  enabled: BooleanFilter
  metadata: MetadataFilter
}

input MetadataFilter {
  category: StringFilter
}
```

This shape permits filters such as `title`, `score`, or `metadata.category`. It does
not imply that every response field is filterable. Derived values, related lists,
audit information, identifiers, and fields omitted from the filter input cannot be
used as filters unless the schema explicitly exposes them.

Use the playground's schema explorer or GraphQL introspection when you are unsure.
The schema is the authoritative contract for the resource currently being queried.
An unsupported field or predicate is rejected as invalid GraphQL input; it is never
silently ignored.

## Predicates

The platform provides typed predicate inputs. The exact schema determines which ones
a particular field exposes, but the common predicate set is:

| Predicate | Argument shape | Meaning |
|---|---|---|
| `equals` | `{ value: … }` | Matches one exact value. |
| `notEquals` | `{ value: … }` | Excludes one exact value. |
| `in` | `{ values: […] }` | Matches any value in a supplied set. |
| `greaterThan` | `{ value: … }` | Matches values strictly above a bound. |
| `lessThan` | `{ value: … }` | Matches values strictly below a bound. |
| `between` | `{ from: …, to: … }` | Matches an inclusive range. |
| `like` | `{ value: "…" }` | Matches a text pattern. |

`equals` is available wherever the schema supports filtering. `like` is intended for
text fields. Comparison and range predicates are intended for ordered types such as
numbers and dates. Boolean fields generally support exact equality only.

### Exact values and membership

Use `equals` when there is one known value. Use `in` when a single field may match
one of several alternatives.

```graphql
{
  items(filter: {
    enabled: { equals: { value: true } }
    status: { in: { values: ["PUBLISHED", "SCHEDULED"] } }
  }) {
    id
    title
  }
}
```

### Ranges and bounds

`greaterThan` and `lessThan` are exclusive: the bound itself does not match.
`between` is inclusive: both endpoints match.

```graphql
{
  items(filter: {
    score: { between: { from: 70, to: 90 } }
    publishedOn: { greaterThan: { value: "2025-01-01" } }
  }) {
    id
    score
    publishedOn
  }
}
```

For an open range, combine bounds on the same field:

```graphql
{
  items(filter: {
    score: {
      greaterThan: { value: 70 }
      lessThan: { value: 90 }
    }
  }) {
    id
    score
  }
}
```

### Text patterns

`like` uses SQL `LIKE` pattern syntax: `%` represents any sequence of characters and
`_` represents exactly one character.

```graphql
{
  items(filter: {
    title: { like: { value: "Guide%" } }
  }) {
    id
    title
  }
}
```

Use `equals` when the complete value is known. Use `like` only when pattern matching
is intentional. Database collation controls details such as case sensitivity, so
clients should not assume `like` is portable case-insensitive search. Broad patterns,
especially those beginning with `%`, can be expensive to evaluate.

## Combining conditions

All conditions supplied in one filter are combined with **AND**:

- Different fields must all match.
- Multiple predicates on the same field must all match.
- Nested fields participate in the same condition set.

The following illustrative query returns records that are in the `guides` category
**and** have a score above 80:

```graphql
{
  items(filter: {
    metadata: { category: { equals: { value: "guides" } } }
    score: { greaterThan: { value: 80 } }
  }) {
    id
    title
    score
  }
}
```

Do not assume that repeating a field creates an OR condition: GraphQL input-object
fields are unique, and multiple predicates on one field are ANDed. For alternatives
on the same field, use `in`.

Explicit `and`, `or`, and `not` composition is not currently part of the public
filter inputs. Its planned design is described in
[Filter composition](FILTER-COMPOSITION.md), but those fields must not be sent to the
API until they are added to the schema.

## Use variables for dynamic filters

Variables keep filter values separate from the operation text, making an operation
reusable and avoiding query-string construction from user input. Declare the
resource's filter input type, then provide the filter in the request variables.

```graphql
query FindItems($filter: ItemFilter) {
  items(filter: $filter) {
    id
    title
    score
  }
}
```

```json
{
  "filter": {
    "enabled": { "equals": { "value": true } },
    "score": { "between": { "from": 70, "to": 90 } }
  }
}
```

Replace `ItemFilter` with the concrete filter input named by the API schema.

## Result limits and empty results

Collection queries are protected by a configurable result cap. Before fetching the
records, the platform counts how many rows the filter would match. If that count is
above the configured limit, the request is rejected with a structured
`RESULT_SET_TOO_LARGE` error instead of materializing an unbounded response. Narrow
the filter and retry when this occurs.

A valid filter that matches no records is not an error; the collection is returned as
an empty list. The precise result-limit setting and the queries it applies to are
part of the API's operational contract.

## Effective filtering checklist

- Consult the schema before writing a filter; it defines the available fields,
  types, and predicates.
- Start with the most selective field available, then add only conditions required
  for the result.
- Use `equals` for exact values, `between` for inclusive ranges, and `in` for
  same-field alternatives.
- Treat `greaterThan` and `lessThan` as exclusive.
- Supply values in the scalar format required by the schema—for example, ISO dates
  where a `Date` scalar is expected.
- Treat `%` and `_` in a `like` value as wildcards, not literal characters.
- Expect every condition to narrow the result because the default combination is
  AND.
- Use GraphQL variables for dynamic or user-provided values.
- Narrow a query and retry if it reaches the result cap.

## How filtering is processed

The platform validates the GraphQL input shape, parses nested inputs into field paths,
combines the supplied predicates into a criteria set, converts values to the
underlying attribute types when necessary, and builds the data-store query. The
result-limit count uses the same criteria as the collection query, so it reflects the
records that the final query would return.
