# Review: dynamic filtering in the replication feed

**Subject.** `docs/REPLICATION-FILTERING.md` as of commit `2439702` on branch
`claude/dynamic-filtering-replication-tjf7qj` (a documentation-only branch, no pull
request yet), and the mechanism it describes, which already ships in
`graphql-infrastructure`: `ResourceService.getBySequence` →
`ResourceDal.findBySequenceAfter` + `ResourceDal.matchingIds` →
`ReplicationPage.partition`.

**Method.** The doc was read against the code, then a throwaway experiment was run
against person-service (H2 1.4.200 in-memory, READ COMMITTED, 10,000 rows) to verify
every behavioral claim end to end, count SQL statements per page, inspect query plans
with and without an index, and time page walks. The experiment was not committed; its
scenarios are listed in Appendix B as candidate integration tests.

## Verdict

The design is right for its goals and the doc describes the mechanism faithfully.
"Page by sequence, re-check the page against the filter, partition, ship
`filteredOutIds`" is the correct stateless answer to rows leaving a content-based
subset, and every behavioral claim held up under test: a row entering the filter, a
row leaving it, a matching row being deleted, the mirror image seen by a client with a
different filter, and zero-match pages that still advance the cursor.

What the doc under-describes is cost and operational edge cases. Statelessness is paid
for by every client on every poll: a client pays for the whole change stream no matter
how selective its filter is, and today each page costs more than it needs to (no index
on `sequence`, two lazy collection loads per delivered row, a second round trip for
the re-check). None of that is a design flaw and none of it requires a protocol change.

Three sentences in the doc overclaim and should be corrected: "every change enters the
page stream exactly once", "a filtered page is two statements", and the sentence that
presents the filter as an authorization boundary. Details in the accuracy section.

Recommendations, in priority order (full list in the Recommendations section):

1. Index `sequence` on every resource table.
2. Batch-fetch lazy collections so a feed page is 3 statements, not 201.
3. Fold the re-check into the page query (one statement, one snapshot).
4. Give the feed its own page-size cap, decoupled from `graphql.query.max-results`.
5. Add the missing integration tests (leave, enter, delete-while-matching, zero-match page).
6. Fix the three overclaims and add the missing caveats to the doc.

## What was verified

| Claim in the doc | Evidence | Result |
|---|---|---|
| The page is read by sequence only, deleted rows included, filter ignored | `ResourceDal.findBySequenceAfter`; emitted SQL `… where sequence>? order by sequence asc limit ?` | holds |
| The re-check is one `id IN (…) AND <filter>` query without the `deleted = false` guard | `ResourceDal.matchingIds`; Hibernate statistics show exactly one extra statement per filtered, non-empty page | holds |
| No filter, or an empty page, skips the re-check | `ResourceService.matchingIds` | holds, but an empty page still runs the max-sequence query: 2 statements, not 1 |
| The partition walks the page in sequence order | `ReplicationPage.partition` iterates the ordered batch; `filteredOutIds` came back as `[5, 4, 6]` for sequences 7, 8, 9 | holds |
| `nextSequence` is filter-independent | Computed from the raw batch before partitioning; Berlin and Munich clients both got `nextSequence: 9` from the same range | holds |
| A row leaving the filter is reported in `filteredOutIds` | Rev2 moved Berlin → Munich: id 5 in `filteredOutIds` for the Berlin client, in `updated` for the Munich client | holds |
| A row entering the filter arrives in `updated` with full content | Rev3 moved Munich → Berlin: in `updated` with `address.city = "Berlin"` | holds |
| A matching row that is deleted arrives in `deleted` with full content | Rev1 deleted while in Berlin: in `deleted`, content still `Berlin` | holds |
| A zero-match page is a productive page | Filter on a city nobody lives in: all three ids in `filteredOutIds`, `nextSequence` advanced to 9 | holds |
| Any predicate the schema exposes works, nested paths included | `like` on `firstName` and `equals` on `address.city` through the feed | holds |
| "Cost: a filtered page is two statements" | Statistics: 2 statements for the feed itself, 102 for a page of 50 Person rows once views are mapped | misleading, see Performance |
| "Every change in the table enters the page stream exactly once, for every client" | Compaction means intermediate changes never enter it; the documented allocation-vs-commit anomaly can drop a change entirely | overclaim, see Accuracy |
| "A client authorized to see only its subset never receives another subset's data" | Nothing in the stack forces a filter; `filter` is optional on `personsBySequence` | overclaim, see Accuracy |

