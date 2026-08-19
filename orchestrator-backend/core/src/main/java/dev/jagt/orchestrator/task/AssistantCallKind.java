package dev.jagt.orchestrator.task;

/**
 * What a metered model call was FOR, so the once-per-task reads can be told apart from the poll that repeats up
 * to hourly.
 */
public enum AssistantCallKind {

    /** Reading the work item behind `do` — once per task. */
    TICKET_READ("ticket read"),
    /** Reading a merge request to `resume` it — rare. */
    MR_READ("merge-request read"),
    /** One review round: the manual `sweep` and every auto-review poll. */
    REVIEW_SWEEP("review sweep"),
    /** Free text mapped to a command (the palette / an unknown console line). */
    COMMAND_MAP("command mapping");

    private final String label;

    AssistantCallKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
