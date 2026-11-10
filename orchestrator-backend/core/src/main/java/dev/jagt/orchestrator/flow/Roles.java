package dev.jagt.orchestrator.flow;

import java.util.List;
import java.util.Optional;

/** The checker roles a task's chart is composed of, and whether one may be. */
public final class Roles {

  private Roles() {}

  /** Sets the roles every task is stepped on. */
  public static void install(List<CheckerRole> checkers) {
    TaskChart.install(Composition.of(checkers));
  }

  /**
   * Why the chart refuses {@code checker} composed beside the core regions, or empty where it
   * stands.
   */
  public static Optional<String> lint(CheckerRole checker) {
    try {
      new TaskChart(Composition.of(List.of(checker)));
      return Optional.empty();
    } catch (RuntimeException refused) {
      return Optional.of(String.valueOf(refused.getMessage()));
    }
  }
}
