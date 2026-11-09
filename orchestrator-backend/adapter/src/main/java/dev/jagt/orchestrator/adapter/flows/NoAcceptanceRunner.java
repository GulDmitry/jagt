package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.port.AcceptanceRunner;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "!'${orchestrator.roles.acceptance.flows.driver:none}'.equals('playwright')")
public class NoAcceptanceRunner implements AcceptanceRunner {

  @Override
  public List<FlowResult> run(Path worktree, List<String> flows, String environment) {
    return Unrun.all(flows, "no flows driver is configured");
  }
}
