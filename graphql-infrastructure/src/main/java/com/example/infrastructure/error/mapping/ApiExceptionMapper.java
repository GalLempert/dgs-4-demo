package com.example.infrastructure.error.mapping;

import com.example.infrastructure.error.ApiException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link ApiException}s already carry their error code and details - they pass through
 * unchanged. Ordered first so subclasses are never re-mapped by broader strategies.
 */
@Component
@Order(0)
public class ApiExceptionMapper implements ExceptionMapper {

    @Override
    public Class<? extends Throwable> supportedType() {
        return ApiException.class;
    }

    @Override
    public ApiException map(Throwable exception) {
        return ApiException.class.cast(exception);
    }
}
