package dev.jagt.orchestrator.service;

import java.util.Set;

/** What sits in a worktree without being anybody's work. */
public final class WorktreeNoise {

    /** Directories too large or too generated to walk. */
    public static final Set<String> UNWALKED = Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle",
            ".idea");

    /** What an editor or the OS writes into a directory of its own accord. */
    static final Set<String> EDITOR_RESIDUE = Set.of(".idea", ".vscode", ".fleet", ".DS_Store");

    /** What an IDE writes into a worktree by itself, so finding it says nothing about whose the directory is. */
    public static final Set<String> IDE_FILES = Set.of(".idea", ".run");

    private WorktreeNoise() {
    }
}
