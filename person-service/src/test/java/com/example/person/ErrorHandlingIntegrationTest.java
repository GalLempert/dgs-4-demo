package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The global error boundary must render every failure as a structured GraphQL error
 * with extensions: literal (error code), httpStatus, errorType, timestamp and - for
 * validation failures - per-constraint details.
 */
@SpringBootTest
class ErrorHandlingIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    @SuppressWarnings("unchecked")
    void jsonSchemaViolationReturnsDetailedError() {
        // GraphQL-wise this input is perfectly valid; only the JSON schema knows that
        // heightCm must be 50..260 and firstName needs at least 2 characters.
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"X\", lastName: \"Oversized\", email: \"tall@example.com\", "
                + "heightCm: 300, hobbies: [\"basketball\"] "
                + "}) { id } }";

        ExecutionResult result = dgsQueryExecutor.execute(mutation);

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("JSON schema validation").contains("person-create");

        Map<String, Object> extensions = error.getExtensions();
        assertThat(extensions.get("literal")).isEqualTo("SCHEMA_VALIDATION_FAILED");
        assertThat(extensions.get("httpStatus")).isEqualTo(400);
        assertThat(extensions.get("errorType")).isEqualTo("BAD_REQUEST");
        assertThat(extensions.get("timestamp")).isNotNull();

        List<Map<String, Object>> details = (List<Map<String, Object>>) extensions.get("details");
        assertThat(details).hasSize(2);
        assertThat(details).anySatisfy(detail -> {
            assertThat(String.valueOf(detail.get("field"))).contains("heightCm");
            assertThat(detail.get("constraint")).isEqualTo("maximum");
            assertThat(String.valueOf(detail.get("reason"))).contains("260");
        });
        assertThat(details).anySatisfy(detail ->
                assertThat(String.valueOf(detail.get("field"))).contains("firstName"));

        // the invalid person must not have been persisted
        List<String> emails = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { email } }", "data.allPersons[*].email");
        assertThat(emails).doesNotContain("tall@example.com");
    }

    @Test
    void notFoundCarriesLiteralAndHttpStatus() {
        ExecutionResult result = dgsQueryExecutor.execute("{ personById(id: \"87654\") { id } }");

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).isEqualTo("Person with id 87654 was not found");
        assertThat(error.getExtensions().get("literal")).isEqualTo("ENTITY_NOT_FOUND");
        assertThat(error.getExtensions().get("httpStatus")).isEqualTo(404);
        assertThat(error.getExtensions().get("errorType")).isEqualTo("NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateEmailCarriesConflictStatus() {
        // ada.lovelace@example.com is seeded at startup
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"Ada\", lastName: \"Clone\", email: \"ada.lovelace@example.com\", "
                + "hobbies: [\"cloning\"] "
                + "}) { id } }";

        ExecutionResult result = dgsQueryExecutor.execute(mutation);

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("already exists");
        assertThat(error.getExtensions().get("literal")).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(error.getExtensions().get("httpStatus")).isEqualTo(409);

        List<Map<String, Object>> details = (List<Map<String, Object>>) error.getExtensions().get("details");
        assertThat(details).anySatisfy(detail -> {
            assertThat(detail.get("field")).isEqualTo("email");
            assertThat(detail.get("constraint")).isEqualTo("unique");
        });
    }
}
