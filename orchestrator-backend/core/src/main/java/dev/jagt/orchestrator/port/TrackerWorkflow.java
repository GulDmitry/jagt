package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TicketFacts;

/**
 * Which stages of a tracker's workflow jagt acts on: what it may start unasked, and what closes the task working
 * on it. Not a client — the reading stays the assistant's and jagt holds no credential — but the vocabulary those
 * reads are judged against, and that vocabulary is a tracker's own rather than jagt's.
 */
public interface TrackerWorkflow {

    /** The {@code orchestrator.intake.tracker} value selecting this one. */
    String id();

    /** What the assistant is asked to find, phrased the way THIS tracker is searched. */
    String candidateQuery();

    /**
     * Whether an item's own facts make it one a machine may start. Decided on the facts read back off the item
     * rather than on whatever listed it; a status nobody could read is the caller's to tell apart from one that
     * simply does not match.
     */
    boolean startsWork(TicketFacts facts);

    /** Whether the item has reached the stage that closes the task working on it. */
    boolean closesWork(TicketFacts facts);
}
