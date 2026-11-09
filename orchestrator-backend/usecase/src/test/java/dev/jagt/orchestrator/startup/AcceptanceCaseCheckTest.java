package dev.jagt.orchestrator.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceCaseCheckTest {

  @Test
  void readsNothingWhereAcceptanceIsOff(@TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: false\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(root.resolve("roles/acceptance/cases.md"), "## broken\n\nnot a case\n");

    assertThat(check(root).problems()).isEmpty();
  }

  @Test
  void acceptsTheShippedCasesAgainstTheShippedBriefWhereAcceptanceIsOn(@TempDir Path root)
      throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/cases.md.dist"),
        root.resolve("roles/acceptance/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/brief.md.dist"),
        root.resolve("roles/acceptance/brief.md.dist"));

    assertThat(check(root).problems()).isEmpty();
  }

  @Test
  void namesACaseWhoseStepNoBriefSectionHolds(@TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/cases.md"),
        "## accept\n\nstep: accept\nat: DEPLOYED\nby: jagt\n"
            + "replies: DEPLOYED; plain\nChecks.\n");
    Files.writeString(root.resolve("roles/acceptance/brief.md"), "## other\n\ntext\n");

    assertThat(check(root).problems()).hasSize(2);
  }

  private static AcceptanceCaseCheck check(Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withConfigFile(root.resolve("jagt.yml").toString())
            .withStateFile(root.resolve("state.json").toString());
    OrchestratorPaths paths = new OrchestratorPaths(properties);
    ConfigService config = new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class));
    return new AcceptanceCaseCheck(new RoleFiles(paths, "acceptance"), config, paths);
  }
}
