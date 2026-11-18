package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.Remote;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Puts a folder's files, those git does not ignore, on a branch of its own repository and pushes
 * that branch alone, from a detached checkout of its own: the human's checkout, local branches and
 * the base branch are never written. A symbolic link is never followed nor committed.
 */
@RequiredArgsConstructor
final class WorkspaceBranch {

  private final GitCommands git;

  /**
   * Whether the branch differs from the base branch, and so was pushed. The branch's remote tip is
   * the start only while {@code fromBranch}, and then only while it carries work not yet in the
   * base; else the start is the base.
   */
  boolean publish(
      String projectKey,
      ProjectConfig project,
      Path folder,
      String branch,
      String message,
      Path checkout,
      boolean fromBranch) {
    Remote remote = project.gitRemote();
    String base = project.baseBranchName();
    if (branch.equals(base) || branch.equals(remote.branchOf(project.deployBranch()))) {
      throw new IllegalStateException(
          branch + " is the base or the deploy branch of " + projectKey);
    }
    Path repository = Path.of(project.path());
    Path relative = real(repository).relativize(real(folder));
    return git.locked(
        repository,
        () -> {
          git.fetchPruning(remote, repository).expectSuccess("git fetch in " + repository);
          List<String> files = files(repository, relative);
          git.clearWorktreePath(repository, checkout);
          try {
            cut(repository, checkout, start(repository, remote, branch, base, fromBranch));
            replace(repository, checkout, relative, files);
            commit(checkout, relative, message);
            boolean carries = differs(checkout, remote.track(base));
            if (carries) {
              git.run(
                      checkout,
                      List.of("git", "push", remote.pushTarget(), "HEAD:refs/heads/" + branch))
                  .expectSuccess("git push " + branch);
            }
            return carries;
          } finally {
            git.removeWorktree(repository, checkout);
          }
        });
  }

  private List<String> files(Path repository, Path relative) {
    String listed =
        git.run(
                repository,
                List.of(
                    "git",
                    "ls-files",
                    "-z",
                    "--cached",
                    "--others",
                    "--exclude-standard",
                    "--",
                    relative.toString()))
            .expectSuccess("git ls-files in " + repository)
            .stdout();
    return Arrays.stream(listed.split("\0"))
        .filter(name ->
            !name.isEmpty()
                && Files.isRegularFile(repository.resolve(name), LinkOption.NOFOLLOW_LINKS))
        .distinct()
        .toList();
  }

  private String start(
      Path repository, Remote remote, String branch, String base, boolean fromBranch) {
    if (!fromBranch) {
      return remote.track(base);
    }
    String tip = remote.track(branch);
    boolean exists =
        git.run(repository, List.of("git", "rev-parse", "--verify", "--quiet", tip + "^{commit}"))
                .exitCode()
            == 0;
    boolean merged =
        exists
            && git.run(
                        repository,
                        List.of("git", "merge-base", "--is-ancestor", tip, remote.track(base)))
                    .exitCode()
                == 0;
    return exists && !merged ? tip : remote.track(base);
  }

  private void cut(Path repository, Path checkout, String start) {
    git.run(repository, List.of("git", "worktree", "add", "--detach", checkout.toString(), start))
        .expectSuccess("git worktree add in " + checkout);
  }

  private void replace(Path repository, Path checkout, Path relative, List<String> files) {
    git.run(
            checkout,
            List.of("git", "rm", "-r", "-q", "--ignore-unmatch", "--", relative.toString()))
        .expectSuccess("git rm in " + checkout);
    try {
      for (String name : files) {
        Path target = checkout.resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(
            repository.resolve(name),
            target,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot copy the workspace into " + checkout, e);
    }
  }

  private void commit(Path checkout, Path relative, String message) {
    git.run(checkout, List.of("git", "add", "-A", "--", relative.toString()))
        .expectSuccess("git add in " + checkout);
    if (git.run(checkout, List.of("git", "diff", "--cached", "--quiet")).exitCode() != 0) {
      git.run(checkout, List.of("git", "commit", "-q", "-m", message))
          .expectSuccess("git commit in " + checkout);
    }
  }

  private boolean differs(Path checkout, String base) {
    int exit = git.run(checkout, List.of("git", "diff", "--quiet", base, "HEAD")).exitCode();
    if (exit > 1) {
      throw new IllegalStateException("git diff against " + base + " failed (exit " + exit + ")");
    }
    return exit == 1;
  }

  private static Path real(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot resolve " + path, e);
    }
  }
}
