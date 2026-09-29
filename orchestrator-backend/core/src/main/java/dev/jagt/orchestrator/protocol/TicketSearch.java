package dev.jagt.orchestrator.protocol;

/**
 * What an answer listing work items has to hold. Keys and nothing else: what an item IS comes from its own read,
 * so the list that names one may not also describe it.
 */
public final class TicketSearch {

    public static final Schema SCHEMA = Schema.answer()
            .required("failure", "string", null)
            .requiredTexts("keys", null);

    private TicketSearch() {
    }
}
