package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.config.role.RoleOptions;
import java.util.stream.Collectors;

/** The {@code role.yml} an acceptance eval run installs: one environment and one flows driver. */
public final class AcceptanceRoleYml {

  private AcceptanceRoleYml() {}

  public static String of(String environment, String landed, RoleOptions.Flows flows) {
    String command =
        flows.command().stream().map(word -> "\"" + word + "\"").collect(Collectors.joining(", "));
    return """
    environments: [{url: "%s", landed: "%s"}]
    flows: {driver: "%s", path: "%s", command: [%s]}
    """
        .formatted(environment, landed, flows.driver(), flows.path(), command);
  }
}
