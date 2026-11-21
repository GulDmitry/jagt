package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ProjectProblems;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.turn.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A project is checked as far as the filesystem answers; asking a remote costs a fetch per project
 * per start.
 */
@Component
@RequiredArgsConstructor
public class ConfigCheck implements StartupCheck {

  private static final String CONFIG_FILE = "orchestrator.config-file";
  private static final List<String> VIEW_MODES = List.of("shared", "tab-per-task");
  private static final Map<String, String> RETIRED_KEYS =
      Map.of(
          "ui", "the board is the only surface",
          "webTerminal", "`focus` raises the terminal window",
          "dashboard", "nothing renders a console table",
          "openWarpWindow", "write `terminal: none` for no terminal window",
          "openTerminalWindow", "write `terminal: none` for no terminal window");
  private static final int NO_CEILING = Integer.MAX_VALUE;

  private record Ranged(
      String key, int ceiling, Function<ConfigService.ConfigFile, @Nullable Integer> written) {}

  private static final List<Ranged> RANGED =
      List.of(
          new Ranged("archive.maxMegabytes", NO_CEILING, c -> c.archive().maxMegabytes()),
          new Ranged(
              "loopBreaker.perHour", TaskState.MAX_HISTORY - 1, c -> c.loopBreaker().perHour()),
          new Ranged("loopBreaker.rounds", Turn.MAX_RUN - 1, c -> c.loopBreaker().rounds()),
          new Ranged(
              "loopBreaker.stalledRounds",
              RoundTrail.MAX_ROUNDS - 1,
              c -> c.loopBreaker().stalledRounds()),
          new Ranged("loopBreaker.ships", NO_CEILING, c -> c.loopBreaker().ships()),
          new Ranged("conflictStallMinutes", NO_CEILING, c -> c.sessions().conflictStallMinutes()),
          new Ranged("maxTasks", NO_CEILING, c -> c.sessions().maxTasks()),
          new Ranged("agent.probeSeconds", NO_CEILING, c -> c.agent().cadence().probeSeconds()),
          new Ranged(
              "agent.autoplan.files", NO_CEILING, c -> c.agent().autoplanOrDefault().files()),
          new Ranged("autoReview.windowHours", NO_CEILING, c -> c.autoReview().windowHours()),
          new Ranged(
              "autoReview.minIntervalMinutes",
              NO_CEILING,
              c -> c.autoReview().minIntervalMinutes()),
          new Ranged("tracker.everyMinutes", NO_CEILING, c -> c.tracker().everyMinutes()));

  private final ConfigService configService;
  private final OrchestratorPaths paths;
  private final SessionHost sessions;
  private final Host host;

  @Override
  public List<Problem> problems() {
    List<Problem> retired = retiredConfig();
    if (!retired.isEmpty()) {
      return retired;
    }
    List<Problem> shadowed = shadowingConfigDir();
    if (!shadowed.isEmpty()) {
      return shadowed;
    }
    ConfigService.ConfigFile config;
    try {
      config = configService.load();
    } catch (RuntimeException unreadable) {
      return List.of(new Problem(CONFIG_FILE, String.valueOf(unreadable.getMessage())));
    }
    List<Problem> problems = new ArrayList<>(retiredKeys());
    problems.addAll(viewerProblems(config));
    problems.addAll(numberProblems(config));
    Map<String, ProjectConfig> projects = config.projects();
    projects.forEach((key, project) -> problems.addAll(ProjectProblems.of(key, project)));
    return problems;
  }

  /**
   * A file no longer read is worse than a missing one: everything it says is still true, and none
   * applies.
   */
  private List<Problem> retiredConfig() {
    Path retired = paths.root().resolve("config.json");
    if (!Files.exists(retired) || Files.exists(paths.configFile())) {
      return List.of();
    }
    return List.of(
        new Problem(
            CONFIG_FILE,
            retired
                + " is no longer read — everything lives in "
                + paths.configFile()
                + " now, under one `orchestrator` root. Copy jagt.desktop.yml.dist next to it"
                + " and move your projects across:\n"
                + "    orchestrator:\n"
                + "      projects:\n"
                + "        my-project:\n"
                + "          path: /absolute/path/to/base/repository\n"
                + "          baseBranch: origin/main\n"
                + "          deployBranch: dev"));
  }

  /**
   * A key nothing binds is dropped in silence, so an older jagt.yml goes on saying what no longer
   * applies.
   */
  private List<Problem> retiredKeys() {
    return configService.declaredKeys().stream()
        .filter(RETIRED_KEYS::containsKey)
        .sorted()
        .map(key ->
            new Problem(
                "orchestrator." + key,
                "orchestrator."
                    + key
                    + " is no longer read: "
                    + RETIRED_KEYS.get(key)
                    + ". Remove it from "
                    + paths.configFile()
                    + "."))
        .toList();
  }

  /**
   * Spring reads {@code ./config/application.yml} above anything a launch hands it, so it outranks
   * jagt.yml.
   */
  private List<Problem> shadowingConfigDir() {
    Path shadowing = host.workingDir().resolve("config").resolve("application.yml");
    if (!Files.exists(shadowing)) {
      return List.of();
    }
    return List.of(
        new Problem(
            "config/application.yml",
            shadowing
                + " outranks jagt.yml for every key it"
                + " names. Move what it holds into jagt.yml and delete it."));
  }

  /**
   * A number is read as written or not at all: one outside its range is refused, never replaced.
   */
  private static List<Problem> numberProblems(ConfigService.ConfigFile config) {
    return RANGED.stream()
        .map(number -> numberProblem(number, number.written().apply(config)))
        .filter(Objects::nonNull)
        .toList();
  }

  private static @Nullable Problem numberProblem(Ranged number, @Nullable Integer value) {
    if (value == null || (value >= 1 && value <= number.ceiling())) {
      return null;
    }
    String range = number.ceiling() == NO_CEILING ? "at least 1" : "1 to " + number.ceiling();
    String key = "orchestrator." + number.key();
    return new Problem(key, key + " is " + value + ": it must be " + range + ".");
  }

  private List<Problem> viewerProblems(ConfigService.ConfigFile config) {
    List<Problem> problems = new ArrayList<>();
    String session = config.viewer().session();
    if (session != null && !session.isBlank()) {
      sessions
          .unaddressable(session)
          .ifPresent(why ->
              problems.add(
                  new Problem(
                      "orchestrator.viewer.tmuxSession",
                      "viewer.tmuxSession '" + session + "' " + why + ". Pick another name.")));
    }
    String viewMode = config.viewer().viewMode();
    if (viewMode != null
        && !viewMode.isBlank()
        && VIEW_MODES.stream().noneMatch(mode -> mode.equalsIgnoreCase(viewMode))) {
      problems.add(
          new Problem(
              "orchestrator.viewer.viewMode",
              "viewer.viewMode '" + viewMode + "' is not one of " + VIEW_MODES + "."));
    }
    return problems;
  }
}
