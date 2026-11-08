package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlowsCheck implements StartupCheck {

  private static final String KEY = "orchestrator.roles.qa.flows.driver";
  private static final List<String> DRIVERS = List.of("none", "playwright");

  private final FlowsProperties properties;

  @Override
  public List<Problem> problems() {
    return DRIVERS.contains(properties.driver())
        ? List.of()
        : List.of(
            new Problem(KEY, KEY + ": '" + properties.driver() + "' is not one of " + DRIVERS));
  }

  @Override
  public boolean machine() {
    return false;
  }
}
