package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
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
