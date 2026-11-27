package dev.jagt.orchestrator.job.conflict;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.job.deploy.DeployConflicts;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A handed deploy conflict whose worktree stops moving goes to the Master where it answers, then to
 * the human: each once, kept in the task's state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictStall {

  private final StateService stateService;
  private final ConfigService config;
  private final Clock clock;
  private final Checkers checkers;

  boolean handed(String taskId) {
    return stateService
        .task(taskId)
        .flatMap(TaskState::currentConflict)
        .filter(Conflict::handed)
        .isPresent();
  }

  void watch(String taskId, DeployConflicts.WaitingConflict conflict) {
    long now = clock.millis();
    Duration after = config.load().conflictStallOrDefault();
    AtomicReference<Conflict.@Nullable Told> told = new AtomicReference<>();
    stateService.reduce(
        taskId,
        (reducer, task) -> {
          TaskState seen =
              reducer.deployStepped(task, new DeployStep.Seen(conflict.progress()), now);
          Optional<Conflict> due =
              seen.currentConflict().filter(standing -> standing.due(after, now));
          if (due.isEmpty()) {
            return seen;
          }
          String reason = due.get().reason(conflict.worktree().toString());
          if (!due.get().askedTheArbiter() && arbiterAnswers(seen)) {
            return reducer.conflictReferred(
                seen, "The " + kindOf(seen) + " conflict stalled: " + reason, now);
          }
          TaskState stalled = reducer.deployStepped(seen, new DeployStep.Told(reason), now);
          stalled.currentConflict().map(Conflict::told).ifPresent(told::set);
          return stalled;
        });
    Optional.ofNullable(told.get()).ifPresent(stop -> announce(taskId, stop, after));
  }

  /**
   * The question a session would put here, were it the one asking, goes to the Master: the same
   * fact {@code MasterAnswerJob} answers by.
   */
  private boolean arbiterAnswers(TaskState task) {
    return !task.standing().report().asks()
        && Turns.next(
                task.standing().status(),
                AgentReport.QUESTION,
                task.work().stopped() ? StopHold.HUMAN : StopHold.NONE,
                checkers.of(task),
                config.load().arbiter().profile(),
                Facts.projected(task))
            .equals(Turn.Holder.ARBITER);
  }

  private static String kindOf(TaskState task) {
    return Facts.revertConflicted(task) ? "revert" : "deploy";
  }

  private void announce(String taskId, Conflict.Told stop, Duration after) {
    Optional<TaskState> task = stateService.task(taskId);
    String alias = task.map(taskState -> taskState.ticket().alias()).orElse(taskId);
    String kind = task.map(ConflictStall::kindOf).orElse("deploy");
    log.atWarn()
        .setMessage(kind + " conflict stalled")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", alias)
        .addKeyValue(
            "cause", "no change in the " + kind + " worktree for " + after.toMinutes() + " min")
        .addKeyValue("said", stop.reason())
        .addKeyValue("effect", "the human is told once; the " + kind + " waits")
        .addKeyValue(
            "fix", "resolve it in the " + kind + " worktree, git add, then " + kind + " " + alias)
        .log();
  }
}
