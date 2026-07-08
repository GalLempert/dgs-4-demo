package com.example.infrastructure.graphql.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the GraphQL layer serializes this field as an enriched
 * {@code EnumValue} object ({@code code}, {@code label}, {@code description}) looked
 * up from the {@link com.example.infrastructure.enums.EnumCatalog} beans, instead of
 * the raw stored code.
 *
 * <p>The matching schema field must be declared as {@code EnumValue}. Clients that
 * only want the raw code simply select {@code fieldName { code }}; the catalog lookup
 * happens only when the field is selected.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GraphQLEnum {

    /** The catalog name to look the code up in, e.g. {@code "gender"}. */
    String value();
}
