package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBootstrapPromptTest {

    @Test
    void theShippedBootstrapPromptLeavesCommittingToTheBriefsOwnRule(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("roles/session"));
        Files.copy(Path.of("../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

        String bootstrap = new SessionBrief(new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()))).line(Step.START);

        assertThat(bootstrap).isNotBlank().doesNotContainIgnoringCase("commit");
    }
}
