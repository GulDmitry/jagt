package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleConfigOriginsTest {

  private final ConfigService config = mock(ConfigService.class);

  @Test
  void enabledComesFromJagtYmlAndEnvironmentsFromTheHumanCopy(@TempDir Path root) throws Exception {
    install(root, "environments: [{url: https://stage.example.com}]\n");
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withRoles(Map.of("acceptance", new RoleConfig(true, "opus"))));

    List<String> lines = logged(root);

    assertThat(lines)
        .contains(
            "acceptance enabled true jagt.yml",
            "acceptance model sonnet role.yml.dist",
            "acceptance environments 1 role.yml");
  }

  @Test
  void modelComesFromTheHumanCopyWhenJagtYmlWritesNone(@TempDir Path root) throws Exception {
    install(root, "model: haiku\n");
    when(config.load()).thenReturn(ConfigFile.defaults());

    List<String> lines = logged(root);

    assertThat(lines).contains("acceptance model haiku role.yml");
  }

  @Test
  void modelComesFromTheShippedDistWhenNeitherWritesOne(@TempDir Path root) throws Exception {
    install(root, "");
    when(config.load()).thenReturn(ConfigFile.defaults());

    List<String> lines = logged(root);

    assertThat(lines).contains("acceptance model sonnet role.yml.dist");
  }

  @Test
  void modelComesFromTheShippedDistWhenTheHumanCopyCannotBeRead(@TempDir Path root)
      throws Exception {
    install(root, "model: [haiku\n");
    when(config.load()).thenReturn(ConfigFile.defaults());

    List<String> lines = logged(root);

    assertThat(lines).contains("acceptance model sonnet role.yml.dist");
  }

  @Test
  void enabledAndEnvironmentsAreDefaultsWhenJagtYmlWritesNone(@TempDir Path root) throws Exception {
    install(root, "");
    when(config.load()).thenReturn(ConfigFile.defaults());

    List<String> lines = logged(root);

    assertThat(lines)
        .contains("acceptance enabled false default", "acceptance environments 0 default");
  }

  @Test
  void everyValueIsDefaultedWhereNoJagtYmlExistsYet(@TempDir Path root) throws Exception {
    install(root, "");
    Files.delete(root.resolve("jagt.yml"));

    List<String> lines = logged(root);

    assertThat(lines)
        .contains("acceptance enabled false default", "acceptance environments 0 default");
  }

  private static void install(Path root, @Nullable String humanCopy) throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(root.resolve("jagt.yml"), "");
    if (humanCopy != null) {
      Files.writeString(root.resolve("roles/acceptance/role.yml"), humanCopy);
    }
  }

  private List<String> logged(Path root) {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    RoleConfigOrigins origins = new RoleConfigOrigins(new InstalledRoles(paths), config, paths);
    try (FakeLogCapture appender = new FakeLogCapture(RoleConfigOrigins.class)) {
      origins.logAtStart();

      List<ILoggingEvent> events = appender.events();
      assertThat(events).extracting(ILoggingEvent::getMessage).containsOnly("role config");
      return events.stream()
          .map(event ->
              event.getKeyValuePairs().stream()
                  .map(pair -> String.valueOf(pair.value))
                  .reduce((a, b) -> a + " " + b)
                  .orElse(""))
          .toList();
    }
  }
}
