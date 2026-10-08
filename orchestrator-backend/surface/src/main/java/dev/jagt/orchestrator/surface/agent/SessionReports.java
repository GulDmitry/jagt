package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.flow.AgentReport;
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

    private final SessionSigns signs;
    private final AgentRuntime runtime;
    private final TurnEnds turnEnds;

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
        signs.record(taskId, blocked(state, report.said()), report.sessionLog());
        return brief(taskId, report.startedBy(), report.task());
    }

    public String turnEnded(String taskId, Report report, boolean sentOn, boolean pausedOnBackgroundWork) {
        record(taskId, SessionProbe.State.IDLE, report);
        return turnEnds.answer(taskId, report.task(), sentOn, pausedOnBackgroundWork);
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
