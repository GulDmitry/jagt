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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @Test
    void namesAProgramWhoseValueChangedInTheSharedConfig(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        runner.run(worktree, timeout, List.of("git", "config", "core.fsmonitor", "/tmp/watch"));
        Map<String, String> trusted = git.gitState(List.of(worktree));
        runner.run(worktree, timeout, List.of("git", "config", "core.fsmonitor", "/tmp/other"));

        Map<String, String> now = git.gitState(List.of(worktree));

        assertThat(GitTrust.of(trusted).differences(now))
                .containsExactly("core.fsmonitor in " + repo.toRealPath().resolve(".git/config") + " changed");
    }

    @Test
    void readsTheHooksInTheDirectoryARelativeHooksPathNamesFromTheCheckout(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        runner.run(worktree, timeout, List.of("git", "config", "core.hooksPath", "custom-hooks"));
        Files.createDirectories(repo.resolve("custom-hooks"));
        Files.writeString(repo.resolve("custom-hooks/pre-push"), "#!/bin/sh\n");

        Map<String, String> state = git.gitState(List.of(worktree));

        assertThat(state).containsKey(repo.toRealPath().resolve("custom-hooks/pre-push").toString());
    }

    @Test
    void marksAnExecutableHookAndDigestsItsContent(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Path hook = repo.resolve(".git/hooks/pre-push");
        Files.writeString(hook, "#!/bin/sh\n");
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

        Map<String, String> state = git.gitState(List.of(worktree));

        assertThat(state.get(repo.toRealPath().resolve(".git/hooks/pre-push").toString()))
                .hasSize(66).endsWith("+x");
    }

    @Test
    void skipsSampleHooksAndSubdirectoriesOfTheHooksDirectory(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Files.writeString(repo.resolve(".git/hooks/pre-commit.sample"), "#!/bin/sh\n");
        Files.createDirectories(repo.resolve(".git/hooks/vendored"));

        Map<String, String> state = git.gitState(List.of(worktree));

        assertThat(state.keySet()).noneMatch(key -> key.endsWith(".sample") || key.endsWith("vendored"));
    }

    @Test
    void keepsEveryEntryAsItsOwnWhenTheConfigsHoldExactlyTheMaximum(@TempDir Path dir) throws Exception {
        Processes runner = new ProcessRunner();
        Duration timeout = Duration.ofSeconds(30);
        Path repo = dir.resolve("repo");
        Path worktree = dir.resolve("wt");
        runner.run(dir, timeout, List.of("git", "init", "-q", "-b", "main", repo.toString()));
        runner.run(repo, timeout, List.of("git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q",
                "--allow-empty", "-m", "init"));
        runner.run(repo, timeout, List.of("git", "worktree", "add", "-q", "-b", "ABC-42", worktree.toString()));
        GitWorktrees git = new GitWorktrees(new GitCommands(runner, new LsofWorktreeProcesses(runner)));
        Files.writeString(repo.resolve(".git/config"), "[core]\n\trepositoryformatversion = 0\n[x]\n"
                + IntStream.range(0, 511).mapToObj(i -> "\tk" + i + " = v\n").collect(Collectors.joining()));

        Map<String, String> state = git.gitState(List.of(worktree));

        assertThat(state).hasSize(512);
    }
}
