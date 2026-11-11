package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Checkers {

  public static final String ACCEPTANCE = "acceptance";

  /** The ids of the checker roles jagt's distribution holds a folder for. */
  public static final Set<String> SHIPPED = Set.of(ACCEPTANCE);

  private final TaskChart chart;

  public Checkers(TaskChart chart) {
    this.chart = chart;
  }

  /** What the board says of a task this checker passed. */
  public static String passedLabel(String id) {
    return ACCEPTANCE.equals(id) ? "accepted" : id + " passed";
  }

  /** The ids of the installed checkers, in composition order. */
  public List<String> installed() {
    return chart.installedCheckers();
  }

  /** Where each installed checker stands for the task, in composition order. */
  public Map<String, CheckerPhase> of(TaskState task) {
    Map<String, CheckerPhase> phases = new LinkedHashMap<>();
    chart.installedCheckers().forEach(role -> phases.put(role, CheckerPhase.of(task, role)));
    return phases;
  }

  /** The installed checkers whose {@code role.yml} declares jobs. */
  public List<String> withJobs() {
    return chart.composition().withJobs();
  }

  /** The first checker whose read is running: it holds the turn. */
  static Optional<String> reading(Map<String, CheckerPhase> checkers) {
    return checkers.entrySet().stream()
        .filter(entry -> entry.getValue() == CheckerPhase.READING)
        .map(Map.Entry::getKey)
        .findFirst();
  }
}
