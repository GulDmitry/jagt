package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The base a repository of a task is diffed against: what its request targets. */
@Component
@RequiredArgsConstructor
public class DiffBases {

  /** The project's repository and the tracking ref the diff reads against. */
  public record Base(Path projectPath, String ref) {}

  private final ConfigService configService;

  public Base of(String taskId, TaskState task, TaskRepo repo) {
    ProjectConfig project = configService.project(Objects.requireNonNull(repo.project()));
    String configured = task.baseBranchOr(project.baseBranch());
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException(
          "Project "
              + repo.project()
              + " has no baseBranch in jagt.yml,"
              + " so a diff of "
              + taskId
              + " has nothing to read against");
    }
    String ref = project.gitRemote().track(project.gitRemote().branchOf(configured));
    return new Base(Path.of(project.path()), ref);
  }
}
