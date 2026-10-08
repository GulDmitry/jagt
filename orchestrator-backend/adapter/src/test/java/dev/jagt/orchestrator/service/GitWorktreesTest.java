package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GitWorktreesTest {

    @Test
    void refusesWorktreeCreationWhenTicketBranchSurvivedPreviousRun(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "branch", "ABC-1"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recreate")
                .hasMessageContaining("resume");
    }

    @Test
    void readsALeftoverBranchAsHoldingWorkOnlyOnceItCarriesACommitTheBaseLacks(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "branch", "ABC-1"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThat(git.holdsOwnCommits(repo, "ABC-1", "origin/main")).isFalse();

        runner.run(repo, timeout, List.of("git", "checkout", "-q", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "changed");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qam", "ABC-1 Widen the column"));

        assertThat(git.holdsOwnCommits(repo, "ABC-1", "origin/main")).isTrue();
    }

    @Test
    void readsALeftoverBranchAsHoldingWorkWhenOnlyItsCopyAtOriginCarriesACommit(@TempDir Path dir)
            throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "changed");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qam", "ABC-1 Widen the column"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        runner.run(repo, timeout, List.of("git", "branch", "-qf", "ABC-1", "main"));
        runner.run(repo, timeout, List.of("git", "fetch", "-q", "origin"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThat(git.holdsOwnCommits(repo, "ABC-1", "origin/main")).isTrue();
    }

    @Test
    void cutsTheWorktreeFromFreshlyFetchedUpstreamEvenWhenBaseBranchIsSpelledLocally(@TempDir Path dir)
            throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        Path other = dir.resolve("other");
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), other.toString()));
        Files.writeString(other.resolve("f.txt"), "moved on");
        runner.run(other, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "ahead"));
        runner.run(other, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "main", BranchStrategy.FRESH);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("moved on");
    }

    @Test
    void removeWorktreeReapsEveryWorktreeRootedProcessNotJustJava(@TempDir Path dir) throws Exception {
        assumeTrue(onPath("lsof"), "lsof is not installed — the reap cannot see cwds without it");
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
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
    void keepsExistingCommitsWhenReopenedTicketResumesItsBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "task work");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "work"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("task work");
    }

    @Test
    void resumesTheBranchAsOriginHoldsItWhenItMovedOnElsewhere(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "task work");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "work"));
        Files.writeString(repo.resolve("f.txt"), "rebased onto the new release");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "rebased"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "reset", "--hard", "-q", "HEAD~1"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("rebased onto the new release");
    }

    @Test
    void refusesToResumeABranchRewrittenOnOrigin(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "rebased onto the new release");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "rebased"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "reset", "--hard", "-q", "HEAD~1"));
        Files.writeString(repo.resolve("f.txt"), "the commit only this machine has");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "local"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.RESUME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diverged");
        assertThat(dir.resolve("wt")).doesNotExist();
    }

    @Test
    void replaysAResumedBranchOnTheTargetItMergesIntoAndPushesItBack(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("task.txt"), "the task's work");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "work"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        Files.writeString(repo.resolve("release.txt"), "shipped while the request waited");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "release"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("release.txt")).exists();
        assertThat(runner.run(dir.resolve("wt"), timeout,
                List.of("git", "rev-list", "--count", "origin/ABC-1..ABC-1")).stdout().strip()).isEqualTo("0");
    }

    @Test
    void resumesAPushedBranchWhoseTargetIsGoneFromOrigin(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("task.txt"), "the task's work");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "work"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/release-that-was-deleted",
                BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("task.txt")).exists();
    }

    @Test
    void leavesAConflictingResumeRebaseStandingForTheSessionToResolve(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "what the task made of it");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "work"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        Files.writeString(repo.resolve("f.txt"), "what the release made of it");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "release"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(runner.run(dir.resolve("wt"), timeout, List.of("git", "status", "--porcelain"))
                .stdout()).contains("UU f.txt");
        assertThat(runner.run(dir, timeout, List.of("git", "--git-dir", origin.toString(), "log", "-1",
                "--format=%s", "ABC-1")).stdout().strip()).isEqualTo("work");
    }

    @Test
    void resumesFromTheRequestsOwnBranchWhenThisMachineNeverHadIt(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "the work the request shows");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "work"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        runner.run(repo, timeout, List.of("git", "branch", "-qD", "ABC-1"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("the work the request shows");
    }

    @Test
    void freesTheBaseRepositoryWhenItStillHoldsTheBranchThisTaskNeeds(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "worktree", "list")).stdout())
                .contains("(detached HEAD)")
                .containsPattern("wt +\\w+ \\[ABC-1]");
    }

    @Test
    void refusesWhenTheCheckoutHoldingTheBranchHasUncommittedWork(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Files.writeString(repo.resolve("f.txt"), "work nobody committed");
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.RESUME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uncommitted changes");
        assertThat(dir.resolve("wt")).doesNotExist();
        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("ABC-1");
    }

    @Test
    void freesTheBaseRepositoryBeforeDeletingTheBranchItStillHolds(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(runner.run(repo, Duration.ofSeconds(30),
                List.of("git", "log", "-1", "--format=%s", "ABC-1")).stdout()).contains("init");
    }

    @Test
    void leavesTheCheckoutAloneWhenItRefusesAnExistingBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("ABC-1");
    }

    @Test
    void freesACheckoutThatOnlyHasUntrackedFilesInIt(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Files.writeString(repo.resolve("scratch.txt"), "never added to git");
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(repo.resolve("scratch.txt")).exists();
    }

    @Test
    void resumesTheBranchWhenTheRequestTargetsABaseThatNoLongerExists(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/deleted-base",
                BranchStrategy.RESUME);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(runner.run(dir.resolve("wt"), Duration.ofSeconds(30),
                List.of("git", "branch", "--show-current")).stdout().strip()).isEqualTo("ABC-1");
    }

    @Test
    void putsTheCheckoutBackWhenTheWorktreeItWasFreedForCannotBeCut(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        Path aFileEvenRootCannotCreateUnder = Files.writeString(dir.resolve("in-the-way"), "");

        assertThatThrownBy(() -> git.createWorktree(repo, aFileEvenRootCannotCreateUnder.resolve("wt"), "ABC-1",
                "origin/main", BranchStrategy.RESUME))
                .isInstanceOf(RuntimeException.class);

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("ABC-1");
    }

    @Test
    void leavesTheFilesOfTheFreedCheckoutExactlyAsTheyWere(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Files.writeString(repo.resolve("f.txt"), "the branch's own content");
        runner.run(repo, Duration.ofSeconds(30), List.of("git", "add", "."));
        runner.run(repo, Duration.ofSeconds(30), List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "on the branch only"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(repo.resolve("f.txt")).content().isEqualTo("the branch's own content");
    }

    @Test
    void leavesARepositoryOnItsOwnBranchAloneWhenAskedToReattach(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        runner.run(repo, Duration.ofSeconds(30), List.of("git", "checkout", "-q", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.reattach(repo, "ABC-1");

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("main");
    }

    @Test
    void putsTheCheckoutBackWhenRecreatingTheBranchLeavesTheWorktreeUncut(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Path notADirectory = Files.writeString(dir.resolve("in-the-way"), "");
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThatThrownBy(() -> git.createWorktree(repo, notADirectory.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.RECREATE))
                .isInstanceOf(RuntimeException.class);

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "symbolic-ref", "-q", "HEAD"))
                .exitCode()).as("the repository is on a branch, not left detached").isZero();
    }

    @Test
    void refusesWhenAnotherWorktreeHoldsTheBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        runner.run(repo, Duration.ofSeconds(30), List.of("git", "checkout", "-q", "main"));
        runner.run(repo, Duration.ofSeconds(30),
                List.of("git", "worktree", "add", "-q", dir.resolve("elsewhere").toString(), "ABC-1"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.RESUME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("elsewhere");
        assertThat(dir.resolve("wt")).doesNotExist();
    }

    private static Path repositoryOnItsOwnBranch(Processes runner, Path dir) throws IOException {
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm",
                "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        return repo;
    }

    @Test
    void startsBranchFreshFromBaseWhenReopenedTicketRecreatesIt(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1"));
        Files.writeString(repo.resolve("f.txt"), "stale merged work");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "old"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
    }

    @Test
    void leavesTheTaskBranchWithoutTheBaseBranchAsUpstream(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "release", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "release"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/release", BranchStrategy.FRESH);

        var upstream = runner.run(dir.resolve("wt"), timeout,
                List.of("git", "rev-parse", "--abbrev-ref", "ABC-1@{upstream}"));
        assertThat(upstream.exitCode()).isNotZero();
    }

    @Test
    void clearsAStaleLeftoverDirectoryBeforeCreatingTheWorktree(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        Path wt = dir.resolve("wt");
        Files.createDirectories(wt);
        Files.writeString(wt.resolve("leftover.txt"), "stale");
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);

        assertThat(wt.resolve("f.txt")).hasContent("base");
    }

    @Test
    void deletesTheDirectoryEvenWhenGitWorktreeRemoveFails(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        Path leftover = dir.resolve("leftover");
        Files.createDirectories(leftover);
        Files.writeString(leftover.resolve("junk.txt"), "x");
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));

        git.removeWorktree(repo, leftover, null);

        assertThat(leftover).doesNotExist();
    }

    @Test
    void removesTheWorktreeEvenWhenTheProcessReaperIsNotInstalled(@TempDir Path dir) throws Exception {
        Processes runner = ScriptedProcesses.failingToStart("lsof");
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "commit", "-qm", "init"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Path worktree = dir.resolve("wt");
        git.createWorktree(repo, worktree, "ABC-1", "origin/main", BranchStrategy.FRESH);

        git.removeWorktree(repo, worktree, "ABC-1");

        assertThat(worktree).doesNotExist();
    }

    private static boolean onPath(String binary) {
        String path = System.getenv("PATH");
        return path != null && Arrays.stream(path.split(":"))
                .anyMatch(dir -> !dir.isBlank() && Files.isExecutable(Path.of(dir, binary)));
    }
}