The worked example in the doc (7 updated, 42 moved away, 55 deleted, 61 never matched)
is exactly the shape the experiment produced, so it can stay as is.

## Strengths

- **Correct without server state.** The only thing the server needs to know about a
  client is the cursor it sends. Leave-detection, normally the hard part of filtered
  replication, needs no history table, no per-client subscription, no "previous
  value" tracking.
- **Compaction for free.** Because a write re-assigns the row's sequence, a row
  changed ten times between polls is shipped once, in its latest state. Clients never
  see intermediate states, and the stream never grows faster than the table.
- **Zero domain code.** company-service gets a fully filtered feed with no resolver,
  DAL or service code. Any field added to `<X>Filter` and any new
  `FilterPredicateStrategy` bean is immediately usable in the feed.
- **One predicate pipeline.** The feed's re-check uses the same `FilterCriteria` →
  `Specification` path as the list, count and filtered mutations. There is no second
  evaluator that could drift from SQL semantics (collation, `LIKE`, NULLs). This is
  the strongest reason to keep the re-check in SQL rather than in Java.
- **Filter-independent cursor.** Every client walks the same stream, so cursors are
  comparable across clients, `personMaxSequence` means the same thing for everyone, and
  a client can change nothing but its filter and reason about what happens.
- **Minimal leakage by construction.** Non-matching rows contribute an id and nothing
  else. Deleted rows are re-checked by content, so a tombstone only reaches the
  clients whose subset it belonged to.
- **Idempotent client operations.** Upsert, delete and drop-by-id are all safe to
  replay, so a client can retry a failed page at the same cursor with no bookkeeping.

## Performance

### Cost model

Per poll, a client costs the server one page read (`sequence > cursor ORDER BY
sequence LIMIT bulkSize`), one re-check (primary-key lookup of at most `bulkSize` ids
with the filter applied) and the view mapping of every delivered row. Work per poll is
proportional to `bulkSize` scanned rows regardless of how selective the filter is. The
number of polls needed to traverse a range of the stream is the number of rows in that
range divided by `bulkSize`, regardless of how many match. With N clients the server
performs N full traversals of the same change stream, and `bulkSize` is capped at
`graphql.query.max-results` (100 by default).

### Measured

Setup: person-service, H2 1.4.200 in-memory, JDBC isolation READ COMMITTED, single
thread, 10,003 person rows, `bulkSize` 100, each walk covering the same 10,000 rows
(100 pages). The 5% filter matches 500 rows, the 95% filter 9,500. Warm-up walks ran
before measuring; series A ran without an index on `sequence`, B with one, C after
dropping it again.

Statements per page (50 Person rows, each with one phone number and two hobbies):

| Page | SQL statements | Collection fetches |
|---|---|---|
| Unfiltered, 50 delivered | 101 | 100 |
| Filtered, 50 of 50 match | 102 | 100 |
| Filtered, 0 of 50 match | 2 | 0 |
| Filtered, empty batch (cursor at tail) | 2 (page read + max sequence) | 0 |
| DAL page read alone | 1 | 0 |

Milliseconds per page over 100 pages:

| Walk | A: no index | B: index | C: no index |
|---|---|---|---|
| DAL: page read only | 4.55 | 0.71 | 2.79 |
| DAL: page read + re-check, 5% filter | 3.47 | 2.19 | 3.11 |
| Service: unfiltered (201 statements per page) | 8.22 | 5.41 | 5.50 |
| Service: filter matches 95% (192 statements per page) | 5.82 | 3.90 | 5.98 |
| Service: filter matches 5% (12 statements per page) | 3.10 | 1.28 | 3.02 |

For contrast only: a plain SQL pushdown of the 5% filter (`… AND address_city = ?`)
with the index needed 5 pages and 10 ms for the same 500 rows. That query is not a
valid replacement (it misses rows that leave the filter), but it shows the round-trip
price of leave-detection: 100 polls instead of 5.

