package com.example.infrastructure.exception;

import java.util.List;

/**
 * Thrown when a request payload fails server-side JSON Schema validation (HTTP 400
 * equivalent). Each {@link ErrorDetail} pinpoints one violated constraint: the field
 * path, the JSON Schema keyword (minimum, maximum, pattern, ...) and the reason.
 */
public class SchemaValidationException extends ApiException {

    private final String schemaName;

    public SchemaValidationException(String schemaName, List<ErrorDetail> details) {
        super(ErrorCode.SCHEMA_VALIDATION_FAILED,
                "Request failed JSON schema validation against schema '" + schemaName + "' ("
                        + details.size() + " violation" + (details.size() == 1 ? "" : "s") + ")",
                details);
        this.schemaName = schemaName;
    }

    public String getSchemaName() {
        return schemaName;
    }
}
