package dev.jagt.orchestrator.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The agent bootstrap prompt is not here: it is one line in application.yml. */
@Component
public class PromptTemplates {

    private final String subAgentContext;

    public PromptTemplates() {
        this.subAgentContext = load("prompts/sub-agent-context.md");
    }

    public String subAgentContext() {
        return subAgentContext;
    }

    private static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load prompt template " + path, e);
        }
    }
}
