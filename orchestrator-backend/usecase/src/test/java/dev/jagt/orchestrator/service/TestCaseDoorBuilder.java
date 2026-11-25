package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionCases;
import dev.jagt.orchestrator.service.log.MissLog;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.time.Clock;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestCaseDoorBuilder(Path root) {

  public static TestCaseDoorBuilder caseDoor() {
    return new TestCaseDoorBuilder(Path.of("/state"));
  }

  public CaseDoor build(StateService state, TaskStops stops, Clock clock) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()).withData(root.toString()),
            FakeHost.machine());
    return new CaseDoor(
        new SessionCases(paths),
        new MasterCases(paths),
        state,
        new MissLog(new JsonMapper(), paths, clock),
        stops);
  }
}
