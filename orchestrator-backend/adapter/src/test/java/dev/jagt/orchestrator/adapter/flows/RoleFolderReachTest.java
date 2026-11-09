package dev.jagt.orchestrator.adapter.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.service.ActStops;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.RoleSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoleFolderReachTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "'tools: {git: true}' | names no environment",
        "'{environments: [{url: a}], flows: {driver: selenium}}' | is not one of",
        "'{environments: [{url: a}], flows: {driver: playwright}}' | no flows.path",
        "'{environments: [{url: a}], flows: {driver: playwright, path: /f}}' | no flows.path",
        "'{environments: [{url: a}], flows: {driver: playwright, command: [x]}}' | no flows.path",
        "'{environments: [{url: a}], tools: {mcp: [browser]}}' | tools.mcp browser",
        "'{environments: [{url: a}], flows: {timeout: soon}}' | flows.timeout soon"
      })
  void anEnabledRoleWhoseFolderCannotRunACheckIsOneMarkOnTheBoardsHead(
      String written, String said, @TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(root.resolve("roles/acceptance/role.yml"), written + "\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    ReachLedger reach = new ReachLedger();

    new RoleFolderReach(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                paths),
            reach)
        .run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(
            lost -> {
              assertThat(lost.host()).isEqualTo("roles/acceptance/role.yml");
              assertThat(lost.said()).contains(said);
            });
  }

  @Test
  void theMarkEndsOnTheFirstReadAfterTheFolderNamesAnEnvironment(@TempDir Path root)
      throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    ReachLedger reach = new ReachLedger();
    new RoleFolderReach(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                paths),
            reach)
        .run();
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"), "environments: [{url: https://a.test}]\n");

    List<ReachLost> standing = reach.standing();

    assertThat(standing).isEmpty();
  }

  @Test
  void aFolderNamingAnEnvironmentAndAnMcpServerRaisesNoMark(@TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "{environments: [{url: a}], tools: {mcp: [mcp__logs]}}\n");
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    ReachLedger reach = new ReachLedger();

    new RoleFolderReach(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                paths),
            reach)
        .run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void aRoleTurnedOffRaisesNoMarkWhateverItsFolderLacks(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString()));
    ReachLedger reach = new ReachLedger();

    new RoleFolderReach(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                paths),
            reach)
        .run();

    assertThat(reach.standing()).isEmpty();
  }
}
