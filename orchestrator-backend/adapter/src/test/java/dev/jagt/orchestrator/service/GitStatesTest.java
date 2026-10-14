package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.task.GitTrust;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitStatesTest {

    @Test
    void namesAProgramASessionSetInTheSharedConfigFromItsWorktree(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Map<String, String> trusted = git.gitState(List.of(worktree));
        runner.run(worktree, timeout, List.of("git", "config", "core.fsmonitor", "/tmp/watch"));

        Map<String, String> now = git.gitState(List.of(worktree));

        assertThat(GitTrust.of(trusted).differences(now))
                .containsExactly("core.fsmonitor in " + repo.toRealPath().resolve(".git/config") + " added");
    }

    @Test
    void passesTheTrackingGitWritesAsABranchIsPushed(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Map<String, String> trusted = git.gitState(List.of(worktree));
        runner.run(worktree, timeout, List.of("git", "config", "branch.ABC-42.remote", "origin"));
        runner.run(worktree, timeout, List.of("git", "config", "branch.ABC-42.merge", "refs/heads/ABC-42"));

        Map<String, String> now = git.gitState(List.of(worktree));

        assertThat(GitTrust.of(trusted).differences(now)).isEmpty();
    }

    @Test
    void namesAConfigSetForTheWorktreeAlone(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "config", "extensions.worktreeConfig", "true"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Map<String, String> trusted = git.gitState(List.of(worktree));
        runner.run(worktree, timeout, List.of("git", "config", "--worktree", "gpg.program", "/tmp/sign"));

        Map<String, String> now = git.gitState(List.of(worktree));

        assertThat(GitTrust.of(trusted).differences(now)).containsExactly("gpg.program in "
                + repo.toRealPath().resolve(".git/worktrees/wt/config.worktree") + " added");
    }

    @Test
    void namesAHookMadeRunnable(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        Path hook = repo.resolve(".git/hooks/post-merge");
        Files.writeString(hook, "#!/bin/sh\n");
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Map<String, String> trusted = git.gitState(List.of(worktree));
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

        Map<String, String> now = git.gitState(List.of(worktree));

        assertThat(GitTrust.of(trusted).differences(now))
                .containsExactly(repo.toRealPath().resolve(".git/hooks/post-merge") + " changed");
    }
}
