package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.ConflictEnding;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Runs the reads {@link ConflictEnding} names for each deploy conflict's cause, while it stands;
 * each read sees the task as the read before it left it.
 */
@Service
@RequiredArgsConstructor
public class DeployConflictJob implements TaskJob {

  private static final Duration EVERY = Duration.ofSeconds(20);
  private static final Duration HOST_READ_EVERY = Duration.ofMinutes(1);

  private final List<ConflictRead> reads;
  private final StateService stateService;
  private final Clock clock;
  private final Map<String, Long> hostReadAt = new ConcurrentHashMap<>();

  @Override
  public String id() {
    return "deploy-conflict";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "run the reads that end each deploy conflict: a later green pipeline, a closed request,"
        + " a staged fix";
  }

  @Override
  public Duration every() {
    return EVERY;
  }

  @Override
  public Watch watch() {
    return Watch.defaults()
        .withStatuses(FlowRules::conflictedInTheDeployWorktree)
        .withStops(StopKind.withoutPause());
  }

  @Override
  public void runOn(Map<String, TaskState> tasks) {
    hostReadAt
        .keySet()
        .removeIf(
            taskId ->
                !Optional.ofNullable(tasks.get(taskId))
                    .map(task -> FlowRules.conflictedInTheDeployWorktree(task.status()))
                    .orElse(false));
    TaskJob.super.runOn(tasks);
  }

  @Override
  public void run(String taskId, TaskState task) {
    boolean hostDue = hostDue(taskId);
    TaskState seen = task;
    for (ConflictEnding ending : ConflictEnding.of(task)) {
      boolean due = ending.readsUnder(seen) && (hostDue || !ending.readsTheHost());
      if (due && readOf(ending).read(taskId, seen)) {
        Optional<TaskState> now = stillConflicted(taskId);
        if (now.isEmpty()) {
          return;
        }
        seen = now.get();
      }
    }
  }

  private Optional<TaskState> stillConflicted(String taskId) {
    return stateService
        .task(taskId)
        .filter(task -> FlowRules.conflictedInTheDeployWorktree(task.status()));
  }

  /** Half a pass early, so a pass late by a moment does not wait a whole pass more. */
  private boolean hostDue(String taskId) {
    long now = clock.millis();
    Long last = hostReadAt.get(taskId);
    if (last != null && now - last < HOST_READ_EVERY.minus(EVERY.dividedBy(2)).toMillis()) {
      return false;
    }
    hostReadAt.put(taskId, now);
    return true;
  }

  private ConflictRead readOf(ConflictEnding ending) {
    return reads.stream()
        .filter(read -> read.ending() == ending)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no read for " + ending));
  }
}
