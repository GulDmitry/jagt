package dev.jagt.orchestrator.adapter.flows;

import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.RoleSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AcceptanceFolders {

  static final String ENABLED = "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n";

  private AcceptanceFolders() {}

  static OrchestratorPaths folder(Path root, String jagtYml) throws IOException {
    Files.writeString(root.resolve("jagt.yml"), jagtYml);
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    return new OrchestratorPaths(
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withConfigFile(root.resolve("jagt.yml").toString()));
  }

  static RoleSettings roleSettings(OrchestratorPaths paths) {
    return new RoleSettings(
        RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
        new ConfigService(
            paths,
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn()))),
        paths);
  }

  static ReachLedger readOver(OrchestratorPaths paths) {
    ReachLedger reach = new ReachLedger();
    new RoleFolderReach(roleSettings(paths), reach).run();
    return reach;
  }
}
