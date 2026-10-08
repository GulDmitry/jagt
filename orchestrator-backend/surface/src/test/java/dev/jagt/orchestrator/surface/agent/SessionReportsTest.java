package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionReportsTest {

    private final SessionSigns signs = mock(SessionSigns.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final TurnEnds turnEnds = mock(TurnEnds.class);

    @Test
    void callsASessionBlockedWhenItsNotificationNamesWhatThisCliSaysWhileBlocked() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(signs, runtime, turnEnds).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude needs your permission to use Bash"));

        verify(signs).record("ABC-1", SessionProbe.State.WAITING, null);
    }

    @Test
    void leavesANotificationThisCliDoesNotUseWhileBlockedToTheThreshold() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(signs, runtime, turnEnds).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude is waiting for your input"));

        verify(signs).record("ABC-1", SessionProbe.State.IDLE, null);
    }

    @Test
    void recordsATurnEndAsTheSessionGoingIdleAndAnswersWithTheTurnsVerdict() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
        when(turnEnds.answer("ABC-1", task, false, false)).thenReturn("{\"decision\": \"block\"}");

        String answered = new SessionReports(signs, runtime, turnEnds)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false);

        verify(signs).record("ABC-1", SessionProbe.State.IDLE, null);
        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void handsACompactedSessionItsBriefBack() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(signs, runtime, turnEnds)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact"));

        assertThat(answered).contains("sub-agent for ABC-1", "task_request.md", "task_context.md");
    }

    @Test
    void restatesTheTaskAndItsOpenQuestionToACompactedSession() {
        when(runtime.compactedStart()).thenReturn("compact");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .title("Add v3 beside v2").ticketUrl("https://tracker.example/ABC-1")
                .message("outcome=question — keep v2?").build();

        String answered = new SessionReports(signs, runtime, turnEnds).record("ABC-1",
                SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact").withTask(task));

        assertThat(answered).contains("Add v3 beside v2", "https://tracker.example/ABC-1", "IN_PROGRESS",
                "your open question: keep v2?");
    }

    @Test
    void answersAnOrdinaryStartWithNothingAtAll() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(signs, runtime, turnEnds)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("startup"));

        assertThat(answered).isEmpty();
    }

    @Test
    void briefsNothingWhenTheCliSaysNothingAboutWhyASessionStarted() {
        when(runtime.compactedStart()).thenReturn("");

        String answered = new SessionReports(signs, runtime, turnEnds)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact"));

        assertThat(answered).isEmpty();
    }
}
