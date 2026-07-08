package com.example.infrastructure.graphql.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the GraphQL layer renders this temporal field ({@code LocalDate} /
 * {@code LocalDateTime}) through a client-chosen format: the schema field takes a
 * {@code format: DateFormat = ISO} argument, resolved against the registered
 * {@link com.example.infrastructure.graphql.format.TemporalFormatter} strategies.
 *
 * <p>GraphQL aliases let one query request several formats of the same field:
 * {@code iso: birthDate, unix: birthDate(format: UNIX)}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphQLTemporal {
}
