package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.AgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoleSettingsTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "'enabled: true'          | sonnet",
        "'model: \"\"'            | sonnet",
        "'model: \" opus \"'      | opus"
      })
  void theRoleYmlsModelIsTheBottomLayerUnderJagtYml(
      String written, String model, @TempDir Path root) throws Exception {
    Path configFile = root.resolve("jagt.yml");
    Files.writeString(
        configFile, "orchestrator:\n  roles:\n    acceptance:\n      " + written + "\n");
    OrchestratorProperties properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withConfigFile(configFile.toString())
            .withStateFile(root.resolve("state.json").toString());
    ConfigService config =
        new ConfigService(
            new OrchestratorPaths(properties), mock(AgentRuntime.class), mock(ActStops.class));

    String loaded =
        new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")), config)
            .load()
            .modelOrInherited();

    assertThat(loaded).isEqualTo(model);
  }
}
