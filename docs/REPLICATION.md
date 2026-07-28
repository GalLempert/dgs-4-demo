# Replication feed — sequence-based polling

How clients import the whole person table and stay in sync by polling, with optional
filtering, without the server keeping any per-client state.

## The model

The technical fields live in one aligned hierarchy per representation, so every
resource - current and future - exposes them identically:

| | base ("technical truth") | + replication |
|---|---|---|
| Entity | `BaseEntity` (id, createdAt, updatedAt, `@Version` version) | `ReplicatedEntity` (sequence, deleted) |
| View DTO | `ResourceView` | `ReplicatedResourceView` |
| GraphQL | `interface Resource` | `interface ReplicatedResource implements Resource` |

`Person` and `Company` both declare `implements ReplicatedResource & Resource`, so
clients can select the technical fields through interface fragments
(`... on Resource { id version }`) on any resource type.

Every replicated resource row (entity extending `ReplicatedEntity` in
`graphql-infrastructure`) carries two extra columns:

- **`sequence`** — a per-table, monotonically increasing change number. The DAL stamps
  a fresh value from a native database sequence (`ReplicationSequences`) on **every**
  write — insert, update and delete alike — so a row's sequence always reflects its
  latest change.
- **`deleted`** — soft-delete marker. `deletePerson` flips this flag (and bumps the
  sequence) instead of removing the row, so deletions are changes the feed can carry.
  Regular queries (`persons`, `personById`, `allPersons`, `personsByCity`) exclude
  deleted rows; only the feed and `countPersonsByFilter(includeDeleted: true)` see them.

Because a write *re-assigns* the row's sequence, the feed is naturally compacting: a
row changed five times appears once, at its latest position, with its latest content.

## The four queries

```graphql
persons(filter: PersonFilter): [Person!]!                    # plain filtered read (live rows only)
personsBySequence(sequence: Long!, bulkSize: Int!, filter: PersonFilter): PersonReplicationPage!
countPersonsByFilter(filter: PersonFilter, includeDeleted: Boolean = false): Int!
personMaxSequence: Long!
```

### `personsBySequence` — the feed page

Fetches the next `bulkSize` rows whose sequence is **strictly greater** than
`sequence`, in sequence order (deleted rows included), and partitions them:

```graphql
type PersonReplicationPage {
    updated: [Person!]!        # live rows matching the filter -> upsert locally
    deleted: [Person!]!        # soft-deleted rows matching the filter -> delete locally
    filteredOutIds: [ID!]!     # ids in the page NOT matching the filter -> drop locally
    nextSequence: Long!        # resume point for the next poll
}
```

- **No filter**: every live row of the page is in `updated`, every deleted row in
  `deleted`, `filteredOutIds` is empty.
- **With a filter**: `updated`/`deleted` contain only matching rows. Any other row in
  the page is reported in `filteredOutIds` — the client can't know whether that row
  used to match (it may have just "left" the filter, e.g. a person who moved city), so
  it must drop its local copy. This is what makes filtered replication correct without
  the server tracking what each client has.
- `bulkSize` must be ≥ 1 (`INVALID_ARGUMENT`) and within `graphql.query.max-results`
  (`RESULT_SET_TOO_LARGE`) — the page is the one query allowed to touch deleted rows,
  but it still never materializes more than the cap.

`nextSequence` is the highest sequence in the page. When the page is empty — the
client is caught up, or sent a sequence beyond the table's tail — it **snaps back to
the table's actual maximum sequence** ("smart sequence"). An over-shot client thus
resumes from a real position; since the feed is strictly-greater-than (`>`, never
`>=`), polling with the maximum keeps returning empty pages until the next write, with
no row ever delivered twice.

### `countPersonsByFilter`

Counts without fetching (the result cap does not apply). `includeDeleted: false`
(default) counts what `persons` would return; `true` also counts soft-deleted rows —
useful for a replication client to sanity-check a finished import.

### `personMaxSequence`

The current tail (0 on an empty table). A client that doesn't need history can start
live polling from here instead of importing from 0.

## Client recipe

```text
next := 0                        # full import; or personMaxSequence to start at the tail
loop:
    page := personsBySequence(sequence: next, bulkSize: 100, filter: F)
    upsert page.updated
    delete page.deleted, page.filteredOutIds
    next := page.nextSequence    # equal pages mean "caught up"; keep polling
```

## Where the pieces live

The **entire stack of the four standard queries ships in `graphql-infrastructure`**,
one layer per class:

| Layer | Infrastructure class | What a domain does |
|---|---|---|
| Entity | `ReplicatedEntity` (`sequence` + `deleted` columns) | `class Company extends ReplicatedEntity` |
| View DTO | `ReplicatedResourceView` (technical fields incl. id/version/timestamps) | `class CompanyView extends ReplicatedResourceView` |
| Schema | `interface Resource` / `interface ReplicatedResource` + `GraphQLModelTypeResolver` | `type Company implements ReplicatedResource & Resource` |
| Repository | `ReplicatedRepository<E>` (feed queries, `maxSequence`) | `interface CompanyRepository extends ReplicatedRepository<Company> { }` |
| DAL | `ReplicatedDal<E>` (capped filtered reads, feed reads, sequence stamping on save, soft-delete visibility) + `ReplicatedDalSupport` | subclass names the resource + DB sequence in a 2-line constructor |
| Service | `ReplicatedResourceService<E, V>` (feed orchestration/partitioning, count, max sequence, `softDelete`) | subclass implements `toView(entity)` |
| Resolvers | `ReplicationResolverFactory` (manufactures all four query resolvers) | one `@Bean` per schema field, one line each |
| Protocol | `ReplicationSequences`, `ReplicationPage`, `LongScalar` + `scalar Long` | nothing — used internally |

The `person-service` and `company-service` modules are both wired exactly this way;
`company-service` is the minimal reference (its DAL and repository bodies are empty).

## Adding a replicated domain (what `company-service` actually contains)

1. Entity extends `ReplicatedEntity`; view extends `ReplicatedResourceView` (the
   technical fields are inherited on both sides and mapped by name).
2. `interface XRepository extends ReplicatedRepository<X> { }`
3. `class XDal extends ReplicatedDal<X>` — constructor calls
   `super("X", "x_replication_seq", repository, support)`.
4. `class XService extends ReplicatedResourceService<X, XView>` — implements
   `toView`; add domain-specific operations (e.g. creation) as needed.
5. A `@Configuration` with one `@Bean GraphQLResolver` per standard query, built by
   `ReplicationResolverFactory` (`filteredList` / `bySequence` / `countByFilter` /
   `maxSequence`) + a `GraphQLModelSource` bean for the view class.
6. `schema/<domain>.graphqls` declaring the fields (via `extend type Query` when
   another module already declares the base type), the resource type as
   `type X implements ReplicatedResource & Resource` (repeating the interface fields,
   as GraphQL requires), the `<X>ReplicationPage` type, and the `<X>Filter` input.

No resolver, DAL, or service logic is written for the four queries themselves.
