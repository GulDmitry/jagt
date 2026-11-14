package dev.jagt.orchestrator.service.reach;

import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.port.reach.ScenarioReach;
import dev.jagt.orchestrator.service.role.RoleSettings;
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
