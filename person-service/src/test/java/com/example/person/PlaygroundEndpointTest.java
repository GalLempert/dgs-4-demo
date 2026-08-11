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
 * The GraphQL Playground UI (playground-spring-boot-starter, pulled in by
 * graphql-infrastructure) must be served at /playground and stay self-hosted:
 * with the starter's CDN mode off (the default) all assets load from the jar
 * under /vendor/playground, so the page works offline.
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
                .contains("/vendor/playground")   // assets bundled in the starter jar
                .doesNotContain("http://unpkg.com")
                .doesNotContain("https://unpkg.com")
                .doesNotContain("cdn.");
    }

    @Test
    void playgroundStaticAssetsAreServedLocally() throws Exception {
        mockMvc.perform(get("/vendor/playground/static/js/middleware.js"))
                .andExpect(status().isOk());
    }
}
