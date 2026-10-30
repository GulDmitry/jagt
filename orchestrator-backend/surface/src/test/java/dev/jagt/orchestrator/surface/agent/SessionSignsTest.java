package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.job.WatchdogService;
import dev.jagt.orchestrator.service.SessionLedger;
import dev.jagt.orchestrator.service.SessionProbe;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SessionSignsTest {

    private final SessionLedger ledger = mock(SessionLedger.class);
    private final WatchdogService watchdog = mock(WatchdogService.class);
    private final AgentSpendReader agentSpend = mock(AgentSpendReader.class);

    @Test
    void hasTheTaskJudgedAtOnceRatherThanOnTheNextSweep() {
        SessionSign sign = new SessionSign.WorkStarted();

        new SessionSigns(ledger, watchdog, agentSpend).record("ABC-1", SessionProbe.State.WORKING, null, null, sign);

        verify(ledger).record("ABC-1", SessionProbe.State.WORKING, null, null, sign);
        verify(watchdog).check("ABC-1");
    }

    @Test
    void countsWhatTheSessionSpentInTheLogFileItNamed() {
        new SessionSigns(ledger, watchdog, agentSpend).record("ABC-1", SessionProbe.State.WAITING, null,
                Path.of("/logs/session.jsonl"), new SessionSign.TurnEnded("/logs/session.jsonl"));

        verify(agentSpend, timeout(2_000)).charge("ABC-1", Path.of("/logs/session.jsonl"));
    }
}
