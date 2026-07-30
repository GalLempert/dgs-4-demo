package com.example.company;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The standard framework mutations on the company service - entirely inherited: this
 * module registers factory-made resolver beans and declares schema fields, and writes
 * ZERO resolver/service/DAL mutation code (CompanyService contributes only the view
 * mapping and the natural key). Same shared-context discipline as the other suites:
 * every test creates its own companies with marker names/industries.
 */
@SpringBootTest
class CompanyMutationIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    private String createCompany(String name, String industry, Integer employeeCount) {
        String mutation = "mutation { createCompany(input: { "
                + "name: \"" + name + "\", industry: \"" + industry + "\""
                + (employeeCount == null ? "" : ", employeeCount: " + employeeCount)
                + " }) { id } }";
        Object id = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createCompany.id");
        return String.valueOf(id);
    }

    private static String industryFilter(String industry) {
        return "{ industry: { equals: { value: \"" + industry + "\" } } }";
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateByFilterMergesOntoEveryMatchingCompany() {
        createCompany("UpdCo One", "UpdIndustry", 10);
        createCompany("UpdCo Two", "UpdIndustry", 20);

        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { updateCompanies(filter: " + industryFilter("UpdIndustry") + ", "
                        + "input: { city: \"Merged City\" }) { name city employeeCount } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        List<Map<String, Object>> updated = (List<Map<String, Object>>) data.get("updateCompanies");
        assertThat(updated).hasSize(2);
        assertThat(updated).allSatisfy(company ->
                assertThat(company.get("city")).isEqualTo("Merged City"));
        // fields the input did not carry survive the merge
        assertThat(updated).extracting(company -> company.get("employeeCount"))
                .containsExactlyInAnyOrder(10, 20);
    }

    @Test
    void saveOrUpdateCoversBothPathsWithOneFieldDefinition() {
        // round 1: nothing matches - the create input materializes
        List<String> created = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { saveOrUpdateCompany(filter: " + industryFilter("UpsIndustry") + ", "
                        + "input: { name: \"UpsCo\", industry: \"UpsIndustry\", employeeCount: 5 }, "
                        + "updateInput: { employeeCount: 50 }) { name } }",
                "data.saveOrUpdateCompany[*].name");
        assertThat(created).containsExactly("UpsCo");

        // round 2: the same call now matches - the update input is applied instead
        List<Integer> counts = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { saveOrUpdateCompany(filter: " + industryFilter("UpsIndustry") + ", "
                        + "input: { name: \"UpsCo\", industry: \"UpsIndustry\", employeeCount: 5 }, "
                        + "updateInput: { employeeCount: 50 }) { employeeCount } }",
                "data.saveOrUpdateCompany[*].employeeCount");
        assertThat(counts).containsExactly(50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveOrOverrideReplacesTheCompanyBehindItsName() {
        String id = createCompany("OvrCo", "OldIndustry", 42);

        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { saveOrOverrideCompany(input: { name: \"OvrCo\", city: \"Fresh City\" }) "
                        + "{ id name industry city employeeCount } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        Map<String, Object> overridden = (Map<String, Object>) data.get("saveOrOverrideCompany");
        // same row, business state replaced: fields absent from the input are blanked
        assertThat(overridden.get("id")).isEqualTo(id);
        assertThat(overridden.get("city")).isEqualTo("Fresh City");
        assertThat(overridden.get("industry")).isNull();
        assertThat(overridden.get("employeeCount")).isNull();
    }

    @Test
    void deleteByFilterReportsTheCountAndHidesTheRows() {
        createCompany("DelCo One", "DelIndustry", null);
        createCompany("DelCo Two", "DelIndustry", null);

        Integer deleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deleteCompanies(filter: " + industryFilter("DelIndustry") + ") }",
                "data.deleteCompanies");
        assertThat(deleted).isEqualTo(2);

        List<String> remaining = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companies(filter: " + industryFilter("DelIndustry") + ") { id } }",
                "data.companies[*].id");
        assertThat(remaining).isEmpty();
    }

    @Test
    void duplicateCompanyNameIsRejectedBecauseTheNameIsTheNaturalKey() {
        createCompany("UniqueCo", "UniqIndustry", null);

        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { createCompany(input: { name: \"UniqueCo\" }) { id } }");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("already exists");
        assertThat(result.getErrors().get(0).getExtensions().get("literal")).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(result.getErrors().get(0).getExtensions().get("httpStatus")).isEqualTo(409);
    }

    @Test
    void filteredMutationRejectsAnEmptyFilterLikeTheQueriesRejectNothing() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { deleteCompanies(filter: { }) }");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getExtensions().get("literal")).isEqualTo("INVALID_ARGUMENT");
    }
}
