package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import org.jspecify.annotations.Nullable;

/** What the board holds about a task that a session which lost its context cannot know. */
public final class TaskFacts {

    private TaskFacts() {
    }

    public static String of(@Nullable TaskState task) {
        if (task == null) {
            return "";
        }
        String asked = task.report().asks()
                ? "; your open question: " + task.message()
                : present(task.message()) ? "; your last report: " + task.message() : "";
        return (present(task.title()) ? " \u2014 " + task.title() : "")
                + (present(task.ticketUrl()) ? " (" + task.ticketUrl() + ")" : "")
                + ". On the board: " + task.status() + asked;
    }

    private static boolean present(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
