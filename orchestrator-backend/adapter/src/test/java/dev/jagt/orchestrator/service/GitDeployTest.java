package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitDeployTest {

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

        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class)).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "rev-parse", "ABC-1")).stdout().trim()).isEqualTo(taskTip);
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout()).contains("task");
        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }

    @Test
    void deploysWhatWasPushedWhenTheLocalRefOfTheTaskBranchHasFallenBehindTheRemote(@TempDir Path dir)
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
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "main:ABC-1"));
        runner.run(repo, t, List.of("git", "branch", "ABC-1", "main"));
        runner.run(repo, t, List.of("git", "checkout", "-q", "-b", "ABC-1-metrics"));
        Files.writeString(repo.resolve("g.txt"), "the work the request shows");
        runner.run(repo, t, List.of("git", "add", "."));
        runner.run(repo, t, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "work"));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "HEAD:ABC-1"));

        new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class)).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("the work the request shows");
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
        EditorDriver editor = mock(EditorDriver.class);
        when(editor.residue()).thenReturn(Set.of(".idea"));

        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                editor).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task");
        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }

    @Test
    void deploysThroughAnEditorsDeployPathWhenEverythingSitsInsideAnotherRepository(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration t = Duration.ofSeconds(30);
        runner.run(dir, t, List.of("git", "init", "-q", dir.toString()));
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
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        Files.createDirectories(dir.resolve("ABC-1-deploy").resolve(".idea"));
        Files.writeString(dir.resolve("ABC-1-deploy").resolve(".idea").resolve("misc.xml"), "<project/>");
        EditorDriver editor = mock(EditorDriver.class);
        when(editor.residue()).thenReturn(Set.of(".idea"));

        new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                editor).mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task");
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.StaleDeployPathException.class)
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);

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
                GitDeploy.deployWorktreePath(web, "ABC-1").toString()));
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

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
                GitDeploy.deployWorktreePath(web, "ABC-1").toString()));
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
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
    void readsADeployConflictAsResolvedOnceEveryPathIsStaged(@TempDir Path dir) throws Exception {
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));

        boolean resolved = git.deployResolved(repo, "ABC-1", "dev");

        assertThat(resolved).isTrue();
    }

    @Test
    void keepsADeployConflictUnresolvedWhileAStagedPathIsEditedAgain(@TempDir Path dir) throws Exception {
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved, then reworked");

        boolean resolved = git.deployResolved(repo, "ABC-1", "dev");

        assertThat(resolved).isFalse();
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
        Path deployWorktree = dir.resolve("ABC-1-deploy");
        Files.writeString(deployWorktree.resolve("f.txt"), "resolved by hand");
        runner.run(deployWorktree, t, List.of("git", "add", "f.txt"));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class)
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));
        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class);
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
                .isInstanceOf(GitDeploy.NothingToDeployException.class)
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, t, List.of("git", "fetch", "-q"));
        assertThat(runner.run(repo, t, List.of("git", "cat-file", "-p", "origin/dev:g.txt")).stdout())
                .contains("task");
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        git.mergeIntoAndPush(repo, "ABC-1", "dev");

        runner.run(repo, timeout, List.of("git", "fetch", "-q"));
        String subject = runner.run(repo, timeout,
                List.of("git", "log", "-1", "--format=%s", "origin/dev")).stdout().trim();
        assertThat(subject).isEqualTo("Merge branch 'ABC-1' into dev");
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(GitDeploy.MergeConflictException.class)
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
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no commits beyond dev");
    }

    @Test
    void refusesDeployOfWorkNobodyCanReviewBecauseTheBranchWasNeverPushed(@TempDir Path dir) throws Exception {
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
        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "main:dev"));
        runner.run(repo, timeout, List.of("git", "checkout", "-q", "-b", "ABC-1"));
        Files.writeString(repo.resolve("g.txt"), "work that was never shipped");
        runner.run(repo, timeout, List.of("git", "add", "."));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "work"));
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was never pushed");
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        runner.run(repo, timeout, List.of("git", "push", "-q", "origin", "ABC-1"));
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
            runner.run(repo, T, List.of("git", "push", "-q", "origin", taskBranch));
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
        GitDeploy git = new GitDeploy(new GitCommands(repo.runner(), new LsofWorktreeProcesses(repo.runner())),
                mock(EditorDriver.class));
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
        GitDeploy git = new GitDeploy(new GitCommands(repo.runner(), new LsofWorktreeProcesses(repo.runner())),
                mock(EditorDriver.class));
        String neverDeployed = repo.sha("ABC-1");

        assertThatThrownBy(() -> git.revertMergeAndPush(repo.path(), "ABC-1", "dev", neverDeployed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not on dev");

        assertThat(dir.resolve("ABC-1-revert")).doesNotExist();
    }

    @Test
    void refusesASecondRevertOfTheSameDeploy(@TempDir Path dir) throws Exception {
        Repo repo = Repo.withTaskBranch(dir, "ABC-1");
        GitDeploy git = new GitDeploy(new GitCommands(repo.runner(), new LsofWorktreeProcesses(repo.runner())),
                mock(EditorDriver.class));
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
        GitDeploy git = new GitDeploy(new GitCommands(repo.runner(), new LsofWorktreeProcesses(repo.runner())),
                mock(EditorDriver.class));
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
        GitDeploy git = new GitDeploy(new GitCommands(repo.runner(), new LsofWorktreeProcesses(repo.runner())),
                mock(EditorDriver.class));
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
    void reportsAFailedMergeAsAnErrorAndNotAsAConflictWhenNothingIsUnmerged(@TempDir Path dir) throws Exception {
        Processes runner = ScriptedProcesses.answering(List.of("git", "merge"),
                new Processes.Result(128, "", "Author identity unknown"));
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
        GitDeploy git = new GitDeploy(new GitCommands(runner, new LsofWorktreeProcesses(runner)),
                mock(EditorDriver.class));

        runner.run(repo, t, List.of("git", "push", "-q", "origin", "ABC-1"));
        assertThatThrownBy(() -> git.mergeIntoAndPush(repo, "ABC-1", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(GitDeploy.MergeConflictException.class)
                .hasMessageContaining("no conflict is waiting for you")
                .hasMessageContaining("Author identity unknown");

        assertThat(dir.resolve("ABC-1-deploy")).doesNotExist();
    }
}
