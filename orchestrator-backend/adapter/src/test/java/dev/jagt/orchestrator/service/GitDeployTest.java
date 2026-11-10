package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.Conflict;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GitDeployTest {

  @TempDir Path dir;
  private final Processes runner = new ProcessRunner();
  private final GitDeploy git = new GitDeploy(GitRepos.commands(runner));

  @Test
  void deployMergesTheTaskBranchIntoDevAndLeavesTheTaskBranchByteIdentical() throws Exception {
    Path repo = GitRepos.cloneWithTask(runner, dir);
    final String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim())
        .isEqualTo(taskTip);
    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:g.txt").stdout())
        .contains("task");
    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
  }

  @Test
  void deploysWhatWasPushedWhenTheLocalRefOfTheTaskBranchHasFallenBehindTheRemote()
      throws Exception {
    Path repo = GitRepos.cloneWithDev(runner, dir);
    GitRepos.git(runner, repo, "push", "-q", "origin", "main:ABC-1");
    GitRepos.git(runner, repo, "branch", "ABC-1", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1-metrics");
    Files.writeString(repo.resolve("g.txt"), "the work the request shows");
    GitRepos.commit(runner, repo, "work");
    GitRepos.git(runner, repo, "push", "-q", "origin", "HEAD:ABC-1");

    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:g.txt").stdout())
        .contains("the work the request shows");
  }

  @Test
  void deploysThroughDeployPathAnEditorRecreatedAfterTheWorktreeWasRemoved() throws Exception {
    Path repo = GitRepos.cloneWithTask(runner, dir);
    Files.createDirectories(dir.resolve("ABC-1-deploy").resolve(".idea"));
    Files.writeString(
        dir.resolve("ABC-1-deploy").resolve(".idea").resolve("misc.xml"), "<project/>");

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:g.txt").stdout())
        .contains("task");
    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
  }

  @Test
  void deploysThroughAnEditorsDeployPathWhenEverythingSitsInsideAnotherRepository()
      throws Exception {
    GitRepos.git(runner, dir, "init", "-q", dir.toString());
    Path repo = GitRepos.cloneWithTask(runner, dir);
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    Files.createDirectories(dir.resolve("ABC-1-deploy").resolve(".idea"));
    Files.writeString(
        dir.resolve("ABC-1-deploy").resolve(".idea").resolve("misc.xml"), "<project/>");

    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:g.txt").stdout())
        .contains("task");
  }

  @Test
  void refusesToDeployThroughDeployPathHoldingSomethingJagtDidNotPutThere() throws Exception {
    Path repo = GitRepos.cloneWithTask(runner, dir);
    Files.createDirectories(dir.resolve("ABC-1-deploy"));
    Files.writeString(dir.resolve("ABC-1-deploy").resolve("notes.txt"), "mine");

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.StaleDeployPathException.class)
        .hasMessageContaining("ABC-1-deploy")
        .hasMessageContaining("notes.txt");
    assertThat(dir.resolve("ABC-1-deploy").resolve("notes.txt")).hasContent("mine");
  }

  @Test
  void deployConflictLeavesDeployWorktreeAndNeverModifiesTheTaskBranch() throws Exception {
    Path repo = GitRepos.cloneWithDev(runner, dir);
    GitRepos.git(runner, repo, "fetch", "-q");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "_dev", "origin/dev");
    Files.writeString(repo.resolve("f.txt"), "dev change");
    GitRepos.git(runner, repo, "commit", "-qam", "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "_dev:dev");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1", "main");
    Files.writeString(repo.resolve("f.txt"), "task change");
    GitRepos.git(runner, repo, "commit", "-qam", "task");
    String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);

    assertThat(GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim())
        .isEqualTo(taskTip);
    assertThat(dir.resolve("ABC-1-deploy")).isDirectory();
  }

  @Test
  void refusesToFinishDeployFromWorktreeAnotherRepositoryCut() throws Exception {
    Path origin = dir.resolve("api-origin.git");
    Path api = dir.resolve("api");
    final Path web = dir.resolve("web");
    GitRepos.git(runner, dir, "init", "-q", "--bare", "-b", "main", origin.toString());
    GitRepos.git(runner, dir, "clone", "-q", origin.toString(), api.toString());
    Files.writeString(api.resolve("f.txt"), "base");
    GitRepos.commit(runner, api, "base");
    GitRepos.git(runner, api, "push", "-q", "origin", "main");
    GitRepos.git(runner, api, "push", "-q", "origin", "main:dev");
    GitRepos.git(runner, api, "branch", "ABC-1");
    GitRepos.emptyRepo(runner, dir, "web");
    GitRepos.addWorktree(
        runner, web, GitDeploy.deployWorktreePath(web, "ABC-1"), "jagt-deploy-ABC-1");

    assertThatThrownBy(() -> git.mergeIntoAndPush(api, "ABC-1", "dev"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("holds a checkout api did not cut");
  }

  @Test
  void onlyTheRepositoryThatCutTheDeployWorktreeClaimsItWhenSiblingDerivesTheSamePath()
      throws Exception {
    Path api = dir.resolve("api");
    Path web = dir.resolve("web");
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", api.toString());
    GitRepos.emptyRepo(runner, dir, "web");
    GitRepos.addWorktree(
        runner, web, GitDeploy.deployWorktreePath(web, "ABC-1"), "jagt-deploy-ABC-1");

    assertThat(git.hasDeployWorktree(web, "ABC-1")).isTrue();
    assertThat(git.hasDeployWorktree(api, "ABC-1")).isFalse();
  }

  @Test
  void discardsTheConflictedMergeWaitingInTheDeployWorktree() throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);

    git.discardDeploy(repo, "ABC-1");

    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    assertThat(
            GitRepos.git(runner, repo, "branch", "--list", "jagt-deploy-ABC-1").stdout())
        .isBlank();
  }

  @Test
  void deployingAgainAfterResolvingTheDeployWorktreePushesDevAndCleansUp() throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    final String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "f.txt", "resolved");

    String merged = git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:f.txt").stdout())
        .contains("resolved");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(merged);
    assertThat(deployWorktree).doesNotExist();
    assertThat(GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim())
        .isEqualTo(taskTip);
  }

  @Test
  void readsDeployConflictAsResolvedOnceEveryPathIsStaged() throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "f.txt", "resolved");

    boolean resolved = git.deployResolved(repo, "ABC-1", "dev");

    assertThat(resolved).isTrue();
  }

  @Test
  void countsConflictsUnmergedPathsAndSeesAnEditBeforeItIsStaged() throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Conflict.Progress conflicted = git.deployProgress(repo, "ABC-1");
    Files.writeString(dir.resolve("ABC-1-deploy").resolve("f.txt"), "resolving");

    Conflict.Progress edited = git.deployProgress(repo, "ABC-1");

    assertThat(conflicted)
        .extracting(Conflict.Progress::unmerged, Conflict.Progress::staged)
        .containsExactly(1, 0);
    assertThat(edited.digest()).isNotEqualTo(conflicted.digest());
  }

  @Test
  void keepsDeployConflictUnresolvedWhileStagedPathIsEditedAgain() throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "f.txt", "resolved");
    Files.writeString(deployWorktree.resolve("f.txt"), "resolved, then reworked");

    boolean resolved = git.deployResolved(repo, "ABC-1", "dev");

    assertThat(resolved).isFalse();
  }

  @Test
  void mergesAgainstTheDeployBranchAsItIsNowWhenTheConflictWasNeverResolved() throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    final Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.git(runner, repo, "checkout", "-q", "_dev");
    Files.writeString(repo.resolve("f.txt"), "task change");
    GitRepos.git(runner, repo, "commit", "-qam", "release");
    GitRepos.git(runner, repo, "push", "-q", "origin", "_dev:dev");

    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "log", "--oneline", "origin/dev").stdout())
        .contains("Merge branch 'ABC-1' into dev");
    assertThat(deployWorktree).doesNotExist();
  }

  @Test
  void keepsTheConflictWaitingWhenPartOfItIsAlreadyResolved() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("f.txt"), "base");
    Files.writeString(repo.resolve("g.txt"), "base");
    GitRepos.commit(runner, repo, "base");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main:dev");
    GitRepos.git(runner, repo, "fetch", "-q");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "_dev", "origin/dev");
    Files.writeString(repo.resolve("f.txt"), "dev change");
    Files.writeString(repo.resolve("g.txt"), "dev change");
    GitRepos.git(runner, repo, "commit", "-qam", "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "_dev:dev");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1", "main");
    Files.writeString(repo.resolve("f.txt"), "task change");
    Files.writeString(repo.resolve("g.txt"), "task change");
    GitRepos.git(runner, repo, "commit", "-qam", "task");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "f.txt", "resolved by hand");

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class)
        .hasMessageContaining("still unresolved");

    assertThat(deployWorktree.resolve("f.txt")).hasContent("resolved by hand");
    assertThat(git.deployProgress(repo, "ABC-1"))
        .extracting(Conflict.Progress::unmerged, Conflict.Progress::staged)
        .containsExactly(1, 1);
  }

  @Test
  void refusesToPushTheOldTargetsLineWhenTheDeployBranchChangedUnderLeftoverWorktree()
      throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    GitRepos.git(runner, repo, "push", "-q", "origin", "main:staging");
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "f.txt", "task change");
    GitRepos.git(runner, deployWorktree, "commit", "-q", "--no-edit");

    git.mergeIntoAndPush(repo, "ABC-1", "staging");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "log", "--oneline", "origin/staging").stdout())
        .doesNotContain("dev only");
  }

  @Test
  void stopsSendingTheHumanBackToTheDeployWorktreeWhenTheDeployBranchAlreadyHoldsWhatItHeld()
      throws Exception {
    Path repo = GitRepos.cloneWithConflictingTask(runner, dir);
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "f.txt", "resolved");
    GitRepos.git(runner, deployWorktree, "commit", "-q", "--no-edit");
    GitRepos.git(runner, deployWorktree, "push", "-q", "origin", "HEAD:dev");
    GitRepos.git(runner, repo, "checkout", "-q", "_dev");
    GitRepos.git(runner, repo, "fetch", "-q");
    GitRepos.git(runner, repo, "reset", "-q", "--hard", "origin/dev");
    Files.writeString(repo.resolve("later.txt"), "release");
    GitRepos.commit(runner, repo, "later");
    GitRepos.git(runner, repo, "push", "-q", "origin", "_dev:dev");

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.NothingToDeployException.class)
        .hasMessageContaining("no commits beyond dev");

    assertThat(deployWorktree).doesNotExist();
  }

  @Test
  void deploysAgainAfterTheDeployWorktreeDirectoryWasDeletedByHand() throws Exception {
    Path repo = GitRepos.cloneWithDev(runner, dir);
    GitRepos.git(runner, repo, "fetch", "-q");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1");
    Files.writeString(repo.resolve("g.txt"), "task");
    GitRepos.commit(runner, repo, "task");
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.git(
        runner,
        repo,
        "worktree",
        "add",
        "-q",
        "-B",
        "jagt-deploy-ABC-1",
        deployWorktree.toString(),
        "origin/dev");
    runner.run(dir, GitRepos.TIMEOUT, List.of("rm", "-rf", deployWorktree.toString()));

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:g.txt").stdout())
        .contains("task");
  }

  @Test
  void namesTheRealTargetBranchInTheDeployMergeCommit() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "dev");
    Files.writeString(repo.resolve("d.txt"), "dev diverges");
    GitRepos.git(runner, repo, "checkout", "-q", "dev");
    GitRepos.commit(runner, repo, "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "dev");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1", "main");
    Files.writeString(repo.resolve("g.txt"), "task");
    GitRepos.commit(runner, repo, "task");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    String subject =
        GitRepos.git(runner, repo, "log", "-1", "--format=%s", "origin/dev")
            .stdout()
            .trim();
    assertThat(subject).isEqualTo("Merge branch 'ABC-1' into dev");
  }

  @Test
  void deployConflictPushesNothingToDevAndLeavesTheDeployWorktreeWithTaskBranchUntouched()
      throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "dev");
    final String devBefore = GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim();
    GitRepos.git(runner, repo, "checkout", "-q", "dev");
    Files.writeString(repo.resolve("f.txt"), "dev change");
    GitRepos.git(runner, repo, "commit", "-qam", "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "dev");
    final String devWithOnlyDevCommit =
        GitRepos.git(runner, repo, "rev-parse", "dev").stdout().trim();
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1", "main");
    Files.writeString(repo.resolve("f.txt"), "task change");
    GitRepos.git(runner, repo, "commit", "-qam", "task");
    final String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class)
        .hasMessageContaining("CONFLICT")
        .hasMessageContaining("nothing was pushed")
        .hasMessageContaining("ABC-1-deploy");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(devWithOnlyDevCommit)
        .isNotEqualTo(devBefore);
    assertThat(GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim())
        .isEqualTo(taskTip);
    assertThat(dir.resolve("ABC-1-deploy")).isDirectory();
  }

  @Test
  void refusesDeployWhenBranchHasNoCommitsBeyondTheTarget() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "dev");
    GitRepos.git(runner, repo, "branch", "ABC-1", "main");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has no commits beyond dev");
  }

  @Test
  void refusesDeployOfWorkNobodyCanReviewBecauseTheBranchWasNeverPushed() throws Exception {
    Path repo = GitRepos.cloneWithDev(runner, dir);
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1");
    Files.writeString(repo.resolve("g.txt"), "work that was never shipped");
    GitRepos.commit(runner, repo, "work");

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("was never pushed");
  }

  @Test
  void publishesTaskCommitsWhenDeployMergesCleanly() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "dev");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1", "main");
    Files.writeString(repo.resolve("g.txt"), "task feature");
    GitRepos.commit(runner, repo, "task");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    final String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:g.txt")
                .stdout())
        .contains("task feature");
    String parents =
        GitRepos.git(runner, repo, "rev-list", "--parents", "-n", "1", "origin/dev")
            .stdout()
            .trim();
    assertThat(parents.split("\\s+")).hasSize(3).contains(taskTip);
  }

  @Test
  void revertTakesTheDeployedChangeBackOutOfDevAndLeavesTheTaskBranchIntact() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    final String taskTip = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    String revert = git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "cat-file", "-e", "origin/dev:feature.txt")
                .exitCode())
        .isNotZero();
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(revert);
    assertThat(GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim())
        .isEqualTo(taskTip);
    assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
  }

  @Test
  void refusesToRevertCommitThatIsNotOnTheDeployBranch() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String neverDeployed = GitRepos.git(runner, repo, "rev-parse", "ABC-1").stdout().trim();

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", neverDeployed, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not on dev");

    assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
  }

  @Test
  void refusesSecondRevertOfTheSameDeploy() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    String firstRevert = git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
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

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", plainCommit, List.of()))
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
    final Path revertWorktree = dir.resolve("ABC-1-revert");

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(GitDeploy.RevertConflictException.class);

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(devTip);
    assertThat(Files.readString(revertWorktree.resolve("feature.txt")))
        .isEqualTo("someone edited the deployed feature");
    assertThat(git.hasRevertWorktree(repo, "ABC-1")).isTrue();
    assertThat(git.revertUnmerged(repo, "ABC-1")).isEqualTo("feature.txt");
    assertThat(git.revertProgress(repo, "ABC-1").unmerged()).isEqualTo(1);
    assertThat(git.revertResolved(repo, "ABC-1")).isFalse();
  }

  @Test
  void continuesTheRevertAndPushesOnceTheConflictIsResolved() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the deployed feature");

    Path revertWorktree = dir.resolve("ABC-1-revert");
    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(GitDeploy.RevertConflictException.class);
    GitRepos.stage(runner, revertWorktree, "feature.txt", "resolved by the session");
    assertThat(git.revertResolved(repo, "ABC-1")).isTrue();

    String revert = git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(revert);
    assertThat(GitRepos.git(runner, repo, "show", "origin/dev:feature.txt").stdout())
        .isEqualTo("resolved by the session");
    assertThat(
            GitRepos.git(runner, repo, "log", "-1", "--format=%B", "origin/dev").stdout())
        .contains("This reverts commit " + merge);
    assertThat(revertWorktree).doesNotExist();
  }

  @Test
  void refusesToFinishTheRevertWhilePathsAreStillUnmergedAndKeepsTheWorktree() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the deployed feature");
    final String devTip = GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim();
    final Path revertWorktree = dir.resolve("ABC-1-revert");
    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(GitDeploy.RevertConflictException.class);

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(GitDeploy.RevertConflictException.class)
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
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "feature.txt", "the feature, fixed");
    final String fix = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    GitRepos.pushDevChange(runner, repo, "feature.txt", "someone edited the fixed feature");
    Path revertWorktree = dir.resolve("ABC-1-revert");
    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of(fix)))
        .isInstanceOf(GitDeploy.RevertConflictException.class);
    GitRepos.stage(runner, revertWorktree, "feature.txt", "the feature");

    git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of(fix));

    GitRepos.git(runner, repo, "fetch", "-q");
    String history = GitRepos.git(runner, repo, "log", "--format=%B", "origin/dev").stdout();
    assertThat(history)
        .contains("This reverts commit " + fix)
        .contains("This reverts commit " + merge);
    assertThat(
            GitRepos.git(runner, repo, "cat-file", "-e", "origin/dev:feature.txt")
                .exitCode())
        .isNotZero();
    assertThat(revertWorktree).doesNotExist();
  }

  @Test
  void reportsFailedMergeAsAnErrorAndNotAsConflictWhenNothingIsUnmerged() throws Exception {
    Processes runner =
        ScriptedProcesses.answering(
            List.of("git", "merge"), new Processes.Result(128, "", "Author identity unknown"));
    Path repo = GitRepos.cloneWithTask(runner, dir);
    GitDeploy git = new GitDeploy(GitRepos.commands(runner));

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(GitDeploy.MergeConflictException.class)
        .hasMessageContaining("no conflict is waiting for you")
        .hasMessageContaining("Author identity unknown");

    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
  }

  @Test
  void dropsTheDeployWorktreeAndSaysSoWhenDevMovedPastWhatItHeldBeforeThePush() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    Path hook = repo.resolve(".git/hooks/pre-push");
    Files.writeString(
        hook,
        "#!/bin/sh\n"
            + "c=$(git commit-tree HEAD^{tree} -p HEAD -m later) && git push -q --no-verify origin"
            + " \"$c:refs/heads/dev\"\n");
    Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.NothingToDeployException.class)
        .hasMessageContaining("Nothing left to push");

    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    assertThat(
            GitRepos.git(runner, repo, "rev-parse", "--verify", "--quiet", "jagt-deploy-ABC-1")
                .exitCode())
        .isNotZero();
  }

  @Test
  void keepsTheDeployWorktreeAndNamesGitsRefusalWhenDevMovedUnderTheMerge() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    Path hook = repo.resolve(".git/hooks/pre-push");
    Files.writeString(
        hook,
        "#!/bin/sh\n"
            + "c=$(git commit-tree origin/dev^{tree} -p origin/dev -m other) && git push -q"
            + " --no-verify origin \"$c:refs/heads/dev\"\n");
    Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(GitDeploy.NothingToDeployException.class)
        .hasMessageContaining("Deploy push to dev was rejected")
        .satisfies(refused -> assertThat(refused.getMessage()).doesNotEndWith("Details: "));

    assertThat(dir.resolve("ABC-1-deploy")).isDirectory();
  }

  @Test
  void revertsThroughRevertPathStaleRunLeftBehind() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    Files.createDirectories(dir.resolve("ABC-1-revert"));
    Files.writeString(dir.resolve("ABC-1-revert").resolve("stale.txt"), "left by a run that died");

    String revert = git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of());

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(revert);
    assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
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

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Revert push to dev was rejected")
        .satisfies(refused -> assertThat(refused.getMessage()).doesNotEndWith("Details: "));
  }

  @Test
  void readsDeployConflictAsResolvedOnceTheHumanCommittedTheMergeByHand() throws Exception {
    Path repo = GitRepos.cloneWithDeployedFeatureBranch(runner, dir);
    GitRepos.git(runner, repo, "checkout", "-q", "-B", "_dev", "origin/dev");
    Files.writeString(repo.resolve("feature.txt"), "dev change");
    GitRepos.commit(runner, repo, "dev");
    GitRepos.git(runner, repo, "push", "-q", "origin", "_dev:dev");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
        .isInstanceOf(GitDeploy.MergeConflictException.class);
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "feature.txt", "resolved");
    GitRepos.git(runner, deployWorktree, "commit", "-qm", "resolved");

    boolean resolved = git.deployResolved(repo, "ABC-1", "dev");

    assertThat(resolved).isTrue();
  }

  @Test
  void cutsTheDeployBranchAsItStandsIntoWorktreeWhoseFixIsNotResolvedUntilItIsStaged()
      throws Exception {
    Path repo = GitRepos.cloneWithFeatureBranch(runner, dir);
    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    git.openFixWorktree(repo, "ABC-1", "dev");

    Path deployWorktree = dir.resolve("ABC-1-deploy");
    assertThat(deployWorktree.resolve("feature.txt")).hasContent("the feature");
    assertThat(git.hasDeployWorktree(repo, "ABC-1")).isTrue();
    assertThat(git.deployResolved(repo, "ABC-1", "dev")).isFalse();
    GitRepos.stage(runner, deployWorktree, "feature.txt", "the feature, fixed");
    assertThat(git.deployResolved(repo, "ABC-1", "dev")).isTrue();
  }

  @Test
  void deployingFinishesTheFixStagedOnTheDeployBranchInsteadOfMergingTheDeployAgain()
      throws Exception {
    Path repo = GitRepos.cloneWithFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    git.openFixWorktree(repo, "ABC-1", "dev");
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "feature.txt", "the feature, fixed");

    String fix = git.mergeIntoAndPush(repo, "ABC-1", "dev");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(fix).isNotEqualTo(merge);
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/dev").stdout().trim())
        .isEqualTo(fix);
    assertThat(
            GitRepos.git(runner, repo, "cat-file", "-p", "origin/dev:feature.txt")
                .stdout())
        .contains("the feature, fixed");
    assertThat(GitRepos.git(runner, repo, "rev-parse", fix + "^1").stdout().trim())
        .isEqualTo(merge);
    assertThat(deployWorktree).doesNotExist();
  }

  @Test
  void revertTakesTheFixOutWithTheMergeItWasMadeOn() throws Exception {
    Path repo = GitRepos.cloneWithFeatureBranch(runner, dir);
    final String merge = git.mergeIntoAndPush(repo, "ABC-1", "dev");
    git.openFixWorktree(repo, "ABC-1", "dev");
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.stage(runner, deployWorktree, "feature.txt", "the feature, fixed");
    String fix = git.mergeIntoAndPush(repo, "ABC-1", "dev");

    git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of(fix));

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(git.isFix(repo, fix)).isTrue();
    assertThat(git.isFix(repo, merge)).isFalse();
    assertThat(
            GitRepos.git(runner, repo, "cat-file", "-e", "origin/dev:feature.txt")
                .exitCode())
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
        runner, web, GitDeploy.revertWorktreePath(web, "ABC-1"), "jagt-revert-ABC-1");

    assertThat(git.hasRevertWorktree(web, "ABC-1")).isTrue();
    assertThat(git.hasRevertWorktree(api, "ABC-1")).isFalse();
  }

  @Test
  void cutsTheDeployBranchAgainAfterTheFixWorktreeDirectoryWasDeletedByHand() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("base.txt"), "base");
    GitRepos.commit(runner, repo, "base");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main:dev");
    Path deployWorktree = dir.resolve("ABC-1-deploy");
    GitRepos.git(runner, repo, "fetch", "-q");
    GitRepos.git(
        runner,
        repo,
        "worktree",
        "add",
        "-q",
        "-B",
        "jagt-deploy-ABC-1",
        deployWorktree.toString(),
        "origin/dev");
    runner.run(dir, GitRepos.TIMEOUT, List.of("rm", "-rf", deployWorktree.toString()));

    git.openFixWorktree(repo, "ABC-1", "dev");

    assertThat(deployWorktree.resolve("base.txt")).hasContent("base");
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

    assertThatThrownBy(() -> git.revertMergeAndPush(repo, "ABC-1", "dev", merge, List.of()))
        .isExactlyInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no conflict is waiting for you");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"0123456789abcdef,01234567", "abcdef12,abcdef12", "abc1234,abc1234", "NIL,null"},
      nullValues = "NIL")
  void showsShaAsItsFirstEightCharactersAndMissingOneAsNull(String sha, String shown) {
    assertThat(GitDeploy.shortSha(sha)).isEqualTo(shown);
  }

  @Test
  void deploysIntoTheLocalDeployBranchWithNoPushWhereTheRepositoryHasNoRemote() throws Exception {
    Path repo = GitRepos.localRepoWithTask(runner, dir);
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    GitDeploy git =
        new GitDeploy(
            new GitCommands(
                runner,
                new LsofWorktreeProcesses(runner),
                path -> java.util.Optional.of(dev.jagt.orchestrator.task.Remote.LOCAL),
                stopped -> {}));

    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    assertThat(GitRepos.git(runner, repo, "cat-file", "-p", "dev:g.txt").stdout())
        .contains("task");
    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
  }

  @Test
  void deploysIntoLocalTargetThatTheRepositoryHasCheckedOut() throws Exception {
    Path repo = GitRepos.localRepoWithTask(runner, dir);
    GitRepos.git(runner, repo, "checkout", "-q", "dev");
    GitDeploy git =
        new GitDeploy(
            new GitCommands(
                runner,
                new LsofWorktreeProcesses(runner),
                path -> java.util.Optional.of(dev.jagt.orchestrator.task.Remote.LOCAL),
                stopped -> {}));

    git.mergeIntoAndPush(repo, "ABC-1", "dev");

    assertThat(repo.resolve("g.txt")).hasContent("task");
    assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
  }
}
