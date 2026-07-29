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
 * The shared technical truth: every resource is a replicated resource, so Person
 * implements the single Resource schema interface and the technical fields (id,
 * version, createdAt, updatedAt, sequence, deleted) can be selected through interface
 * fragments. The Java side mirrors it: every entity extends BaseEntity, every view
 * extends ResourceView. (company-service, a separate service on the same framework,
 * has the identical shape - covered by its own test suite.)
 */
@SpringBootTest
class ResourceInterfaceIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    @SuppressWarnings("unchecked")
    void technicalFieldsAreSelectableThroughTheSharedInterface() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ persons(filter: { firstName: { equals: { value: \"Ada\" } } }) { "
                        + "... on Resource { id version createdAt updatedAt sequence deleted } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        Map<String, Object> ada = ((List<Map<String, Object>>) data.get("persons")).get(0);

        assertThat(ada.get("id")).isNotNull();
        assertThat(((Number) ada.get("version")).longValue()).isGreaterThanOrEqualTo(0L);
        assertThat((String) ada.get("createdAt")).isNotBlank();
        assertThat((String) ada.get("updatedAt")).isNotBlank();
        assertThat(((Number) ada.get("sequence")).longValue()).isPositive();
        assertThat(ada.get("deleted")).isEqualTo(false);
    }

    @Test
    void versionStartsAtZeroAndIncrementsOnEveryUpdate() {
        String id = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { createPerson(input: { firstName: \"Versioned\", lastName: \"Row\", "
                        + "email: \"versioned.row@interface-test.example.com\" }) { id } }",
                "data.createPerson.id");

        Number initialVersion = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personById(id: \"" + id + "\") { version } }", "data.personById.version");
        assertThat(initialVersion.longValue()).isEqualTo(0L);

        // the mutation RESPONSE must already carry the bumped version: the DAL flushes
        // on save so @Version/@PreUpdate values are current before view mapping
        Number responseVersion = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { updatePersonSalary(id: \"" + id + "\", salary: 100000) { version } }",
                "data.updatePersonSalary.version");
        assertThat(responseVersion.longValue()).isEqualTo(1L);

        Number bumpedVersion = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personById(id: \"" + id + "\") { version } }", "data.personById.version");
        assertThat(bumpedVersion.longValue()).isEqualTo(1L);
    }

    @Test
    void updatedAtRendersInTheRequestedFormatLikeAnyTemporalField() {
        // the @GraphQLTemporal annotation is inherited from the shared ResourceView base
        String unix = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personById(id: \"1\") { updatedAt(format: UNIX) } }", "data.personById.updatedAt");
        assertThat(unix).matches("\\d+");
    }
}
