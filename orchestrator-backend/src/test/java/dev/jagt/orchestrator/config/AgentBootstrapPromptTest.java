package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.host.SystemHost;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.task.turn.Step;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentBootstrapPromptTest {

  @Test
  void theShippedBootstrapPromptLeavesCommittingToTheBriefsOwnRule(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String bootstrap =
        new SessionBrief(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()), new SystemHost()))
            .line(Step.START);

    assertThat(bootstrap).isNotBlank().doesNotContainIgnoringCase("commit");
  }

  @Test
  void theShippedContextReportsRunningPipelineAsCiPollingNeverAsWait(@TempDir Path root)
      throws Exception {
    Files.createDirectories(root.resolve("roles/session"));
    Files.copy(
        Path.of("../roles/session/brief.md.dist"), root.resolve("roles/session/brief.md.dist"));

    String context =
        new SessionBrief(
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()), new SystemHost()))
            .line(Step.CONTEXT);

    assertThat(context)
        .contains(
            "CI_POLLING with the request link when told or while its pipeline runs, never"
                + " outcome=wait.");
  }
}
