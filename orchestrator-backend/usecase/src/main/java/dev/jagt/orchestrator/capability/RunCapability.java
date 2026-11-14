package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.reach.ShellEscapeReach;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunCapability implements TaskCapability {

  private final AgentSessions sessions;
  private final ShellEscapeReach reach;

  @Override
  public TaskAction action() {
    return TaskAction.RUN;
  }

  @Override
  public boolean offered() {
    return reach.ready();
  }

  @Override
  public void vetLine(String line) {
    if (line.isBlank() || line.chars().anyMatch(Character::isISOControl)) {
      throw Refusal.byState("run takes one line of text, nothing else");
    }
  }

  @Override
  public Outcome run(String taskId) {
    throw Refusal.byState("run needs a line: use the Run button on the Session tab");
  }

  @Override
  public Outcome runLine(String taskId, String line) {
    return Outcome.relayed(sessions.run(taskId, line), null);
  }
}
