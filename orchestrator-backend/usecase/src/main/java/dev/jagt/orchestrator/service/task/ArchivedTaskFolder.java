package dev.jagt.orchestrator.service.task;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.launch.TaskName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Moves what a finished task left in its folder into a snapshot of that task, never over a
 * snapshot's own copy, or drops the folder where nothing is left. A folder still holding a worktree
 * stays put: moving it would break the checkout.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArchivedTaskFolder {

  private static final String TASK = "task";

  private final OrchestratorPaths paths;
  private final Clock clock;

  /** {@code snapshot} is the one the task's own record went into, else a fresh one is cut. */
  public void shelve(String taskId, @Nullable Path snapshot) {
    Path folder = paths.task(taskId);
    try {
      if (!Files.isDirectory(folder) || holdsWorktree(folder)) {
        return;
      }
      Files.deleteIfExists(folder.resolve("worktrees"));
      if (isEmpty(folder)) {
        Files.delete(folder);
        return;
      }
      Path into = roomFor(TaskName.slug(taskId), snapshot).resolve(TASK);
      Files.createDirectories(into.getParent());
      Files.move(folder, into);
      log.atInfo()
          .setMessage("task folder archived")
          .addKeyValue("task", taskId)
          .addKeyValue("to", into)
          .log();
    } catch (IOException e) {
      log.atWarn()
          .setMessage("task folder not archived")
          .addKeyValue("task", taskId)
          .addKeyValue("path", folder)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "the folder stays under tasks")
          .log();
    }
  }

  private Path roomFor(String slug, @Nullable Path snapshot) {
    LocalDateTime at = LocalDateTime.now(clock);
    Path into = snapshot;
    while (into == null || Files.exists(into.resolve(TASK))) {
      into = named(at, slug);
      at = at.plusSeconds(1);
    }
    return into;
  }

  private Path named(LocalDateTime at, String slug) {
    return paths.layout().archive().resolve(SnapshotName.of(at, slug));
  }

  private static boolean holdsWorktree(Path folder) throws IOException {
    Path worktrees = folder.resolve("worktrees");
    return Files.isDirectory(worktrees) && !isEmpty(worktrees);
  }

  private static boolean isEmpty(Path folder) throws IOException {
    try (Stream<Path> entries = Files.list(folder)) {
      return entries.findAny().isEmpty();
    }
  }
}
