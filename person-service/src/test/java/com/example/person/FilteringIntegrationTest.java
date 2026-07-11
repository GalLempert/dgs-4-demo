package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dynamic filter system end to end: GraphQL filter input -> FilterParser ->
 * FilterSpecificationBuilder -> dynamically built WHERE clause on H2.
 *
 * Seeded: Ada Lovelace (Tel Aviv, 168cm, FEMALE, born 1985), Alan Turing (Haifa,
 * 180cm, MALE, born 1990), Grace Hopper (Tel Aviv, 165cm, FEMALE, born 1978).
 */
@SpringBootTest
class FilteringIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    private List<String> firstNames(String filter) {
        return dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons(filter: " + filter + ") { firstName } }", "data.persons[*].firstName");
    }

    @Test
    void equalsOnStringField() {
        assertThat(firstNames("{ firstName: { equals: { value: \"Ada\" } } }"))
                .containsExactly("Ada");
    }

    @Test
    void likeOnStringField() {
        assertThat(firstNames("{ lastName: { like: { value: \"%ing\" } } }"))
                .containsExactly("Alan");
    }

    @Test
    void inOnStringField() {
        assertThat(firstNames("{ firstName: { in: { values: [\"Ada\", \"Alan\"] } } }"))
                .containsExactlyInAnyOrder("Ada", "Alan");
    }

    @Test
    void notEqualsExcludes() {
        assertThat(firstNames("{ firstName: { notEquals: { value: \"Ada\" } } }"))
                .doesNotContain("Ada");
    }

    @Test
    void betweenOnIntField() {
        assertThat(firstNames("{ heightCm: { between: { from: 160, to: 170 } } }"))
                .containsExactlyInAnyOrder("Ada", "Grace");
    }

    @Test
    void multiplePredicatesOnOneFieldAndTogether() {
        assertThat(firstNames("{ heightCm: { greaterThan: { value: 160 }, lessThan: { value: 170 } } }"))
                .containsExactlyInAnyOrder("Ada", "Grace");
    }

    @Test
    void nestedAddressFieldBuildsDottedPath() {
        assertThat(firstNames("{ address: { city: { equals: { value: \"Tel Aviv\" } } } }"))
                .containsExactlyInAnyOrder("Ada", "Grace");
    }

    @Test
    void multipleFieldsCombineWithAnd() {
        assertThat(firstNames("{ address: { city: { equals: { value: \"Tel Aviv\" } } }, "
                + "heightCm: { greaterThan: { value: 166 } } }"))
                .containsExactly("Ada");
    }

    @Test
    void dateFilterUsesTheDateScalar() {
        // other test classes may add persons to the shared context - assert on seeds
        assertThat(firstNames("{ birthDate: { greaterThan: { value: \"1980-01-01\" } } }"))
                .contains("Ada", "Alan")
                .doesNotContain("Grace");
    }

    @Test
    void enumCodeIsCoercedToTheEntityEnum() {
        assertThat(firstNames("{ gender: { equals: { value: \"FEMALE\" } } }"))
                .containsExactlyInAnyOrder("Ada", "Grace");
    }

    @Test
    void floatFilterOnBigDecimalColumn() {
        // salaries: Ada 540000, Alan 480000, Grace 620000 (other tests may add more)
        assertThat(firstNames("{ salary: { greaterThan: { value: 500000 } } }"))
                .contains("Ada", "Grace")
                .doesNotContain("Alan");
    }

    @Test
    void booleanFilterMatchesAllSeededActivePersons() {
        assertThat(firstNames("{ active: { equals: { value: false } } }")).isEmpty();
    }

    @Test
    void noFilterReturnsEverySeededPerson() {
        List<String> names = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ persons { firstName } }", "data.persons[*].firstName");
        assertThat(names).contains("Ada", "Alan", "Grace");
    }
}
