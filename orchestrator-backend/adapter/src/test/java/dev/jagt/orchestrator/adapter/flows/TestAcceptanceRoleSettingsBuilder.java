package dev.jagt.orchestrator.adapter.flows;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.role.RoleSettings;
import dev.jagt.orchestrator.service.stop.ActStops;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.With;

@With
public record TestAcceptanceRoleSettingsBuilder(Path root, String jagtYml) {

  public static TestAcceptanceRoleSettingsBuilder acceptanceRoleSettings() {
    return new TestAcceptanceRoleSettingsBuilder(
        Path.of("/no-root"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
  }

  public RoleKits build(AgentRuntime agentRuntime, ActStops actStops) throws IOException {
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
    return new RoleKits(
        List.of(
            new RoleKit(
                new RoleSettings(
                    RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                    new ConfigService(
                        new ConfigSource(paths, new FakeConfigInputs()),
                        agentRuntime,
                        actStops,
                        new Checkers(new TaskChart(Composition.builtIn()))),
                    paths),
                new RoleFiles(paths, "acceptance"))));
  }
}
