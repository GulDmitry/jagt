package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ComplexityCeilingsTest {

  private static final Map<String, Integer> STORED =
      JsonMapper.builder()
          .build()
          .readValue(
              SourceRoots.read(Path.of("src/test/resources/complexity-ceilings.json")),
              new TypeReference<>() {});

  @Test
  void storesOneCountForEveryKindAndEverySuiteContextCeilingAndNothingElse() {
    assertThat(STORED.keySet())
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(
                    Arrays.stream(CeilingKind.values()).map(CeilingKind::key),
                    Stream.of("contextsTest", "contextsBoardTest", "contextsE2eTest"))
                .toList());
  }

  @ParameterizedTest
  @EnumSource(CeilingKind.class)
  void kindStaysWithinItsStoredCountUnlessDecisionRaisesIt(CeilingKind kind) {
    List<String> items = kind.items();

    assertThat(items)
        .as(
            "%s is stored at %d, counted %d: %s",
            kind.key(), STORED.get(kind.key()), items.size(), items)
        .hasSizeLessThanOrEqualTo(Objects.requireNonNull(STORED.get(kind.key())));
  }

  @ParameterizedTest
  @EnumSource(CeilingKind.class)
  void storedCountFallsWithItsKindSoTheRatchetTightens(CeilingKind kind) {
    List<String> items = kind.items();

    assertThat(items)
        .as(
            "%s counted %d, stored %d: lower the stored number in complexity-ceilings.json",
            kind.key(), items.size(), STORED.get(kind.key()))
        .hasSizeGreaterThanOrEqualTo(Objects.requireNonNull(STORED.get(kind.key())));
  }
}
