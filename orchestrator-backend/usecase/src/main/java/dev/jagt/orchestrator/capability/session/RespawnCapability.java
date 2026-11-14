package dev.jagt.orchestrator.capability.session;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RespawnCapability implements TaskCapability {

  private final AgentSessions sessions;

  @Override
  public TaskAction action() {
    return TaskAction.RESPAWN;
  }

  @Override
  public Outcome run(String taskId) {
    return Outcome.ok(sessions.openTaskTab(taskId, null));
  }
}
