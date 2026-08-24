package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Everything one report from a session's own harness does, so that the endpoint receiving it holds the report
 * and nothing else: the sign of life, the verdict on it, what the session has spent, and the one line jagt
 * hands back to a session that has just lost its context.
 */
@Service
@RequiredArgsConstructor
public class SessionReports {

    private final SessionProbe probe;
    private final WatchdogService watchdog;
    private final AgentSpendReader agentSpend;
    private final AgentRuntime runtime;

    /**
     * @param startedBy why the session started, in the CLI's own word; null where it said nothing
     * @return the brief for a session that has just been compacted, or empty for every other report
     */
    public String record(String taskId, SessionProbe.State state, Path sessionLog, String startedBy) {
        if (sessionLog != null) {
            probe.logAt(taskId, sessionLog);
        }
        probe.report(taskId, state, System.currentTimeMillis());
        watchdog.check(taskId);
        // Off this thread: the answer below is what the session reads, and a read of its log — or the state
        // lock a sweep is holding — must never be what that answer waits for.
        if (sessionLog != null) {
            Thread.startVirtualThread(() -> agentSpend.charge(taskId, sessionLog));
        }
        return brief(taskId, startedBy);
    }

    /**
     * A compaction drops the brief silently, and an agent that can no longer see the rules starts breaking
     * them. The line does not repeat them — it names where they are, which is what every other start already
     * has.
     */
    private String brief(String taskId, String startedBy) {
        String compacted = runtime.compactedStart();
        if (compacted == null || compacted.isBlank() || startedBy == null
                || !compacted.equalsIgnoreCase(startedBy.strip())) {
            return "";
        }
        return "You are jagt's sub-agent for " + taskId + ". Your brief is task_context.md in this worktree:"
                + " re-read it, and the repository's own agent instructions, before doing anything else.\n";
    }
}
