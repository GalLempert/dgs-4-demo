package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared technical truth: every resource is a replicated resource, so Person and
 * Company both implement the single Resource schema interface and the technical fields
 * (id, version, createdAt, updatedAt, sequence, deleted) exist with identical shapes
 * on every resource and can even be selected through interface fragments. The Java
 * side mirrors it: every entity extends BaseEntity, every view extends ResourceView.
 */
@SpringBootTest
class ResourceInterfaceIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    @SuppressWarnings("unchecked")
    void technicalFieldsAreSelectableThroughTheSharedInterfaces() {
        // the same interface fragments work on both resource types
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ persons(filter: { firstName: { equals: { value: \"Ada\" } } }) { "
                        + "... on Resource { id version createdAt updatedAt sequence deleted } } "
                        + "companies(filter: { name: { equals: { value: \"Initech\" } } }) { "
                        + "... on Resource { id version createdAt updatedAt sequence deleted } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        Map<String, Object> ada = ((java.util.List<Map<String, Object>>) data.get("persons")).get(0);
        Map<String, Object> initech = ((java.util.List<Map<String, Object>>) data.get("companies")).get(0);

        for (Map<String, Object> resource : java.util.Arrays.asList(ada, initech)) {
            assertThat(resource.get("id")).isNotNull();
            assertThat(((Number) resource.get("version")).longValue()).isGreaterThanOrEqualTo(0L);
            assertThat((String) resource.get("createdAt")).isNotBlank();
            assertThat((String) resource.get("updatedAt")).isNotBlank();
            assertThat(((Number) resource.get("sequence")).longValue()).isPositive();
            assertThat(resource.get("deleted")).isEqualTo(false);
        }
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

        String companyUnix = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companies(filter: { name: { equals: { value: \"Globex\" } } }) { updatedAt(format: UNIX) } }",
                "data.companies[0].updatedAt");
        assertThat(companyUnix).matches("\\d+");
    }
}
