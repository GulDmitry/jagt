package dev.jagt.orchestrator.adapter.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.RoleSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlowsRunnerTest {

  @Test
  void everyFlowIsUnprovableAndNothingRunsWhereTheFolderNamesNoDriver(@TempDir Path root)
      throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    Processes processes = mock(Processes.class);

    List<FlowResult> results =
        new FlowsRunner(
                new RoleSettings(
                    RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                    new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                    paths),
                new PlaywrightRunner(processes))
            .run(root, List.of("AC-1", "AC-2"), "https://alpha.test");

    assertThat(results)
        .extracting(FlowResult::verdict)
        .containsExactly(Verdict.UNPROVABLE, Verdict.UNPROVABLE);
    verify(processes, never()).run(any(), any(), anyMap(), anyList());
  }
}
