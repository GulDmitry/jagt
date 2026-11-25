package dev.jagt.orchestrator.config.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.testing.FakeConfigInputs;
import dev.jagt.orchestrator.testing.FakeHost;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSourceTest {

  @TempDir Path root;

  private OrchestratorPaths paths;

  @BeforeEach
  void wirePaths() {
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withConfigFile(root.resolve("jagt.yml").toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
  }

  @Test
  void keyReadsTheVariableAndNoLogLineCarriesIt() throws IOException {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  tracker:\n    token: ${TOKEN}\n");
    FakeConfigInputs inputs = new FakeConfigInputs().setting("TOKEN", "s3cret-value");

    try (FakeLogCapture log = new FakeLogCapture(ConfigSource.class)) {
      Object section = new ConfigSource(paths, inputs).section();

      assertThat(section).isEqualTo(Map.of("tracker", Map.of("token", "s3cret-value")));
      assertThat(log.events()).noneMatch(event -> event.getFormattedMessage().contains("s3cret"));
      assertThat(log.events())
          .noneMatch(event -> event.getKeyValuePairs().toString().contains("s3cret"));
    }
  }

  @Test
  void keyReadsTheFileWithItsTrailingNewlineCut() throws IOException {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  tracker:\n    token: ${file:/run/secrets/x}\n");
    FakeConfigInputs inputs = new FakeConfigInputs().holding("/run/secrets/x", "from-file\n");

    Object section = new ConfigSource(paths, inputs).section();

    assertThat(section).isEqualTo(Map.of("tracker", Map.of("token", "from-file")));
  }

  @Test
  void keyReadsTheDefaultWhenTheVariableIsUnset() throws IOException {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  conflictStallMinutes: ${STALL:45}\n");

    Object section = new ConfigSource(paths, new FakeConfigInputs()).section();

    assertThat(section).isEqualTo(Map.of("conflictStallMinutes", "45"));
  }

  @Test
  void variableOverridesTheKeyTheFileWrites() throws IOException {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  conflictStallMinutes: 30\n");
    FakeConfigInputs inputs =
        new FakeConfigInputs().setting("JAGT__orchestrator__conflictStallMinutes", "90");

    Object section = new ConfigSource(paths, inputs).section();

    assertThat(section).isEqualTo(Map.of("conflictStallMinutes", "90"));
  }

  @Test
  void answersNoSectionWhenThereIsNoConfigFile() {
    Object section = new ConfigSource(paths, new FakeConfigInputs()).section();

    assertThat(section).isNull();
  }

  @Test
  void appliesAnEnvironmentOverrideWhenThereIsNoConfigFile() {
    FakeConfigInputs inputs =
        new FakeConfigInputs().setting("JAGT__orchestrator__keepAwake", "false");

    Object section = new ConfigSource(paths, inputs).section();

    assertThat(section).isEqualTo(Map.of("keepAwake", "false"));
  }

  @Test
  void refusesTheStartNamingTheKeyAndTheVariableWhenItIsUnsetWithoutDefault() throws IOException {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  tracker:\n    token: ${TOKEN}\n");
    ConfigSource source = new ConfigSource(paths, new FakeConfigInputs());

    assertThatThrownBy(source::section)
        .isInstanceOfSatisfying(
            ConfigValueRefused.class,
            refused -> {
              assertThat(refused.getKey()).isEqualTo("orchestrator.tracker.token");
              assertThat(refused.getInput()).isEqualTo("variable TOKEN");
            });
  }

  @Test
  void refusesTheStartNamingTheKeyAndTheFileWhenItCannotBeRead() throws IOException {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  tracker:\n    token: ${file:/run/secrets/none}\n");
    ConfigSource source = new ConfigSource(paths, new FakeConfigInputs());

    assertThatThrownBy(source::section)
        .isInstanceOfSatisfying(
            ConfigValueRefused.class,
            refused -> {
              assertThat(refused.getKey()).isEqualTo("orchestrator.tracker.token");
              assertThat(refused.getInput()).isEqualTo("file:/run/secrets/none");
            });
  }

  @Test
  void loadsAsBeforeWhenNoPlaceholderAndNoOverrideIsThere() throws IOException {
    Files.writeString(
        root.resolve("jagt.yml"),
        "orchestrator:\n  conflictStallMinutes: 30\n  tracker:\n    mode: off\n");
    FakeConfigInputs inputs = new FakeConfigInputs().setting("PATH", "/usr/bin");

    Object section = new ConfigSource(paths, inputs).section();

    assertThat(section)
        .isEqualTo(Map.of("conflictStallMinutes", 30, "tracker", Map.of("mode", "off")));
  }

  @Test
  void rereadsWhenVariableTheFileNamesChanges() throws IOException {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  tracker:\n    token: ${TOKEN}\n");
    FakeConfigInputs inputs = new FakeConfigInputs().setting("TOKEN", "first");
    ConfigSource source = new ConfigSource(paths, inputs);
    source.section();

    inputs.setting("TOKEN", "second");

    assertThat(source.section()).isEqualTo(Map.of("tracker", Map.of("token", "second")));
  }

  @Test
  void rereadsWhenJagtVariableAppears() throws IOException {
    Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  conflictStallMinutes: 30\n");
    FakeConfigInputs inputs = new FakeConfigInputs();
    ConfigSource source = new ConfigSource(paths, inputs);
    source.section();

    inputs.setting("JAGT__orchestrator__conflictStallMinutes", "90");

    assertThat(source.section()).isEqualTo(Map.of("conflictStallMinutes", "90"));
  }

  @Test
  void rereadsWhenFileTheConfigNamesChanges() throws IOException {
    Files.writeString(
        root.resolve("jagt.yml"), "orchestrator:\n  tracker:\n    token: ${file:/run/secrets/x}\n");
    FakeConfigInputs inputs = new FakeConfigInputs().holding("/run/secrets/x", "first");
    ConfigSource source = new ConfigSource(paths, inputs);
    source.section();

    inputs.holding("/run/secrets/x", "second");

    assertThat(source.section()).isEqualTo(Map.of("tracker", Map.of("token", "second")));
  }
}
