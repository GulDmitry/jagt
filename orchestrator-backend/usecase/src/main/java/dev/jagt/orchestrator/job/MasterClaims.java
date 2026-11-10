package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The claims a Master verdict relays under, so a task that moved on keeps what it moved to. */
@Component
@RequiredArgsConstructor
class MasterClaims {

  private final FlowReports reports;

  /**
   * Back to work in the claim itself, or the session's report is no transition and the verdict
   * reads as this round's.
   */
  StepRelay.Claim reporting(String taskId, TaskState read, AgentReport means, String message) {
    return new StepRelay.Claim(
        Standing.of(read),
        opensTheTurn ->
            reports.reportWhileStanding(
                taskId, read, FlowRules.relayed(), means, message, opensTheTurn),
        opened -> reports.undoClaim(taskId, read, opened));
  }

  /** Opens a turn of its own and moves no status; the task as opened lands in {@code after}. */
  StepRelay.Claim opening(String taskId, TaskState task, AtomicReference<TaskState> after) {
    return new StepRelay.Claim(
        Standing.of(task),
        opens ->
            reports.claimWhileStanding(
                taskId,
                task,
                live -> {
                  TaskState opened = opens.apply(live);
                  after.set(opened);
                  return opened;
                }),
        opened -> reports.undoClaim(taskId, task, opened));
  }
}
