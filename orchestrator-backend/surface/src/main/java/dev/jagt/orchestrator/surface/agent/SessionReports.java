package dev.jagt.orchestrator.surface.agent;

import org.jspecify.annotations.Nullable;
import dev.jagt.orchestrator.service.SessionProbe;
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
     * {@code said} what the CLI told the human; {@code task} the task it runs for; {@code endedBy} why a session
     * ended; {@code failedOn} the error a turn ended on. Each null where nothing was named.
     */
    public record Report(@Nullable Path sessionLog, @Nullable String startedBy, @Nullable String said,
                         @Nullable TaskState task, @Nullable String endedBy, @Nullable String failedOn) {

        /** Everything absent: a harness may hand a hook nothing jagt can use, and most of them do. */
        public static Report defaults() {
            return new Report(null, null, null, null, null, null);
        }

        public Report withSessionLog(@Nullable Path sessionLog) {
            return new Report(sessionLog, startedBy, said, task, endedBy, failedOn);
        }

        public Report withStartedBy(@Nullable String startedBy) {
            return new Report(sessionLog, startedBy, said, task, endedBy, failedOn);
        }

        public Report withSaid(@Nullable String said) {
            return new Report(sessionLog, startedBy, said, task, endedBy, failedOn);
        }

        public Report withTask(@Nullable TaskState task) {
            return new Report(sessionLog, startedBy, said, task, endedBy, failedOn);
        }

        public Report withEndedBy(@Nullable String endedBy) {
            return new Report(sessionLog, startedBy, said, task, endedBy, failedOn);
        }

        public Report withFailedOn(@Nullable String failedOn) {
            return new Report(sessionLog, startedBy, said, task, endedBy, failedOn);
        }
    }

    /** The brief for a session that has just been compacted, or empty for every other report. */
    public String record(String taskId, SessionProbe.State state, Report report) {
        signs.record(taskId, continued(blocked(state, report.said()), report.endedBy()), null, report.sessionLog());
        return brief(taskId, report.startedBy(), report.task());
    }

    /** Records the turn as failed on the error the CLI named, and says whether that error is a usage limit. */
    public boolean turnFailed(String taskId, Report report) {
        signs.record(taskId, SessionProbe.State.FAILED, report.failedOn(), report.sessionLog());
        String limit = runtime.rateLimitError();
        return report.failedOn() != null && limit != null && !limit.isBlank()
                && limit.equalsIgnoreCase(report.failedOn().strip());
    }

    public String turnEnded(String taskId, Report report, boolean sentOn, boolean pausedOnBackgroundWork) {
        record(taskId, SessionProbe.State.IDLE, report);
        return turnEnds.answer(taskId, report.task(), sentOn, pausedOnBackgroundWork);
    }

    /**
     * One event, two waits: a session refused a permission cannot go on, one merely quiet may be part-way through
     * something long. The CLI's own wording separates them, and an unrecognised one stays the quieter.
     */
    private SessionProbe.State blocked(SessionProbe.State state, @Nullable String said) {
        // Asked only where an answer could change anything: every start and every turn end says nothing.
        if (state != SessionProbe.State.IDLE || said == null) {
            return state;
        }
        String blocking = runtime.blockingNotification();
        return blocking != null && !blocking.isBlank()
                && said.toLowerCase(java.util.Locale.ROOT).contains(blocking.toLowerCase(java.util.Locale.ROOT))
                ? SessionProbe.State.WAITING : state;
    }

    private SessionProbe.State continued(SessionProbe.State state, @Nullable String endedBy) {
        return state == SessionProbe.State.GONE && endedBy != null
                && runtime.continuingEnds().stream().anyMatch(reason -> reason.equalsIgnoreCase(endedBy.strip()))
                ? SessionProbe.State.IDLE : state;
    }

    /** A compaction drops the brief and the task's facts silently: the rules are named, the facts restated. */
    private String brief(String taskId, @Nullable String startedBy, @Nullable TaskState task) {
        String compacted = runtime.compactedStart();
        if (compacted == null || compacted.isBlank() || startedBy == null
                || !compacted.equalsIgnoreCase(startedBy.strip())) {
            return "";
        }
        return "You are jagt's sub-agent for " + taskId + facts(task) + ". The task is its branch, " + taskId
                + ". In this worktree task_request.md is what"
                + " the task was opened with and task_context.md the instruction standing now: re-read both, and the"
                + " repository's own agent instructions, before doing anything else.\n";
    }

    private static String facts(@Nullable TaskState task) {
        if (task == null) {
            return "";
        }
        String asked = task.report().asks()
                ? "; your open question: " + task.message()
                : present(task.message()) ? "; your last report: " + task.message() : "";
        return (present(task.title()) ? " — " + task.title() : "")
                + (present(task.ticketUrl()) ? " (" + task.ticketUrl() + ")" : "")
                + ". On the board: " + task.status() + asked;
    }

    private static boolean present(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
