package dev.jagt.orchestrator.service;

import java.util.Set;

/** What sits in a worktree without being anybody's work. */
public final class WorktreeNoise {

    /** Directories too large or too generated to walk. */
    public static final Set<String> UNWALKED = Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle");

    /** What the OS writes into a directory of its own accord. */
    static final Set<String> SYSTEM_RESIDUE = Set.of(".DS_Store");

    private WorktreeNoise() {
    }
}
