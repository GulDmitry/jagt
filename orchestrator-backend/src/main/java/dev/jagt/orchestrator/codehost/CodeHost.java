package dev.jagt.orchestrator.codehost;

import dev.jagt.orchestrator.model.MergeRequestRef;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.ReviewFacts;

import java.util.Optional;

/**
 * The pluggable code host (GitLab / GitHub / …) spoken to over its own REST API — a sibling of the
 * {@code AgentRuntime} / {@code TerminalDriver} / {@code UserNotifier} seams, selected by
 * {@code orchestrator.code-host.type}.
 *
 * <p>Why this exists: the mechanical outside work is MECHANICAL (is it approved, is the pipeline green, which
 * discussions are unresolved, does a merge request for this branch exist — all fields in an API, not
 * judgements), yet it used to cost a full headless model call per poll, or an agent following prose. Over REST
 * the same work is free, instant and drift-free. What stays with the models is the JUDGEMENT work: the code,
 * the review replies, ticket distillation.
 *
 * <p>What an implementation may write is EXACTLY ONE thing: the review request of a task branch
 * ({@link #createOrUpdateMergeRequest}), which is the artifact a human then reviews. Never a branch, never a
 * merge, never a comment, never an approval — those either belong to the human's gates ({@code ship},
 * {@code deploy}) or to the agent's own MCP. A `CodeHost` that merges is a bug, not a feature.
 */
public interface CodeHost {

    /** Human-facing host name (e.g. "GitLab") for log lines and the sweep's messages. */
    String displayName();

    /**
     * Whether this host can read that review-request URL — the host is fully configured (base URL + token)
     * AND the URL belongs to it. False means the caller falls back to the paid headless read, so an
     * implementation must never claim a URL it cannot actually fetch (a claimed-but-failing read looks like
     * a deleted merge request to the human).
     */
    boolean supports(String reviewRequestUrl);

    /**
     * One review round over REST. Empty = the read failed (transport, auth, or the request is gone); the
     * caller reports that to the human rather than silently paying for a headless retry.
     */
    Optional<ReviewFacts> readReview(String reviewRequestUrl);

    /**
     * Whether the repository behind that git remote lives on THIS host — the write-side counterpart of
     * {@link #supports}. A remote comes in either shape ({@code git@host:group/proj.git},
     * {@code https://host/group/proj.git}), and only the host part decides: pushing a merge request to the
     * wrong host is not a retryable mistake.
     */
    boolean hostsRepository(String gitRemoteUrl);

    /**
     * Opens the review request for {@code spec}'s pushed branch, or updates the one already open for it —
     * idempotent by the (source, target) pair, because {@code ship} runs again on every review round and a
     * second merge request for one branch is a mess only a human can clean up. Empty = the host refused or
     * could not be reached; the caller must NOT assume a request exists.
     */
    Optional<MergeRequestRef> createOrUpdateMergeRequest(MergeRequestSpec spec);
}
