package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterReview;
import org.springframework.beans.factory.annotation.Autowired;

class MasterVerdictEvalShard5Test extends MasterVerdictEvalTest {

  @Autowired
  MasterVerdictEvalShard5Test(
      StateService stateService,
      MasterReview reviews,
      UsageTracker usage,
      MasterDecisions decisions) {
    super(stateService, reviews, usage, decisions);
  }
}
