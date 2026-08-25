package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.config.ClaudeProperties;

import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeAgentRuntimeTest {

    @Test
    void launchesTheClaudeCliWithTheBootstrapPromptQuoted() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults()
                .withAgentPrompt("Read AGENTS.md and work"), new ClaudeProperties("claude"),
                new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));

        assertThat(runtime.launchCommand(Path.of("/wt"), false))
                .isEqualTo("claude 'Read AGENTS.md and work'");
    }

    @Test
    void startsTheAgentInPlanModeWhenTheHumanAskedToAgreeOnTheApproachFirst() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));

        assertThat(runtime.launchCommand(Path.of("/wt"), true))
                .isEqualTo("claude --permission-mode plan 'go'");
    }

    @Test
    void keepsAPromptWithAnApostropheOneShellArgumentInsteadOfBreakingTheLaunch() {
        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("it's fine"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));

        assertThat(runtime.launchCommand(Path.of("/wt"), false))
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

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

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

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(worktree.resolve("mcp_client.js")).doesNotExist();
    }

    @Test
    void aliasesClaudeMdToTheSharedSystemKnowledgeFileSoOnlyOneCopyExists(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "system knowledge");

        assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("system knowledge");
    }

    @Test
    void writesTheBriefingBesideTheRepositorysOwnClaudeMdInsteadOfReplacingIt(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve("CLAUDE.md"), "project rules");

        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
        Path briefing = runtime.systemKnowledgeFile(worktree);
        runtime.provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(briefing).isEqualTo(worktree.resolve("CLAUDE.local.md"));
        assertThat(Files.readString(worktree.resolve("CLAUDE.md"))).isEqualTo("project rules");
    }

    @Test
    void writesTheBriefingBesideTheRepositorysOwnAgentsMdInsteadOfReplacingIt(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "project rules");

        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));
        Path briefing = runtime.systemKnowledgeFile(worktree);
        runtime.provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(briefing).isEqualTo(worktree.resolve("CLAUDE.local.md"));
        assertThat(Files.readString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE)))
                .isEqualTo("project rules");
    }

    @Test
    void refusesToStartAnAgentWhenTheRepositoryOwnsEveryNameTheBriefingCouldTake(@TempDir Path root)
            throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve("CLAUDE.md"), "project rules");
        Files.writeString(worktree.resolve("CLAUDE.local.md"), "personal rules");

        var runtime = new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"));

        assertThatThrownBy(() -> runtime.systemKnowledgeFile(worktree))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLAUDE.local.md");
    }

    @Test
    void pinsConfiguredOutputStyleInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson("sob-ai:Engineer", null, null);

        String style = new JsonMapper().readTree(json).path("outputStyle").asString(null);

        assertThat(style).isEqualTo("sob-ai:Engineer");
    }

    @Test
    void preApprovesTheJagtToolsAndTheAgentsGitInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, null, null);

        List<String> allow = new ArrayList<>();
        new JsonMapper().readTree(json).path("permissions").path("allow").forEach(n -> allow.add(n.asString("")));

        assertThat(allow).contains("mcp__jagt-orchestrator", "Bash(git:*)");
    }

    @Test
    void disablesConfiguredPluginsInGeneratedAgentSettings() {
        String json = ClaudeAgentRuntime.settingsJson(null, List.of("jdtls-lsp@claude-plugins-official"), null);

        boolean enabled = new JsonMapper().readTree(json)
                .path("enabledPlugins").path("jdtls-lsp@claude-plugins-official").asBoolean(true);

        assertThat(enabled).isFalse();
    }

    /**
     * A hook fires from the harness rather than from the model, so a session stopped at a prompt, out of
     * tokens or gone still says so. Which events those are is declared in {@code hooks/claude.properties}.
     */
    @Test
    void writesADeclaredEventAsAHookThatReportsTheStateItMeans(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        String command = new JsonMapper()
                .readTree(Files.readString(worktree.resolve(".claude").resolve("settings.local.json")))
                .path("hooks").path("Stop").path(0).path("hooks").path(0).path("command").asString("");
        assertThat(command).contains("/api/agent/session/idle", worktree.toString());
    }

    /** The gate is scoped to the one tool that can push: a hook on every call would sit in front of every step. */
    @Test
    void writesTheGateAsAHookOnTheShellToolAlone(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new ClaudeAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                new ClaudeProperties("claude"), new McpEndpoint("http://localhost:8290/mcp"),
                new HookEndpoint("http://127.0.0.1:8290/api/agent/session", "http://127.0.0.1:8290/api/agent"))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        var gate = new JsonMapper().readTree(Files.readString(
                        worktree.resolve(".claude/settings.local.json")))
                .path("hooks").path("PreToolUse").path(0);
        assertThat(gate.path("matcher").asString("")).isEqualTo("Bash");
        assertThat(gate.path("hooks").path(0).path("command").asString(""))
                .contains("/api/agent/tool", worktree.toString());
    }
}
