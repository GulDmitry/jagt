package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.config.home.InstalledJar;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Layout;
import dev.jagt.orchestrator.task.launch.TaskName;
import dev.jagt.orchestrator.task.repo.NewRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Resolves the orchestrator root: the install home of a jar run from {@code versions/<v>/}, else by
 * walking up from the launch directory to a committed marker. TWO are accepted, so the root does
 * not depend on whether the stdio bridge file is still there.
 */
@Component
public class OrchestratorPaths {

  private static final java.util.List<String> ROOT_MARKERS =
      java.util.List.of("jagt.desktop.yml.dist", "mcp_client.js");

  private final Path root;
  private final Path configFile;
  private final Layout layout;

  public OrchestratorPaths(OrchestratorProperties properties, Host host) {
    this.root =
        properties.root() != null && !properties.root().isBlank()
            ? Path.of(properties.root()).toAbsolutePath().normalize()
            : findRoot(host);
    this.configFile = resolve(properties.configFile(), "jagt.yml");
    Path data =
        properties.data() != null && !properties.data().isBlank()
            ? Path.of(properties.data()).toAbsolutePath().normalize()
            : root.resolve("data");
    this.layout =
        new Layout(
            data,
            properties.stateFile() != null && !properties.stateFile().isBlank()
                ? Path.of(properties.stateFile()).toAbsolutePath().normalize()
                : null);
  }

  /** Everything jagt writes; {@code data} under the root where none is configured. */
  public Path data() {
    return layout.data();
  }

  /** The folders jagt creates at start. */
  public List<Path> folders() {
    return layout.folders();
  }

  public Path tasks() {
    return layout.tasks();
  }

  /** Where a task's checkout of one project is cut. */
  public Path worktree(String projectKey, String taskId) {
    return layout.worktree(TaskName.slug(taskId), projectKey);
  }

  public Path legacyWorktrees(String projectKey) {
    return layout.legacyWorktrees(projectKey);
  }

  /** Where a project named by url is cloned. */
  public Path clone(String projectKey) {
    return layout.clone(projectKey);
  }

  /** Where a task's deploy worktree stands in one project. */
  public Path deployWorktree(String projectKey, String taskId) {
    return layout.deployWorktree(TaskName.slug(taskId), projectKey);
  }

  /** Every deploy worktree a task of these repositories can stand in, each once. */
  public List<Path> deployWorktrees(List<NewRepo> repos, String taskId) {
    return repos.stream()
        .filter(
            repo -> repo.config().deployBranch() != null && !repo.config().deployBranch().isBlank())
        .map(repo -> deployWorktree(repo.project(), taskId))
        .distinct()
        .toList();
  }

  /** Separate from the deploy worktree, which may be sitting in a conflict. */
  public Path revertWorktree(String projectKey, String taskId) {
    return layout.revertWorktree(TaskName.slug(taskId), projectKey);
  }

  /** Where a project's checkout of a role's branch is cut for one commit. */
  public Path roleBranchWorktree(String projectKey, String branch) {
    return layout.roleBranchWorktree(projectKey, branch);
  }

  /** What an older version kept flat in an older folder, by name, with where it belongs now. */
  public Map<String, Path> placed() {
    return layout.placed();
  }

  public Map<String, Function<String, Path>> placedEntries() {
    return layout.placedEntries();
  }

  public @Nullable Path placedVerdicts(String name) {
    return layout.placedVerdicts(name);
  }

  public Path root() {
    return root;
  }

  public Path configFile() {
    return configFile;
  }

  public Path stateFile() {
    return layout.stateFile();
  }

  /** Present while `act` is stopped, whatever the configured mode says. */
  public Path actStopped() {
    return layout.actStopped();
  }

  /** Present while the whole board is stopped; holds the epoch millis of the stop. */
  public Path boardStopped() {
    return layout.boardStopped();
  }

  /**
   * The tasks counted toward stopping `act`, one per line, present while the count is above zero.
   */
  public Path actStrikes() {
    return layout.actStrikes();
  }

  /**
   * The Master's token, drawn at each start: beside the state, so a read-only root still starts.
   */
  public Path token() {
    return layout.token();
  }

  /** The last moment this process was seen alive, rewritten every minute. */
  public Path alive() {
    return layout.alive();
  }

  /** The keys intake turned away, one per line. */
  public Path intakeTurnedAway() {
    return layout.intakeTurnedAway();
  }

  /** What each deploy's check by the checker {@code role} ended in, one line per task. */
  public Path checkerVerdicts(String role) {
    return layout.checkerVerdicts(role);
  }

  /**
   * Where the verdicts were kept while the role was called qa; read while the new file is absent.
   */
  public Path legacyQaVerdicts() {
    return layout.legacyQaVerdicts();
  }

