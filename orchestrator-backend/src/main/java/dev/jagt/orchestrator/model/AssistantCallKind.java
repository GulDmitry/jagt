package dev.jagt.orchestrator.model;

/**
 * What a metered model call was FOR. Without this split {@code stats} can only say "42 calls, 1.8M tokens",
 * and the one question worth asking — is the spend the once-per-task ticket read or the poll that repeats up
 * to hourly for a day? — has to be guessed from the call count.
 *
 * <p>It is also how the payoff of moving a read to a REST {@code CodeHost} becomes visible: that category's
 * line stops growing while the others keep going.
 */
public enum AssistantCallKind {

    /** Reading the work item behind `do` — once per task. */
    TICKET_READ("ticket read"),
    /** Reading a merge request to `resume` it — rare. */
    MR_READ("merge-request read"),
    /** One review round: the manual `review` and every auto-review poll. */
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
