package dev.jagt.orchestrator.flow;

import org.jspecify.annotations.Nullable;

/** The repo region's states: nothing asked, or a repository the session asked for and none attached yet. */
public enum RepoPhase {
    NONE, ASKED;

    public static RepoPhase of(@Nullable String repoRequest) {
        return repoRequest == null ? NONE : ASKED;
    }
}
