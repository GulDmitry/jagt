package dev.jagt.orchestrator.job.deploy;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.session.SessionStanding;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.service.git.GitRevert;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeployConflictsTest {

  @TempDir Path root;
  private final ConfigService config = mock(ConfigService.class);
  private final GitDeploy git = mock(GitDeploy.class);
  private final GitRevert revert = mock(GitRevert.class);
  private final Deployment.Target web = Deployment.Target.of("web", "dev");
  private StateService state;
  private DeployConflicts service;

  @BeforeEach
  void onlyWebIsConfiguredUnderSrc() {
    state = stateService().withRoot(root).build();
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of("web", new ProjectConfig("/src/web", "origin/main", "dev", List.of()))));
    service = new DeployConflicts(new DeployTargets(config), git, revert);
  }

  @Test
  void findsConflictInTheRepositoryItsRecordNames() {
    state.putTask(
        "ABC-1",
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        Deployment.Target.of("api", "dev"),
                        web.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))),
                    1,
                    null))
            .build());
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "api",
                        new ProjectConfig("/repo/api", "origin/main", "dev", List.of()),
                        "web",
                        new ProjectConfig("/src/web", "origin/main", "dev", List.of()))));
    when(git.hasDeployWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);
    when(git.deployWorktreePath(Path.of("/src/web"), "ABC-1"))
        .thenReturn(Path.of("/data/worktrees/web/ABC-1-deploy"));
    when(git.deployResolved(Path.of("/src/web"), "ABC-1", "dev")).thenReturn(true);
    when(git.deployProgress(Path.of("/src/web"), "ABC-1"))
        .thenReturn(new Conflict.Progress(0, 2, "d"));

    var conflicts = service.waiting("ABC-1", state.task("ABC-1").orElseThrow());

    assertThat(conflicts)
        .contains(
            new DeployConflicts.WaitingConflict(
                Path.of("/data/worktrees/web/ABC-1-deploy"),
                true,
                new Conflict.Progress(0, 2, "d"),
                null,
                false,
                new SessionStanding(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));
  }

  @Test
  void leavesPausedTasksConflictAlone() {
    state.putTask(
        "ABC-1",
        TaskState.builder(List.of(TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT)
            .stop(new TaskStop(TaskStop.Trigger.PAUSE, 1, "you paused it"))
            .deployment(
                new Deployment(
                    1, List.of(web.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))), 0, null))
            .build());
    when(git.hasDeployWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);

    var conflicts = service.waiting("ABC-1", state.task("ABC-1").orElseThrow());

    assertThat(conflicts).isEmpty();
  }

  @Test
  void findsNoConflictWhoseWorktreeIsGone() {
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1, List.of(web.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))), 0, null))
            .build();

    var conflicts = service.waiting("ABC-1", task);

    assertThat(conflicts).isEmpty();
  }

  @Test
  void namesTheRedPipelineConflictWasHandedBackFor() {
    state.putTask(
        "ABC-1",
        TaskState.builder(List.of(TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT)
            .message(
                "red pipeline on the deploy commit: web 01234567 https://git.example.invalid/p/7")
            .deployment(
                new Deployment(
                    1,
                    List.of(web.withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))),
                    0,
                    null))
            .build());
    when(git.hasDeployWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);
    when(git.deployProgress(Path.of("/src/web"), "ABC-1"))
        .thenReturn(new Conflict.Progress(0, 0, "d"));

    var conflicts = service.waiting("ABC-1", state.task("ABC-1").orElseThrow());

    assertThat(conflicts.orElseThrow().red())
        .isEqualTo(
            "red pipeline on the deploy commit: web 01234567 https://git.example.invalid/p/7");
  }

  @Test
  void findsRevertConflictFromItsRecordAndTheRevertWorktree() {
    state.putTask(
        "ABC-1",
        TaskState.builder(List.of(TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOY_CONFLICT)
            .deployment(
                new Deployment(
                    1,
                    List.of(
                        web.withMerge("cafebabe1234")
                            .withConflict(Conflict.of(Deployment.Cause.REVERT, 1))),
                    0,
                    null))
            .build());
    when(revert.hasRevertWorktree(Path.of("/src/web"), "ABC-1")).thenReturn(true);
    when(revert.revertWorktreePath(Path.of("/src/web"), "ABC-1"))
        .thenReturn(Path.of("/data/worktrees/web/ABC-1-revert"));
    when(revert.revertResolved(Path.of("/src/web"), "ABC-1")).thenReturn(true);
    when(revert.revertProgress(Path.of("/src/web"), "ABC-1"))
        .thenReturn(new Conflict.Progress(0, 1, "r"));

    var conflicts = service.waiting("ABC-1", state.task("ABC-1").orElseThrow());

    assertThat(conflicts)
        .contains(
            new DeployConflicts.WaitingConflict(
                Path.of("/data/worktrees/web/ABC-1-revert"),
                true,
                new Conflict.Progress(0, 1, "r"),
                null,
                true,
                new SessionStanding(TaskStatus.DEPLOY_CONFLICT, "NONE", 0, 0)));
  }
}
