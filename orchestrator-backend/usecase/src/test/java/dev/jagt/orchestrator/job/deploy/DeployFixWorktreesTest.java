package dev.jagt.orchestrator.job.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.deploy.DeployTargets;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.testing.FakeJobFaults;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeployFixWorktreesTest {

  private final DeployTargets targets = mock(DeployTargets.class);
  private final GitDeploy gitDeploy = mock(GitDeploy.class);

  @Test
  void opensTheWorktreeInTheRepositoryWhosePipelineWentRed() {
    TaskState task =
        TaskState.builder(
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.DEPLOYED)
            .build();
    when(targets.landed(task))
        .thenReturn(
            List.of(
                new DeployTargets.Target(
                    "api", new ProjectConfig("/src/api", "origin/main", "dev", List.of()), "dev"),
                new DeployTargets.Target(
                    "web",
                    new ProjectConfig("/src/web", "origin/main", "test", List.of()),
                    "test")));

    Optional<String> unopened =
        new DeployFixWorktrees(targets, gitDeploy, new FakeJobFaults()).open(task, "ABC-42", "web");

    verify(gitDeploy).openFixWorktree(Path.of("/src/web"), "ABC-42", "test");
    assertThat(unopened).isEmpty();
  }

  @Test
  void reportsAsItsJobsFailureTheWorktreeItCouldNotOpen() {
    TaskState task =
        TaskState.builder(List.of(TaskRepo.of("web", "/web-wt")), TaskStatus.DEPLOYED).build();
    when(targets.landed(task))
        .thenThrow(new IllegalStateException("the path holds another checkout"));
    FakeJobFaults faults = new FakeJobFaults();

    Optional<String> unopened =
        new DeployFixWorktrees(targets, gitDeploy, faults).open(task, "ABC-42", "web");

    assertThat(unopened).contains("the path holds another checkout");
    assertThat(faults.causes()).containsExactly("the path holds another checkout");
  }
}
