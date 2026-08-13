package dev.jagt.orchestrator.codehost;

import dev.jagt.orchestrator.model.ReviewFacts;

import java.util.Optional;

/**
 * The pluggable code host (GitLab / GitHub / …) read over its own REST API — a sibling of the
 * {@code AgentRuntime} / {@code TerminalDriver} / {@code UserNotifier} seams, selected by
 * {@code orchestrator.code-host.type}.
 *
 * <p>Why this exists: the review sweep is MECHANICAL (is it approved, is the pipeline green, which
 * discussions are unresolved — all fields in an API, not judgements), yet it used to cost a full headless
 * model call per poll, which dominates what a task spends. Over REST the same sweep is free, instant and
 * drift-free. What stays with the models is the JUDGEMENT work: the code, the review replies, ticket
 * distillation.
 *
 * <p>This does NOT weaken the human-in-the-loop rule — it only changes who READS. Nothing here writes to a
 * host: no push, no merge, no comment. Implementations must stay read-only.
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
}
