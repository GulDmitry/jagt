package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.SessionPhase;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.ToldOnce;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

class WatchdogServiceTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"NEW", "IN_PROGRESS", "SHIPPING"})
  void recordsTheSilenceWhenAnAgentStoppedInStatusItShouldBeWorkingIn(
      TaskStatus status, @TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", status).alias("a1").lastActiveTimestamp(1_000).build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.SILENCE))
        .isTrue();
  }

  @Test
  void leavesSessionRestartedWhileItsWindowWasReadAsNotGone(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    long now = Clock.systemUTC().millis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(now)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenAnswer(
            read -> {
              state.updateTask("ABC-1", reducer::sessionStartedOver);
              return new SessionSign.Read(
                  Session.Window.State.DEAD_SHELL, 0, 0, now, 300_000, null);
            });

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void leavesSessionRestartedTwiceWhileItsWindowWasReadAsNotGone(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    long now = Clock.systemUTC().millis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(now)
            .build());
    state.updateTask("ABC-1", reducer::sessionStartedOver);
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenAnswer(
            read -> {
              state.updateTask("ABC-1", reducer::sessionStartedOver);
              return new SessionSign.Read(
                  Session.Window.State.DEAD_SHELL, 0, 0, now, 300_000, null);
            });

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void missingWindowBeforeFreshSessionsFirstReadIsNotAnEnding(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    long now = Clock.systemUTC().millis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(now)
            .build());
    state.updateTask("ABC-1", reducer::sessionStartedOver);
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.MISSING, 0, 0, now, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isNotEqualTo(SessionPhase.GONE);
  }

  @Test
  void endsFreshSessionWhoseCliExitedBeforeItsFirstRead(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", reducer::sessionStartedOver);
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .ended("ABC-1", "Error: bad config");

    assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
        .isEqualTo("the session ended: Error: bad config");
  }

  @Test
  void watchesOnlyTheStatusesInWhichAnAgentIsSupposedToBeWorking() {
    assertThat(Arrays.stream(TaskStatus.values()).filter(WatchdogService::watches).toList())
        .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.SHIPPING);
  }

  @ParameterizedTest
  @EnumSource(TaskStatus.class)
  void watchesEveryStatusWhoseNextMoveIsTheAgentsOwnAndJagtIsNotHoldingItself(TaskStatus status) {
    assertThat(WatchdogService.watches(status))
        .isEqualTo(Move.ownerOf(status) == Owner.AGENT && !FlowRules.awaitingVerification(status));
  }

  @Test
  void readsTheSessionOfTaskWhoseStatusIdlesByDesign(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    SessionProbe probe = mock(SessionProbe.class);

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .run();

    verify(probe).read(anyString(), any(), anyLong(), anyLong());
  }

  @Test
  void readsNothingOfClosedTask(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());
    SessionProbe probe = mock(SessionProbe.class);

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .run();

    verify(probe, never()).read(anyString(), any(), anyLong(), anyLong());
  }

  @Test
  void recordsNoSilenceOfAnEndedSessionWhoseStatusIdlesByDesign(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().isEmpty()).isTrue();
  }

  @Test
  void recordsNoSilenceOfSessionThatToldJagtItWaitsOnDependency(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .message("db down")
            .report(AgentReport.WAIT)
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().isEmpty()).isTrue();
  }

  @Test
  void recordsNoSilenceOfAnAgentThatHadAlreadyAsked(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .message("which database")
            .report(AgentReport.QUESTION)
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.TurnEnded(null), 1_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().isEmpty()).isTrue();
  }

  @Test
  void recordsNoSilenceOfAnAgentWhosePromptTheScreenRaisedWithNoHookAfter(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .report(AgentReport.PERMISSION)
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().isEmpty()).isTrue();
  }

  @Test
  void recordsTheQuietTheReadFoundOnTheSessionSoBothSurfacesShowIt(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .run();

    assertThat(SessionPhase.silentSince(state.task("ABC-1").orElseThrow())).isEqualTo(1_000);
  }

  @Test
  void writesNothingOnTheTicksThatFindTheSameVerdictAsTheOneBefore(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService seeded =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(properties),
            reducer,
            event -> {});
    seeded.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    AtomicInteger writes = new AtomicInteger();
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(properties),
            reducer,
            event -> writes.incrementAndGet());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    WatchdogService watchdog =
        new WatchdogService(state, properties, probe, Clock.systemUTC());

    watchdog.run();
    watchdog.run();
    watchdog.run();

    assertThat(writes).hasValue(2);
  }

  @Test
  void writesNothingOnTheTicksOfTaskThatKeepsMoving(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService seeded =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(properties),
            reducer,
            event -> {});
    seeded.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    AtomicInteger writes = new AtomicInteger();
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(properties),
            reducer,
            event -> writes.incrementAndGet());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));
    WatchdogService watchdog =
        new WatchdogService(state, properties, probe, Clock.systemUTC());

    watchdog.run();
    watchdog.run();

    assertThat(writes).hasValue(0);
  }

  @Test
  void recordsSilenceOfDeadSessionEvenWhereTheQuestionItAskedIsStillUnanswered(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .message("which database")
            .report(AgentReport.QUESTION)
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.SILENCE))
        .isTrue();
  }

  @Test
  void recordsSilenceWhereNothingWasReportedAtAll(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().has(ToldOnce.Kind.SILENCE))
        .isTrue();
  }

  @Test
  void logsOnceWhileTheSameSilenceKeepsStandingWithinTheStaleWindow(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      WatchdogService watchdog =
          new WatchdogService(state, properties, probe, Clock.systemUTC());

      watchdog.run();
      watchdog.run();

      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .hasSize(1);
    }
  }

  @Test
  void logsOnceForOneSilenceAcrossRestart(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

      new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .hasSize(1);
    }
  }

  @Test
  void logsOnceForOneSilenceAcrossManyStaleWindows(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(10_000_000L, 10_300_000L, 10_600_000L, 12_000_000L);
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      WatchdogService watchdog = new WatchdogService(state, properties, probe, clock);

      watchdog.run();
      watchdog.run();
      watchdog.run();
      watchdog.run();

      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .hasSize(1);
    }
  }

  @Test
  void logsAgainWhenTheSilenceEndedAndNewOneBegan(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      WatchdogService watchdog =
          new WatchdogService(
              state,
              properties,
              probe,
              Clock.fixed(Instant.ofEpochMilli(5_500), ZoneOffset.UTC));
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 2_000));
      watchdog.run();
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Called(), 3_000));
      watchdog.run();
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 4_000));

      watchdog.run();

      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .hasSize(2);
    }
  }

  @Test
  void forgetsTheSilenceAlertOnceTheSilenceEnded(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    WatchdogService watchdog =
        new WatchdogService(
            state,
            properties,
            probe,
            Clock.fixed(Instant.ofEpochMilli(5_500), ZoneOffset.UTC));
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended(null), 2_000));
    watchdog.run();
    state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Called(), 3_000));

    watchdog.run();

    assertThat(state.task("ABC-1").orElseThrow().toldOrNone().isEmpty()).isTrue();
  }

  @Test
  void logsWhatEndedTheSessionAsTheCauseNeverNull(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    state.updateTask(
        "ABC-1",
        task ->
            reducer.signed(task, new SessionSign.Ended("Error: Invalid MCP configuration"), 5_000));
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      new WatchdogService(state, properties, probe, Clock.systemUTC())
          .check("ABC-1");

      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .singleElement()
          .extracting(ILoggingEvent::getKeyValuePairs)
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .contains(new KeyValuePair("cause", "Error: Invalid MCP configuration"));
    }
  }

  @Test
  void logsSilentAsTheCauseOfSessionThatWentQuietWithoutWord(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      new WatchdogService(state, properties, probe, Clock.systemUTC())
          .check("ABC-1");

      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .singleElement()
          .extracting(ILoggingEvent::getKeyValuePairs)
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .contains(new KeyValuePair("cause", "silent"));
    }
  }

  @Test
  void endsSessionThatPrintedNothingWithNoCauseRatherThanAnEmptyOne(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .ended("ABC-1", " ");

    assertThat(Objects.requireNonNull(state.task("ABC-1").orElseThrow().session().halt()).cause())
        .isNull();
  }

  @Test
  void marksTheSessionStoppedAtOnceWhenItEndedRatherThanAtTheNextTick(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .ended("ABC-1", "Error: usage limit");

    assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
        .isEqualTo("the session ended: Error: usage limit");
  }

  @Test
  void alertsNobodyAndKeepsItsVerdictWhereTheSessionsWindowsCannotBeListed(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .lastActiveTimestamp(1_000)
            .session(Session.defaults().withQuietSince(1_000))
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenThrow(
            new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));

    new WatchdogService(state, properties, probe, Clock.systemUTC()).run();

    assertThat(SessionPhase.silentSince(state.task("ABC-1").orElseThrow())).isEqualTo(1_000);
  }

  @Test
  void endsGoneVerdictOnTheCardAndLogsItOnceWhenTheWorktreeCallsMcp(@TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
    try (FakeLogCapture log = new FakeLogCapture(WatchdogService.class)) {
      WatchdogService watchdog =
          new WatchdogService(
              state,
              properties,
              probe,
              Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC));
      state.updateTask("ABC-1", task -> reducer.signed(task, new SessionSign.Ended("boom"), 5_000));

      watchdog.run();
      boolean silentAfterEnd = SessionPhase.silent(state.task("ABC-1").orElseThrow());
      state.updateTask(
          "ABC-1", task -> reducer.signed(task.touched(9_000), new SessionSign.Called(), 9_000));
      watchdog.run();

      assertThat(silentAfterEnd).isTrue();
      assertThat(SessionPhase.silent(state.task("ABC-1").orElseThrow())).isFalse();
      assertThat(log.events())
          .filteredOn(event -> "task stopped".equals(event.getMessage()))
          .hasSize(1);
    }
  }

  @Test
  void namesTheRunningProcessInTheQuietOfSessionThatMovedNothingWhileItsWindowRuns(
      @TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(1_000)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 1_000, 300_000, null));

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .run();

    assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
        .isEqualTo("no sign of life, process running");
  }

  @ParameterizedTest
  @EnumSource(
      value = Session.Window.State.class,
      names = {"DEAD_SHELL", "MISSING"})
  void endsTheSessionOfTaskInProgressWhoseWindowHoldsNoAgentProcess(
      Session.Window.State window, @TempDir Path root) {
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    StateService state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    long now = Clock.systemUTC().millis();
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .lastActiveTimestamp(now)
            .build());
    SessionProbe probe = mock(SessionProbe.class);
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(window, 0, 0, now, 300_000, "Error: bad config"));

    new WatchdogService(state, properties, probe, Clock.systemUTC())
        .run();

    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isEqualTo(SessionPhase.GONE);
    assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
        .isEqualTo("the session ended: Error: bad config");
  }
}
