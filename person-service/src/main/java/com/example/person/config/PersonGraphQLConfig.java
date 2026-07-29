package com.example.person.config;

import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.infrastructure.graphql.model.GraphQLModelSource;
import com.example.infrastructure.replication.MutationResolverFactory;
import com.example.infrastructure.replication.ReplicationResolverFactory;
import com.example.person.service.PersonService;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Registers the person domain's GraphQL model classes and wires the standard
 * replicated-resource queries and mutations to their schema fields - the resolvers
 * themselves are manufactured by the infrastructure's
 * {@link ReplicationResolverFactory} / {@link MutationResolverFactory}, so the domain
 * contributes only these one-line bean declarations. (createPerson, updatePersonSalary
 * and deletePerson keep their hand-written resolver classes as examples of the
 * non-factory way.)
 */
@Configuration
public class PersonGraphQLConfig {

    @Bean
    public GraphQLModelSource personGraphQLModels() {
        return () -> Collections.singletonList(PersonView.class);
    }

    @Bean
    public GraphQLResolver persons(ReplicationResolverFactory factory, PersonService service) {
        return factory.filteredList("persons", service);
    }

    @Bean
    public GraphQLResolver personsBySequence(ReplicationResolverFactory factory, PersonService service) {
        return factory.bySequence("personsBySequence", service);
    }

    @Bean
    public GraphQLResolver countPersonsByFilter(ReplicationResolverFactory factory, PersonService service) {
        return factory.countByFilter("countPersonsByFilter", service);
    }

    @Bean
    public GraphQLResolver personMaxSequence(ReplicationResolverFactory factory, PersonService service) {
        return factory.maxSequence("personMaxSequence", service);
    }

    // ------------------------------------------------------- standard mutations
    // The UpdatePersonInput schema type maps onto the same Java DTO as creation -
    // at merge time every field is optional anyway, only non-null fields apply.

    @Bean
    public GraphQLResolver updatePersons(MutationResolverFactory factory, PersonService service) {
        return factory.updateByFilter("updatePersons", service, CreatePersonInput.class)
                .validating("input", "person-update");
    }

    @Bean
    public GraphQLResolver saveOrUpdatePerson(MutationResolverFactory factory, PersonService service) {
        return factory.saveOrUpdate("saveOrUpdatePerson", service, CreatePersonInput.class, CreatePersonInput.class)
                .validating("input", "person-create")
                .validating("updateInput", "person-update");
    }

    @Bean
    public GraphQLResolver saveOrOverridePerson(MutationResolverFactory factory, PersonService service) {
        return factory.saveOrOverride("saveOrOverridePerson", service, CreatePersonInput.class)
                .validating("input", "person-create");
    }

    @Bean
    public GraphQLResolver deletePersons(MutationResolverFactory factory, PersonService service) {
        return factory.deleteByFilter("deletePersons", service);
    }
}
