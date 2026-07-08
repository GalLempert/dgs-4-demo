package com.example.person;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The self-hosted playground (graphql-playground module) must be served at /playground
 * and be fully self-contained: no external (CDN) scripts or stylesheets.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlaygroundEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void playgroundPageIsServed() throws Exception {
        mockMvc.perform(get("/playground"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GraphQL Playground")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/graphql")));
    }

    @Test
    void playgroundPageHasNoExternalAssets() throws Exception {
        String body = mockMvc.perform(get("/playground"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("http://unpkg.com")
                .doesNotContain("https://unpkg.com")
                .doesNotContain("cdn.");
    }
}
