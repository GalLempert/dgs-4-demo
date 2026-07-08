package com.example.infrastructure.exception;

/**
 * Framework-neutral "not found" exception for service layers to throw (HTTP 404
 * equivalent). Rendered as a NOT_FOUND GraphQL error by the exception handler.
 */
public class EntityNotFoundException extends ApiException {

    public EntityNotFoundException(String message) {
        super(ErrorCode.ENTITY_NOT_FOUND, message);
    }

    public static EntityNotFoundException of(String entityName, Object id) {
        return new EntityNotFoundException(entityName + " with id " + id + " was not found");
    }
}
