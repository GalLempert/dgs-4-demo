# Dynamic filtering in the replication feed

How `personsBySequence(sequence, bulkSize, filter)` lets every client replicate its
own arbitrary subset of a table — any filter the schema allows, different per client,
changeable without any server-side setup — while the server keeps zero per-client
state. This page explains the mechanism and why it is correct; the feed protocol
itself (sequences, soft deletes, cursors) is in [REPLICATION.md](REPLICATION.md), and
the filter language (fields, predicates, AND semantics) is in
[FILTERING.md](FILTERING.md).

## What "dynamic" means here

The feed takes the **same filter input as the plain list query** — `persons(filter:)`
and `personsBySequence(filter:)` both accept `PersonFilter`. Nothing about a filtered
feed is declared, registered, or provisioned in advance:

- Any filterable field/predicate combination the schema exposes works, including
  nested paths (`address: { city: { equals: { value: "Berlin" } } }`).
- Every client picks its own filter, per request. The server never learns "client X
  replicates Berlin" — the filter arrives with each poll and is forgotten after it.
- The subset is defined by **current row content**, not by a static partition key.
  That is the hard part: rows *move in and out* of a content-based subset as they are
  updated, and a correct feed has to make both directions visible.

## The problem: rows that leave the filter

A naive filtered feed would just add the filter to the page query:

```sql
-- WRONG: filter applied to the page query itself
WHERE sequence > :cursor AND city = 'Berlin' ORDER BY sequence LIMIT :bulkSize
```

Suppose a client replicates Berlin persons and person 42 (a Berlin row the client
holds) is updated to Munich. The update bumps 42's sequence, but the new row no
longer matches the WHERE clause — the change is **silently invisible** to this
client, which keeps its stale Berlin copy of 42 forever. Deletions of
formerly-matching rows would vanish the same way. Fixing that server-side would
require remembering what each client has — exactly the per-client state this
protocol refuses to keep.

## The mechanism: page by sequence, then partition

The feed therefore never lets the filter touch pagination. One page is produced in
three steps (`ResourceService.getBySequence` in `graphql-infrastructure`):

1. **Fetch the page by sequence only.** `ResourceDal.findBySequenceAfter` reads the
   next `bulkSize` rows with `sequence > :cursor`, in sequence order, soft-deleted
   rows included, filter ignored. Every change in the table enters the page stream
   exactly once, for every client, whatever their filter.
2. **Re-check the page's rows against the filter.** For a non-empty filter,
   `ResourceDal.matchingIds` runs one query — `id IN (page ids) AND <filter>` — using
   the **same dynamically built WHERE clause as everywhere else**
   (`FilterSpecificationBuilder` turning the parsed `FilterCriteria` into a JPA
   `Specification`), except that unlike regular reads it does **not** add the
   `deleted = false` guard: a deleted row still has content, and the client only
   needs to erase it if it matches the filter. The result is the set of matching ids
   within this page. With no filter (or an empty page) the second query is skipped
   and every row counts as matching.
3. **Partition.** `ReplicationPage.partition` walks the page in sequence order and
   routes each row:

   | Row is… | Lands in | Client action |
   |---|---|---|
   | matching + live | `updated` (full view) | upsert locally |
   | matching + soft-deleted | `deleted` (full view) | delete locally |
   | not matching | `filteredOutIds` (id only) | drop local copy if any |

`nextSequence` is computed from the raw page (its highest sequence) **before** any
filtering, so the cursor is filter-independent: all clients walk the identical
change stream and simply project different subsets out of it.

The filter argument itself goes through the standard pipeline on the way in: the
manufactured `bySequence` resolver (`ReplicationResolverFactory`) hands the raw
GraphQL input to `FilterParser`, which flattens nested inputs into dotted field
paths and predicate leaves — one `FilterCriteria` tree, the same object the plain
list, count, and filtered-mutation paths consume.

## Why this is correct

- **Entering the filter** needs no special handling: when a Munich row is updated to
  Berlin, that write bumps its sequence, the row appears in a page, now matches, and
  arrives in `updated` with full content.
- **Leaving the filter** is what `filteredOutIds` exists for: the row still appears
  in the page (pagination ignored the filter), fails the re-check, and its id is
  reported. The client cannot tell whether the row used to match — and does not need
  to: "delete if present" is idempotent, and a client that never had the row drops
  nothing. This is the trick that makes filtered replication correct with a
  stateless server — the server ships a per-page "you may need to forget this id"
  signal instead of remembering who has what.
