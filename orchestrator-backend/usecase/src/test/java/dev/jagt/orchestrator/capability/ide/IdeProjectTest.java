package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdeProjectTest {

    private final EditorDriver editor = mock(EditorDriver.class);
    private final ConfigService config = mock(ConfigService.class);
    private final GitDeploy deploys = mock(GitDeploy.class);

    @Test
    void opensTheDeployWorktreeOfTheRepositoryThatActuallyOwnsTheConflict(@TempDir Path root) throws Exception {
        Path api = Files.createDirectories(root.resolve("one/api"));
        Path web = Files.createDirectories(root.resolve("two/web"));
        TaskState task = TaskState.builder(List.of(
                        TaskRepo.of("api", root.resolve("one/ABC-1-api").toString()),
                        TaskRepo.of("web", root.resolve("two/ABC-1-web").toString())),
                TaskStatus.DEPLOY_CONFLICT).build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of(
                "api", new ProjectConfig(api.toString(), "origin/main", "dev", List.of()),
                "web", new ProjectConfig(web.toString(), "origin/main", "dev", List.of()))));
        when(deploys.hasDeployWorktree(api, "ABC-1")).thenReturn(false);
        when(deploys.hasDeployWorktree(web, "ABC-1")).thenReturn(true);

        new IdeProject(config, deploys, editor).open("ABC-1", task);

        verify(editor).open(GitDeploy.deployWorktreePath(web, "ABC-1"));
        verify(editor, never()).open(GitDeploy.deployWorktreePath(api, "ABC-1"));
    }

    @Test
    void saysItIsTheDeployWorktreeItOpenedForAConflict(@TempDir Path root) throws Exception {
        Path repo = Files.createDirectories(root.resolve("repo"));
        TaskState task = TaskState.builder("proj", root.resolve("ABC-1-demo").toString(),
                TaskStatus.DEPLOY_CONFLICT).build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(
                Map.of("proj", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
        when(deploys.hasDeployWorktree(repo, "ABC-1")).thenReturn(true);

        String out = new IdeProject(config, deploys, editor).open("ABC-1", task);

        assertThat(out).contains("deploy worktree");
    }

    @Test
    void opensTheTaskWorktreeWhenAConflictedProjectHasLeftTheConfiguration(@TempDir Path root) throws Exception {
        Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        TaskState task = TaskState.builder("gone", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT)
                .build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));

        String out = new IdeProject(config, deploys, editor).open("ABC-1", task);

        verify(editor).open(taskWorktree);
        assertThat(out).contains("as a project in the editor");
    }

    @Test
    void showsTheHumanTheTasksOwnWorktreeAsAProject() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        new IdeProject(config, deploys, editor).open("ABC-1", task);

        verify(editor).open(Path.of("/wt"));
    }

    @Test
    void opensAWorktreePerRepositoryWhenTheTaskSpansTwoProjects() {
        TaskState task = TaskState.builder(List.of(
                        TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.REVIEW_PENDING).build();

        new IdeProject(config, deploys, editor).open("ABC-1", task);

        verify(editor).open(Path.of("/api-wt"));
        verify(editor).open(Path.of("/web-wt"));
    }
}
