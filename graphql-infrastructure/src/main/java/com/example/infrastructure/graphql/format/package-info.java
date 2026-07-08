/**
 * Client-chosen output formats for temporal fields: one
 * {@link com.example.infrastructure.graphql.format.TemporalFormatter} bean per format
 * literal of the schema's {@code DateFormat} enum, indexed by the
 * {@link com.example.infrastructure.graphql.format.TemporalFormatterRegistry}. Fields
 * opt in with {@code @GraphQLTemporal}.
 */
package com.example.infrastructure.graphql.format;
