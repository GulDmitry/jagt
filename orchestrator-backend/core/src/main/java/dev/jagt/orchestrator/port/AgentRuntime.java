package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

/** One implementation per agent CLI, selected by {@code orchestrator.agent.cli}. */
public interface AgentRuntime {

    /** The cross-agent system-knowledge file written into every worktree; another name is aliased, never copied. */
    String SYSTEM_KNOWLEDGE_FILE = "AGENTS.md";

    String displayName();

    /** A bare shell command to run with {@code worktree} as its working directory, bootstrap prompt inside it. */
    String launchCommand(Path worktree, boolean planMode);

    /** Where this agent's system knowledge goes; a name the checkout already uses is the project's own, never taken. */
    Path systemKnowledgeFile(Path worktree);

    /** Writes what this agent needs to run in a fresh worktree, once per task, before the agent starts. */
    void provisionWorktree(AgentWorktree worktree);

    /** Undoes what {@link #provisionWorktree} wrote OUTSIDE the worktree; the worktree itself is deleted for it. */
    default void retireWorktree(Path worktree) {
    }

    /** Worktree-relative paths {@link #provisionWorktree} writes, so a commit of the agent's work leaves them out. */
    default List<String> generatedFiles() {
        return List.of();
    }

    /** Worktree-relative paths of this agent's own files, in git exclude syntax: a directory ends with {@code /}. */
    default List<String> statusExclusions() {
        return List.of();
    }

    /** Epoch millis of the last entry in the session's own record; 0 while it holds none, EMPTY where there is none. */
    OptionalLong lastSessionActivity(Path worktree);

    /** What this CLI calls a start that follows a COMPACTION, the one start that lost the brief; blank when silent. */
    default String compactedStart() {
        return "";
    }

    /** What this CLI puts in a notification that a session cannot go on without a human; blank when it says nothing. */
    default String blockingNotification() {
        return "";
    }
}
