package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.WAITING)));

        new WatchdogService(state, notifications, properties, probe).run();

        verify(notifications).send(argThat(sent -> sent.topic() == Notification.Topic.WATCHDOG
                && "ABC-1".equals(sent.taskId()) && "waiting for input".equals(sent.body())));
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

        new WatchdogService(state, mock(Notifications.class), properties, probe).run();

        verify(probe, never()).of(anyString(), any(), anyLong(), anyLong());
    }

    @ParameterizedTest
    @EnumSource(value = SessionProbe.State.class, names = {"WAITING", "IDLE"})
    void sendsNoSecondBannerForAnAgentThatHadAlreadyAsked(SessionProbe.State reported, @TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("awaiting: which database").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, reported)));

        new WatchdogService(state, notifications, properties, probe).run();

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
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, SessionProbe.State.GONE)));

        new WatchdogService(state, mock(Notifications.class), properties, probe).check("ABC-1");

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
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, null)));

        new WatchdogService(state, mock(Notifications.class), properties, probe).run();

        assertThat(state.task("ABC-1").orElseThrow().silentSince()).isEqualTo(4_000);
    }

    @Test
    void takesTheStampBackOffAsSoonAsTheSessionIsMovingAgain(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .silentSince(1_000).build());

        new WatchdogService(state, mock(Notifications.class), properties, mock(SessionProbe.class)).run();

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
                .thenReturn(Optional.of(new SessionProbe.Silence(4_000, null)));
        AtomicInteger writes = new AtomicInteger();
        state.onChange(file -> writes.incrementAndGet());
        WatchdogService watchdog = new WatchdogService(state, mock(Notifications.class), properties, probe);

        watchdog.run();
        watchdog.run();
        watchdog.run();

        assertThat(writes).hasValue(1);
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void watchesEveryStatusWhoseNextMoveIsTheAgentsOwn(TaskStatus status) {
        assertThat(WatchdogService.watches(status)).isEqualTo(Move.ownerOf(status) == Owner.AGENT);
    }

    @Test
    void bannersADeadSessionEvenWhereTheQuestionItAskedIsStillUnanswered(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("awaiting: which database").build());
        Notifications notifications = mock(Notifications.class);
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.of(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, SessionProbe.State.GONE)));

        new WatchdogService(state, notifications, properties, probe).run();

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

        new WatchdogService(state, mock(Notifications.class), properties, mock(SessionProbe.class))
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
                .thenReturn(Optional.of(new SessionProbe.Silence(1_000, null)));

        new WatchdogService(state, notifications, properties, probe).run();

        verify(notifications).send(argThat(sent -> "no sign of life".equals(sent.body())));
    }
}
