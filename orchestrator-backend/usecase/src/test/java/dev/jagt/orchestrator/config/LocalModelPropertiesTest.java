package dev.jagt.orchestrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocalModelPropertiesTest {

  @ParameterizedTest
  @CsvSource({"0.8, 0.8", "1, 1", "0, 0", "-0.1, 0", "1.5, 0", "NaN, 0"})
  void keepsConfidenceWithinZeroAndOneAndFallsBackToZeroOutsideIt(
      double configured, double expected) {
    assertThat(LocalModelProperties.off().withMinConfidence(configured).minConfidence())
        .isEqualTo(expected);
  }
}
