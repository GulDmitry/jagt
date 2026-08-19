package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;

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

class GitServiceTest {

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recreate")
                .hasMessageContaining("resume");
    }

    /**
     * A fetch refreshes {@code origin/main} but never fast-forwards a checkout-less local branch, so cutting
     * from the local spelling would inherit whatever the clone last saw instead of what the teammate pushed.
     */
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "main", GitService.BranchStrategy.FRESH);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("moved on");
    }

    @Test
    void deployMergesTheTaskBranchIntoDevAndLeavesTheTaskBranchByteIdentical(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("o.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1"));
        Files.writeString(repo.resolve("g.txt"), "task");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "task"));
        String taskTip = runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim();

        new GitService(runner, new LsofWorktreeProcesses(runner)).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim()).isEqualTo(taskTip);
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout()).contains("task");
        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }

    @Test
    void deployConflictLeavesADeployWorktreeAndNeverModifiesTheTaskBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("o.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(repo, t, List.of("git", "fetch", "-q"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "_dev", "origin/dev"));
        Files.writeString(repo.resolve("f.txt"), "dev change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "dev"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "_dev:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1", "main"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "task"));
        String taskTip = runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim();
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class);

        assertThat(runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim()).isEqualTo(taskTip);
        assertThat(dir.resolve("ABC-1-deploy")).isDirectory();
    }

    @Test
    void refusesToFinishADeployFromAWorktreeAnotherRepositoryCut(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("api-origin.git");
        Path api = dir.resolve("api");
        Path web = dir.resolve("web");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), api.toString()));
        Files.writeString(api.resolve("f.txt"), "base");
        runner.run(api, t, List.of("git", "add", "."));
        runner.run(api, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base"));
        runner.run(api, t, List.of("git", "push", "-q", "origin", "main"));
        runner.run(api, t, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(api, t, List.of("git", "branch", "ABC-1"));
        runner.run(dir, t, List.of("git", "init", "-q", "-b", "main", web.toString()));
        runner.run(web, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "base"));
        runner.run(web, t, List.of("git", "worktree", "add", "-q", "-b", "jagt-deploy-ABC-1",
                GitService.deployWorktreePath(web, "ABC-1").toString()));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.mergeIntoAndPush(api, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holds a checkout api did not cut");
    }

    @Test
    void onlyTheRepositoryThatCutTheDeployWorktreeClaimsItWhenASiblingDerivesTheSamePath(@TempDir Path dir)
            throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        Path api = dir.resolve("api");
        Path web = dir.resolve("web");
        runner.run(dir, t, List.of("git", "init", "-q", "-b", "main", api.toString()));
        runner.run(dir, t, List.of("git", "init", "-q", "-b", "main", web.toString()));
        runner.run(web, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "base"));
        runner.run(web, t, List.of("git", "worktree", "add", "-q", "-b", "jagt-deploy-ABC-1",
                GitService.deployWorktreePath(web, "ABC-1").toString()));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThat(git.hasDeployWorktree(web, "ABC-1")).isTrue();
        assertThat(git.hasDeployWorktree(api, "ABC-1")).isFalse();
    }

    @Test
    void deployingAgainAfterResolvingTheDeployWorktreePushesDevAndCleansUp(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("o.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(repo, t, List.of("git", "fetch", "-q"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "_dev", "origin/dev"));
        Files.writeString(repo.resolve("f.txt"), "dev change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "dev"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "_dev:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1", "main"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "task"));
        String taskTip = runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim();
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:f.txt")).stdout()).contains("resolved");
        assertThat(deployWorktree).doesNotExist();
        assertThat(runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim()).isEqualTo(taskTip);
    }

    /**
     * Needs the real {@code lsof} — the reap has no other way to ask which processes sit in a directory. A
     * minimal image (many Linux containers) has none, and there the reap is a documented no-op, so this SKIPS
     * rather than fails: the production behaviour without lsof has its own test below. The {@code sleep} stands
     * in for the agent's Node session or any MCP daemon, which the reap must take by cwd rather than by name.
     */
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-1", "origin/main", GitService.BranchStrategy.FRESH);
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("task work");
    }

    /**
     * The reported case: the base repository itself sat on the ticket's branch, and git allows one checkout per
     * branch. Nobody works in that repository, so it is put back on the base branch instead of stopping a task.
     */
    @Test
    void freesTheBaseRepositoryWhenItStillHoldsTheBranchThisTaskNeeds(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RESUME);

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "worktree", "list")).stdout())
                .contains("(detached HEAD)")
                .containsPattern("wt +\\w+ \\[ABC-1]");
    }

    @Test
    void refusesWhenTheCheckoutHoldingTheBranchHasUncommittedWork(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Files.writeString(repo.resolve("f.txt"), "work nobody committed");
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.RESUME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uncommitted changes");
        assertThat(dir.resolve("wt")).doesNotExist();
        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("ABC-1");
    }

    /** `git branch -D` cannot delete a checked-out branch, so this is the strategy freeing exists for. */
    @Test
    void freesTheBaseRepositoryBeforeDeletingTheBranchItStillHolds(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RECREATE);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(runner.run(repo, Duration.ofSeconds(30),
                List.of("git", "log", "-1", "--format=%s", "ABC-1")).stdout()).contains("init");
    }

    /** A refusal must leave the human's own repository exactly where it was. */
    @Test
    void leavesTheCheckoutAloneWhenItRefusesAnExistingBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("ABC-1");
    }

    @Test
    void freesACheckoutThatOnlyHasUntrackedFilesInIt(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Files.writeString(repo.resolve("scratch.txt"), "never added to git");
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RESUME);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(repo.resolve("scratch.txt")).exists();
    }

    @Test
    void resumesTheBranchWhenTheRequestTargetsABaseThatNoLongerExists(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/deleted-base",
                GitService.BranchStrategy.RESUME);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(runner.run(dir.resolve("wt"), Duration.ofSeconds(30),
                List.of("git", "branch", "--show-current")).stdout().strip()).isEqualTo("ABC-1");
    }

    /** The switch is jagt's, so undoing it is too: a creation that fails afterwards owes the checkout back. */
    @Test
    void putsTheCheckoutBackWhenTheWorktreeItWasFreedForCannotBeCut(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        // A FILE where the worktree's parent directory would go: git cannot create anything under it, and
        // unlike a read-only directory that holds for root too — the container harness runs as one.
        Path notADirectory = Files.writeString(dir.resolve("in-the-way"), "");

        assertThatThrownBy(() -> git.createWorktree(repo, notADirectory.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.RESUME))
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RESUME);

        assertThat(repo.resolve("f.txt")).content().isEqualTo("the branch's own content");
    }

    /** Only what jagt detached is jagt's to move back: anything else is a checkout the human is standing in. */
    @Test
    void leavesARepositoryOnItsOwnBranchAloneWhenAskedToReattach(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        runner.run(repo, Duration.ofSeconds(30), List.of("git", "checkout", "-q", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.reattach(repo, "ABC-1");

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("main");
    }

    @Test
    void putsTheCheckoutBackWhenRecreatingTheBranchLeavesTheWorktreeUncut(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Path notADirectory = Files.writeString(dir.resolve("in-the-way"), "");
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.createWorktree(repo, notADirectory.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.RECREATE))
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.RESUME))
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RECREATE);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
    }

    @Test
    void namesTheRealTargetBranchInTheDeployMergeCommit(@TempDir Path dir) throws Exception {
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
        runner.run(repo, timeout, List.of("git", "branch", "dev"));
        Files.writeString(repo.resolve("d.txt"), "dev diverges");
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "dev"));
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "dev"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "dev"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1", "main"));
        Files.writeString(repo.resolve("g.txt"), "task");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "task"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        String subject = runner.run(repo, timeout,
                List.of("git", "log", "-1", "--format=%s", "origin/dev")).stdout().trim();
        assertThat(subject).isEqualTo("Merge branch 'ABC-1' into dev");
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/release", GitService.BranchStrategy.FRESH);

        var upstream = runner.run(dir.resolve("wt"), timeout,
                List.of("git", "rev-parse", "--abbrev-ref", "ABC-1@{upstream}"));
        assertThat(upstream.exitCode()).isNotZero();
    }

    @Test
    void deployConflictPushesNothingToDevAndLeavesTheDeployWorktreeWithTaskBranchUntouched(@TempDir Path dir)
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
        runner.run(repo, timeout, List.of("git", "branch", "dev"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "dev"));
        String devBefore = runner.run(repo, timeout, List.of("git", "rev-parse", "origin/dev")).stdout().trim();
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "dev"));
        Files.writeString(repo.resolve("f.txt"), "dev change");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "dev"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "dev"));
        String devWithOnlyDevCommit = runner.run(repo, timeout, List.of("git", "rev-parse", "dev")).stdout().trim();
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1", "main"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "task"));
        String taskTip = runner.run(repo, timeout, List.of("git", "rev-parse", "ABC-1")).stdout().trim();
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class)
                .hasMessageContaining("CONFLICT")
                .hasMessageContaining("nothing was pushed")
                .hasMessageContaining("ABC-1-deploy");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, timeout, List.of("git", "rev-parse", "origin/dev")).stdout().trim())
                .isEqualTo(devWithOnlyDevCommit).isNotEqualTo(devBefore);
        assertThat(runner.run(repo, timeout, List.of("git", "rev-parse", "ABC-1")).stdout().trim()).isEqualTo(taskTip);
        assertThat(dir.resolve("ABC-1-deploy")).isDirectory();
    }

    @Test
    void excludesGitIgnoredPlumbingFromTheIdeDiffSnapshot(@TempDir Path dir) throws Exception {
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-1", "origin/main", GitService.BranchStrategy.FRESH);
        Files.writeString(wt.resolve("f.txt"), "task change");
        Files.writeString(wt.resolve("new.js"), "new source");
        Files.writeString(wt.resolve("mcp_client.js"), "plumbing");
        Files.writeString(repo.resolve(".git").resolve("info").resolve("exclude"), "mcp_client.js\n");

        Path clean = git.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-1");

        assertThat(clean.resolve("mcp_client.js")).doesNotExist();
        assertThat(clean.resolve("f.txt")).hasContent("task change");
        assertThat(clean.resolve("new.js")).hasContent("new source");
    }

    @Test
    void commitsTheTasksWorkWithoutTheMcpConfigWrittenForThatWorktree(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        Files.writeString(repo.resolve(".mcp.json"), "{\"mcpServers\": {}}");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-1", "origin/main", GitService.BranchStrategy.FRESH);
        Files.writeString(wt.resolve("f.txt"), "the task's work");
        Files.writeString(wt.resolve(".mcp.json"), "{\"headers\": {\"X-Working-Directory\": \"" + wt + "\"}}");

        git.commitAll(repo, wt, "ABC-1 work");

        assertThat(runner.run(wt, timeout, List.of("git", "show", "--name-only", "--format=", "HEAD")).stdout())
                .contains("f.txt")
                .doesNotContain(".mcp.json");
        assertThat(wt.resolve(".mcp.json")).content().contains("X-Working-Directory");
    }

    @Test
    void refusesDeployWhenBranchHasNoCommitsBeyondTheTarget(@TempDir Path dir) throws Exception {
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
        runner.run(repo, timeout, List.of("git", "branch", "dev"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "dev"));
        runner.run(repo, timeout, List.of("git", "branch", "ABC-1", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nothing to deploy");
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.createWorktree(repo, wt, "ABC-1", "origin/main", GitService.BranchStrategy.FRESH);

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.removeWorktree(repo, leftover, null);

        assertThat(leftover).doesNotExist();
    }

    /** A deploy is one MERGE commit even where git could have fast-forwarded: that is what `revert` undoes. */
    @Test
    void publishesTaskCommitsWhenDeployMergesCleanly(@TempDir Path dir) throws Exception {
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
        runner.run(repo, timeout, List.of("git", "branch", "dev"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "dev"));
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1", "main"));
        Files.writeString(repo.resolve("g.txt"), "task feature");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "task"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        String taskTip = runner.run(repo, timeout, List.of("git", "rev-parse", "ABC-1")).stdout().trim();
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, timeout, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task feature");
        String parents = runner.run(repo, timeout,
                List.of("git", "rev-list", "--parents", "-n", "1", "origin/dev")).stdout().trim();
        assertThat(parents.split("\\s+")).hasSize(3).contains(taskTip);
    }

    /**
     * A cloned repo with origin/main + origin/dev and one committed task branch — the exact shape a deploy
     * needs. Extracted because every revert case starts from it, and ten lines of `git` per test is how a
     * suite stops being read.
     */
    private record Repo(Processes runner, Path dir, Path path) {

        private static final Duration T = Duration.ofSeconds(30);

        static Repo withTaskBranch(Path dir, String taskBranch) throws Exception {
            Processes runner = new ProcessRunner();
            Path origin = dir.resolve("o.git");
            Path repo = dir.resolve("repo");
            runner.run(dir, T, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
            runner.run(dir, T, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
            Repo fixture = new Repo(runner, dir, repo);
            Files.writeString(repo.resolve("base.txt"), "base");
            fixture.commitAll("base");
            runner.run(repo, T, List.of("git", "push", "-q", "origin", "main"));
            runner.run(repo, T, List.of("git", "push", "-q", "origin", "main:dev"));
            runner.run(repo, T, List.of("git", "checkout", "-q", "-b", taskBranch));
            Files.writeString(repo.resolve("feature.txt"), "the feature");
            fixture.commitAll("feature");
            runner.run(repo, T, List.of("git", "checkout", "-q", "main"));
            return fixture;
        }

        void commitAll(String message) {
            runner.run(path, T, List.of("git", "add", "-A"));
            runner.run(path, T, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                    "commit", "-qm", message));
        }

        /** Commits a change straight onto the shared dev branch — what "the branch moved on" means here. */
        void commitOnDev(String file, String content) throws Exception {
            runner.run(path, T, List.of("git", "fetch", "-q"));
            runner.run(path, T, List.of("git", "checkout", "-q", "-B", "_dev", "origin/dev"));
            Files.writeString(path.resolve(file), content);
            commitAll("dev change");
            runner.run(path, T, List.of("git", "push", "-q", "origin", "_dev:dev"));
            runner.run(path, T, List.of("git", "checkout", "-q", "main"));
        }

        String sha(String rev) {
            runner.run(path, T, List.of("git", "fetch", "-q"));
            return runner.run(path, T, List.of("git", "rev-parse", rev)).stdout().trim();
        }

        boolean existsOnDev(String file) {
            runner.run(path, T, List.of("git", "fetch", "-q"));
            return runner.run(path, T, List.of("git", "cat-file", "-e", "origin/dev:" + file)).exitCode() == 0;
        }
    }

    @Test
    void revertTakesTheDeployedChangeBackOutOfDevAndLeavesTheTaskBranchIntact(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()));
        String merge = git.mergeIntoAndPush(repo.path(), "ABC-1", "dev");
        String taskTip = repo.sha("ABC-1");

        String revert = git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge);

        assertThat(repo.existsOnDev("feature.txt")).isFalse();
        assertThat(repo.sha("origin/dev")).isEqualTo(revert);
        assertThat(repo.sha("ABC-1")).isEqualTo(taskTip);
        assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
    }

    @Test
    void refusesToRevertACommitThatIsNotOnTheDeployBranch(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()));
        String neverDeployed = repo.sha("ABC-1");

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", neverDeployed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not on dev");

        assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
    }

    /** Idempotence on a shared branch: a second revert would silently RE-APPLY the change. */
    @Test
    void refusesASecondRevertOfTheSameDeploy(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()));
        String merge = git.mergeIntoAndPush(repo.path(), "ABC-1", "dev");
        String firstRevert = git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge);

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was already reverted");

        assertThat(repo.sha("origin/dev")).isEqualTo(firstRevert);
    }

    /** Every jagt deploy is a merge (--no-ff); a plain commit means reverting would undo part of a task. */
    @Test
    void refusesToRevertACommitThatIsNotAMerge(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()));
        repo.commitOnDev("unrelated.txt", "someone else's commit");
        String plainCommit = repo.sha("origin/dev");

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", plainCommit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not a merge");

        assertThat(repo.sha("origin/dev")).isEqualTo(plainCommit);
    }

    @Test
    void abortsAndPushesNothingWhenTheRevertConflictsWithLaterWorkOnDev(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()));
        String merge = git.mergeIntoAndPush(repo.path(), "ABC-1", "dev");
        repo.commitOnDev("feature.txt", "someone edited the deployed feature");
        String devTip = repo.sha("origin/dev");

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts with work done there since the deploy");

        assertThat(repo.sha("origin/dev")).isEqualTo(devTip);
        assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
    }

    /**
     * `lsof` missing must not take `done` down with it. The reap is hygiene — it frees a language server's
     * memory — while REMOVING the worktree is the actual job, and a machine without lsof (a slim Linux image,
     * a locked-down host) used to fail the whole call: ProcessRunner throws when a binary cannot be started,
     * and the "best-effort, never thrown" promise in the reap's own javadoc was not kept.
     */
    @Test
    void removesTheWorktreeEvenWhenTheProcessReaperIsNotInstalled(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner() {
            @Override
            public Processes.Result run(Path workingDir, Duration timeout, List<String> command) {
                if (command.get(0).equals("lsof")) {
                    throw new IllegalStateException("Failed to start command: lsof (not installed)");
                }
                return super.run(workingDir, timeout, command);
            }
        };
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "commit", "-qm", "init"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));
        Path worktree = dir.resolve("wt");
        git.createWorktree(repo, worktree, "ABC-1", "origin/main", GitService.BranchStrategy.FRESH);

        git.removeWorktree(repo, worktree, "ABC-1");

        assertThat(worktree).doesNotExist();
    }

    /**
     * The failure a CI runner found: `git merge` exits non-zero for plenty of reasons that are NOT a conflict
     * (no committer identity there, a refusing hook, a broken object), and calling all of them a conflict sent
     * the human to resolve conflicts that did not exist — while LEAVING the deploy worktree behind, so the next
     * `deploy` took the "the human resolved it" path and pushed whatever was in there. The scaffolding must
     * therefore be gone too.
     */
    @Test
    void reportsAFailedMergeAsAnErrorAndNotAsAConflictWhenNothingIsUnmerged(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner() {
            @Override
            public Processes.Result run(Path workingDir, Duration timeout, List<String> command) {
                if (command.size() > 1 && command.get(1).equals("merge")) {
                    return new Processes.Result(128, "", "Author identity unknown");
                }
                return super.run(workingDir, timeout, command);
            }
        };
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "commit", "-qm", "base"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1"));
        Files.writeString(repo.resolve("g.txt"), "task");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "commit", "-qm", "task"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(GitService.MergeConflictException.class)
                .hasMessageContaining("no conflict is waiting for you")
                .hasMessageContaining("Author identity unknown");

        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }

    private static boolean onPath(String binary) {
        String path = System.getenv("PATH");
        return path != null && Arrays.stream(path.split(":"))
                .anyMatch(dir -> !dir.isBlank() && Files.isExecutable(Path.of(dir, binary)));
    }

    @Test
    void readsOneBranchNamePerLineIgnoringBlanks() {
        assertThat(GitService.branchNames("ABC-40\nABC-41\n\n  main  \n")).containsExactly(
                "ABC-40", "ABC-41", "main");
        assertThat(GitService.branchNames("")).isEmpty();
    }
}
