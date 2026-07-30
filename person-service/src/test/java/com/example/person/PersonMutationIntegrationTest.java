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
 * The standard framework mutations end to end: update-by-filter, save-or-update,
 * save-or-override and delete-by-filter, all manufactured by the infrastructure's
 * MutationResolverFactory and driven by the SAME PersonFilter as the queries.
 *
 * <p>Shared-context discipline: every test creates its own persons (unique emails and
 * marker last names) and filters on them, so tests neither collide with each other nor
 * with the seed data.
 */
@SpringBootTest
class PersonMutationIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    private String createPerson(String firstName, String lastName, String email, String extraFields) {
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"" + firstName + "\", lastName: \"" + lastName + "\", "
                + "email: \"" + email + "\"" + (extraFields.isEmpty() ? "" : ", " + extraFields)
                + " }) { id } }";
        Object id = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createPerson.id");
        return String.valueOf(id);
    }

    private static String lastNameFilter(String lastName) {
        return "{ lastName: { equals: { value: \"" + lastName + "\" } } }";
    }

    // ---------------------------------------------------------- updatePersons

    @Test
    @SuppressWarnings("unchecked")
    void updateByFilterMergesTheInputOntoEveryMatch() {
        createPerson("Amy", "UpdFam", "amy.updfam@example.com", "salary: 1000, nickname: \"Aims\"");
        createPerson("Ben", "UpdFam", "ben.updfam@example.com", "salary: 2000");

        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { updatePersons(filter: " + lastNameFilter("UpdFam") + ", "
                        + "input: { salary: 5555 }) { firstName nickname salary version } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        List<Map<String, Object>> updated = (List<Map<String, Object>>) data.get("updatePersons");
        assertThat(updated).hasSize(2);
        // the non-null input field landed on every match...
        assertThat(updated).allSatisfy(person ->
                assertThat(((Number) person.get("salary")).doubleValue()).isEqualTo(5555.0));
        // ...everything the input did not carry survived untouched
        assertThat(updated).extracting(person -> person.get("firstName"))
                .containsExactlyInAnyOrder("Amy", "Ben");
        assertThat(updated).anySatisfy(person -> assertThat(person.get("nickname")).isEqualTo("Aims"));
        // a real write: optimistic-locking version moved past the initial revision
        assertThat(updated).allSatisfy(person ->
                assertThat(((Number) person.get("version")).longValue()).isGreaterThanOrEqualTo(1L));
    }

    @Test
    void updateByFilterReplacesCollectionsAsAWhole() {
        createPerson("Cara", "UpdPhones", "cara.updphones@example.com",
                "phoneNumbers: [{ type: MOBILE, number: \"050-1111111\" }], hobbies: [\"chess\"]");

        List<String> numbers = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { updatePersons(filter: " + lastNameFilter("UpdPhones") + ", "
                        + "input: { phoneNumbers: [{ type: WORK, number: \"03-2222222\" }, "
                        + "{ type: HOME, number: \"03-3333333\" }], hobbies: [\"go\", \"shogi\"] }) "
                        + "{ phoneNumbers { number } } }",
                "data.updatePersons[0].phoneNumbers[*].number");
        assertThat(numbers).containsExactly("03-2222222", "03-3333333");

        List<String> hobbies = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: " + lastNameFilter("UpdPhones") + ") { hobbies } }",
                "data.persons[0].hobbies[*]");
        assertThat(hobbies).containsExactlyInAnyOrder("go", "shogi");
    }

    @Test
    void updateWithAnEmptyFilterIsRejected() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { updatePersons(filter: { }, input: { salary: 1 }) { id } }");

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("non-empty filter");
        assertThat(error.getExtensions().get("literal")).isEqualTo("INVALID_ARGUMENT");
        assertThat(error.getExtensions().get("httpStatus")).isEqualTo(400);
    }

    @Test
    void updateInputRunsThroughItsOwnJsonSchema() {
        // salary >= 0 lives only in person-update.json
        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { updatePersons(filter: " + lastNameFilter("Whoever") + ", "
                        + "input: { salary: -5 }) { id } }");

        assertThat(result.getErrors()).hasSize(1);
        GraphQLError error = result.getErrors().get(0);
        assertThat(error.getMessage()).contains("person-update");
        assertThat(error.getExtensions().get("literal")).isEqualTo("SCHEMA_VALIDATION_FAILED");
    }

    // ----------------------------------------------------- saveOrUpdatePerson

    @Test
    @SuppressWarnings("unchecked")
    void saveOrUpdateCreatesWhenNothingMatchesTheFilter() {
        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { saveOrUpdatePerson(filter: " + lastNameFilter("UpsNew") + ", "
                        + "input: { firstName: \"Uma\", lastName: \"UpsNew\", email: \"uma.upsnew@example.com\" }, "
                        + "updateInput: { salary: 9999 }) { email salary } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        List<Map<String, Object>> created = (List<Map<String, Object>>) data.get("saveOrUpdatePerson");
        // create path: made from input, updateInput ignored (no salary landed)
        assertThat(created).hasSize(1);
        assertThat(created.get(0).get("email")).isEqualTo("uma.upsnew@example.com");
        assertThat(created.get(0).get("salary")).isNull();
    }

    @Test
    void saveOrUpdateUpdatesTheMatchesWithTheUpdateInput() {
        String id = createPerson("Vic", "UpsExisting", "vic.upsexisting@example.com", "salary: 100");

        List<Map<String, Object>> updated = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { saveOrUpdatePerson(filter: " + lastNameFilter("UpsExisting") + ", "
                        + "input: { firstName: \"Never\", lastName: \"Created\", email: \"never@example.com\" }, "
                        + "updateInput: { salary: 777 }) { id firstName salary } }",
                "data.saveOrUpdatePerson[*]");

        // update path: the existing row was updated in place, nothing new was created
        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).get("id")).isEqualTo(id);
        assertThat(updated.get(0).get("firstName")).isEqualTo("Vic");
        assertThat(((Number) updated.get(0).get("salary")).doubleValue()).isEqualTo(777.0);

        List<String> phantom = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: { email: { equals: { value: \"never@example.com\" } } }) { id } }",
                "data.persons[*].id");
        assertThat(phantom).isEmpty();
    }

    @Test
    void saveOrUpdateFallsBackToTheCreateInputWhenUpdateInputIsOmitted() {
        createPerson("Wes", "UpsFallback", "wes.upsfallback@example.com", "salary: 100");

        List<Map<String, Object>> updated = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { saveOrUpdatePerson(filter: " + lastNameFilter("UpsFallback") + ", "
                        + "input: { firstName: \"Wesley\", lastName: \"UpsFallback\", "
                        + "email: \"wes.upsfallback@example.com\", salary: 300 }) { firstName salary } }",
                "data.saveOrUpdatePerson[*]");

        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).get("firstName")).isEqualTo("Wesley");
        assertThat(((Number) updated.get(0).get("salary")).doubleValue()).isEqualTo(300.0);
    }

    // --------------------------------------------------- saveOrOverridePerson

    @Test
    void saveOrOverrideCreatesForAnUnknownEmail() {
        String id = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { saveOrOverridePerson(input: { firstName: \"Ora\", lastName: \"OvrNew\", "
                        + "email: \"ora.ovrnew@example.com\" }) { id } }",
                "data.saveOrOverridePerson.id");
        assertThat(id).isNotNull();

        List<String> found = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: " + lastNameFilter("OvrNew") + ") { email } }",
                "data.persons[*].email");
        assertThat(found).containsExactly("ora.ovrnew@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveOrOverrideReplacesTheWholeBusinessStateOfTheExistingPerson() {
        String id = createPerson("Old", "OvrExisting", "ovr.existing@example.com",
                "nickname: \"Oldie\", salary: 4000, hobbies: [\"stamps\"]");

        // same email, no nickname/salary/hobbies: override semantics blank them out
        ExecutionResult result = dgsQueryExecutor.execute(
                "mutation { saveOrOverridePerson(input: { firstName: \"New\", lastName: \"OvrExisting\", "
                        + "email: \"ovr.existing@example.com\" }) "
                        + "{ id firstName nickname salary hobbies } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        Map<String, Object> overridden = (Map<String, Object>) data.get("saveOrOverridePerson");
        // same row (technical identity preserved), completely new business state
        assertThat(overridden.get("id")).isEqualTo(id);
        assertThat(overridden.get("firstName")).isEqualTo("New");
        assertThat(overridden.get("nickname")).isNull();
        assertThat(overridden.get("salary")).isNull();
        assertThat((List<String>) overridden.get("hobbies")).isEmpty();
    }

    // --------------------------------------------------------- deletePersons

    @Test
    void deleteByFilterSoftDeletesEveryMatchAndFeedsTheDeletions() {
        Number baseline = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personMaxSequence }", "data.personMaxSequence");
        createPerson("Dana", "DelFam", "dana.delfam@example.com", "");
        createPerson("Dave", "DelFam", "dave.delfam@example.com", "");

        Integer deleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePersons(filter: " + lastNameFilter("DelFam") + ") }",
                "data.deletePersons");
        assertThat(deleted).isEqualTo(2);

        // gone from regular queries...
        List<String> remaining = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: " + lastNameFilter("DelFam") + ") { id } }",
                "data.persons[*].id");
        assertThat(remaining).isEmpty();

        // ...but the deletions travel through the replication feed
        List<String> feedDeleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personsBySequence(sequence: " + baseline.longValue() + ", bulkSize: 50) "
                        + "{ deleted { lastName } } }",
                "data.personsBySequence.deleted[*].lastName");
        assertThat(feedDeleted).containsExactly("DelFam", "DelFam");
    }

    @Test
    void deleteWithAnEmptyFilterIsRejected() {
        ExecutionResult result = dgsQueryExecutor.execute("mutation { deletePersons(filter: { }) }");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getExtensions().get("literal")).isEqualTo("INVALID_ARGUMENT");
    }
}
