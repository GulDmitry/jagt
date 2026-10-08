package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TicketFacts;

import java.util.List;

/** Reads work items given an issue KEY or a URL to one in any tracker at all. Every call is booked as it returns. */
public interface TrackerAssistant {

    default Answer<TicketFacts> readTicket(String ticketRef) {
        return readTicket(ticketRef, List.of());
    }

    /**
     * The same read, told what was wrong with the last answer. Asking the identical question again is how a
     * retry gets the identical answer, so every correction the caller found rides with it.
     */
    Answer<TicketFacts> readTicket(String ticketRef, List<String> corrections);

    /** The item's summary, description, acceptance criteria and comments, verbatim. */
    Answer<String> readTicketText(String ticketRef);

    /**
     * Lists the keys of the work items matching {@code query}, phrased by the tracker's own workflow. An empty
     * LIST means the tracker answered and nothing matched; an empty {@code Optional} means nobody got to ask.
     */
    Answer<List<String>> findCandidates(String query);
}
