package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.config.role.RoleOptions.Flows;
import dev.jagt.orchestrator.port.worktree.AcceptanceRunner;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.role.RoleSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The driver is read from the role's folder on every run, so an edit needs no restart. */
@Component
@RequiredArgsConstructor
public class FlowsRunner implements AcceptanceRunner {

  static final String PLAYWRIGHT = "playwright";
  static final List<String> DRIVERS = List.of("none", PLAYWRIGHT);

  private final RoleKits kits;
  private final PlaywrightRunner playwright;

  @Override
  public List<FlowResult> run(String role, Path worktree, List<String> flows, String environment) {
    Optional<RoleSettings> settings = kits.find(role).map(RoleKit::settings);
    if (settings.isEmpty()) {
      return Unrun.all(flows, "no role " + role + " is installed");
    }
    Flows named = settings.get().load().flows();
    return PLAYWRIGHT.equals(named.driver())
        ? playwright.run(named, worktree, flows, environment)
        : Unrun.all(flows, "no flows driver is named in " + settings.get().file());
  }
}
