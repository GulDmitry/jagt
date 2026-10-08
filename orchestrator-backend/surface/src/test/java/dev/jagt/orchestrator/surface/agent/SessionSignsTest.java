package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.job.WatchdogService;
import dev.jagt.orchestrator.service.SessionProbe;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SessionSignsTest {

    private final SessionProbe probe = mock(SessionProbe.class);
    private final WatchdogService watchdog = mock(WatchdogService.class);
    private final AgentSpendReader agentSpend = mock(AgentSpendReader.class);

    @Test
    void hasTheTaskJudgedAtOnceRatherThanOnTheNextSweep() {
        new SessionSigns(probe, watchdog, agentSpend).record("ABC-1", SessionProbe.State.WORKING, null);

        verify(probe).report(eq("ABC-1"), eq(SessionProbe.State.WORKING), anyLong());
        verify(watchdog).check("ABC-1");
    }

    @Test
    void believesTheLogFileTheSessionNamedAndCountsWhatItSpent() {
        new SessionSigns(probe, watchdog, agentSpend)
                .record("ABC-1", SessionProbe.State.WAITING, Path.of("/logs/session.jsonl"));

        verify(probe).logAt("ABC-1", Path.of("/logs/session.jsonl"));
        verify(agentSpend, timeout(2_000)).charge("ABC-1", Path.of("/logs/session.jsonl"));
    }
}
