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
 * The company service running the SAME infrastructure stack as the person service,
 * as its own standalone application: the four standard queries exist and behave
 * identically even though this module wrote no resolver, DAL or service logic for
 * them - only bean wiring and schema. Seeded: Initech (Software, employees 1+2),
 * Globex (Manufacturing, employee 3), Hooli (Software, no employees).
 *
 * <p>Same shared-context discipline as the person suite: every test baselines on the
 * current companyMaxSequence and creates its own companies.
 */
@SpringBootTest
class CompanyReplicationIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    private long maxSequence() {
        Number max = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companyMaxSequence }", "data.companyMaxSequence");
        return max.longValue();
    }

    private String createCompany(String name, String industry) {
        String mutation = "mutation { createCompany(input: { "
                + "name: \"" + name + "\", industry: \"" + industry + "\", city: \"Feed City\" "
                + "}) { id } }";
        Object id = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createCompany.id");
        return String.valueOf(id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(long sequence, int bulkSize, String filter) {
        String filterArgument = filter == null ? "" : ", filter: " + filter;
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ companiesBySequence(sequence: " + sequence + ", bulkSize: " + bulkSize + filterArgument + ") { "
                        + "updated { id name sequence deleted } "
                        + "deleted { id name deleted } "
                        + "filteredOutIds nextSequence } }");
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        return (Map<String, Object>) data.get("companiesBySequence");
    }

    @SuppressWarnings("unchecked")
    private List<String> names(Map<String, Object> page, String part) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) page.get(part);
        return rows.stream().map(r -> (String) r.get("name")).collect(java.util.stream.Collectors.toList());
    }

    @Test
    void filteredCompaniesQueryUsesTheSharedFilterMachinery() {
        List<String> software = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companies(filter: { industry: { equals: { value: \"Software\" } } }) { name } }",
                "data.companies[*].name");
        assertThat(software).contains("Initech", "Hooli").doesNotContain("Globex");
    }

    @Test
    void employeesAreExposedAsIdOnlyPersonStubs() {
        // cross-service reference: only the key travels; person-service owns the data
        List<String> employeeIds = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companies(filter: { name: { equals: { value: \"Initech\" } } }) "
                        + "{ employees { id } } }",
                "data.companies[0].employees[*].id");
        assertThat(employeeIds).containsExactly("1", "2");

        List<Object> none = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companies(filter: { name: { equals: { value: \"Hooli\" } } }) "
                        + "{ employees { id } } }",
                "data.companies[0].employees[*].id");
        assertThat(none).isEmpty();
    }

    @Test
    void employeeReferencesFlowThroughTheCreateMutation() {
        List<String> ids = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { createCompany(input: { name: \"RefCorp\", employeeIds: [\"7\", \"8\"] }) "
                        + "{ employees { id } } }",
                "data.createCompany.employees[*].id");
        assertThat(ids).containsExactly("7", "8");
    }

    @Test
    @SuppressWarnings("unchecked")
    void technicalFieldsAreSelectableThroughTheSharedInterface() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ companies(filter: { name: { equals: { value: \"Initech\" } } }) { "
                        + "... on Resource { id version createdAt updatedAt sequence deleted } } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        Map<String, Object> initech = ((List<Map<String, Object>>) data.get("companies")).get(0);
        assertThat(initech.get("id")).isNotNull();
        assertThat(((Number) initech.get("sequence")).longValue()).isPositive();
        assertThat(initech.get("deleted")).isEqualTo(false);
    }

    @Test
    void companyFeedPagesAndResumesLikeThePersonFeed() {
        long baseline = maxSequence();
        createCompany("FeedCorp A", "Testing");
        createCompany("FeedCorp B", "Testing");

        Map<String, Object> first = page(baseline, 1, null);
        assertThat(names(first, "updated")).containsExactly("FeedCorp A");

        Map<String, Object> second = page(((Number) first.get("nextSequence")).longValue(), 10, null);
        assertThat(names(second, "updated")).containsExactly("FeedCorp B");
        assertThat(((Number) second.get("nextSequence")).longValue()).isEqualTo(maxSequence());
    }

    @Test
    void overShotCompanySequenceSnapsBackToTheActualMax() {
        createCompany("TailCorp", "Testing");
        long max = maxSequence();

        Map<String, Object> page = page(max + 999_999, 10, null);
        assertThat(names(page, "updated")).isEmpty();
        assertThat(((Number) page.get("nextSequence")).longValue()).isEqualTo(max);
    }

    @Test
    @SuppressWarnings("unchecked")
    void companyFilterPartitionsThePageAndReportsFilteredOutIds() {
        long baseline = maxSequence();
        createCompany("MatchCorp", "FilteredIndustry");
        String filteredOutId = createCompany("MissCorp", "OtherIndustry");

        Map<String, Object> page = page(baseline, 10,
                "{ industry: { equals: { value: \"FilteredIndustry\" } } }");

        assertThat(names(page, "updated")).containsExactly("MatchCorp");
        assertThat((List<Object>) page.get("filteredOutIds")).containsExactly(filteredOutId);
    }

    @Test
    void companySoftDeleteTravelsThroughTheFeedAndCountRespectsIncludeDeleted() {
        long baseline = maxSequence();
        String id = createCompany("DoomedCorp", "CountedIndustry");

        Boolean deleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deleteCompany(id: \"" + id + "\") }", "data.deleteCompany");
        assertThat(deleted).isTrue();

        Map<String, Object> page = page(baseline, 10, null);
        assertThat(names(page, "updated")).isEmpty();
        assertThat(names(page, "deleted")).containsExactly("DoomedCorp");

        String filter = "{ industry: { equals: { value: \"CountedIndustry\" } } }";
        Integer live = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ countCompaniesByFilter(filter: " + filter + ") }", "data.countCompaniesByFilter");
        Integer all = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ countCompaniesByFilter(filter: " + filter + ", includeDeleted: true) }",
                "data.countCompaniesByFilter");
        assertThat(live).isEqualTo(0);
        assertThat(all).isEqualTo(1);
    }
}
