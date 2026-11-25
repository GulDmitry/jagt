package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.task.launch.TaskName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The two throwaway checkouts a diff viewer compares: the base, and the task's tree as it stands.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiffCheckouts {

  private final GitCommands git;
  private final Host host;

  /**
   * Both throwaway checkouts a diff leaves in the temp directory, so retiring a task can find them
   * again. The project is part of the name: the repositories of one task would otherwise clear each
   * other's checkout.
   */
  public static List<Path> diffWorktreePaths(Host host, String taskId, String project) {
    Path temp = host.temp();
    String slug = TaskName.slug(taskId) + "-" + TaskName.slug(project);
    return List.of(temp.resolve("jagt-diff-" + slug), temp.resolve("jagt-diff-new-" + slug));
  }

  /**
   * A throwaway detached checkout at the base branch, reused per task until the task is retired.
   */
  public Path checkoutBaseForDiff(
      Path projectPath, String baseBranch, String taskId, String project) {
    return git.locked(
        projectPath,
        () -> {
          git.run(projectPath, List.of("git", "fetch", "--prune"))
              .expectSuccess("git fetch in " + projectPath);
          Path temp = diffWorktreePaths(host, taskId, project).getFirst();
          git.clearWorktreePath(projectPath, temp);
          git.run(
                  projectPath,
                  List.of("git", "worktree", "add", "--detach", temp.toString(), baseBranch))
              .expectSuccess("git worktree add (diff base) " + temp);
          return temp;
        });
  }

  /**
   * The task's CURRENT tracked state, committed or not, snapshotted through a throwaway index so
   * {@code .gitignore} and {@code .git/info/exclude} are honored. Reused per task until the task is
   * retired.
   */
  public Path checkoutWorktreeCleanForDiff(
      Path worktreePath, Path projectPath, String baseBranch, String taskId, String project) {
    return git.locked(
        projectPath,
        () -> {
          Path temp = diffWorktreePaths(host, taskId, project).getLast();
          git.clearWorktreePath(projectPath, temp);
          String tree = snapshotTree(worktreePath, taskId);
          String commit =
              git.run(
                      worktreePath,
                      List.of(
                          "git",
                          "commit-tree",
                          tree,
                          "-p",
                          baseBranch,
                          "-m",
                          "jagt diff " + taskId))
                  .expectSuccess("git commit-tree (clean diff) " + taskId)
                  .stdout()
                  .trim();
          git.run(
                  projectPath,
                  List.of("git", "worktree", "add", "--detach", temp.toString(), commit))
              .expectSuccess("git worktree add (clean diff) " + temp);
          return temp;
        });
  }

  /** The tree of the worktree's tracked and untracked files, ignored ones left out. */
  public String snapshotTree(Path worktreePath, String taskId) {
    Path index;
    try {
      index = Files.createTempFile("jagt-diff-index-" + TaskName.slug(taskId) + "-", "");
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot allocate temp git index for diff of " + taskId, e);
    }
    try {
      Map<String, String> env = Map.of("GIT_INDEX_FILE", index.toString());
      git.run(worktreePath, env, List.of("git", "read-tree", "HEAD"))
          .expectSuccess("git read-tree (clean diff) " + taskId);
      git.run(worktreePath, env, List.of("git", "add", "-A"))
          .expectSuccess("git add -A (clean diff) " + taskId);
      return git.run(worktreePath, env, List.of("git", "write-tree"))
          .expectSuccess("git write-tree (clean diff) " + taskId)
          .stdout()
          .trim();
    } finally {
      try {
        Files.deleteIfExists(index);
      } catch (IOException e) {
        log.atWarn()
            .setMessage("temp diff index delete failed")
            .addKeyValue("path", index)
            .addKeyValue("cause", e.toString())
            .log();
      }
    }
  }
}
