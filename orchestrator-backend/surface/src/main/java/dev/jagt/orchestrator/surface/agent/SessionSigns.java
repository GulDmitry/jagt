package dev.jagt.orchestrator.surface.agent;

import org.jspecify.annotations.Nullable;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.job.WatchdogService;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.SessionLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/** A session's sign of life, the verdict on it, and what it has spent. */
@Service
@RequiredArgsConstructor
public class SessionSigns {

    private final SessionLedger ledger;
    private final WatchdogService watchdog;
    private final AgentSpendReader agentSpend;

    public void asked(String taskId, SessionSign.Asked sign) {
        ledger.sign(taskId, sign);
        watchdog.check(taskId);
    }

    /** {@code sessionLog} is the file the session appends to, or null where none was named. */
    public void record(String taskId, SessionProbe.State state, @Nullable String cause, @Nullable Path sessionLog,
                       SessionSign sign) {
        ledger.record(taskId, state, cause, sessionLog, sign);
        watchdog.check(taskId);
        // Off this thread: a read of the log, or the state lock a sweep holds, must not delay the answer.
        if (sessionLog != null) {
            Thread.startVirtualThread(() -> agentSpend.charge(taskId, sessionLog));
        }
    }
}
