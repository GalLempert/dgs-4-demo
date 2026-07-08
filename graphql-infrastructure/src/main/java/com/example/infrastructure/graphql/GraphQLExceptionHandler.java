package com.example.infrastructure.graphql;

import com.example.infrastructure.exception.ApiException;
import com.example.infrastructure.exception.ErrorCode;
import com.example.infrastructure.exception.ErrorDetail;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Global error boundary for every data fetcher. DGS picks this bean up instead of its
 * default handler, so ANY exception thrown anywhere below the GraphQL layer ends up
 * here and is turned into a structured GraphQL error.
 *
 * <ul>
 *   <li>{@link ApiException}s (known failures: not found, schema validation, duplicates…)
 *       keep their message and are rendered with {@code extensions} carrying the error
 *       literal, the equivalent HTTP status, a classification, a timestamp and — when
 *       available — per-field {@code details} explaining exactly what was violated.</li>
 *   <li>Anything else is logged with its stack trace and rendered as a generic
 *       INTERNAL_ERROR (500) so internals never leak by accident.</li>
 * </ul>
 */
@Component
public class GraphQLExceptionHandler implements DataFetcherExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GraphQLExceptionHandler.class);

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrap(handlerParameters.getException());
        ApiException apiException = asApiException(exception, handlerParameters);

        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("literal", apiException.getErrorCode().name());
        extensions.put("errorType", apiException.getErrorCode().graphqlErrorType());
        extensions.put("httpStatus", apiException.getErrorCode().httpStatus());
        extensions.put("timestamp", Instant.now().toString());
        if (!apiException.getDetails().isEmpty()) {
            extensions.put("details", apiException.getDetails().stream()
                    .map(ErrorDetail::toMap)
                    .collect(Collectors.toList()));
        }

        GraphQLError error = GraphqlErrorBuilder.newError()
                .message(apiException.getMessage())
                .path(handlerParameters.getPath())
                .location(handlerParameters.getSourceLocation())
                .extensions(extensions)
                .build();
        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }

    private ApiException asApiException(Throwable exception, DataFetcherExceptionHandlerParameters params) {
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            log.warn("GraphQL request failed at path {} with {} ({}): {}",
                    params.getPath(),
                    apiException.getErrorCode(),
                    apiException.getErrorCode().httpStatus(),
                    apiException.getMessage());
            log.debug("Failure details at path {}: {}", params.getPath(), apiException.getDetails());
            return apiException;
        }
        log.error("Unhandled {} at path {}", exception.getClass().getSimpleName(), params.getPath(), exception);
        return new ApiException(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred while processing the request",
                Collections.singletonList(
                        new ErrorDetail(null, exception.getClass().getSimpleName(), exception.getMessage())));
    }

    private Throwable unwrap(Throwable throwable) {
        while ((throwable instanceof CompletionException || throwable instanceof InvocationTargetException)
                && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
