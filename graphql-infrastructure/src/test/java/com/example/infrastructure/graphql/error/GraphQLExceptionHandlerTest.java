package com.example.infrastructure.graphql.error;

import com.example.infrastructure.error.EntityNotFoundException;
import com.example.infrastructure.error.ErrorDetail;
import com.example.infrastructure.error.mapping.ApiExceptionMapper;
import com.example.infrastructure.validation.SchemaValidationException;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphQLExceptionHandlerTest {

    private final GraphQLExceptionHandler handler =
            new GraphQLExceptionHandler(Collections.singletonList(new ApiExceptionMapper()));

    private DataFetcherExceptionHandlerParameters paramsFor(Throwable exception) {
        DataFetcherExceptionHandlerParameters params = mock(DataFetcherExceptionHandlerParameters.class);
        when(params.getException()).thenReturn(exception);
        when(params.getPath()).thenReturn(ResultPath.rootPath().segment("personById"));
        when(params.getSourceLocation()).thenReturn(new SourceLocation(1, 3));
        return params;
    }

    @Test
    void apiExceptionKeepsMessageAndRendersStructuredExtensions() {
        GraphQLError error = handler.onException(paramsFor(EntityNotFoundException.of("Person", 9)))
                .getErrors().get(0);

        assertThat(error.getMessage()).isEqualTo("Person with id 9 was not found");
        assertThat(error.getExtensions())
                .containsEntry("literal", "ENTITY_NOT_FOUND")
                .containsEntry("errorType", "NOT_FOUND")
                .containsEntry("httpStatus", 404)
                .containsKey("timestamp")
                .doesNotContainKey("details");
        assertThat(error.getPath()).containsExactly("personById");
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailsAreRenderedWhenPresent() {
        SchemaValidationException exception = new SchemaValidationException("person-create",
                Collections.singletonList(new ErrorDetail("$.heightCm", "maximum", "too tall")));

        GraphQLError error = handler.onException(paramsFor(exception)).getErrors().get(0);

        List<Map<String, Object>> details = (List<Map<String, Object>>) error.getExtensions().get("details");
        assertThat(details).hasSize(1);
        assertThat(details.get(0))
                .containsEntry("field", "$.heightCm")
                .containsEntry("constraint", "maximum");
    }

    @Test
    void unknownExceptionBecomesGenericInternalError() {
        GraphQLError error = handler.onException(paramsFor(new IllegalStateException("secret detail")))
                .getErrors().get(0);

        assertThat(error.getMessage()).isEqualTo("An unexpected error occurred while processing the request");
        assertThat(error.getExtensions())
                .containsEntry("literal", "INTERNAL_ERROR")
                .containsEntry("httpStatus", 500)
                .containsEntry("errorType", "INTERNAL");
    }

    @Test
    void wrappedExceptionsAreUnwrappedBeforeMapping() {
        CompletionException wrapped = new CompletionException(EntityNotFoundException.of("Person", 5));

        GraphQLError error = handler.onException(paramsFor(wrapped)).getErrors().get(0);

        assertThat(error.getExtensions()).containsEntry("literal", "ENTITY_NOT_FOUND");
        assertThat(error.getMessage()).contains("Person with id 5");
    }

    @Test
    void exactlyOneErrorPerFailure() {
        assertThat(handler.onException(paramsFor(new RuntimeException("x"))).getErrors()).hasSize(1);
    }
}
