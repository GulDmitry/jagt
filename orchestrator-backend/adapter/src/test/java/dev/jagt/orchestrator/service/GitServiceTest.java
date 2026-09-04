package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.agent.StubAgentProperties;
import dev.jagt.orchestrator.adapter.agent.StubAgentRuntime;
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        assertThatThrownBy(() -> git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.FRESH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recreate")
                .hasMessageContaining("resume");
    }

    @Test
    void readsTheWorkAWorktreeHoldsUncommittedAndIgnoresJagtsOwnFiles(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Files.createDirectories(repo);
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        Files.writeString(repo.resolve("a file.java"), "class A {}");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-qm", "init"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        Files.writeString(repo.resolve("task_context.md"), "the round brief");
        assertThat(git.hasUncommittedChanges(repo, repo)).isFalse();

        Files.writeString(repo.resolve("a file.java"), "class A { int x; }");
        assertThat(git.hasUncommittedChanges(repo, repo)).isTrue();
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "main", BranchStrategy.FRESH);

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

        new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults())).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim()).isEqualTo(taskTip);
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout()).contains("task");
        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }

    @Test
    void deploysThroughADeployPathAnEditorRecreatedAfterTheWorktreeWasRemoved(@TempDir Path dir) throws Exception {
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
        Files.createDirectories(dir.resolve("ABC-1-deploy").resolve(".idea"));
        Files.writeString(dir.resolve("ABC-1-deploy").resolve(".idea").resolve("misc.xml"), "<project/>");

        new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults())).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task");
        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }

    @Test
    void refusesToDeployThroughADeployPathHoldingSomethingJagtDidNotPutThere(@TempDir Path dir) throws Exception {
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
        Files.createDirectories(dir.resolve("ABC-1-deploy"));
        Files.writeString(dir.resolve("ABC-1-deploy").resolve("notes.txt"), "mine");
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.StaleDeployPathException.class)
                .hasMessageContaining("ABC-1-deploy")
                .hasMessageContaining("notes.txt");
        assertThat(dir.resolve("ABC-1-deploy").resolve("notes.txt")).hasContent("mine");
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
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

    @Test
    void mergesAgainstTheDeployBranchAsItIsNowWhenTheConflictWasNeverResolved(@TempDir Path dir) throws Exception {
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        runner.run(repo, t, List.of("git", "checkout", "-q", "_dev"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "release"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "_dev:dev"));

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "log", "--oneline", "origin/dev")).stdout())
                .contains("Merge branch 'ABC-1' into dev");
        assertThat(deployWorktree).doesNotExist();
    }

    @Test
    void keepsTheConflictWaitingWhenPartOfItIsAlreadyResolved(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        Path origin = dir.resolve("o.git");
        Path repo = dir.resolve("repo");
        runner.run(dir, t, List.of("git", "init", "-q", "--bare", "-b", "main", origin.toString()));
        runner.run(dir, t, List.of("git", "clone", "-q", origin.toString(), repo.toString()));
        Files.writeString(repo.resolve("f.txt"), "base");
        Files.writeString(repo.resolve("g.txt"), "base");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(repo, t, List.of("git", "fetch", "-q"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "_dev", "origin/dev"));
        Files.writeString(repo.resolve("f.txt"), "dev change");
        Files.writeString(repo.resolve("g.txt"), "dev change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "dev"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "_dev:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1", "main"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        Files.writeString(repo.resolve("g.txt"), "task change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "task"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved by hand");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class)
                .hasMessageContaining("still unresolved");

        assertThat(deployWorktree.resolve("f.txt")).hasContent("resolved by hand");
    }

    @Test
    void refusesToPushTheOldTargetsLineWhenTheDeployBranchChangedUnderALeftoverWorktree(@TempDir Path dir)
            throws Exception {
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
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:staging"));
        runner.run(repo, t, List.of("git", "fetch", "-q"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "_dev", "origin/dev"));
        Files.writeString(repo.resolve("f.txt"), "dev change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "dev only"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "_dev:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1", "main"));
        Files.writeString(repo.resolve("f.txt"), "task change");
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "task"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "task change");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));
        runner.run(deployWorktree, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-q", "--no-edit"));

        git.mergeIntoAndPush(repo, "ABC-1", "staging");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "log", "--oneline", "origin/staging")).stdout())
                .doesNotContain("dev only");
    }

    @Test
    void stopsSendingTheHumanBackToTheDeployWorktreeWhenTheDeployBranchAlreadyHoldsWhatItHeld(@TempDir Path dir)
            throws Exception {
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));
        runner.run(deployWorktree, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t",
                "commit", "-q", "--no-edit"));
        runner.run(deployWorktree, t, List.of("git", "push", "-q", "origin", "HEAD:dev"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "_dev"));
        runner.run(repo, t, List.of("git", "fetch", "-q"));
        runner.run(repo, t, List.of("git", "reset", "-q", "--hard", "origin/dev"));
        Files.writeString(repo.resolve("later.txt"), "release");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "later"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "_dev:dev"));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitService.NothingToDeployException.class)
                .hasMessageContaining("no commits beyond dev");

        assertThat(deployWorktree).doesNotExist();
    }

    @Test
    void deploysAgainAfterTheDeployWorktreeDirectoryWasDeletedByHand(@TempDir Path dir) throws Exception {
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
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1"));
        Files.writeString(repo.resolve("g.txt"), "task");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "task"));
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        runner.run(repo, t, List.of("git", "worktree", "add", "-q", "-B", "jagt-deploy-ABC-1",
                deployWorktree.toString(), "origin/dev"));
        runner.run(dir, t, List.of("rm", "-rf", deployWorktree.toString()));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task");
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt").resolve("f.txt")).hasContent("task work");
    }

    @Test
    void freesTheBaseRepositoryWhenItStillHoldsTheBranchThisTaskNeeds(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(runner.run(repo, Duration.ofSeconds(30),
                List.of("git", "log", "-1", "--format=%s", "ABC-1")).stdout()).contains("init");
    }

    @Test
    void leavesTheCheckoutAloneWhenItRefusesAnExistingBranch(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(dir.resolve("wt")).isDirectory();
        assertThat(repo.resolve("scratch.txt")).exists();
    }

    @Test
    void resumesTheBranchWhenTheRequestTargetsABaseThatNoLongerExists(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        // A FILE where the worktree's parent directory would go: git cannot create anything under it, and
        // unlike a read-only directory that holds for root too — the container harness runs as one.
        Path notADirectory = Files.writeString(dir.resolve("in-the-way"), "");

        assertThatThrownBy(() -> git.createWorktree(repo, notADirectory.resolve("wt"), "ABC-1", "origin/main",
                BranchStrategy.RESUME))
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RESUME);

        assertThat(repo.resolve("f.txt")).content().isEqualTo("the branch's own content");
    }

    @Test
    void leavesARepositoryOnItsOwnBranchAloneWhenAskedToReattach(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        runner.run(repo, Duration.ofSeconds(30), List.of("git", "checkout", "-q", "main"));
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.reattach(repo, "ABC-1");

        assertThat(runner.run(repo, Duration.ofSeconds(30), List.of("git", "branch", "--show-current"))
                .stdout().strip()).isEqualTo("main");
    }

    @Test
    void putsTheCheckoutBackWhenRecreatingTheBranchLeavesTheWorktreeUncut(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Path repo = repositoryOnItsOwnBranch(runner, dir);
        Path notADirectory = Files.writeString(dir.resolve("in-the-way"), "");
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/main", BranchStrategy.RECREATE);

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.createWorktree(repo, dir.resolve("wt"), "ABC-1", "origin/release", BranchStrategy.FRESH);

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);
        Files.writeString(wt.resolve("f.txt"), "task change");
        Files.writeString(wt.resolve("new.js"), "new source");
        Files.writeString(wt.resolve("mcp_client.js"), "plumbing");
        Files.writeString(repo.resolve(".git").resolve("info").resolve("exclude"), "mcp_client.js\n");

        Path clean = git.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-1");

        boolean plumbing = Files.exists(clean.resolve("mcp_client.js"));
        String changed = Files.readString(clean.resolve("f.txt"));
        String added = Files.readString(clean.resolve("new.js"));
        git.removeDiffWorktrees(repo, "ABC-1");

        assertThat(plumbing).isFalse();
        assertThat(changed).isEqualTo("task change");
        assertThat(added).isEqualTo("new source");
    }

    @Test
    void prunesTheAdminEntryOfADiffCheckoutSomethingElseAlreadyDeleted(@TempDir Path dir) throws Exception {
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        Path base = git.checkoutBaseForDiff(repo, "origin/main", "ABC-8");
        runner.run(dir, timeout, List.of("rm", "-rf", base.toString()));

        git.removeDiffWorktrees(repo, "ABC-8");

        assertThat(runner.run(repo, timeout, List.of("git", "worktree", "list")).stdout())
                .doesNotContain(base.getFileName().toString());
    }

    @Test
    void deletesBothThrowawayDiffCheckoutsWhenTheTaskIsRetired(@TempDir Path dir) throws Exception {
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        Path wt = dir.resolve("wt");
        git.createWorktree(repo, wt, "ABC-9", "origin/main", BranchStrategy.FRESH);
        Path base = git.checkoutBaseForDiff(repo, "origin/main", "ABC-9");
        Path clean = git.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-9");

        git.removeDiffWorktrees(repo, "ABC-9");

        assertThat(base).doesNotExist();
        assertThat(clean).doesNotExist();
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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.removeWorktree(repo, leftover, null);

        assertThat(leftover).doesNotExist();
    }

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, timeout, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task feature");
        String parents = runner.run(repo, timeout,
                List.of("git", "rev-list", "--parents", "-n", "1", "origin/dev")).stdout().trim();
        assertThat(parents.split("\\s+")).hasSize(3).contains(taskTip);
    }

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
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()),
                new StubAgentRuntime(StubAgentProperties.defaults()));
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
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        String neverDeployed = repo.sha("ABC-1");

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", neverDeployed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not on dev");

        assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
    }

    @Test
    void refusesASecondRevertOfTheSameDeploy(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        String merge = git.mergeIntoAndPush(repo.path(), "ABC-1", "dev");
        String firstRevert = git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge);

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was already reverted");

        assertThat(repo.sha("origin/dev")).isEqualTo(firstRevert);
    }

    @Test
    void refusesToRevertACommitThatIsNotAMerge(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()),
                new StubAgentRuntime(StubAgentProperties.defaults()));
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
        GitService git = new GitService(repo.runner(), new LsofWorktreeProcesses(repo.runner()),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        String merge = git.mergeIntoAndPush(repo.path(), "ABC-1", "dev");
        repo.commitOnDev("feature.txt", "someone edited the deployed feature");
        String devTip = repo.sha("origin/dev");

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", merge))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts with work done there since the deploy");

        assertThat(repo.sha("origin/dev")).isEqualTo(devTip);
        assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
    }

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));
        Path worktree = dir.resolve("wt");
        git.createWorktree(repo, worktree, "ABC-1", "origin/main", BranchStrategy.FRESH);

        git.removeWorktree(repo, worktree, "ABC-1");

        assertThat(worktree).doesNotExist();
    }

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
        GitService git = new GitService(runner, new LsofWorktreeProcesses(runner),
                new StubAgentRuntime(StubAgentProperties.defaults()));

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
