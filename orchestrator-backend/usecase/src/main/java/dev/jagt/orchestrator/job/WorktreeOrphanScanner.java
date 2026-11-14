package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Point;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.service.WorktreeNoise;
import dev.jagt.orchestrator.service.WorktreeOrphans;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

/**
 * WARNS about worktree directories no task owns: an orphan can hold uncommitted work AND copies of
 * gitignored secrets. Each is reported with how many of those copies it still holds. The one it
 * deletes instead of reporting is jagt's own residue — a directory the IDE wrote back into after
 * the checkout was already gone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorktreeOrphanScanner implements Job {
  @Override
  public String id() {
    return "orphanscan";
  }

  @Override
  public String describe() {
    return "delete jagt's own worktree residue, warn about the leftovers holding work or copied"
        + " secrets";
  }

  @Override
  public @Nullable Duration every() {
    return null;
  }

  private final WorktreeOrphans orphans;
  private final Notifications notifications;

  /**
   * One WARN per leftover directory, plus a single desktop ping for whoever never opens the log.
   */
  @Override
  public void run() {
    List<WorktreeOrphans.Orphan> left =
        orphans.scan().stream().filter(orphan -> !sweep(orphan)).toList();
    if (left.isEmpty()) {
      return;
    }
    int secrets = left.stream().mapToInt(WorktreeOrphans.Orphan::secretFiles).sum();
    left.forEach(orphan ->
        log.atWarn()
            .setMessage("worktree orphaned")
            .addKeyValue("path", orphan.path())
            .addKeyValue("secrets", orphan.secretFiles())
            .addKeyValue("owner", "none")
            .log());
    notifications.send(
        Notification.housekeeping(
            Point.HOUSEKEEPING.id(),
            left.size() + " orphaned worktree(s)",
            secrets > 0
                ? secrets + " copied secret file(s) left on disk — `worktrees` lists them"
                : "left over from a crashed or abandoned task — `worktrees` lists them"));
  }

  /**
   * Deletes what jagt can prove is its own residue and nobody's work: no checkout, no copied
   * secret, and every file in it either the editor's own or empty. The editor rewrites its project
   * files into a path git already emptied, and the husk that leaves outlives the worktree by
   * itself.
   */
  private boolean sweep(WorktreeOrphans.Orphan orphan) {
    if (orphan.secretFiles() > 0
        || Files.exists(orphan.path().resolve(".git"))
        || !holdsOnlyEditorFiles(orphan.path())) {
      return false;
    }
    try {
      FileSystemUtils.deleteRecursively(orphan.path());
      log.atInfo().setMessage("worktree husk deleted").addKeyValue("path", orphan.path()).log();
      return true;
    } catch (IOException e) {
      log.atWarn()
          .setMessage("worktree husk delete failed")
          .addKeyValue("path", orphan.path())
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "reported as an orphan instead")
          .log();
      return false;
    }
  }

  private boolean holdsOnlyEditorFiles(Path directory) {
    boolean[] onlyIdeFiles = {true};
    try {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              return WorktreeNoise.isResidue(dir.getFileName().toString())
                  ? FileVisitResult.SKIP_SUBTREE
                  : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.size() == 0 || WorktreeNoise.isResidue(file.getFileName().toString())) {
                return FileVisitResult.CONTINUE;
              }
              onlyIdeFiles[0] = false;
              return FileVisitResult.TERMINATE;
            }
          });
    } catch (IOException e) {
      log.atWarn()
          .setMessage("husk scan failed")
          .addKeyValue("path", directory)
          .addKeyValue("cause", e.toString())
          .log();
      return false;
    }
    return onlyIdeFiles[0];
  }
}
