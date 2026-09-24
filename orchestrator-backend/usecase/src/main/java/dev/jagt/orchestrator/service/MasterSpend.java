package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * What the Master session has cost. A line of its own: it is nobody's task, and booking it against one would
 * charge a human's work to a reviewer's reading. Kept in memory and recounted from the log after a restart,
 * since the log is cumulative and nothing is lost by reading it again.
 */
@Service
@RequiredArgsConstructor
public class MasterSpend {

    private final SessionLog sessionLog;
    private final AgentRuntime agentRuntime;
    private final OrchestratorPaths paths;

    private volatile TokenUsage counted = TokenUsage.NONE;
    private volatile String log = "";
    private volatile long mark;

    /** Reads what the session has appended since the last look. Called on the tick that watches the session. */
    public synchronized void advance() {
        Path current = agentRuntime.sessionLogOf(paths.root()).orElse(null);
        if (current == null) {
            return;
        }
        // Another log means another session: its own bytes are counted from the start, the total carrying over.
        String name = current.toAbsolutePath().normalize().toString();
        if (!name.equals(log)) {
            log = name;
            mark = 0;
        }
        LogSpend.since(sessionLog, current, mark).ifPresent(advance -> {
            counted = counted.plus(advance.usage());
            mark = advance.mark();
        });
    }

    public TokenUsage total() {
        return counted;
    }
}
