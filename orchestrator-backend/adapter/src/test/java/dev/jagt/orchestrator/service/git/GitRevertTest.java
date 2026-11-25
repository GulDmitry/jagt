package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitRevertTest {

  @TempDir Path dir;
  private final Processes runner = new ProcessRunner();
  private DeployWorktreePaths worktreePaths;
  private GitDeploy git;
  private GitRevert reverts;

  @BeforeEach
  void setUp() {
    worktreePaths = GitRepos.worktreePaths(dir);
    GitCommands commands = GitRepos.commands(runner);
    WorktreeConflicts conflicts = new WorktreeConflicts(commands);
    git = new GitDeploy(commands, worktreePaths, conflicts);
    reverts = new GitRevert(commands, worktreePaths, conflicts);
  }

  @Test
  void revertTakesTheDeployedChangeBackOutOfDevAndLeavesTheTaskBranchIntact() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    final String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    String revert = reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "cat-file", "-e", "origin/dev:feature.txt").exitCode())
        .isNotZero();
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(revert);
    assertThat(GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim()).isEqualTo(taskTip);
    assertThat(dir.resolve("data/tasks/ABC-1/worktrees/repo-revert")).doesNotExist();
  }

  @Test
  void refusesToRevertCommitThatIsNotOnTheDeployBranch() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String neverDeployed = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    assertThatThrownBy(
            () -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", neverDeployed, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not on dev");

    assertThat(dir.resolve("data/tasks/ABC-1/worktrees/repo-revert")).doesNotExist();
  }

  @Test
  void refusesSecondRevertOfTheSameDeploy() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    String firstRevert = reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("was already reverted");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(firstRevert);
  }

  @Test
  void refusesToRevertCommitThatIsNotMerge() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    GitRepos.pushDevChange(runner, repo, "unrelated.txt", "someone else's commit");
    String plainCommit = GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim();

    assertThatThrownBy(
            () -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", plainCommit, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not a merge");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(plainCommit);
  }

  @Test
  void keepsTheRevertWorktreeWithItsUnmergedPathsAndPushesNothingWhenTheRevertConflictsOnDev()
      throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the deployed feature");
    String devTip = GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim();
    final Path revertWorktree = dir.resolve("data/tasks/ABC-1/worktrees/repo-revert");

    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(RevertConflictException.class);

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(devTip);
    assertThat(Files.readString(revertWorktree.resolve("feature.txt")))
        .isEqualTo("someone edited the deployed feature");
    assertThat(reverts.hasRevertWorktree(repo, "ABC-1")).isTrue();
    assertThat(reverts.revertUnmerged(repo, "ABC-1")).isEqualTo("feature.txt");
    assertThat(reverts.revertProgress(repo, "ABC-1").unmerged()).isEqualTo(1);
    assertThat(reverts.revertResolved(repo, "ABC-1")).isFalse();
  }

  @Test
  void continuesTheRevertAndPushesOnceTheConflictIsResolved() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the deployed feature");

    Path revertWorktree = dir.resolve("data/tasks/ABC-1/worktrees/repo-revert");
    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(RevertConflictException.class);
    GitRepos.stage(runner, revertWorktree, "feature.txt", "resolved by the session");
    assertThat(reverts.revertResolved(repo, "ABC-1")).isTrue();

    String revert = reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(revert);
    assertThat(GitRepos.git(runner, repo, "show", "origin/dev:feature.txt").stdout())
        .isEqualTo("resolved by the session");
    assertThat(GitRepos.git(runner, repo, "log", "-1", "--format=%B", "origin/dev").stdout())
        .contains("This reverts commit " + merge);
    assertThat(revertWorktree).doesNotExist();
  }

  @Test
  void refusesToFinishTheRevertWhilePathsAreStillUnmergedAndKeepsTheWorktree() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the deployed feature");
    final String devTip = GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim();
    final Path revertWorktree = dir.resolve("data/tasks/ABC-1/worktrees/repo-revert");
    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(RevertConflictException.class);

    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(RevertConflictException.class)
        .hasMessageContaining("feature.txt");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(devTip);
    assertThat(Files.readString(revertWorktree.resolve("feature.txt")))
        .isEqualTo("someone edited the deployed feature");
  }

  @Test
  void revertsTheMergeTooafterFixRevertThatConflictedWasResolved() throws Exception {
    Path repo = GitRepos.cloneWithFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    git.openFixWorktree(repo, "ABC-1", "dev");
    Path deployWorktree = dir.resolve("data/tasks/ABC-1/worktrees/repo-deploy");
    GitRepos.stage(runner, deployWorktree, "feature.txt", "the feature, fixed");
    final String fix = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the fixed feature");
    Path revertWorktree = dir.resolve("data/tasks/ABC-1/worktrees/repo-revert");
    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of(fix)))
        .isInstanceOf(RevertConflictException.class);
    GitRepos.stage(runner, revertWorktree, "feature.txt", "the feature");

    reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of(fix));

    GitRepos.git(runner, repo, "fetch", "-q");
    String history = GitRepos.git(runner, repo, "log", "--format=%B", "origin/dev").stdout();
    assertThat(history)
        .contains("This reverts commit " + fix)
        .contains("This reverts commit " + merge);
    assertThat(GitRepos.git(runner, repo, "cat-file", "-e", "origin/dev:feature.txt").exitCode())
        .isNotZero();
    assertThat(revertWorktree).doesNotExist();
  }

  @Test
  void revertsThroughRevertPathStaleRunLeftBehind() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    Files.createDirectories(dir.resolve("data/tasks/ABC-1/worktrees/repo-revert"));
    Files.writeString(
        dir.resolve("data/tasks/ABC-1/worktrees/repo-revert").resolve("stale.txt"),
        "left by a run that died");

    String revert = reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(revert);
    assertThat(dir.resolve("data/tasks/ABC-1/worktrees/repo-revert")).doesNotExist();
  }

  @Test
  void namesGitsRefusalWhenDevMovedWhileTheRevertWasBeingMade() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    Path hook = repo.resolve(".git/hooks/pre-push");
    Files.writeString(
        hook,
        "#!/bin/sh\n"
            + "c=$(git commit-tree origin/dev^{tree} -p origin/dev -m other) && git push -q"
            + " --no-verify origin \"$c:refs/heads/dev\"\n");
    Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Revert push to dev was rejected")
        .satisfies(refused -> assertThat(refused.getMessage()).doesNotEndWith("Details: "));
  }

  @Test
  void revertTakesTheFixOutWithTheMergeItWasMadeOn() throws Exception {
    Path repo = GitRepos.cloneWithFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    git.openFixWorktree(repo, "ABC-1", "dev");
    Path deployWorktree = dir.resolve("data/tasks/ABC-1/worktrees/repo-deploy");
    GitRepos.stage(runner, deployWorktree, "feature.txt", "the feature, fixed");
    String fix = git.mergeIntoAndPush(repo, "ABC-1", "dev");

    reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of(fix));

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(git.isFix(repo, fix)).isTrue();
    assertThat(git.isFix(repo, merge)).isFalse();
    assertThat(GitRepos.git(runner, repo, "cat-file", "-e", "origin/dev:feature.txt").exitCode())
        .isNotZero();
  }

  @Test
  void onlyTheRepositoryThatCutTheRevertWorktreeClaimsItWhenSiblingDerivesTheSamePath()
      throws Exception {
    Path api = dir.resolve("api");
    Path web = dir.resolve("web");
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", api.toString());
    GitRepos.emptyRepo(runner, dir, "web");
    GitRepos.addWorktree(
        runner, web, reverts.revertWorktreePath(web, "ABC-1"), "jagt-revert-ABC-1");

    assertThat(reverts.hasRevertWorktree(web, "ABC-1")).isTrue();
    assertThat(reverts.hasRevertWorktree(api, "ABC-1")).isFalse();
  }

  @Test
  void refusesRevertGitRejectedWithNoConflictAndNamesItAsAnErrorNotConflict() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("base.txt"), "base");
    GitRepos.commit(runner, repo, "base");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "_dev", "main");
    Files.writeString(repo.resolve("same.txt"), "same");
    GitRepos.commit(runner, repo, "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "_dev:dev");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1", "main");
    Files.writeString(repo.resolve("same.txt"), "same");
    GitRepos.commit(runner, repo, "task");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");

    assertThatThrownBy(() -> reverts.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isExactlyInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no conflict is waiting for you");
  }
}
