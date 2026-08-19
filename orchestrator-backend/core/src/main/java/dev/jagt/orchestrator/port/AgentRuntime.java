package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.port.AgentWorktree;
import java.nio.file.Path;

/**
 * The pluggable AI-agent runtime — what jagt spawns per task and how it wires that agent to the
 * orchestrator. One implementation per agent CLI (Claude Code, Codex, …), selected by
 * {@code orchestrator.agent} exactly like the {@code platform} strategies
 * ({@link dev.jagt.orchestrator.port.TerminalDriver} etc.). Everything agent-specific — the launch
 * command, the agent's own MCP config file, the settings that let it run unattended — lives behind this seam
 * so the task flow (create worktree → provision → launch → talk over MCP) stays agent-agnostic.
 *
 * <p>A new agent is therefore one class: implement this, register a config value. If something else has to
 * change to add one, that is the pluggable-by-design invariant leaking and belongs here instead.
 */
public interface AgentRuntime {

    /**
     * The cross-agent system-knowledge file jagt writes into every worktree (the {@code AGENTS.md}
     * convention). A runtime whose CLI insists on another name aliases it in {@link #provisionWorktree} —
     * jagt never writes the same knowledge twice, because two copies drift.
     */
    String SYSTEM_KNOWLEDGE_FILE = "AGENTS.md";

    /** Human-facing agent name (e.g. "Claude") for dashboard/log messages. */
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
     * Writes what this agent needs to run in a fresh worktree: the config that declares jagt's MCP proxy
     * (Claude {@code .mcp.json}, Codex {@code config.toml}, …), whatever lifts its permission prompts (nobody
     * watches the tmux window to answer one), and its alias for {@link #SYSTEM_KNOWLEDGE_FILE}. Called once
     * per task, before the agent starts.
     */
    void provisionWorktree(AgentWorktree worktree);
}
