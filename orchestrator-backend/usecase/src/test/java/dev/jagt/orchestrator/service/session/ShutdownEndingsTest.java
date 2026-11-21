package dev.jagt.orchestrator.service.session;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionLedger;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;

class ShutdownEndingsTest {

  @TempDir Path root;

  private StateService state;
  private SessionLedger ledger;
  private AgentSessions sessions;

  @BeforeEach
  void putTasks() {
    state = stateService().withRoot(root).build();
    ledger = new SessionLedger(state, new FakeClock());
    sessions = mock(AgentSessions.class);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    state.putTask("ABC-3", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
  }

  @Test
  void recordsEveryWorkingSessionAsGoneAndTheNextStartReadsThemSo() {
    ledger.sign("ABC-1", new SessionSign.WorkStarted());
    ledger.sign("ABC-2", new SessionSign.WorkStarted());

    new ShutdownEndings(state, ledger, sessions).stop();

    StateService restarted = stateService().withRoot(root).build();
    assertThat(SessionPhase.of(restarted.task("ABC-1").orElseThrow().session()))
        .isEqualTo(SessionPhase.GONE);
    assertThat(SessionPhase.of(restarted.task("ABC-2").orElseThrow().session()))
        .isEqualTo(SessionPhase.GONE);
    verify(sessions).killWindows("ABC-1");
    verify(sessions).killWindows("ABC-2");
  }

  @Test
  void leavesTaskThatNeverHadSessionAlone() {
    ledger.sign("ABC-1", new SessionSign.WorkStarted());

    new ShutdownEndings(state, ledger, sessions).stop();

    assertThat(SessionPhase.of(state.task("ABC-3").orElseThrow().session()))
        .isEqualTo(SessionPhase.NONE);
    verify(sessions, never()).killWindows("ABC-3");
  }

  @Test
  void stillEndsTheOtherSessionsWhenOneWindowCannotBeClosed() {
    ledger.sign("ABC-1", new SessionSign.WorkStarted());
    ledger.sign("ABC-2", new SessionSign.WorkStarted());
    doThrow(new IllegalStateException("tmux unreadable")).when(sessions).killWindows("ABC-1");

    new ShutdownEndings(state, ledger, sessions).stop();

    assertThat(SessionPhase.of(state.task("ABC-2").orElseThrow().session()))
        .isEqualTo(SessionPhase.GONE);
    verify(sessions).killWindows("ABC-2");
  }

  @Test
  void endsTheSessionsWhenTheContextClosesWithOnStopEnd() {
    ledger.sign("ABC-1", new SessionSign.WorkStarted());
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withPropertyValues("orchestrator.on-stop=end")
            .withBean(ShutdownEndings.class, () -> new ShutdownEndings(state, ledger, sessions));

    runner.run(context -> {});

    verify(sessions).killWindows("ABC-1");
  }

  @Test
  void keepsTheSessionsWhenTheContextClosesWithOnStopUnset() {
    ledger.sign("ABC-1", new SessionSign.WorkStarted());
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withBean(ShutdownEndings.class, () -> new ShutdownEndings(state, ledger, sessions));

    runner.run(context -> {});

    verify(sessions, never()).killWindows("ABC-1");
    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isEqualTo(SessionPhase.WORKING);
  }

  @Test
  void endsOneSessionStartedWhileTheWebServerDrains() {
    SmartLifecycle webServer =
        new SmartLifecycle() {
          private boolean running;

          @Override
          public void start() {
            running = true;
          }

          @Override
          public void stop() {
            running = false;
            ledger.sign("ABC-1", new SessionSign.WorkStarted());
          }

          @Override
          public boolean isRunning() {
            return running;
          }

          @Override
          public int getPhase() {
            return SmartLifecycle.DEFAULT_PHASE - 2048;
          }
        };
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withPropertyValues("orchestrator.on-stop=end")
            .withBean(ShutdownEndings.class, () -> new ShutdownEndings(state, ledger, sessions))
            .withBean(SmartLifecycle.class, () -> webServer);

    runner.run(context -> {});

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isEqualTo(SessionPhase.GONE);
    verify(sessions).killWindows("ABC-1");
  }
}
