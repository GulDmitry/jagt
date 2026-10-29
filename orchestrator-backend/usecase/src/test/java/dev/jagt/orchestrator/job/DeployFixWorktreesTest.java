package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployFixWorktreesTest {

    private final DeployTargets targets = mock(DeployTargets.class);
    private final GitDeploy gitDeploy = mock(GitDeploy.class);

    @Test
    void opensTheWorktreeInTheRepositoryWhosePipelineWentRed() {
        TaskState task = TaskState.builder(List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.DEPLOYED).build();
        when(targets.all(task)).thenReturn(List.of(
                new DeployTargets.Target("api", new ProjectConfig("/src/api", "origin/main", "dev", null)),
                new DeployTargets.Target("web", new ProjectConfig("/src/web", "origin/main", "test", null))));

        new DeployFixWorktrees(targets, gitDeploy).open(task, "ABC-42", "web");

        verify(gitDeploy).openFixWorktree(Path.of("/src/web"), "ABC-42", "test");
    }
}
