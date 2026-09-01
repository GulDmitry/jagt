package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Everything one report from a session's own harness does: the sign of life, the verdict on it, what the session
 * has spent, and the one line handed back to a session that has just lost its context.
 */
@Service
@RequiredArgsConstructor
public class SessionReports {

    private final SessionProbe probe;
    private final WatchdogService watchdog;
    private final AgentSpendReader agentSpend;
    private final AgentRuntime runtime;

    /**
     * Everything a harness hands a hook that jagt can use, which is not the state. {@code sessionLog} is the file
     * the session appends to, otherwise derived; {@code startedBy} why it started, in the CLI's own word;
     * {@code said} what the CLI told the human. Each null where nothing was named.
     */
    public record Report(Path sessionLog, String startedBy, String said) {

        /** Everything absent: a harness may hand a hook nothing jagt can use, and most of them do. */
        public static Report defaults() {
            return new Report(null, null, null);
        }

        public Report withSessionLog(Path sessionLog) {
            return new Report(sessionLog, startedBy, said);
        }

        public Report withStartedBy(String startedBy) {
            return new Report(sessionLog, startedBy, said);
        }

        public Report withSaid(String said) {
            return new Report(sessionLog, startedBy, said);
        }
    }

    /** The brief for a session that has just been compacted, or empty for every other report. */
    public String record(String taskId, SessionProbe.State state, Report report) {
        Path sessionLog = report.sessionLog();
        String startedBy = report.startedBy();
        if (sessionLog != null) {
            probe.logAt(taskId, sessionLog);
        }
        probe.report(taskId, blocked(state, report.said()), System.currentTimeMillis());
        watchdog.check(taskId);
        // Off this thread: a read of the log, or the state lock a sweep holds, must not delay the answer below.
        if (sessionLog != null) {
            Thread.startVirtualThread(() -> agentSpend.charge(taskId, sessionLog));
        }
        return brief(taskId, startedBy);
    }

    /**
     * One event, two waits: a session refused a permission cannot go on, one merely quiet may be part-way through
     * something long. The CLI's own wording separates them, and an unrecognised one stays the quieter.
     */
    private SessionProbe.State blocked(SessionProbe.State state, String said) {
        // Asked only where an answer could change anything: every start and every turn end says nothing.
        if (state != SessionProbe.State.IDLE || said == null) {
            return state;
        }
        String blocking = runtime.blockingNotification();
        return blocking != null && !blocking.isBlank()
                && said.toLowerCase(java.util.Locale.ROOT).contains(blocking.toLowerCase(java.util.Locale.ROOT))
                ? SessionProbe.State.WAITING : state;
    }

    /** A compaction drops the brief silently; this line names where the rules are rather than repeating them. */
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
