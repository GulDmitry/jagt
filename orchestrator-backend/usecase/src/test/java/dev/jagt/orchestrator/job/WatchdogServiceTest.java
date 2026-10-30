package dev.jagt.orchestrator.job;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.SessionPhase;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", status).alias("a1").lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.WATCHDOG
                && "ABC-1".equals(sent.taskId()) && "the session ended".equals(sent.body())));
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
    void readsTheSessionOfATaskWhoseStatusIdlesByDesign(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        verify(probe).read(anyString(), any(), anyLong(), anyLong());
    }

    @Test
    void readsNothingOfAClosedTask(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        verify(probe, never()).read(anyString(), any(), anyLong(), anyLong());
    }

    @Test
    void alertsNobodyAboutAnEndedSessionWhoseStatusIdlesByDesign(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verifyNoInteractions(notifications);
    }

    @Test
    void alertsNobodyAboutASessionThatToldJagtItWaitsOnADependency(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("db down").report(AgentReport.WAIT).lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verifyNoInteractions(notifications);
    }

    @Test
    void sendsNoSecondBannerForAnAgentThatHadAlreadyAsked(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("which database").report(AgentReport.QUESTION).lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.TurnEnded(null), 1_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verifyNoInteractions(notifications);
    }

    @Test
    void sendsNoBannerForAnAgentWhosePromptTheScreenRaisedAndNoHookEverFollowed(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .report(AgentReport.PERMISSION).lastActiveTimestamp(1_000).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verifyNoInteractions(notifications);
    }

    @Test
    void recordsTheQuietTheReadFoundOnTheSessionSoBothSurfacesShowIt(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        assertThat(SessionPhase.silentSince(state.task("ABC-1").orElseThrow())).isEqualTo(1_000);
    }

    @Test
    void writesNothingOnTheTicksThatFindTheSameVerdictAsTheOneBefore(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
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
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));
        AtomicInteger writes = new AtomicInteger();
        state.onChange(file -> writes.incrementAndGet());
        WatchdogService watchdog = new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC());

        watchdog.run();
        watchdog.run();

        assertThat(writes).hasValue(0);
    }

    @Test
    void bannersADeadSessionEvenWhereTheQuestionItAskedIsStillUnanswered(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("which database").report(AgentReport.QUESTION).lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> "the session ended".equals(sent.body())));
    }

    @Test
    void saysThereIsNoSignOfLifeWhereNothingWasReportedAtAll(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        verify(notifications).send(argThat(sent -> "no sign of life".equals(sent.body())));
    }

    @Test
    void alertsOnceWhileTheSameSilenceKeepsStandingWithinTheStaleWindow(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
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
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
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
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 5_000));
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
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
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
        WatchdogService watchdog = new WatchdogService(state, notifications, properties, probe,
                Clock.fixed(Instant.ofEpochMilli(5_500), ZoneOffset.UTC));
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 2_000));
        watchdog.run();
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Called(), 3_000));
        watchdog.run();
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended(null), 4_000));

        watchdog.run();

        verify(notifications, times(2)).send(any());
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsWhatEndedTheSessionAsTheCauseNeverNull(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").lastActiveTimestamp(1_000).build());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task,
                new SessionSign.Ended("Error: Invalid MCP configuration"), 5_000));
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(WatchdogService.class)).addAppender(log);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).check("ABC-1");

        assertThat(List.copyOf(log.list)).filteredOn(event -> "task stopped".equals(event.getMessage()))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("cause", "Error: Invalid MCP configuration"));
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void logsSilentAsTheCauseOfASessionThatWentQuietWithoutAWord(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").lastActiveTimestamp(1_000).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(WatchdogService.class)).addAppender(log);

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).check("ABC-1");

        assertThat(List.copyOf(log.list)).filteredOn(event -> "task stopped".equals(event.getMessage()))
                .singleElement().extracting(ILoggingEvent::getKeyValuePairs).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(new KeyValuePair("cause", "silent"));
    }

    @Test
    void endsASessionThatPrintedNothingWithNoCauseRatherThanAnEmptyOne(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).ended("ABC-1", " ");

        assertThat(state.task("ABC-1").orElseThrow().session().halt().cause()).isNull();
    }

    @Test
    void marksTheSessionStoppedAtOnceWhenItEndedRatherThanAtTheNextTick(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 0, 300_000, null));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC())
                .ended("ABC-1", "Error: usage limit");

        assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
                .isEqualTo("the session ended: Error: usage limit");
    }

    @Test
    void alertsNobodyAndKeepsItsVerdictWhereTheSessionsWindowsCannotBeListed(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .lastActiveTimestamp(1_000).session(Session.defaults().withQuietSince(1_000)).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong())).thenThrow(new SessionHost.WindowsUnreadable(
                "tmux list-windows jagt failed (exit 1): server timed out"));

        new WatchdogService(state, notifications, properties, probe, Clock.systemUTC()).run();

        assertThat(SessionPhase.silentSince(state.task("ABC-1").orElseThrow())).isEqualTo(1_000);
        verifyNoInteractions(notifications);
    }

    @Test
    void endsAGoneVerdictOnTheCardAndTheBannerWhenTheWorktreeCallsMcp(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.UNREAD, 0, 0, 1_000, 300_000, null));
        WatchdogService watchdog = new WatchdogService(state, notifications, properties, probe,
                Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC));
        state.updateTask("ABC-1", task -> TaskReducer.signed(task, new SessionSign.Ended("boom"), 5_000));

        watchdog.run();
        boolean silentAfterEnd = SessionPhase.silent(state.task("ABC-1").orElseThrow());
        state.updateTask("ABC-1", task -> TaskReducer.signed(task.touched(9_000), new SessionSign.Called(), 9_000));
        watchdog.run();

        assertThat(silentAfterEnd).isTrue();
        assertThat(SessionPhase.silent(state.task("ABC-1").orElseThrow())).isFalse();
        verify(notifications, times(1)).send(argThat(sent -> "the session ended: boom".equals(sent.body())));
    }

    @Test
    void namesTheRunningProcessInTheQuietOfASessionThatMovedNothingWhileItsWindowRuns(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(1_000).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 1_000, 300_000, null));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
                .isEqualTo("no sign of life, process running");
    }

    @ParameterizedTest
    @EnumSource(value = Session.Window.State.class, names = {"DEAD_SHELL", "MISSING"})
    void endsTheSessionOfATaskInProgressWhoseWindowHoldsNoAgentProcess(Session.Window.State window, @TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        long now = Clock.systemUTC().millis();
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .lastActiveTimestamp(now).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(window, 0, 0, now, 300_000, "Error: bad config"));

        new WatchdogService(state, mock(Notifications.class), properties, probe, Clock.systemUTC()).run();

        assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session())).isEqualTo(SessionPhase.GONE);
        assertThat(SessionPhase.silentBecause(state.task("ABC-1").orElseThrow()))
                .isEqualTo("the session ended: Error: bad config");
    }
}
