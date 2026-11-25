package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.repo.Remote;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The only write to a shared branch that undoes a deploy: a revert commit, never a rewrite. */
@Service
@RequiredArgsConstructor
public class GitRevert {

  private final GitCommands git;
  private final DeployWorktreePaths worktrees;
  private final WorktreeConflicts conflicts;

  /**
   * Undoes ONE deploy: reverts {@code mergeCommit} on {@code targetBranch} and pushes, returning
   * the revert commit. Only ever ADDS a commit — no rewrite, no force-push — and leaves the task
   * branch alone. Refuses rather than guess when the commit is not on the branch, is not a merge,
   * or was already reverted. A revert that conflicts LEAVES its worktree with the markers; the next
   * call continues a resolution waiting there, reverts what is still to revert and pushes.
   */
  public String revertMergeAndPush(
      Path projectPath,
      String sourceBranch,
      String targetBranch,
      String mergeCommit,
      List<String> fixesNewestFirst) {
    Remote remote = git.remote(projectPath);
    return git.locked(
        projectPath,
        () -> {
          Path revertWorktree = revertWorktreePath(projectPath, sourceBranch);
          String revertBranch = TaskBranches.revert(sourceBranch);
          git.fetchPruning(remote, projectPath).expectSuccess("git fetch in " + projectPath);
          boolean resuming =
              Files.isDirectory(revertWorktree)
                  && revertWaiting(remote, projectPath, revertWorktree, sourceBranch, targetBranch);
          if (!resuming) {
            cutRevertWorktree(remote, projectPath, revertWorktree, revertBranch, targetBranch);
          }
          try {
            String commit =
                revertAndPush(
                    remote, revertWorktree, targetBranch, mergeCommit, fixesNewestFirst, resuming);
            removeRevertWorktree(projectPath, revertWorktree, revertBranch);
            return commit;
          } catch (RevertConflictException kept) {
            throw kept;
          } catch (RuntimeException failed) {
            if (!resuming) {
              removeRevertWorktree(projectPath, revertWorktree, revertBranch);
            }
            throw failed;
          }
        });
  }

  private void cutRevertWorktree(
      Remote remote,
      Path projectPath,
      Path revertWorktree,
      String revertBranch,
      String targetBranch) {
    if (Files.exists(revertWorktree)) {
      git.clearWorktreePath(projectPath, revertWorktree);
    }
    git.run(
            projectPath,
            List.of(
                "git",
                "worktree",
                "add",
                "-B",
                revertBranch,
                revertWorktree.toString(),
                remote.track(targetBranch)))
        .expectSuccess("git worktree add (revert) " + targetBranch);
  }

  private String revertAndPush(
      Remote remote,
      Path revertWorktree,
      String targetBranch,
      String mergeCommit,
      List<String> fixesNewestFirst,
      boolean resuming) {
    if (resuming) {
      continuePendingRevert(revertWorktree, targetBranch, mergeCommit);
    } else {
      requireRevertable(revertWorktree, targetBranch, mergeCommit);
      requireMergeCommit(revertWorktree, mergeCommit, targetBranch);
    }
    fixesNewestFirst.forEach(
        fix -> revertOnce(revertWorktree, targetBranch, fix, resuming, List.of()));
    revertOnce(revertWorktree, targetBranch, mergeCommit, resuming, List.of("-m", "1"));
    var push = git.publish(remote, revertWorktree, targetBranch);
    if (push.exitCode() != 0) {
      String details = push.stderr().isBlank() ? push.stdout() : push.stderr();
      throw new IllegalStateException(
          "Revert push to "
              + targetBranch
              + " was rejected — it moved while the"
              + " revert was being made. "
              + (resuming
                  ? "The revert worktree "
                      + revertWorktree
                      + " is kept; `hand-off` on the task gives the session `git merge "
                      + remote.track(targetBranch)
                      + "` to run in it, then revert again"
                  : "Revert again")
              + ". Details: "
              + details);
    }
    return git.run(revertWorktree, List.of("git", "rev-parse", "HEAD"))
        .expectSuccess("git rev-parse HEAD in " + revertWorktree)
        .stdout()
        .trim();
  }

  /**
   * A fix goes first, newest first, so the merge below it reverts onto the tree it made. {@code -m
   * 1} on a merge keeps the target's own line: reverting it means undoing what the SECOND parent
   * brought in.
   */
  private void revertOnce(
      Path revertWorktree,
      String targetBranch,
      String commit,
      boolean resuming,
      List<String> mainline) {
    if (resuming && !revertOf(revertWorktree, commit).isBlank()) {
      return;
    }
    requireRevertable(revertWorktree, targetBranch, commit);
    List<String> command = new ArrayList<>(List.of("git", "revert"));
    command.addAll(mainline);
    command.addAll(List.of("--no-edit", commit));
    var revert = git.run(revertWorktree, command);
    if (revert.exitCode() != 0) {
      throw revertFailure(
          revertWorktree,
          targetBranch,
          commit,
          revert.stderr().isBlank() ? revert.stdout() : revert.stderr());
    }
  }

  /**
   * Only UNMERGED PATHS mean a conflict; any other refusal is aborted, with nothing left to
   * resolve.
   */
  private RuntimeException revertFailure(
      Path revertWorktree, String targetBranch, String commit, String details) {
    if (!conflicts.unmergedPaths(revertWorktree).isBlank()) {
      return new RevertConflictException(commit, targetBranch, details, revertWorktree);
    }
    git.run(revertWorktree, List.of("git", "revert", "--abort"));
    return new IllegalStateException(
        "Could not revert "
            + Shas.shortSha(commit)
            + " on "
            + targetBranch
            + " — nothing was pushed and no conflict is waiting for you; git said: "
            + details);
  }

