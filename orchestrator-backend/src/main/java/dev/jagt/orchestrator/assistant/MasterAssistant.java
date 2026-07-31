package dev.jawo.orchestrator.assistant;

import java.util.List;
import java.util.Optional;

/**
 * A one-shot, hard-formatted headless Claude call for the Master side: spin up a stripped session
 * that inherits ONLY the human's own MCP servers (no hardcoded servers/paths in jawo), ask one
 * question, force a deterministic JSON answer, done. Used to read a Jira ticket before a worktree
 * exists (so the sub-agent can't do it yet). Empty result = assistant unavailable / call failed —
 * callers fall back to explicit input.
 */
public interface MasterAssistant {

    /**
     * Facts distilled from a work item. The input may be an issue KEY or a URL to it (Jira or any
     * tracker); {@code key} is the canonical id the assistant read back (jawo names the branch/worktree
     * by it — it is NOT parsed from the URL). {@code exists=false} means it could not be read.
     */
    record TicketFacts(boolean exists, String key, String title, String jiraProject, List<String> labels) {
    }

    /** Facts about an existing merge request. {@code exists=false} means the URL resolved to nothing. */
    record MergeRequestFacts(boolean exists, String sourceBranch, String projectPath, String title) {
    }

    /**
     * A review sweep of an MR: latest pipeline result and every UNRESOLVED discussion note (bots +
     * humans), each pre-formatted as one line for relaying to the agent.
     */
    record ReviewFacts(boolean exists, String pipelineStatus, List<String> comments) {
    }

    /** Reads a work item given an issue KEY or a URL to it (any tracker); returns its canonical key + facts. */
    Optional<TicketFacts> readTicket(String ticketRef);

    /** Reads an MR by URL so `resume` can recover its source branch (= the task) and project. */
    Optional<MergeRequestFacts> readMergeRequest(String mrUrl);

    /** The `review` sweep: pipeline state + unresolved comments of an MR (a slow, multi-call read). */
    Optional<ReviewFacts> readReview(String mrUrl);
}
