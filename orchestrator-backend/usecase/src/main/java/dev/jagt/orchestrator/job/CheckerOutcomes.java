package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.AcceptanceDecline;
import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceQuestion;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What a finished check does to the task: rows not met go back as a round, unprovable ones to the
 * human.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckerOutcomes {

  private final RoleDefinition role;
  private final StepRelay relay;
  private final FlowReports reports;
  private final TaskStops stops;
  private final StateService state;

  private boolean stands(String taskId, String sha) {
    return state
        .task(taskId)
        .map(TaskState::deployment)
        .filter(deployment -> deployment.live() && deployment.tips().equals(sha))
        .isPresent();
  }

  /**
   * A task closed while acceptance read is gone, and its verdict is refused at the door; any other
   * is a deploy moved on.
   */
  private AcceptanceEnding notStanding(String taskId) {
    return state.task(taskId).isEmpty() ? AcceptanceEnding.GONE : AcceptanceEnding.DROPPED;
  }

  /**
   * What acceptance made of the session's decline: no dispute ends the check, a first one goes to
   * whoever stands in for the human, and one after that to the human alone, since only a right the
   * human grants is never answered by another.
   */
  public AcceptanceEnding declined(
      String taskId, AcceptanceDecline decline, boolean disputedBefore) {
    if (!stands(taskId, decline.sha())) {
      return notStanding(taskId);
    }
    if (!decline.failure().isBlank()) {
      stops.stop(taskId, TaskStop.Trigger.ACCEPTANCE_FAILED, decline.failure());
      return AcceptanceEnding.FAILED;
    }
    if (decline.dispute().isBlank()) {
      return AcceptanceEnding.DECLINED;
    }
    String said =
        "Acceptance disputes your answer to its round, on "
            + decline.sha()
            + ":\n"
            + decline.dispute();
    reports.ask(taskId, disputedBefore ? AgentReport.PERMISSION : AgentReport.QUESTION, said);
    log.atInfo()
        .setMessage("acceptance disputes")
        .addKeyValue("task", taskId)
        .addKeyValue("again", disputedBefore)
        .log();
    return disputedBefore ? AcceptanceEnding.ESCALATED : AcceptanceEnding.DISPUTED;
  }

  /** A verdict on a sha no longer the deploy's tip is dropped, whatever it found. */
  public AcceptanceEnding apply(String taskId, TaskState task, AcceptanceVerdict verdict) {
    AcceptanceEnding ending =
        stands(taskId, verdict.sha()) ? verdict.ending() : notStanding(taskId);
    switch (ending) {
      case PASSED ->
          state.updateTask(
              taskId, current -> TaskReducer.checkerPassed(current, role.id(), verdict.sha()));
      case NOT_MET -> notMet(taskId, task, verdict);
      case UNPROVABLE -> unprovable(taskId, verdict);
      case FAILED -> failed(taskId, verdict);
      default ->
          log.atInfo()
              .setMessage("acceptance verdict kept")
              .addKeyValue("task", taskId)
              .addKeyValue("ending", ending)
              .log();
    }
    return ending;
  }

  private boolean notMet(String taskId, TaskState task, AcceptanceVerdict verdict) {
    String facts =
        verdict.sha()
            + ":\n"
            + verdict.notMet().stream()
                .map(AcceptanceVerdict.Row::line)
                .collect(Collectors.joining("\n"));
    StepRelay.Claim claim =
        new StepRelay.Claim(
            Standing.of(task),
            opensTheTurn ->
                reports.reportWhileStanding(
                    taskId,
                    task,
                    FlowRules.relayed(),
                    AgentReport.PLAIN,
                    "acceptance: not met; relayed",
                    opensTheTurn),
            opened -> reports.undoClaim(taskId, task, opened));
    boolean sent =
        OriginContext.as(
            ActionOrigin.ACCEPTANCE,
            () -> relay.relayRound(taskId, Step.ACCEPTANCE_ROUND, facts, claim));
    if (sent) {
      String digest =
          Objects.requireNonNull(Objects.requireNonNull(task.deployment()).askedDigest());
      state.updateTask(
          taskId, current -> TaskReducer.checkerRoundSent(current, role.id(), digest, facts));
    }
    log.atInfo()
        .setMessage("acceptance returns the round")
        .addKeyValue("task", taskId)
        .addKeyValue("rows", verdict.notMet().size())
        .addKeyValue("sent", sent)
        .log();
    return sent;
  }

  private void unprovable(String taskId, AcceptanceVerdict verdict) {
    String said =
        verdict.question().isBlank()
            ? verdict.rows().stream()
                .filter(row -> row.status() != AcceptanceVerdict.Status.MET)
                .map(AcceptanceVerdict.Row::line)
                .collect(Collectors.joining("\n"))
            : verdict.question();
    reports.ask(
        taskId,
        AgentReport.QUESTION,
        AcceptanceQuestion.PREFIX
            + verdict.sha()
            + ":\n"
            + (said.isBlank() ? "the check named no criterion" : said));
    log.atInfo()
        .setMessage("acceptance asks")
        .addKeyValue("task", taskId)
        .addKeyValue("rows", verdict.rows().size())
        .log();
  }

  private void failed(String taskId, AcceptanceVerdict verdict) {
    stops.stop(taskId, TaskStop.Trigger.ACCEPTANCE_FAILED, verdict.failure());
    log.atWarn()
        .setMessage("acceptance check failed")
        .addKeyValue("task", taskId)
        .addKeyValue("cause", verdict.failure())
        .log();
  }
}
