package com.example.lite.person.config;

import com.example.lite.graphql.GraphQLResolver;
import com.example.lite.graphql.GraphQLResolvers;
import com.example.lite.person.domain.Person;
import com.example.lite.person.graphql.AllPersonsFetcher;
import com.example.lite.person.graphql.CreatePersonFetcher;
import com.example.lite.person.graphql.DeletePersonFetcher;
import com.example.lite.person.graphql.PersonByIdFetcher;
import com.example.lite.person.graphql.PersonsByCityFetcher;
import com.example.lite.person.service.PersonService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The GraphQL wiring of the lite person service - the schema-first counterpart of the
 * old framework's annotated Query/Mutation registry classes: one bean per operation,
 * each reusing an existing legacy {@code DataFetcher} unchanged via
 * {@link GraphQLResolvers}. ({@code Query.personCount} is absent on purpose - its
 * resolver is a {@code @Component} implementing the lite contract directly.)
 */
@Configuration
public class PersonLiteGraphQLConfig {

    // ---------------------------------------------------------------- queries

    @Bean
    public GraphQLResolver personById(PersonService service) {
        return GraphQLResolvers.query("personById", new PersonByIdFetcher(service));
    }

    @Bean
    public GraphQLResolver allPersons(PersonService service) {
        return GraphQLResolvers.query("allPersons", new AllPersonsFetcher(service));
    }

    @Bean
    public GraphQLResolver personsByCity(PersonService service) {
        return GraphQLResolvers.query("personsByCity", new PersonsByCityFetcher(service));
    }

    // -------------------------------------------------------------- mutations

    @Bean
    public GraphQLResolver createPerson(PersonService service) {
        return GraphQLResolvers.mutation("createPerson", new CreatePersonFetcher(service));
    }

    @Bean
    public GraphQLResolver deletePerson(PersonService service) {
        return GraphQLResolvers.mutation("deletePerson", new DeletePersonFetcher(service));
    }

    // ------------------------------------------------------------ type fields

    /** {@code Person.fullName} is computed, not stored - a field resolver supplies it. */
    @Bean
    public GraphQLResolver personFullName(PersonService service) {
        return GraphQLResolvers.field("Person", "fullName", environment -> {
            Person person = environment.getSource();
            return service.fullNameOf(person);
        });
    }
}
