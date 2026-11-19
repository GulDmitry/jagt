package dev.jagt.orchestrator.job.verdict;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What a verdict does: a plan that holds is the plan verdicts', every other one the round's. */
@Service
@RequiredArgsConstructor
public class MasterRuling {

  private final MasterVerdicts verdicts;
  private final MasterPlanVerdicts plans;

  /** Whether the verdict moved anything. */
  public boolean act(
      String taskId,
      TaskState task,
      MasterReview.Verdict verdict,
      ConfigService.ConfigFile.ArbiterConfig config) {
    boolean ready = verdict.kind() == MasterReview.Kind.READY;
    if (ready && FlowRules.holdsPlan(task.status())) {
      return plans.holds(taskId, task, verdict, config);
    }
    return verdicts.act(taskId, task, verdict, config);
  }
}
