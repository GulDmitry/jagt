package dev.jagt.orchestrator.task;

/** {@code ABC-1 (a1)}, or the id alone when there is no alias. */
public final class TaskLabel {

    private TaskLabel() {
    }

    public static String of(String taskId, String alias) {
        return alias == null || alias.isBlank() ? taskId : taskId + " (" + alias + ")";
    }
}
