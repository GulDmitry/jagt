package dev.jagt.orchestrator.mastereval;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.jagt.orchestrator.adapter.agent.MasterEvalTranscripts;
import dev.jagt.orchestrator.eval.EvalGit;
import dev.jagt.orchestrator.eval.EvalShards;
import dev.jagt.orchestrator.eval.MasterCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class MasterEvalWorkspace {

  private static final String TMUX_PREFIX = "jagt-master-eval";
  static final String TMUX_SESSION = TMUX_PREFIX + "-" + ProcessHandle.current().pid();
  private static final Path FIXED_ROOT_A_HUMAN_TRUSTED_ONCE =
      Path.of("..", ".master-eval").toAbsolutePath().normalize();

  private static final EvalShards SHARDS =
      EvalShards.under(FIXED_ROOT_A_HUMAN_TRUSTED_ONCE, shards());

  private MasterEvalWorkspace() {}

  static int shards() {
    return Integer.getInteger("masterEval.shards", 1);
  }

  static int shard() {
    return SHARDS.claimed();
  }

  static Path root() {
    return FIXED_ROOT_A_HUMAN_TRUSTED_ONCE.resolve("root").resolve("shard-" + shard());
  }

  static void claim(Class<?> shardClass) throws Exception {
    SHARDS.claim(shardClass);
    reapSessionsOfDeadRuns();
  }

  private static void reapSessionsOfDeadRuns() throws Exception {
    Process listing =
        new ProcessBuilder("tmux", "list-sessions", "-F", "#{session_name}")
            .redirectErrorStream(true)
            .start();
    String listed = new String(listing.getInputStream().readAllBytes(), UTF_8);
    for (String session : listed.lines().filter(MasterEvalWorkspace::ofDeadRun).toList()) {
      new ProcessBuilder("tmux", "kill-session", "-t", "=" + session)
          .start()
          .waitFor(10, TimeUnit.SECONDS);
    }
  }

  private static boolean ofDeadRun(String session) {
    if (!session.startsWith(TMUX_PREFIX + "-")) {
      return false;
    }
    String pid = session.substring(TMUX_PREFIX.length() + 1).split("-", 2)[0];
    return pid.chars().allMatch(Character::isDigit) && !pid.isEmpty() && !alive(pid);
  }

  static synchronized void release() throws Exception {
    new ProcessBuilder("tmux", "kill-session", "-t", "=" + TMUX_SESSION)
        .start()
        .waitFor(10, TimeUnit.SECONDS);
    SHARDS.release();
  }

  private static boolean alive(String pid) {
    try {
      return ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false);
    } catch (NumberFormatException unreadable) {
      return false;
    }
  }

  static void clean() throws IOException {
    if (!Files.exists(root())) {
      return;
    }
    try (var walk = Files.walk(root())) {
      walk.sorted(java.util.Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException leftBehind) {
              throw new java.io.UncheckedIOException(leftBehind);
            }
          });
    }
  }

  static Path worktreeFor(Path repo, MasterCase round, String taskId) throws Exception {
    Files.createDirectories(repo);
    EvalGit.run(repo, "init", "--initial-branch=main", ".");
    EvalGit.run(repo, "config", "user.email", "eval@example.com");
    EvalGit.run(repo, "config", "user.name", "jagt master eval");
    write(repo, round.baseline());
    EvalGit.run(repo, "add", "-A");
    EvalGit.run(repo, "commit", "-m", "Baseline");
    Path origin = Objects.requireNonNull(repo.getParent()).resolve("origin.git");
    EvalGit.run(repo.getParent(), "init", "--bare", "--initial-branch=main", origin.toString());
    EvalGit.run(repo, "remote", "add", "origin", origin.toString());
    EvalGit.run(repo, "push", "origin", "main");
    EvalGit.run(repo, "fetch", "origin");
    Path worktree = repo.getParent().resolve(taskId);
    EvalGit.run(repo, "worktree", "add", "-b", taskId, worktree.toString(), "main");
    write(
        worktree,
        round.change().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, file -> file.getValue().replace("{task}", taskId))));
    Files.writeString(worktree.resolve("task_request.md"), round.instructions());
    if (!round.plan().isBlank()) {
      Files.writeString(worktree.resolve("plan.md"), round.plan());
    }
    Files.writeString(repo.resolve(".git/info/exclude"), "task_request.md\nplan.md\n");
    MasterEvalTranscripts.nothingTypedIn(worktree);
    return worktree;
  }

  static void writeConfig(Path configFile, Path projectPath, String brief) throws IOException {
    writeConfig(configFile, projectPath, brief, "judge");
  }

  static void writeConfig(Path configFile, Path projectPath, String brief, String mode)
      throws IOException {
    Files.createDirectories(configFile.getParent());
    Files.writeString(
        configFile,
        """
        orchestrator:
          projects:
            proj: { path: "%s", baseBranch: main }
          viewer: { tmuxSession: "%s" }
          master: { mode: %s, brief: "%s", model: "%s" }
        """
            .formatted(
                projectPath,
                TMUX_SESSION,
                mode,
                brief,
                System.getProperty("masterEval.model", "")));
  }

  private static void write(Path root, Map<String, String> files) throws IOException {
    for (Map.Entry<String, String> file : files.entrySet()) {
      Path target = root.resolve(file.getKey());
      Files.createDirectories(target.getParent());
      Files.writeString(target, file.getValue());
    }
  }
}
