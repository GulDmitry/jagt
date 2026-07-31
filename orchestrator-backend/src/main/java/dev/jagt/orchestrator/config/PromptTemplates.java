package dev.jagt.orchestrator.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * All prompt texts live as resources under src/main/resources/prompts/ —
 * one place to edit wording, no prose inside Java code. (The Master prompt
 * is user-facing config and stays at the repo root as master_prompt.md;
 * the agent bootstrap prompt is one line and lives in application.yml.)
 */
@Component
public class PromptTemplates {

    private final String subAgentContext;

    public PromptTemplates() {
        this.subAgentContext = load("prompts/sub-agent-context.md");
    }

    /** %s placeholders — see the formatted() call in OrchestratorTools for the argument order. */
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
