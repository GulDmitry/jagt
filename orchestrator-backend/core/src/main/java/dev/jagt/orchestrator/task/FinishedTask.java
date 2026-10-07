package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jagt.orchestrator.flow.TaskStatus;

import java.util.List;

/**
 * What a task left behind after `done` removed it. The status log is kept whole rather than summarised: whatever
 * is later asked of finished work — how long a phase took, how many rounds it cost — is computable from it, and a
 * number this record did not think to store cannot be recovered.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinishedTask(
        String id,
        String alias,
        String title,
        List<String> projects,
        String ticketUrl,
        long finishedAt,
        // How many times the task went out for review: every entry into CI_POLLING is one round.
        int rounds,
        // The host's last word on the checks, or null if nothing ever read one.
        String checks,
        long tokens,
        List<StatusChange> history,
        // What the unattended reviewer concluded last, or null where nothing judged this task.
        String masterVerdict
) {

    public static FinishedTask of(String id, TaskState task, long finishedAt) {
        return of(id, task, finishedAt, null);
    }

    public static FinishedTask of(String id, TaskState task, long finishedAt, String masterVerdict) {
        return new FinishedTask(id, task.alias(), task.title(), task.projects(), task.ticketUrl(), finishedAt,
                rounds(task), task.pipelineStatus(), task.totalUsage().total(), task.history(), masterVerdict);
    }

    /** Whether the reviewer read this task at all; every comparison below is over these and no others. */
    public boolean judged() {
        return masterVerdict != null && !masterVerdict.isBlank();
    }

    /** The one word that means it passed; every other ending was a round going back. */
    public boolean passed() {
        return "ready".equalsIgnoreCase(masterVerdict == null ? "" : masterVerdict.strip());
    }

    /** A question judged nothing, so whatever you did after it cannot disagree with it. */
    public boolean asked() {
        return "question".equalsIgnoreCase(masterVerdict == null ? "" : masterVerdict.strip());
    }

    private static int rounds(TaskState task) {
        return (int) task.history().stream()
                .filter(step -> step.status() == TaskStatus.CI_POLLING).count();
    }

    /** Who opened it, which is who chose its repository; null for a task opened before origins were stamped. */
    public ActionOrigin openedBy() {
        return history.isEmpty() ? null : history.get(0).origin();
    }

    /** Whether the last round judged came back before the task first reached {@code status}. */
    public boolean judgedBefore(TaskStatus status) {
        int judged = -1;
        int reachedAt = -1;
        for (int i = 0; i < history.size(); i++) {
            TaskStatus step = history.get(i).status();
            if (step == TaskStatus.REVIEW_PENDING) {
                judged = i;
            }
            if (step == status && reachedAt < 0) {
                reachedAt = i;
            }
        }
        return reachedAt >= 0 && judged < reachedAt;
    }

    /** Whether the task ever stood in this status, which is how a claim about it is checked against what it did. */
    public boolean reached(TaskStatus status) {
        return history.stream().anyMatch(step -> step.status() == status);
    }

    /**
     * Whether where this task was done can be LEARNED from. A human chose the repository, or the work reached
     * the shared branch from it — a routing nothing confirmed is the router's own guess coming back as a lesson.
     */
    public boolean routingWorthLearningFrom() {
        return projects.size() == 1
                && (openedBy() != ActionOrigin.TRACKER || reached(TaskStatus.DEPLOYED));
    }

    /** From the first step it took to the moment it was retired; zero where nothing was ever recorded. */
    public long tookMillis() {
        return history.isEmpty() ? 0 : finishedAt - history.get(0).at();
    }
}
