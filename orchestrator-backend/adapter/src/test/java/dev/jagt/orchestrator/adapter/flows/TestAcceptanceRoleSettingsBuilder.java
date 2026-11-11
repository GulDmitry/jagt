package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.RoleSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.With;

@With
public record TestAcceptanceRoleSettingsBuilder(Path root, String jagtYml) {

  public static TestAcceptanceRoleSettingsBuilder acceptanceRoleSettings() {
    return new TestAcceptanceRoleSettingsBuilder(
        Path.of("/no-root"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
  }

  public RoleSettings build(AgentRuntime agentRuntime, ActStops actStops) throws IOException {
    Files.writeString(root.resolve("jagt.yml"), jagtYml);
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    return new RoleSettings(
        RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
        new ConfigService(
            paths, agentRuntime, actStops, new Checkers(new TaskChart(Composition.builtIn()))),
        paths);
  }
}
