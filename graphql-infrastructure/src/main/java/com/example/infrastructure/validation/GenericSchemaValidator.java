package com.example.infrastructure.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the shared validation library's {@code GenericSchemaValidator}: validates
 * any entity against a JSON Schema (draft-07) passed as its raw text, backed by the
 * Everit JSON Schema engine. When the real library is on the classpath this stub is
 * replaced by it; the API is identical, so callers don't change.
 *
 * <p>On violation it throws Everit's {@link ValidationException} (possibly aggregating
 * several causes). Callers inside this framework translate that third-party exception
 * into the framework's own {@link SchemaValidationException} - see
 * {@link JsonSchemaValidationService}.
 */
@Component
public class GenericSchemaValidator {

    private final ObjectMapper objectMapper;

    /** Compiled-schema cache so per-request validation doesn't re-parse schema text. */
    private final Map<String, Schema> compiledSchemas = new ConcurrentHashMap<>();

    public GenericSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the entity (any Jackson-serializable object, e.g. a raw GraphQL argument
     * map or a DTO) against the given JSON Schema text.
     *
     * @throws ValidationException if the entity violates the schema
     */
    public <T> void validateEntityBySchema(T entity, String schema) {
        Schema compiled = compiledSchemas.computeIfAbsent(schema, this::compile);
        compiled.validate(toOrgJson(entity));
    }

    private Schema compile(String schemaText) {
        return SchemaLoader.builder()
                .schemaJson(new JSONObject(new JSONTokener(schemaText)))
                .draftV7Support()
                .build()
                .load()
                .build();
    }

    /** Everit validates org.json values, so bridge from the Jackson world. */
    private Object toOrgJson(Object entity) {
        JsonNode node = objectMapper.valueToTree(entity);
        return new JSONTokener(node.toString()).nextValue();
    }
}
