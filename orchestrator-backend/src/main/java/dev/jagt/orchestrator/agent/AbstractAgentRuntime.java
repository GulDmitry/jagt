package dev.jagt.orchestrator.agent;

/**
 * Common agent-runtime logic, so concrete runtimes only fill in what actually varies. Thin today (the
 * shared shell-quoting used to build a launch command); the worktree-provisioning TEMPLATE lands here
 * next, with per-agent hooks (system-knowledge filename, MCP/config writing) overridden by subclasses.
 */
public abstract class AbstractAgentRuntime implements AgentRuntime {

    /** POSIX single-quote a shell argument (the agent's bootstrap prompt in {@link #launchCommand}). */
    protected static String shellQuote(String s) {
        return "'" + (s == null ? "" : s).replace("'", "'\\''") + "'";
    }
}