Query plans (H2 `EXPLAIN`): without an index the page query is
`/* PUBLIC.PERSON.tableScan */ … ORDER BY 4 FETCH FIRST 100 ROWS ONLY`; with an index
it is `/* IDX…: SEQUENCE > 3 */ … /* index sorted */`. The re-check uses the primary
key (`/* PRIMARY_KEY_8: ID IN(…) */`).

Absolute numbers are indicative only (in-memory database, single thread, sub-millisecond
noise). The plan change and the statement counts are the robust findings.

### Findings

1. **There is no index on `sequence`.** `BaseEntity` is a `@MappedSuperclass` and
   cannot declare one; neither `Person` nor `Company` declares one; Hibernate's DDL
   creates none. Every poll therefore scans and sorts the whole table to find the next
   100 rows. The index makes the page read 4 to 6 times cheaper at 10,000 rows and,
   more importantly, stops the cost growing with table size.
2. **View mapping, not the re-check, dominates a page.** `PersonMapper.toView` maps
   through Jackson, which reads `phoneNumbers` and `hobbies`, both `LAZY`: two extra
   statements per delivered row, 201 statements for an unfiltered page of 100. On an
   in-memory database this roughly doubles the page time; on a networked database
   200 extra round trips per page would dominate everything else. Narrow filters are
   cheap today precisely because they deliver few rows.
3. **The re-check is cheap but wasteful.** It is a primary-key `IN` lookup, so it
   costs about one page read, but it selects every column of the matching rows
   (`repository.findAll(specification)`) to read only their ids, and Hibernate inlines
   the ids as literals (`id in (4 , 5 , 6)`), so every page produces a distinct SQL
   text. On databases with a plan cache (Oracle, SQL Server) that is a hard parse per
   poll.
4. **Selectivity buys nothing in round trips.** A client replicating 5% of a table
   makes exactly as many polls as a client replicating all of it. That is inherent to
   the design and acceptable for the intended scale; the alternatives section covers
   what to do when it is not.

## Accuracy, reliability and edge cases

### Two statements, two snapshots

The page read and the re-check are separate statements in one read-only transaction
under READ COMMITTED (verified: isolation level 2), so a write committing between them
is visible to the second statement but not the first. Hibernate's first-level cache
then returns the already-loaded entities for the re-check's rows without refreshing
them, so the match verdict comes from the newer snapshot and the shipped content from
the older one.

Example: a Berlin client's page loads person 42 as Munich; before the re-check, 42 is
updated to Berlin. The re-check says "matches", and the client receives a `Munich`
row in `updated`. The next poll delivers 42 again with Berlin content, because the
concurrent write got a higher sequence, so the replica heals. It does not heal if that
write's sequence was allocated before the page read and committed after it, which is
the documented allocation-versus-commit anomaly. Folding the re-check into the page
query (recommendation 3) removes this window entirely.

### Inherited: sequence allocation versus commit order

`ReplicationSequences` documents that a transaction can allocate a lower sequence and
commit after a poll advanced past a higher one, so its change is never delivered. For
an unfiltered client that is a stale version until the row changes again. For a
filtered client the lost change may be the one that moved the row out of the subset,
so the local replica keeps a row that should not be there at all. The
REPLICATION-FILTERING doc claims "exactly once" without referencing this caveat; it
should link to "Correctness under concurrency" in REPLICATION.md, and the outbox
work planned there matters more once filtering is in use.

### Sequence reset

The protocol has no epoch. In this demo the database is in-memory with
`ddl-auto: create-drop`, so a restart resets ids and sequences to 1 and re-seeds. A
client that polls with its old cursor gets `nextSequence` snapped down to the new
maximum and an empty page, keeps its stale rows, and never receives the re-seeded rows
1 to 3 because they sit below the snapped cursor. Nothing tells it that the stream it
is following is a different stream. The production analogue is a restore from backup
or a re-created environment. A per-table feed epoch returned with every page (a random
value stored next to the sequence, reset whenever the table is rebuilt) would let
clients detect this and resync from 0.

### The filter is not an authorization boundary

