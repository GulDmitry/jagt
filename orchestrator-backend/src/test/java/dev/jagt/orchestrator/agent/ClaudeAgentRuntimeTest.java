package dev.jagt.orchestrator.agent;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeAgentRuntimeTest {

    private static ClaudeAgentRuntime runtime(String command, String prompt) {
        return new ClaudeAgentRuntime(OrchestratorProperties.defaults()
                .withClaudeCommand(command).withAgentPrompt(prompt),
                new McpEndpoint("http://localhost:8290/mcp"));
    }

    @Test
    void launchesTheClaudeCliWithTheBootstrapPromptQuoted() {
        assertThat(runtime("claude", "Read AGENTS.md and work").launchCommand(Path.of("/wt"), false))
                .isEqualTo("claude 'Read AGENTS.md and work'");
    }

    @Test
    void addsPlanModeFlagWhenRequested() {
        assertThat(runtime("claude", "go").launchCommand(Path.of("/wt"), true))
                .isEqualTo("claude --permission-mode plan 'go'");
    }

    @Test
    void escapesSingleQuotesInThePrompt() {
        assertThat(runtime("claude", "it's fine").launchCommand(Path.of("/wt"), false))
                .isEqualTo("claude 'it'\\''s fine'");
    }

    /**
     * Claude Code speaks MCP over HTTP, so the worktree gets an endpoint and a header — not a proxy process.
     * The header value IS the worktree path: that is how the backend answers "which task is calling?", and it
     * is exactly what the old Node bridge computed at runtime as {@code process.cwd()}.
     */
    @Test
    void declaresTheJagtMcpServerOverHttpWithThisWorktreeAsTheCaller(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("claude", "go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        var config = new JsonMapper().readTree(Files.readString(worktree.resolve(".mcp.json")))
                .path("mcpServers").path("jagt-orchestrator");
        assertThat(config.path("type").asString("")).isEqualTo("http");
        assertThat(config.path("url").asString("")).isEqualTo("http://localhost:8290/mcp");
        assertThat(config.path("headers").path("X-Working-Directory").asString(""))
                .isEqualTo(worktree.toAbsolutePath().normalize().toString());
    }

    /** No bridge, no Node: an agent that can reach the endpoint itself must not get a proxy in its worktree. */
    @Test
    void leavesNoStdioProxyInTheWorktree(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("claude", "go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(worktree.resolve("mcp_client.js")).doesNotExist();
    }

    @Test
    void aliasesClaudeMdToTheSharedSystemKnowledgeFileSoOnlyOneCopyExists(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("claude", "go").provisionWorktree(new AgentWorktree(worktree, root, null, null));
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "system knowledge");

        assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("system knowledge");
    }

    @Test
    void pinsConfiguredOutputStyleInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson("sob-ai:Engineer", null);

        String style = new JsonMapper().readTree(json).path("outputStyle").asString(null);

        assertThat(style).isEqualTo("sob-ai:Engineer");
    }

    @Test
    void preApprovesTheJagtToolsAndTheAgentsGitInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, null);

        List<String> allow = new ArrayList<>();
        new JsonMapper().readTree(json).path("permissions").path("allow").forEach(n -> allow.add(n.asString("")));

        assertThat(allow).contains("mcp__jagt-orchestrator", "Bash(git:*)");
    }

    @Test
    void disablesConfiguredPluginsInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, List.of("jdtls-lsp@claude-plugins-official"));

        boolean enabled = new JsonMapper().readTree(json)
                .path("enabledPlugins").path("jdtls-lsp@claude-plugins-official").asBoolean(true);

        assertThat(enabled).isFalse();
    }
}
