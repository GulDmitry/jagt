package dev.jagt.orchestrator.assistant;

import java.util.List;
import java.util.Optional;

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

    /** Facts about an existing merge request. {@code exists=false} means the URL resolved to nothing. */
    record MergeRequestFacts(boolean exists, String sourceBranch, String projectPath, String title) {
    }

    /**
     * A review sweep of an MR: whether it is APPROVED, its latest pipeline result, and every UNRESOLVED
     * discussion note (bots + humans), each pre-formatted as one line for relaying to the agent.
     */
    record ReviewFacts(boolean exists, boolean approved, String pipelineStatus, List<String> comments) {
    }

    /** Reads a work item given an issue KEY or a URL to it (any tracker); returns its canonical key + facts. */
    Optional<TicketFacts> readTicket(String ticketRef);

    /** Reads an MR by URL so `resume` can recover its source branch (= the task) and project. */
    Optional<MergeRequestFacts> readMergeRequest(String mrUrl);

    /** The `review` sweep: pipeline state + unresolved comments of an MR (a slow, multi-call read). */
    Optional<ReviewFacts> readReview(String mrUrl);
}
