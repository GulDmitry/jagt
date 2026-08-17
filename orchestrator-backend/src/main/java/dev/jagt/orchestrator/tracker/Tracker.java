package dev.jagt.orchestrator.tracker;

import dev.jagt.orchestrator.model.TicketFacts;

import java.util.Optional;

/**
 * An issue tracker over its own API, selected by {@code orchestrator.tracker.type}. It only ever READS: a
 * tracker that transitions, comments or assigns is a bug — an issue's state is the human's to move.
 */
public interface Tracker {

    /** Human-facing tracker name (e.g. "Jira") for log lines. */
    String displayName();

    /**
     * Whether this tracker is fully configured AND can actually fetch {@code ticketRef} — an issue key of its
     * own shape, or a URL of its own. Never claim a reference that cannot be fetched: a claimed-but-failing
     * read is indistinguishable from a ticket that does not exist, and the caller stops rather than paying a
     * model to try again.
     */
    boolean supports(String ticketRef);

    /**
     * The item's facts. A read this tracker could not complete answers {@code exists=false} rather than
     * nothing: a launch must be able to tell "no such ticket" from "no tracker asked", because the second one
     * creates the task from a bare key and the first one must refuse. Empty is reserved for a reference this
     * tracker does not claim at all.
     */
    Optional<TicketFacts> readTicket(String ticketRef);
}
