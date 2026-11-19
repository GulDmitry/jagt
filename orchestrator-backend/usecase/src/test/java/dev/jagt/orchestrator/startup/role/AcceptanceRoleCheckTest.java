package dev.jagt.orchestrator.startup.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceRoleCheckTest {

  private final AgentRuntime agents = mock(AgentRuntime.class);
  private final ActStops actStops = mock(ActStops.class);
  private final Checkers checkers = new Checkers(new TaskChart(Composition.builtIn()));

  @Test
  void disabledAcceptanceWithNoAcceptanceRoleInstalledNeverFailsTheStartAndTheNoteSaysWhy(
      @TempDir Path root) throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    InstalledRoles roles = new InstalledRoles(paths);

    AcceptanceRoleCheck check =
        new AcceptanceRoleCheck(
            roles,
            new ConfigService(
                new ConfigSource(paths, new FakeConfigInputs()), agents, actStops, checkers),
            paths);

    assertThat(check.problems()).isEmpty();
    assertThat(check.notes())
        .singleElement()
        .satisfies(note -> {
          assertThat(note.key()).isEqualTo("roles/acceptance");
          assertThat(note.text()).contains("no roles/acceptance/role.yml");
        });
  }

  @Test
  void anEnabledAcceptanceWithNoAcceptanceRoleInstalledRefusesTheStartNamingTheReason(
      @TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    InstalledRoles roles = new InstalledRoles(paths);

    assertThat(
            new AcceptanceRoleCheck(
                    roles,
                    new ConfigService(
                        new ConfigSource(paths, new FakeConfigInputs()),
                        agents,
                        actStops,
                        checkers),
                    paths)
                .problems())
        .singleElement()
        .satisfies(problem -> {
          assertThat(problem.key()).isEqualTo("roles/acceptance");
          assertThat(problem.text()).contains("no roles/acceptance/role.yml");
        });
  }
}
