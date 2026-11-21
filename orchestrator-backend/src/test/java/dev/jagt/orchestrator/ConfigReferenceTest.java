package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.dataformat.yaml.YAMLMapper;

class ConfigReferenceTest {

  private static final List<Path> ROOTS =
      List.of(
          Path.of("usecase/src/main/java"),
          Path.of("adapter/src/main/java"),
          Path.of("surface/src/main/java"),
          Path.of("src/main/java"));

  @Test
  void jagtYmlDistDescribesEveryKeyTheBackendReads() {
    String dist = SourceRoots.read(Path.of("../jagt.desktop.yml.dist"));
    Pattern named = Pattern.compile("[\"{]orchestrator\\.([a-z][a-z-]*)");
    Stream<String> bound =
        Stream.of(
                OrchestratorProperties.class, ToolchainProperties.class, AssistantProperties.class)
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(RecordComponent::getName);
    Stream<String> looked =
        ROOTS.stream()
            .flatMap(SourceRoots::javaFilesUnder)
            .flatMap(path -> named.matcher(SourceRoots.read(path)).results())
            .map(match ->
                Pattern.compile("-([a-z])")
                    .matcher(match.group(1))
                    .replaceAll(letter -> letter.group(1).toUpperCase(Locale.ROOT)));

    assertThat(
            Stream.concat(bound, looked)
                .distinct()
                .filter(
                    key -> !Pattern.compile("(?m)^\\s*#?\\s*" + key + ":").matcher(dist).find()))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"../jagt.desktop.yml.dist", "../jagt.server.yml.dist"})
  void shippedConfigurationFilesValidateAgainstTheSchemaOfTheirRecords(String file)
      throws IOException {
    assertThat(
            JagtYmlSchema.compiled()
                .validate(new YAMLMapper().readTree(Files.readString(Path.of(file)))))
        .isEmpty();
  }

  @Test
  void schemaRejectsValueOfWrongType() {
    assertThat(
            JagtYmlSchema.compiled()
                .validate(
                    new YAMLMapper()
                        .readTree("orchestrator:\n  usageLimits:\n    enabled: nope\n")))
        .isNotEmpty();
  }

  @Test
  void schemaRejectsKeyNoRecordHolds() {
    assertThat(
            JagtYmlSchema.compiled()
                .validate(new YAMLMapper().readTree("orchestrator:\n  agent:\n    noSuchKey: 1\n")))
        .isNotEmpty();
  }

  @Test
  void schemaTakesProjectsAndRolesOnlyTheConfigFileHolds() {
    assertThat(
            JagtYmlSchema.compiled()
                .validate(
                    new YAMLMapper()
                        .readTree(
                            """
                            orchestrator:
                              projects:
                                widgets:
                                  path: /w
                              roles:
                                master:
                                  mode: act
                            """)))
        .isEmpty();
  }
}
