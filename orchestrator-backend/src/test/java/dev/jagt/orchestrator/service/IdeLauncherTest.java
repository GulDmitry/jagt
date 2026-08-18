package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.EditorDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which directory a human is shown, which is not always the task's own worktree. */
class IdeLauncherTest {

    private final EditorDriver editor = mock(EditorDriver.class);
    private final ConfigService config = mock(ConfigService.class);
    private final GitService git = mock(GitService.class);

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    private IdeLauncher launcher(StateService state) {
        return new IdeLauncher(state, config, git, editor);
    }

    @Test
    void opensTheDeployWorktreeOfTheRepositoryThatActuallyOwnsTheConflict(@TempDir Path root) throws Exception {
        Path api = Files.createDirectories(root.resolve("one/api"));
        Path web = Files.createDirectories(root.resolve("two/web"));
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder(List.of(dev.jagt.orchestrator.model.TaskRepo.of("api",
                        root.resolve("one/ABC-1-api").toString()),
                dev.jagt.orchestrator.model.TaskRepo.of("web", root.resolve("two/ABC-1-web").toString())),
                TaskStatus.DEPLOY_CONFLICT).alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig(api.toString(), "origin/main", "dev", List.of()),
                "web", new ProjectConfig(web.toString(), "origin/main", "dev", List.of()))));
        when(git.hasDeployWorktree(api, "ABC-1")).thenReturn(false);
        when(git.hasDeployWorktree(web, "ABC-1")).thenReturn(true);

        launcher(state).open("a1", null);

        verify(editor).open(GitService.deployWorktreePath(web, "ABC-1"));
        verify(editor, never()).open(GitService.deployWorktreePath(api, "ABC-1"));
    }

    @Test
    void opensTheTaskWorktreeWhenAConflictedProjectHasLeftTheConfiguration(@TempDir Path root) throws Exception {
        Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("gone", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT)
                .alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));

        String out = launcher(state).open("a1", null);

        verify(editor).open(taskWorktree);
        assertThat(out).contains("as a project in the editor");
    }

    @Test
    void ideOpensTheDeployWorktreeForATaskStuckInDeployConflict(@TempDir Path root) throws Exception {
        Path repo = Files.createDirectories(root.resolve("repo"));
        Path deployWorktree = Files.createDirectories(root.resolve("ABC-1-deploy"));   // sibling of the repo
        Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT)
                .alias("a1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("proj", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
        when(git.hasDeployWorktree(repo, "ABC-1")).thenReturn(true);

        String out = launcher(state).open("a1", null);

        // The conflict lives on the deploy side — `ide` must open THAT, never the (clean) task worktree.
        verify(editor).open(deployWorktree);
        verify(editor, never()).open(taskWorktree);
        assertThat(out).contains("deploy worktree");
    }

    @Test
    void opensStaticDiffAgainstBaseWhenModeIsDiff(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(git.checkoutBaseForDiff(any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/base"));
        when(git.checkoutWorktreeCleanForDiff(any(), any(), any(), any())).thenReturn(java.nio.file.Path.of("/tmp/clean"));

        launcher(state).open("a1", "diff");

        verify(editor).openDiff(java.nio.file.Path.of("/tmp/base"), java.nio.file.Path.of("/tmp/clean"));
    }

    @Test
    void opensWorktreeAsProjectByDefault(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        launcher(state).open("a1", null);

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }

    @Test
    void opensWorktreeAsProjectWhenModeIsProject(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        launcher(state).open("a1", "project");

        verify(editor).open(java.nio.file.Path.of("/wt"));
    }
}
