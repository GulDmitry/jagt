package dev.jagt.orchestrator.adapter.agent;

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

    private static CodexAgentRuntime runtime(String prompt) {
        return runtime(prompt, Path.of("/orchestrator-root"));
    }

    private static CodexAgentRuntime runtime(String prompt, Path orchestratorRoot) {
        return new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt(prompt),
                CodexProperties.defaults(),
                new OrchestratorPaths(OrchestratorProperties.defaults()
                        .withRoot(orchestratorRoot.toString())));
    }

    @Test
    void launchesTheCodexCliAgainstTheWorktreesOwnCodexHome() {
        assertThat(runtime("Read AGENTS.md and work").launchCommand(Path.of("/wt/ABC-1-proj"), false))
                .isEqualTo("CODEX_HOME='/wt/ABC-1-proj/.codex' codex 'Read AGENTS.md and work'");
    }

    @Test
    void runsReadOnlyInPlanModeSoTheAgentCannotTouchFilesBeforeTheHumanApproves() {
        assertThat(runtime("go").launchCommand(Path.of("/wt/ABC-1-proj"), true))
                .isEqualTo("CODEX_HOME='/wt/ABC-1-proj/.codex' codex --sandbox read-only 'go'");
    }

    @Test
    void refusesToStartAnAgentWhoseOnlyMemoryFileTheRepositoryAlreadyOwns(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve(AgentRuntime.SYSTEM_KNOWLEDGE_FILE), "project rules");

        assertThatThrownBy(() -> runtime("go").systemKnowledgeFile(worktree))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AgentRuntime.SYSTEM_KNOWLEDGE_FILE);
    }

    @Test
    void refusesToWireAnAgentOntoAScriptTheRepositoryAlreadyShipsUnderThatName(@TempDir Path root)
            throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();
        Files.writeString(worktree.resolve("mcp_client.js"), "the project's own script");

        assertThatThrownBy(() -> runtime("go")
                .provisionWorktree(new AgentWorktree(worktree, root, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mcp_client.js");
    }

    @Test
    void declaresTheJagtMcpProxyInTheWorktreesConfigToml(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readString(worktree.resolve(".codex/config.toml")))
                .contains("[mcp_servers.jagt-orchestrator]")
                .contains("args = [\"" + worktree.resolve("mcp_client.js") + "\"]");
        // The config names the bridge, so the bridge has to BE there: Codex spawns its MCP servers rather than
        // reaching a remote one, which is the one reason Node is still installed for this runtime.
        assertThat(Files.readSymbolicLink(worktree.resolve("mcp_client.js")))
                .isEqualTo(root.resolve("mcp_client.js"));
    }

    @Test
    void runsWithoutApprovalPromptsBecauseNobodyWatchesTheTmuxWindow(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readString(worktree.resolve(".codex/config.toml")))
                .contains("approval_policy = \"never\"")
                .contains("sandbox_mode = \"workspace-write\"");
    }

    @Test
    void writesNoClaudeArtifactsIntoACodexWorktree(@TempDir Path root) {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("go").provisionWorktree(new AgentWorktree(worktree, root, "sob-ai:Engineer", null));

        assertThat(worktree.resolve(".mcp.json")).doesNotExist();
        assertThat(worktree.resolve(".claude")).doesNotExist();
        assertThat(worktree.resolve("CLAUDE.md")).doesNotExist();
    }

    @Test
    void refusesToStartWithoutTheBridgeItIsTheOnlyRuntimeStillSpawning(@TempDir Path root) {
        assertThat(runtime("go", root).problems())
                .anySatisfy(problem -> assertThat(problem).contains("mcp_client.js", "is not there"));
    }

    @Test
    void hasNothingToReportAboutTheBridgeOnceItIsThere(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("mcp_client.js"), "// bridge");

        assertThat(runtime("go", root).problems())
                .noneSatisfy(problem -> assertThat(problem).contains("mcp_client.js"));
    }
}
