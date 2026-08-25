package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionReportsTest {

    private final SessionProbe probe = mock(SessionProbe.class);
    private final WatchdogService watchdog = mock(WatchdogService.class);
    private final AgentSpendReader agentSpend = mock(AgentSpendReader.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);

    @Test
    void hasTheTaskJudgedAtOnceRatherThanOnTheNextSweep() {
        new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults());

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WORKING), anyLong());
        verify(watchdog).check("ABC-1");
    }

    @Test
    void believesTheLogFileTheSessionNamedAndCountsWhatItSpent() {
        new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WAITING,
                        SessionReports.Report.defaults().withSessionLog(Path.of("/logs/session.jsonl")));

        verify(probe).logAt("ABC-1", Path.of("/logs/session.jsonl"));
        verify(agentSpend, timeout(2_000)).charge("ABC-1", Path.of("/logs/session.jsonl"));
    }

    /**
     * One event covers a permission a session is being refused and a prompt merely left quiet, and only the
     * CLI's own wording separates them.
     */
    @Test
    void callsASessionBlockedWhenItsNotificationNamesWhatThisCliSaysWhileBlocked() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(probe, watchdog, agentSpend, runtime).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude needs your permission to use Bash"));

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WAITING), anyLong());
    }

    @Test
    void leavesANotificationThisCliDoesNotUseWhileBlockedToTheThreshold() {
        when(runtime.blockingNotification()).thenReturn("needs your permission");

        new SessionReports(probe, watchdog, agentSpend, runtime).record("ABC-1", SessionProbe.State.IDLE,
                SessionReports.Report.defaults().withSaid("Claude is waiting for your input"));

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.IDLE), anyLong());
    }

    @Test
    void handsACompactedSessionItsBriefBack() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact"));

        assertThat(answered).contains("sub-agent for ABC-1", "task_context.md");
    }

    @Test
    void answersAnOrdinaryStartWithNothingAtAll() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("startup"));

        assertThat(answered).isEmpty();
    }

    @Test
    void briefsNothingWhenTheCliSaysNothingAboutWhyASessionStarted() {
        when(runtime.compactedStart()).thenReturn("");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, SessionReports.Report.defaults().withStartedBy("compact"));

        assertThat(answered).isEmpty();
    }
}