  /** The usage levels already told, with the period each was told in. */
  public Path usageAlerts() {
    return layout.usageAlerts();
  }

  /** Everything of one task: its worktrees, plan baseline, ticket text and scratch. */
  public Path task(String taskId) {
    return layout.task(TaskName.slug(taskId));
  }

  /** The last approved plan of a task. */
  public Path planBaseline(String taskId) {
    return layout.planBaseline(TaskName.slug(taskId));
  }

  /** A task's ticket text as first read. */
  public Path ticketText(String taskId) {
    return layout.ticketText(TaskName.slug(taskId));
  }

  /** The folder a task with no repository plans in. */
  public Path scratch(String taskId) {
    return layout.scratch(TaskName.slug(taskId));
  }

  public boolean isScratchOf(Path folder, String taskId) {
    return layout.isScratchOf(folder, TaskName.slug(taskId));
  }

  public @Nullable Path scratchCeiling(Path folder) {
    return layout.scratchCeiling(folder);
  }

  public Path archive() {
    return layout.archive();
  }

  public Path memory() {
    return layout.memory();
  }

  public Path openCodeGate() {
    return layout.openCodeGate();
  }

  public Path usageByDay() {
    return layout.usageByDay();
  }

  public Path finished() {
    return layout.finished();
  }

  public Path misses() {
    return layout.misses();
  }

  public Path interactions() {
    return layout.interactions();
  }

  public Path stops() {
    return layout.stops();
  }

  public Path stopsReviewed() {
    return layout.stopsReviewed();
  }

  public Path codeReviewLedger() {
    return layout.codeReviewLedger();
  }

  public Path codeReviewCheckouts() {
    return layout.codeReviewCheckouts();
  }

  public Path codeReviewStats() {
    return layout.codeReviewStats();
  }

  private Path resolve(@Nullable String override, String defaultName) {
    return override != null && !override.isBlank()
        ? Path.of(override).toAbsolutePath().normalize()
        : root.resolve(defaultName);
  }

  /**
   * Answered WITHOUT a Spring context: a launch has to hand Spring that file before there is one.
   */
  public static Path configFileOutside(String[] args, Host host) {
    String named = named(args, "--orchestrator.config-file=");
    String env = System.getenv("ORCHESTRATOR_CONFIG_FILE");
    String override = named != null ? named : env;
    if (override != null && !override.isBlank()) {
      return Path.of(override).toAbsolutePath().normalize();
    }
    return rootOutside(args, host).resolve("jagt.yml");
  }

  private static @Nullable String named(String[] args, String flag) {
    for (String arg : args) {
      if (arg.startsWith(flag)) {
        return arg.substring(flag.length());
      }
    }
    return null;
  }

  /** Answered WITHOUT a Spring context, as {@link #configFileOutside}. */
  public static Path dataOutside(String[] args, Host host) {
    return rootOutside(args, host).resolve("data");
  }

  /** The install home, where the jar's own files are placed; empty when the root is named. */
  public static Optional<Path> installedRootOutside(String[] args, Host host) {
    return installedRoot(
        args,
        host.env("ORCHESTRATOR_ROOT").orElse(null),
        host.jar().map(Path::toString).orElse(""));
  }

  public static Optional<Path> installedRoot(
      String[] args, @Nullable String env, String classPath) {
    String named = named(args, "--orchestrator.root=");
    boolean chosen = (named != null && !named.isBlank()) || (env != null && !env.isBlank());
    return chosen ? Optional.empty() : InstalledJar.onClassPath(classPath).map(InstalledJar::root);
  }

  private static Path rootOutside(String[] args, Host host) {
    String named = named(args, "--orchestrator.root=");
    String env = host.env("ORCHESTRATOR_ROOT").orElse(null);
    String root = named != null ? named : env;
    return root != null && !root.isBlank()
        ? Path.of(root).toAbsolutePath().normalize()
        : findRoot(host);
  }

  private static Path findRoot(Host host) {
    String env = host.env("ORCHESTRATOR_ROOT").orElse(null);
    if (env != null && !env.isBlank()) {
      return Path.of(env).toAbsolutePath().normalize();
    }
    Optional<InstalledJar> installed = host.jar().flatMap(InstalledJar::at);
    if (installed.isPresent()) {
      return installed.get().root();
    }
    Path start = host.workingDir().toAbsolutePath().normalize();
    for (Path dir = start; dir != null; dir = dir.getParent()) {
      for (String marker : ROOT_MARKERS) {
        if (Files.exists(dir.resolve(marker))) {
          return dir;
        }
      }
    }
    throw new IllegalStateException(
        "Cannot locate orchestrator root: none of "
            + ROOT_MARKERS
            + " found in "
            + start
            + " or any parent. Set ORCHESTRATOR_ROOT.");
  }
}
