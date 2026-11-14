package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import org.springframework.beans.factory.annotation.Autowired;

class MasterVerdictEvalShard0Test extends MasterVerdictEvalTest {

  @Autowired
  MasterVerdictEvalShard0Test(
      StateService stateService,
      MasterReview reviews,
      UsageTracker usage,
      MasterDecisions decisions) {
    super(stateService, reviews, usage, decisions);
  }
}
