package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.WatchdogService;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
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
    private final ConfigService configService;

    /**
     * Everything a harness hands a hook that jagt can use, which is not the state. {@code sessionLog} is the file
     * the session appends to, otherwise derived; {@code startedBy} why it started, in the CLI's own word;
     * {@code said} what the CLI told the human; {@code task} the task it runs for. Each null where nothing was
     * named.
     */
    public record Report(Path sessionLog, String startedBy, String said, TaskState task) {

        /** Everything absent: a harness may hand a hook nothing jagt can use, and most of them do. */
        public static Report defaults() {
            return new Report(null, null, null, null);
        }

        public Report withSessionLog(Path sessionLog) {
            return new Report(sessionLog, startedBy, said, task);
        }

        public Report withStartedBy(String startedBy) {
            return new Report(sessionLog, startedBy, said, task);
        }

        public Report withSaid(String said) {
            return new Report(sessionLog, startedBy, said, task);
        }

        public Report withTask(TaskState task) {
            return new Report(sessionLog, startedBy, said, task);
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
        return brief(taskId, startedBy, report.task());
    }

    /**
     * A turn end, answered with the CLI's refusal where the move is still the agent's and nothing was reported
     * since the turn began. One refusal per turn: a turn the refusal itself sent on always ends.
     */
    public String turnEnded(String taskId, Report report, boolean sentOn, boolean pausedOnBackgroundWork) {
        record(taskId, SessionProbe.State.IDLE, report);
        TaskState task = report.task();
        long turnStarted = probe.turnStartedAt(taskId);
        if (sentOn || pausedOnBackgroundWork || task == null || turnStarted == 0
                || task.lastActiveTimestamp() >= turnStarted || !Move.endsUnreported(task.status(), task.message())) {
            return withTheMaster(task) ? runtime.toldTheHuman("→ with the Master for review · its verdict arrives"
                    + " here") : "";
        }
        return runtime.refusedTurnEnd("Your turn is ending with " + taskId + " at " + task.status()
                + " on the board and nothing reported this turn. Call update_agent_status first: REVIEW_PENDING"
                + " if the work is done, outcome=question if you need the human, IN_PROGRESS if you go on.");
    }

    private boolean withTheMaster(TaskState task) {
        return task != null && task.status() == TaskStatus.REVIEW_PENDING && configService.load().master().running();
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

    /** A compaction drops the brief and the task's facts silently: the rules are named, the facts restated. */
    private String brief(String taskId, String startedBy, TaskState task) {
        String compacted = runtime.compactedStart();
        if (compacted == null || compacted.isBlank() || startedBy == null
                || !compacted.equalsIgnoreCase(startedBy.strip())) {
            return "";
        }
        return "You are jagt's sub-agent for " + taskId + facts(task) + ". In this worktree task_request.md is what"
                + " the task was opened with and task_context.md the instruction standing now: re-read both, and the"
                + " repository's own agent instructions, before doing anything else.\n";
    }

    private static String facts(TaskState task) {
        if (task == null) {
            return "";
        }
        String asked = AgentReport.of(task.message()) == AgentReport.QUESTION
                ? "; your open question: " + AgentReport.QUESTION.detailOf(task.message()) : "";
        return (present(task.title()) ? " — " + task.title() : "")
                + (present(task.ticketUrl()) ? " (" + task.ticketUrl() + ")" : "")
                + ". On the board: " + task.status() + asked;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
