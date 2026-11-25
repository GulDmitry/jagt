package dev.jagt.orchestrator.job.verdict;

import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.plan.PlanApproval;
import dev.jagt.orchestrator.service.plan.Unplaceable;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** A plan the Master found holds: approved where a human said the reviewer stands in for them. */
@Service
@RequiredArgsConstructor
@Slf4j
class MasterPlanVerdicts {

  private static final String PLAN_ANSWERED =
      "The Master read plan.md against the ticket and answered your question,"
          + " standing in for the human:\n";

  private final StepRelay relay;
  private final MasterClaims claims;
  private final MasterDecisions decisions;
  private final PlanApproval approval;

  boolean holds(
      String taskId,
      TaskState task,
      MasterReview.Verdict verdict,
      ConfigFile.ArbiterConfig config) {
    return OriginContext.as(
        ActionOrigin.MASTER,
        () ->
            config.mayApprovePlan(task)
                && !task.gate().planGate().keptForTheHuman()
                && go(taskId, task, verdict));
  }

  private boolean go(String taskId, TaskState task, MasterReview.Verdict verdict) {
    TaskState standing = advised(taskId, task, verdict);
    StepRelay.Claim claim =
        claims.reporting(taskId, standing, AgentReport.PLAIN, "master: the plan holds");
    try {
      if (!approval.approve(
          taskId, "The Master read plan.md against the ticket, standing in for the human", claim)) {
        return false;
      }
    } catch (Unplaceable unplaceable) {
      return revise(taskId, task, standing, verdict, unplaceable.revision());
    }
    decisions.returned(task, verdict.round(), List.of());
    log.atInfo()
        .setMessage("master approves the plan")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .log();
    return true;
  }

  /** jagt.yml is the human's, so a plan naming what it lacks goes back to its author. */
  private boolean revise(
      String taskId,
      TaskState task,
      TaskState standing,
      MasterReview.Verdict verdict,
      String revision) {
    StepRelay.Claim claim =
        claims.reporting(taskId, standing, AgentReport.PLAIN, "reviewer: not ready; relayed");
    if (!relay.relayRound(taskId, MasterMove.PLAN_NOT_READY.step(), revision, claim)) {
      return false;
    }
    decisions.returned(task, verdict.round(), List.of());
    log.atInfo()
        .setMessage("master returns an unplaceable plan")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .log();
    return true;
  }

  /**
   * The task as it stands once the plan's question is answered, which opens a turn of its own and
   * moves no status.
   */
  private TaskState advised(String taskId, TaskState task, MasterReview.Verdict verdict) {
    if (!task.standing().report().asks() || verdict.advice().isEmpty()) {
      return task;
    }
    AtomicReference<TaskState> after = new AtomicReference<>(task);
    relay.relay(
        taskId,
        MasterMove.PLAN_ANSWERED.step(),
        PLAN_ANSWERED
            + verdict.advice().stream()
                .map(MasterReview.Said::line)
                .collect(Collectors.joining("\n")),
        claims.opening(taskId, task, after));
    return after.get();
  }
}