`filter` is optional on `personsBySequence`. Any client can omit it and receive
everything, so the sentence "a client authorized to see only its subset never receives
another subset's data" is only true if something outside this stack forces a filter,
and nothing in the repository does. Even with a forced filter, `filteredOutIds`
discloses every row id in the table and the full write rate. The doc should present
the filter as a subsetting mechanism and say that authorization must be layered
separately.

### SQL NULL semantics reach the client

The re-check runs in SQL, so three-valued logic applies. Verified: with the filter
`nickname: { notEquals: { value: "zzz" } }`, a person whose nickname is NULL is
reported in `filteredOutIds`, and a filtering client will drop it. This is consistent
with `persons(filter:)` and therefore correct, but a client author who reads
`notEquals` as "everything except" will be surprised. The same applies to
`greaterThan`, `lessThan` and `between` on nullable columns. Worth one sentence in the
doc.

### One replica per filter

Because OR composition is not implemented, a client that wants "Berlin persons or
inactive persons" must run two feeds. Merging two filtered feeds into one local table
is unsafe: a Hamburg inactive person arrives in `updated` from the inactive feed and in
`filteredOutIds` from the Berlin feed; whichever page the client applies last wins,
and if it is the Berlin page the row is wrongly deleted. The safe options are one
feed per local table, an unfiltered feed filtered locally, or (once available) a
single feed with an `or` filter. The doc should state the rule explicitly: one local
replica per (feed, filter) pair.

### Filter changes are undetectable

The doc's advice to keep the filter stable is correct, and the server cannot enforce it
because it keeps no state. The practical mitigation is client-side and cheap: store the
filter next to the cursor, and treat any difference as "restart from 0". The doc
should say that instead of leaving it as a warning.

### Tombstones are forever

Soft-deleted rows are never purged, so a fresh import scans every tombstone the table
ever accumulated and reports each one in `deleted` or `filteredOutIds` to a client that
never had it. Filtering does not make this worse, but it does make initial imports of
narrow subsets pay for it in full. The eventual answer is a retention policy plus an
"oldest safe cursor" the client compares its cursor against; until then it is a
scaling limit to be aware of.

### Smaller observations

- `bulkSize: 1000` fails with "Query matches 1000 Person rows, exceeding the maximum of
  100 - narrow the filter", with `field: filter`. The cap is reused for a page-size
  check and the message describes the wrong thing.
- The `IN` list is bounded by `graphql.query.max-results`. Raising that property above
  roughly 1,000 would break the re-check on Oracle (1,000-expression limit) and stress
  SQL Server's 2,100-parameter limit.
- Tombstone content is frozen: every write path (`update`, `saveOrOverride`,
  `softDelete`) reads live rows only, so a deleted row's filter verdict can never
  change. That is what makes "erase it if it matches" well defined.
- A negative `sequence` is accepted and behaves like 0. Harmless, but an explicit
  `INVALID_ARGUMENT` would be cheaper to debug than an unexpected full import.
- A row created and deleted between two polls is delivered once, as a tombstone or a
  filtered-out id, to a client that never had it: a wasted row but a correct one.

## Ease of use

**For a domain developer: excellent.** Extending `BaseEntity`, subclassing the DAL and
service, and declaring `<X>Filter` in the schema is the whole job; the feed's filter
support is inherited, and adding a filterable field or a predicate is one schema line
or one bean. The one design question the domain must not ignore is which filterable
fields are mutable, because that determines whether the pushdown hybrid in the
alternatives section is ever applicable.

**For a client author: moderate.** A correct filtered client must:

- keep the cursor and the filter together and restart from 0 when the filter changes;
- apply three operations per page (upsert `updated`, delete `deleted`, delete
  `filteredOutIds`), all idempotent;
- treat a page with empty `updated` and `deleted` but a non-empty `filteredOutIds` as
  progress, not as "caught up";
- detect "caught up" as `nextSequence` equal to the sent sequence, and "more to come"
  as a page whose three parts together hold `bulkSize` ids;
- keep one local replica per filter and never merge feeds;
- know that `bulkSize` is scanned rows, is capped at 100, and cannot be raised to
  speed up a narrow filter.

