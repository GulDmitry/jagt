package dev.jawo.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitServiceTest {

    @Test
    void refusesWorktreeCreationWhenTicketBranchSurvivedPreviousRun(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        GitService git = new GitService(runner);

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                GitService.BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recreate")
                .hasMessageContaining("resume");
    }

    @Test
    void keepsExistingCommitsWhenReopenedTicketResumesItsBranch(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        GitService git = new GitService(runner);

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("task work");
    }

    @Test
    void startsBranchFreshFromBaseWhenReopenedTicketRecreatesIt(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        GitService git = new GitService(runner);

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", GitService.BranchStrategy.RECREATE);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("base");
    }

    @Test
    void namesTheRealTargetBranchInTheDeployMergeCommit(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "PAN-1", "main"));
        Files.writeString(repo.resolve("g.txt"), "task");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "task"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitService git = new GitService(runner);

        git.mergeIntoAndPush(repo, "PAN-1", "dev");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        String subject = runner.run(repo, timeout,
                List.of("git", "log", "-1", "--format=%s", "origin/dev")).stdout().trim();
        assertThat(subject).isEqualTo("Merge branch 'PAN-1' into dev");
    }

    @Test
    void leavesTheTaskBranchWithoutTheBaseBranchAsUpstream(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "release", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "release"));
        GitService git = new GitService(runner);

        git.createWorktree(repo, dir.resolve("wt"), "PAN-1", "origin/release", GitService.BranchStrategy.FRESH);

        var upstream = runner.run(dir.resolve("wt"), timeout,
                List.of("git", "rev-parse", "--abbrev-ref", "PAN-1@{upstream}"));
        assertThat(upstream.exitCode()).isNotZero();
    }

    @Test
    void abortsDeployCleanlyWhenMergeConflicts(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        runner.run(repo, timeout, List.of("git", "checkout", "-qb", "ABC-1", "main"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "task"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "main"));
        GitService git = new GitService(runner);

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFLICT")
                .hasMessageContaining("nothing was pushed");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        String devAfterConflict = runner.run(repo, timeout, List.of("git", "rev-parse", "origin/dev~0")).stdout().trim();
        String devTipWithOnlyDevCommit = runner.run(repo, timeout, List.of("git", "rev-parse", "dev")).stdout().trim();
        assertThat(devAfterConflict).isEqualTo(devTipWithOnlyDevCommit).isNotEqualTo(devBefore);
        assertThat(runner.run(repo, timeout, List.of("git", "branch", "--list", "jawo-deploy*")).stdout()).isBlank();
    }

    @Test
    void excludesGitIgnoredPlumbingFromTheIdeDiffSnapshot(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path origin = dir.resolve("origin.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, timeout, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, timeout, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "init"));
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main"));
        GitService git = new GitService(runner);
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
    void refusesDeployWhenBranchHasNoCommitsBeyondTheTarget(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        GitService git = new GitService(runner);

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nothing to deploy");
    }

    @Test
    void deletesTheDirectoryEvenWhenGitWorktreeRemoveFails(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        GitService git = new GitService(runner);

        git.removeWorktree(repo, leftover, null);

        assertThat(leftover).doesNotExist();
    }

    @Test
    void publishesTaskCommitsWhenDeployMergesCleanly(@TempDir Path dir) throws Exception {
        ProcessRunner runner = new ProcessRunner();
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
        GitService git = new GitService(runner);

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        String originDevTip = runner.run(repo, timeout, List.of("git", "rev-parse", "origin/dev")).stdout().trim();
        assertThat(originDevTip).isEqualTo(taskTip);
    }
}
