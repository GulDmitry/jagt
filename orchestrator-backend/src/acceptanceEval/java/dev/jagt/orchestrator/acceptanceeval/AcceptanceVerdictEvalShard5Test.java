package dev.jagt.orchestrator.acceptanceeval;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.job.CheckerFlows;
import dev.jagt.orchestrator.job.CheckerPrompts;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.UsageTracker;
import org.springframework.beans.factory.annotation.Autowired;

class AcceptanceVerdictEvalShard5Test extends AcceptanceVerdictEvalTest {

  @Autowired
  AcceptanceVerdictEvalShard5Test(
      CheckerReader reader,
      CheckerPrompts prompts,
      CheckerFlows checkerFlows,
      RoleDefinition acceptanceDefinition,
      UsageTracker usage) {
    super(reader, prompts, checkerFlows, acceptanceDefinition, usage);
  }
}
