package com.example.lite.company;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests proving the lite infrastructure is reused as-is by a second
 * service: DGS endpoint -> lite dispatch controller -> lambda fetcher -> service ->
 * in-memory DAL.
 */
@SpringBootTest
class CompanyLiteGraphQLIntegrationTest {

    @Autowired
    private DgsQueryExecutor dgsQueryExecutor;

    @Test
    void allCompaniesReturnsSeededData() {
        List<String> names = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ allCompanies { name } }", "data.allCompanies[*].name");
        assertThat(names).contains("Initech", "Acme");
    }

    @Test
    void companyByIdReturnsSingleCompany() {
        String name = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companyById(id: \"1\") { name } }", "data.companyById.name");
        assertThat(name).isEqualTo("Initech");
    }

    @Test
    void unknownCompanyIdResolvesToNull() {
        Object company = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companyById(id: \"999999\") { name } }", "data.companyById");
        assertThat(company).isNull();
    }

    @Test
    void createCompanyPersistsThroughServiceAndDal() {
        String id = dgsQueryExecutor.executeAndExtractJsonPath(
                "mutation { createCompany(name: \"Globex\", industry: \"Energy\") { id } }",
                "data.createCompany.id");

        String industry = dgsQueryExecutor.executeAndExtractJsonPath(
                "{ companyById(id: \"" + id + "\") { industry } }", "data.companyById.industry");
        assertThat(industry).isEqualTo("Energy");
    }
}
