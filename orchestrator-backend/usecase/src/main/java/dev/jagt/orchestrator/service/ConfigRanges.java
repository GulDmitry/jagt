package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.turn.Standing;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** The one table of the numeric keys of {@code jagt.yml} and the range each is read in. */
public final class ConfigRanges {

  private static final int NO_CEILING = Integer.MAX_VALUE;

  /** A written number outside its range. */
  public record Refusal(String key, int value, String range) {}

  private record Ranged(
      String key,
      Function<ConfigFile, Integer> floor,
      int ceiling,
      Function<ConfigFile, @Nullable Integer> written) {

    Ranged(String key, int ceiling, Function<ConfigFile, @Nullable Integer> written) {
      this(key, config -> 1, ceiling, written);
    }
  }

  private static final List<Ranged> RANGED =
      List.of(
          new Ranged("archive.maxMegabytes", NO_CEILING, c -> c.archive().maxMegabytes()),
          new Ranged(
              "loopBreaker.perHour", Standing.MAX_HISTORY - 1, c -> c.loopBreaker().perHour()),
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
          new Ranged(
              "autoReview.maxIntervalMinutes",
              c -> c.autoReview().minIntervalMinutesOrDefault(),
              NO_CEILING,
              c -> c.autoReview().maxIntervalMinutes()),
          new Ranged(
              "autoReview.waitingMaxIntervalMinutes",
              c -> c.autoReview().maxIntervalMinutesOrDefault(),
              NO_CEILING,
              c -> c.autoReview().waitingMaxIntervalMinutes()),
          new Ranged("tracker.everyMinutes", NO_CEILING, c -> c.tracker().everyMinutes()));

  private ConfigRanges() {}

  public static List<String> keys() {
    return RANGED.stream().map(Ranged::key).toList();
  }

  public static @Nullable Integer written(String key, ConfigFile config) {
    return RANGED.stream()
        .filter(number -> number.key().equals(key))
        .findFirst()
        .map(number -> number.written().apply(config))
        .orElse(null);
  }

  public static List<Refusal> refusals(ConfigFile config) {
    return RANGED.stream().map(number -> refusal(number, config)).filter(Objects::nonNull).toList();
  }

  private static @Nullable Refusal refusal(Ranged number, ConfigFile config) {
    Integer value = number.written().apply(config);
    int floor = number.floor().apply(config);
    if (value == null || (value >= floor && value <= number.ceiling())) {
      return null;
    }
    return new Refusal(number.key(), value, range(floor, number.ceiling()));
  }

  private static String range(int floor, int ceiling) {
    return ceiling == NO_CEILING ? "at least " + floor : floor + " to " + ceiling;
  }
}
