package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.AgentRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.OptionalLong;

public abstract class AbstractAgentRuntime implements AgentRuntime {

    @Override
    public Path systemKnowledgeFile(Path worktree) {
        Path file = worktree.resolve(SYSTEM_KNOWLEDGE_FILE);
        if (broughtByCheckout(file)) {
            throw new IllegalStateException("Cannot brief the agent: " + SYSTEM_KNOWLEDGE_FILE
                    + " belongs to the repository, and this agent reads no other name.");
        }
        return file;
    }

    @Override
    public final void provisionWorktree(AgentWorktree worktree) {
        wireAgent(worktree);
    }

    /** Answering nothing degrades to the signs jagt can take itself; a guess would be read as certainty. */
    @Override
    public OptionalLong lastSessionActivity(Path worktree) {
        return OptionalLong.empty();
    }

    /** A fresh worktree holds nothing but the checkout; jagt's own plumbing is written as links. */
    protected static boolean broughtByCheckout(Path file) {
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file);
    }

    /**
     * The per-agent half of provisioning: the MCP config (HTTP endpoint or the stdio bridge), unattended-run
     * permissions, and an alias for {@link #SYSTEM_KNOWLEDGE_FILE} if this CLI reads another filename.
     */
    protected abstract void wireAgent(AgentWorktree worktree);

    /**
     * For a CLI that cannot talk to a remote MCP server and can only SPAWN one. Call it ONLY from a runtime
     * that needs it — an agent speaking HTTP wants no proxy process, and linking one for everybody is what put
     * Node among jagt's prerequisites.
     */
    protected static void linkStdioProxy(AgentWorktree worktree) {
        symlink(worktree.path().resolve("mcp_client.js"),
                worktree.orchestratorRoot().resolve("mcp_client.js"));
    }

    protected static String shellQuote(String s) {
        return "'" + (s == null ? "" : s).replace("'", "'\\''") + "'";
    }

    /** Replaces an existing link, so re-provisioning a worktree is idempotent. */
    protected static void symlink(Path link, Path target) {
        if (broughtByCheckout(link)) {
            throw new IllegalStateException("Cannot wire the agent: the repository ships its own "
                    + link.getFileName() + ", and jagt needs that name.");
        }
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
