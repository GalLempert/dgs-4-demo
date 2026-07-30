package com.example.infrastructure.mapping;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;

/**
 * Maps input DTOs onto other objects (typically entities) by matching field names, so
 * services don't hand-copy every field. How a class is populated is declared on the
 * class itself with Jackson annotations - e.g. {@code @JsonManagedReference} /
 * {@code @JsonBackReference} to wire parent-child references, {@code @JsonIgnore} to
 * keep a field out of mapping, {@code @JsonAlias} to accept alternative input names.
 *
 * <p>Three operations, one philosophy (field names are the mapping):
 * <ul>
 *   <li>{@link #map} - build a new target from a source (create path, entity-to-view);</li>
 *   <li>{@link #merge} - copy the source's non-null fields onto an existing target,
 *       leaving everything else untouched (partial-update path);</li>
 *   <li>{@link #override} - copy ALL of the source's fields onto an existing target,
 *       nulls included, so the target's mapped state becomes exactly the source's
 *       (full-replace path). Fields the source type does not declare are untouched -
 *       that is what keeps an entity's technical fields (id, version, sequence...) safe.</li>
 * </ul>
 *
 * <p>Configuration choices:
 * <ul>
 *   <li>field access - entities don't need setters (or Jackson-specific constructors);</li>
 *   <li>null input fields are skipped in {@link #map} and {@link #merge} - field
 *       initializers in the target (e.g. {@code active = true}) survive as defaults;</li>
 *   <li>unknown properties are ignored - inputs may carry fields the target lacks;</li>
 *   <li>{@link #merge} and {@link #override} mutate the target's existing collections
 *       in place (clear + refill) instead of assigning new instances - JPA-managed
 *       collections (orphan-removal one-to-many, element collections) must keep their
 *       identity or Hibernate rejects the flush.</li>
 * </ul>
 */
@Component
public class DeclarativeMapper {

    private static final Logger log = LoggerFactory.getLogger(DeclarativeMapper.class);

    private final ObjectMapper mapper;
    private final ObjectMapper overridingMapper;

    public DeclarativeMapper(ObjectMapper applicationObjectMapper) {
        this.mapper = applicationObjectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // identical, except null source fields are serialized too - so they overwrite
        this.overridingMapper = mapper.copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public <T> T map(Object source, Class<T> targetType) {
        T mapped = mapper.convertValue(source, targetType);
        log.debug("Mapped {} to {}", source.getClass().getSimpleName(), targetType.getSimpleName());
        return mapped;
    }

    /** Partial update: copies the source's non-null fields onto the existing target. */
    public <T> T merge(Object source, T target) {
        return updateInPlace(mapper, source, target, "Merged");
    }

    /** Full replace: copies every field the source declares onto the target, nulls included. */
    public <T> T override(Object source, T target) {
        return updateInPlace(overridingMapper, source, target, "Overrode");
    }

    private <T> T updateInPlace(ObjectMapper activeMapper, Object source, T target, String verb) {
        ObjectNode sourceTree = activeMapper.valueToTree(source);
        refillCollectionsInPlace(activeMapper, sourceTree, target);
        try {
            T updated = activeMapper.readerForUpdating(target).readValue(sourceTree);
            log.debug("{} {} onto {}", verb, source.getClass().getSimpleName(), target.getClass().getSimpleName());
            return updated;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not apply " + source.getClass().getSimpleName()
                    + " onto " + target.getClass().getSimpleName(), e);
        }
    }

    /**
     * Applies the source tree's collection-typed properties by clearing and refilling
     * the target's existing collection instances (and removes them from the tree so
     * the reader does not re-assign them). Jackson would otherwise bind a brand-new
     * collection into the field, and a JPA-managed collection that loses its identity
     * fails the flush ("collection ... was no longer referenced").
     */
    private void refillCollectionsInPlace(ObjectMapper activeMapper, ObjectNode sourceTree, Object target) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !Collection.class.isAssignableFrom(field.getType())
                        || !sourceTree.has(field.getName())) {
                    continue;
                }
                JsonNode value = sourceTree.remove(field.getName());
                Collection<Object> existing = currentCollection(field, target);
                if (existing == null) {
                    // no instance to preserve - let the reader assign a fresh collection
                    sourceTree.set(field.getName(), value);
                    continue;
                }
                existing.clear();
                if (!value.isNull()) {
                    JavaType collectionType = activeMapper.getTypeFactory().constructType(field.getGenericType());
                    existing.addAll(activeMapper.convertValue(value, collectionType));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> currentCollection(Field field, Object target) {
        try {
            field.setAccessible(true);
            return (Collection<Object>) field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read collection field '" + field.getName()
                    + "' of " + target.getClass().getSimpleName(), e);
        }
    }
}
