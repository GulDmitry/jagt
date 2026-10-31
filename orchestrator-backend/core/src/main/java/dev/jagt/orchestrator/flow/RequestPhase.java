package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;

/** The request region's states: no review request, some open, or every one merged. */
public enum RequestPhase {
    NONE, OPEN, MERGED;

    public static RequestPhase of(TaskState task) {
        if (!task.hasReviewRequest()) {
            return NONE;
        }
        return task.requestsMerged() ? MERGED : OPEN;
    }
}
