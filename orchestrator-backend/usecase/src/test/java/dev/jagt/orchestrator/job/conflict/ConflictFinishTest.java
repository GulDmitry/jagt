package dev.jagt.orchestrator.job.conflict;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictFinishTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final CommandService commands = mock(CommandService.class);
  private final TaskStops stops = mock(TaskStops.class);
  @TempDir Path root;
  private StateService state;

  @BeforeEach
  void stateOnDisk() {
    state = stateService().withRoot(root).build();
  }

  @Test
  void pressesTheDeployTheHumanStarted() {
    state.putTask(
        "ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());
    when(stops.moveDue("ABC-42")).thenReturn(true);

    new ConflictFinish(commands, stops, state, new FakeClock())
        .press("ABC-42", Path.of("/src/ABC-42-deploy"));

    verify(commands).execute("ABC-42", TaskAction.DEPLOY);
  }

  @Test
  void pressesNothingWhereTheTaskMadeTooManyUnattendedMovesInTheHour() {
    state.putTask(
        "ABC-42", TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build());
    when(stops.moveDue("ABC-42")).thenReturn(false);

    new ConflictFinish(commands, stops, state, new FakeClock())
        .press("ABC-42", Path.of("/src/ABC-42-deploy"));

    verify(commands, never()).execute("ABC-42", TaskAction.DEPLOY);
  }

  @Nested
  class WhileJagtHoldsResolvedConflictStopped {

    private final FakeLogCapture log = new FakeLogCapture(ConflictFinish.class);

    @BeforeEach
    void theConflictIsResolvedWhileTheTaskIsStopped() {
      TaskState stopped =
          TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT)
              .stop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "same failure"))
              .build();
      TaskState opened =
          reducer.deployStepped(
              stopped, new DeployStep.Opened(List.of(Deployment.Target.of("proj", "dev"))), 1);
      state.putTask(
          "ABC-42",
          reducer.deployStepped(
              opened, new DeployStep.Conflicted("proj", Deployment.Cause.MERGE), 1));
    }

    @AfterEach
    void releasesTheLog() {
      log.close();
    }

    @Test
    void pressesNothingAndSaysOnceWhileJagtHoldsResolvedConflictStopped() {
      ConflictFinish finish = new ConflictFinish(commands, stops, state, new FakeClock());

      finish.press("ABC-42", Path.of("/src/ABC-42-deploy"));
      finish.press("ABC-42", Path.of("/src/ABC-42-deploy"));
      finish.press("ABC-42", Path.of("/src/ABC-42-deploy"));

      verify(commands, never()).execute("ABC-42", TaskAction.DEPLOY);
      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("conflict resolved held by stop");
    }

    @Test
    void saysOnceAcrossRestartWhileJagtHoldsResolvedConflictStopped() {
      new ConflictFinish(commands, stops, state, new FakeClock())
          .press("ABC-42", Path.of("/src/ABC-42-deploy"));
      new ConflictFinish(commands, stops, state, new FakeClock())
          .press("ABC-42", Path.of("/src/ABC-42-deploy"));

      assertThat(log.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("conflict resolved held by stop");
    }
  }

  @Test
  void finishesTheDeployOnTheTickAfterTheHumanLifts() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(TaskStop.Trigger.UNCHANGED_FAILURE, 5, "same failure"))
            .build());
    when(stops.moveDue("ABC-42")).thenReturn(true);
    ConflictFinish finish = new ConflictFinish(commands, stops, state, new FakeClock());
    finish.press("ABC-42", Path.of("/src/ABC-42-deploy"));

    state.updateTask("ABC-42", task -> task.lifted(9));
    finish.press("ABC-42", Path.of("/src/ABC-42-deploy"));

    verify(commands, times(1)).execute("ABC-42", TaskAction.DEPLOY);
  }

  @Test
  void pressesTheRevertTheHumanStartedWhereTheConflictIsRevertsAndNotTheDeploy() {
    TaskState conflicted =
        TaskState.builder("proj", "/src/ABC-42", TaskStatus.DEPLOY_CONFLICT).build();
    TaskState opened =
        reducer.deployStepped(
            conflicted, new DeployStep.Opened(List.of(Deployment.Target.of("proj", "dev"))), 1);
    state.putTask(
        "ABC-42",
        reducer.deployStepped(
            opened, new DeployStep.Conflicted("proj", Deployment.Cause.REVERT), 1));
    when(stops.moveDue("ABC-42")).thenReturn(true);

    new ConflictFinish(commands, stops, state, new FakeClock())
        .press("ABC-42", Path.of("/src/ABC-42-revert"));

    verify(commands).execute("ABC-42", TaskAction.REVERT);
    verify(commands, never()).execute("ABC-42", TaskAction.DEPLOY);
  }
}
