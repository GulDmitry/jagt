package dev.jagt.orchestrator.model;

/**
 * What a metered model call was FOR, so {@code stats} can separate the once-per-task ticket read from the poll
 * that repeats up to hourly — and so the payoff of moving a read to a REST {@code CodeHost} is visible as one
 * category's line stopping while the others grow.
 */
public enum AssistantCallKind {

    /** Reading the work item behind `do` — once per task. */
    TICKET_READ("ticket read"),
    /** Reading a merge request to `resume` it — rare. */
    MR_READ("merge-request read"),
    /** One review round: the manual `sweep` and every auto-review poll. */
    REVIEW_SWEEP("review sweep"),
    /** Free text mapped to a command (the palette / an unknown console line) — cheapest call jagt makes. */
    COMMAND_MAP("command mapping");

    private final String label;

    AssistantCallKind(String label) {
        this.label = label;
    }

    /** For the stats table — the enum name would shout in a column of numbers. */
    public String label() {
        return label;
    }
}
