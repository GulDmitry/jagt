package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.WorktreeContainers;
import dev.jagt.orchestrator.port.WorktreeProcesses;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorktreesTest {

  @TempDir Path dir;
  private final Processes runner = new ProcessRunner();
  private final GitWorktrees git = new GitWorktrees(GitRepos.commands(runner));

  @Test
  void refusesWorktreeCreationWhenTicketBranchSurvivedPreviousRun() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "ABC-1");

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recreate")
        .hasMessageContaining("resume");
  }

  @Test
  void readsLeftoverBranchAsHoldingWorkOnlyOnceItCarriesCommitTheBaseLacks() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "ABC-1");

    assertThat(git.holdsOwnCommits(repo, "ABC-1", "origin/main")).isFalse();

    GitRepos.git(runner, repo, "checkout", "-q", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "changed");
    GitRepos.git(runner, repo, "commit", "-qam", "ABC-1 Widen the column");

    assertThat(git.holdsOwnCommits(repo, "ABC-1", "origin/main")).isTrue();
  }

  @Test
  void readsLeftoverBranchAsHoldingWorkWhenOnlyItsCopyAtOriginCarriesCommit() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "changed");
    GitRepos.git(runner, repo, "commit", "-qam", "ABC-1 Widen the column");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    GitRepos.git(runner, repo, "branch", "-qf", "ABC-1", "main");
    GitRepos.git(runner, repo, "fetch", "-q", "origin");

    assertThat(git.holdsOwnCommits(repo, "ABC-1", "origin/main")).isTrue();
  }

  @Test
  void cutsTheWorktreeFromFreshlyFetchedUpstreamEvenWhenBaseBranchIsSpelledLocally()
      throws Exception {
    final Path repo = GitRepos.clone(runner, dir, "repo", "base");
    Path other = GitRepos.siblingClone(runner, dir, GitRepos.originOf(dir, "repo"), "other");
    Files.writeString(other.resolve("f.txt"), "moved on");
    GitRepos.git(runner, other, "commit", "-qam", "ahead");
    GitRepos.git(runner, other, "push", "-q", "origin", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "main", BranchStrategy.FRESH);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("moved on");
  }

  @Test
  void removeWorktreeReapsEveryWorktreeRootedProcessNotJustJava() throws Exception {
    assumeTrue(
        Arrays.stream(System.getenv("PATH").split(":"))
            .anyMatch(bin -> Files.isExecutable(Path.of(bin, "lsof"))),
        "lsof is not installed — the reap cannot see cwds without it");
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    Path wt = dir.resolve("wt");
    git.createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);
    Process rooted = new ProcessBuilder("sleep", "300").directory(wt.toFile()).start();
    try {
      git.removeWorktree(repo, wt, "ABC-1");

      assertThat(rooted.waitFor(5, TimeUnit.SECONDS))
          .as("a non-java process rooted in the worktree must be reaped on removal")
          .isTrue();
    } finally {
      rooted.destroyForcibly();
    }
  }

  @Test
  void removeWorktreeStopsTheContainerStackStartedInsideIt() {
    Processes runner = mock(Processes.class);
    when(runner.run(any(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    WorktreeContainers containers = mock(WorktreeContainers.class);
    GitWorktrees git =
        new GitWorktrees(
            new GitCommands(
                runner,
                mock(WorktreeProcesses.class),
                path -> java.util.Optional.empty(),
                containers));

    git.removeWorktree(dir.resolve("repo"), dir.resolve("alpha-wt"), null);

    verify(containers).stop(dir.resolve("alpha-wt"));
  }

  @Test
  void keepsExistingCommitsWhenReopenedTicketResumesItsBranch() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "task work");
    GitRepos.git(runner, repo, "commit", "-qam", "work");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("task work");
  }

  @Test
  void findsTheWorktreeSomeoneElseCutOnTheTaskBranch() throws Exception {
    Path repo = GitRepos.emptyRepo(runner, dir);
    GitRepos.addWorktree(runner, repo, dir.resolve("by-hand"), "ABC-1");

    Optional<Path> found = git.worktreeOf(repo, "ABC-1");

    assertThat(found).contains(dir.resolve("by-hand").toRealPath());
  }

  @Test
  void findsNoWorktreeWhereOnlyTheRepositoryItselfHasTheBranchCheckedOut() throws Exception {
    Path repo = dir.resolve("repo");
    GitRepos.git(runner, dir, "init", "-q", "-b", "ABC-1", repo.toString());
    GitRepos.git(runner, repo, "commit", "-q", "--allow-empty", "-m", "init");

    Optional<Path> found = git.worktreeOf(repo.toRealPath(), "ABC-1");

    assertThat(found).isEmpty();
  }

  @Test
  void resumesTheBranchAsOriginHoldsItWhenItMovedOnElsewhere() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "task work");
    GitRepos.git(runner, repo, "commit", "-qam", "work");
    Files.writeString(repo.resolve("f.txt"), "rebased onto the new release");
    GitRepos.git(runner, repo, "commit", "-qam", "rebased");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    GitRepos.git(runner, repo, "reset", "--hard", "-q", "HEAD~1");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("rebased onto the new release");
  }

  @Test
  void refusesToResumeBranchRewrittenOnOrigin() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "rebased onto the new release");
    GitRepos.git(runner, repo, "commit", "-qam", "rebased");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    GitRepos.git(runner, repo, "reset", "--hard", "-q", "HEAD~1");
    Files.writeString(repo.resolve("f.txt"), "the commit only this machine has");
    GitRepos.git(runner, repo, "commit", "-qam", "local");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diverged");
    assertThat(dir.resolve("wt")).doesNotExist();
  }

  @Test
  void removesTheResumedWorktreeAndKeepsTheBranchWhenThePushBackIsRefused() throws Exception {
    Path repo = GitRepos.cloneWithPushedTask(runner, dir);
    GitRepos.shipRelease(runner, repo);
    GitRepos.refusePushes(runner, repo, dir);

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("push was refused")
        .hasMessageContaining("the rebase was undone");
    assertThat(dir.resolve("wt")).doesNotExist();
    assertThat(git.branchExists(repo, "ABC-1")).isTrue();
  }

  @Test
  void removesTheWorktreeAndTheBranchItCutWhenResumeFromOriginCannotPushBack() throws Exception {
    Path repo = GitRepos.cloneWithPushedTask(runner, dir);
    GitRepos.git(runner, repo, "branch", "-qD", "ABC-1");
    GitRepos.shipRelease(runner, repo);
    GitRepos.refusePushes(runner, repo, dir);

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("push was refused");
    assertThat(dir.resolve("wt")).doesNotExist();
    assertThat(git.branchExists(repo, "ABC-1")).isFalse();
  }

  @Test
  void replaysResumedBranchOnTheTargetItMergesIntoAndPushesItBack() throws Exception {
    Path repo = GitRepos.cloneWithPushedTask(runner, dir);
    GitRepos.shipRelease(runner, repo);

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt").resolve("release.txt")).exists();
    assertThat(
            GitRepos.git(runner, dir.resolve("wt"), "rev-list", "--count", "origin/ABC-1..ABC-1")
                .stdout()
                .strip())
        .isEqualTo("0");
  }

  @Test
  void resumesPushedBranchWhoseTargetIsGoneFromOrigin() throws Exception {
    Path repo = GitRepos.cloneWithPushedTask(runner, dir);

    git.createWorktree(
        repo, dir.resolve("wt"), "ABC-1", "origin/release-that-was-deleted", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt").resolve("task.txt")).exists();
  }

  @Test
  void leavesConflictingResumeRebaseStandingForTheSessionToResolve() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "what the task made of it");
    GitRepos.git(runner, repo, "commit", "-qam", "work");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    Files.writeString(repo.resolve("f.txt"), "what the release made of it");
    GitRepos.git(runner, repo, "commit", "-qam", "release");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(GitRepos.git(runner, dir.resolve("wt"), "status", "--porcelain").stdout())
        .contains("UU f.txt");
    assertThat(
            GitRepos.git(
                    runner,
                    dir,
                    "--git-dir",
                    GitRepos.originOf(dir, "repo").toString(),
                    "log",
                    "-1",
                    "--format=%s",
                    "ABC-1")
                .stdout()
                .strip())
        .isEqualTo("work");
  }

  @Test
  void resumesFromTheRequestsOwnBranchWhenThisMachineNeverHadIt() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "the work the request shows");
    GitRepos.git(runner, repo, "commit", "-qam", "work");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    GitRepos.git(runner, repo, "branch", "-qD", "ABC-1");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("the work the request shows");
  }

  @Test
  void freesTheBaseRepositoryWhenItStillHoldsTheBranchThisTaskNeeds() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(
            runner.run(repo, Duration.ofSeconds(30), List.of("git", "worktree", "list")).stdout())
        .contains("(detached HEAD)")
        .containsPattern("wt +\\w+ \\[ABC-1]");
  }

  @Test
  void refusesWhenTheCheckoutHoldingTheBranchHasUncommittedWork() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "work nobody committed");

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("uncommitted changes");
    assertThat(dir.resolve("wt")).doesNotExist();
    assertThat(
            runner
                .run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout()
                .strip())
        .isEqualTo("ABC-1");
  }

  @Test
  void freesTheBaseRepositoryBeforeDeletingTheBranchItStillHolds() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

    assertThat(dir.resolve("wt")).isDirectory();
    assertThat(
            runner
                .run(
                    repo,
                    Duration.ofSeconds(30),
                    List.of("git", "log", "-1", "--format=%s", "ABC-1"))
                .stdout())
        .contains("init");
  }

  @Test
  void leavesTheCheckoutAloneWhenItRefusesAnExistingBranch() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.FRESH))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            runner
                .run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout()
                .strip())
        .isEqualTo("ABC-1");
  }

  @Test
  void freesCheckoutThatOnlyHasUntrackedFilesInIt() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("scratch.txt"), "never added to git");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt")).isDirectory();
    assertThat(repo.resolve("scratch.txt")).exists();
  }

  @Test
  void resumesTheBranchWhenTheRequestTargetsBaseThatNoLongerExists() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");

    git.createWorktree(
        repo, dir.resolve("wt"), "ABC-1", "origin/deleted-base", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt")).isDirectory();
    assertThat(
            runner
                .run(
                    dir.resolve("wt"),
                    Duration.ofSeconds(30),
                    List.of("git", "branch", "--show-current"))
                .stdout()
                .strip())
        .isEqualTo("ABC-1");
  }

  @Test
  void putsTheCheckoutBackWhenTheWorktreeItWasFreedForCannotBeCut() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");

    Path fileEvenRootCannotCreateUnder = Files.writeString(dir.resolve("in-the-way"), "");

    assertThatThrownBy(() ->
            git.createWorktree(
                repo,
                fileEvenRootCannotCreateUnder.resolve("wt"),
                "ABC-1",
                "origin/main",
                BranchStrategy.RESUME))
        .isInstanceOf(RuntimeException.class);

    assertThat(
            runner
                .run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout()
                .strip())
        .isEqualTo("ABC-1");
  }

  @Test
  void leavesTheFilesOfTheFreedCheckoutExactlyAsTheyWere() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "the branch's own content");
    runner.run(repo, Duration.ofSeconds(30), List.of("git", "add", "."));
    runner.run(
        repo,
        Duration.ofSeconds(30),
        List.of(
            "git",
            "-c",
            "user.email=t@t",
            "-c",
            "user.name=t",
            "commit",
            "-qm",
            "on the branch only"));

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(repo.resolve("f.txt")).content().isEqualTo("the branch's own content");
  }

  @Test
  void leavesRepositoryOnItsOwnBranchAloneWhenAskedToReattach() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    runner.run(repo, Duration.ofSeconds(30), List.of("git", "checkout", "-q", "main"));

    String stopped = git.reattach(repo, "ABC-1");

    assertThat(stopped).isNull();
    assertThat(
            runner
                .run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout()
                .strip())
        .isEqualTo("main");
  }

  @Test
  void putsTheCheckoutBackWhenRecreatingTheBranchLeavesTheWorktreeUncut() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Path notDirectory = Files.writeString(dir.resolve("in-the-way"), "");

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, notDirectory.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE))
        .isInstanceOf(RuntimeException.class);

    assertThat(
            runner
                .run(repo, Duration.ofSeconds(30), List.of("git", "symbolic-ref", "-q", "HEAD"))
                .exitCode())
        .as("the repository is on a branch, not left detached")
        .isZero();
  }

  @Test
  void refusesWhenAnotherWorktreeHoldsTheBranch() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    runner.run(repo, Duration.ofSeconds(30), List.of("git", "checkout", "-q", "main"));
    runner.run(
        repo,
        Duration.ofSeconds(30),
        List.of("git", "worktree", "add", "-q", dir.resolve("elsewhere").toString(), "ABC-1"));

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("elsewhere")
        .hasMessageContaining("free it there");
    assertThat(dir.resolve("wt")).doesNotExist();
  }

  @Test
  void startsBranchFreshFromBaseWhenReopenedTicketRecreatesIt() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "stale merged work");
    GitRepos.git(runner, repo, "commit", "-qam", "old");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
  }

  @Test
  void leavesTheTaskBranchWithoutTheBaseBranchAsUpstream() throws Exception {
    Path origin = dir.resolve("origin.git");
    Path repo = dir.resolve("repo");
    GitRepos.git(runner, dir, "init", "-q", "--bare", "-b", "release", origin.toString());
    GitRepos.git(runner, dir, "clone", "-q", origin.toString(), repo.toString());
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "release");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/release", BranchStrategy.FRESH);

    var upstream =
        GitRepos.git(runner, dir.resolve("wt"), "rev-parse", "--abbrev-ref", "ABC-1@{upstream}");
    assertThat(upstream.exitCode()).isNotZero();
  }

  @Test
  void clearsStaleLeftoverDirectoryBeforeCreatingTheWorktree() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    Path wt = dir.resolve("wt");
    Files.createDirectories(wt);
    Files.writeString(wt.resolve("leftover.txt"), "stale");

    git.createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);

    assertThat(wt.resolve("f.txt")).hasContent("base");
  }

  @Test
  void deletesTheDirectoryEvenWhenGitWorktreeRemoveFails() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");
    Path leftover = dir.resolve("leftover");
    Files.createDirectories(leftover);
    Files.writeString(leftover.resolve("junk.txt"), "x");

    git.removeWorktree(repo, leftover, null);

    assertThat(leftover).doesNotExist();
  }

  @Test
  void removesTheWorktreeEvenWhenTheProcessReaperIsNotInstalled() throws Exception {
    Processes runner = ScriptedProcesses.failingToStart("lsof");
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    Path worktree = dir.resolve("wt");
    git.createWorktree(repo, worktree, "ABC-1", "origin/main", BranchStrategy.FRESH);

    git.removeWorktree(repo, worktree, "ABC-1");

    assertThat(worktree).doesNotExist();
  }

  @Test
  void readsTheRemoteWithoutTheTokenItsUrlCarries() throws Exception {
    GitRepos.git(runner, dir, "init", "-q", dir.toString());
    GitRepos.git(runner, dir, "remote", "add", "origin", "https://bot:s3cret@code.example/g/p.git");

    String remote = git.remoteUrl(dir);

    assertThat(remote).isEqualTo("https://code.example/g/p.git");
  }

  @Test
  void reattachesRepositoryDetachedAtTheBranchTipAndReportsNothingStopped() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    GitRepos.git(runner, repo, "switch", "-q", "--detach");

    String stopped = git.reattach(repo, "ABC-1");

    assertThat(stopped).isNull();
    assertThat(GitRepos.git(runner, repo, "branch", "--show-current").stdout().strip())
        .isEqualTo("ABC-1");
  }

  @Test
  void leavesDetachedRepositoryWhereItStandsWhenItIsNotAtTheBranchTip() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("f.txt"), "task work");
    GitRepos.git(runner, repo, "commit", "-qam", "work");
    GitRepos.git(runner, repo, "switch", "-q", "--detach", "HEAD~1");

    String stopped = git.reattach(repo, "ABC-1");

    assertThat(stopped).isNull();
    assertThat(GitRepos.git(runner, repo, "branch", "--show-current").stdout().strip()).isEmpty();
  }

  @Test
  void reportsWhyTheBranchCouldNotBePutBackWhenAnotherWorktreeNowHoldsIt() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    GitRepos.git(runner, repo, "switch", "-q", "--detach");
    GitRepos.git(
        runner, repo, "worktree", "add", "-q", dir.resolve("elsewhere").toString(), "ABC-1");

    String stopped = git.reattach(repo, "ABC-1");

    assertThat(stopped).contains("ABC-1").contains("elsewhere");
  }

  @Test
  void recreatesTheBranchWhenTheWorktreeHoldingItWasDeletedByHand() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "ABC-1");
    GitRepos.git(runner, repo, "worktree", "add", "-q", dir.resolve("gone").toString(), "ABC-1");
    runner.run(dir, GitRepos.TIMEOUT, List.of("rm", "-rf", dir.resolve("gone").toString()));

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
    assertThat(GitRepos.git(runner, repo, "worktree", "list").stdout()).doesNotContain("gone");
  }

  @Test
  void leavesTheResumedBranchWithoutAnUpstreamEvenWhenItWasPushedWithOne() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    GitRepos.git(runner, repo, "push", "-q", "-u", "origin", "ABC-1");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(
            GitRepos.git(runner, dir.resolve("wt"), "rev-parse", "--abbrev-ref", "ABC-1@{upstream}")
                .exitCode())
        .isNotZero();
  }

  @Test
  void resumesBranchHoldingCommitsOriginHasNeverSeen() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-qb", "ABC-1");
    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");
    Files.writeString(repo.resolve("local.txt"), "only on this machine");
    GitRepos.commit(runner, repo, "local");
    GitRepos.git(runner, repo, "checkout", "-q", "main");

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

    assertThat(dir.resolve("wt").resolve("local.txt")).hasContent("only on this machine");
  }

  @Test
  void refusesResumeWhoseRebaseOntoTheTargetCannotStart() throws Exception {
    Path repo = GitRepos.cloneWithPushedTask(runner, dir);
    GitRepos.shipRelease(runner, repo);
    Path hook = repo.resolve(".git/hooks/pre-rebase");
    Files.writeString(hook, "#!/bin/sh\nexit 1\n");
    Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

    assertThatThrownBy(() ->
            git.createWorktree(
                repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Rebasing 'ABC-1' onto origin/main failed");
  }

  @Test
  void readsWorktreeAsStandingOnBranchOnlyOnceItsHeadHoldsWhatOriginHasThere() throws Exception {
    final Path repo = GitRepos.clone(runner, dir, "repo", "base");
    final Path other = GitRepos.siblingClone(runner, dir, GitRepos.originOf(dir, "repo"), "other");
    GitRepos.git(runner, other, "checkout", "-qb", "ABC-41");
    Files.writeString(other.resolve("g.txt"), "stacked on");
    GitRepos.commit(runner, other, "ABC-41");
    GitRepos.git(runner, other, "push", "-q", "origin", "ABC-41");

    assertThat(git.standsOn(repo, "ABC-41")).isFalse();

    GitRepos.git(runner, repo, "reset", "-q", "--hard", "origin/ABC-41");

    assertThat(git.standsOn(repo, "ABC-41")).isTrue();
  }

  @Test
  void readsBranchAtOriginAsPresentOnlyOnceItWasPushed() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "ABC-1");

    assertThat(git.remoteBranchExists(repo, "ABC-1")).isFalse();

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-1");

    assertThat(git.remoteBranchExists(repo, "ABC-1")).isTrue();
  }

  @Test
  void unregistersWorktreeWhoseDirectoryWasDeletedByHandAndDeletesItsBranch() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.addWorktree(runner, repo, dir.resolve("wt"), "ABC-1");
    runner.run(dir, GitRepos.TIMEOUT, List.of("rm", "-rf", dir.resolve("wt").toString()));

    git.removeWorktree(repo, dir.resolve("wt"), "ABC-1");

    assertThat(GitRepos.git(runner, repo, "worktree", "list").stdout()).doesNotContain("wt");
    assertThat(git.branchExists(repo, "ABC-1")).isFalse();
  }

  @Test
  void removesTheDeployWorktreeAndItsBranchWhenOneIsWaiting() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    Path deploy = GitDeploy.deployWorktreePath(repo, "ABC-1");
    GitRepos.git(
        runner,
        repo,
        "worktree",
        "add",
        "-q",
        "-b",
        GitDeploy.deployBranch("ABC-1"),
        deploy.toString(),
        "origin/main");

    git.removeDeployWorktreeIfPresent(repo, "ABC-1");

    assertThat(GitDeploy.deployWorktreePath(repo, "ABC-1")).doesNotExist();
    assertThat(git.branchExists(repo, GitDeploy.deployBranch("ABC-1"))).isFalse();
  }

  @Test
  void discardsTheDiffCheckoutsOfRepositoryNobodyCanNameAnyMore() throws Exception {
    Path first = DiffCheckouts.diffWorktreePaths("ABC-5", "gone").getFirst();
    Path last = DiffCheckouts.diffWorktreePaths("ABC-5", "gone").getLast();
    Files.createDirectories(first);
    Files.writeString(first.resolve("f.txt"), "base");
    Files.createDirectories(last);
    Files.writeString(last.resolve("f.txt"), "task");

    git.discardDiffWorktrees("ABC-5", "gone");

    assertThat(first).doesNotExist();
    assertThat(last).doesNotExist();
  }

  @Test
  void namesTheCommonGitDirectoryOfRepository() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");

    Path common = git.gitCommonDir(repo);

    assertThat(common).isEqualTo(repo.toRealPath().resolve(".git"));
  }

  @Test
  void warnsWhereGitCouldNotRemoveTheWorktreeItWasAskedTo() throws Exception {
    Path repo = GitRepos.emptyRepo(runner, dir);
    Path notWorktree = Files.createDirectories(dir.resolve("leftover"));

    try (FakeLogCapture log = new FakeLogCapture(GitWorktrees.class)) {
      git.removeWorktree(repo, notWorktree, null);

      assertThat(log.events())
          .filteredOn(event -> "git worktree remove failed".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.WARN);
    }
  }

  @Test
  void warnsWhereGitCouldNotDeleteTheBranchItWasAskedTo() throws Exception {
    Path repo = GitRepos.emptyRepo(runner, dir);
    Path notWorktree = Files.createDirectories(dir.resolve("leftover"));

    try (FakeLogCapture log = new FakeLogCapture(GitWorktrees.class)) {
      git.removeWorktree(repo, notWorktree, "ABC-404");

      assertThat(log.events())
          .filteredOn(event -> "git branch delete failed".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.WARN);
    }
  }

  @Test
  void stillFindsTheWorktreeSomeoneElseCutOnTheTaskBranchAfterDeletingItsDirectoryByHand()
      throws Exception {
    Path repo = GitRepos.emptyRepo(runner, dir);
    GitRepos.addWorktree(runner, repo, dir.resolve("by-hand"), "ABC-1");
    Path where = dir.toRealPath().resolve("by-hand");
    Files.delete(where.resolve(".git"));
    Files.delete(where);

    Optional<Path> found = git.worktreeOf(repo, "ABC-1");

    assertThat(found).contains(where);
  }

  @Test
  void failsWithItsOwnCauseAloneWhenRecreatingBranchNobodyHadCheckedOut() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "branch", "ABC-1");
    Path notDirectory = Files.writeString(dir.resolve("in-the-way"), "");

    Throwable failure =
        catchThrowable(() ->
            git.createWorktree(
                repo, notDirectory.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE));

    assertThat(failure).isInstanceOf(RuntimeException.class).hasNoSuppressedExceptions();
  }

  @Test
  void cutsTaskWorktreeFromTheLocalBaseBranchWhereTheRepositoryHasNoRemote() throws Exception {
    Path repo = dir.resolve("repo");
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");
    GitWorktrees git =
        new GitWorktrees(
            new GitCommands(
                runner,
                new LsofWorktreeProcesses(runner),
                path -> Optional.of(dev.jagt.orchestrator.task.Remote.LOCAL),
                stopped -> {}));

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "main", BranchStrategy.FRESH);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
  }

  @Test
  void cutsTaskWorktreeFromTheRemoteTheProjectNamesInsteadOfOrigin() throws Exception {
    Path upstream = dir.resolve("upstream.git");
    Path repo = dir.resolve("repo");
    GitRepos.git(runner, dir, "init", "-q", "--bare", "-b", "main", upstream.toString());
    GitRepos.git(
        runner, dir, "clone", "-q", "-o", "upstream", upstream.toString(), repo.toString());
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "upstream", "main");
    GitWorktrees git =
        new GitWorktrees(
            new GitCommands(
                runner,
                new LsofWorktreeProcesses(runner),
                path -> Optional.of(new dev.jagt.orchestrator.task.Remote("upstream")),
                stopped -> {}));

    git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "upstream/main", BranchStrategy.FRESH);

    assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
  }
}
