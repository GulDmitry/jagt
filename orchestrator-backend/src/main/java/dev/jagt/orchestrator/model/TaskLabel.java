package dev.jagt.orchestrator.model;

/** {@code ABC-1 (a1)} — a line with only one of the two cannot be matched against the other surface. */
public final class TaskLabel {

    private TaskLabel() {
    }

    public static String of(String taskId, String alias) {
        return alias == null || alias.isBlank() ? taskId : taskId + " (" + alias + ")";
    }
}
