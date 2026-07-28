/**
 * Domain-agnostic support for sequence-based replication feeds.
 *
 * <p>The protocol: every replicated resource row carries a per-table, monotonically
 * increasing {@code sequence} that is re-assigned on every write, plus a soft-delete
 * flag (see {@link com.example.infrastructure.persistence.ReplicatedEntity}). Clients
 * import and stay in sync by polling "the next N rows with sequence &gt; X" and
 * resuming from the highest sequence of each page. Because deletes are soft, a
 * deletion is just another change the poll picks up.
 *
 * <p>{@link com.example.infrastructure.replication.ReplicationSequences} allocates the
 * sequence numbers from native database sequences;
 * {@link com.example.infrastructure.replication.ReplicationPage} is the page a feed
 * query returns and knows how to partition a fetched batch into updated / deleted /
 * filtered-out. Domain modules only wire these into their DAL and resolvers.
 */
package com.example.infrastructure.replication;
