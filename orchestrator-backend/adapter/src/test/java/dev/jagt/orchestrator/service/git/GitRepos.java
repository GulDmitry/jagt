package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.adapter.worktree.LsofWorktreeProcesses;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.repo.BranchStrategy;
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

  static Path bareClone(Processes runner, Path dir, String name) {
    Path origin = originOf(dir, name);
    Path repo = dir.resolve(name);
    git(runner, dir, "init", "-q", "--bare", "-b", "main", origin.toString());
    git(runner, dir, "clone", "-q", origin.toString(), repo.toString());
    return repo;
  }

  static Path clone(Processes runner, Path dir, String name, String content) throws IOException {
    Path repo = bareClone(runner, dir, name);
    Files.writeString(repo.resolve("f.txt"), content);
    commit(runner, repo, "init");
    git(runner, repo, "push", "-q", "origin", "main");
    return repo;
  }

  static Path cloneWithDev(Processes runner, Path dir) throws IOException {
    Path repo = clone(runner, dir, "repo", "base");
    git(runner, repo, "push", "-q", "origin", "main:dev");
    return repo;
  }

  static Path cloneWithTask(Processes runner, Path dir) throws IOException {
    Path repo = cloneWithDev(runner, dir);
    git(runner, repo, "checkout", "-q", "-b", "ABC-1");
    Files.writeString(repo.resolve("g.txt"), "task");
    commit(runner, repo, "task");
    return repo;
  }

  static Path cloneWithConflictingTask(Processes runner, Path dir) throws IOException {
    Path repo = cloneWithDev(runner, dir);
    git(runner, repo, "fetch", "-q");
    git(runner, repo, "checkout", "-q", "-b", "_dev", "origin/dev");
    Files.writeString(repo.resolve("f.txt"), "dev change");
    git(runner, repo, "commit", "-qam", "dev only");
    git(runner, repo, "push", "-q", "origin", "_dev:dev");
    git(runner, repo, "checkout", "-q", "-b", "ABC-1", "main");
    Files.writeString(repo.resolve("f.txt"), "task change");
    git(runner, repo, "commit", "-qam", "task");
    git(runner, repo, "push", "-q", "origin", "ABC-1");
    return repo;
  }

  static Path cloneWithDeployedFeatureBranch(Processes runner, Path dir) throws IOException {
    Path repo = cloneWithFeatureBranch(runner, dir);
    git(runner, repo, "checkout", "-q", "main");
    return repo;
  }

  static Path cloneWithFeatureBranch(Processes runner, Path dir) throws IOException {
    Path repo = bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("base.txt"), "base");
    commit(runner, repo, "base");
    git(runner, repo, "push", "-q", "origin", "main");
    git(runner, repo, "push", "-q", "origin", "main:dev");
    git(runner, repo, "checkout", "-q", "-b", "ABC-1");
    Files.writeString(repo.resolve("feature.txt"), "the feature");
    commit(runner, repo, "feature");
    git(runner, repo, "push", "-q", "origin", "ABC-1");
    return repo;
  }

  static void pushDevChange(Processes runner, Path repo, String file, String content)
      throws IOException {
    git(runner, repo, "fetch", "-q");
    git(runner, repo, "checkout", "-q", "-B", "_dev", "origin/dev");
    Files.writeString(repo.resolve(file), content);
    commit(runner, repo, "dev change");
    git(runner, repo, "push", "-q", "origin", "_dev:dev");
    git(runner, repo, "checkout", "-q", "main");
  }

  static Path cloneWithPushedTask(Processes runner, Path dir) throws IOException {
    Path repo = clone(runner, dir, "repo", "base");
    git(runner, repo, "checkout", "-qb", "ABC-1");
    Files.writeString(repo.resolve("task.txt"), "the task's work");
    commit(runner, repo, "work");
    git(runner, repo, "push", "-q", "origin", "ABC-1");
    git(runner, repo, "checkout", "-q", "main");
    return repo;
  }

  static void refusePushes(Processes runner, Path repo, Path dir) {
    git(runner, repo, "remote", "set-url", "--push", "origin", dir.resolve("gone.git").toString());
  }

  static void shipRelease(Processes runner, Path repo) throws IOException {
    Files.writeString(repo.resolve("release.txt"), "shipped while the request waited");
    commit(runner, repo, "release");
    git(runner, repo, "push", "-q", "origin", "main");
  }

  static void stage(Processes runner, Path worktree, String file, String content)
      throws IOException {
    Files.writeString(worktree.resolve(file), content);
    git(runner, worktree, "add", file);
  }

  static Path siblingClone(Processes runner, Path dir, Path origin, String name) {
    Path sibling = dir.resolve(name);
    git(runner, dir, "clone", "-q", origin.toString(), sibling.toString());
    return sibling;
  }

  static Path localRepoWithTask(Processes runner, Path dir) throws IOException {
    Path repo = emptyRepo(runner, dir);
    Files.writeString(repo.resolve("f.txt"), "base");
    commit(runner, repo, "base");
    git(runner, repo, "branch", "dev");
    git(runner, repo, "checkout", "-q", "-b", "ABC-1");
    Files.writeString(repo.resolve("g.txt"), "task");
    commit(runner, repo, "task");
    return repo;
  }

  static Path emptyRepo(Processes runner, Path dir) {
    return emptyRepo(runner, dir, "repo");
  }

  static Path emptyRepo(Processes runner, Path dir, String name) {
    Path repo = dir.resolve(name);
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

  static void freshWorktree(Processes runner, Path repo, Path worktree) {
    new GitWorktrees(commands(runner))
        .createWorktree(repo, worktree, "ABC-1", "origin/main", BranchStrategy.FRESH);
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
