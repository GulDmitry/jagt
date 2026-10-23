package dev.jagt.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBootstrapPromptTest {

    @Test
    void theShippedBootstrapPromptLeavesCommittingToTheBriefsOwnRule(@TempDir Path root) throws Exception {
        Files.copy(Path.of("../session-brief.md.dist"), root.resolve("session-brief.md.dist"));

        String bootstrap = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))).line("start");

        assertThat(bootstrap).isNotBlank().doesNotContainIgnoringCase("commit");
    }
}
