package dev.jagt.orchestrator.service.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketScenariosTest {

  private static final ConfigService.ConfigFile ON =
      ConfigService.ConfigFile.defaults()
          .withRoles(Map.of("acceptance", new ConfigService.ConfigFile.RoleConfig(true, null)));

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private ConfigService.ConfigFile loaded = ON;
  private TicketScenarios scenarios;

  @BeforeEach
  void readsTheAcceptanceRoleUnderTheTempRoot() {
    when(config.load()).thenAnswer(call -> loaded);
    scenarios =
        new TicketScenarios(
            new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                config,
                new OrchestratorPaths(
                    OrchestratorProperties.defaults().withRoot(root.toString()))));
  }

  @Test
  void namesTheTicketsScenariosFileWhenAcceptanceWroteOne() throws Exception {
    Path file = root.resolve("roles/acceptance/workspace/scenarios/ABC-42.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "- WHEN a THEN b `from-ticket`\n");
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "workspace: workspace\n");

    String line = scenarios.briefingLine("ABC-42");

    assertThat(line).contains(file.toString(), "`## Acceptance criteria`");
  }

  @Test
  void namesNothingWhereTheTicketHasNoScenariosFile() throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance/workspace/scenarios"));
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "workspace: workspace\n");

    String line = scenarios.briefingLine("ABC-42");

    assertThat(line).isEmpty();
  }

  @Test
  void namesNothingWhereAcceptanceIsOff() throws Exception {
    Path file = root.resolve("roles/acceptance/workspace/scenarios/ABC-42.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "- WHEN a THEN b `from-ticket`\n");
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "workspace: workspace\n");
    loaded = ConfigService.ConfigFile.defaults();

    String line = scenarios.briefingLine("ABC-42");

    assertThat(line).isEmpty();
  }
}