  private void continuePendingRevert(Path revertWorktree, String targetBranch, String mergeCommit) {
    String unmerged = conflicts.unmergedPaths(revertWorktree);
    if (!unmerged.isBlank()) {
      throw new RevertConflictException(
          mergeCommit,
          targetBranch,
          "still unresolved (git add them):\n" + unmerged,
          revertWorktree);
    }
    if (revertInProgress(revertWorktree)) {
      git.run(revertWorktree, List.of("git", "-c", "core.editor=true", "revert", "--continue"))
          .expectSuccess("git revert --continue in " + revertWorktree);
    }
  }

  private boolean revertInProgress(Path revertWorktree) {
    return git.run(revertWorktree, List.of("git", "rev-parse", "-q", "--verify", "REVERT_HEAD"))
            .exitCode()
        == 0;
  }

  /**
   * A worktree this repository cut that holds a revert: one begun and conflicted, or begun and
   * committed.
   */
  private boolean revertWaiting(
      Remote remote,
      Path projectPath,
      Path revertWorktree,
      String sourceBranch,
      String targetBranch) {
    return hasRevertWorktree(projectPath, sourceBranch)
        && (revertInProgress(revertWorktree)
            || !"0"
                .equals(
                    git.run(
                            revertWorktree,
                            List.of(
                                "git",
                                "rev-list",
                                "--count",
                                remote.track(targetBranch) + "..HEAD"))
                        .expectSuccess("git rev-list count HEAD in " + revertWorktree)
                        .stdout()
                        .trim()));
  }

  public boolean hasRevertWorktree(Path projectPath, String sourceBranch) {
    return git.worktreeOwner(revertWorktreePath(projectPath, sourceBranch))
        .filter(owner -> GitCommands.sameDirectory(owner, projectPath))
        .isPresent();
  }

  /**
   * Whether the conflict left in this task's revert worktree is resolved in full: nothing unmerged
   * or edited past what was staged.
   */
  public boolean revertResolved(Path projectPath, String sourceBranch) {
    Path revertWorktree = revertWorktreePath(projectPath, sourceBranch);
    return hasRevertWorktree(projectPath, sourceBranch)
        && conflicts.unmergedPaths(revertWorktree).isBlank()
        && git.run(revertWorktree, List.of("git", "diff", "--quiet")).exitCode() == 0;
  }

  /**
   * The paths still unmerged in this task's revert worktree, one per line; blank when none, or when
   * it is gone.
   */
  public String revertUnmerged(Path projectPath, String sourceBranch) {
    return hasRevertWorktree(projectPath, sourceBranch)
        ? conflicts.unmergedPaths(revertWorktreePath(projectPath, sourceBranch))
        : "";
  }

  public Conflict.Progress revertProgress(Path projectPath, String sourceBranch) {
    return conflicts.progress(revertWorktreePath(projectPath, sourceBranch));
  }

  private void removeRevertWorktree(Path projectPath, Path revertWorktree, String revertBranch) {
    git.run(
        projectPath, List.of("git", "worktree", "remove", "--force", revertWorktree.toString()));
    git.run(projectPath, List.of("git", "branch", "-D", revertBranch));
  }

  /**
   * Only a merge reverts as ONE unit; a non-merge would undo one commit of a task instead of the
   * deploy.
   */
  private void requireMergeCommit(Path revertWorktree, String mergeCommit, String targetBranch) {
    String parents =
        git.run(revertWorktree, List.of("git", "rev-list", "--parents", "-n", "1", mergeCommit))
            .expectSuccess("git rev-list --parents " + mergeCommit)
            .stdout()
            .trim();
    if (parents.split("\\s+").length < 3) {
      throw new IllegalStateException(
          "Commit "
              + Shas.shortSha(mergeCommit)
              + " on "
              + targetBranch
              + " is not a merge, so reverting it would undo only part of what was deployed."
              + " Nothing was reverted; pick one of the listed merge commits.");
    }
  }

  private void requireRevertable(Path revertWorktree, String targetBranch, String mergeCommit) {
    boolean onBranch =
        git.run(revertWorktree, List.of("git", "merge-base", "--is-ancestor", mergeCommit, "HEAD"))
                .exitCode()
            == 0;
    if (!onBranch) {
      throw new IllegalStateException(
          "Commit "
              + Shas.shortSha(mergeCommit)
              + " is not on "
              + targetBranch
              + " — it was never deployed there, or that history was rewritten. Nothing was"
              + " reverted.");
    }
    String existing = revertOf(revertWorktree, mergeCommit);
    if (!existing.isBlank()) {
      throw new IllegalStateException(
          "Commit "
              + Shas.shortSha(mergeCommit)
              + " was already reverted on "
              + targetBranch
              + " by "
              + Shas.shortSha(existing)
              + ". Nothing to do.");
    }
  }

  /**
   * git's own revert message is the marker, so a revert made by hand counts too; blank when none.
   */
  private String revertOf(Path revertWorktree, String commit) {
    return git.run(
            revertWorktree,
            List.of(
                "git",
                "log",
                "--fixed-strings",
                "--grep=This reverts commit " + commit,
                "--format=%H",
                "-1"))
        .expectSuccess("git log (revert search) in " + revertWorktree)
        .stdout()
        .trim();
  }

  /**
   * Where a revert is staged — separate from the deploy worktree, which may be sitting in a
   * conflict.
   */
  public Path revertWorktreePath(Path projectPath, String sourceBranch) {
    return worktrees.revert(projectPath, sourceBranch);
  }
}
