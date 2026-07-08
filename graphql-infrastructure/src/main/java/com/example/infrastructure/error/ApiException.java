package com.example.infrastructure.error;

import java.util.Collections;
import java.util.List;

/**
 * Base class for every "known" failure in the application. Carries a canonical
 * {@link ErrorCode} (which maps to an HTTP status and a GraphQL classification) and an
 * optional list of {@link ErrorDetail}s explaining precisely what was wrong.
 *
 * <p>Service and DAL layers throw subclasses of this; the GraphQL exception handler
 * turns them into structured GraphQL errors. Anything that is NOT an ApiException is
 * treated as an unexpected internal error.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorDetail> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, Collections.emptyList());
    }

    public ApiException(ErrorCode errorCode, String message, List<ErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.unmodifiableList(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
