package dev.jagt.orchestrator.startup;

import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.InstalledRoles;
import lombok.With;

@With
public record TestRoleCheckBuilder(InstalledRoles roles, OrchestratorPaths paths) {

  public static TestRoleCheckBuilder roleCheck(InstalledRoles roles, OrchestratorPaths paths) {
    return new TestRoleCheckBuilder(roles, paths);
  }

  public RoleCheck build() {
    return new RoleCheck(
        roles,
        new ConfigService(
            paths,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn()))),
        paths);
  }
}
