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
 * End-to-end tests through the full stack: GraphQL dispatch controller -> resolver ->
 * service (calculations) -> DAL -> H2.
 */
@SpringBootTest
class PersonGraphQLIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    void allPersonsReturnsSeededDataWithCalculatedFields() {
        List<String> fullNames = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { fullName } }", "data.allPersons[*].fullName");
        assertThat(fullNames).contains("Ada Lovelace", "Alan Turing", "Grace Hopper");

        List<Integer> ages = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { age } }", "data.allPersons[*].age");
        assertThat(ages).allMatch(age -> age != null && age > 0);

        // lazy collections must be materialized by the service layer inside the transaction
        List<List<String>> hobbies = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { hobbies } }", "data.allPersons[*].hobbies");
        assertThat(hobbies).isNotEmpty().allMatch(list -> !list.isEmpty());
    }

    @Test
    void personsByCityFiltersOnNestedAddress() {
        List<String> cities = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personsByCity(city: \"tel aviv\") { address { city } } }",
                "data.personsByCity[*].address.city");
        assertThat(cities).isNotEmpty().allMatch("Tel Aviv"::equals);
    }

    @Test
    void createPersonMutationPersistsNestedFieldsAndCalculates() {
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"Linus\", lastName: \"Torvalds\", email: \"linus@example.com\", "
                + "birthDate: \"1969-12-28\", gender: MALE, salary: 720000, "
                + "hireDate: \"2020-02-01\", heightCm: 177, weightKg: 75.0, "
                + "address: { street: \"Kernel Rd\", houseNumber: 1, city: \"Herzliya\", country: \"Israel\" }, "
                + "phoneNumbers: [{ type: MOBILE, number: \"+972-52-0000000\" }], "
                + "hobbies: [\"diving\"] "
                + "}) { id fullName age monthlyNetSalary bmi address { city } phoneNumbers { type number } } }";

        String fullName = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createPerson.fullName");
        assertThat(fullName).isEqualTo("Linus Torvalds");

        // monthlyNetSalary = 720000 * 0.75 / 12 = 45000
        Double monthlyNet = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personsByCity(city: \"Herzliya\") { monthlyNetSalary } }",
                "data.personsByCity[0].monthlyNetSalary");
        assertThat(monthlyNet).isEqualTo(45000.0);
    }

    @Test
    void simpleFieldsFlowThroughAllLayersWithZeroMappingCode() {
        // nickname exists only as declarations (schema, input DTO, entity, view) -
        // no mapper, resolver or service change; the declarative mapping carries it
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"Nick\", lastName: \"Named\", email: \"nick@example.com\", "
                + "nickname: \"Nicky\", birthDate: \"1992-03-04\", hobbies: [\"testing\"] "
                + "}) { nickname fullName } }";

        String nickname = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createPerson.nickname");
        assertThat(nickname).isEqualTo("Nicky");

        // and it is persisted, not just echoed
        List<String> stored = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { nickname } }", "data.allPersons[*].nickname");
        assertThat(stored).contains("Nicky");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullFieldsRenderAsExplicitNullsByDefault() {
        // graphql.response.omit-null-fields defaults to false: per the GraphQL spec a
        // requested field with a null value keeps its key ("nickname": null)
        ExecutionResult result = dgsQueryExecutor.execute(
                "{ personsByCity(city: \"Haifa\") { firstName nickname } }");

        assertThat(result.getErrors()).isEmpty();
        Map<String, Object> data = (Map<String, Object>) result.toSpecification().get("data");
        List<Map<String, Object>> persons = (List<Map<String, Object>>) data.get("personsByCity");
        assertThat(persons).isNotEmpty()
                .allSatisfy(person -> assertThat(person).containsEntry("nickname", null));
    }

    @Test
    void unknownPersonYieldsNotFoundError() {
        ExecutionResult result = dgsQueryExecutor.execute("{ personById(id: \"99999\") { id } }");
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0).getMessage()).contains("not found");
    }

    @Test
    void deletePersonReturnsFalseForMissingPerson() {
        Boolean deleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePerson(id: \"424242\") }", "data.deletePerson");
        assertThat(deleted).isFalse();
    }
}
