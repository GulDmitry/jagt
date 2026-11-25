package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.flow.session.Standing;
import dev.jagt.orchestrator.service.git.TrustedGitState;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.turn.Step;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrustCapability implements TaskCapability {

  static final String TRUSTED =
      "The human trusted your git config and hooks as they are now:"
          + " retry what the git-state precondition refused.";

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
        ? relay.relay(taskId, Step.ANSWERED_UNDER, TRUSTED, StepRelay.Delivery.UNDER, read)
        : relay.relay(taskId, Step.ANSWERED, TRUSTED, StepRelay.Delivery.CONTEXT, read);
  }
}
