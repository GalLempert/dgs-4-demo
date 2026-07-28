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
 * table uses its own sequence (e.g. {@code person_replication_seq}), created lazily on
 * first use, so numbering is per-table and survives application restarts as long as
 * the database does.
 *
 * <p>Database sequences are atomic, so concurrent writers never receive the same
 * number. They are also non-transactional: a rolled-back write leaves a gap in the
 * numbering, which the feed protocol tolerates by design (clients only rely on
 * "strictly greater than", never on density).
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

    /** Returns the next value of the named sequence, creating the sequence on first use. */
    public long next(String sequenceName) {
        ensureExists(sequenceName);
        Long value = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequenceName, Long.class);
        log.debug("Allocated sequence {} from {}", value, sequenceName);
        return value;
    }

    private void ensureExists(String sequenceName) {
        if (!VALID_SEQUENCE_NAME.matcher(sequenceName).matches()) {
            throw new IllegalArgumentException("Invalid sequence name: '" + sequenceName + "'");
        }
        if (knownSequences.add(sequenceName)) {
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + sequenceName + " START WITH 1");
            log.info("Replication sequence '{}' is ready", sequenceName);
        }
    }
}
