package com.example.person;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The annotation-driven field presentation: {@code @GraphQLEnum} fields are enriched
 * from the enum catalog, {@code @GraphQLTemporal} fields render in the client-chosen
 * format. Ada Lovelace (id 1, born 1985-12-10, FEMALE) is seeded at startup.
 */
@SpringBootTest
class FieldPresentationIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    void enumFieldIsEnrichedFromTheCatalog() {
        String query = "{ personById(id: \"1\") { gender { code label description } } }";

        String code = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.gender.code");
        String label = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.gender.label");
        String description = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.gender.description");

        assertThat(code).isEqualTo("FEMALE");
        assertThat(label).isEqualTo("Female");
        assertThat(description).contains("female");
    }

    @Test
    void temporalFieldRendersInAllFormatsViaAliases() {
        String query = "{ personById(id: \"1\") { "
                + "iso: birthDate "
                + "unix: birthDate(format: UNIX) "
                + "rfc: birthDate(format: RFC_1123) } }";

        String iso = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.iso");
        String unix = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.unix");
        String rfc = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.rfc");

        assertThat(iso).isEqualTo("1985-12-10");
        assertThat(unix).isEqualTo("503020800");   // 1985-12-10T00:00:00Z
        assertThat(rfc).contains("10 Dec 1985");
    }

    @Test
    void dateTimeFieldSupportsFormatsToo() {
        String query = "{ personById(id: \"1\") { createdAt(format: UNIX) } }";

        String unix = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.createdAt");

        assertThat(unix).matches("\\d+");
    }

    @Test
    void unannotatedFieldsKeepTheirRawValue() {
        String query = "{ personById(id: \"1\") { firstName age } }";

        String firstName = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.firstName");
        Integer age = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.personById.age");

        assertThat(firstName).isEqualTo("Ada");
        assertThat(age).isPositive();
    }
}
