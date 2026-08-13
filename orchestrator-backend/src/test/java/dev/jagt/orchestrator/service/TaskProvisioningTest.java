package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.agent.ClaudeAgentRuntime;
import dev.jagt.orchestrator.agent.McpEndpoint;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.NewTask;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        ClaudeAgentRuntime runtime = new ClaudeAgentRuntime(properties, new McpEndpoint("http://localhost:8290/mcp"));
        AgentSessions sessions = new AgentSessions(config, state, mock(TmuxService.class),
                mock(TerminalDriver.class), runtime);
        return new TaskProvisioning(config, state, git, sessions, runtime, properties,
                new OrchestratorPaths(properties), new PromptTemplates());
    }

    /** A task id becomes a branch name, a directory name and a tmux window name — so it is validated first. */
    @ParameterizedTest
    @ValueSource(strings = {"feature/X", "../escape", "a b"})
    void rejectsTaskIdBeforeTouchingGitWhenItCannotBeABranchName(String unsafeTaskId) {
        assertThatThrownBy(() -> provisioning().initializeTask(NewTask.builder(unsafeTaskId, "proj").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git);
    }

    @Test
    void rejectsUnknownModeBeforeTouchingGit() {
        assertThatThrownBy(() -> provisioning().initializeTask(NewTask.builder("ABC-1", "proj").mode("bogus").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mode");
        verifyNoInteractions(git);
    }

    @Test
    void removesFreshWorktreeAndBranchWhenContextSetupFailsAfterCheckout() {
        Path projectPath = withProject("proj");
        when(git.remoteUrl(any())).thenThrow(new IllegalStateException("remote lookup failed"));

        assertThatThrownBy(() -> provisioning().initializeTask(NewTask.builder("ABC-9", "proj").build()))
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

        provisioning().initializeTask(NewTask.builder("ABC-2", "proj").build());

        assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
    }

    @Test
    void cutsTheWorktreeFromTheBranchTheHumanNamedAndRemembersItForTheReviewRequest() throws Exception {
        Files.createDirectories(root.resolve("ABC-3-proj"));
        Path projectPath = withProject("proj");
        when(git.remoteBranchExists(any(), eq("feature/parent"))).thenReturn(true);
        when(git.remoteUrl(any())).thenReturn("git@host:g/p.git");
        when(git.gitCommonDir(any())).thenReturn(root.resolve("gitdir"));

        provisioning().initializeTask(NewTask.builder("ABC-3", "proj").baseBranch("origin/feature/parent").build());

        verify(git).createWorktree(projectPath.toAbsolutePath().normalize(), root.resolve("ABC-3-proj"),
                "ABC-3", "feature/parent", GitService.BranchStrategy.FRESH);
        assertThat(state.task("ABC-3").orElseThrow().baseBranch()).isEqualTo("feature/parent");
    }

    @Test
    void leavesTheBaseBranchUnsetWhenTheHumanNamedNone() throws Exception {
        Files.createDirectories(root.resolve("ABC-4-proj"));
        withProject("proj");
        when(git.remoteUrl(any())).thenReturn("git@host:g/p.git");
        when(git.gitCommonDir(any())).thenReturn(root.resolve("gitdir"));

        provisioning().initializeTask(NewTask.builder("ABC-4", "proj").build());

        assertThat(state.task("ABC-4").orElseThrow().baseBranch()).isNull();
    }

    @Test
    void refusesABaseBranchOriginDoesNotHaveBeforeCreatingAnything() {
        withProject("proj");
        when(git.remoteBranchExists(any(), eq("feature/typo"))).thenReturn(false);

        assertThatThrownBy(() -> provisioning().initializeTask(
                NewTask.builder("ABC-5", "proj").baseBranch("feature/typo").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist on origin");

        verify(git, never()).createWorktree(any(), any(), any(), any(), any());
        assertThat(state.task("ABC-5")).isEmpty();
    }

    private Path withProject(String key) {
        Path projectPath = root.resolve("repo");
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of(key, new ProjectConfig(projectPath.toString(), "origin/main", null, List.of()))));
        return projectPath;
    }
}
