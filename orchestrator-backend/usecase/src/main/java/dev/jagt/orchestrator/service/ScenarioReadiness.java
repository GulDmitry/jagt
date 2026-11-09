package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.port.ScenarioReach;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScenarioReadiness implements ScenarioReach {

  private final RoleSettings settings;

  @Override
  public boolean ready() {
    RoleOptions options = settings.load();
    return options.enabled() && options.workspacePath().isPresent();
  }
}
