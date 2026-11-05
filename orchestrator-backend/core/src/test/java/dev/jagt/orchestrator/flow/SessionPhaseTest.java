package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionPhaseTest {

    private static final Session GONE = Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.GONE, 4_000, "boom"));

    @Test
    void isSilentSinceTheEndWhereTheAgentIsExpectedToWork() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).session(GONE).build();

        assertThat(SessionPhase.silentSince(task)).isEqualTo(4_000);
        assertThat(SessionPhase.silentBecause(task)).isEqualTo("the session ended: boom");
    }

    @Test
    void isNotSilentWhereTheStatusIdlesByDesign() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.REVIEW_PENDING).session(GONE).build();

        assertThat(SessionPhase.silent(task)).isFalse();
    }

    @Test
    void isNotSilentWhileTheSessionWaitsOnADependencyItNamed() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).report(AgentReport.WAIT).session(GONE).build();

        assertThat(SessionPhase.silent(task)).isFalse();
    }

    @Test
    void hasNoWordsForAQuietSessionThatNeverSaidAnything() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .session(Session.defaults().withQuietSince(3_000)).build();

        assertThat(SessionPhase.silentSince(task)).isEqualTo(3_000);
        assertThat(SessionPhase.silentBecause(task)).isNull();
    }

    @Test
    void isColdFromTheLaterOfItsHaltAndItsLastMove() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.pastCacheLifetime(7_000, Duration.ofHours(1))).thenReturn(true);
        Session session = Session.defaults().withMovedAt(7_000)
                .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null));

        assertThat(SessionPhase.cold(session, runtime, Duration.ofHours(1))).isTrue();
    }

    @Test
    void isColdFromItsLastReadSignWhereNoHookEverStampedIt() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.pastCacheLifetime(7_000, Duration.ofHours(1))).thenReturn(true);

        assertThat(SessionPhase.cold(Session.defaults().withQuietSince(7_000), runtime, Duration.ofHours(1))).isTrue();
    }

    @Test
    void isColdWhileItsTurnIsOpenWhenItsLastSignIsPastTheCacheLifetime() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.pastCacheLifetime(7_000, Duration.ofHours(1))).thenReturn(true);

        assertThat(SessionPhase.cold(Session.defaults().withMovedAt(7_000), runtime, Duration.ofHours(1))).isTrue();
    }

    @Test
    void isNotColdWhenItHasSignedNothing() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.pastCacheLifetime(anyLong(), any())).thenReturn(true);

        assertThat(SessionPhase.cold(Session.defaults(), runtime, Duration.ofHours(1))).isFalse();
    }

    @Test
    void namesAFailedTurnWithItsCauseWhereOneWasGiven() {
        TaskState failed = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .session(Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.FAILED, 4_000, "boom"))).build();
        TaskState unexplained = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .session(Session.defaults().withHalt(new Session.Halt(Session.Halt.Kind.FAILED, 4_000, null))).build();

        assertThat(SessionPhase.silentBecause(failed)).isEqualTo("its turn failed: boom");
        assertThat(SessionPhase.silentBecause(unexplained)).isEqualTo("its turn failed");
    }

    @Test
    void namesAnEndedTurnAndWhetherItsProcessStillRuns() {
        Session.Halt idle = new Session.Halt(Session.Halt.Kind.IDLE, 4_000, null);
        TaskState stopped = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .session(Session.defaults().withHalt(idle).withQuietSince(5_000)).build();
        TaskState running = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .session(Session.defaults().withHalt(idle).withQuietSince(5_000)
                        .withWindow(new Session.Window(Session.Window.State.RUNNING, 1, 0))).build();

        assertThat(SessionPhase.silentBecause(stopped)).isEqualTo("its turn ended and nothing has moved since");
        assertThat(SessionPhase.silentBecause(running))
                .isEqualTo("its turn ended and nothing has moved since, process running");
    }

    @Test
    void namesTheRunningProcessOfASessionQuietWithoutAnySign() {
        TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS)
                .session(Session.defaults().withQuietSince(3_000)
                        .withWindow(new Session.Window(Session.Window.State.RUNNING, 1, 0))).build();

        assertThat(SessionPhase.silentBecause(task)).isEqualTo("no sign of life, process running");
    }

    @Test
    void isWorkingOnceTheSessionHasMovedAndNoneBefore() {
        assertThat(SessionPhase.of(Session.defaults().withMovedAt(1))).isEqualTo(SessionPhase.WORKING);
        assertThat(SessionPhase.of(Session.defaults())).isEqualTo(SessionPhase.NONE);
    }
}
