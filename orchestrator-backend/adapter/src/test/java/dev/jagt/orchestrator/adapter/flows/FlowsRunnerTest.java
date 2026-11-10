package dev.jagt.orchestrator.adapter.flows;

import static dev.jagt.orchestrator.adapter.flows.AcceptanceFolders.ENABLED;
import static dev.jagt.orchestrator.adapter.flows.AcceptanceFolders.folder;
import static dev.jagt.orchestrator.adapter.flows.AcceptanceFolders.roleSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import dev.jagt.orchestrator.port.Processes;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlowsRunnerTest {

  @Test
  void everyFlowIsUnprovableAndNothingRunsWhereTheFolderNamesNoDriver(@TempDir Path root)
      throws Exception {
    OrchestratorPaths paths = folder(root, ENABLED);
    Processes processes = mock(Processes.class);

    List<FlowResult> results =
        new FlowsRunner(roleSettings(paths), new PlaywrightRunner(processes))
            .run(root, List.of("AC-1", "AC-2"), "https://alpha.test");

    assertThat(results)
        .extracting(FlowResult::verdict)
        .containsExactly(Verdict.UNPROVABLE, Verdict.UNPROVABLE);
    verify(processes, never()).run(any(), any(), anyMap(), anyList());
  }
}
