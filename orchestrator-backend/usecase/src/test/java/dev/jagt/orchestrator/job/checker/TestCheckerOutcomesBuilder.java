package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import java.nio.file.Path;
import lombok.With;

@With
public record TestCheckerOutcomesBuilder(Path roleFile) {

  public static TestCheckerOutcomesBuilder checkerOutcomes() {
    return new TestCheckerOutcomesBuilder(Path.of("../../roles/acceptance/role.yml.dist"));
  }

  public CheckerOutcomes build(
      StepRelay relay, FlowReports flow, TaskStops stops, StateService state) {
    return new CheckerOutcomes(RoleDefinitions.fromFile(roleFile), relay, flow, stops, state);
  }
}
