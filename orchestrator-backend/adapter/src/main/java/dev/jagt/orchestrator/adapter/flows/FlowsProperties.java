package dev.jagt.orchestrator.adapter.flows;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The flows driver, the repository holding the flows and the command that runs them. */
@ConfigurationProperties(prefix = "orchestrator.roles.qa.flows")
public record FlowsProperties(String driver, String path, List<String> command, Duration timeout) {

  public FlowsProperties {
    driver = driver == null || driver.isBlank() ? "none" : driver.strip();
    path = path == null ? "" : path.strip();
    command = command == null ? List.of() : List.copyOf(command);
    timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
  }
}
