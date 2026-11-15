package dev.jagt.orchestrator.job.checker.wiring;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.job.checker.CheckerOutcomes;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a finished check does to a task, shared by every checker. */
@Component
@RequiredArgsConstructor
public class CheckerActs {

  private final StepRelay relay;
  private final FlowReports reports;
  private final TaskStops stops;
  private final StateService state;

  public CheckerOutcomes outcomes(RoleKit kit) {
    return new CheckerOutcomes(kit.definition(), relay, reports, stops, state);
  }
}
