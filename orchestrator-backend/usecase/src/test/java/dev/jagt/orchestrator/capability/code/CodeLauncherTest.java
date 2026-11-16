package dev.jagt.orchestrator.capability.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CodeLauncherTest {

  private final StateService stateService = mock(StateService.class);
  private final CodeProject project = mock(CodeProject.class);
  private final CodeDiff diff = mock(CodeDiff.class);

  @Test
  void opensTaskAsProjectsThatRunWhenAskedForTheProject() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
    when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(task));
    when(project.open("ABC-1", task)).thenReturn("Opened ABC-1");

    String opened =
        new CodeLauncher(stateService, project, diff).open("a1", CodeLauncher.Mode.PROJECT);

    assertThat(opened).isEqualTo("Opened ABC-1");
    verify(diff, never()).open("ABC-1", task);
  }

  @Test
  void opensTaskAsStaticDiffWhenAskedForTheDiff() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
    when(stateService.canonicalTaskId("a1")).thenReturn("ABC-1");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(task));
    when(diff.open("ABC-1", task)).thenReturn("Opened STATIC diff of ABC-1");

    String opened =
        new CodeLauncher(stateService, project, diff).open("a1", CodeLauncher.Mode.DIFF);

    assertThat(opened).isEqualTo("Opened STATIC diff of ABC-1");
    verify(project, never()).open("ABC-1", task);
  }

  @Test
  void refusesTaskItDoesNotKnow() {
    when(stateService.canonicalTaskId("nope")).thenReturn("nope");
    when(stateService.task("nope")).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
            new CodeLauncher(stateService, project, diff).open("nope", CodeLauncher.Mode.DIFF))
        .isInstanceOf(Refusal.class);
  }
}
