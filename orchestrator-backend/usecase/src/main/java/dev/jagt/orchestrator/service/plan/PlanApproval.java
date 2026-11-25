package dev.jagt.orchestrator.service.plan;

import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.CaseDoor;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.turn.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * The one way a plan is approved, whoever read it: the gate opens and the session is told to start.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanApproval {

  private final StateService stateService;
  private final StepRelay relay;
  private final CaseDoor door;
  private final ApprovedPlan plan;

  /**
   * Whether the session was told anything new; the gate is open either way, unless no case allowed
   * the approval.
   */
  public boolean approve(String taskId, String reader) {
    return approve(taskId, reader, null);
  }

  /**
   * The same under a claim, which opens the gate in its own write, so a task that moved on keeps
   * its gate.
   */
  public boolean approve(String taskId, String reader, StepRelay.@Nullable Claim claim) {
    String id = stateService.canonicalTaskId(taskId);
    if (claim != null) {
      relay.requireStanding(id, Step.PLAN, claim);
    }
    if (!door.presses(id, TaskAction.APPROVE_PLAN.id())) {
      return false;
    }
    boolean placed = plan.settle(id);
    Standing read = stateService.task(id).map(Standing::of).orElse(Standing.ABSENT);
    if (claim == null) {
      stateService.reduce(id, TaskReducer::planApproved);
    }
    String facts = reader + ": the plan holds.";
    boolean told =
        claim == null
            ? relay.relay(id, Step.PLAN, facts, delivery(placed), read)
            : relay.relay(id, Step.PLAN, facts, delivery(placed), opened(claim));
    if (claim != null && !told) {
      stateService.reduce(id, TaskReducer::planApproved);
    }
    plan.leave(id);
    log.atInfo()
        .setMessage("plan approved")
        .addKeyValue("task", id)
        .addKeyValue("reader", reader)
        .log();
    return told;
  }

  /** A task just placed starts a fresh session in its new primary: its plan carries the context. */
  private static StepRelay.Delivery delivery(boolean placed) {
    return placed ? StepRelay.Delivery.UNDER_FRESH : StepRelay.Delivery.CONTEXT;
  }

  private StepRelay.Claim opened(StepRelay.Claim claim) {
    return new StepRelay.Claim(
        claim.read(),
        opens ->
            claim.write().apply(task -> stateService.reducer().planApproved(opens.apply(task))),
        claim.undo());
  }
}
