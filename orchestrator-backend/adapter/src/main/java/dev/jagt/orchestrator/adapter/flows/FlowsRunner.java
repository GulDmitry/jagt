package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.config.RoleOptions.Flows;
import dev.jagt.orchestrator.port.AcceptanceRunner;
import dev.jagt.orchestrator.service.RoleSettings;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The driver is read from the role's folder on every run, so an edit needs no restart. */
@Component
@RequiredArgsConstructor
public class FlowsRunner implements AcceptanceRunner {

  static final String PLAYWRIGHT = "playwright";
  static final List<String> DRIVERS = List.of("none", PLAYWRIGHT);

  private final RoleSettings settings;
  private final PlaywrightRunner playwright;

  @Override
  public List<FlowResult> run(Path worktree, List<String> flows, String environment) {
    Flows named = settings.load().flows();
    return PLAYWRIGHT.equals(named.driver())
        ? playwright.run(named, worktree, flows, environment)
        : Unrun.all(flows, "no flows driver is named in " + settings.file());
  }
}
