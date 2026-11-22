package dev.jagt.orchestrator.startup.role;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Keys an older jagt.yml wrote for the acceptance role, which nothing reads any more. */
@Component
@RequiredArgsConstructor
public class AcceptanceConfigCheck implements StartupCheck {

  private static final String RETIRED = "qa";
  private static final List<String> FOLDER_OPTIONS = List.of("environments", "flows", "model");

  private final ConfigService configService;
  private final OrchestratorPaths paths;

  @Override
  public List<Problem> problems() {
    ConfigFile config;
    try {
      config = configService.load();
    } catch (RuntimeException unreadable) {
      return List.of();
    }
    return Stream.of(retiredKey(), retiredRole(config), folderOptions())
        .flatMap(List::stream)
        .toList();
  }

  private List<Problem> retiredKey() {
    return configService.declaredKeys().contains(RETIRED)
        ? List.of(retired("orchestrator." + RETIRED))
        : List.of();
  }

  private List<Problem> retiredRole(ConfigFile config) {
    Map<String, ?> roles = config.sessions().roles();
    return roles != null && roles.containsKey(RETIRED)
        ? List.of(retired("orchestrator.roles." + RETIRED))
        : List.of();
  }

  private Problem retired(String key) {
    return new Problem(
        key,
        key
            + " is no longer read: the role is called `acceptance`; write it under"
            + " `roles.acceptance` in "
            + paths.configFile()
            + ".");
  }

  private List<Problem> folderOptions() {
    String role = "roles." + Checkers.ACCEPTANCE;
    return configService.declaredKeys("roles", Checkers.ACCEPTANCE).stream()
        .filter(FOLDER_OPTIONS::contains)
        .sorted()
        .map(key ->
            new Problem(
                "orchestrator." + role + "." + key,
                "orchestrator."
                    + role
                    + "."
                    + key
                    + " is no longer read: write `"
                    + key
                    + "` in roles/"
                    + Checkers.ACCEPTANCE
                    + "/role.yml, and keep only `enabled` in "
                    + paths.configFile()
                    + "."))
        .toList();
  }
}
