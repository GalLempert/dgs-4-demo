/**
 * Domain-agnostic infrastructure, reusable by any domain module. Nothing in here knows
 * about Person or any other concrete resource.
 *
 * <ul>
 *   <li>{@code graphql.*}    - GraphQL entry point: dispatch, arguments, scalars, error rendering</li>
 *   <li>{@code error}        - the structured error model every layer shares</li>
 *   <li>{@code validation}   - server-side JSON Schema validation</li>
 *   <li>{@code persistence}  - common JPA building blocks</li>
 * </ul>
 */
package com.example.infrastructure;
