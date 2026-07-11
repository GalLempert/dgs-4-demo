package com.example.infrastructure.error;

import java.util.Collections;

/**
 * A query (usually filtered) would match more rows than the configured cap allows
 * (HTTP 422 equivalent). The client should narrow the filter.
 */
public class TooManyResultsException extends ApiException {

    public TooManyResultsException(String resourceName, long matchedRows, long maxResults) {
        super(ErrorCode.RESULT_SET_TOO_LARGE,
                "Query matches " + matchedRows + " " + resourceName + " rows, exceeding the maximum of "
                        + maxResults + " - narrow the filter",
                Collections.singletonList(new ErrorDetail("filter", "max-results",
                        "matched " + matchedRows + " rows, cap is " + maxResults)));
    }
}
