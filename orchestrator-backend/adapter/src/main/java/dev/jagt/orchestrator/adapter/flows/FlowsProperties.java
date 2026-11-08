package dev.jagt.orchestrator.adapter.flows;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The flows repository, the command that runs it and the base URLs it may be run against. */
@ConfigurationProperties(prefix = "orchestrator.qa.flows")
public record FlowsProperties(
    String path, List<String> command, List<String> environments, Duration timeout) {

  public FlowsProperties {
    path = path == null ? "" : path.strip();
    command = command == null ? List.of() : List.copyOf(command);
    environments = environments == null ? List.of() : List.copyOf(environments);
    timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
  }

  public boolean allows(String environment) {
    return environments.stream()
        .anyMatch(listed -> withoutTrailingSlash(listed).equals(withoutTrailingSlash(environment)));
  }

  private static String withoutTrailingSlash(String url) {
    String stripped = url.strip();
    return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
  }
}
