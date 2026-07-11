package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hard cap on non-paginated queries: with the cap lowered to 2 and 3 persons
 * seeded, unfiltered queries must be rejected (after the COUNT, before fetching)
 * while a filter narrow enough to fit passes.
 */
@SpringBootTest(properties = "graphql.query.max-results=2")
class QueryResultCapIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    void queriesMatchingMoreRowsThanTheCapAreRejected() {
        ExecutionResult result = dgsQueryExecutor.execute("{ persons { firstName } }");

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("3 Person rows").contains("maximum of 2").contains("narrow");
        assertThat(error.getExtensions().get("literal")).isEqualTo("RESULT_SET_TOO_LARGE");
        assertThat(error.getExtensions().get("httpStatus")).isEqualTo(422);
    }

    @Test
    void theCapAlsoGuardsTheLegacyListQueries() {
        ExecutionResult allPersons = dgsQueryExecutor.execute("{ allPersons { firstName } }");
        assertThat(allPersons.getErrors()).hasSize(1);
        assertThat(allPersons.getErrors().get(0).getExtensions().get("literal"))
                .isEqualTo("RESULT_SET_TOO_LARGE");
    }

    @Test
    void narrowEnoughFiltersStillWork() {
        List<String> names = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: { firstName: { equals: { value: \"Ada\" } } }) { firstName } }",
                "data.persons[*].firstName");

        assertThat(names).containsExactly("Ada");
    }

    @Test
    void cityQueryWithinTheCapStillWorks() {
        List<String> names = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personsByCity(city: \"Tel Aviv\") { firstName } }",
                "data.personsByCity[*].firstName");

        assertThat(names).containsExactlyInAnyOrder("Ada", "Grace");
    }
}
