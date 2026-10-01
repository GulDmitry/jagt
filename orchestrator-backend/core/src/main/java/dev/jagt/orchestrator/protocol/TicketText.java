package dev.jagt.orchestrator.protocol;

/** What a work item says, as one text: read once for every reviewer of a round rather than by each of them. */
public final class TicketText {

    public static final Schema SCHEMA = Schema.answer()
            .required("text", "string", null)
            .required("failure", "string", null);

    private TicketText() {
    }
}
