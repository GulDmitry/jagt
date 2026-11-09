package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketScenariosTest {

  private static final ConfigService.ConfigFile ON =
      ConfigService.ConfigFile.defaults()
          .withRoles(Map.of("acceptance", new ConfigService.ConfigFile.RoleConfig(true, null)));

  @Test
  void namesTheTicketsScenariosFileWhenAcceptanceWroteOne(@TempDir Path root) throws Exception {
    Path file = root.resolve("roles/acceptance/workspace/scenarios/ABC-42.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "- WHEN a THEN b `from-ticket`\n");
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "workspace: workspace\n");
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ON);
    TicketScenarios scenarios =
        new TicketScenarios(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                config,
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()))));

    String line = scenarios.briefingLine("ABC-42");

    assertThat(line).contains(file.toString(), "`## Acceptance criteria`");
  }

  @Test
  void namesNothingWhereTheTicketHasNoScenariosFile(@TempDir Path root) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance/workspace/scenarios"));
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "workspace: workspace\n");
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ON);
    TicketScenarios scenarios =
        new TicketScenarios(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                config,
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()))));

    String line = scenarios.briefingLine("ABC-42");

    assertThat(line).isEmpty();
  }

  @Test
  void namesNothingWhereAcceptanceIsOff(@TempDir Path root) throws Exception {
    Path file = root.resolve("roles/acceptance/workspace/scenarios/ABC-42.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "- WHEN a THEN b `from-ticket`\n");
    ConfigService config = mock(ConfigService.class);
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "workspace: workspace\n");
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    TicketScenarios scenarios =
        new TicketScenarios(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                config,
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()))));

    String line = scenarios.briefingLine("ABC-42");

    assertThat(line).isEmpty();
  }
}
