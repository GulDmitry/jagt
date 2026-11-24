package dev.jagt.gradle.suitestats;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A fresh run slower than the median of earlier fresh runs;
 * any other run says nothing about speed.
 */
public final class Trend {

  private Trend() {}

  public static Optional<String> slower(
      List<Map<String, Object>> history,
      String task,
      String command,
      long seconds,
      boolean fresh,
      int trendRuns,
      BigDecimal factor) {
    if (!fresh) {
      return Optional.empty();
    }
    List<Long> earlier =
        history.stream()
            .filter(
                run ->
                    Boolean.TRUE.equals(run.get("success"))
                        && Boolean.TRUE.equals(run.get("fresh"))
                        && task.equals(run.get("task"))
                        && command.equals(run.get("command")))
            .map(run -> ((Number) run.get("seconds")).longValue())
            .toList();
    int from = Math.max(0, earlier.size() - trendRuns);
    List<Long> window = earlier.subList(from, earlier.size()).stream().sorted().toList();
    if (window.isEmpty()) {
      return Optional.empty();
    }
    long median = window.get(window.size() / 2);
    BigDecimal limit = BigDecimal.valueOf(median).multiply(factor);
    if (BigDecimal.valueOf(seconds).compareTo(limit) <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        "suiteStats: %s took %d s, its trend %d s over %d fresh runs"
            .formatted(task, seconds, median, window.size()));
  }
}
