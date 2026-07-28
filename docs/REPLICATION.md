# Replication feed — sequence-based polling

How clients import the whole person table and stay in sync by polling, with optional
filtering, without the server keeping any per-client state.

## The model

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

| Piece | Location |
|---|---|
| `sequence` + `deleted` columns | `ReplicatedEntity` (infrastructure, extends `BaseEntity`) |
| Sequence allocation (`person_replication_seq`) | `ReplicationSequences` (infrastructure), invoked by `PersonDal.save` |
| Page partitioning + smart next-sequence contract | `ReplicationPage` (infrastructure) |
| `Long` scalar (sequences overflow 32-bit `Int`) | `LongScalar` + `schema/common.graphqls` (infrastructure) |
| Feed fetch, matching-ids re-check, count, max | `PersonDal` / `PersonService` |
| The three resolvers | `person-service` `graphql/query/` |

A new domain opts in by extending `ReplicatedEntity`, stamping sequences in its DAL's
`save`, and declaring its own `<Resource>ReplicationPage` type + the three fields on
`Query` — the infrastructure pieces are domain-agnostic.
