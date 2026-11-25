package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecSymbolsTest {

  @Test
  void everySymbolSpecsNameExists() {
    assertThat(SpecSymbols.missingFromMain()).isEmpty();
  }

  @Test
  void everyHostJobIsNamedInSpecs() {
    assertThat(SpecSymbols.hostJobIds()).contains("jarwatch", "keepawake", "releasecheck");
    assertThat(SpecSymbols.hostJobIdsNamedNowhere()).isEmpty();
  }
}
