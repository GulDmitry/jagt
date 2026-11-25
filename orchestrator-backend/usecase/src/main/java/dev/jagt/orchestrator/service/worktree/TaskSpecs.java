package dev.jagt.orchestrator.service.worktree;

import dev.jagt.orchestrator.port.worktree.Specs;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The specs each repository of a task records its behaviour in, read in whichever of the task's
 * worktrees holds them.
 */
@Component
@RequiredArgsConstructor
public class TaskSpecs {

  private final ConfigService configService;
  private final Specs specs;

  /** Why the task's change may not be handed back yet, as its session is to read it. */
  public Optional<String> owed(String taskId, TaskState task) {
    return homes(task).stream()
        .map(home ->
            home.worktree() == null
                ? Optional.of(home.unheld())
                : specs
                    .owed(home.worktree(), home.root(), taskId)
                    .map(owed -> "[" + home.project() + "] " + owed))
        .flatMap(Optional::stream)
        .findFirst();
  }

  /** Folds the task's open changes into every specs folder it reaches, throwing what refused it. */
  public void fold(String taskId, TaskState task) {
    for (Home home : homes(task)) {
      if (home.worktree() == null) {
        throw new IllegalStateException(home.unheld());
      }
      specs.fold(home.worktree(), home.root(), taskId);
    }
  }

  private List<Home> homes(TaskState task) {
    return task.repos().stream().map(repo -> home(task, repo)).distinct().toList();
  }

  private Home home(TaskState task, TaskRepo repo) {
    String own = Objects.toString(repo.project(), "");
    ProjectConfig.SpecRoot root = specRoot(own);
    String project =
        root == null || root.project() == null || root.project().isBlank() ? own : root.project();
    @Nullable String path = root == null ? null : root.path();
    TaskRepo holder =
        project.equals(own)
            ? repo
            : task.repos().stream()
                .filter(held -> project.equals(held.project()))
                .findFirst()
                .orElse(TaskRepo.of(project, null));
    String worktree = holder.worktreePath();
    return new Home(project, worktree == null ? null : Path.of(worktree), path);
  }

  private ProjectConfig.@Nullable SpecRoot specRoot(String project) {
    ProjectConfig config = configService.load().projects().get(project);
    return config == null ? null : config.specs();
  }

  private record Home(String project, @Nullable Path worktree, @Nullable String root) {

    String unheld() {
      return "["
          + project
          + "] this task's specs live in "
          + project
          + (root == null ? "" : " at " + root)
          + ", which it holds no worktree of: report outcome=question with needsProject: "
          + project
          + " to have it added, then open the change there";
    }
  }
}
