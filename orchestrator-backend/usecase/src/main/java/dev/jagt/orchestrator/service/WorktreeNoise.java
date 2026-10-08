package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.EditorDriver;

import java.util.Set;

/** What sits in a worktree without being anybody's work. */
public final class WorktreeNoise {

    /** Directories too large or too generated to walk. */
    public static final Set<String> UNWALKED = Set.of(".git", "node_modules", "build", "target", "out", "dist", ".gradle");

    /** What the OS writes into a directory of its own accord. */
    private static final Set<String> SYSTEM_RESIDUE = Set.of(".DS_Store");

    private WorktreeNoise() {
    }

    /** Written by the editor or the OS, never by anybody's work. */
    public static boolean isResidue(String name, EditorDriver editor) {
        return SYSTEM_RESIDUE.contains(name) || editor.residue().contains(name);
    }
}
