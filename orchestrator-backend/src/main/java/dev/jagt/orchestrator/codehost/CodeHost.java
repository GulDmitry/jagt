package dev.jagt.orchestrator.codehost;

import dev.jagt.orchestrator.model.MergeRequestRef;
import dev.jagt.orchestrator.model.MergeRequestSpec;
import dev.jagt.orchestrator.model.ReviewFacts;

import java.util.Optional;

/**
 * A code host over its own REST API, selected by {@code orchestrator.code-host.type}.
 *
 * <p>An implementation may write EXACTLY ONE thing: {@link #createOrUpdateMergeRequest}. Never a branch, a
 * merge, a comment or an approval — one that merges is a bug, not a feature.
 */
public interface CodeHost {

    /** Human-facing host name (e.g. "GitLab") for log lines and the sweep's messages. */
    String displayName();

    /**
     * Whether this host is fully configured (base URL + token) AND the URL belongs to it. Never claim a URL
     * that cannot actually be fetched: a claimed-but-failing read is indistinguishable from a deleted request.
     */
    boolean supports(String reviewRequestUrl);

    /** One review round. Empty = the read failed: transport, auth, or the request is gone. */
    Optional<ReviewFacts> readReview(String reviewRequestUrl);

    /**
     * Whether the repository behind that remote lives on THIS host — the write-side counterpart of
     * {@link #supports}. Either remote shape must be accepted, and only the host part may decide.
     */
    boolean hostsRepository(String gitRemoteUrl);

    /**
     * Opens the review request for {@code spec}'s pushed branch, or updates the one already open for it:
     * idempotent by the (source, target) pair. Empty = nothing was opened, and no request may be assumed.
     */
    Optional<MergeRequestRef> createOrUpdateMergeRequest(MergeRequestSpec spec);
}
