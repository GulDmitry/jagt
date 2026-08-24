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
                .record("ABC-1", SessionProbe.State.WORKING, null, null);

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WORKING), anyLong());
        verify(watchdog).check("ABC-1");
    }

    @Test
    void believesTheLogFileTheSessionNamedAndCountsWhatItSpent() {
        new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WAITING, Path.of("/logs/session.jsonl"), null);

        verify(probe).logAt("ABC-1", Path.of("/logs/session.jsonl"));
        verify(agentSpend, timeout(2_000)).charge("ABC-1", Path.of("/logs/session.jsonl"));
    }

    @Test
    void handsACompactedSessionItsBriefBack() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, null, "compact");

        assertThat(answered).contains("sub-agent for ABC-1", "task_context.md");
    }

    @Test
    void answersAnOrdinaryStartWithNothingAtAll() {
        when(runtime.compactedStart()).thenReturn("compact");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, null, "startup");

        assertThat(answered).isEmpty();
    }

    @Test
    void briefsNothingWhenTheCliSaysNothingAboutWhyASessionStarted() {
        when(runtime.compactedStart()).thenReturn("");

        String answered = new SessionReports(probe, watchdog, agentSpend, runtime)
                .record("ABC-1", SessionProbe.State.WORKING, null, "compact");

        assertThat(answered).isEmpty();
    }
}
