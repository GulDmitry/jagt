package dev.jagt.orchestrator.job.master;

import static dev.jagt.orchestrator.job.TestTaskTurnsBuilder.taskTurns;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterPressTest {

  private final CommandService commands = mock(CommandService.class);
  private final HumanPaths humanPaths = mock(HumanPaths.class);
  private final TaskStops stops = mock(TaskStops.class);

  @TempDir Path root;

  private StateService state;
  private MasterPress press;

  @BeforeEach
  void masterPressOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    press =
        new MasterPress(commands, humanPaths, stops, taskTurns().build(state, Clock.systemUTC()));
  }

  @Test
  void pressesTheVerbWhereTheTaskTouchedNoPathKeptForTheHuman() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    when(stops.moveDue("ABC-1")).thenReturn(true);
    when(humanPaths.touched(task)).thenReturn(Optional.empty());

    boolean pressed = press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(pressed).isTrue();
    verify(commands).execute("ABC-1", TaskAction.DEPLOY);
  }

  @Test
  void leavesTheVerbToTheHumanWhereTheTaskTouchedPathKeptForThem() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

    boolean pressed = press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(pressed).isFalse();
    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void refusesPressWhereTheTaskMadeTooManyUnattendedMovesInTheHour() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    when(humanPaths.touched(task)).thenReturn(Optional.empty());
    when(stops.moveDue("ABC-1")).thenReturn(false);

    boolean pressed = press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(pressed).isFalse();
    verify(commands, never()).execute(anyString(), any());
  }

  @Test
  void pressesVerbNoPathCanKeepWithoutAskingWhatTheTaskTouched() {
    when(stops.moveDue("ABC-1")).thenReturn(true);

    press.execute("ABC-1", TaskAction.ADD_REPO);

    verify(commands).execute("ABC-1", TaskAction.ADD_REPO);
    verifyNoInteractions(humanPaths);
  }

  @Test
  void marksTheStepLeftToTheHumanWithThePathThatKeptItFromTheMaster() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    state.putTask("ABC-1", task);
    when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

    press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(state.task("ABC-1").orElseThrow().arbiterOrNone().left())
        .isEqualTo("deploy demo/src/auth/Login.java");
  }

  @Test
  void replacesTheStepLeftWithTheOneLeftNow() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEWED)
            .master(Master.defaults().withLeft("deploy demo/src/old/Old.java"))
            .build();
    state.putTask("ABC-1", task);
    when(humanPaths.touched(task)).thenReturn(Optional.of("demo/src/auth/Login.java"));

    press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(state.task("ABC-1").orElseThrow().arbiterOrNone().left())
        .isEqualTo("deploy demo/src/auth/Login.java");
  }

  @Test
  void forgetsTheStepLeftOnceTheVerbIsPressed() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEWED)
            .master(Master.defaults().withLeft("deploy demo/src/old/Old.java"))
            .build();
    state.putTask("ABC-1", task);
    when(stops.moveDue("ABC-1")).thenReturn(true);
    when(humanPaths.touched(task)).thenReturn(Optional.empty());

    press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(state.task("ABC-1").orElseThrow().arbiterOrNone().left()).isEmpty();
  }

  @Test
  void takesRefusedDeployAsAnOutcomeNotFault() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEWED).build();
    when(stops.moveDue("ABC-1")).thenReturn(true);
    when(humanPaths.touched(task)).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("Nothing to deploy"))
        .when(commands)
        .execute("ABC-1", TaskAction.DEPLOY);

    boolean pressed = press.press("ABC-1", task, TaskAction.DEPLOY);

    assertThat(pressed).isFalse();
  }

  @Test
  void letsFaultOtherThanRefusalEscape() {
    when(stops.moveDue("ABC-1")).thenReturn(true);
    doThrow(new UncheckedIOException(new IOException("disk gone")))
        .when(commands)
        .execute("ABC-1", TaskAction.DEPLOY);

    assertThatThrownBy(() -> press.execute("ABC-1", TaskAction.DEPLOY))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void liftsStopWithoutAskingWhetherMoveIsDueForTaskThatIsStopped() {
    boolean lifted = press.lift("ABC-1");

    assertThat(lifted).isTrue();
    verify(commands).execute("ABC-1", TaskAction.LIFT);
    verifyNoInteractions(stops);
  }

  @Test
  void reportsLiftTheDoorRefusedAsNotMade() {
    doThrow(new IllegalStateException("not stopped"))
        .when(commands)
        .execute("ABC-1", TaskAction.LIFT);

    boolean lifted = press.lift("ABC-1");

    assertThat(lifted).isFalse();
  }
}
