package com.example.infrastructure.graphql.response;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Feature toggle for null-field omission: when {@code graphql.response.omit-null-fields}
 * is {@code true}, every field whose resolved value is {@code null} is removed from the
 * response JSON entirely - key and value - recursively through nested objects and lists.
 * When {@code false} (the default) responses are untouched and follow the GraphQL
 * specification, which requires every requested field to appear with an explicit
 * {@code null}.
 *
 * <p>Implemented as a graphql-java instrumentation so it runs once per operation on the
 * completed {@link ExecutionResult}, after execution and before serialization; resolvers,
 * the dispatch controller and the error boundary are untouched. DGS discovers every
 * {@code Instrumentation} bean and chains them automatically.
 *
 * <p>Only the {@code data} part of the result is rewritten; {@code errors} and
 * {@code extensions} pass through unchanged. {@code null} elements INSIDE lists are
 * kept - list elements are not fields, and dropping them would shift indices.
 */
@Component
public class NullFieldOmittingInstrumentation extends SimpleInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(NullFieldOmittingInstrumentation.class);

    private final boolean omitNullFields;

    public NullFieldOmittingInstrumentation(
            @Value("${graphql.response.omit-null-fields:false}") boolean omitNullFields) {
        this.omitNullFields = omitNullFields;
        log.info("Null-field omission in GraphQL responses: {}",
                omitNullFields ? "ON (null fields are dropped from the JSON)" : "off (spec-compliant explicit nulls)");
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult,
                                                                        InstrumentationExecutionParameters parameters) {
        if (!omitNullFields || executionResult.getData() == null) {
            return CompletableFuture.completedFuture(executionResult);
        }
        return CompletableFuture.completedFuture(ExecutionResultImpl.newExecutionResult()
                .from(executionResult)
                .data(withoutNullFields(executionResult.getData()))
                .build());
    }

    private Object withoutNullFields(Object value) {
        if (Map.class.isInstance(value)) {
            Map<?, ?> object = Map.class.cast(value);
            Map<Object, Object> pruned = new LinkedHashMap<>();
            object.forEach((fieldName, fieldValue) -> {
                if (fieldValue != null) {
                    pruned.put(fieldName, withoutNullFields(fieldValue));
                }
            });
            return pruned;
        }
        if (List.class.isInstance(value)) {
            List<?> list = List.class.cast(value);
            List<Object> pruned = new ArrayList<>(list.size());
            list.forEach(element -> pruned.add(element == null ? null : withoutNullFields(element)));
            return pruned;
        }
        return value;
    }
}