The doc covers the first three and partly the fourth. The filtered client recipe
below could replace the unfiltered one in REPLICATION.md.

```text
state := { cursor: 0, filter: F }          # persist both together
loop:
    page := personsBySequence(sequence: state.cursor, bulkSize: 100, filter: state.filter)
    upsert page.updated
    delete page.deleted, page.filteredOutIds
    state.cursor := page.nextSequence
    if page.nextSequence == previous cursor: caught up, back off before the next poll
    if |updated| + |deleted| + |filteredOutIds| == bulkSize: poll again immediately
on filter change: state := { cursor: 0, filter: newF }; clear the local replica
```

## Alternatives

| Approach | Leave-detection | Server state | Cost per poll | Verdict |
|---|---|---|---|---|
| **A. Page by sequence, re-check, `filteredOutIds`** (implemented) | Exact, via the re-check | None | Whole stream, regardless of selectivity | Right default; keep |
| B. Push the filter into the page query | None: rows leaving the filter vanish silently | None | Matches only | Rejected correctly by the doc |
| C. Hybrid: push down predicates on immutable fields, re-check the rest | Exact for mutable predicates; immutable ones never produce leaves | None, plus a per-field "immutable" declaration | Matches of the immutable part | The natural next step for tenant or region partition keys |
| D. Per-row watermark of the last change to any filterable column, page on `filter OR watermark > cursor` | Exact | One column per table, maintained on write | Matches plus rows whose filterable fields changed | Clever, cuts scanning when most writes are non-filter fields; adds write-path logic |
| E. Change log or outbox with before-image (CDC style) | Exact, page on `before matches OR after matches` | A history table with retention | Matches only | Fits the planned outbox; loses compaction, needs pruning |
| F. Server-side subscriptions (server remembers each client's filter) | Exact | Per-client | Matches only | Explicitly out of scope; the design's whole point is to avoid this |
| G. Unfiltered feed, filter on the client | Trivial | None | Whole stream, full rows | Fine when the client may see everything; leaks everything otherwise |
| H. Same as A, but one statement: page query with a computed `matches` column | Exact | None | Whole stream, one round trip, one snapshot | Recommended refinement of A |

H in more detail: a JPA criteria tuple query selecting the entity and
`cb.selectCase().when(<filter predicate>, true).otherwise(false)`, with
`sequence > :cursor`, ordered by sequence, limited to `bulkSize`. The predicate comes
from the existing `FilterSpecificationBuilder`, so semantics stay identical; the
`IN` list, the second round trip, the literal-inlining and the two-snapshot window all
disappear. It needs the `EntityManager` in the DAL (a custom repository fragment),
which is still layer 3.

## Recommendations

Code, in priority order:

1. **Index `sequence`.** Add `@Table(indexes = @Index(columnList = "sequence"))` to
   `Person` and `Company` now, and make it part of the "technical truth" the
   infrastructure owns: `ReplicationSequences.register` already runs DDL at startup,
   so creating `<table>_sequence_idx` alongside the database sequence would guarantee
   every future resource gets it.
2. **Batch-fetch collections on feed pages.** One property,
   `spring.jpa.properties.hibernate.default_batch_fetch_size: 100`, turns a page of
   100 persons from 201 statements into 3. Per-collection `@BatchSize` is the
   narrower alternative. Avoid fetch joins on two collections (cartesian product).
3. **Fold the re-check into the page query** (alternative H). Removes a round trip and
   the two-snapshot window with no protocol change. If that is too large a change,
   the minimal fix is to project ids only and bind the `IN` list as parameters.
4. **Decouple the feed's page cap** from `graphql.query.max-results`
   (`graphql.replication.max-bulk-size`, default 1,000 or similar). A feed page is
   bounded by construction, so the list cap's rationale does not apply, and the change
   also fixes the misleading error text.

Tests:

5. **Add the missing integration tests** (Appendix B). `ReplicationIntegrationTest`
   covers a filter splitting a page of freshly created rows; nothing covers a row
   leaving the filter, entering it, being deleted while matching, or a zero-match
   page. `ReplicationPageTest` covers the partition only with a hand-built matching
   set, and `matchingIds` including deleted rows has no test at all. The core claim of
   the doc is currently untested.
6. **Add a k6 scenario for the feed** (unfiltered and 5% filter) to `perf-tests`; the
   load test today exercises only the list queries and the mutations.

Documentation (concrete wording in Appendix A):

7. Correct the "exactly once" sentence and link the concurrency caveat.
8. Correct the "two statements" cost claim.
9. Replace the authorization sentence with an explicit "not an authorization boundary".
10. Add: the one-replica-per-filter rule, the store-filter-with-cursor rule, NULL
    semantics, how to detect a full page, and that `bulkSize` cannot be raised past
    the cap.

Protocol, later:

11. **Feed epoch** in the page, so clients can detect a rebuilt stream and resync.
12. **Tombstone retention** with an "oldest safe cursor" the client must be above.
13. **Hybrid pushdown for declared-immutable fields** (alternative C) when multi-tenant
    scale makes "every client scans everything" too expensive.

## Appendix A: suggested doc edits

- "Every change in the table enters the page stream exactly once, for every client,
  whatever their filter." → "Every row's latest change enters the page stream once,
  for every client, whatever their filter (intermediate states between two polls are
  compacted away; see 'Correctness under concurrency' in REPLICATION.md for the one
  known way a change can be missed)."
- "Cost: a filtered page is two statements … an unfiltered page is one." → "Cost: the
  feed itself is one statement per page plus one re-check when a filter is present
  (and one max-sequence read on an empty page). Mapping delivered rows to views adds
  whatever the domain's view mapping costs; for Person that is two lazy collection
  loads per delivered row unless batch fetching is enabled."
- "Non-matching rows leak only their id … a client authorized to see only its subset
  never receives another subset's data" → "Non-matching rows contribute only their id.
  The filter is a subsetting mechanism, not an authorization boundary: nothing forces a
  client to send one, and `filteredOutIds` reveals every row id and the table's write
  rate. Enforce visibility outside the feed."
- Under "Consequences", add: "One local replica per filter. Two feeds with different
  filters must never be merged into one local table: a row can be `updated` in one and
  filtered out in the other, and the last page applied wins." Add: "Store the filter
  with the cursor and restart from 0 when it changes." Add: "Predicates follow SQL NULL
  semantics: a row whose filtered column is NULL never matches, including for
  `notEquals`." Add: "A page is full, and more is waiting, when its three parts
  together hold `bulkSize` ids."
- Under "bulkSize counts scanned changes": say plainly that the cap is
  `graphql.query.max-results` (100 by default) and that a narrow filter cannot be sped
  up by raising `bulkSize` beyond it.

## Appendix B: experiment scenarios as candidate tests

Each scenario is what the throwaway experiment executed through `DgsQueryExecutor`;
ids and sequences are the values observed.

1. **Leave, enter, delete-while-matching.** Create Rev1 (Berlin), Rev2 (Berlin), Rev3
   (Munich); page from the baseline with a Berlin filter → `updated: [Rev1, Rev2]`,
   `filteredOutIds: [Rev3]`. Then `updatePersons` Rev2 to Munich, `deletePerson` Rev1,
   `updatePersons` Rev3 to Berlin; page from the previous `nextSequence` with the
   Berlin filter → `updated: [Rev3]`, `deleted: [Rev1 with city Berlin]`,
   `filteredOutIds: [Rev2]`.
2. **Mirror image.** Same range with a Munich filter → `updated: [Rev2]`,
   `filteredOutIds: [Rev1, Rev3]`, same `nextSequence`.
3. **Zero-match page advances the cursor.** Same range with a filter nobody matches →
   all three ids in `filteredOutIds`, same `nextSequence`.
4. **Any predicate.** `firstName like "Nick%"` through the feed returns both Nick rows.
5. **NULL semantics.** `nickname notEquals "zzz"` reports the NULL-nickname row in
   `filteredOutIds`.
6. **Statement budget.** With Hibernate statistics enabled, a filtered page of 50
   rows costs 2 feed statements plus 2 per delivered row; a zero-match page costs 2;
   an empty page costs 2. A future batch-fetch change should assert the smaller number.
7. **Plan.** `EXPLAIN` of the page query uses an index on `sequence` once
   recommendation 1 lands.
