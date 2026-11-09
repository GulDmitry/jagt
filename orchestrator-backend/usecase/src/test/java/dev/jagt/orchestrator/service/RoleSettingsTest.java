package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.port.AgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoleSettingsTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "'environments: []'       | sonnet",
        "'model: \"\"'            | sonnet",
        "'model: \" opus \"'      | opus"
      })
  void theFoldersModelLaysOverTheShippedOneAndJagtYmlNamesNone(
      String written, String model, @TempDir Path root) throws Exception {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  roles:\n    acceptance:\n      enabled: true\n      model: haiku\n");
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

    String loaded =
        new RoleSettings(
                RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
                new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
                paths)
            .load()
            .model();

    assertThat(loaded).isEqualTo(model);
  }

  @Test
  void anEnvironmentWrittenIntoTheFolderHoldsAtTheNextLoadWithNoRestart(@TempDir Path root)
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
    RoleSettings settings =
        new RoleSettings(
            RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist")),
            new ConfigService(paths, mock(AgentRuntime.class), mock(ActStops.class)),
            paths);
    settings.load();
    Files.writeString(
        root.resolve("roles/acceptance/role.yml"),
        "environments: [{url: https://stage.example.com, landed: https://stage.example.com/up}]\n");

    RoleOptions loaded = settings.load();

    assertThat(loaded.environments())
        .containsExactly(
            new RoleOptions.Environment(
                "https://stage.example.com", "https://stage.example.com/up"));
  }
}
