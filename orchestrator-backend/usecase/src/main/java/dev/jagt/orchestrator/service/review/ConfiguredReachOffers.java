package dev.jagt.orchestrator.service.review;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.port.reach.ScenarioReach;
import dev.jagt.orchestrator.port.reach.ShellEscapeReach;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConfiguredReachOffers implements ReachOffers {

  private final ScenarioReach scenarios;
  private final ShellEscapeReach shellEscape;

  @Override
  public Facts.Reach facts() {
    return Facts.Reach.defaults()
        .withScenarios(scenarios.ready())
        .withShellEscape(shellEscape.ready());
  }
}
