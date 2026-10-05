package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** One implementation per agent CLI, selected by {@code orchestrator.agent.cli}. */
public interface AgentRuntime {

    /** The cross-agent system-knowledge file written into every worktree; another name is aliased, never copied. */
    String SYSTEM_KNOWLEDGE_FILE = "AGENTS.md";

    String displayName();

    /** A bare shell command to run with {@code worktree} as its working directory, bootstrap prompt inside it. */
    String launchCommand(Path worktree, boolean planMode);

    /**
     * The same, run by a named model. A runtime that cannot be told answers {@link #choosesModel()} false, and a
     * configured model is refused at startup rather than ignored here.
     */
    default String launchCommand(Path worktree, boolean planMode, String model) {
        return launchCommand(worktree, planMode);
    }

    /**
     * The same session started on a brief of its own rather than on the sub-agent bootstrap. A runtime that
     * cannot be told what to start on falls back to that bootstrap, which is wrong for a session owning no task.
     */
    default String launchCommand(Path worktree, boolean planMode, String model, String prompt) {
        return launchCommand(worktree, planMode, model);
    }

    /**
     * The command re-entering the session this worktree last held, for a window jagt revives on its own rather
     * than one a human restarted; the fresh {@link #launchCommand} where the runtime keeps nothing to re-enter.
     */
    default String reviveCommand(Path worktree) {
        return launchCommand(worktree, false);
    }

    /**
     * How long a conversation stays worth continuing: past it the CLI rereads the whole history uncached, so new
     * work goes to a fresh session reading the task's files. EMPTY where a conversation is always continued.
     */
    default Optional<Duration> continuesWithin() {
        return Optional.empty();
    }

    /** Whether this runtime can be told which model to run. */
    default boolean choosesModel() {
        return false;
    }

    /** The log a session in {@code worktree} is appending to, where this runtime keeps one jagt can find. */
    default Optional<Path> sessionLogOf(Path worktree) {
        return Optional.empty();
    }

    /**
     * What a human typed into the session in {@code worktree}, oldest first, with this runtime's own launch prompts
     * and every {@code typedByJagt} cut out. Empty where the record could not be read, which is not "said nothing".
     */
    default Optional<List<String>> humanSaid(Path worktree, Set<String> typedByJagt) {
        return Optional.empty();
    }

    /** Whether {@code screen}, a session's window as shown, holds input the human has typed and not yet sent. */
    default boolean holdsDraft(String screen) {
        return false;
    }

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

    /** The answer that keeps this CLI's turn going with {@code reason} as its next input; blank where it cannot. */
    default String refusedTurnEnd(String reason) {
        return "";
    }

    /** The answer that shows {@code line} to the human and not to the model; blank where this CLI cannot. */
    default String toldTheHuman(String line) {
        return "";
    }
}
