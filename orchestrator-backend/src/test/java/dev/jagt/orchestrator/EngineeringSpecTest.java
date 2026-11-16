package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EngineeringSpecTest {

  private static final Pattern NAMED_TEST = Pattern.compile("`([A-Z][A-Za-z0-9]*Test)`");

  @ParameterizedTest
  @ValueSource(strings = {"../openspec/specs/engineering/spec.md", "../docs/practices.md"})
  void everyTestClassItNamesExists(String document) {
    List<String> existing =
        SourceRoots.javaFiles()
            .map(source -> source.getFileName().toString().replace(".java", ""))
            .toList();

    List<String> missing =
        NAMED_TEST
            .matcher(DocFiles.read(Path.of(document)))
            .results()
            .map(match -> match.group(1))
            .distinct()
            .filter(name -> !existing.contains(name))
            .toList();

    assertThat(missing).isEmpty();
  }

  @Test
  void theCapabilityNamesTestClasses() {
    long named =
        NAMED_TEST
            .matcher(DocFiles.read(Path.of("../openspec/specs/engineering/spec.md")))
            .results()
            .count();

    assertThat(named).isGreaterThan(20);
  }
}
