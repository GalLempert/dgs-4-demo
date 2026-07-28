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
 * long as the database does.
 *
 * <p><b>Commit-visibility ordering</b>: a database sequence guarantees unique,
 * increasing allocation but says nothing about commit order - transaction T1 could
 * allocate 1, stall, and commit after T2 already committed 2, letting a feed poll
 * advance its cursor past the still-invisible 1, which would then never satisfy
 * {@code sequence > cursor}. To keep allocation order consistent with commit
 * visibility, {@link #next} first takes a row lock on the resource's entry in the
 * {@code replication_write_lock} table. Row locks are held until the transaction
 * ends, so writers of the same resource serialize: nobody can allocate the next
 * sequence until the previous writer's row is committed (or rolled back). Writes to
 * different resources are unaffected.
 *
 * <p>{@link #next} must therefore run inside the writing transaction - which it does,
 * because the DAL stamps the sequence as part of {@code save()} and every write goes
 * through a {@code @Transactional} service method. Rolled-back writes still leave a
 * gap in the numbering, which the feed protocol tolerates by design (clients only
 * rely on "strictly greater than", never on density).
 */
@Component
public class ReplicationSequences {

    /** Guards the SQL built by string concatenation: names must be plain identifiers. */
    private static final Pattern VALID_SEQUENCE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final String LOCK_TABLE = "replication_write_lock";

    private static final Logger log = LoggerFactory.getLogger(ReplicationSequences.class);

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> knownSequences = ConcurrentHashMap.newKeySet();

    public ReplicationSequences(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates the named sequence and its write-lock row if they do not exist yet.
     * Called from DAL constructors, i.e. at startup and OUTSIDE any business
     * transaction - the DDL here must never run inside one (DDL implicitly commits).
     */
    public void register(String sequenceName) {
        if (!VALID_SEQUENCE_NAME.matcher(sequenceName).matches()) {
            throw new IllegalArgumentException("Invalid sequence name: '" + sequenceName + "'");
        }
        if (!knownSequences.add(sequenceName)) {
            return;
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + LOCK_TABLE + " (name VARCHAR(128) PRIMARY KEY)");
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + sequenceName + " START WITH 1");
        jdbcTemplate.update("MERGE INTO " + LOCK_TABLE + " KEY(name) VALUES (?)", sequenceName);
        log.info("Replication sequence '{}' is ready", sequenceName);
    }

    /**
     * Returns the next value of the named sequence, first serializing against other
     * writers of the same resource (see class javadoc). Must be called inside the
     * writing transaction so the lock is held until that transaction commits.
     */
    public long next(String sequenceName) {
        if (!knownSequences.contains(sequenceName)) {
            throw new IllegalStateException("Replication sequence '" + sequenceName
                    + "' was never registered - the DAL must call register() at construction time");
        }
        jdbcTemplate.queryForObject(
                "SELECT name FROM " + LOCK_TABLE + " WHERE name = ? FOR UPDATE", String.class, sequenceName);
        Long value = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequenceName, Long.class);
        log.debug("Allocated sequence {} from {}", value, sequenceName);
        return value;
    }
}
