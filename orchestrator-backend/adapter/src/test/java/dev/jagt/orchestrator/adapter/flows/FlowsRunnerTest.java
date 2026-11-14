package dev.jagt.orchestrator.adapter.flows;

import static dev.jagt.orchestrator.adapter.flows.TestAcceptanceRoleSettingsBuilder.acceptanceRoleSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.worktree.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.worktree.AcceptanceRunner.Verdict;
import dev.jagt.orchestrator.service.stop.ActStops;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlowsRunnerTest {

  @Test
  void everyFlowIsUnprovableAndNothingRunsWhereTheFolderNamesNoDriver(@TempDir Path root)
      throws Exception {
    Processes processes = mock(Processes.class);

    List<FlowResult> results =
        new FlowsRunner(
                acceptanceRoleSettings()
                    .withRoot(root)
                    .build(mock(AgentRuntime.class), mock(ActStops.class)),
                new PlaywrightRunner(processes))
            .run(root, List.of("AC-1", "AC-2"), "https://alpha.test");

    assertThat(results)
        .extracting(FlowResult::verdict)
        .containsExactly(Verdict.UNPROVABLE, Verdict.UNPROVABLE);
    verify(processes, never()).run(any(), any(), anyMap(), anyList());
  }
}
