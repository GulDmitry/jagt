package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.ShellEscapeReach;
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
