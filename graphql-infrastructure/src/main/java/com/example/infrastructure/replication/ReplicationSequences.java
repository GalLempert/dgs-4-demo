package com.example.infrastructure.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Allocates replication sequence numbers from named database sequences. Each resource
 * table uses its own sequence (e.g. {@code person_replication_seq}), registered at
 * startup by its DAL, so numbering is per-table and survives application restarts as
 * long as the database does. Allocation is a single lock-free {@code NEXT VALUE FOR}
 * call - nothing on the write path blocks on other writers.
 *
 * <p><b>KNOWN, DELIBERATE LIMITATION - allocation order is not commit order.</b>
 * A database sequence guarantees unique, increasing allocation but says nothing about
 * commit visibility: under concurrent writers of the same table, a transaction that
 * allocated a lower sequence can commit after a feed poll already advanced past a
 * higher one, and that row's change is then never delivered ({@code sequence > cursor}
 * can no longer match it). Serializing writers with a lock held until commit would
 * close the gap but was rejected on purpose - it turns every write into a queue on the
 * database. The planned fix is the outbox pattern stubbed in
 * {@link ReplicationOutbox}: sequences get assigned in commit order by an asynchronous
 * relay, off the write transaction entirely. Until then the feed is correct for
 * non-overlapping writers, and overlapping writers carry this documented anomaly -
 * see "Correctness under concurrency" in {@code docs/REPLICATION.md}.
 *
 * <p>Rolled-back writes leave a gap in the numbering, which the feed protocol
 * tolerates by design (clients only rely on "strictly greater than", never density).
 */
@Component
public class ReplicationSequences {

    /** Guards the SQL built by string concatenation: names must be plain identifiers. */
    private static final Pattern VALID_SEQUENCE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Logger log = LoggerFactory.getLogger(ReplicationSequences.class);

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> knownSequences = ConcurrentHashMap.newKeySet();

    public ReplicationSequences(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates the named sequence if it does not exist yet. Called from DAL
     * constructors, i.e. at startup and OUTSIDE any business transaction - the DDL
     * here must never run inside one (DDL implicitly commits).
     */
    public void register(String sequenceName) {
        if (!VALID_SEQUENCE_NAME.matcher(sequenceName).matches()) {
            throw new IllegalArgumentException("Invalid sequence name: '" + sequenceName + "'");
        }
        if (!knownSequences.add(sequenceName)) {
            return;
        }
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + sequenceName + " START WITH 1");
        log.info("Replication sequence '{}' is ready", sequenceName);
    }

    /** Returns the next value of the named sequence (lock-free, see class javadoc). */
    public long next(String sequenceName) {
        if (!knownSequences.contains(sequenceName)) {
            throw new IllegalStateException("Replication sequence '" + sequenceName
                    + "' was never registered - the DAL must call register() at construction time");
        }
        Long value = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequenceName, Long.class);
        log.debug("Allocated sequence {} from {}", value, sequenceName);
        return value;
    }
}
