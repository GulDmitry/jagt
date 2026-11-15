package dev.jagt.orchestrator.acceptanceeval;

import dev.jagt.orchestrator.job.checker.wiring.CheckerReaders;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import org.springframework.beans.factory.annotation.Autowired;

class AcceptanceVerdictEvalShard5Test extends AcceptanceVerdictEvalTest {

  @Autowired
  AcceptanceVerdictEvalShard5Test(
      CheckerReader reader, RoleKits kits, CheckerReaders readers, UsageTracker usage) {
    super(reader, kits, readers, usage);
  }
}
