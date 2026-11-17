package dev.jagt.orchestrator.capability.done;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.service.task.ArchivedTaskFolder;
import dev.jagt.orchestrator.service.task.FinishedTasks;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DoneCapabilityTest {

  private final TaskRetirement retirement = mock(TaskRetirement.class);
  private final FinishedTasks finished = mock(FinishedTasks.class);
  private final FinishedArtifacts artifacts = mock(FinishedArtifacts.class);
  private final ArchivedTaskFolder folders = mock(ArchivedTaskFolder.class);

  @Test
  void recordsAndKeepsTheArtifactsBeforeRetirementDropsWhatBothAreBuiltFrom() {
    FinishedTask record =
        FinishedTask.of("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build(), 1L);
    Path snapshot = Path.of("/archive/261117-101530-ABC-1");
    when(finished.record("ABC-1")).thenReturn(Optional.of(record));
    when(artifacts.keep(record)).thenReturn(Optional.of(snapshot));
    when(retirement.retire("ABC-1")).thenReturn("Task ABC-1 removed");

    Outcome outcome = new DoneCapability(retirement, finished, artifacts, folders).run("ABC-1");

    assertThat(outcome.message()).isEqualTo("Task ABC-1 removed");
    InOrder order = inOrder(finished, artifacts, retirement, folders);
    order.verify(finished).record("ABC-1");
    order.verify(artifacts).keep(record);
    order.verify(retirement).retire("ABC-1");
    order.verify(folders).shelve("ABC-1", snapshot);
  }
}
