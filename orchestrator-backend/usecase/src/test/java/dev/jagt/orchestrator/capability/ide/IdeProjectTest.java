package dev.jagt.orchestrator.capability.ide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdeProjectTest {

  private final EditorDriver editor = mock(EditorDriver.class);
  private final ConfigService config = mock(ConfigService.class);
  private final GitDeploy deploys = mock(GitDeploy.class);

  @Test
  void opensTheDeployWorktreeOfTheRepositoryThatActuallyOwnsTheConflict(@TempDir Path root)
      throws Exception {
    Path api = Files.createDirectories(root.resolve("one/api"));
    Path web = Files.createDirectories(root.resolve("two/web"));
    final TaskState task =
        TaskState.builder(
                List.of(
                    TaskRepo.of("api", root.resolve("one/ABC-1-api").toString()),
                    TaskRepo.of("web", root.resolve("two/ABC-1-web").toString())),
                TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("api", "dev"),
                        Deployment.Target.of("web", "dev")
                            .withConflict(Conflict.of(Deployment.Cause.MERGE, 1))),
                    1,
                    null))
            .build();
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "api", new ProjectConfig(api.toString(), "origin/main", "dev", List.of()),
                        "web",
                            new ProjectConfig(web.toString(), "origin/main", "dev", List.of()))));
    when(deploys.hasDeployWorktree(api, "ABC-1")).thenReturn(false);
    when(deploys.hasDeployWorktree(web, "ABC-1")).thenReturn(true);
    when(deploys.deployWorktreePath(api, "ABC-1"))
        .thenReturn(Path.of("/data/worktrees/api/ABC-1-deploy"));
    when(deploys.deployWorktreePath(web, "ABC-1"))
        .thenReturn(Path.of("/data/worktrees/web/ABC-1-deploy"));

    new IdeProject(new DeployTargets(config), deploys, editor).open("ABC-1", task);

    verify(editor).open(Path.of("/data/worktrees/web/ABC-1-deploy"));
    verify(editor, never()).open(Path.of("/data/worktrees/api/ABC-1-deploy"));
  }

  @Test
  void saysItIsTheDeployWorktreeItOpenedForConflict(@TempDir Path root) throws Exception {
    Path repo = Files.createDirectories(root.resolve("repo"));
    TaskState task =
        TaskState.builder("proj", root.resolve("ABC-1-demo").toString(), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("proj", "dev")
                            .withConflict(Conflict.of(Deployment.Cause.MERGE, 1))),
                    0,
                    null))
            .build();
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "proj",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
    when(deploys.hasDeployWorktree(repo, "ABC-1")).thenReturn(true);

    String out = new IdeProject(new DeployTargets(config), deploys, editor).open("ABC-1", task);

    assertThat(out).contains("deploy worktree");
  }

  @Test
  void opensTheTaskWorktreeWhenConflictedProjectHasLeftTheConfiguration(@TempDir Path root)
      throws Exception {
    Path taskWorktree = Files.createDirectories(root.resolve("ABC-1-demo"));
    TaskState task =
        TaskState.builder("gone", taskWorktree.toString(), TaskStatus.DEPLOY_CONFLICT).build();
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of()));

    String out = new IdeProject(new DeployTargets(config), deploys, editor).open("ABC-1", task);

    verify(editor).open(taskWorktree);
    assertThat(out).contains("as a project in the editor");
  }

  @Test
  void showsTheHumanTheTasksOwnWorktreeAsProject() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    new IdeProject(new DeployTargets(config), deploys, editor).open("ABC-1", task);

    verify(editor).open(Path.of("/wt"));
  }

  @Test
  void opensWorktreePerRepositoryWhenTheTaskSpansTwoProjects() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.REVIEW_PENDING)
            .build();

    new IdeProject(new DeployTargets(config), deploys, editor).open("ABC-1", task);

    verify(editor).open(Path.of("/api-wt"));
    verify(editor).open(Path.of("/web-wt"));
  }

  @Test
  void opensTheRevertWorktreeWhereTheConflictIsRevertsAndSaysToRevertAgain(@TempDir Path root)
      throws Exception {
    Path repo = Files.createDirectories(root.resolve("repo"));
    TaskState task =
        TaskState.builder("proj", root.resolve("ABC-1-demo").toString(), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("proj", "dev")
                            .withMerge("cafebabe1234")
                            .withConflict(Conflict.of(Deployment.Cause.REVERT, 1))),
                    0,
                    null))
            .build();
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "proj",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));
    when(deploys.hasRevertWorktree(repo, "ABC-1")).thenReturn(true);
    when(deploys.revertWorktreePath(repo, "ABC-1"))
        .thenReturn(Path.of("/data/worktrees/proj/ABC-1-revert"));

    String out = new IdeProject(new DeployTargets(config), deploys, editor).open("ABC-1", task);

    verify(editor).open(Path.of("/data/worktrees/proj/ABC-1-revert"));
    assertThat(out).contains("revert worktree", "`revert ABC-1` again");
  }
}
