package dev.jagt.orchestrator.service.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.source.ConfigSource;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeptNumbers;
import dev.jagt.orchestrator.service.master.ActStops;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleBeansTest {

  @Test
  void acceptanceJobsStandIdleWhenNoAcceptanceRoleIsInstalledEvenWhereJagtYmlTurnsItOn(
      @TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    ConfigService config =
        new ConfigService(
            new ConfigSource(paths, new FakeConfigInputs()),
            mock(AgentRuntime.class),
            mock(ActStops.class),
            new Checkers(new TaskChart(Composition.builtIn())),
            new KeptNumbers(new ReachLedger()));

    RoleKits kits =
        new RoleBeans()
            .roleKits(
                new InstalledRoles(paths),
                config,
                paths,
                List.of(new RoleKits.Expected(Checkers.ACCEPTANCE)));

    assertThat(kits.find(Checkers.ACCEPTANCE).orElseThrow().settings().load().enabled()).isFalse();
  }

  @Test
  void everyInstalledCheckerHasItsKitLookedUpByItsId(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/audit"));
    Files.copy(
        Path.of("src/test/resources/checker-fixture/roles/audit/role.yml"),
        root.resolve("roles/audit/role.yml"));
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleKits kits =
        new RoleBeans()
            .roleKits(new InstalledRoles(paths), mock(ConfigService.class), paths, List.of());

    assertThat(kits.find("audit").orElseThrow().definition().checking().readSteps())
        .containsExactly("audit", "audit-again", "audit-decline");
  }
}
