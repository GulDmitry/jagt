package dev.jagt.orchestrator.agent;

import dev.jagt.orchestrator.config.CodexProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CodexAgentRuntimeTest {

    private static CodexAgentRuntime runtime(String prompt) {
        return new CodexAgentRuntime(OrchestratorProperties.defaults().withAgentPrompt(prompt),
                CodexProperties.defaults());
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
    void declaresTheJagtMcpProxyInTheWorktreesConfigToml(@TempDir Path root) throws Exception {
        Path worktree = root.resolve("ABC-1-proj");
        worktree.toFile().mkdirs();

        runtime("go").provisionWorktree(new AgentWorktree(worktree, root, null, null));

        assertThat(Files.readString(worktree.resolve(".codex/config.toml")))
                .contains("[mcp_servers.jagt-orchestrator]")
                .contains("args = [\"" + worktree.resolve("mcp_client.js") + "\"]");
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
}
