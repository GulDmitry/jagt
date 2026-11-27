package dev.jagt.orchestrator.job.session;

import static dev.jagt.orchestrator.job.TestWatchdogTaskBuilder.watchedTask;
import static dev.jagt.orchestrator.job.session.TestWatchdogServiceBuilder.watchdogService;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionProbe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.KeyValuePair;

class WatchdogServiceTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  private final SessionProbe probe = mock(SessionProbe.class);
  private final long now = new FakeClock().millis();

  @TempDir Path root;

  private StateService state;
  private WatchdogService watchdog;

  @BeforeEach
  void givenWatchdogOverAnEmptyState() {
    state = stateService().withRoot(root).build();
    watchdog = watchdogService().withRoot(root).build(state, probe, new FakeClock());
  }

  @Test
  void watchesOnlyTheStatusesInWhichAnAgentIsSupposedToBeWorking() {
    assertThat(Arrays.stream(TaskStatus.values()).filter(WatchdogService::watches).toList())
        .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.SHIPPING);
  }

  @Test
  void readsTheSessionOfTaskWhoseStatusIdlesByDesign() {
    state.putTask(
        "ABC-1", watchedTask().withStatus(TaskStatus.REVIEW_PENDING).withLastActive(0).build());

    watchdog.run();

    verify(probe).read(anyString(), any(), anyLong(), anyLong());
  }

  @Test
  void readsNothingOfClosedTask() {
    state.putTask("ABC-1", watchedTask().withStatus(TaskStatus.DONE).withLastActive(0).build());

    watchdog.run();

    verify(probe, never()).read(anyString(), any(), anyLong(), anyLong());
  }

  @Test
  void missingWindowBeforeFreshSessionsFirstReadIsNotAnEnding() {
    state.putTask("ABC-1", watchedTask().withLastActive(now).build());
    state.updateTask("ABC-1", reducer::sessionStartedOver);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.MISSING, 0, 0, now, 300_000, null));

    watchdog.run();

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().work().session()))
        .isNotEqualTo(SessionPhase.GONE);
  }

  @ParameterizedTest
  @EnumSource(
      value = Session.Window.State.class,
      names = {"DEAD_SHELL", "MISSING"})
  void endsTheSessionOfTaskInProgressWhoseWindowHoldsNoAgentProcess(Session.Window.State window) {
    state.putTask("ABC-1", watchedTask().withLastActive(now).build());
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(window, 0, 0, now, 300_000, "Error: bad config"));

    watchdog.run();

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().work().session()))
        .isEqualTo(SessionPhase.GONE);
    assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
        .isEqualTo("the session ended: Error: bad config");
  }

  @Test
  void writesNothingOnTheTicksOfTaskThatKeepsMoving() {
    state.putTask("ABC-1", watchedTask().withLastActive(0).build());
    AtomicInteger writes = new AtomicInteger();
    StateService counted = stateService().withRoot(root).build(event -> writes.incrementAndGet());
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));
    WatchdogService counting =
        watchdogService().withRoot(root).build(counted, probe, new FakeClock());

    counting.run();
    counting.run();

    assertThat(writes).hasValue(0);
  }

  @Test
  void endsSessionThatPrintedNothingWithNoCauseRatherThanAnEmptyOne() {
    state.putTask("ABC-1", watchedTask().withStatus(TaskStatus.NEW).withLastActive(0).build());

    watchdog.ended("ABC-1", " ");

    assertThat(
            Objects.requireNonNull(state.task("ABC-1").orElseThrow().work().session().halt())
                .cause())
        .isNull();
  }

  @Test
  void marksTheSessionStoppedAtOnceWhenItEndedRatherThanAtTheNextTick() {
    state.putTask("ABC-1", watchedTask().withLastActive(0).build());

    watchdog.ended("ABC-1", "Error: usage limit");

    assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
        .isEqualTo("the session ended: Error: usage limit");
  }

  @Test
  void alertsNobodyAndKeepsItsVerdictWhereTheSessionsWindowsCannotBeListed() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(1_000)
            .session(Session.defaults().withQuietSince(1_000))
            .build());
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenThrow(
            new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));

    watchdog.run();

    assertThat(SessionPhase.silentSince(state.task("ABC-1").orElseThrow())).isEqualTo(1_000);
  }

  @Test
  void endsTheSessionOfKilledAgentNamingWhatItPrintedLast() {
    state.putTask("ABC-42", watchedTask().withLastActive(now).build());
    AgentSessions sessions = mock(AgentSessions.class);
    when(sessions.windowState("ABC-42")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
    when(sessions.exitTail("ABC-42")).thenReturn(Optional.of("zsh: killed agent"));
    SessionProbe real =
        new SessionProbe(mock(ConfigService.class), sessions, mock(AgentRuntime.class));

    watchdogService().withRoot(root).build(state, real, new FakeClock()).run();

    TaskState task = state.task("ABC-42").orElseThrow();
    assertThat(SessionPhase.of(task.work().session())).isEqualTo(SessionPhase.GONE);
    assertThat(SessionPhase.silentBecause(task)).isEqualTo("the session ended: zsh: killed agent");
  }

  @Nested
  class WhereTheSessionIsRestartedWhileItsWindowIsRead {

    @BeforeEach
    void givenWindowReadAsNotGoneThatRestartsTheSession() {
      state.putTask("ABC-1", watchedTask().withLastActive(now).build());
      when(probe.read(anyString(), any(), anyLong(), anyLong()))
          .thenAnswer(read -> {
            state.updateTask("ABC-1", reducer::sessionStartedOver);
            return new SessionSign.Read(Session.Window.State.DEAD_SHELL, 0, 0, now, 300_000, null);
          });
    }

    @Test
    void leavesSessionRestartedWhileItsWindowWasReadAsNotGone() {
      watchdog.run();

      assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().work().session()))
          .isNotEqualTo(SessionPhase.GONE);
    }

    @Test
    void leavesSessionRestartedTwiceWhileItsWindowWasReadAsNotGone() {
      state.updateTask("ABC-1", reducer::sessionStartedOver);

      watchdog.run();

      assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().work().session()))
          .isNotEqualTo(SessionPhase.GONE);
    }
  }

  @Nested
  class WhereTheWindowReadsAsUnread {

    @BeforeEach
    void givenWindowReadAsUnread() {
      when(probe.read(anyString(), any(), anyLong(), anyLong()))
          .thenReturn(
              new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    }

    @ParameterizedTest
    @EnumSource(
        value = TaskStatus.class,
        names = {"NEW", "IN_PROGRESS", "SHIPPING"})
    void recordsTheSilenceWhenAnAgentStoppedInStatusItShouldBeWorkingIn(TaskStatus status) {
      state.putTask("ABC-1", watchedTask().withStatus(status).build());
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));

      watchdog.run();

      assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().has(ToldOnce.Kind.SILENCE))
          .isTrue();
    }

    @Test
    void endsFreshSessionWhoseCliExitedBeforeItsFirstRead() {
      state.putTask("ABC-1", watchedTask().withStatus(TaskStatus.NEW).build());
      state.updateTask("ABC-1", reducer::sessionStartedOver);

      watchdog.ended("ABC-1", "Error: bad config");

      assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
          .isEqualTo("the session ended: Error: bad config");
    }

    @Test
    void recordsNoSilenceOfAnEndedSessionWhoseStatusIdlesByDesign() {
      state.putTask("ABC-1", watchedTask().withStatus(TaskStatus.REVIEW_PENDING).build());
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));

      watchdog.run();

      assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().isEmpty()).isTrue();
    }

    @Test
    void recordsNoSilenceOfSessionThatToldJagtItWaitsOnDependency() {
      state.putTask(
          "ABC-1", watchedTask().withMessage("db down").withReport(AgentReport.WAIT).build());
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));

      watchdog.run();

      assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().isEmpty()).isTrue();
    }

    @Test
    void recordsNoSilenceOfAnAgentThatHadAlreadyAsked() {
      state.putTask(
          "ABC-1",
          watchedTask().withMessage("which database").withReport(AgentReport.QUESTION).build());
      state.updateTask(
          "ABC-1", task -> reducer.signed(task, new SessionSign.TurnEnded(null), 1_000));

      watchdog.run();

      assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().isEmpty()).isTrue();
    }

    @Test
    void recordsNoSilenceOfAnAgentWhosePromptTheScreenRaisedWithNoHookAfter() {
      state.putTask("ABC-1", watchedTask().withReport(AgentReport.PERMISSION).build());

      watchdog.run();

      assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().isEmpty()).isTrue();
    }

    @Test
    void recordsSilenceOfDeadSessionEvenWhereTheQuestionItAskedIsStillUnanswered() {
      state.putTask(
          "ABC-1",
          watchedTask().withMessage("which database").withReport(AgentReport.QUESTION).build());
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));

      watchdog.run();

      assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().has(ToldOnce.Kind.SILENCE))
          .isTrue();
    }

    @Test
    void logsWhatEndedTheSessionAsTheCauseNeverNull() {
      state.putTask("ABC-1", watchedTask().withStatus(TaskStatus.NEW).build());
      state.updateTask(
          "ABC-1",
          task ->
              reducer.signed(
                  task, new SessionSign.Ended("Error: Invalid MCP configuration"), 5_000));
      try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
        watchdog.check("ABC-1");

        assertThat(log.events())
            .filteredOn(event -> "task stopped".equals(event.getMessage()))
            .singleElement()
            .extracting(ILoggingEvent::getKeyValuePairs)
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .contains(new KeyValuePair("cause", "Error: Invalid MCP configuration"));
      }
    }

    @Test
    void logsSilentAsTheCauseOfSessionThatWentQuietWithoutWord() {
      state.putTask("ABC-1", watchedTask().withStatus(TaskStatus.NEW).build());
      try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
        watchdog.check("ABC-1");

        assertThat(log.events())
            .filteredOn(event -> "task stopped".equals(event.getMessage()))
            .singleElement()
            .extracting(ILoggingEvent::getKeyValuePairs)
            .asInstanceOf(InstanceOfAssertFactories.LIST)
            .contains(new KeyValuePair("cause", "silent"));
      }
    }

    @Nested
    class OfAnInProgressTask {

      @BeforeEach
      void givenInProgressTask() {
        state.putTask("ABC-1", watchedTask().build());
      }

      @Test
      void recordsTheQuietTheReadFoundOnTheSessionSoBothSurfacesShowIt() {
        watchdog.run();

        assertThat(SessionPhase.silentSince(state.task("ABC-1").orElseThrow())).isEqualTo(1_000);
      }

      @Test
      void writesNothingOnTheTicksThatFindTheSameVerdictAsTheOneBefore() {
        AtomicInteger writes = new AtomicInteger();
        StateService counted =
            stateService().withRoot(root).build(event -> writes.incrementAndGet());
        WatchdogService counting =
            watchdogService().withRoot(root).build(counted, probe, new FakeClock());

        counting.run();
        counting.run();
        counting.run();

        assertThat(writes).hasValue(2);
      }

      @Test
      void recordsSilenceWhereNothingWasReportedAtAll() {
        watchdog.run();

        assertThat(
                state.task("ABC-1").orElseThrow().round().toldOrNone().has(ToldOnce.Kind.SILENCE))
            .isTrue();
      }

      @Test
      void namesTheRunningProcessInTheQuietOfSessionThatMovedNothingWhileItsWindowRuns() {
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
            .thenReturn(
                new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 1_000, 300_000, null));

        watchdog.run();

        assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
            .isEqualTo("no sign of life, process running");
      }

      @Test
      void forgetsTheSilenceAlertOnceTheSilenceEnded() {
        WatchdogService atFiveHundred =
            watchdogService()
                .withRoot(root)
                .build(state, probe, Clock.fixed(Instant.ofEpochMilli(5_500), ZoneOffset.UTC));
        state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 2_000));
        atFiveHundred.run();
        state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Called(), 3_000));

        atFiveHundred.run();

        assertThat(state.task("ABC-1").orElseThrow().round().toldOrNone().isEmpty()).isTrue();
      }

      @Test
      void logsAgainWhenTheSilenceEndedAndNewOneBegan() {
        WatchdogService atFiveHundred =
            watchdogService()
                .withRoot(root)
                .build(state, probe, Clock.fixed(Instant.ofEpochMilli(5_500), ZoneOffset.UTC));
        try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
          state.updateTask(
              "ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 2_000));
          atFiveHundred.run();
          state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Called(), 3_000));
          atFiveHundred.run();
          state.updateTask(
              "ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 4_000));

          atFiveHundred.run();

          assertThat(log.events())
              .filteredOn(event -> "task stopped".equals(event.getMessage()))
              .hasSize(2);
        }
      }

      @Test
      void endsGoneVerdictOnTheCardAndLogsItOnceWhenTheWorktreeCallsMcp() {
        WatchdogService atTenThousand =
            watchdogService()
                .withRoot(root)
                .build(state, probe, Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC));
        try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
          state.updateTask(
              "ABC-1", task -> reducer.signed(task, new SessionSign.Ended("boom"), 5_000));

          atTenThousand.run();
          boolean silentAfterEnd = SessionPhase.silent(state.task("ABC-1").orElseThrow());
          state.updateTask(
              "ABC-1",
              task -> reducer.signed(task.touched(9_000), new SessionSign.Called(), 9_000));
          atTenThousand.run();

          assertThat(silentAfterEnd).isTrue();
          assertThat(SessionPhase.silent(state.task("ABC-1").orElseThrow())).isFalse();
          assertThat(log.events())
              .filteredOn(event -> "task stopped".equals(event.getMessage()))
              .hasSize(1);
        }
      }

      @Nested
      class AndItsSessionEnded {

        private final FakeLogCapture log = new FakeLogCapture(WatchdogService.class);

        @BeforeEach
        void givenEndedSession() {
          state.updateTask(
              "ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
        }

        @AfterEach
        void detachTheLog() {
          log.close();
        }

        @Test
        void logsOnceWhileTheSameSilenceKeepsStandingWithinTheStaleWindow() {
          watchdog.run();
          watchdog.run();

          assertThat(log.events())
              .filteredOn(event -> "task stopped".equals(event.getMessage()))
              .hasSize(1);
        }

        @Test
        void logsOnceForOneSilenceAcrossRestart() {
          watchdog.run();
          WatchdogService restarted =
              watchdogService().withRoot(root).build(state, probe, new FakeClock());

          restarted.run();

          assertThat(log.events())
              .filteredOn(event -> "task stopped".equals(event.getMessage()))
              .hasSize(1);
        }

        @Test
        void logsOnceForOneSilenceAcrossManyStaleWindows() {
          Clock clock = mock(Clock.class);
          when(clock.millis()).thenReturn(10_000_000L, 10_300_000L, 10_600_000L, 12_000_000L);
          WatchdogService staling = watchdogService().withRoot(root).build(state, probe, clock);

          staling.run();
          staling.run();
          staling.run();
          staling.run();

          assertThat(log.events())
              .filteredOn(event -> "task stopped".equals(event.getMessage()))
              .hasSize(1);
        }
      }
    }
  }
}
