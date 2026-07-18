# Filtering

Filtering narrows the collection returned by the `persons` query.  It is useful when
you know characteristics of the people you need, such as a city, an employment
status, or a salary range.  The server translates the filter into the database query;
clients do not need to retrieve every person and filter the response themselves.

```graphql
query PeopleInTelAviv {
  persons(filter: {
    address: { city: { equals: { value: "Tel Aviv" } } }
  }) {
    id
    fullName
    address { city }
  }
}
```

The `filter` argument is optional.  Omitting it returns the same collection as an
unfiltered `persons` query, subject to the platform's result cap.  A filter only
affects which `Person` records are returned; it does not change the fields you may
select in the response.

## Start with the filter shape

Every filter has three layers:

1. **A filterable field**, for example `salary` or `address.city`.
2. **A predicate** supported by that field's type, such as `greaterThan` or `like`.
3. **The predicate arguments**, such as `{ value: 500000 }`.

For example, this reads as “people whose salary is greater than 500,000”:

```graphql
{
  persons(filter: {
    salary: { greaterThan: { value: 500000 } }
  }) {
    fullName
    salary
  }
}
```

The predicate is deliberately an object rather than a shorthand value.  This keeps
the API consistent: single-value predicates use `value`, range predicates use `from`
and `to`, and membership predicates use `values`.

## What can be filtered

`PersonFilter` exposes the following stored Person fields:

| Field | Filter type | Notes |
|---|---|---|
| `firstName`, `lastName`, `email`, `nickname` | `StringFilter` | Text matching and membership. |
| `gender` | `StringFilter` | Supply the stored enum code, for example `FEMALE`. |
| `salary`, `weightKg` | `FloatFilter` | Numeric comparisons and ranges. |
| `heightCm` | `IntFilter` | Integer comparisons and ranges. |
| `active` | `BooleanFilter` | Exact true/false matching. |
| `birthDate`, `hireDate` | `DateFilter` | Use ISO dates such as `"1985-12-10"`. |
| `address.street`, `address.city`, `address.zipCode`, `address.country` | `StringFilter` | Place the field beneath `address`. |

Only fields declared in `PersonFilter` and `AddressFilter` are filterable.  Calculated
response fields such as `fullName`, `age`, `monthlyNetSalary`, `yearsOfService`, and
`bmi` are not filters.  Neither are `id`, phone numbers, hobbies, audit timestamps,
or `address.houseNumber`.  If a field is not in the filter input shown by schema
introspection, the API will reject it instead of silently ignoring it.

## Predicates by type

Use the predicate that expresses the question directly.  GraphQL only presents the
predicates valid for a field's filter type.

| Filter type | Available predicates | Meaning |
|---|---|---|
| `StringFilter` | `equals`, `notEquals`, `like`, `in` | Exact comparison, SQL-pattern match, or one-of-many match. |
| `IntFilter`, `FloatFilter`, `DateFilter` | `equals`, `notEquals`, `greaterThan`, `lessThan`, `between`, `in` | Exact comparison, exclusive bounds, inclusive range, or one-of-many match. |
| `BooleanFilter` | `equals` | Exact true/false comparison. |

### Exact value and membership

```graphql
{
  persons(filter: {
    active: { equals: { value: true } }
    gender: { equals: { value: "FEMALE" } }
    firstName: { in: { values: ["Ada", "Grace"] } }
  }) {
    fullName
    gender { code label }
  }
}
```

Use `in` when a field may match one of several values.  It is clearer and more
compact than trying to express alternatives with several `equals` predicates.

### Ranges and bounds

`greaterThan` and `lessThan` are exclusive.  `between` includes both endpoints.

```graphql
{
  persons(filter: {
    heightCm: { between: { from: 160, to: 170 } }
    hireDate: { greaterThan: { value: "2020-01-01" } }
  }) {
    fullName
    heightCm
    hireDate
  }
}
```

For an open range, combine bounds on the same field:

```graphql
{
  persons(filter: {
    salary: {
      greaterThan: { value: 500000 }
      lessThan: { value: 700000 }
    }
  }) {
    fullName
    salary
  }
}
```

### Text patterns

`like` uses SQL `LIKE` syntax: `%` means any sequence of characters and `_` means
exactly one character.  Matching behavior such as case sensitivity is determined by
the database collation, so do not rely on `like` for portable case-insensitive search.

```graphql
{
  persons(filter: {
    lastName: { like: { value: "%ing" } }
  }) {
    fullName
  }
}
```

Choose `equals` when you know the full value.  Use `like` only when pattern matching
is intentional, and prefer a specific prefix or suffix over a leading `%` when you
can; broad patterns can be substantially more expensive for a database to evaluate.

## Combining conditions: implicit AND

All supplied conditions are combined with **AND**:

- Different fields must all match.
- Multiple predicates on one field must all match.
- Nested fields participate in the same AND.

This query returns only people in Tel Aviv **and** taller than 166 cm:

```graphql
{
  persons(filter: {
    address: { city: { equals: { value: "Tel Aviv" } } }
    heightCm: { greaterThan: { value: 166 } }
  }) {
    fullName
    heightCm
    address { city }
  }
}
```

There is currently no `or`, `and`, or `not` field in the public filter inputs.  Do
not assume that repeating a field creates an OR condition; GraphQL input-object fields
are unique, and multiple predicates on that field are ANDed.  For a same-field
alternative, use `in`.  The planned design for explicit, recursive `and`/`or`/`not`
composition is documented in [Filter composition](FILTER-COMPOSITION.md), but it is
not implemented and must not be sent to the API yet.

## Use variables for dynamic filters

Variables keep values separate from the query text and make one operation reusable.
Declare the variable as `PersonFilter` and pass the same filter object in the request
variables.

```graphql
query FindPeople($filter: PersonFilter) {
  persons(filter: $filter) {
    id
    fullName
    salary
  }
}
```

```json
{
  "filter": {
    "active": { "equals": { "value": true } },
    "salary": { "between": { "from": 500000, "to": 700000 } }
  }
}
```

This is especially important for application code: use GraphQL variables rather than
building a query string from user input.

## Result cap and empty results

List queries are intentionally non-paginated in this demo.  Before fetching a list,
the platform counts the rows that the filter would match.  If the count exceeds
`graphql.query.max-results` (100 by default), the request is rejected with the
structured `RESULT_SET_TOO_LARGE` error rather than materializing an unbounded
response.  Narrow the filter and retry when this occurs.

The cap also applies to `allPersons` and `personsByCity`; `personById` is exempt.  A
valid filter that matches no people is not an error: `persons` returns an empty list.

## Effective filtering checklist

- Start with the most selective stored field you have, then add only conditions that
  are required for the result.
- Use `equals` for exact values, `between` for inclusive ranges, and `in` for a set
  of alternatives on one field.
- Treat `greaterThan` and `lessThan` as exclusive; use `between` when endpoint
  inclusion matters.
- Send dates in `YYYY-MM-DD` format and enum codes such as `FEMALE`, not display
  labels.
- Use `like` sparingly and deliberately; `%` and `_` are wildcards, not literal
  characters.
- Expect every supplied condition to narrow the result because conditions are ANDed.
- Use variables for user-provided values, and narrow a query if it hits the result
  cap.
- Inspect the schema in the playground when in doubt: it is the authoritative list
  of currently filterable fields and valid predicate shapes.

## How filtering is processed

The API validates the GraphQL input shape first.  It then parses nested inputs into
field paths (for example, `address.city`), combines the supplied predicates into a
criteria set, converts values to the underlying attribute types where needed, and
builds the database `WHERE` clause.  The count used for the result cap uses that same
filter, so the cap reflects the records the final query would return.

For implementation details or guidance on adding a predicate or filterable field, see
[Extending the service](EXTENDING.md#add-a-filter-predicate-eg-startswith).  For the
future composition design, see [Filter composition](FILTER-COMPOSITION.md).
