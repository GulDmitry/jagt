package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.service.worktree.WorktreeNoise;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.repo.Remote;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** The only write to a shared branch that lands work: a deploy's merge. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitDeploy {

  private final GitCommands git;
  private final DeployWorktreePaths worktrees;
  private final WorktreeConflicts conflicts;

  /**
   * Merges the task branch into the deploy branch and pushes, in a worktree cut from {@code
   * origin/<target>} so the task branch is NEVER modified. A conflict LEAVES that worktree with its
   * markers instead of aborting; the next call finishes a resolution waiting there, and otherwise
   * merges again against the target as it is then.
   */
  public String mergeIntoAndPush(Path projectPath, String sourceBranch, String targetBranch) {
    Remote remote = git.remote(projectPath);
    return git.locked(
        projectPath,
        () -> {
          Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
          String deployBranch = TaskBranches.deploy(sourceBranch);
          git.fetchPruning(remote, projectPath).expectSuccess("git fetch in " + projectPath);
          if (Files.isDirectory(deployWorktree)
              && settleLeftover(
                  remote, projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch)) {
            return finishDeploy(
                remote, projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
          }
          // Deploy is decoupled from review state: its ONLY precondition is work that was SHIPPED.
          // What it
          // merges is the remote branch, which is what the request shows — a session may push HEAD
          // under the
          // task's name from a local branch named otherwise, leaving that local ref moved by
          // nobody.
          String source = remote.track(sourceBranch);
          requireWorkToDeploy(remote, projectPath, source, sourceBranch, targetBranch);
          // A registration outlives a directory deleted by hand, and the add then refuses the
          // branch as
          // still checked out.
          git.clearWorktreePath(projectPath, deployWorktree);
          git.run(
                  projectPath,
                  List.of(
                      "git",
                      "worktree",
                      "add",
                      "-B",
                      deployBranch,
                      deployWorktree.toString(),
                      remote.track(targetBranch)))
              .expectSuccess("git worktree add (deploy) " + targetBranch);
          // The message is explicit because git's default would name the throwaway branch, not the
          // target.
          // --no-ff ALWAYS: that one merge commit is what `revert` undoes; a fast-forward leaves
          // them loose.
          var merge =
              git.run(
                  deployWorktree,
                  List.of(
                      "git",
                      "merge",
                      "--no-ff",
                      "--no-edit",
                      "-m",
                      "Merge branch '" + sourceBranch + "' into " + targetBranch,
                      source));
          if (merge.exitCode() != 0) {
            throw mergeFailure(
                projectPath,
                deployWorktree,
                deployBranch,
                sourceBranch,
                targetBranch,
                merge.stderr().isBlank() ? merge.stdout() : merge.stderr());
          }
          return pushAndRemoveDeploy(
              remote, projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
        });
  }

  /**
   * Cuts the deploy worktree from the deploy branch as it stands, where a fix of what already
   * landed is made and finished by the next deploy; work waiting in a leftover one is kept.
   */
  public void openFixWorktree(Path projectPath, String sourceBranch, String targetBranch) {
    Remote remote = git.remote(projectPath);
    git.locked(
        projectPath,
        () -> {
          Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
          String deployBranch = TaskBranches.deploy(sourceBranch);
          git.fetchPruning(remote, projectPath).expectSuccess("git fetch in " + projectPath);
          if (Files.isDirectory(deployWorktree)
              && settleLeftover(
                  remote, projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch)) {
            return;
          }
          git.clearWorktreePath(projectPath, deployWorktree);
          git.run(
                  projectPath,
                  List.of(
                      "git",
                      "worktree",
                      "add",
                      "-B",
                      deployBranch,
                      deployWorktree.toString(),
                      remote.track(targetBranch)))
              .expectSuccess("git worktree add (deploy fix) " + targetBranch);
        });
  }

  /**
   * Whether human work waits in the leftover worktree; otherwise it is cleared, or refused when not
   * this repository's.
   */
  private boolean settleLeftover(
      Remote remote,
      Path projectPath,
      Path deployWorktree,
      String deployBranch,
      String sourceBranch,
      String targetBranch) {
    if (hasDeployWorktree(projectPath, sourceBranch)) {
      if (humanWorkWaiting(remote, deployWorktree, targetBranch)) {
        return true;
      }
      discardDeployWorktree(remote, projectPath, deployWorktree, deployBranch, targetBranch);
    } else if (git.worktreeOwner(deployWorktree).isPresent()) {
      throw new ForeignDeployWorktreeException(deployWorktree, projectPath);
    } else {
      requireOnlyEditorResidue(deployWorktree);
    }
    return false;
  }

  private void requireWorkToDeploy(
      Remote remote, Path projectPath, String source, String sourceBranch, String targetBranch) {
    if (git.run(projectPath, List.of("git", "rev-parse", "--verify", "--quiet", source)).exitCode()
        != 0) {
      throw new NothingToDeployException(
          "Nothing to deploy: branch '"
              + sourceBranch
              + "' was never "
              + (remote.local() ? "committed" : "pushed")
              + " — ship it first.");
    }
    String ahead =
        git.run(
                projectPath,
                List.of("git", "rev-list", "--count", remote.track(targetBranch) + ".." + source))
            .expectSuccess("git rev-list count " + source)
            .stdout()
            .trim();
    if ("0".equals(ahead)) {
      throw new NothingToDeployException(sourceBranch, targetBranch);
    }
  }

  private RuntimeException mergeFailure(
      Path projectPath,
      Path deployWorktree,
      String deployBranch,
      String sourceBranch,
      String targetBranch,
      String details) {
    // Only UNMERGED PATHS mean a conflict. git also exits non-zero for a missing committer identity
    // or a refusing hook, and calling those a conflict leaves a worktree the next deploy pushes.
    if (conflicts.unmergedPaths(deployWorktree).isBlank()) {
      git.removeWorktreeAndBranch(projectPath, deployWorktree, deployBranch);
      return new IllegalStateException(
          "Could not merge "
              + sourceBranch
              + " into "
              + targetBranch
              + " — nothing was pushed and no conflict is waiting for you; git said: "
              + details);
    }
    return new MergeConflictException(sourceBranch, targetBranch, details, deployWorktree);
  }

  /**
   * Whether the leftover worktree holds work of the human's: a resolution staged, committed, or
   * part done. One still exactly as the conflict left it, or one the merge was aborted in, holds
   * none.
   */
  private boolean humanWorkWaiting(Remote remote, Path deployWorktree, String targetBranch) {
    if (!conflicts.unmergedPaths(deployWorktree).isBlank()) {
      return partlyResolved(deployWorktree);
    }
    return mergeInProgress(deployWorktree)
        || resolutionCommitted(remote, deployWorktree, targetBranch)
        || stagedFix(deployWorktree);
  }

  /** A change staged on the deploy branch as it stands, which no merge is in progress to own. */
  private boolean stagedFix(Path deployWorktree) {
    return git.run(deployWorktree, List.of("git", "diff", "--cached", "--quiet")).exitCode() != 0;
  }

  /**
   * Whether the conflict left in this task's deploy worktree is resolved in full: nothing unmerged,
   * nothing edited past what was staged, and the merge still standing or committed.
   */
  public boolean deployResolved(Path projectPath, String sourceBranch, String targetBranch) {
    Remote remote = git.remote(projectPath);
    Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
    return hasDeployWorktree(projectPath, sourceBranch)
        && conflicts.unmergedPaths(deployWorktree).isBlank()
        && git.run(deployWorktree, List.of("git", "diff", "--quiet")).exitCode() == 0
        && (mergeInProgress(deployWorktree)
            || resolutionCommitted(remote, deployWorktree, targetBranch)
            || stagedFix(deployWorktree));
  }

  /**
   * Where the resolution in this task's deploy worktree stands; any edit, staged or not, changes
   * the digest.
   */
  public Conflict.Progress deployProgress(Path projectPath, String sourceBranch) {
    return conflicts.progress(deployWorktreePath(projectPath, sourceBranch));
  }

  /** A conflicted path already added is the human's work, and no press of theirs throws it away. */
  private boolean partlyResolved(Path deployWorktree) {
    return git.run(deployWorktree, List.of("git", "status", "--porcelain"))
        .expectSuccess("git status in " + deployWorktree)
        .stdout()
        .lines()
        .anyMatch(line -> !line.isBlank() && "MADRC".indexOf(line.charAt(0)) >= 0);
  }

  /**
   * A resolution committed by hand. The first parent must sit on the target: a worktree cut for a
   * DIFFERENT branch carries that branch's whole line, and pushing it would dump it into this one.
   */
  private boolean resolutionCommitted(Remote remote, Path deployWorktree, String targetBranch) {
    boolean ahead =
        !"0"
            .equals(
                git.run(
                        deployWorktree,
                        List.of(
                            "git", "rev-list", "--count", remote.track(targetBranch) + "..HEAD"))
                    .expectSuccess("git rev-list count HEAD in " + deployWorktree)
                    .stdout()
                    .trim());
    return ahead
        && git.run(
                    deployWorktree,
                    List.of(
                        "git", "merge-base", "--is-ancestor", "HEAD^1", remote.track(targetBranch)))
                .exitCode()
            == 0;
  }

  private void discardDeployWorktree(
      Remote remote,
      Path projectPath,
      Path deployWorktree,
      String deployBranch,
      String targetBranch) {
    log.atInfo()
        .setMessage("deploy worktree discarded")
        .addKeyValue("worktree", deployWorktree)
        .addKeyValue("cause", "no resolution staged, committed or part done in it")
        .addKeyValue("effect", "merged again from " + remote.track(targetBranch))
        .log();
    git.removeWorktreeAndBranch(projectPath, deployWorktree, deployBranch);
  }

  private boolean mergeInProgress(Path deployWorktree) {
    return git.run(deployWorktree, List.of("git", "rev-parse", "-q", "--verify", "MERGE_HEAD"))
            .exitCode()
        == 0;
  }

  private String finishDeploy(
      Remote remote,
      Path projectPath,
      Path deployWorktree,
      String deployBranch,
      String sourceBranch,
      String targetBranch) {
    String unmerged = conflicts.unmergedPaths(deployWorktree);
    if (!unmerged.isBlank()) {
      throw new MergeConflictException(
          sourceBranch,
          targetBranch,
          "still unresolved (git add them):\n" + unmerged,
          deployWorktree);
    }
    if (mergeInProgress(deployWorktree)) {
      git.run(deployWorktree, List.of("git", "commit", "--no-edit"))
          .expectSuccess("git commit (deploy resolution) " + deployWorktree);
    } else if (stagedFix(deployWorktree)) {
      git.run(
              deployWorktree,
              List.of(
                  "git", "commit", "-m", "Fix " + targetBranch + " after merging " + sourceBranch))
          .expectSuccess("git commit (deploy fix) " + deployWorktree);
    }
    return pushAndRemoveDeploy(
        remote, projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
  }

  /**
   * KEEPS the worktree on a rejected push (the target moved under the merge) so the resolution
   * isn't lost. Returns the pushed merge commit — what `revert` undoes, and knowable only before
   * the worktree is gone.
   */
  private String pushAndRemoveDeploy(
      Remote remote,
      Path projectPath,
      Path deployWorktree,
      String deployBranch,
      String sourceBranch,
      String targetBranch) {
    var push = git.publish(remote, deployWorktree, targetBranch);
    if (push.exitCode() != 0) {
      if (nothingLeftToPush(remote, deployWorktree, targetBranch)) {
        git.removeWorktreeAndBranch(projectPath, deployWorktree, deployBranch);
        throw new NothingToDeployException(
            "Nothing left to push: what the deploy worktree held is"
                + " already on "
                + targetBranch
                + ", which has moved on since. That worktree is gone —"
                + " deploy again if '"
                + sourceBranch
                + "' still holds work "
                + targetBranch
                + " lacks.");
      }
      String d = push.stderr().isBlank() ? push.stdout() : push.stderr();
      throw new IllegalStateException(
          "Deploy push to "
              + targetBranch
              + " was rejected: it moved while the deploy was made. The deploy worktree "
              + deployWorktree
              + " is kept; `hand-off` on the task gives the session `git merge "
              + remote.track(targetBranch)
              + "` to run in it, then deploy again. Details: "
              + d);
    }
    String merged =
        git.run(deployWorktree, List.of("git", "rev-parse", "HEAD"))
            .expectSuccess("git rev-parse HEAD in " + deployWorktree)
            .stdout()
            .trim();
    git.removeWorktreeAndBranch(projectPath, deployWorktree, deployBranch);
    return merged;
  }

  /**
   * Whether the worktree holds nothing the target lacks. The merge is NOT claimed as this task's:
   * the commit reached here may be the target tip the worktree was cut from.
   */
  private boolean nothingLeftToPush(Remote remote, Path deployWorktree, String targetBranch) {
    boolean fetched = git.fetchBranch(remote, deployWorktree, targetBranch).exitCode() == 0;
    return fetched
        && git.run(
                    deployWorktree,
                    List.of(
                        "git", "merge-base", "--is-ancestor", "HEAD", remote.track(targetBranch)))
                .exitCode()
            == 0;
  }

  /**
   * Throws away the deploy this repository has waiting, a conflicted merge included; none of it was
   * pushed.
   */
  public void discardDeploy(Path projectPath, String sourceBranch) {
    git.locked(
        projectPath,
        () -> {
          if (!hasDeployWorktree(projectPath, sourceBranch)) {
            return;
          }
          Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
          log.atInfo()
              .setMessage("deploy worktree discarded")
              .addKeyValue("worktree", deployWorktree)
              .addKeyValue("cause", "the deploy was reverted")
              .log();
          git.removeWorktreeAndBranch(
              projectPath, deployWorktree, TaskBranches.deploy(sourceBranch));
        });
  }

  /**
   * Whether the commit pushed to the deploy branch is a fix on top of it rather than a deploy
   * merge.
   */
  public boolean isFix(Path projectPath, String commit) {
    return git.run(projectPath, List.of("git", "rev-list", "--parents", "-n", "1", commit))
            .expectSuccess("git rev-list --parents " + commit)
            .stdout()
            .trim()
            .split("\\s+")
            .length
        < 3;
  }

  public Path deployWorktreePath(Path projectPath, String sourceBranch) {
    return worktrees.deploy(projectPath, sourceBranch);
  }

  /** Whether a deploy worktree for this task is waiting in THIS repository. */
  public boolean hasDeployWorktree(Path projectPath, String sourceBranch) {
    return git.worktreeOwner(deployWorktreePath(projectPath, sourceBranch))
        .filter(owner -> GitCommands.sameDirectory(owner, projectPath))
        .isPresent();
  }

  /**
   * A path that is no checkout cannot be another repository's conflict: it is jagt's own leftover,
   * an editor having written its project files back into a directory git had emptied. The deploy
   * goes on and the path is cleared before the add; anything else found there is left untouched and
   * named.
   */
  private void requireOnlyEditorResidue(Path path) {
    List<String> kept;
    try (var entries = Files.list(path)) {
      kept =
          entries
              .map(entry -> entry.getFileName().toString())
              .filter(name -> !WorktreeNoise.isResidue(name))
              .sorted()
              .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the deploy worktree path " + path, e);
    }
    if (!kept.isEmpty()) {
      throw new StaleDeployPathException(path, kept);
    }
  }
}
