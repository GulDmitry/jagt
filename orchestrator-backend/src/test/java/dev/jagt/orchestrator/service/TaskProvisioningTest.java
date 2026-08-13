package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.agent.ClaudeAgentRuntime;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.TerminalDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Worktree creation + provisioning, extracted from the OrchestratorTools facade. Everything it needs is
 * git, state, config and the agent runtime — no editor, notifier or MCP surface.
 */
class TaskProvisioningTest {

    @TempDir
    Path root;

    private OrchestratorProperties properties;
    private StateService state;
    private ConfigService config;
    private final GitService git = mock(GitService.class);

    @BeforeEach
    void setUp() {
        properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()).withAgentPrompt("prompt");
        state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    private TaskProvisioning provisioning() {
        ClaudeAgentRuntime runtime = new ClaudeAgentRuntime(properties);
        AgentSessions sessions = new AgentSessions(config, state, mock(TmuxService.class),
                mock(TerminalDriver.class), runtime);
        return new TaskProvisioning(config, state, git, sessions, runtime, properties,
                new OrchestratorPaths(properties), new PromptTemplates());
    }

    /** A task id becomes a branch name, a directory name and a tmux window name — so it is validated first. */
    @ParameterizedTest
    @ValueSource(strings = {"feature/X", "../escape", "a b"})
    void rejectsTaskIdBeforeTouchingGitWhenItCannotBeABranchName(String unsafeTaskId) {
        assertThatThrownBy(() -> provisioning().initializeTask(unsafeTaskId, "proj", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git);
    }

    @Test
    void rejectsUnknownModeBeforeTouchingGit() {
        assertThatThrownBy(() -> provisioning().initializeTask("ABC-1", "proj", null, "bogus", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mode");
        verifyNoInteractions(git);
    }

    @Test
    void removesFreshWorktreeAndBranchWhenContextSetupFailsAfterCheckout() {
        Path projectPath = withProject("proj");
        when(git.remoteUrl(any())).thenThrow(new IllegalStateException("remote lookup failed"));

        assertThatThrownBy(() -> provisioning().initializeTask("ABC-9", "proj", null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);

        verify(git).removeWorktree(projectPath.toAbsolutePath().normalize(),
                root.resolve("ABC-9-proj"), "ABC-9");
    }

    @Test
    void assignsNextFreeAliasWhenTicketLetterAlreadyInUse() throws Exception {
        Files.createDirectories(root.resolve("ABC-2-proj"));
        withProject("proj");
        state.putTask("ABC-1", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(git.remoteUrl(any())).thenReturn("git@host:g/p.git");
        when(git.gitCommonDir(any())).thenReturn(root.resolve("gitdir"));

        provisioning().initializeTask("ABC-2", "proj", null, null, null, null, null);

        assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
    }

    private Path withProject(String key) {
        Path projectPath = root.resolve("repo");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of(key, new ProjectConfig(projectPath.toString(), "origin/main", null, List.of()))));
        return projectPath;
    }
}
