package com.example.infrastructure.mapping;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps input DTOs onto other objects (typically entities) by matching field names, so
 * services don't hand-copy every field. How a class is populated is declared on the
 * class itself with Jackson annotations - e.g. {@code @JsonManagedReference} /
 * {@code @JsonBackReference} to wire parent-child references, {@code @JsonIgnore} to
 * keep a field out of mapping, {@code @JsonAlias} to accept alternative input names.
 *
 * <p>Configuration choices:
 * <ul>
 *   <li>field access - entities don't need setters (or Jackson-specific constructors);</li>
 *   <li>null input fields are skipped - field initializers in the target (e.g.
 *       {@code active = true}) survive as defaults;</li>
 *   <li>unknown properties are ignored - inputs may carry fields the target lacks.</li>
 * </ul>
 */
@Component
public class InputMapper {

    private static final Logger log = LoggerFactory.getLogger(InputMapper.class);

    private final ObjectMapper mapper;

    public InputMapper(ObjectMapper applicationObjectMapper) {
        this.mapper = applicationObjectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public <T> T map(Object source, Class<T> targetType) {
        T mapped = mapper.convertValue(source, targetType);
        log.debug("Mapped {} to {}", source.getClass().getSimpleName(), targetType.getSimpleName());
        return mapped;
    }
}
