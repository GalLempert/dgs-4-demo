package com.example.infrastructure.error.mapping;

import com.example.infrastructure.error.ApiException;

/**
 * Strategy for translating a raised exception into a structured {@link ApiException}.
 *
 * <p>The GraphQL exception handler asks every registered mapper bean (in bean order)
 * whether it supports the thrown exception and uses the first match; when none matches
 * the failure is treated as an unexpected internal error. Domain modules can plug in
 * their own mappers (e.g. persistence-specific exceptions to DUPLICATE_RESOURCE)
 * without touching infrastructure code.
 */
public interface ExceptionMapper {

    Class<? extends Throwable> supportedType();

    ApiException map(Throwable exception);

    default boolean supports(Throwable exception) {
        return supportedType().isInstance(exception);
    }
}
