package com.example.infrastructure.exception;

import java.util.Collections;

/**
 * Thrown when a create/update would violate a uniqueness rule (HTTP 409 equivalent).
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message, String field) {
        super(ErrorCode.DUPLICATE_RESOURCE, message,
                Collections.singletonList(new ErrorDetail(field, "unique", message)));
    }
}
