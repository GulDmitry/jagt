package dev.jagt.orchestrator.acceptanceeval;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.job.checker.CheckerFlows;
import dev.jagt.orchestrator.job.checker.CheckerPrompts;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import org.springframework.beans.factory.annotation.Autowired;

class AcceptanceVerdictEvalShard3Test extends AcceptanceVerdictEvalTest {

  @Autowired
  AcceptanceVerdictEvalShard3Test(
      CheckerReader reader,
      CheckerPrompts prompts,
      CheckerFlows checkerFlows,
      RoleDefinition acceptanceDefinition,
      UsageTracker usage) {
    super(reader, prompts, checkerFlows, acceptanceDefinition, usage);
  }
}
