package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.port.Processes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class GitRepos {

  static final Duration TIMEOUT = Duration.ofSeconds(30);

  private GitRepos() {}

  static GitCommands commands(Processes runner) {
    return new GitCommands(
        runner, new LsofWorktreeProcesses(runner), path -> Optional.empty(), stopped -> {});
  }

  static Processes.Result git(Processes runner, Path dir, String... args) {
    List<String> command =
        new ArrayList<>(List.of("git", "-c", "user.email=t@t", "-c", "user.name=t"));
    command.addAll(List.of(args));
    return runner.run(dir, TIMEOUT, command);
  }

  static Path originOf(Path dir, String name) {
    return dir.resolve(name + "-origin.git");
  }

  static Path clone(Processes runner, Path dir, String name, String content) throws IOException {
    Path origin = originOf(dir, name);
    Path repo = dir.resolve(name);
    git(runner, dir, "init", "-q", "--bare", "-b", "main", origin.toString());
    git(runner, dir, "clone", "-q", origin.toString(), repo.toString());
    Files.writeString(repo.resolve("f.txt"), content);
    commit(runner, repo, "init");
    git(runner, repo, "push", "-q", "origin", "main");
    return repo;
  }

  static Path siblingClone(Processes runner, Path dir, Path origin, String name) {
    Path sibling = dir.resolve(name);
    git(runner, dir, "clone", "-q", origin.toString(), sibling.toString());
    return sibling;
  }

  static Path emptyRepo(Processes runner, Path dir) {
    Path repo = dir.resolve("repo");
    git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    git(runner, repo, "commit", "-q", "--allow-empty", "-m", "init");
    return repo;
  }

  static void commit(Processes runner, Path repo, String message) {
    git(runner, repo, "add", ".");
    git(runner, repo, "commit", "-qm", message);
  }

  static void addWorktree(Processes runner, Path repo, Path worktree, String branch) {
    git(runner, repo, "worktree", "add", "-q", "-b", branch, worktree.toString());
  }

  static void executable(Path file, String content) throws IOException {
    Files.writeString(file, content);
    Files.setPosixFilePermissions(
        file,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
  }

  static Process shell(Path dir, String command) throws IOException {
    return new ProcessBuilder("/bin/sh", "-c", command)
        .directory(dir.toFile())
        .redirectErrorStream(true)
        .start();
  }
}
