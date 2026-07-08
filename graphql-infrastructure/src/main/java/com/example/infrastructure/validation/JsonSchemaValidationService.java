package com.example.infrastructure.validation;

import com.example.infrastructure.exception.ErrorDetail;
import com.example.infrastructure.exception.SchemaValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side JSON Schema validation, one level stronger than what GraphQL's type
 * system can express (value ranges, string patterns, array sizes, formats...).
 *
 * <p>Domain modules drop schema files under {@code classpath:json-schema/<name>.json}
 * (draft-07); the schema is addressed by its file name. The dispatch controller runs
 * every resolver-declared argument through {@link #validate} before the resolver is
 * invoked, so invalid payloads never reach the service layer.
 *
 * <p>On violation a {@link SchemaValidationException} is thrown carrying one
 * {@link ErrorDetail} per broken constraint: the field path, the JSON Schema keyword
 * (minimum, maximum, pattern...) and a human-readable reason.
 */
@Component
public class JsonSchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidationService.class);
    private static final String SCHEMA_LOCATION_PATTERN = "classpath*:json-schema/*.json";

    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemasByName = new LinkedHashMap<>();

    public JsonSchemaValidationService(ObjectMapper objectMapper, ResourcePatternResolver resourceResolver) {
        this.objectMapper = objectMapper;
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try {
            Resource[] resources = resourceResolver.getResources(SCHEMA_LOCATION_PATTERN);
            for (Resource resource : resources) {
                String name = schemaName(resource);
                schemasByName.put(name, factory.getSchema(resource.getInputStream()));
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
        JsonSchema schema = schemasByName.get(schemaName);
        if (schema == null) {
            throw new IllegalStateException("No JSON schema named '" + schemaName
                    + "' found under classpath:json-schema/ (available: " + schemasByName.keySet() + ")");
        }
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        log.debug("Validating payload against JSON schema '{}': {}", schemaName, payloadNode);

        Set<ValidationMessage> violations = schema.validate(payloadNode);
        if (violations.isEmpty()) {
            log.debug("Payload passed JSON schema '{}'", schemaName);
            return;
        }
        List<ErrorDetail> details = violations.stream()
                .map(violation -> new ErrorDetail(violation.getPath(), violation.getType(), violation.getMessage()))
                .collect(Collectors.toList());
        log.warn("Payload failed JSON schema '{}' with {} violation(s): {}", schemaName, details.size(), details);
        throw new SchemaValidationException(schemaName, details);
    }

    private String schemaName(Resource resource) {
        String filename = resource.getFilename() != null ? resource.getFilename() : "unnamed.json";
        return filename.replaceFirst("\\.schema\\.json$", "").replaceFirst("\\.json$", "");
    }
}
