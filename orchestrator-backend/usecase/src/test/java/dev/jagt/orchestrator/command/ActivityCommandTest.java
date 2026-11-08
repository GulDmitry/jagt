package dev.jagt.orchestrator.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityCommandTest {

  private final StateService state = mock(StateService.class);
  private final FinishedTasks finished = mock(FinishedTasks.class);
  private final ActivityCommand command = new ActivityCommand(new ActivityReport(state, finished));

  @Test
  void passesWhatWasTypedAfterTheVerbOnAsTheFilter() {
    when(finished.all()).thenReturn(List.of());
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-42",
                TaskState.builder("alpha", "/wt", TaskStatus.DEPLOYED)
                    .history(
                        List.of(
                            new StatusChange(TaskStatus.IN_PROGRESS, 1_000L, null),
                            new StatusChange(TaskStatus.DEPLOYED, 2_000L, null)))
                    .build()));

    String said = command.run("deployed");

    assertThat(said).contains("→ deployed").doesNotContain("→ agent working");
  }

  @Test
  void declaresTheKindsAndTheProjectItTakes() {
    assertThat(command.usage())
        .containsExactly("activity [created|shipped|deployed|stopped|done] [<project>]");
  }
}
