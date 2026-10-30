package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void isIdleSinceTheLaterOfItsHaltAndItsLastMove() {
        Session session = Session.defaults().withMovedAt(7_000)
                .withHalt(new Session.Halt(Session.Halt.Kind.IDLE, 5_000, null));

        assertThat(SessionPhase.idleSince(session)).isEqualTo(7_000);
    }

    @Test
    void isNotIdleWhileItsTurnIsOpen() {
        assertThat(SessionPhase.idleSince(Session.defaults().withMovedAt(7_000))).isZero();
    }

    @Test
    void isNotIdleWhenItHasSignedNothing() {
        assertThat(SessionPhase.idleSince(Session.defaults())).isZero();
    }
}
