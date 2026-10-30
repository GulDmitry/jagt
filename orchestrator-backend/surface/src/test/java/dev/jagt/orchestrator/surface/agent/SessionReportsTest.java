package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionReportsTest {

    private final SessionSigns signs = mock(SessionSigns.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final TurnEnds turnEnds = mock(TurnEnds.class);

    @Test
    void signsAnAskWhenItsNotificationNamesWhatThisCliSaysWhileBlocked() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(signs, runtime, turnEnds).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude needs your permission to use Bash"));

        verify(signs).asked("ABC-1", new SessionSign.Asked("Claude needs your permission to use Bash",
                SessionSign.Asked.Channel.HOOK));
    }

    @Test
    void leavesANotificationThisCliDoesNotUseWhileBlockedToTheThreshold() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(signs, runtime, turnEnds).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude is waiting for your input"));

        verify(signs).record("ABC-1", SessionProbe.State.IDLE, null, null, new SessionSign.TurnEnded(null));
        verify(signs, never()).asked(any(), any());
    }

    @Test
    void recordsASessionClearedOrResumedAsIdleBecauseANewStartFollows() {
        when(runtime.continuingEnds()).thenReturn(Set.of("clear", "resume"));

        new SessionReports(signs, runtime, turnEnds).record("ABC-1", SessionProbe.State.GONE,
                SessionReports.Report.defaults().withEndedBy("clear"));

        verify(signs).record("ABC-1", SessionProbe.State.IDLE, null, null, new SessionSign.Restarted("clear"));
    }

    @Test
    void recordsASessionThatEndedForGoodAsGone() {
        when(runtime.continuingEnds()).thenReturn(Set.of("clear", "resume"));

        new SessionReports(signs, runtime, turnEnds).record("ABC-1", SessionProbe.State.GONE,
                SessionReports.Report.defaults().withEndedBy("prompt_input_exit"));

        verify(signs).record("ABC-1", SessionProbe.State.GONE, null, null, new SessionSign.Ended("prompt_input_exit"));
    }

    @Test
    void recordsAUsageLimitFailureAsFailedAndSaysItIsOne() {
        when(runtime.rateLimitError()).thenReturn("rate_limit");

        boolean limit = new SessionReports(signs, runtime, turnEnds)
                .turnFailed("ABC-1", SessionReports.Report.defaults().withFailedOn("rate_limit"));

        verify(signs).record("ABC-1", SessionProbe.State.FAILED, "rate_limit", null, new SessionSign.TurnFailed("rate_limit"));
        assertThat(limit).isTrue();
    }

    @Test
    void recordsAnyOtherFailureAsFailedNamingItAndSaysItIsNoLimit() {
        when(runtime.rateLimitError()).thenReturn("rate_limit");

        boolean limit = new SessionReports(signs, runtime, turnEnds)
                .turnFailed("ABC-1", SessionReports.Report.defaults().withFailedOn("overloaded"));

        verify(signs).record("ABC-1", SessionProbe.State.FAILED, "overloaded", null,
                new SessionSign.TurnFailed("overloaded"));
        assertThat(limit).isFalse();
    }

    @Test
    void recordsATurnEndAsTheSessionGoingIdleAndAnswersWithTheTurnsVerdict() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();
        when(turnEnds.answer("ABC-1", task, false, false)).thenReturn("{\"decision\": \"block\"}");

        String answered = new SessionReports(signs, runtime, turnEnds)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, false);

        verify(signs).record("ABC-1", SessionProbe.State.IDLE, null, null, new SessionSign.TurnEnded(null));
        assertThat(answered).isEqualTo("{\"decision\": \"block\"}");
    }

    @Test
    void reportsASessionWaitingOnItsOwnBackgroundWorkAsWorkingSoItIsNeverCold() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

        new SessionReports(signs, runtime, turnEnds)
                .turnEnded("ABC-1", SessionReports.Report.defaults().withTask(task), false, true);

        verify(signs).record("ABC-1", SessionProbe.State.WORKING, null, null, new SessionSign.WorkStarted());
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
                .message("keep v2?").report(AgentReport.QUESTION).build();

        String answered = new SessionReports(signs, runtime, turnEnds).record("ABC-1",
                SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact").withTask(task));

        assertThat(answered).contains("Add v3 beside v2", "https://tracker.example/ABC-1", "IN_PROGRESS",
                "your open question: keep v2?");
    }

    @Test
    void restatesTheLastReportAndTheBranchToACompactedSession() {
        when(runtime.compactedStart()).thenReturn("compact");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("outcome=progress — v3 routes done").build();

        String answered = new SessionReports(signs, runtime, turnEnds).record("ABC-1",
                SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact").withTask(task));

        assertThat(answered).contains("its branch, ABC-1", "your last report: outcome=progress — v3 routes done");
    }

    @Test
    void restatesNoReportToACompactedSessionTheTaskNeverHeardFrom() {
        when(runtime.compactedStart()).thenReturn("compact");
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

        String answered = new SessionReports(signs, runtime, turnEnds).record("ABC-1",
                SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact").withTask(task));

        assertThat(answered).doesNotContain("your last report", "null");
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
