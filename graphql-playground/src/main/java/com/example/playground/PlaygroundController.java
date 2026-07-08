package com.example.playground;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the self-hosted playground page. Disable with
 * {@code graphql.playground.enabled=false} (e.g. in production profiles); point it at a
 * non-default GraphQL endpoint with {@code graphql.playground.endpoint}.
 */
@Controller
@ConditionalOnProperty(name = "graphql.playground.enabled", havingValue = "true", matchIfMissing = true)
public class PlaygroundController {

    private final String html;

    public PlaygroundController(@Value("${graphql.playground.endpoint:/graphql}") String graphqlEndpoint) {
        try {
            String template = StreamUtils.copyToString(
                    new ClassPathResource("playground/playground.html").getInputStream(),
                    StandardCharsets.UTF_8);
            this.html = template.replace("__GRAPHQL_ENDPOINT__", graphqlEndpoint);
        } catch (IOException e) {
            throw new UncheckedIOException("playground.html is missing from the classpath", e);
        }
    }

    @GetMapping(value = "${graphql.playground.path:/playground}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String playground() {
        return html;
    }
}
