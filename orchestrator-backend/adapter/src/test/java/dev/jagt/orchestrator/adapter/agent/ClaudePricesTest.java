package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClaudePricesTest {

  @ParameterizedTest
  @CsvSource({
    "claude-fable-5-1, FABLE_5_1",
    "claude-fable-5, FABLE",
    "claude-opus-5, OPUS",
    "claude-opus-4-1-20250805, OPUS_4_1",
    "claude-sonnet-5, SONNET_5",
    "claude-sonnet-4-6, SONNET",
    "claude-haiku-4-5-20251001, HAIKU",
  })
  void picksTheRowNamingTheModelId(String model, ClaudePrices expected) {
    assertThat(ClaudePrices.of(model)).contains(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "claude-opus-5-turbo",
    "my-claude-opus-5",
    "opus",
    "claude-opus-5-2025",
    "claude-nova-9",
  })
  void leavesUnpricedAModelIdOnlyContainingAKnownModelsName(String model) {
    assertThat(ClaudePrices.of(model)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "claude-opus-5-5, 24.2",
    "claude-sonnet-5-5, 12.1",
    "claude-haiku-5-5, 3.05",
  })
  void pricesAMillionOfInputCacheReadAndOutputAtTheModelsOwnListPrice(
      String model, double expected) {
    double cost =
        ClaudePrices.of(model).orElseThrow().costOf(1_000_000, 0, 0, 1_000_000, 1_000_000);

    assertThat(cost).isCloseTo(expected, within(1e-9));
  }

  @Test
  void pricesEachTokenKindAtItsOwnRatePerMillion() {
    double cost =
        ClaudePrices.SONNET.costOf(2_000_000, 1_000_000, 1_000_000, 10_000_000, 1_000_000);

    assertThat(cost).isCloseTo(3 * 2 + 3.75 + 6 + 3 + 15, within(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
    "100000, 0, 0, 0, 0.51",
    "100001, 0, 0, 0, 2.5500005",
    "0, 0, 0, 100001, 2.50500005",
    "0, 100001, 0, 0, 2.562500625",
    "0, 0, 100001, 0, 2.600001",
  })
  void chargesHaikuFiveFiveItsLongContextRateOnlyForAPromptAbove100KTokens(
      long input, long write5m, long write1h, long cacheRead, double expected) {
    double cost = ClaudePrices.HAIKU_5_5.costOf(input, write5m, write1h, cacheRead, 1_000_000);

    assertThat(cost).isCloseTo(expected, within(1e-9));
  }

  @Test
  void keepsOpusFiveFiveAtOneRateForAPromptOfAMillionTokens() {
    double cost = ClaudePrices.OPUS_5_5.costOf(1_000_000, 0, 0, 0, 0);

    assertThat(cost).isCloseTo(4, within(1e-9));
  }
}
