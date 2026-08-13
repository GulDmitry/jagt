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
                .withClaudeCommand(command).withAgentPrompt(prompt));
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

    @Test
    void linksTheAgentAgnosticMcpProxyIntoTheWorktree(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("claude", "go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readSymbolicLink(worktree.resolve("mcp_client.js")))
                .isEqualTo(root.resolve("mcp_client.js"));
    }

    @Test
    void declaresTheJagtMcpServerThroughTheProjectMcpConfig(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("claude", "go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readSymbolicLink(worktree.resolve(".mcp.json"))).isEqualTo(root.resolve(".mcp.json"));
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
