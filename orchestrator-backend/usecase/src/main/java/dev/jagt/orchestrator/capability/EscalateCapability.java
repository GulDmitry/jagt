package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.TaskStops;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalateCapability implements TaskCapability {

  private final TaskStops stops;
  private final AgentSessions sessions;

  @Override
  public TaskAction action() {
    return TaskAction.ESCALATE;
  }

  @Override
  public Outcome run(String taskId) {
    stops.escalate(taskId);
    return Outcome.ok("lift " + taskId + " on the next tier: " + sessions.restartOnTier(taskId));
  }
}
