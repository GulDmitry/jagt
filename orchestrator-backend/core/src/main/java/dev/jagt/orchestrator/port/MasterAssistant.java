package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * One question to a model, asked before any worktree exists. An implementation spends money, so it is metered.
 * Empty result = unavailable or failed.
 */
public interface MasterAssistant {

    /**
     * Free text mapped onto ONE command of the console grammar — a PROPOSAL, never an execution. {@code command}
     * empty (or "none") means nothing matched, and {@code reason} then says why in one line.
     */
    record CommandProposal(String command, String task, String ticket, String reason) {
    }

    /**
     * One answer plus what it cost. The usage is reported even when {@code facts} is empty — a call that failed was
     * paid for all the same. The cost rides in the RETURN so no implementation can forget to meter.
     */
    record Answer<T>(Optional<T> facts, TokenUsage usage) {

        /** Never happened, so it cost nothing. */
        public static <T> Answer<T> unavailable() {
            return new Answer<>(Optional.empty(), TokenUsage.NONE);
        }

        /** Shapes the facts while carrying the cost through untouched. */
        public <R> Answer<R> map(Function<? super T, ? extends R> mapper) {
            return new Answer<>(facts.map(mapper), usage);
        }
    }

    /** Reads a work item given an issue KEY or a URL to it in any tracker at all. */
    Answer<TicketFacts> readTicket(String ticketRef);

    /** Reads a review request by URL. */
    Answer<MergeRequestFacts> readMergeRequest(String mrUrl);

    /** Checks state plus unresolved comments of a review request; a slow, multi-call read. */
    Answer<ReviewFacts> readReview(String mrUrl);

    /**
     * The MCP servers this assistant cannot use right now, each as {@code name (status)}. Empty {@code Optional} =
     * could not be established; an empty LIST means nothing is down.
     */
    Optional<List<String>> brokenMcpServers();

    /**
     * Maps a free-text request onto one grammar command. {@code context} is the prompt-ready list of commands and
     * current tasks to consider. Reads NOTHING from the outside, so an implementation runs with no tools at all.
     */
    Answer<CommandProposal> mapCommand(String text, String context);
}
