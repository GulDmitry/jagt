package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.config.RoleOptions.Flows;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.RoleSettings;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** An enabled role whose folder cannot run a check is jagt's own reach failing, never a card's. */
@Component
@RequiredArgsConstructor
public class RoleFolderReach implements Job {

  private static final String MCP = "mcp__";

  private final RoleSettings settings;
  private final Reach reach;

  @Override
  public String id() {
    return "role-folder-reach";
  }

  @Override
  public String describe() {
    return "reads whether an enabled role's folder names an environment and a runner it can use";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    String driver = settings.role().id();
    String file = settings.file();
    missing()
        .ifPresentOrElse(
            said ->
                reach.lost(
                    new ReachLost(driver, file, List.of(), said), () -> missing().isPresent()),
            () -> reach.reached(driver, file));
  }

  private Optional<String> missing() {
    RoleOptions options = settings.load();
    if (!options.enabled()) {
      return Optional.empty();
    }
    String file = settings.file();
    if (options.environments().isEmpty()) {
      return Optional.of(file + " names no environment, so no check opens");
    }
    if (!options.unread().isEmpty()) {
      return Optional.of(file + ": " + String.join("; ", options.unread()));
    }
    Optional<String> server =
        options.mcp().stream().filter(tool -> !tool.startsWith(MCP)).findFirst();
    if (server.isPresent()) {
      return Optional.of(file + ": tools.mcp " + server.get() + " names no " + MCP + " server");
    }
    Flows flows = options.flows();
    if (!FlowsRunner.DRIVERS.contains(flows.driver())) {
      return Optional.of(
          file + ": flows.driver " + flows.driver() + " is not one of " + FlowsRunner.DRIVERS);
    }
    if (flows.used() && (flows.path().isEmpty() || flows.command().isEmpty())) {
      return Optional.of(file + " names a flows driver but no flows.path or flows.command");
    }
    return Optional.empty();
  }
}
