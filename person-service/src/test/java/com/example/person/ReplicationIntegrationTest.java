package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The replication feed end to end: personsBySequence / countPersonsByFilter /
 * personMaxSequence, sequence stamping on every write and soft deletes.
 *
 * <p>The Spring context (and the H2 database) is shared with the other test classes,
 * so every test captures the current personMaxSequence as its baseline, creates its
 * own persons (unique emails, cities no other test asserts on) and only looks at the
 * feed AFTER that baseline - earlier rows never leak in.
 */
@SpringBootTest
class ReplicationIntegrationTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    // ---------------------------------------------------------------- helpers

    private long maxSequence() {
        Number max = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personMaxSequence }", "data.personMaxSequence");
        return max.longValue();
    }

    private String createPerson(String firstName, String city) {
        String email = firstName.toLowerCase() + "-" + UNIQUE.incrementAndGet() + "@replication-test.example.com";
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"" + firstName + "\", lastName: \"Replicated\", email: \"" + email + "\", "
                + "address: { street: \"Feed St\", houseNumber: 1, city: \"" + city + "\" } "
                + "}) { id } }";
        Object id = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createPerson.id");
        return String.valueOf(id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(long sequence, int bulkSize, String filter) {
        String filterArgument = filter == null ? "" : ", filter: " + filter;
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ personsBySequence(sequence: " + sequence + ", bulkSize: " + bulkSize + filterArgument + ") { "
                        + "updated { id firstName sequence deleted } "
                        + "deleted { id firstName sequence deleted } "
                        + "filteredOutIds nextSequence } }");
        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        return (Map<String, Object>) data.get("personsBySequence");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> part(Map<String, Object> page, String name) {
        return (List<Map<String, Object>>) page.get(name);
    }

    @SuppressWarnings("unchecked")
    private List<Object> filteredOutIds(Map<String, Object> page) {
        // the ID scalar serializes ids as strings
        return (List<Object>) page.get("filteredOutIds");
    }

    private List<String> firstNames(Map<String, Object> page, String name) {
        return part(page, name).stream().map(p -> (String) p.get("firstName"))
                .collect(java.util.stream.Collectors.toList());
    }

    private long nextSequence(Map<String, Object> page) {
        return ((Number) page.get("nextSequence")).longValue();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void sequenceAndDeletedAreExposedOnPersonAndEveryWriteBumpsTheMax() {
        long before = maxSequence();
        createPerson("SeqExposed", "Feedville");
        long after = maxSequence();

        assertThat(after).isGreaterThan(before);

        Map<String, Object> page = page(before, 10, null);
        Map<String, Object> created = part(page, "updated").stream()
                .filter(p -> "SeqExposed".equals(p.get("firstName")))
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(((Number) created.get("sequence")).longValue()).isGreaterThan(before);
        assertThat(created.get("deleted")).isEqualTo(false);
    }

    @Test
    void feedReturnsEverythingStrictlyAfterTheGivenSequence() {
        long baseline = maxSequence();
        createPerson("FeedOne", "Feedville");
        createPerson("FeedTwo", "Feedville");

        Map<String, Object> page = page(baseline, 10, null);

        assertThat(firstNames(page, "updated")).containsExactly("FeedOne", "FeedTwo");
        assertThat(part(page, "deleted")).isEmpty();
        assertThat(filteredOutIds(page)).isEmpty();
        assertThat(nextSequence(page)).isEqualTo(maxSequence());
    }

    @Test
    void bulkSizeChunksTheFeedAndNextSequenceResumesExactly() {
        long baseline = maxSequence();
        createPerson("ChunkA", "Feedville");
        createPerson("ChunkB", "Feedville");
        createPerson("ChunkC", "Feedville");

        Map<String, Object> first = page(baseline, 2, null);
        assertThat(firstNames(first, "updated")).containsExactly("ChunkA", "ChunkB");
        assertThat(nextSequence(first)).isLessThan(maxSequence());

        Map<String, Object> second = page(nextSequence(first), 2, null);
        assertThat(firstNames(second, "updated")).containsExactly("ChunkC");
        assertThat(nextSequence(second)).isEqualTo(maxSequence());
    }

    @Test
    void overShotSequenceSnapsBackToTheActualMax() {
        createPerson("Overshoot", "Feedville");
        long max = maxSequence();

        Map<String, Object> page = page(max + 1_000_000, 10, null);

        assertThat(part(page, "updated")).isEmpty();
        assertThat(part(page, "deleted")).isEmpty();
        assertThat(nextSequence(page)).isEqualTo(max);

        // and polling from that smart sequence is an empty page, not an error
        assertThat(part(page(nextSequence(page), 10, null), "updated")).isEmpty();
    }

    @Test
    void filterSplitsThePageAndReportsFilteredOutIds() {
        long baseline = maxSequence();
        String matchingId = createPerson("InFilter", "FilterTown");
        String filteredOutId = createPerson("OutOfFilter", "ElsewhereTown");

        Map<String, Object> page = page(baseline, 10,
                "{ address: { city: { equals: { value: \"FilterTown\" } } } }");

        assertThat(firstNames(page, "updated")).containsExactly("InFilter");
        assertThat(part(page, "updated").get(0).get("id")).isEqualTo(matchingId);
        assertThat(part(page, "deleted")).isEmpty();
        assertThat(filteredOutIds(page)).containsExactly(filteredOutId);
        assertThat(nextSequence(page)).isEqualTo(maxSequence());
    }

    @Test
    void deletedPersonsTravelThroughTheFeedButLeaveRegularQueries() {
        long baseline = maxSequence();
        String id = createPerson("Doomed", "Feedville");

        Boolean deleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePerson(id: \"" + id + "\") }", "data.deletePerson");
        assertThat(deleted).isTrue();

        // the row appears once, in the deleted part, with its post-delete sequence
        Map<String, Object> page = page(baseline, 10, null);
        assertThat(part(page, "updated")).isEmpty();
        assertThat(firstNames(page, "deleted")).containsExactly("Doomed");
        assertThat(part(page, "deleted").get(0).get("deleted")).isEqualTo(true);
        assertThat(nextSequence(page)).isEqualTo(maxSequence());

        // regular queries no longer see the person
        List<String> found = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: { firstName: { equals: { value: \"Doomed\" } } }) { firstName } }",
                "data.persons[*].firstName");
        assertThat(found).isEmpty();

        // deleting again reports false - the row is already gone from the live view
        Boolean again = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePerson(id: \"" + id + "\") }", "data.deletePerson");
        assertThat(again).isFalse();
    }

    @Test
    void countPersonsByFilterCountsLiveRowsAndOptionallyDeletedOnes() {
        String keep = createPerson("CountKeep", "CountTown");
        String drop = createPerson("CountDrop", "CountTown");
        dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePerson(id: \"" + drop + "\") }", "data.deletePerson");

        String filter = "{ address: { city: { equals: { value: \"CountTown\" } } } }";
        Integer live = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ countPersonsByFilter(filter: " + filter + ") }", "data.countPersonsByFilter");
        Integer all = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ countPersonsByFilter(filter: " + filter + ", includeDeleted: true) }",
                "data.countPersonsByFilter");

        assertThat(live).isEqualTo(1);
        assertThat(all).isEqualTo(2);
        assertThat(keep).isNotNull();
    }

    @Test
    void nonPositiveBulkSizeIsRejectedAsInvalidArgument() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ personsBySequence(sequence: 0, bulkSize: 0) { nextSequence } }");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getExtensions().get("literal")).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void bulkSizeAboveTheResultCapIsRejected() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ personsBySequence(sequence: 0, bulkSize: 1000) { nextSequence } }");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getExtensions().get("literal")).isEqualTo("RESULT_SET_TOO_LARGE");
    }
}
