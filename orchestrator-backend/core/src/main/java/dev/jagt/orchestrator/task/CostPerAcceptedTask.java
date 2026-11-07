package dev.jagt.orchestrator.task;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public record CostPerAcceptedTask(int shipped, int finished, double spentUsd, boolean lowerBound) {

  private static final long WEEK_MILLIS = Duration.ofDays(7).toMillis();

  public static CostPerAcceptedTask over(List<FinishedTask> tasks, long weekStart) {
    var week =
        tasks.stream()
            .filter(
                task ->
                    task.finishedAt() >= weekStart && task.finishedAt() < weekStart + WEEK_MILLIS)
            .toList();
    double spent =
        week.stream()
            .filter(task -> task.spend().costUsd() != null)
            .mapToDouble(task -> task.spend().costUsd())
            .sum();
    boolean unknown =
        week.stream()
            .anyMatch(task -> task.spend().costUsd() == null || task.spend().costIsLowerBound());
    int shipped = (int) week.stream().filter(task -> task.reached(TaskStatus.DEPLOYED)).count();
    return new CostPerAcceptedTask(shipped, week.size(), spent, unknown);
  }

  public Optional<Double> perAccepted() {
    return shipped == 0 ? Optional.empty() : Optional.of(spentUsd / shipped);
  }
}
