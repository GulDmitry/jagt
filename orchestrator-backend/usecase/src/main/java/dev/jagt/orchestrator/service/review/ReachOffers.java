package dev.jagt.orchestrator.service.review;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.port.reach.ScenarioReach;
import dev.jagt.orchestrator.port.reach.ShellEscapeReach;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What the configuration lets jagt offer on any card. */
@Component
@RequiredArgsConstructor
public class ReachOffers {

  private final ScenarioReach scenarios;
  private final ShellEscapeReach shellEscape;

  public Facts.Reach facts() {
    return Facts.Reach.defaults()
        .withScenarios(scenarios.ready())
        .withShellEscape(shellEscape.ready());
  }
}
