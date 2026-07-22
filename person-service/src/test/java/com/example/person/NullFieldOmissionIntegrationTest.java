package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the {@code graphql.response.omit-null-fields} toggle ON, null-valued fields must
 * disappear from the response JSON entirely (key and value), while non-null fields are
 * untouched. The seeded persons have no nickname, so {@code nickname} resolves to null.
 * The spec-compliant default (explicit nulls) is asserted in
 * {@link PersonGraphQLIntegrationTest}.
 */
@SpringBootTest(properties = "graphql.response.omit-null-fields=true")
class NullFieldOmissionIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    @SuppressWarnings("unchecked")
    void nullFieldsAreOmittedFromTheResponse() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ personsByCity(city: \"Haifa\") { firstName nickname address { city } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        List<Map<String, Object>> persons = (List<Map<String, Object>>) data.get("personsByCity");

        assertThat(persons).isNotEmpty();
        for (Map<String, Object> person : persons) {
            assertThat(person).containsKeys("firstName", "address").doesNotContainKey("nickname");
            assertThat((Map<String, Object>) person.get("address")).containsEntry("city", "Haifa");
        }
    }

    @Test
    void nonNullValuesOfTheSameFieldAreStillReturned() {
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"Omit\", lastName: \"Nulls\", email: \"omit.nulls@example.com\", "
                + "nickname: \"Sparse\", birthDate: \"1990-01-01\", hobbies: [\"json\"] "
                + "}) { nickname } }";

        String nickname = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createPerson.nickname");
        assertThat(nickname).isEqualTo("Sparse");
    }
}
