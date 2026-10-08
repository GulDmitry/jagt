package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdeLauncherTest {

    private final EditorDriver editor = mock(EditorDriver.class);
    private final ConfigService config = mock(ConfigService.class);
    private final GitDeploy deploys = mock(GitDeploy.class);
    private final DiffCheckouts diffs = mock(DiffCheckouts.class);

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    private IdeLauncher launcher(StateService state) {
        return new IdeLauncher(state, config, deploys, diffs, editor);
    }

    @Test
    void opensTheDeployWorktreeOfTheRepositoryThatActuallyOwnsTheConflict(@TempDir Path root) throws Exception {
        Path api = Files.createDirectories(root.resolve("one/api"));
        Path web = Files.createDirectories(root.resolve("two/web"));
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(
                        TaskRepo.of("api", root.resolve("one/ABC-1-api").toString()),
                        TaskRepo.of("web", root.resolve("two/ABC-1-web").toString())),
                TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig(api.toString(), "origin/main", "dev", List.of()),
                "web", new ProjectConfig(web.toString(), "origin/main", "dev", List.of()))));
        when(deploys.hasDeployWorktree(api, "ABC-1")).thenReturn(false);
        when(deploys.hasDeployWorktree(web, "ABC-1")).thenReturn(true);

        launcher(state).open("a1", IdeLauncher.Mode.PROJECT);

        verify(editor).open(GitDeploy.deployWorktreePath(web, "ABC-1"));
        verify(editor, never()).open(GitDeploy.deployWorktreePath(api, "ABC-1"));
    }

    @Test
    void saysItIsTheDeployWorktreeItOpenedForAConflict(@TempDir Path root) throws Exception {
        Path repo = Files.createDirectories(root.resolve("repo"));
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", root.resolve("ABC-1-demo").toString(),
                TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("proj", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
        when(deploys.hasDeployWorktree(repo, "ABC-1")).thenReturn(true);

        assertThat(launcher(state).open("a1", IdeLauncher.Mode.PROJECT)).contains("deploy worktree");
    }

    @Test
    void opensTheTaskWorktreeWhenAConflictedProjectHasLeftTheConfiguration(@TempDir Path root) throws Exception {
        Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("gone", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT)
                .alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));

        String out = launcher(state).open("a1", IdeLauncher.Mode.PROJECT);

        verify(editor).open(taskWorktree);
        assertThat(out).contains("as a project in the editor");
    }

    @Test
    void showsTheChangeAgainstTheBaseBranchWhenTheHumanAsksForADiff(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(diffs.checkoutBaseForDiff(any(), any(), any(), any())).thenReturn(Path.of("/tmp/base"));
        when(diffs.checkoutWorktreeCleanForDiff(any(), any(), any(), any(), any())).thenReturn(Path.of("/tmp/clean"));

        launcher(state).open("a1", IdeLauncher.Mode.DIFF);

        verify(editor).openDiff(Path.of("/tmp/base"), Path.of("/tmp/clean"));
    }

    @Test
    void diffsAgainstWhatTheRequestTargetsRatherThanWhereDeployLands(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release/stage", "dev", null));
        when(diffs.checkoutBaseForDiff(any(), any(), any(), any())).thenReturn(Path.of("/tmp/base"));
        when(diffs.checkoutWorktreeCleanForDiff(any(), any(), any(), any(), any())).thenReturn(Path.of("/tmp/clean"));

        launcher(state).open("a1", IdeLauncher.Mode.DIFF);

        verify(diffs).checkoutBaseForDiff(Path.of("/repo"), "origin/release/stage", "ABC-1", "proj");
    }

    @Test
    void showsTheHumanTheTasksOwnWorktreeAsAProject(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        launcher(state).open("a1", IdeLauncher.Mode.PROJECT);

        verify(editor).open(Path.of("/wt"));
    }

    @Test
    void opensAWorktreePerRepositoryWhenTheTaskSpansTwoProjects(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(
                        TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.REVIEW_PENDING).alias("a1").build());

        launcher(state).open("a1", IdeLauncher.Mode.PROJECT);

        verify(editor).open(Path.of("/api-wt"));
        verify(editor).open(Path.of("/web-wt"));
    }

    @Test
    void diffsEveryRepositoryAgainstItsOwnTargetWhenTheTaskSpansTwoProjects(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(
                        TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(config.project("api")).thenReturn(new ProjectConfig("/api-repo", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/next", "dev", null));
        when(diffs.checkoutBaseForDiff(any(), any(), any(), any())).thenReturn(Path.of("/tmp/base"));
        when(diffs.checkoutWorktreeCleanForDiff(any(), any(), any(), any(), any())).thenReturn(Path.of("/tmp/clean"));

        launcher(state).open("a1", IdeLauncher.Mode.DIFF);

        verify(diffs).checkoutBaseForDiff(Path.of("/api-repo"), "origin/main", "ABC-1", "api");
        verify(diffs).checkoutBaseForDiff(Path.of("/web-repo"), "origin/next", "ABC-1", "web");
    }
}