- **Deletions of matching rows** arrive as full views in `deleted` because the
  re-check includes soft-deleted rows; a filtering client erases exactly the deleted
  rows that belong to its subset and treats the rest as filtered-out ids.
- **Non-matching rows leak only their id.** `filteredOutIds` carries no content, so
  a client authorized to see only its subset never receives another subset's data —
  just opaque ids it has no local copy of.

## Consequences to be aware of

- **`bulkSize` counts scanned changes, not matches.** A page may contain zero
  matching rows — `updated` and `deleted` empty, `filteredOutIds` full — and that is
  a normal, productive page: the cursor advanced. Clients with a narrow filter
  should size `bulkSize` for stream throughput, not for expected matches (it is
  still capped by `graphql.query.max-results`).
- **The filter sees current content, once per page.** Because a write re-assigns the
  row's sequence, the feed is compacting: intermediate states between two polls are
  never observed, and match/no-match is decided against the state the page read. A
  row that briefly matched between polls is simply never seen matching.
- **Keep the filter stable for the lifetime of a cursor.** The server evaluates only
  the filter sent with each poll. A client that *narrows* its filter mid-stream
  keeps stale rows that no longer match until each such row happens to change (only
  then is it reported filtered-out); one that *widens* it misses rows that already
  matched the wider filter but have not changed since the cursor position. After
  changing a filter, restart from sequence 0 (or reconcile against
  `countPersonsByFilter`).
- **Cost:** a filtered page is two statements — the sequence-page read plus one
  `id IN (…) AND <filter>` re-check bounded by `bulkSize`; an unfiltered page is
  one. The count/max-sequence queries are unaffected by the feed's filter.

## Worked example

Client replicates Berlin persons with cursor 340. Since its last poll: person 7
(Berlin) was updated, person 42 moved Berlin → Munich, person 55 (Berlin) was
deleted, person 61 (Hamburg) was updated.

```graphql
{
  personsBySequence(
    sequence: 340
    bulkSize: 100
    filter: { address: { city: { equals: { value: "Berlin" } } } }
  ) {
    updated { id firstName address { city } sequence }
    deleted { id sequence }
    filteredOutIds
    nextSequence
  }
}
```

```json
{
  "personsBySequence": {
    "updated":        [ { "id": "7",  "firstName": "Ada", "address": { "city": "Berlin" }, "sequence": 341 } ],
    "deleted":        [ { "id": "55", "sequence": 343 } ],
    "filteredOutIds": [ "42", "61" ],
    "nextSequence": 344
  }
}
```

Person 7 is upserted, 55 deleted, and 42 dropped — the client has no way to know 42
"left" rather than "never matched", and drops it either way, which is exactly right.
Id 61 never matched and is a no-op drop. A Hamburg-filtered client polling the same
range gets the mirror image (61 in `updated`; 7, 42, 55 in `filteredOutIds`) from
the identical underlying page, and both end up at `nextSequence: 344`.

## Where the pieces live

All of it ships domain-agnostically in `graphql-infrastructure`; a domain module
contributes nothing beyond its `<X>Filter` schema input and the standard wiring
(see [REPLICATION.md](REPLICATION.md) "Where the pieces live"):

| Step | Class (infrastructure) |
|---|---|
| Parse raw `filter` arg → `FilterCriteria` | `FilterParser` (via the manufactured `bySequence` resolver in `ReplicationResolverFactory`) |
| Page read by sequence, filter ignored | `ResourceDal.findBySequenceAfter` |
| Re-check page ids against the filter (deleted included) | `ResourceDal.matchingIds` + `FilterSpecificationBuilder` |
| Orchestrate + compute `nextSequence` | `ResourceService.getBySequence` |
| Partition into updated / deleted / filteredOutIds | `ReplicationPage.partition` |

Because the re-check reuses the one `FilterCriteria` → `Specification` pipeline,
every filtering extension is automatically a replication-filtering extension: add a
field to the filter input, or register a new `FilterPredicateStrategy` bean
([EXTENDING.md](EXTENDING.md)), and the feed's dynamic filter supports it with no
replication-specific code.
