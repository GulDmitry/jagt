package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.port.AgentWorktree;
import java.nio.file.Path;

/**
 * The pluggable AI-agent runtime — what jagt spawns per task and how it wires that agent to the orchestrator. One
 * implementation per agent CLI, selected by {@code orchestrator.agent}. Everything agent-specific — the launch
 * command, the agent's own MCP config file, the settings that let it run unattended — lives behind this seam so
 * the task flow (create worktree → provision → launch → talk over MCP) stays agent-agnostic.
 *
 * <p>A new agent is therefore one class: implement this, register a config value.
 */
public interface AgentRuntime {

    /**
     * The cross-agent system-knowledge file jagt writes into every worktree (the {@code AGENTS.md}
     * convention). A runtime whose CLI insists on another name aliases it in {@link #provisionWorktree} —
     * jagt never writes the same knowledge twice, because two copies drift.
     */
    String SYSTEM_KNOWLEDGE_FILE = "AGENTS.md";

    /** Human-facing agent name, for messages a human reads. */
    String displayName();

    /**
     * The agent CLI invocation to run inside {@code worktree} — a bare shell command executed with the
     * worktree as the working directory; the terminal driver wraps it (tmux window, exit tail, …). The
     * agent's bootstrap prompt rides inside it.
     */
    String launchCommand(Path worktree, boolean planMode);

    /**
     * Where in {@code worktree} this agent's system knowledge is written. A fresh worktree holds nothing but
     * the checkout, so a regular file already sitting on one of these names is the PROJECT's own — jagt never
     * takes it: the agent would lose the instructions the repository ships, and the next {@code ship} would
     * commit the loss. A runtime whose names are all taken refuses rather than start an agent without the
     * safety rules this file carries.
     */
    Path systemKnowledgeFile(Path worktree);

    /**
     * Writes what this agent needs to run in a fresh worktree: the config that declares jagt's MCP server,
     * whatever lifts its permission prompts (nobody watches the window to answer one), and its alias for
     * {@link #SYSTEM_KNOWLEDGE_FILE}. Called once per task, before the agent starts.
     */
    void provisionWorktree(AgentWorktree worktree);

    /**
     * Epoch millis of the last entry in the session's own record of itself; 0 where this runtime keeps none.
     *
     * <p>Such a record grows only when something actually happened, which is what makes it the one clock that
     * tells a working session from one waiting at a prompt — a measure of terminal output cannot, because a
     * session waiting for an answer keeps repainting.
     */
    long lastSessionActivityMillis(Path worktree);

    /**
     * What this CLI calls a start that follows a COMPACTION — the one start that has lost the brief and needs it
     * again. Blank where a CLI says nothing about why a session started, which briefs nothing rather than
     * briefing every start.
     */
    default String compactedStart() {
        return "";
    }

    /**
     * What this CLI puts in a notification that a session cannot go on without a human — a permission it is
     * being refused, not a prompt merely left idle. One event covers both in at least one CLI, and the word it
     * chooses is the only thing that tells them apart.
     *
     * <p>Blank where a CLI says nothing, and an unrecognised notification stays the quieter of the two: a wait
     * that turns out to be real is found by the same threshold as every other silence, while one invented over
     * a session that was working is what teaches a human to stop reading the board.
     */
    default String blockingNotification() {
        return "";
    }
}
