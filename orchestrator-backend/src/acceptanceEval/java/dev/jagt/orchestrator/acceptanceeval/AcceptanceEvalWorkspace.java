package dev.jagt.orchestrator.acceptanceeval;

import dev.jagt.orchestrator.eval.AcceptanceCase;
import dev.jagt.orchestrator.eval.EvalShards;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class AcceptanceEvalWorkspace {

  private static final Path FIXED_ROOT_A_HUMAN_TRUSTED_ONCE =
      Path.of("..", ".acceptance-eval").toAbsolutePath().normalize();
  private static final EvalShards SHARDS =
      EvalShards.under(FIXED_ROOT_A_HUMAN_TRUSTED_ONCE, shards());

  private AcceptanceEvalWorkspace() {}

  static int shards() {
    return Integer.getInteger("acceptanceEval.shards", 1);
  }

  static void claim(Class<?> shardClass) {
    SHARDS.claim(shardClass);
  }

  static int shard() {
    return SHARDS.claimed();
  }

  static void release() throws IOException {
    SHARDS.release();
  }

  static Path root() {
    return FIXED_ROOT_A_HUMAN_TRUSTED_ONCE.resolve("root").resolve("shard-" + shard());
  }

  static void clean() throws IOException {
    if (!Files.exists(root())) {
      return;
    }
    try (var walk = Files.walk(root())) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  static Path worktreeFor(Path repo, AcceptanceCase deployed) throws Exception {
    Files.createDirectories(repo);
    git(repo, "init", "--initial-branch=main", ".");
    git(repo, "config", "user.email", "eval@example.com");
    git(repo, "config", "user.name", "jagt acceptance eval");
    Files.writeString(repo.resolve("README.md"), "baseline\n");
    git(repo, "add", "README.md");
    git(repo, "commit", "-m", "Baseline");
    for (String change : deployed.deploy().diff()) {
      String[] fileAndWhat = change.split(": ", 2);
      Path target = repo.resolve(fileAndWhat[0]);
      Files.createDirectories(target.getParent());
      Files.writeString(target, fileAndWhat[1] + "\n");
      git(repo, "add", fileAndWhat[0]);
    }
    git(repo, "commit", "-m", deployed.title());
    return repo;
  }

  static String head(Path repo) throws Exception {
    Process process =
        new ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(repo.toFile()).start();
    String sha = new String(process.getInputStream().readAllBytes()).strip();
    if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
      throw new IllegalStateException("git rev-parse HEAD failed in " + repo);
    }
    return sha;
  }

  static String ticket(AcceptanceCase deployed, String environmentUrl, String sha) {
    String criteria =
        deployed.criteria().stream()
            .map(
                criterion ->
                    "- "
                        + criterion.id()
                        + ": "
                        + criterion.text()
                        + (criterion.testable()
                            ? ""
                            : " (a non-functional wish, no threshold is named)"))
            .collect(Collectors.joining("\n"));
    String landed =
        switch (deployed.deploy().landed()) {
          case "host-job" -> "The host's deploy job for " + sha + " succeeded.";
          default -> "The host reports no deploy job for this change.";
        };
    String roles =
        ((java.util.Map<?, ?>) deployed.environment().getOrDefault("login", java.util.Map.of()))
            .keySet().stream()
                .map(
                    role -> "- " + role + ": sign in with GET " + environmentUrl + "/login/" + role)
                .collect(Collectors.joining("\n"));
    java.util.List<String> pages = new java.util.ArrayList<>();
    ((java.util.Map<?, ?>) deployed.environment().getOrDefault("ui", java.util.Map.of()))
        .keySet()
        .forEach(page -> pages.add("GET " + page));
    ((java.util.Map<?, ?>) deployed.environment().getOrDefault("http", java.util.Map.of()))
        .keySet()
        .forEach(call -> pages.add(String.valueOf(call)));
    String served =
        pages.stream()
            .map(
                page -> {
                  int space = page.indexOf(' ');
                  return "- "
                      + page.substring(0, space)
                      + " "
                      + environmentUrl
                      + page.substring(space + 1);
                })
            .collect(Collectors.joining("\n"));
    return "\n\nThe ticket "
        + deployed.key()
        + ", "
        + deployed.title()
        + ", has these acceptance criteria:\n"
        + criteria
        + "\n"
        + landed
        + "\nThe change is the last commit of the repository named above."
        + "\nRoles you may sign in as:\n"
        + roles
        + "\nPages the environment serves:\n"
        + served
        + "\nEach page answers with what it renders, as JSON; a sign-in GET needs no session.";
  }

  static void git(Path directory, String... arguments) throws Exception {
    String[] command = new String[arguments.length + 1];
    command[0] = "git";
    System.arraycopy(arguments, 0, command, 1, arguments.length);
    Process process =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
    if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
      throw new IllegalStateException(
          "git "
              + String.join(" ", arguments)
              + " failed in "
              + directory
              + ": "
              + new String(process.getInputStream().readAllBytes()));
    }
  }

  static void writeConfig(Path configFile, Path projectPath) throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            proj: { path: "%s", baseBranch: main }
          master: { mode: off }
        """
            .formatted(projectPath));
  }
}
