package com.example.infrastructure.error;

/**
 * Canonical error catalog. Every {@link ApiException} carries one of these codes; the
 * GraphQL exception handler exposes the code's literal, its HTTP-equivalent status and
 * its GraphQL error classification in the error's {@code extensions}.
 */
public enum ErrorCode {

    ENTITY_NOT_FOUND(404, "NOT_FOUND"),
    SCHEMA_VALIDATION_FAILED(400, "BAD_REQUEST"),
    INVALID_ARGUMENT(400, "BAD_REQUEST"),
    DUPLICATE_RESOURCE(409, "FAILED_PRECONDITION"),
    RESULT_SET_TOO_LARGE(422, "FAILED_PRECONDITION"),
    INTERNAL_ERROR(500, "INTERNAL") {
        @Override
        public boolean isServerFault() {
            return true;
        }
    };

    /**
     * Whether the failure is the server's fault (logged with stack trace) rather than
     * a client mistake (logged as a warning). Values override this instead of call
     * sites comparing enum identities.
     */
    public boolean isServerFault() {
        return false;
    }

    private final int httpStatus;
    private final String graphqlErrorType;

    ErrorCode(int httpStatus, String graphqlErrorType) {
        this.httpStatus = httpStatus;
        this.graphqlErrorType = graphqlErrorType;
    }

    /** HTTP status code equivalent (GraphQL itself always answers 200 on the transport). */
    public int httpStatus() {
        return httpStatus;
    }

    /** DGS-style classification (NOT_FOUND, BAD_REQUEST, ...) clients may switch on. */
    public String graphqlErrorType() {
        return graphqlErrorType;
    }
}
