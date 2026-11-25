package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.service.review.ReviewSweepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A round decides nothing: what it found is reported and nothing more. A round with nothing new in
 * it, or one still waiting, answers as nothing done.
 */
@Component
@RequiredArgsConstructor
public class SweepCapability implements TaskCapability {

  private final ReviewSweepService sweeps;

  @Override
  public TaskAction action() {
    return TaskAction.SWEEP;
  }

  @Override
  public Outcome run(String taskId) {
    ReviewSweepService.SweepResult result = sweeps.sweep(taskId);
    return switch (result.kind()) {
      case UNCHANGED, PENDING -> Outcome.nothing(result.message());
      default -> Outcome.ok(result.message());
    };
  }
}
