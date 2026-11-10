package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfigReferenceTest {

  private static final List<Path> ROOTS =
      List.of(
          Path.of("usecase/src/main/java"),
          Path.of("adapter/src/main/java"),
          Path.of("surface/src/main/java"),
          Path.of("src/main/java"));

  @Test
  void jagtYmlDistDescribesEveryKeyTheBackendReads() {
    String dist = SourceRoots.read(Path.of("../jagt.yml.dist"));
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
            .map(
                match ->
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

}
