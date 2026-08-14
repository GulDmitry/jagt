package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.agent.AgentRuntime;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.agent.ClaudeAgentRuntime;
import dev.jagt.orchestrator.agent.McpEndpoint;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.TerminalDriver;
import dev.jagt.orchestrator.platform.UserNotifier;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.ViewerConfig;
import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TmuxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrchestratorToolsTest {

    @Test
    void keepsAgentsViewerOpenAfterLastTaskByDefault(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("shared")));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        TerminalDriver terminal = mock(TerminalDriver.class);
        OrchestratorTools tools = facade(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties);

        tools.removeTask("a1", null);

        verifyNoInteractions(terminal);
    }

    @Test
    void closesAgentsViewerAfterLastTaskWhenKeepViewerDisabled(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("shared")
                        .withKeepViewer(false)));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        TerminalDriver terminal = mock(TerminalDriver.class);
        OrchestratorTools tools = facade(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties);

        tools.removeTask("a1", null);

        verify(terminal).closeViewerWindow("jagt");
    }

    @Test
    void ideOpensTheDeployWorktreeForATaskStuckInDeployConflict(@TempDir Path root) throws Exception {
        Path repo = Files.createDirectories(root.resolve("repo"));
        Path deployWorktree = Files.createDirectories(root.resolve("ABC-1-deploy"));   // sibling of the repo
        Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT)
                .alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = facade(config, state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties);

        String out = tools.openInIde("a1", null, null);

        // The conflict lives on the deploy side — `ide` must open THAT, never the (clean) task worktree.
        verify(editor).open(deployWorktree);
        verify(editor, never()).open(taskWorktree);
        assertThat(out).contains("DEPLOY worktree");
    }

    @Test
    void storesTheMrLinkFromTheStatusMessageForTheDashboard(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        tools.updateAgentStatus("CI_POLLING", "MR: https://gitlab/x/-/merge_requests/9", "ABC-1", null);

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void notifiesHumanWhenAgentFinishesAndHandsBackForReview(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        UserNotifier notifier = mock(UserNotifier.class);
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                notifier, properties);

        tools.updateAgentStatus("REVIEW_PENDING", "done", "ABC-1", "ABC-1");

        verify(notifier).notify(org.mockito.ArgumentMatchers.contains("ABC-1"), anyString());
    }

    @Test
    void doesNotNotifyOnRoutineInProgressKeepAlive(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        UserNotifier notifier = mock(UserNotifier.class);
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                notifier, properties);

        tools.updateAgentStatus("IN_PROGRESS", "step 2", "ABC-1", "ABC-1");

        verifyNoInteractions(notifier);
    }

    @Test
    void opensStaticDiffAgainstBaseWhenModeIsDiff(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.checkoutBaseForDiff(any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/base"));
        when(git.checkoutWorktreeCleanForDiff(any(), any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/clean"));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = facade(config, state, git, mock(TmuxService.class),
                editor, mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        tools.openInIde("a1", "diff", null);

        verify(editor).openDiff(java.nio.file.Path.of("/tmp/base"), java.nio.file.Path.of("/tmp/clean"));
    }

    @Test
    void opensWorktreeAsProjectByDefault(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties);

        tools.openInIde("a1", null, null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @Test
    void opensWorktreeAsProjectWhenModeIsProject(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties);

        tools.openInIde("a1", "project", null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @Test
    void rejectsCiPollingStatusWhenMessageCarriesNoMrLink(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.updateAgentStatus("CI_POLLING", "branch pushed", "ABC-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MR link");
    }

    @Test
    void acceptsCiPollingStatusWhenMessageCarriesTheMrLink(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        tools.updateAgentStatus("CI_POLLING", "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    /**
     * Carrying an MR link is not a licence to go back to polling it: a task the human has already taken past
     * review must not be dropped into CI_POLLING by a confused agent, which would re-arm the auto-review poll
     * against a request nobody is waiting on any more.
     */
    @Test
    void refusesToPushATaskThatIsPastReviewBackIntoCiPolling(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1")
                .mrUrl("https://gitlab.example/g/p/-/merge_requests/1").build());
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.updateAgentStatus("CI_POLLING", "waiting for the pipeline", "ABC-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MR link");
    }

    @Test
    void rejectsStatusUpdateWhenSubAgentTargetsSiblingTask(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("OTHER-1", TaskState.builder("proj", "/other", TaskStatus.IN_PROGRESS).alias("o1").build());
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.updateAgentStatus("DONE", null, "OTHER-1", "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only act on their own task");
    }

    @Test
    void truncatesStatusMessageToOneDashboardLineWhenAgentSendsAnEssay(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        OrchestratorTools tools = facade(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        tools.updateAgentStatus("IN_PROGRESS", "root cause\nanalysis ".repeat(20), "ABC-1", null);

        String stored = state.task("ABC-1").orElseThrow().message();
        assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("...");
    }

    @Test
    void movesTaskToDeployedAfterASuccessfulDeploy(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        OrchestratorTools tools = facade(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        tools.deployTask("a1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    /** Without the commit stored, `revert` has nothing exact to undo and can only send the human to git. */
    @Test
    void recordsTheMergeCommitTheDeployCreatedSoItCanBeReverted(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"))).thenReturn("cafebabe1234");
        OrchestratorTools tools = facade(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        String result = tools.deployTask("a1", null);

        assertThat(state.task("ABC-1").orElseThrow().deployCommit()).isEqualTo("cafebabe1234");
        assertThat(result).contains("cafebabe");
    }

    @Test
    void revertsTheDeployedMergeAndMovesTheTaskToReverted(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1")
                .deployCommit("cafebabe1234").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.revertMergeAndPush(any(), eq("ABC-1"), eq("dev"), eq("cafebabe1234")))
                .thenReturn("f00dfeed5678");
        OrchestratorTools tools = facade(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        String result = tools.revertTask("a1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.REVERTED);
        assertThat(result).contains("f00dfeed");
    }

    /**
     * A task deployed before jagt stored the commit: the message must be the by-hand recipe, because guessing
     * which merge to revert on a SHARED branch is the one mistake here with no cheap undo.
     */
    @Test
    void refusesToGuessTheMergeCommitOfADeployItDidNotRecord(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        OrchestratorTools tools = facade(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.revertTask("a1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git revert -m 1");
        verify(git, never()).revertMergeAndPush(any(), anyString(), anyString(), anyString());
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void refusesToRevertATaskThatWasNeverDeployed(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        GitService git = mock(GitService.class);
        OrchestratorTools tools = facade(mock(ConfigService.class), state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.revertTask("a1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only a DEPLOYED task");
        verifyNoInteractions(git);
    }

    @Test
    void refusesRevertWhenCalledBySubAgent(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        GitService git = mock(GitService.class);
        OrchestratorTools tools = facade(mock(ConfigService.class), state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.revertTask("ABC-1", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master-only");
        verifyNoInteractions(git);
    }

    @Test
    void flagsDeployConflictOnTheDashboardWithoutOpeningAnEditorOrTouchingTheTaskBranch(@TempDir Path root)
            throws Exception {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        Path worktree = java.nio.file.Files.createDirectories(root.resolve("wt"));
        state.putTask("ABC-1", TaskState.builder("proj", worktree.toString(), TaskStatus.CI_POLLING)
                .message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        GitService git = mock(GitService.class);
        Path deployWorktree = root.resolve("ABC-1-deploy");
        doThrow(new GitService.MergeConflictException("ABC-1", "dev", "conflict in liquibase/master.yaml", deployWorktree))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = facade(config, state, git, mock(TmuxService.class), editor,
                mock(TerminalDriver.class), mock(UserNotifier.class), properties);

        String result = tools.deployTask("a1", null);

        // The human resolves it himself — jagt opens no editor, briefs no agent, and never touches the task branch.
        verifyNoInteractions(editor);
        assertThat(worktree.resolve("task_context.md")).doesNotExist();
        assertThat(result).contains(deployWorktree.toString()).contains("untouched").contains("deploy ABC-1");
        TaskState after = state.task("ABC-1").orElseThrow();
        assertThat(after.status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
        assertThat(after.message()).contains(deployWorktree.toString());
    }

    @Test
    void refusesDeployWhenDeployBranchIsTheBaseBranch(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).message("MR: http://x").alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release", "release", null));
        GitService git = mock(GitService.class);
        OrchestratorTools tools = facade(config, state, git,
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.deployTask("a1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base branch");
        verifyNoInteractions(git);
    }

    @Test
    void refusesDeployWhenProjectHasNoDeployBranch(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", null, null));
        OrchestratorTools tools = facade(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties);

        assertThatThrownBy(() -> tools.deployTask("ABC-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deployBranch");
    }

    @Test
    void refusesDeployWhenCalledBySubAgent(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        OrchestratorTools tools = facade(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), mock(GitService.class), mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties);

        assertThatThrownBy(() -> tools.deployTask("ABC-1", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master-only");
    }

    /**
     * `resume` validates the ticket id up front, BEFORE it matches the MR url against every project's git
     * remote — otherwise an unusable id costs a remote lookup per configured project just to be rejected.
     */
    @Test
    void refusesAnUnusableTicketIdBeforeResolvingTheProjectFromTheMrUrl(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        GitService git = mock(GitService.class);
        OrchestratorTools tools = facade(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties);

        assertThatThrownBy(() -> tools.resumeTask("feature/X", "https://host/mr/1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git);
    }

    /**
     * The facade no longer takes the tmux/terminal/provisioning collaborators — it takes the two services the
     * split extracted. This wires them the way the Spring context does, so a facade test keeps saying what it
     * always said; a test of the extracted concerns builds only that concern (see AgentSessionsTest,
     * TaskProvisioningTest) and needs a fraction of this.
     */
    private static OrchestratorTools facade(ConfigService config, StateService state, GitService git,
                                            TmuxService tmux, EditorDriver editor, TerminalDriver terminal,
                                            UserNotifier notifier, OrchestratorProperties properties) {
        AgentRuntime runtime = new ClaudeAgentRuntime(properties, new McpEndpoint("http://localhost:8290/mcp"));
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, runtime);
        return new OrchestratorTools(config, state, git, editor, notifier, sessions,
                new TaskProvisioning(config, state, git, sessions, runtime, properties,
                        new OrchestratorPaths(properties), new PromptTemplates()));
    }
}
