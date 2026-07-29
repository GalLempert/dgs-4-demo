package com.example.person.config;

import com.example.infrastructure.graphql.dispatch.GraphQLResolver;
import com.example.infrastructure.graphql.model.GraphQLModelSource;
import com.example.infrastructure.replication.ReplicationResolverFactory;
import com.example.person.service.PersonService;
import com.example.person.service.dto.PersonView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Registers the person domain's GraphQL model classes and wires the four standard
 * replicated-resource queries to their schema fields - the resolvers themselves are
 * manufactured by the infrastructure's {@link ReplicationResolverFactory}, so the
 * domain contributes only these one-line bean declarations.
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
}
