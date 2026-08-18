package dev.jagt.orchestrator.assistant;

import dev.jagt.orchestrator.model.MergeRequestFacts;
import dev.jagt.orchestrator.model.ReviewFacts;
import dev.jagt.orchestrator.model.TicketFacts;
import dev.jagt.orchestrator.model.TokenUsage;

import java.util.Optional;
import java.util.function.Function;

/**
 * A one-shot, hard-formatted headless Claude call for the Master side: spin up a stripped session
 * that inherits ONLY the human's own MCP servers (no hardcoded servers/paths in jagt), ask one
 * question, force a deterministic JSON answer, done. Reads a ticket before a worktree
 * exists (so the sub-agent can't do it yet). Empty result = assistant unavailable / call failed —
 * callers fall back to explicit input.
 */
public interface MasterAssistant {

    /**
     * Free text mapped onto ONE command of the console grammar — a PROPOSAL, never an execution: the caller
     * validates it against the same gates a typed command hits and runs it itself. {@code command} empty (or
     * "none") means nothing matched, and {@code reason} then says why in one line for the human.
     */
    record CommandProposal(String command, String task, String ticket, String reason) {
    }

    /**
     * One answer plus what it cost. The usage is reported even when {@code facts} is empty — a call that
     * failed or came back unusable was paid for all the same, and the caller books it either way. Keeping
     * the cost in the RETURN keeps this port about reading: no implementation can forget to meter, and the
     * caller (the only one who knows whether a task exists yet) decides what the spend is attributed to.
     */
    record Answer<T>(Optional<T> facts, TokenUsage usage) {

        /** Never happened, so it cost nothing (a blank ref, a non-http url — no process was spawned). */
        public static <T> Answer<T> unavailable() {
            return new Answer<>(Optional.empty(), TokenUsage.NONE);
        }

        /** Shapes the facts while carrying the cost through untouched. */
        <R> Answer<R> map(Function<? super T, ? extends R> mapper) {
            return new Answer<>(facts.map(mapper), usage);
        }
    }

    /**
     * Reads a work item given an issue KEY or a URL to it in any tracker at all — following the URL is what
     * this read is for, and what no configured API can do.
     */
    Answer<TicketFacts> readTicket(String ticketRef);

    /**
     * Reads a review request by URL so `resume` can recover the branch it is built on and the branch it
     * targets. The FALLBACK read: it follows a URL no configured host claims, which is what keeps it here.
     */
    Answer<MergeRequestFacts> readMergeRequest(String mrUrl);

    /** The sweep: checks state + unresolved comments of a review request (a slow, multi-call read). */
    Answer<ReviewFacts> readReview(String mrUrl);

    /**
     * Maps a free-text request ("push the login one for review") onto one grammar command. {@code context} is the
     * prompt-ready list of commands and current tasks the caller wants considered — the port knows nothing
     * about the grammar, so adding a command never touches this interface. Reads NOTHING from the outside,
     * so implementations should run stripped of MCP entirely: cheaper, and it cannot call a tool by accident.
     */
    Answer<CommandProposal> mapCommand(String text, String context);
}
