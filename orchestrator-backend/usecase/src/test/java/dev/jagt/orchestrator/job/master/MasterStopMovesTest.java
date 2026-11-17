package dev.jagt.orchestrator.job.master;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.verdict.MasterVerdicts;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterStopMovesTest {

  private final MasterPress press = mock(MasterPress.class);
  private final MasterVerdicts verdicts = mock(MasterVerdicts.class);

  @TempDir Path root;

  private StateService state;
  private MasterStopMoves moves;

  @BeforeEach
  void movesOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    moves = new MasterStopMoves(press, verdicts, state);
  }

  @Test
  void liftsTheStopWhereThePressIsNotRefused() {
    when(press.lift("ABC-1")).thenReturn(true);

    assertThat(moves.lift("ABC-1")).isTrue();
  }

  @Test
  void tellsTheSessionOfLiftedTaskWhatToDoDifferently() {
    TaskState lifted = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    state.putTask("ABC-1", lifted);
    when(verdicts.stopReturned(anyString(), any(), anyString())).thenReturn(true);

    boolean told = moves.returned("ABC-1", "split the change");

    assertThat(told).isTrue();
    verify(verdicts).stopReturned("ABC-1", lifted, "split the change");
  }

  @Test
  void tellsNothingToTaskWhoseStopStillStands() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"))
            .build());

    boolean told = moves.returned("ABC-1", "split the change");

    assertThat(told).isFalse();
    verify(verdicts, never()).stopReturned(anyString(), any(), anyString());
  }
}
