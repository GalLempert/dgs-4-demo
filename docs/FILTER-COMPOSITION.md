# Filter composition (`and` / `or` / `not`) — design

**Status: agreed design, not yet implemented.** Today's filter system combines
everything with an implicit AND. This document records how explicit logical
composition will work when added, and why it is shaped this way.

## Current semantics (implemented)

- Multiple **fields** in one filter → AND: the row must match every field filter.
- Multiple **predicates on one field** → AND: `heightCm: { greaterThan: {value: 160},
  lessThan: {value: 170} }` is a range.
- `FilterCriteria.Combinator` already models AND/OR (enum-as-strategy over
  `CriteriaBuilder.and/or`), but OR is not reachable from the schema.

## Decision 1: keep AND as the uniform default at every level

Considered and rejected: different defaults per level (e.g. OR between predicates of
the same field, AND across fields).

- The dominant same-field combination is a **range** (`greaterThan` + `lessThan`),
  which only makes sense as AND.
- The common same-field OR ("equals A or equals B") already has a clearer predicate:
  `in`.
- Every system users' intuition is trained on — SQL `WHERE`, MongoDB
  (`{age: {$gt: 5, $lt: 10}}` is AND), Prisma, Hasura, JPA — defaults to AND at every
  level. Asymmetric defaults are a least-surprise violation waiting to become an
  incident (a "range" that silently matches everything).

## Decision 2: OR/NOT arrive as explicit, recursive composition — not as a mode switch

Considered and rejected:

- **Per-field mode knob** (`heightCm: { mode: OR, ... }`): awkward, rarely needed,
  `in` covers the common case.
- **Global combinator argument** (`persons(filter: ..., combinator: OR)`): too blunt —
  real queries mix ANDs and ORs; a single switch cannot express `(a AND b) OR c`.

Chosen (Prisma/Hasura-style): three composition fields on the **resource root
filter**, recursive, each branch being a full filter of the same type:

```graphql
input PersonFilter {
    firstName: StringFilter
    heightCm:  IntFilter
    address:   AddressFilter
    ...
    and: [PersonFilter!]     # every branch must match
    or:  [PersonFilter!]     # at least one branch must match
    not: PersonFilter        # the branch must not match
}
```

Implicit AND stays the default *inside every level*, so composition is strictly
opt-in and fully backward compatible:

```graphql
{ persons(filter: {
    active: { equals: { value: true } }              # ANDed with the or-block
    or: [
      { address: { city: { equals: { value: "Tel Aviv" } } },
        heightCm: { greaterThan: { value: 160 } } }  # branch = implicit AND
      { address: { city: { equals: { value: "Haifa" } } } }
    ]
}) { fullName } }

-- WHERE active = true
--   AND ( (city = 'Tel Aviv' AND height_cm > 160) OR city = 'Haifa' )
```

## The schema problem: SDL has no generics

`input Filter<T>` does not exist, and input objects cannot implement interfaces — so
the self-referencing `and: [PersonFilter!]` fields must be declared per resource
filter type. Two ways to get there; the runtime behavior is identical and fully
generic in both (the parser treats `and`/`or`/`not` as reserved keys wherever they
appear, without knowing the resource):

### Option A — explicit convention (recommended start)

Each domain adds the three lines to its resource filter (as shown above). Honest SDL,
zero magic, three lines of boilerplate per resource.

### Option B — `@composable` directive + programmatic rewriting (follow-up)

Infrastructure declares `directive @composable on INPUT_OBJECT` in
`common.graphqls`; domains write only:

```graphql
input PersonFilter @composable {
    firstName: StringFilter
    ...
}
```

At startup an infrastructure hook post-processes the `TypeDefinitionRegistry` (the
same object the dispatch controller already receives) and appends the three
self-referencing fields to every input marked `@composable`. Introspection — and
therefore the playground and client codegen — sees the final concrete fields as if
hand-written. Pure infrastructure addition; does not change the parser, the criteria
model, or any client. Adopt when the three-line ritual across several domains starts
to annoy.

## Scoping rules

- Composition lives **only on resource-root filters**. The typed leaf filters
  (`StringFilter`, `IntFilter`, ...) in `common.graphqls` keep their predicates ANDed;
  alternatives are expressed with `in` or a root-level `or`.
- Nested object filters (`AddressFilter`) stay plain — alternation lifts to the root:
  `or: [{address: ...}, {address: ...}]`. Mental model: *predicates constrain, the
  root composes*.
- `and` / `or` / `not` become **reserved names** on composable filter inputs: an
  entity field with one of those names cannot be filterable under its own name.
  (Hasura avoids this with `_and`/`_or`; we accept the clean names and the rare
  restriction.)
- Recursive input types are legal GraphQL as long as the recursive fields are
  nullable (they are). The playground's skeleton generator already caps input
  expansion depth, so the self-reference cannot loop it.

## Implementation sketch

1. **Criteria model** becomes a composite: a filter node is either a leaf
   (`FieldFilter`: path + predicate + arguments) or a logical node (combinator +
   child nodes). `Combinator` gains `NOT` (unary), keeping the enum-as-strategy
   style over `CriteriaBuilder.and/or/not`.
2. **`FilterParser`**: at a composable level, the reserved keys parse into logical
   nodes (each list entry recursing as a full filter); all sibling entries — field
   filters and logical nodes alike — fold into the implicit AND of that level.
3. **`FilterSpecificationBuilder`**: tree recursion; leaves resolve exactly as today.
4. **Schema**: Option A lines on `PersonFilter`.
5. **Tests**: parser tree shapes (or-branches, not, nesting, reserved-key/field
   clash), builder unit tests per combinator, integration scenarios asserting SQL
   semantics against the seeded data, cap interaction (COUNT uses the same composed
   WHERE).

Everything existing keeps working unchanged: a filter without composition keys parses
to the same flat AND criteria as today.
