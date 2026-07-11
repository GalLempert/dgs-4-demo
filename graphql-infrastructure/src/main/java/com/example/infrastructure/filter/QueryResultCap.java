package com.example.infrastructure.filter;

import com.example.infrastructure.error.TooManyResultsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hard cap for non-paginated queries: the DAL counts the rows a filter would match
 * BEFORE fetching them, and this guard rejects the request when the count exceeds
 * {@code graphql.query.max-results} (default 100). Clients get a structured error
 * telling them to narrow the filter, and the database never materializes an unbounded
 * result set.
 */
@Component
public class QueryResultCap {

    private static final Logger log = LoggerFactory.getLogger(QueryResultCap.class);

    private final long maxResults;

    public QueryResultCap(@Value("${graphql.query.max-results:100}") long maxResults) {
        this.maxResults = maxResults;
        log.info("Query result cap: {} rows per non-paginated query", maxResults);
    }

    public void enforce(String resourceName, long matchedRows) {
        log.debug("Result cap check for {}: {} matched rows (cap {})", resourceName, matchedRows, maxResults);
        if (matchedRows > maxResults) {
            throw new TooManyResultsException(resourceName, matchedRows, maxResults);
        }
    }
}
