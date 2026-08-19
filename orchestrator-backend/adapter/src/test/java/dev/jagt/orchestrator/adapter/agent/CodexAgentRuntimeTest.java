package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.config.CodexProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodexAgentRuntimeTest {

    @Test
    void launchesTheCodexCliAgainstTheWorktreesOwnCodexHome() {
        var runtime = new CodexAgentRuntime(OrchestratorProperties.defaults()
                .withAgentPrompt("Read AGENTS.md and work"), CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")));

        assertThat(runtime.launchCommand(Path.of("/wt/ABC-1-proj"), false))
                .isEqualTo("CODEX_HOME='/wt/ABC-1-proj/.jagt/codex' codex 'Read AGENTS.md and work'");
    }

    @Test
    void runsReadOnlyInPlanModeSoTheAgentCannotTouchFilesBeforeTheHumanApproves() {
        var runtime = new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")));

        assertThat(runtime.launchCommand(Path.of("/wt/ABC-1-proj"), true))
                .isEqualTo("CODEX_HOME='/wt/ABC-1-proj/.jagt/codex' codex --sandbox read-only 'go'");
    }

    @Test
    void refusesToStartAnAgentWhoseOnlyMemoryFileTheRepositoryAlreadyOwns(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "project rules");

        var runtime = new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")));

        assertThatThrownBy(() -> runtime.systemKnowledgeFile(worktree))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AgentRuntime.SYSTEM_KNOWLEDGE_FILE);
    }

    @Test
    void refusesToWireAnAgentOntoAScriptTheRepositoryAlreadyShipsUnderThatName(@TempDir Path root)
            throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve("mcp_client.js"), "the project's own script");

        var runtime = new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")));

        assertThatThrownBy(() -> runtime.provisionWorktree(new AgentWorktree(worktree, root, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mcp_client.js");
    }

    @Test
    void declaresTheJagtMcpProxyInTheWorktreesConfigToml(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readString(worktree.resolve(".jagt/codex/config.toml")))
                .contains("[mcp_servers.jagt-orchestrator]")
                .contains("args = [\"" + worktree.resolve("mcp_client.js") + "\"]");
    }

    /**
     * Codex spawns its MCP servers rather than reaching a remote one, so the bridge its config names has to BE
     * there — the one reason Node is still a requirement for this runtime.
     */
    @Test
    void linksTheBridgeItsConfigNamesIntoTheWorktree(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readSymbolicLink(worktree.resolve("mcp_client.js")))
                .isEqualTo(root.resolve("mcp_client.js"));
    }

    @Test
    void runsWithoutApprovalPromptsBecauseNobodyWatchesTheTmuxWindow(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readString(worktree.resolve(".jagt/codex/config.toml")))
                .contains("approval_policy = \"never\"")
                .contains("sandbox_mode = \"workspace-write\"");
    }

    @Test
    void leavesACodexConfigTheRepositoryShipsExactlyAsItCheckedOut(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        Files.createDirectories(worktree.resolve(".codex"));
        Files.writeString(worktree.resolve(".codex/config.toml"), "model = \"the project's own choice\"\n");

        new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")))
                .provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readString(worktree.resolve(".codex/config.toml")))
                .isEqualTo("model = \"the project's own choice\"\n");
    }

    @Test
    void writesNoClaudeArtifactsIntoACodexWorktree(@TempDir Path root) {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/orchestrator-root")))
                .provisionWorktree(new AgentWorktree(worktree, root, "sob-ai:Engineer", null));

        assertThat(worktree.resolve(".mcp.json")).doesNotExist();
        assertThat(worktree.resolve(".claude")).doesNotExist();
        assertThat(worktree.resolve("CLAUDE.md")).doesNotExist();
    }

    @Test
    void refusesToStartWithoutTheBridgeItIsTheOnlyRuntimeStillSpawning(@TempDir Path root) {
        var runtime = new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(runtime.problems())
                .anySatisfy(problem -> assertThat(problem).contains("mcp_client.js", "is not there"));
    }

    @Test
    void hasNothingToReportAboutTheBridgeOnceItIsThere(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("mcp_client.js"), "// bridge");

        var runtime = new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt("go"),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));

        assertThat(runtime.problems())
                .noneSatisfy(problem -> assertThat(problem).contains("mcp_client.js"));
    }
}
