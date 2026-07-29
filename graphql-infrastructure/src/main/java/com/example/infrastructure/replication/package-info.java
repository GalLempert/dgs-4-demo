/**
 * The standard resource stack: every resource is a replicated resource, and this
 * package ships the complete, domain-agnostic implementation of its behavior.
 *
 * <p>The protocol: every resource row carries a per-table, monotonically increasing
 * {@code sequence} that is re-assigned on every write, plus a soft-delete flag (both
 * on {@link com.example.infrastructure.persistence.BaseEntity}). Clients import and
 * stay in sync by polling "the next N rows with sequence &gt; X" and resuming from
 * the highest sequence of each page. Because deletes are soft, a deletion is just
 * another change the poll picks up.
 *
 * <p>The whole stack of the four standard queries (filtered list, feed page, count,
 * max sequence) ships here, one layer per class - a domain module only subclasses and
 * wires beans:
 *
 * <ul>
 *   <li>{@link com.example.infrastructure.replication.ResourceRepository} - Spring
 *       Data base interface with the feed queries; extend it with the entity type.</li>
 *   <li>{@link com.example.infrastructure.replication.ResourceDal} - complete DAL
 *       (capped filtered reads, feed reads, sequence stamping on save, soft-delete
 *       visibility rules); subclass names the resource and its DB sequence, taking
 *       {@link com.example.infrastructure.replication.ResourceDalSupport} in the
 *       constructor.</li>
 *   <li>{@link com.example.infrastructure.replication.ResourceService} - complete
 *       service layer (feed orchestration and partitioning, counting, soft delete);
 *       subclass supplies the entity-to-view mapping (views extend
 *       {@link com.example.infrastructure.graphql.model.ResourceView}, which carries
 *       the technical fields backing the {@code Resource} schema interface).</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicationResolverFactory} -
 *       manufactures the four query resolvers; the domain registers one
 *       {@code @Bean} per schema field.</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicationSequences} /
 *       {@link com.example.infrastructure.replication.ReplicationPage} - sequence
 *       allocation and the page/partitioning contract, used by the classes above.</li>
 *   <li>{@link com.example.infrastructure.replication.ReplicationOutbox} - a
 *       deliberate stub for the planned outbox-based, commit-ordered sequencing that
 *       will replace inline allocation without locking the write path.</li>
 * </ul>
 */
package com.example.infrastructure.replication;
