package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.repo.GitTrust;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GitStatesTest {

  @TempDir Path dir;

  private final Processes runner = new ProcessRunner();
  private Path repo;
  private Path worktree;
  private GitWorktrees git;

  @BeforeEach
  void startFromRepositoryWithEmptyCommit() {
    repo = GitRepos.emptyRepo(runner, dir);
    worktree = dir.resolve("wt");
    git = new GitWorktrees(GitRepos.commands(runner));
  }

  @Test
  void namesProgramSessionSetInTheSharedConfigFromItsWorktree() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Map<String, String> trusted = git.gitState(List.of(worktree));
    runner.run(
        worktree, GitRepos.TIMEOUT, List.of("git", "config", "core.fsmonitor", "/tmp/watch"));

    Map<String, String> now = git.gitState(List.of(worktree));

    assertThat(GitTrust.of(trusted).differences(now))
        .containsExactly(
            "core.fsmonitor in " + repo.toRealPath().resolve(".git/config") + " added");
  }

  @ParameterizedTest
  @CsvSource({
    "core.fsmonitor,/tmp/watch,/tmp/watch",
    "remote.origin.url,https://user:pw@host/r.git,https://***@host/r.git",
    "credential.helper,store,(hidden)"
  })
  void readsConfigValueAsTextWithSecretsHidden(String key, String value, String shown)
      throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    runner.run(worktree, GitRepos.TIMEOUT, List.of("git", "config", key, value));

    Map<String, String> contents = git.gitContents(List.of(worktree));

    assertThat(contents)
        .containsEntry(key + " in " + repo.toRealPath().resolve(".git/config"), shown);
  }

  @Test
  void readsHooksTextWhereItHasOne() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Files.writeString(repo.resolve(".git/hooks/pre-push"), "#!/bin/sh\ncurl evil\n");

    Map<String, String> contents = git.gitContents(List.of(worktree));

    assertThat(contents)
        .containsEntry(
            repo.toRealPath().resolve(".git/hooks/pre-push").toString(), "#!/bin/sh\ncurl evil\n");
  }

  @Test
  void passesTheTrackingGitWritesAsBranchIsPushed() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Map<String, String> trusted = git.gitState(List.of(worktree));
    runner.run(
        worktree, GitRepos.TIMEOUT, List.of("git", "config", "branch.ABC-42.remote", "origin"));
    runner.run(
        worktree,
        GitRepos.TIMEOUT,
        List.of("git", "config", "branch.ABC-42.merge", "refs/heads/ABC-42"));

    Map<String, String> now = git.gitState(List.of(worktree));

    assertThat(GitTrust.of(trusted).differences(now)).isEmpty();
  }

  @Test
  void namesConfigSetForTheWorktreeAlone() throws Exception {
    runner.run(
        repo, GitRepos.TIMEOUT, List.of("git", "config", "extensions.worktreeConfig", "true"));
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Map<String, String> trusted = git.gitState(List.of(worktree));
    runner.run(
        worktree,
        GitRepos.TIMEOUT,
        List.of("git", "config", "--worktree", "gpg.program", "/tmp/sign"));

    Map<String, String> now = git.gitState(List.of(worktree));

    assertThat(GitTrust.of(trusted).differences(now))
        .containsExactly(
            "gpg.program in "
                + repo.toRealPath().resolve(".git/worktrees/wt/config.worktree")
                + " added");
  }

  @Test
  void namesHookMadeRunnable() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Path hook = repo.resolve(".git/hooks/post-merge");
    Files.writeString(hook, "#!/bin/sh\n");
    Map<String, String> trusted = git.gitState(List.of(worktree));
    Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

    Map<String, String> now = git.gitState(List.of(worktree));

    assertThat(GitTrust.of(trusted).differences(now))
        .containsExactly(repo.toRealPath().resolve(".git/hooks/post-merge") + " changed");
  }

  @Test
  void namesProgramWhoseValueChangedInTheSharedConfig() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    runner.run(
        worktree, GitRepos.TIMEOUT, List.of("git", "config", "core.fsmonitor", "/tmp/watch"));
    Map<String, String> trusted = git.gitState(List.of(worktree));
    runner.run(
        worktree, GitRepos.TIMEOUT, List.of("git", "config", "core.fsmonitor", "/tmp/other"));

    Map<String, String> now = git.gitState(List.of(worktree));

    assertThat(GitTrust.of(trusted).differences(now))
        .containsExactly(
            "core.fsmonitor in " + repo.toRealPath().resolve(".git/config") + " changed");
  }

  @Test
  void readsTheHooksInTheDirectoryRelativeHooksPathNamesFromTheCheckout() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    runner.run(
        worktree, GitRepos.TIMEOUT, List.of("git", "config", "core.hooksPath", "custom-hooks"));
    Files.createDirectories(repo.resolve("custom-hooks"));
    Files.writeString(repo.resolve("custom-hooks/pre-push"), "#!/bin/sh\n");

    Map<String, String> state = git.gitState(List.of(worktree));

    assertThat(state).containsKey(repo.toRealPath().resolve("custom-hooks/pre-push").toString());
  }

  @Test
  void marksAnExecutableHookAndDigestsItsContent() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Path hook = repo.resolve(".git/hooks/pre-push");
    Files.writeString(hook, "#!/bin/sh\n");
    Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));

    Map<String, String> state = git.gitState(List.of(worktree));

    assertThat(state.get(repo.toRealPath().resolve(".git/hooks/pre-push").toString()))
        .hasSize(66)
        .endsWith("+x");
  }

  @Test
  void skipsSampleHooksAndSubdirectoriesOfTheHooksDirectory() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Files.writeString(repo.resolve(".git/hooks/pre-commit.sample"), "#!/bin/sh\n");
    Files.createDirectories(repo.resolve(".git/hooks/vendored"));

    Map<String, String> state = git.gitState(List.of(worktree));

    assertThat(state.keySet())
        .noneMatch(key -> key.endsWith(".sample") || key.endsWith("vendored"));
  }

  @Test
  void keepsEveryEntryAsItsOwnWhenTheConfigsHoldExactlyTheMaximum() throws Exception {
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
    Files.writeString(
        repo.resolve(".git/config"),
        "[core]\n\trepositoryformatversion = 0\n[x]\n"
            + IntStream.range(0, 511)
                .mapToObj(i -> "\tk" + i + " = v\n")
                .collect(Collectors.joining()));

    Map<String, String> state = git.gitState(List.of(worktree));

    assertThat(state).hasSize(512);
  }
}
