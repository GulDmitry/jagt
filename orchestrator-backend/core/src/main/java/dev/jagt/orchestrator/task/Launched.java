package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

/**
 * What a launch or a resume answers: the task it now has, null when it made none, and the sentence to show a human.
 * Declining is an ordinary answer rather than a failure, so a surface must not read a null task as success.
 */
public record Launched(@Nullable String taskId, String message) {

    public static final String NOTHING_TO_DO = "error: no ticket and nothing to do";

    public static Launched refused(String message) {
        return new Launched(null, message);
    }

    public static Launched created(String taskId, String message) {
        return new Launched(taskId, message);
    }

    public boolean created() {
        return taskId != null;
    }
}
