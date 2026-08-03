package dev.jagt.orchestrator.mcp;

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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrchestratorToolsTest {

    @Test
    void closesTaskWindowWhenCalledWithItsAlias(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("TEST-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "t1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt", "TEST-1")).thenReturn(1);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        String result = tools.closeTaskTab("t1", null);

        assertThat(result).contains("Closed 1 tmux window(s) for TEST-1");
    }

    @Test
    void givesEachTaskItsOwnSessionWhenViewModeIsTabPerTask(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("TEST-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "t1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("tab-per-task")));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt-TEST-1", "TEST-1")).thenReturn(1);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.closeTaskTab("t1", null);

        verify(tmux).killTaskWindows("jagt-TEST-1", "TEST-1");
    }

    @Test
    void keepsAgentsViewerOpenAfterLastTaskByDefault(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("shared")));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        TerminalDriver terminal = mock(TerminalDriver.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties, paths,
                new PromptTemplates());

        tools.removeTask("a1", null);

        verifyNoInteractions(terminal);
    }

    @Test
    void closesAgentsViewerAfterLastTaskWhenKeepViewerDisabled(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("shared")
                        .withKeepViewer(false)));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        TerminalDriver terminal = mock(TerminalDriver.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties, paths,
                new PromptTemplates());

        tools.removeTask("a1", null);

        verify(terminal).closeViewerWindow("jagt");
    }

    @Test
    void storesTheMrLinkFromTheStatusMessageForTheDashboard(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null, null));
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        tools.updateAgentStatus("CI_POLLING", "MR: https://gitlab/x/-/merge_requests/9", "ABC-1", null);

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void notifiesHumanWhenAgentFinishesAndHandsBackForReview(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null, null));
        UserNotifier notifier = mock(UserNotifier.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                notifier, properties, paths, new PromptTemplates());

        tools.updateAgentStatus("REVIEW_PENDING", "done", "ABC-1", "ABC-1");

        verify(notifier).notify(org.mockito.ArgumentMatchers.contains("ABC-1"), anyString());
    }

    @Test
    void doesNotNotifyOnRoutineInProgressKeepAlive(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null, null));
        UserNotifier notifier = mock(UserNotifier.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                notifier, properties, paths, new PromptTemplates());

        tools.updateAgentStatus("IN_PROGRESS", "step 2", "ABC-1", "ABC-1");

        verifyNoInteractions(notifier);
    }

    @Test
    void copiesLegacyIdeaRunConfigurationsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        java.nio.file.Files.createDirectories(project.resolve(".idea").resolve("runConfigurations"));
        java.nio.file.Files.writeString(project.resolve(".idea").resolve("runConfigurations").resolve("App.xml"),
                "<configuration/>");
        Path worktree = root.resolve("PAN-1-repo");

        OrchestratorTools.copyRunConfigurations(project, worktree);

        assertThat(worktree.resolve(".idea").resolve("runConfigurations").resolve("App.xml"))
                .exists().hasContent("<configuration/>");
    }

    @Test
    void copiesModernDotRunConfigurationsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        java.nio.file.Files.createDirectories(project.resolve(".run"));
        java.nio.file.Files.writeString(project.resolve(".run").resolve("App.run.xml"), "<configuration/>");
        Path worktree = root.resolve("PAN-1-repo");

        OrchestratorTools.copyRunConfigurations(project, worktree);

        assertThat(worktree.resolve(".run").resolve("App.run.xml")).exists().hasContent("<configuration/>");
    }

    @Test
    void doesNotFailWhenBaseProjectHasNoSharedRunConfigurations(@TempDir Path root) {
        Path project = root.resolve("repo");
        Path worktree = root.resolve("PAN-1-repo");

        OrchestratorTools.copyRunConfigurations(project, worktree);

        assertThat(worktree.resolve(".idea")).doesNotExist();
    }

    @Test
    void opensStaticDiffAgainstBaseWhenModeIsDiff(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.checkoutBaseForDiff(any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/base"));
        when(git.checkoutWorktreeCleanForDiff(any(), any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/clean"));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                editor, mock(TerminalDriver.class), mock(UserNotifier.class), properties, paths,
                new PromptTemplates());

        tools.openInIde("a1", "diff", null);

        verify(editor).openDiff(java.nio.file.Path.of("/tmp/base"), java.nio.file.Path.of("/tmp/clean"));
    }

    @Test
    void opensWorktreeAsProjectByDefault(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null, null));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.openInIde("a1", null, null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @Test
    void opensWorktreeAsProjectWhenModeIsProject(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null, null));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.openInIde("a1", "project", null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS,    true,  PROCEED",
            "IN_PROGRESS,    false, PROCEED",
            "REVIEW_PENDING, true,  PROCEED",
            "SHIPPING,       false, PROCEED",
            "SHIPPING,       true,  REFUSE",
            "CI_POLLING,     false, REFUSE",
            "DEPLOYED,       false, REFUSE",
            "NEW,            true,  REFUSE",
            "DONE,           false, REFUSE"
    })
    void shipGateProceedsOrRefusesByStatusAndAgentLiveness(
            TaskStatus status, boolean agentLive, OrchestratorTools.ShipGate expected) {
        assertThat(OrchestratorTools.shipGate(status, agentLive)).isEqualTo(expected);
    }

    @Test
    void rejectsCiPollingStatusWhenMessageCarriesNoMrLink(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null, null));
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.updateAgentStatus("CI_POLLING", "branch pushed", "ABC-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MR link");
    }

    @Test
    void acceptsCiPollingStatusWhenMessageCarriesTheMrLink(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null, null));
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        tools.updateAgentStatus("CI_POLLING", "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void rejectsStatusUpdateWhenSubAgentTargetsSiblingTask(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("OTHER-1", new TaskState("proj", "/other", TaskStatus.IN_PROGRESS, 0, null, "o1", null, null, null, null));
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.updateAgentStatus("DONE", null, "OTHER-1", "MINE-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only act on their own task");
    }

    @Test
    void truncatesStatusMessageToOneDashboardLineWhenAgentSendsAnEssay(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null, null));
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        tools.updateAgentStatus("IN_PROGRESS", "root cause\nanalysis ".repeat(20), "ABC-1", null);

        String stored = state.task("ABC-1").orElseThrow().message();
        assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("...");
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature/X", "../escape", "a b"})
    void rejectsTaskIdBeforeTouchingGitWhenItCannotBeABranchName(String unsafeTaskId, @TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        GitService git = mock(GitService.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.initializeTask(unsafeTaskId, "proj", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git);
    }

    @Test
    void rejectsUnknownModeBeforeTouchingGit(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        GitService git = mock(GitService.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.initializeTask("ABC-1", "proj", null, "bogus", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mode");
        verifyNoInteractions(git);
    }

    @Test
    void removesFreshWorktreeAndBranchWhenContextSetupFailsAfterCheckout(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        Path projectPath = root.resolve("repo");
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("proj", new ProjectConfig(projectPath.toString(), "origin/main", null, null))));
        GitService git = mock(GitService.class);
        when(git.remoteUrl(any())).thenThrow(new IllegalStateException("remote lookup failed"));
        OrchestratorTools tools = new OrchestratorTools(config,
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.initializeTask("ABC-9", "proj", null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);

        verify(git).removeWorktree(projectPath.toAbsolutePath().normalize(),
                root.resolve("ABC-9-proj"), "ABC-9");
    }

    @Test
    void movesTaskToDeployedAfterASuccessfulDeploy(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.CI_POLLING, 0,
                "MR: http://x", "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        tools.deployTask("a1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void delegatesConflictResolutionToTheAgentWithoutCommittingAndStaysUndeployed(@TempDir Path root)
            throws Exception {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        Path worktree = java.nio.file.Files.createDirectories(root.resolve("wt"));
        state.putTask("ABC-1", new TaskState("proj", worktree.toString(), TaskStatus.CI_POLLING, 0,
                "MR: http://x", "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        GitService git = mock(GitService.class);
        doThrow(new GitService.MergeConflictException("ABC-1", "dev", "Merge conflict in liquibase/master.yaml"))
                .when(git).mergeIntoAndPush(any(), eq("ABC-1"), eq("dev"));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(any())).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(TmuxService.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        OrchestratorTools tools = new OrchestratorTools(config, state, git, tmux, mock(EditorDriver.class),
                mock(TerminalDriver.class), mock(UserNotifier.class), properties, paths, new PromptTemplates());

        String result = tools.deployTask("a1", null);

        // The agent is handed a resolve-but-don't-commit brief; the human keeps the commit + the next deploy.
        assertThat(java.nio.file.Files.readString(worktree.resolve("task_context.md")))
                .contains("git merge origin/dev")
                .contains("DO NOT commit");
        assertThat(result).contains("ide ABC-1").contains("COMMIT it yourself");
        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void refusesDeployWhenDeployBranchIsTheBaseBranch(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.CI_POLLING, 0, "MR: http://x", "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release/sng", "release/sng", null));
        GitService git = mock(GitService.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, git,
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.deployTask("a1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base branch");
        verifyNoInteractions(git);
    }

    @Test
    void refusesDeployWhenProjectHasNoDeployBranch(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.CI_POLLING, 0, null, "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", null, null));
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.deployTask("ABC-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deployBranch");
    }

    @Test
    void refusesDeployWhenCalledBySubAgent(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), mock(GitService.class), mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.deployTask("ABC-1", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master-only");
    }

    @Test
    void nudgesRunningAgentWhenTaskContextIsUpdated(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", root.toString(), TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(TmuxService.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        String result = tools.writeTaskContext("a1", "new instructions");

        assertThat(result).contains("nudged");
    }

    @Test
    void respawnsADownSessionWhenWriteTaskContextTargetsIt(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", root.toString(), TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(TmuxService.WindowState.MISSING);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        String result = tools.writeTaskContext("a1", "new instructions");

        assertThat(result).contains("respawned");
        verify(tmux).openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false));
    }

    @Test
    void assignsNextFreeAliasWhenTicketLetterAlreadyInUse(@TempDir Path root) throws Exception {
        java.nio.file.Files.createDirectories(root.resolve("ABC-2-proj"));
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, "prompt", null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/first", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null, null));
        Path projectPath = root.resolve("repo");
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("proj", new ProjectConfig(projectPath.toString(), "origin/main", null, null))));
        GitService git = mock(GitService.class);
        when(git.remoteUrl(any())).thenReturn("git@host:g/p.git");
        when(git.gitCommonDir(any())).thenReturn(root.resolve("gitdir"));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        OrchestratorTools tools = new OrchestratorTools(config, state, git, tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.initializeTask("ABC-2", "proj", null, null, null, null, null);

        assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
    }

    @Test
    void pinsConfiguredOutputStyleInGeneratedAgentSettings() {
        String json = OrchestratorTools.agentSettingsJson("sob-ai:Engineer", null);

        String style = new JsonMapper().readTree(json).path("outputStyle").asText(null);

        assertThat(style).isEqualTo("sob-ai:Engineer");
    }

    @Test
    void copiesLocalFilesMatchingGlobsSkippingHeavyDirs(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        java.nio.file.Files.createDirectories(base.resolve("app"));
        java.nio.file.Files.writeString(base.resolve("app/.env"), "SECRET=1");
        java.nio.file.Files.createDirectories(base.resolve("lib"));
        java.nio.file.Files.writeString(base.resolve("lib/key.pem"), "PEM");
        java.nio.file.Files.createDirectories(base.resolve("node_modules"));
        java.nio.file.Files.writeString(base.resolve("node_modules/.env"), "IGNORED=1");
        Path wt = root.resolve("wt");
        java.nio.file.Files.createDirectories(wt);

        OrchestratorTools.copyLocalFiles(base, wt, List.of("**/.env", "**/*.pem"));

        assertThat(wt.resolve("app/.env")).exists().hasContent("SECRET=1");
        assertThat(wt.resolve("lib/key.pem")).exists().hasContent("PEM");
        assertThat(wt.resolve("node_modules/.env")).doesNotExist();
    }

    @Test
    void copiesNothingWithoutFailingWhenAGlobsDirectoryIsAbsent(@TempDir Path root) throws Exception {
        Path base = root.resolve("base");
        java.nio.file.Files.createDirectories(base.resolve("src"));
        java.nio.file.Files.writeString(base.resolve("src/Main.java"), "class Main {}");
        Path wt = root.resolve("wt");
        java.nio.file.Files.createDirectories(wt);

        OrchestratorTools.copyLocalFiles(base, wt, List.of("vendor/**"));

        assertThat(wt.resolve("vendor")).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({
        "ABC-42 Widget layout is off, ABC-42, Widget layout is off",
        "ABC-42: tidy imports,        ABC-42, tidy imports",
        "Widget layout is off,        ABC-42, Widget layout is off",
        "ABC-42,                      ABC-42, ''"
    })
    void stripsLeadingTicketSoTheShipTitleNeverDoublesIt(String stored, String ticket, String expected) {
        assertThat(OrchestratorTools.stripTicketPrefix(stored, ticket)).isEqualTo(expected);
    }

    @Test
    void firstShipCommitsTheExactPatternTitleAndOpensTheMr() {
        String instruction = OrchestratorTools.shipInstruction(true, "ABC-42 Widget layout is off",
                "ABC-42", "dev", "");

        assertThat(instruction)
                .contains("EXACTLY this message: \"ABC-42 Widget layout is off\"")
                .contains("create one via your GitLab MCP");
    }

    @Test
    void reviewRoundShipCommitsAConciseSummaryNotTheTicketTitle() {
        String instruction = OrchestratorTools.shipInstruction(false, "ABC-42 Widget layout is off",
                "ABC-42", "dev", "");

        assertThat(instruction)
                .contains("CONCISE one-line message")
                .doesNotContain("EXACTLY this message")
                .doesNotContain("ABC-42 Widget layout is off");
    }

    @ParameterizedTest
    @CsvSource({
        "git@example.com:group-a/backend.git, group-a/backend",
        "https://example.com/group-a/backend.git, group-a/backend",
        "https://example.com/group-a/backend, group-a/backend"
    })
    void derivesGitProjectPathFromRemote(String remote, String expected) {
        assertThat(OrchestratorTools.gitProjectPath(remote)).isEqualTo(expected);
    }

    @Test
    void disablesConfiguredPluginsInGeneratedAgentSettings() {
        String json = OrchestratorTools.agentSettingsJson(null, List.of("jdtls-lsp@claude-plugins-official"));

        boolean enabled = new JsonMapper().readTree(json)
                .path("enabledPlugins").path("jdtls-lsp@claude-plugins-official").asBoolean(true);

        assertThat(enabled).isFalse();
    }
}
