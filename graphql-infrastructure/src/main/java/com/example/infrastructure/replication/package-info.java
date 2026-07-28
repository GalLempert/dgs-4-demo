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
 * <p>The whole stack of the four standard queries (filtered list, feed page, count,
 * max sequence) ships here, one layer per class - a domain module only subclasses and
 * wires beans:
 *
 * <ul>
 *   <li>{@link com.example.infrastructure.replication.ReplicatedRepository} - Spring
 *       Data base interface with the feed queries; extend it with the entity type.</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicatedDal} - complete DAL
 *       (capped filtered reads, feed reads, sequence stamping on save, soft-delete
 *       visibility rules); subclass names the resource and its DB sequence, taking
 *       {@link com.example.infrastructure.replication.ReplicatedDalSupport} in the
 *       constructor.</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicatedResourceService} -
 *       complete service layer (feed orchestration and partitioning, counting, soft
 *       delete); subclass supplies the entity-to-view mapping.</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicationResolverFactory} -
 *       manufactures the four query resolvers; the domain registers one
 *       {@code @Bean} per schema field.</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicationSequences} /
 *       {@link com.example.infrastructure.replication.ReplicationPage} - sequence
 *       allocation and the page/partitioning contract, used by the classes above.</li>
 * </ul>
 */
package com.example.infrastructure.replication;
