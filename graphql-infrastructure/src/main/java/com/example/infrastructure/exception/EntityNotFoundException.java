package com.example.infrastructure.exception;

/**
 * Framework-neutral "not found" exception for service layers to throw. The GraphQL
 * dispatch controller translates it into a NOT_FOUND GraphQL error, so services stay
 * free of any GraphQL/DGS dependency.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public static EntityNotFoundException of(String entityName, Object id) {
        return new EntityNotFoundException(entityName + " with id " + id + " was not found");
    }
}
