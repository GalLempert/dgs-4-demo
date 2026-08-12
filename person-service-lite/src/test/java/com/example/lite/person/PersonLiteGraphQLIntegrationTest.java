package com.example.lite.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests through the whole lite stack: DGS endpoint -> lite dispatch
 * controller -> legacy-style fetcher (or native resolver) -> service -> in-memory DAL.
 */
@SpringBootTest
class PersonLiteGraphQLIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    void allPersonsReturnsSeededDataWithComputedFullName() {
        List<String> fullNames = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { fullName } }", "data.allPersons[*].fullName");
        assertThat(fullNames).contains("Ada Lovelace", "Alan Turing", "Grace Hopper");
    }

    @Test
    void personByIdReturnsSinglePerson() {
        String email = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personById(id: \"1\") { email } }", "data.personById.email");
        assertThat(email).isEqualTo("ada.lovelace@example.com");
    }

    @Test
    void unknownPersonIdResolvesToNull() {
        Object person = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personById(id: \"999999\") { email } }", "data.personById");
        assertThat(person).isNull();
    }

    @Test
    void personsByCityFiltersCaseInsensitively() {
        List<String> cities = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personsByCity(city: \"tel aviv\") { city } }", "data.personsByCity[*].city");
        assertThat(cities).isNotEmpty().allMatch("Tel Aviv"::equals);
    }

    @Test
    void personCountMatchesTheListQuery() {
        List<String> ids = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allPersons { id } }", "data.allPersons[*].id");
        Integer count = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personCount }", "data.personCount");
        assertThat(count).isEqualTo(ids.size());
    }

    @Test
    void createPersonPersistsThroughServiceAndDal() {
        String mutation = "mutation { createPerson(input: { "
                + "firstName: \"Linus\", lastName: \"Torvalds\", "
                + "email: \"linus@example.com\", city: \"Herzliya\" "
                + "}) { id fullName } }";
        String fullName = dgsQueryExecutor.executeAndExtractJsonPath(mutation, "data.createPerson.fullName");
        assertThat(fullName).isEqualTo("Linus Torvalds");

        List<String> emails = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personsByCity(city: \"Herzliya\") { email } }", "data.personsByCity[*].email");
        assertThat(emails).contains("linus@example.com");
    }

    @Test
    void deletePersonRemovesThePerson() {
        String id = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { createPerson(input: { firstName: \"To\", lastName: \"Delete\", "
                        + "email: \"delete.me@example.com\" }) { id } }",
                "data.createPerson.id");

        Boolean deleted = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePerson(id: \"" + id + "\") }", "data.deletePerson");
        assertThat(deleted).isTrue();

        Boolean deletedAgain = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { deletePerson(id: \"" + id + "\") }", "data.deletePerson");
        assertThat(deletedAgain).isFalse();

        Object person = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ personById(id: \"" + id + "\") { email } }", "data.personById");
        assertThat(person).isNull();
    }
}
