package dev.jagt.orchestrator.agent;

/**
 * The pluggable AI-agent runtime — what jagt spawns per task and how it wires that agent to the
 * orchestrator. One implementation per agent CLI (Claude Code today; Codex/Gemini/… later), selected by
 * {@code orchestrator.agent} exactly like the {@code platform} strategies
 * ({@link dev.jagt.orchestrator.platform.TerminalDriver} etc.). Everything agent-specific — the launch
 * command, and (later) the worktree config + MCP callback wiring — lives behind this seam so the task
 * flow stays agent-agnostic.
 *
 * <p>Grows by milestone: launch first; worktree provisioning + MCP artifacts land with the provisioner.
 */
public interface AgentRuntime {

    /** Human-facing agent name (e.g. "Claude") for dashboard/log messages. */
    String displayName();

    /**
     * The agent CLI invocation to run inside a worktree window — a bare shell command; the terminal
     * driver wraps it (tmux window, exit tail, …). The agent's bootstrap prompt rides inside it.
     */
    String launchCommand(boolean planMode);
}
