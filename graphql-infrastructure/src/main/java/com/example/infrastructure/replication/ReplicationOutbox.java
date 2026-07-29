package com.example.infrastructure.replication;

/**
 * DELIBERATE STUB - the planned commit-ordered replacement for inline sequence
 * allocation. Not wired as a bean and not implemented yet, on purpose: it exists so
 * the design decision is visible in code, next to the limitation it will fix (see
 * {@link ReplicationSequences} - allocation order vs. commit order under concurrent
 * writers of the same table).
 *
 * <p>The rejected alternative was serializing writers with a per-table row lock held
 * until commit: correct, but it queues every write on the database. The outbox
 * pattern gets the same guarantee without touching write latency:
 *
 * <ol>
 *   <li><b>Record, don't sequence</b>: the write transaction no longer allocates the
 *       feed sequence. It only inserts a small change record (resource name + row id)
 *       into an outbox table - atomically with the business change, so a committed
 *       write always has exactly one committed change record.</li>
 *   <li><b>Relay assigns in commit order</b>: a single asynchronous relay polls the
 *       outbox for committed records and assigns the per-table feed sequence in the
 *       order it observes them, stamping the resource row (and deleting the record)
 *       outside any write transaction. One sequential consumer means sequences become
 *       visible in strictly increasing order - a feed poll can never advance past a
 *       not-yet-visible lower sequence, because lower sequences are always assigned
 *       to already-committed rows.</li>
 *   <li><b>Feed semantics unchanged</b>: rows appear in the feed only after the relay
 *       stamps them (a short, bounded delay); clients poll exactly as today.</li>
 * </ol>
 *
 * <p>Until this is implemented, the feed carries the documented anomaly under
 * overlapping writers of the same table - see "Correctness under concurrency" in
 * {@code docs/REPLICATION.md}.
 */
public final class ReplicationOutbox {

    private ReplicationOutbox() {
        // stub: not instantiable until implemented
    }

    /**
     * Will insert the change record inside the current write transaction, replacing
     * {@link ReplicationSequences#next} on the write path.
     */
    public static void recordChange(String resourceName, long rowId) {
        throw new UnsupportedOperationException(
                "Outbox-based commit-ordered sequencing is not implemented yet - see the class javadoc "
                        + "and docs/REPLICATION.md; inline allocation via ReplicationSequences is the "
                        + "current, deliberately chosen behavior");
    }

    /**
     * Will be invoked by the asynchronous relay: assigns feed sequences to committed
     * change records in commit order and stamps the resource rows.
     */
    public static void relayPendingChanges() {
        throw new UnsupportedOperationException(
                "Outbox-based commit-ordered sequencing is not implemented yet - see the class javadoc "
                        + "and docs/REPLICATION.md");
    }
}
