package dev.jagt.orchestrator.service.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.RoleConfig;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RoleConfigOriginsTest {

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private RoleConfigOrigins origins;

  @BeforeEach
  void shipsTheAcceptanceRoleWithAnEmptyJagtYml() throws Exception {
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of("../../roles/acceptance/role.yml.dist"),
        root.resolve("roles/acceptance/role.yml.dist"));
    Files.writeString(root.resolve("jagt.yml"), "");
    Files.writeString(root.resolve("roles/acceptance/role.yml"), "");
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    origins = new RoleConfigOrigins(new InstalledRoles(paths), config, paths);
  }

  static Stream<Arguments> origins() {
    return Stream.of(
        Arguments.of(
            "enabled comes from jagt.yml and environments from the human copy",
            "environments: [{url: https://stage.example.com}]\n",
            ConfigFile.defaults().withRoles(Map.of("acceptance", new RoleConfig(true, "opus"))),
            List.of(
                "acceptance enabled true jagt.yml",
                "acceptance model sonnet role.yml.dist",
                "acceptance environments 1 role.yml")),
        Arguments.of(
            "model comes from the human copy when jagt.yml writes none",
            "model: haiku\n",
            ConfigFile.defaults(),
            List.of("acceptance model haiku role.yml")),
        Arguments.of(
            "model comes from the shipped dist when neither writes one",
            "",
            ConfigFile.defaults(),
            List.of("acceptance model sonnet role.yml.dist")),
        Arguments.of(
            "model comes from the shipped dist when the human copy cannot be read",
            "model: [haiku\n",
            ConfigFile.defaults(),
            List.of("acceptance model sonnet role.yml.dist")),
        Arguments.of(
            "enabled and environments are defaults when jagt.yml writes none",
            "",
            ConfigFile.defaults(),
            List.of("acceptance enabled false default", "acceptance environments 0 default")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("origins")
  void logsEachRoleSettingWithTheFileItCameFrom(
      String name, String humanCopy, ConfigFile file, List<String> expected) throws Exception {
    when(config.load()).thenReturn(file);
    Files.writeString(root.resolve("roles/acceptance/role.yml"), humanCopy);

    try (FakeLogCapture appender = new FakeLogCapture(RoleConfigOrigins.class)) {
      origins.logAtStart();

      assertThat(appender.events())
          .extracting(event -> event.getMessage())
          .containsOnly("role config");
      assertThat(appender.events())
          .extracting(event ->
              event.getKeyValuePairs().stream()
                  .map(pair -> String.valueOf(pair.value))
                  .collect(Collectors.joining(" ")))
          .containsAll(expected);
    }
  }

  @Test
  void everyValueIsDefaultedWhereNoJagtYmlExistsYet() throws Exception {
    Files.delete(root.resolve("jagt.yml"));

    try (FakeLogCapture appender = new FakeLogCapture(RoleConfigOrigins.class)) {
      origins.logAtStart();

      assertThat(appender.events())
          .extracting(ILoggingEvent::getMessage)
          .containsOnly("role config");
      assertThat(appender.events())
          .extracting(event ->
              event.getKeyValuePairs().stream()
                  .map(pair -> String.valueOf(pair.value))
                  .reduce((first, second) -> first + " " + second)
                  .orElse(""))
          .contains("acceptance enabled false default", "acceptance environments 0 default");
    }
  }
}
