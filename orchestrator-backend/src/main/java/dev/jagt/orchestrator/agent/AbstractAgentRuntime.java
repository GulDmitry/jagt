package dev.jagt.orchestrator.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common agent-runtime logic, so concrete runtimes only fill in what actually varies: provisioning is a
 * TEMPLATE here (the agent-agnostic MCP proxy is linked the same way for everyone) with one hook for the
 * per-agent artifacts.
 */
public abstract class AbstractAgentRuntime implements AgentRuntime {

    @Override
    public final void provisionWorktree(AgentWorktree worktree) {
        // mcp_client.js is a STANDARD stdio↔HTTP MCP proxy — every agent links the same script, and only the
        // config that declares it differs. Keeping the link here is what stops that from being duplicated.
        symlink(worktree.path().resolve("mcp_client.js"),
                worktree.orchestratorRoot().resolve("mcp_client.js"));
        wireAgent(worktree);
    }

    /**
     * The per-agent half of provisioning: the MCP config declaring the proxy, unattended-run permissions, and
     * an alias for {@link #SYSTEM_KNOWLEDGE_FILE} if this CLI reads another filename.
     */
    protected abstract void wireAgent(AgentWorktree worktree);

    /** POSIX single-quote a shell argument (the agent's bootstrap prompt in {@link #launchCommand}). */
    protected static String shellQuote(String s) {
        return "'" + (s == null ? "" : s).replace("'", "'\\''") + "'";
    }

    /** Replaces an existing link, so re-provisioning a worktree is idempotent. */
    protected static void symlink(Path link, Path target) {
        try {
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create symlink " + link + " -> " + target, e);
        }
    }

    protected static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
