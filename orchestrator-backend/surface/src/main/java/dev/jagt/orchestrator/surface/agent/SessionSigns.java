package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.job.WatchdogService;
import dev.jagt.orchestrator.service.SessionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/** A session's sign of life, the verdict on it, and what it has spent. */
@Service
@RequiredArgsConstructor
public class SessionSigns {

    private final SessionProbe probe;
    private final WatchdogService watchdog;
    private final AgentSpendReader agentSpend;

    /** {@code sessionLog} is the file the session appends to, or null where none was named. */
    public void record(String taskId, SessionProbe.State state, Path sessionLog) {
        if (sessionLog != null) {
            probe.logAt(taskId, sessionLog);
        }
        probe.report(taskId, state, System.currentTimeMillis());
        watchdog.check(taskId);
        // Off this thread: a read of the log, or the state lock a sweep holds, must not delay the answer.
        if (sessionLog != null) {
            Thread.startVirtualThread(() -> agentSpend.charge(taskId, sessionLog));
        }
    }
}
