# Replication feed — sequence-based polling

How clients import the whole person table and stay in sync by polling, with optional
filtering, without the server keeping any per-client state.

## The model

Every resource is a replicated resource, so the technical fields - id, `@Version`
version, createdAt, updatedAt, sequence, deleted - live in a single base per
representation and every resource, current and future, exposes them identically:

| Representation | Single base ("technical truth") |
|---|---|
| Entity | `BaseEntity` |
| View DTO | `ResourceView` |
| GraphQL | `interface Resource` |

`Person` and `Company` both declare `implements Resource`, so clients can select the
technical fields through interface fragments (`... on Resource { id version sequence }`)
on any resource type.

Every resource row (entity extending `BaseEntity` in `graphql-infrastructure`)
carries two replication columns:

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

`nextSequence` is the highest sequence in the page. On an empty page the cursor
**never moves forward** — it only **snaps an over-shot cursor down to the table's
actual maximum sequence** ("smart sequence"): a client that sent a sequence beyond the
tail resumes from a real position, while a caught-up client keeps its own cursor.
Advancing an empty page's cursor to a maximum read in a separate query could skip a
row committed between the two reads — see "Correctness under concurrency" below.
Since the feed is strictly-greater-than (`>`, never `>=`), polling with the maximum
keeps returning empty pages until the next write, with no row ever delivered twice.

## Correctness under concurrency

Two guarantees make the feed lossless when writers and pollers overlap:

- **Sequence order matches commit-visibility order.** A database sequence alone
  guarantees unique allocation, not commit order: T1 could allocate 1, stall, and
  commit after T2 already committed 2 — a poll would advance past 2 and never see 1.
  To prevent this, `ReplicationSequences.next()` first takes a row lock on the
  resource's entry in the `replication_write_lock` table (one row per resource,
  created at startup). Row locks are held until the transaction ends, so writers of
  the *same* resource serialize: nobody allocates the next sequence until the previous
  writer committed or rolled back. Writes to different resources are unaffected; a
  rolled-back write leaves a harmless gap.
- **An empty page never advances the cursor.** The page query and the max-sequence
  query are separate reads; a write committing between them could otherwise be
  advertised as `nextSequence` without its row ever having been returned. The cursor
  therefore only moves forward along rows the client actually received
  (`min(requestedSequence, maxSequence)` on empty pages).

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
| Entity | `BaseEntity` (technical fields incl. `sequence` + `deleted`) | `class Company extends BaseEntity` |
| View DTO | `ResourceView` (technical fields incl. id/version/timestamps) | `class CompanyView extends ResourceView` |
| Schema | `interface Resource` + `GraphQLModelTypeResolver` | `type Company implements Resource` |
| Repository | `ResourceRepository<E>` (feed queries, `maxSequence`) | `interface CompanyRepository extends ResourceRepository<Company> { }` |
| DAL | `ResourceDal<E>` (capped filtered reads, feed reads, sequence stamping on save, soft-delete visibility) + `ResourceDalSupport` | subclass names the resource + DB sequence in a 2-line constructor |
| Service | `ResourceService<E, V>` (feed orchestration/partitioning, count, max sequence, `softDelete`) | subclass implements `toView(entity)` |
| Resolvers | `ReplicationResolverFactory` (manufactures all four query resolvers) | one `@Bean` per schema field, one line each |
| Protocol | `ReplicationSequences`, `ReplicationPage`, `LongScalar` + `scalar Long` | nothing — used internally |

The `person-service` and `company-service` modules are both wired exactly this way;
`company-service` is the minimal reference (its DAL and repository bodies are empty).

## Adding a replicated domain (what `company-service` actually contains)

1. Entity extends `BaseEntity`; view extends `ResourceView` (the technical fields are
   inherited on both sides and mapped by name).
2. `interface XRepository extends ResourceRepository<X> { }`
3. `class XDal extends ResourceDal<X>` — constructor calls
   `super("X", "x_replication_seq", repository, support)`.
4. `class XService extends ResourceService<X, XView>` — implements
   `toView`; add domain-specific operations (e.g. creation) as needed.
5. A `@Configuration` with one `@Bean GraphQLResolver` per standard query, built by
   `ReplicationResolverFactory` (`filteredList` / `bySequence` / `countByFilter` /
   `maxSequence`) + a `GraphQLModelSource` bean for the view class.
6. `schema/<domain>.graphqls` declaring the fields (via `extend type Query` when
   another module already declares the base type), the resource type as
   `type X implements Resource` (repeating the interface fields, as GraphQL
   requires), the `<X>ReplicationPage` type, and the `<X>Filter` input.

No resolver, DAL, or service logic is written for the four queries themselves.
