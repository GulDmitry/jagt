package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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
        List<StatusChange> history
) {

    public static FinishedTask of(String id, TaskState task, long finishedAt) {
        return new FinishedTask(id, task.alias(), task.title(), task.projects(), task.ticketUrl(), finishedAt,
                rounds(task), task.pipelineStatus(), task.totalUsage().total(), task.history());
    }

    private static int rounds(TaskState task) {
        return (int) task.history().stream()
                .filter(step -> step.status() == dev.jagt.orchestrator.flow.TaskStatus.CI_POLLING).count();
    }

    /** From the first step it took to the moment it was retired; zero where nothing was ever recorded. */
    public long tookMillis() {
        return history.isEmpty() ? 0 : finishedAt - history.get(0).at();
    }
}
