package dev.jagt.orchestrator.job.review;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.service.report.AgentStatusReports;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.service.turn.TaskMovedOn;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Step;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Runs the project's own command, then the plan's acceptance probes, for every task waiting at
 * VERIFYING. Green hands the round on to the human; red goes back to the agent with the output, so
 * nobody opens a code viewer on a tree that does not build.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyJob implements TaskJob {

  private final StateService stateService;
  private final Verification verification;
  private final AgentStatusReports statusReports;
  private final StepRelay relay;
  private final TaskStops stops;

  @Override
  public String id() {
    return "verify";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "run each project's own command before a hand-back reaches a human";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(20);
  }

  @Override
  public Watch watch() {
    return Watch.defaults()
        .withStatuses(FlowRules::awaitingVerification)
        .withReports(report -> !report.asks());
  }

  @Override
  public void run(String taskId, TaskState task) {
    verify(taskId, task);
  }

  private void verify(String taskId, TaskState task) {
    Optional<String> key = verification.key(task);
    if (key.filter(failing -> task.round().toldOrNone().said(ToldOnce.Kind.VERIFY_FAILED, failing))
        .isPresent()) {
      stops.stop(
          taskId,
          TaskStop.Trigger.UNCHANGED_FAILURE,
          "handed back the tree its verification failed on: "
              + standing(task)
              + failedOutput(task));
      return;
    }
    Optional<String> failure = verification.failure(task);
    if (failure.isPresent()) {
      if (stillThisRound(taskId, task, Step.VERIFY)) {
        returned(taskId, task, key, Step.VERIFY, failure.get(), "verification failed; relayed");
      }
      return;
    }
    List<Probe> probed = verification.probed(taskId, task);
    if (!stillThisRound(taskId, task, Step.PROBES)) {
      return;
    }
    if (!probed.isEmpty()) {
      stateService.updateTask(taskId, current -> current.withProbes(probed));
    }
    List<Probe> failed = probed.stream().filter(Probe::failed).toList();
    if (failed.isEmpty()) {
      statusReports.verified(true, "verified", taskId);
      return;
    }
    returned(
        taskId,
        task,
        key,
        Step.PROBES,
        failed.stream().map(Probe::result).collect(Collectors.joining("\n\n")),
        "plan probe failed; relayed");
  }

  private boolean stillThisRound(String taskId, TaskState taken, Step step) {
    Optional<TaskState> now = stateService.task(taskId);
    boolean same =
        now.filter(task ->
                task.standing().status() == taken.standing().status()
                    && task.standing().statusSince() == taken.standing().statusSince())
            .isPresent();
    if (!same) {
      log.atInfo()
          .setMessage("verification outdated")
          .addKeyValue("task", taskId)
          .addKeyValue("step", step.id())
          .addKeyValue("status", now.map(task -> task.standing().status().name()).orElse("gone"))
          .log();
    }
    return same;
  }

  private static String failedOutput(TaskState task) {
    String output = task.round().toldOrNone().valueOf(ToldOnce.Kind.VERIFY_OUTPUT);
    return output.isEmpty() ? "" : "\n" + output;
  }

  private static String standing(TaskState task) {
    String probes =
        task.gate().probes().stream()
            .filter(Probe::failed)
            .map(Probe::toString)
            .collect(Collectors.joining("; "));
    return probes.isEmpty() ? "the project's verify command" : probes;
  }

  /**
   * A round stopped at its ceiling stays at VERIFYING, verified anew once the human lifts the stop.
   */
  private void returned(
      String taskId, TaskState read, Optional<String> key, Step step, String facts, String said) {
    boolean told;
    try {
      told = relay.relay(taskId, step, facts, StepRelay.Delivery.ROUND, Standing.of(read));
    } catch (TaskMovedOn dropped) {
      return;
    }
    if (told) {
      key.ifPresent(failing ->
          stateService.reduce(
              taskId,
              (reducer, current) ->
                  reducer.told(
                      reducer.told(current, ToldOnce.Kind.VERIFY_FAILED, failing),
                      ToldOnce.Kind.VERIFY_OUTPUT,
                      facts.substring(0, Math.min(facts.length(), TaskStops.MAX_EVIDENCE + 1)))));
    } else if (stateService
        .task(taskId)
        .filter(taskState -> taskState.work().stopped())
        .isPresent()) {
      return;
    }
    statusReports.verified(false, said, taskId);
  }
}
