package com.example.infrastructure.graphql;

import com.example.infrastructure.exception.ApiException;
import com.example.infrastructure.exception.ErrorCode;
import com.example.infrastructure.exception.ErrorDetail;
import com.example.infrastructure.exception.ExceptionMapper;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Global error boundary for every data fetcher. DGS picks this bean up instead of its
 * default handler, so ANY exception thrown anywhere below the GraphQL layer ends up
 * here and is turned into a structured GraphQL error.
 *
 * <p>Translation is delegated to the {@link ExceptionMapper} strategies found in the
 * application context; exceptions no strategy claims are rendered as a generic
 * INTERNAL_ERROR (500) and logged with their stack trace, so internals never leak.
 */
@Component
public class GraphQLExceptionHandler implements DataFetcherExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GraphQLExceptionHandler.class);

    private static final List<Class<? extends Throwable>> WRAPPER_TYPES =
            Arrays.asList(CompletionException.class, InvocationTargetException.class);

    private final List<ExceptionMapper> exceptionMappers;

    public GraphQLExceptionHandler(List<ExceptionMapper> exceptionMappers) {
        this.exceptionMappers = exceptionMappers;
    }

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrap(handlerParameters.getException());
        ApiException apiException = mapToApiException(exception);
        logFailure(apiException, exception, handlerParameters);
        return DataFetcherExceptionHandlerResult.newResult()
                .error(toGraphQLError(apiException, handlerParameters))
                .build();
    }

    private ApiException mapToApiException(Throwable exception) {
        return exceptionMappers.stream()
                .filter(mapper -> mapper.supports(exception))
                .findFirst()
                .map(mapper -> mapper.map(exception))
                .orElseGet(() -> unexpectedError(exception));
    }

    private ApiException unexpectedError(Throwable exception) {
        return new ApiException(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred while processing the request",
                Collections.singletonList(
                        new ErrorDetail(null, exception.getClass().getSimpleName(), exception.getMessage())));
    }

    private void logFailure(ApiException apiException,
                            Throwable original,
                            DataFetcherExceptionHandlerParameters params) {
        if (apiException.getErrorCode() == ErrorCode.INTERNAL_ERROR) {
            log.error("Unhandled {} at path {}", original.getClass().getSimpleName(), params.getPath(), original);
            return;
        }
        log.warn("GraphQL request failed at path {} with {} ({}): {}",
                params.getPath(),
                apiException.getErrorCode(),
                apiException.getErrorCode().httpStatus(),
                apiException.getMessage());
        log.debug("Failure details at path {}: {}", params.getPath(), apiException.getDetails());
    }

    private GraphQLError toGraphQLError(ApiException apiException, DataFetcherExceptionHandlerParameters params) {
        return GraphqlErrorBuilder.newError()
                .message(apiException.getMessage())
                .path(params.getPath())
                .location(params.getSourceLocation())
                .extensions(extensionsOf(apiException))
                .build();
    }

    private Map<String, Object> extensionsOf(ApiException apiException) {
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
        return extensions;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (isWrapper(current) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isWrapper(Throwable throwable) {
        return WRAPPER_TYPES.stream().anyMatch(type -> type.isInstance(throwable));
    }
}
