package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.stop.ActStops;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceCaseCheckTest {

  @TempDir Path root;
  private AcceptanceCaseCheck check;

  @BeforeEach
  void checkOverThisTestsRoot() {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withConfigFile(root.resolve("jagt.yml").toString())
            .withStateFile(root.resolve("state.json").toString());
    OrchestratorPaths paths = new OrchestratorPaths(properties);
    ConfigService config =
        new ConfigService(
            paths,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())));
    check =
        new AcceptanceCaseCheck(
            new RoleFiles(paths, "acceptance"),
            config,
            paths,
            new Checkers(new TaskChart(Composition.builtIn())));
  }

  @Test
  void readsNothingWhereAcceptanceIsOff() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: false\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(root.resolve("roles/acceptance/cases.md"), "## broken\n\nnot a case\n");

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void acceptsTheShippedCasesAgainstTheShippedBriefWhereAcceptanceIsOn() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/cases.md.dist"),
        root.resolve("roles/acceptance/cases.md.dist"));
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/brief.md.dist"),
        root.resolve("roles/acceptance/brief.md.dist"));

    assertThat(check.problems()).isEmpty();
  }

  @Test
  void namesCaseWhoseStepNoBriefSectionHolds() throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.writeString(
        root.resolve("roles/acceptance/cases.md"),
        """
        ## accept

        step: accept
        at: DEPLOYED
        by: jagt
        replies: DEPLOYED; plain
        Checks.
        """);
    Files.writeString(root.resolve("roles/acceptance/brief.md"), "## other\n\ntext\n");

    assertThat(check.problems()).hasSize(2);
  }
}
