package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Whether a task changed a path its project keeps for a human. */
@Component
@RequiredArgsConstructor
class HumanPaths {

  /** Kept, since a path nobody could read may be one of them. */
  static final String UNREAD = "unread";

  private final ConfigService configService;
  private final WorktreeChanges changes;

  /** The first such path as {@code project/path}, or {@link #UNREAD}; empty where none is kept. */
  Optional<String> touched(TaskState task) {
    if (task.repos().stream().anyMatch(repo -> repo.project() == null)) {
      return Optional.of(UNREAD);
    }
    Map<String, List<String>> kept;
    try {
      kept =
          task.repos().stream()
              .map(TaskRepo::project)
              .map(Objects::requireNonNull)
              .distinct()
              .collect(
                  Collectors.toMap(
                      project -> project, project -> configService.project(project).humanPaths()));
    } catch (IllegalArgumentException unknownProject) {
      return Optional.of(UNREAD);
    }
    if (kept.values().stream().allMatch(List::isEmpty)) {
      return Optional.empty();
    }
    return changes
        .paths(task)
        .map(paths ->
            paths.entrySet().stream()
                .flatMap(repo ->
                    WorktreeFiles.firstMatching(
                        kept.getOrDefault(repo.getKey(), List.of()), repo.getValue())
                        .map(path -> repo.getKey() + "/" + path)
                        .stream())
                .findFirst())
        .orElse(Optional.of(UNREAD));
  }
}
