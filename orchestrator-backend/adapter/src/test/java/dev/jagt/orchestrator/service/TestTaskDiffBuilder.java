package dev.jagt.orchestrator.service;

import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.With;

@With
public record TestTaskDiffBuilder(Path repo, Path worktree) {

  public static TestTaskDiffBuilder taskDiff() {
    return new TestTaskDiffBuilder(Path.of("/repo"), Path.of("/wt"));
  }

  public TaskDiff build(StateService state, ConfigService config, Processes runner) {
    when(state.canonicalTaskId("ABC-1")).thenReturn("ABC-1");
    when(config.project("proj"))
        .thenReturn(new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()));
    when(state.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder(
                        List.of(TaskRepo.of("proj", worktree.toString())),
                        TaskStatus.REVIEW_PENDING)
                    .build()));
    GitCommands commands = GitRepos.commands(runner);
    return new TaskDiff(state, new DiffBases(config), new DiffCheckouts(commands), commands);
  }
}
