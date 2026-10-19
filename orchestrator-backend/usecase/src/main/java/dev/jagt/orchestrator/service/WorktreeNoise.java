package dev.jagt.orchestrator.service;

import java.util.Set;

/** What sits in a worktree without being anybody's work. */
public final class WorktreeNoise {

    /** Directories too large or too generated to walk. */
    public static final Set<String> UNWALKED = Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle");

    /** What the OS writes into a directory of its own accord. */
    private static final Set<String> SYSTEM_RESIDUE = Set.of(".DS_Store");

    /** Top-level directories common IDEs write into a project of their own accord. */
    private static final Set<String> IDE_RESIDUE = Set.of(".idea", ".run", ".vscode", ".fleet", ".zed", ".settings");

    private WorktreeNoise() {
    }

    /** Written by an IDE or the OS, never by anybody's work. */
    public static boolean isResidue(String name) {
        return SYSTEM_RESIDUE.contains(name) || IDE_RESIDUE.contains(name);
    }
}
