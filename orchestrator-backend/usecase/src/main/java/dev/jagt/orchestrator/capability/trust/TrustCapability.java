package dev.jagt.orchestrator.capability.trust;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TrustedGitState;
import dev.jagt.orchestrator.task.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrustCapability implements TaskCapability {

  static final String TRUSTED =
      "The human trusted your git config and hooks as they are now: retry what the git-state guard"
          + " refused.";

  private final TrustedGitState trusted;
  private final StepRelay relay;

  @Override
  public TaskAction action() {
    return TaskAction.TRUST;
  }

  @Override
  public Outcome run(String taskId) {
    String message = "trust " + taskId + ": its git config and hooks are trusted as they are now";
    return Outcome.ok(
        trusted.trust(taskId) && answer(taskId)
            ? message + "; its session is told to go on"
            : message);
  }

  private boolean answer(String taskId) {
    Standing read = relay.standing(taskId);
    return (read.status() != null && FlowRules.awaitsItsInstruction(read.status()))
        ? relay.relayUnder(taskId, Step.ANSWERED_UNDER, TRUSTED, false, read)
        : relay.relay(taskId, Step.ANSWERED, TRUSTED, read);
  }
}
