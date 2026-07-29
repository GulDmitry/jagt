package dev.jawo.orchestrator.mcp;

import dev.jawo.orchestrator.config.OrchestratorPaths;
import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.config.PromptTemplates;
import dev.jawo.orchestrator.model.ProjectConfig;
import dev.jawo.orchestrator.model.TaskState;
import dev.jawo.orchestrator.model.TaskStatus;
import dev.jawo.orchestrator.platform.EditorDriver;
import dev.jawo.orchestrator.platform.TerminalDriver;
import dev.jawo.orchestrator.platform.UserNotifier;
import dev.jawo.orchestrator.service.ConfigService;
import dev.jawo.orchestrator.service.GitService;
import dev.jawo.orchestrator.service.StateService;
import dev.jawo.orchestrator.service.TmuxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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
        state.putTask("TEST-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "t1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), null, null, null, null, null));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jawo");
        when(tmux.killTaskWindows("jawo", "TEST-1")).thenReturn(1);
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
        state.putTask("TEST-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "t1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), "jawo", "tab-per-task", null, null, null));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jawo")).thenReturn("jawo");
        when(tmux.killTaskWindows("jawo-TEST-1", "TEST-1")).thenReturn(1);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.closeTaskTab("t1", null);

        verify(tmux).killTaskWindows("jawo-TEST-1", "TEST-1");
    }

    @Test
    void keepsAgentsViewerOpenAfterLastTaskByDefault(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "a1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), "jawo", "shared", null, null, null));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jawo")).thenReturn("jawo");
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
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.DONE, 0, null, "a1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), "jawo", "shared", false, null, null));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jawo")).thenReturn("jawo");
        TerminalDriver terminal = mock(TerminalDriver.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties, paths,
                new PromptTemplates());

        tools.removeTask("a1", null);

        verify(terminal).closeViewerWindow("jawo");
    }

    @Test
    void storesTheMrLinkFromTheStatusMessageForTheDashboard(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null));
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
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null));
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
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null));
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
    void opensDiffAgainstBaseByDefault(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        GitService git = mock(GitService.class);
        when(git.checkoutBaseForDiff(any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/base"));
        when(git.checkoutWorktreeCleanForDiff(any(), any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/clean"));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                editor, mock(TerminalDriver.class), mock(UserNotifier.class), properties, paths,
                new PromptTemplates());

        tools.openInIde("a1", null, null);

        verify(editor).openDiff(java.nio.file.Path.of("/tmp/base"), java.nio.file.Path.of("/tmp/clean"));
    }

    @Test
    void opensWorktreeAsProjectWhenModeIsProject(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.openInIde("a1", "project", null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @Test
    void rejectsCiPollingStatusWhenMessageCarriesNoMrLink(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null));
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
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.REVIEW_PENDING, 0, null, "a1", null, null, null));
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
        state.putTask("OTHER-1", new TaskState("proj", "/other", TaskStatus.IN_PROGRESS, 0, null, "o1", null, null, null));
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
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null));
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

        assertThatThrownBy(() -> tools.initializeTask(unsafeTaskId, "proj", null, null, null, null))
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

        assertThatThrownBy(() -> tools.initializeTask("ABC-1", "proj", null, "bogus", null, null))
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
        when(config.load()).thenReturn(new ConfigService.ConfigFile(
                Map.of("proj", new ProjectConfig(projectPath.toString(), "origin/main", null, null)), null, null, null, null, null));
        GitService git = mock(GitService.class);
        when(git.remoteUrl(any())).thenThrow(new IllegalStateException("remote lookup failed"));
        OrchestratorTools tools = new OrchestratorTools(config,
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        assertThatThrownBy(() -> tools.initializeTask("ABC-9", "proj", null, null, null, null))
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
                "MR: http://x", "a1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates());

        tools.deployTask("a1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
    }

    @Test
    void refusesDeployWhenDeployBranchIsTheBaseBranch(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.CI_POLLING, 0, "MR: http://x", "a1", null, null, null));
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
        state.putTask("ABC-1", new TaskState("proj", "/wt", TaskStatus.CI_POLLING, 0, null, "a1", null, null, null));
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
        state.putTask("ABC-1", new TaskState("proj", root.toString(), TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), null, null, null, null, null));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jawo");
        when(tmux.taskWindowState("jawo", "ABC-1")).thenReturn(TmuxService.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jawo"), eq("ABC-1"), anyString())).thenReturn(true);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        String result = tools.writeTaskContext("a1", "new instructions");

        assertThat(result).contains("nudged");
    }

    @Test
    void reportsDeadSessionWhenWriteTaskContextTargetsClosedAgent(@TempDir Path root) {
        OrchestratorProperties properties = new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false,
                new OrchestratorProperties.Watchdog(Duration.ofMinutes(5)));
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", new TaskState("proj", root.toString(), TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null));
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(Map.of(), null, null, null, null, null));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jawo");
        when(tmux.taskWindowState("jawo", "ABC-1")).thenReturn(TmuxService.WindowState.MISSING);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        String result = tools.writeTaskContext("a1", "new instructions");

        assertThat(result).contains("NOT running");
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
        state.putTask("ABC-1", new TaskState("proj", "/first", TaskStatus.IN_PROGRESS, 0, null, "a1", null, null, null));
        Path projectPath = root.resolve("repo");
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(new ConfigService.ConfigFile(
                Map.of("proj", new ProjectConfig(projectPath.toString(), "origin/main", null, null)), null, null, null, null, null));
        GitService git = mock(GitService.class);
        when(git.remoteUrl(any())).thenReturn("git@host:g/p.git");
        when(git.gitCommonDir(any())).thenReturn(root.resolve("gitdir"));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jawo");
        OrchestratorTools tools = new OrchestratorTools(config, state, git, tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates());

        tools.initializeTask("ABC-2", "proj", null, null, null, null);

        assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
    }

    @Test
    void pinsConfiguredOutputStyleInGeneratedAgentSettings() {
        String json = OrchestratorTools.agentSettingsJson("sob-ai:Engineer");

        String style = new JsonMapper().readTree(json).path("outputStyle").asText(null);

        assertThat(style).isEqualTo("sob-ai:Engineer");
    }
}
