package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.SessionHost;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WatchdogServiceTest {

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "IN_PROGRESS", "SHIPPING"})
    void alertsTheHumanWhenAnAgentStoppedInAStatusItShouldBeWorkingIn(TaskStatus status, @TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", status).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE, null)));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.WATCHDOG
                && "ABC-1".equals(sent.taskId()) && "the session ended".equals(sent.body())));
    }

    @Test
    void watchesOnlyTheStatusesInWhichAnAgentIsSupposedToBeWorking() {
        assertThat(Arrays.stream(TaskStatus.values()).filter(WatchdogService::watches).toList())
                .containsExactly(TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.SHIPPING);
    }

    @Test
    void asksForNoVerdictAboutASessionWhoseStatusIdlesByDesign(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        verify(probe, never()).of(anyString(), any(), anyLong(), anyLong());
    }

    @Test
    void asksForNoVerdictAboutASessionThatToldJagtItWaitsOnADependency(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("db down").report(AgentReport.WAIT).build());
        SessionProbe probe = mock(SessionProbe.class);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        verify(probe, never()).of(anyString(), any(), anyLong(), anyLong());
    }

    @Test
    void sendsNoSecondBannerForAnAgentThatHadAlreadyAsked(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("which database").report(AgentReport.QUESTION).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.IDLE, null)));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verifyNoInteractions(notifications);
    }

    @Test
    void sendsNoBannerForAnAgentWhosePromptTheScreenRaisedAndNoHookEverFollowed(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .report(AgentReport.PERMISSION).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, null, null)));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verifyNoInteractions(notifications);
    }

    @Test
    void stampsWhySoBothSurfacesCanSayItAndNotOnlyTheBanner(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, SessionProbe.State.GONE, null)));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).check("ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().silentBecause()).isEqualTo("the session ended");
    }

    @Test
    void stampsTheSilenceOnTheTaskSoBothSurfacesShowItAndNotOnlyTheDesktopPing(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, null, null)));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        assertThat(state.task("ABC-1").orElseThrow().silentSince()).isEqualTo(4_000);
    }

    @Test
    void takesTheStampBackOffAsSoonAsTheSessionIsMovingAgain(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .silentSince(1_000).build());

        new WatchdogService(state, mock(Notifications.class), properties, mock(SessionProbe.class), Clock.systemUTC()).run();

        assertThat(state.task("ABC-1").orElseThrow().agentIsSilent()).isFalse();
    }

    @Test
    void writesNothingOnTheTicksThatFindTheSameVerdictAsTheOneBefore(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, null, null)));
        AtomicInteger writes = new AtomicInteger();
        state.onChange(file -> writes.incrementAndGet());
        WatchdogService watchdog = new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC());

        watchdog.run();
        watchdog.run();
        watchdog.run();

        assertThat(writes).hasValue(2);
    }

    @Test
    void writesNothingOnTheTicksOfATaskThatKeepsMoving(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        AtomicInteger writes = new AtomicInteger();
        state.onChange(file -> writes.incrementAndGet());
        WatchdogService watchdog = new WatchdogService(state, mock(Notifications.class), properties,
                mock(SessionProbe.class), Clock.systemUTC());

        watchdog.run();
        watchdog.run();

        assertThat(writes).hasValue(0);
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void watchesEveryStatusWhoseNextMoveIsTheAgentsOwnAndJagtIsNotHoldingItself(TaskStatus status) {
        assertThat(WatchdogService.watches(status))
                .isEqualTo(Move.ownerOf(status) == Owner.AGENT && !FlowRules.awaitingVerification(status));
    }

    @Test
    void bannersADeadSessionEvenWhereTheQuestionItAskedIsStillUnanswered(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("which database").report(AgentReport.QUESTION).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE, null)));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> "the session ended".equals(sent.body())));
    }

    @Test
    void leavesAStampThatLandedWhileItWasStillDeciding(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        TaskState decidedFrom = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .silentSince(1_000).build();
        state.putTask("ABC-1", decidedFrom.withSilentSince(5_000, "waiting for input"));

        new WatchdogService(state, mock(Notifications.class), properties, mock(SessionProbe.class), Clock.systemUTC())
                .stamp("ABC-1", decidedFrom, null);

        assertThat(state.task("ABC-1").orElseThrow().silentSince()).isEqualTo(5_000);
    }

    @Test
    void saysThereIsNoSignOfLifeWhereNothingWasReportedAtAll(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, null, null)));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> "no sign of life".equals(sent.body())));
    }

    @Test
    void alertsOnceWhileTheSameSilenceKeepsStandingWithinTheStaleWindow(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE, null)));
        WatchdogService watchdog = new WatchdogService(state, notifications, properties, probe, Clock.systemUTC());

        watchdog.run();
        watchdog.run();

        verify(notifications, times(1)).send(any());
    }

    @Test
    void alertsOnceForOneSilenceAcrossARestart(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE, null)));
        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications, times(1)).send(any());
    }

    @Test
    void forgetsTheSessionsOfTasksThatNoLongerExist(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        verify(probe).keepOnly(Set.of("ABC-1"));
    }

    @Test
    void alertsOnceForOneSilenceAcrossManyStaleWindows(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE, null)));
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(10_000_000L, 10_300_000L, 10_600_000L, 12_000_000L);
        WatchdogService watchdog = new WatchdogService(state, notifications, properties, probe, clock);

        watchdog.run();
        watchdog.run();
        watchdog.run();
        watchdog.run();

        verify(notifications, times(1)).send(any());
    }

    @Test
    void alertsAgainWhenTheSilenceEndedAndANewOneBegan(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong())).thenReturn(
                Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE, null)),
                Optional.empty(),
                Optional.of(new SessionProbe.Silence(9_000_000, SessionProbe.State.GONE, null)));
        WatchdogService watchdog = new WatchdogService(state, notifications, properties, probe, Clock.systemUTC());

        watchdog.run();
        watchdog.run();
        watchdog.run();

        verify(notifications, times(2)).send(any());
    }

    @ParameterizedTest
    @CsvSource({"GONE,Error: Invalid MCP configuration,Error: Invalid MCP configuration", ",,silent"})
    @ResourceLock(Resources.GLOBAL)
    void logsWhatEndedTheSessionAsTheCauseNeverNull(SessionProbe.State reported, String cause, String logged,
                                                    @TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, reported, cause)));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(WatchdogService.class)).addAppender(log);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).check("ABC-1");

        assertThat(List.copyOf(log.list)).filteredOn(event -> "task stopped".equals(event.getMessage()))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("cause", logged));
    }

    @Test
    void endsASessionThatPrintedNothingWithNoCauseRatherThanAnEmptyOne(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        SessionProbe probe = mock(SessionProbe.class);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).ended("ABC-1", " ");

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.GONE), isNull(), anyLong());
    }

    @Test
    void stampsTheSilenceAtOnceWhenASessionEndedRatherThanAtTheNextTick(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, SessionProbe.State.GONE, null)));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC())
                .ended("ABC-1", "Error: usage limit");

        assertThat(state.task("ABC-1").orElseThrow().agentIsSilent()).isTrue();
    }

    @Test
    void alertsNobodyAndKeepsItsVerdictWhereTheSessionsWindowsCannotBeListed(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(1_000).silentSince(1_000).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong())).thenThrow(new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        assertThat(state.task("ABC-1").orElseThrow().silentSince()).isEqualTo(1_000);
        verifyNoInteractions(notifications);
    }
}
