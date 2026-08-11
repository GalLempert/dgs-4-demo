package com.example.infrastructure.validation;

import com.example.infrastructure.error.ErrorDetail;
import org.everit.json.schema.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side JSON Schema validation, one level stronger than what GraphQL's type
 * system can express (value ranges, string patterns, array sizes, formats...).
 *
 * <p>Domain modules drop schema files under {@code classpath:json-schema/<name>.json}
 * (draft-07); the schema is addressed by its file name. The dispatch controller runs
 * every resolver-declared argument through {@link #validate} before the resolver is
 * invoked, so invalid payloads never reach the service layer.
 *
 * <p>The actual engine is the validation library's {@link GenericSchemaValidator}
 * (Everit-backed); this service resolves the named schema text, delegates to it, and
 * translates the engine's {@link ValidationException} into the framework's
 * {@link SchemaValidationException} carrying one {@link ErrorDetail} per broken
 * constraint: the field path, the JSON Schema keyword (minimum, maximum, pattern...)
 * and a human-readable reason.
 */
@Component
public class JsonSchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidationService.class);
    private static final String SCHEMA_LOCATION_PATTERN = "classpath*:json-schema/*.json";

    private final GenericSchemaValidator schemaValidator;
    private final Map<String, String> schemasByName = new LinkedHashMap<>();

    public JsonSchemaValidationService(GenericSchemaValidator schemaValidator,
                                       ResourcePatternResolver resourceResolver) {
        this.schemaValidator = schemaValidator;
        try {
            Resource[] resources = resourceResolver.getResources(SCHEMA_LOCATION_PATTERN);
            for (Resource resource : resources) {
                String name = schemaName(resource);
                schemasByName.put(name, readSchemaText(resource));
                log.info("Loaded JSON schema '{}' from {}", name, resource.getDescription());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load JSON schemas from " + SCHEMA_LOCATION_PATTERN, e);
        }
        if (schemasByName.isEmpty()) {
            log.info("No JSON schemas found under {} - schema validation is a no-op", SCHEMA_LOCATION_PATTERN);
        }
    }

    /**
     * Validates the given payload (typically the raw GraphQL argument map) against the
     * named schema.
     *
     * @throws SchemaValidationException with per-constraint details if validation fails
     * @throws IllegalStateException     if no schema with that name is on the classpath
     */
    public void validate(String schemaName, Object payload) {
        String schema = schemasByName.get(schemaName);
        if (schema == null) {
            throw new IllegalStateException("No JSON schema named '" + schemaName
                    + "' found under classpath:json-schema/ (available: " + schemasByName.keySet() + ")");
        }
        log.debug("Validating payload against JSON schema '{}'", schemaName);
        try {
            schemaValidator.validateEntityBySchema(payload, schema);
        } catch (ValidationException e) {
            List<ErrorDetail> details = new ArrayList<>();
            collectViolations(e, details);
            log.warn("Payload failed JSON schema '{}' with {} violation(s): {}", schemaName, details.size(), details);
            throw new SchemaValidationException(schemaName, details);
        }
        log.debug("Payload passed JSON schema '{}'", schemaName);
    }

    /** Everit aggregates multiple failures as a tree; flatten it to one detail per leaf. */
    private void collectViolations(ValidationException violation, List<ErrorDetail> details) {
        if (violation.getCausingExceptions().isEmpty()) {
            details.add(new ErrorDetail(
                    violation.getPointerToViolation(), violation.getKeyword(), violation.getErrorMessage()));
            return;
        }
        for (ValidationException cause : violation.getCausingExceptions()) {
            collectViolations(cause, details);
        }
    }

    private String readSchemaText(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String schemaName(Resource resource) {
        String filename = resource.getFilename() != null ? resource.getFilename() : "unnamed.json";
        return filename.replaceFirst("\\.schema\\.json$", "").replaceFirst("\\.json$", "");
    }
}
