package com.example.person.config;

import com.example.infrastructure.graphql.model.GraphQLModelSource;
import com.example.person.service.dto.PersonView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Registers the person domain's GraphQL model classes so the infrastructure picks up
 * their presentation annotations.
 */
@Configuration
public class PersonGraphQLConfig {

    @Bean
    public GraphQLModelSource personGraphQLModels() {
        return () -> Collections.singletonList(PersonView.class);
    }
}
