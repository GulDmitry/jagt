package dev.jagt.orchestrator.assistant;

import dev.jagt.orchestrator.model.ReviewFacts;
import dev.jagt.orchestrator.model.TokenUsage;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A one-shot, hard-formatted headless Claude call for the Master side: spin up a stripped session
 * that inherits ONLY the human's own MCP servers (no hardcoded servers/paths in jagt), ask one
 * question, force a deterministic JSON answer, done. Used to read a Jira ticket before a worktree
 * exists (so the sub-agent can't do it yet). Empty result = assistant unavailable / call failed —
 * callers fall back to explicit input.
 */
public interface MasterAssistant {

    /**
     * Facts distilled from a work item in whatever issue tracker the session's MCP exposes. The input may
     * be an issue key OR a URL to it; {@code key} is read back from the item, NOT parsed from the URL.
     * {@code url} may be empty when the tracker exposes none. {@code exists=false} means it could not be read.
     */
    record TicketFacts(boolean exists, String key, String title, String trackerProject, List<String> labels,
                       String url) {
    }

    /**
     * Free text mapped onto ONE command of the console grammar — a PROPOSAL, never an execution: the caller
     * validates it against the same gates a typed command hits and runs it itself. {@code command} empty (or
     * "none") means nothing matched, and {@code reason} then says why in one line for the human.
     */
    record CommandProposal(String command, String task, String ticket, String reason) {
    }

    /** Facts about an existing merge request. {@code exists=false} means the URL resolved to nothing. */
    record MergeRequestFacts(boolean exists, String sourceBranch, String projectPath, String title) {
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

    /** Reads a work item given an issue KEY or a URL to it (any tracker); returns its canonical key + facts. */
    Answer<TicketFacts> readTicket(String ticketRef);

    /** Reads an MR by URL so `resume` can recover its source branch (= the task) and project. */
    Answer<MergeRequestFacts> readMergeRequest(String mrUrl);

    /** The `review` sweep: pipeline state + unresolved comments of an MR (a slow, multi-call read). */
    Answer<ReviewFacts> readReview(String mrUrl);

    /**
     * Maps a free-text request ("залей ту задачу с логином") onto one grammar command. {@code context} is the
     * prompt-ready list of commands and current tasks the caller wants considered — the port knows nothing
     * about the grammar, so adding a command never touches this interface. Reads NOTHING from the outside,
     * so implementations should run stripped of MCP entirely: cheaper, and it cannot call a tool by accident.
     */
    Answer<CommandProposal> mapCommand(String text, String context);
}
