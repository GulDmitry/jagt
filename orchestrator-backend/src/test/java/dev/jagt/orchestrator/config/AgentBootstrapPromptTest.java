package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBootstrapPromptTest {

    @Test
    void theShippedBootstrapPromptLeavesEveryCommitToAHumansShip() throws Exception {
        String applicationYml;
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            applicationYml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(applicationYml)
                .contains("do NOT commit")
                .doesNotContain("commit to your task branch WITHOUT asking");
    }
}
