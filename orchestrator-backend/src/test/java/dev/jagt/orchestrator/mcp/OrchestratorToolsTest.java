package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.agent.ClaudeAgentRuntime;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    void listsMergedTaskBranchesWithoutDeletingAnythingUntilAsked(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig(root.toString(), "origin/main", "dev", List.of()))));
        GitService git = mock(GitService.class);
        state.putTask("ABC-99", TaskState.builder("demo", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(git.branchesMergedInto(any(Path.class), eq("origin/dev")))
                .thenReturn(List.of("ABC-40", "ABC-41", "ABC-99", "dev", "main"));
        when(git.currentBranch(any(Path.class))).thenReturn("main");
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.pruneBranches(false);

        assertThat(result).contains("ABC-40", "ABC-41", "dry run", "`prune all`");
        // The protections must show up as ABSENCE from the list, or nothing pins them: ABC-99 is a live task
        // (merged is not finished), main is the base branch and the repo's checkout, dev is the deploy branch.
        assertThat(result).doesNotContain("  ABC-99\n", "  main\n", "  dev\n");
        verify(git, never()).deleteLocalBranch(any(Path.class), anyString());
    }

    @Test
    void deletesTheMergedBranchesAndReportsTheOnesGitRefused(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig(root.toString(), "origin/main", "dev", List.of()))));
        GitService git = mock(GitService.class);
        when(git.branchesMergedInto(any(Path.class), eq("origin/dev")))
                .thenReturn(List.of("ABC-40", "ABC-41"));
        when(git.currentBranch(any(Path.class))).thenReturn("main");
        when(git.deleteLocalBranch(any(Path.class), eq("ABC-40"))).thenReturn(Optional.empty());
        when(git.deleteLocalBranch(any(Path.class), eq("ABC-41")))
                .thenReturn(Optional.of("error: branch is checked out at /wt"));
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.pruneBranches(true);

        assertThat(result).contains("deleted ABC-40", "KEPT ABC-41 — error: branch is checked out at /wt");
        // The count must say what HAPPENED, not what was offered — git refused one of the two.
        assertThat(result).contains("deleted 1 of 2");
    }

    @Test
    void neverOffersToDeleteALiveTasksBranchOrALongLivedOne() {
        // Merged into the deploy branch is NOT the same as finished: a task stays live until `done`.
        List<String> merged = List.of("dev", "main", "release", "ABC-40", "ABC-99", "feature-x");

        List<String> prunable = OrchestratorTools.prunable(merged, "origin/main", "dev", "release",
                Set.of("ABC-99"));

        assertThat(prunable).containsExactly("ABC-40", "feature-x");
    }

    @Test
    void keepsTheRecordOfWhatItDeletedWhenAnotherProjectCannotBeExamined(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        ConfigService config = mock(ConfigService.class);
        Map<String, ProjectConfig> projects = new java.util.LinkedHashMap<>();
        projects.put("alpha", new ProjectConfig(root.resolve("alpha").toString(), "origin/main", "dev", List.of()));
        projects.put("beta", new ProjectConfig(root.resolve("beta").toString(), "origin/main", "release", List.of()));
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(projects));
        GitService git = mock(GitService.class);
        when(git.branchesMergedInto(any(Path.class), eq("origin/dev"))).thenReturn(List.of("ABC-40"));
        when(git.branchesMergedInto(any(Path.class), eq("origin/release")))
                .thenThrow(new IllegalStateException("git branch --merged origin/release failed: no such ref"));
        when(git.currentBranch(any(Path.class))).thenReturn("main");
        when(git.deleteLocalBranch(any(Path.class), eq("ABC-40"))).thenReturn(Optional.empty());
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.pruneBranches(true);

        // Losing "deleted ABC-40" because a LATER project failed would leave the human with no audit trail.
        assertThat(result).contains("deleted ABC-40", "beta: SKIPPED — git branch --merged origin/release failed");
    }

    @Test
    void doesNotClaimTheRepoIsCleanWhenNoProjectCouldBeExamined(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("demo", new ProjectConfig(root.toString(), "origin/main", "dev", List.of()))));
        GitService git = mock(GitService.class);
        when(git.branchesMergedInto(any(Path.class), eq("origin/dev")))
                .thenThrow(new IllegalStateException("git branch --merged origin/dev failed: no such ref"));
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.pruneBranches(false);

        assertThat(result).contains("no project could be examined").doesNotContain("nothing to prune");
    }

    @Test
    void closesTaskWindowWhenCalledWithItsAlias(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt", "TEST-1")).thenReturn(1);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.closeTaskTab("t1", null);

        assertThat(result).contains("Closed 1 tmux window(s) for TEST-1");
    }

    @Test
    void givesEachTaskItsOwnSessionWhenViewModeIsTabPerTask(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("tab-per-task")));
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt-TEST-1", "TEST-1")).thenReturn(1);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.closeTaskTab("t1", null);

        verify(tmux).killTaskWindows("jagt-TEST-1", "TEST-1");
    }

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
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties, paths,
                new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), terminal, mock(UserNotifier.class), properties, paths,
                new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.removeTask("a1", null);

        verify(terminal).closeViewerWindow("jagt");
    }

    @Test
    void ideOpensTheDeployWorktreeForATaskStuckInDeployConflict(@TempDir Path root) throws Exception {
        Path repo = Files.createDirectories(root.resolve("repo"));
        Path deployWorktree = Files.createDirectories(root.resolve("ABC-1-deploy"));   // sibling of the repo
        Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-sng"));
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT)
                .alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.project("proj")).thenReturn(new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()));
        EditorDriver editor = mock(EditorDriver.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                notifier, properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                notifier, properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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

        OrchestratorTools.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".idea").resolve("runConfigurations").resolve("App.xml"))
                .exists().hasContent("<configuration/>");
    }

    @Test
    void copiesDatabaseConnectionsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        java.nio.file.Files.createDirectories(project.resolve(".idea").resolve("dataSources"));
        java.nio.file.Files.writeString(project.resolve(".idea").resolve("dataSources.xml"), "<dataSource/>");
        java.nio.file.Files.writeString(project.resolve(".idea").resolve("dataSources.local.xml"), "<local/>");
        java.nio.file.Files.writeString(project.resolve(".idea").resolve("dataSources").resolve("pg.xml"), "<db/>");
        Path worktree = root.resolve("PAN-1-repo");

        OrchestratorTools.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".idea").resolve("dataSources.xml")).exists().hasContent("<dataSource/>");
        assertThat(worktree.resolve(".idea").resolve("dataSources.local.xml")).exists().hasContent("<local/>");
        assertThat(worktree.resolve(".idea").resolve("dataSources").resolve("pg.xml")).exists().hasContent("<db/>");
    }

    @Test
    void copiesModernDotRunConfigurationsIntoTheWorktree(@TempDir Path root) throws Exception {
        Path project = root.resolve("repo");
        java.nio.file.Files.createDirectories(project.resolve(".run"));
        java.nio.file.Files.writeString(project.resolve(".run").resolve("App.run.xml"), "<configuration/>");
        Path worktree = root.resolve("PAN-1-repo");

        OrchestratorTools.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".run").resolve("App.run.xml")).exists().hasContent("<configuration/>");
    }

    @Test
    void doesNotFailWhenBaseProjectHasNoSharedRunConfigurations(@TempDir Path root) {
        Path project = root.resolve("repo");
        Path worktree = root.resolve("PAN-1-repo");

        OrchestratorTools.copyIdeProjectFiles(project, worktree);

        assertThat(worktree.resolve(".idea")).doesNotExist();
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
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class),
                editor, mock(TerminalDriver.class), mock(UserNotifier.class), properties, paths,
                new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), editor, mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.openInIde("a1", "project", null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS,    true,  false, PROCEED",
            "IN_PROGRESS,    false, false, PROCEED",
            "REVIEW_PENDING, true,  false, PROCEED",
            "SHIPPING,       false, false, PROCEED",
            "SHIPPING,       true,  false, REFUSE",
            "CI_POLLING,     false, true,  PROCEED",
            "CI_FAILED,      false, true,  PROCEED",
            "DEPLOYED,       false, true,  PROCEED",
            "CI_POLLING,     false, false, REFUSE",
            "DEPLOYED,       false, false, REFUSE",
            "NEW,            true,  false, REFUSE",
            "DONE,           false, true,  REFUSE"
    })
    void shipGateProceedsOrRefusesByStatusAndAgentLiveness(
            TaskStatus status, boolean agentLive, boolean hasMr, OrchestratorTools.ShipGate expected) {
        assertThat(OrchestratorTools.shipGate(status, agentLive, hasMr)).isEqualTo(expected);
    }

    @Test
    void rejectsCiPollingStatusWhenMessageCarriesNoMrLink(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.updateAgentStatus("CI_POLLING", "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void rejectsStatusUpdateWhenSubAgentTargetsSiblingTask(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("OTHER-1", TaskState.builder("proj", "/other", TaskStatus.IN_PROGRESS).alias("o1").build());
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class), state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.updateAgentStatus("IN_PROGRESS", "root cause\nanalysis ".repeat(20), "ABC-1", null);

        String stored = state.task("ABC-1").orElseThrow().message();
        assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("...");
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature/X", "../escape", "a b"})
    void rejectsTaskIdBeforeTouchingGitWhenItCannotBeABranchName(String unsafeTaskId, @TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        GitService git = mock(GitService.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        assertThatThrownBy(() -> tools.initializeTask(unsafeTaskId, "proj", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
        verifyNoInteractions(git);
    }

    @Test
    void rejectsUnknownModeBeforeTouchingGit(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        GitService git = mock(GitService.class);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), git, mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        assertThatThrownBy(() -> tools.initializeTask("ABC-1", "proj", null, "bogus", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mode");
        verifyNoInteractions(git);
    }

    @Test
    void removesFreshWorktreeAndBranchWhenContextSetupFailsAfterCheckout(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
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
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        assertThatThrownBy(() -> tools.initializeTask("ABC-9", "proj", null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);

        verify(git).removeWorktree(projectPath.toAbsolutePath().normalize(),
                root.resolve("ABC-9-proj"), "ABC-9");
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
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.deployTask("a1", null);

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.DEPLOYED);
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
        OrchestratorTools tools = new OrchestratorTools(config, state, git, mock(TmuxService.class), editor,
                mock(TerminalDriver.class), mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release/sng", "release/sng", null));
        GitService git = mock(GitService.class);
        OrchestratorTools tools = new OrchestratorTools(config, state, git,
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

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
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class),
                mock(TmuxService.class), mock(EditorDriver.class), mock(TerminalDriver.class),
                mock(UserNotifier.class), properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        assertThatThrownBy(() -> tools.deployTask("ABC-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deployBranch");
    }

    @Test
    void refusesDeployWhenCalledBySubAgent(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        OrchestratorTools tools = new OrchestratorTools(mock(ConfigService.class),
                new StateService(new JsonMapper(), paths), mock(GitService.class), mock(TmuxService.class),
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        assertThatThrownBy(() -> tools.deployTask("ABC-1", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master-only");
    }

    @Test
    void nudgesRunningAgentWhenTaskContextIsUpdated(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(TmuxService.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.writeTaskContext("a1", "new instructions");

        assertThat(result).contains("nudged");
    }

    @Test
    void respawnsADownSessionWhenWriteTaskContextTargetsIt(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).alias("a1").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        TmuxService tmux = mock(TmuxService.class);
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(TmuxService.WindowState.MISSING);
        OrchestratorTools tools = new OrchestratorTools(config, state, mock(GitService.class), tmux,
                mock(EditorDriver.class), mock(TerminalDriver.class), mock(UserNotifier.class),
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        String result = tools.writeTaskContext("a1", "new instructions");

        assertThat(result).contains("respawned");
        verify(tmux).openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false));
    }

    @Test
    void assignsNextFreeAliasWhenTicketLetterAlreadyInUse(@TempDir Path root) throws Exception {
        java.nio.file.Files.createDirectories(root.resolve("ABC-2-proj"));
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())
                .withAgentPrompt("prompt");
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        StateService state = new StateService(new JsonMapper(), paths);
        state.putTask("ABC-1", TaskState.builder("proj", "/first", TaskStatus.IN_PROGRESS).alias("a1").build());
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
                properties, paths, new PromptTemplates(), new ClaudeAgentRuntime(OrchestratorProperties.defaults()));

        tools.initializeTask("ABC-2", "proj", null, null, null, null, null);

        assertThat(state.task("ABC-2").orElseThrow().alias()).isEqualTo("a2");
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
                .contains("create one via your code-host MCP");
    }

    @Test
    void reviewRoundShipCommitLeadsWithTheTaskIdButNotTheFullTitle() {
        String instruction = OrchestratorTools.shipInstruction(false, "ABC-42 Widget layout is off",
                "ABC-42", "dev", "");

        assertThat(instruction)
                .contains("STARTS with \"ABC-42\"")
                .doesNotContain("EXACTLY this message")
                .doesNotContain("ABC-42 Widget layout is off");
    }

}
