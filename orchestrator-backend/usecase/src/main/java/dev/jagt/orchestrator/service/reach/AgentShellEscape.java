package dev.jagt.orchestrator.service.reach;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.reach.ShellEscapeReach;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentShellEscape implements ShellEscapeReach {

  private final AgentRuntime runtime;

  @Override
  public boolean ready() {
    return runtime.shellEscape().isPresent();
  }
}
