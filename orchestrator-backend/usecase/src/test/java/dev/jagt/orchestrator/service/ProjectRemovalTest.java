package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectRemovalTest {

  private final ProjectSetup setup = mock(ProjectSetup.class);
  private final StateService state = mock(StateService.class);
  private final ProjectRemoval removal = new ProjectRemoval(setup, state);

  @Test
  void refusesAProjectAnOpenTaskWorksInAndNamesTheTask() {
    when(state.tasks())
        .thenReturn(
            Map.of(
                "ABC-42",
                TaskState.builder("billing", "/wt/abc-42", TaskStatus.IN_PROGRESS).build()));

    assertThatThrownBy(() -> removal.remove("billing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ABC-42");
    verify(setup, never()).remove("billing");
  }

  @Test
  void refusesAProjectTheSecondRepositoryOfAnOpenTaskUses() {
    TaskState spanning =
        TaskState.builder("web", "/wt/abc-42-web", TaskStatus.IN_PROGRESS)
            .repos(
                List.of(
                    TaskRepo.of("web", "/wt/abc-42-web"),
                    TaskRepo.of("billing", "/wt/abc-42-billing")))
            .build();
    when(state.tasks()).thenReturn(Map.of("ABC-42", spanning));

    assertThatThrownBy(() -> removal.remove("billing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ABC-42");
  }

  @Test
  void removesAProjectOnlyFinishedTasksWorkedIn() {
    when(state.tasks())
        .thenReturn(
            Map.of("ABC-42", TaskState.builder("billing", "/wt/abc-42", TaskStatus.DONE).build()));
    when(setup.remove("billing")).thenReturn("Removed billing from jagt.yml");

    assertThat(removal.remove("billing")).isEqualTo("Removed billing from jagt.yml");
  }
}
